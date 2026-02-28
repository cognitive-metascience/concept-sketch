#!/usr/bin/env python3
"""
Tag a corpus with Stanza (tokenization, POS, lemma, dependency parsing).
Outputs CoNLL-U format suitable for BlackLab indexing.

Uses GPU (CUDA) if available for faster processing.

Usage:
    python tag_with_stanza.py --input corpus.txt --output corpus.conllu --lang en

Requirements:
    pip install stanza torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
"""

import argparse
import stanza
import sys
from pathlib import Path
import torch


def check_cuda():
    """Check CUDA availability and print GPU info."""
    if torch.cuda.is_available():
        gpu_count = torch.cuda.device_count()
        gpu_name = torch.cuda.get_device_name(0)
        gpu_memory = torch.cuda.get_device_properties(0).total_memory / (1024**3)
        print(f"✓ CUDA available: {gpu_count} GPU(s)")
        print(f"  GPU 0: {gpu_name} ({gpu_memory:.1f} GB)")
        print(f"  CUDA version: {torch.version.cuda}")
        return True
    else:
        print("✗ CUDA not available - will use CPU (slower)")
        print("  To enable GPU: pip install torch --index-url https://download.pytorch.org/whl/cu118")
        return False


def stream_units(input_path: Path, paragraph_mode: bool = False):
    """Yield processing units from the input file.

    paragraph_mode=False (default): yield one non-empty line at a time.
        Use this for corpora formatted as one sentence per line.
    paragraph_mode=True: yield blank-line-separated paragraph blocks.
        Use this when sentences span multiple lines within a paragraph.
    """
    if not paragraph_mode:
        with open(input_path, 'r', encoding='utf-8') as f:
            for line in f:
                stripped = line.rstrip('\n')
                if stripped:
                    yield stripped
    else:
        buf = []
        with open(input_path, 'r', encoding='utf-8') as f:
            for line in f:
                stripped = line.rstrip('\n')
                if stripped == '':
                    if buf:
                        yield '\n'.join(buf)
                        buf = []
                else:
                    buf.append(stripped)
            if buf:
                yield '\n'.join(buf)



def main():
    parser = argparse.ArgumentParser(
        description='Tag corpus with Stanza (tokenize, POS, lemma, depparse) using GPU'
    )
    parser.add_argument(
        '--input', '-i',
        required=True,
        type=Path,
        help='Input text file (one sentence per line or paragraph)'
    )
    parser.add_argument(
        '--output', '-o',
        required=True,
        type=Path,
        help='Output CoNLL-U file'
    )
    parser.add_argument(
        '--lang', '-l',
        default='en',
        help='Language code (default: en)'
    )
    parser.add_argument(
        '--download',
        action='store_true',
        help='Download the model before processing'
    )
    parser.add_argument(
        '--cpu',
        action='store_true',
        help='Force CPU usage (disable GPU)'
    )
    parser.add_argument(
        '--batch-size',
        type=int,
        default=32,
        help='Internal token batch size for the NLP model (GPU throughput tuning, default: 32)'
    )
    parser.add_argument(
        '--progress',
        type=int,
        default=1000,
        help='Print progress every N lines/paragraphs (default: 1000)'
    )
    parser.add_argument(
        '--paragraph-mode',
        action='store_true',
        help='Input uses blank-line-separated paragraphs instead of one sentence per line'
    )
    
    args = parser.parse_args()
    
    # Validate input
    if not args.input.exists():
        print(f"Error: Input file not found: {args.input}", file=sys.stderr)
        sys.exit(1)
    
    # Create output directory if needed
    args.output.parent.mkdir(parents=True, exist_ok=True)
    
    # Check CUDA
    use_cuda = not args.cpu and torch.cuda.is_available()
    check_cuda()
    
    # Download model if requested
    if args.download:
        print(f"Downloading Stanza model for {args.lang}...")
        stanza.download(args.lang)
    
    # Initialize pipeline with GPU support
    print(f"Initializing Stanza pipeline for {args.lang}...")
    print(f"Processors: tokenize, pos, lemma, depparse")
    print(f"Device: {'GPU (CUDA)' if use_cuda else 'CPU'}")
    print(f"Model token batch size: {args.batch_size}")
    
    try:
        nlp = stanza.Pipeline(
            lang=args.lang,
            processors='tokenize,pos,lemma,depparse',
            verbose=False,
            # GPU settings
            use_gpu=use_cuda and not args.cpu,
            gpu_memory_fraction=0.9,  # Use 90% of available GPU memory
            # Batch processing for GPU efficiency
            batch_size=args.batch_size,
        )
    except Exception as e:
        print(f"Error initializing Stanza: {e}", file=sys.stderr)
        if not use_cuda:
            print("GPU not available, falling back to CPU...")
            try:
                nlp = stanza.Pipeline(
                    lang=args.lang,
                    processors='tokenize,pos,lemma,depparse',
                    verbose=False,
                    use_gpu=False,
                    batch_size=args.batch_size,
                )
            except Exception as e2:
                print(f"Error initializing Stanza on CPU: {e2}", file=sys.stderr)
                print("Try running with --download flag to download the model first.", file=sys.stderr)
                sys.exit(1)
        else:
            print("Try running with --download flag to download the model first.", file=sys.stderr)
            sys.exit(1)
    
    # Process and write output (streaming)
    print(f"Streaming input: {args.input}")
    print(f"Processing paragraphs one-by-one (model token batch={args.batch_size})...")
    with open(args.output, 'w', encoding='utf-8') as f:
        sentence_count = 0
        token_count = 0
        para_count = 0

        for para in stream_units(args.input, args.paragraph_mode):
            doc = nlp(para)
            para_count += 1
            if para_count % args.progress == 0:
                print(f"  Processed {para_count:,} paragraphs ({sentence_count:,} sentences, {token_count:,} tokens)...")

            # Write in CoNLL-U format
            for sentence in doc.sentences:
                sentence_count += 1
                token_count += len(sentence.words)

                # Write CoNLL-U format
                for word in sentence.words:
                    # CoNLL-U columns:
                    # ID FORM LEMMA UPOS XPOS FEATS HEAD DEPREL DEPS MISC
                    misc = f"Text={word.text}" if word.text else ""
                    print(
                        word.id,
                        word.text,
                        word.lemma if word.lemma else '_',
                        word.upos if word.upos else '_',
                        word.xpos if word.xpos else '_',
                        word.feats if word.feats else '_',
                        word.head,
                        word.deprel if word.deprel else '_',
                        '_',
                        misc,
                        sep='\t',
                        file=f
                    )
                # Empty line after each sentence
                print(file=f)

        print(f"\nComplete!")
        print(f"  Paragraphs: {para_count:,}")
        print(f"  Sentences: {sentence_count:,}")
        print(f"  Tokens: {token_count:,}")
        print(f"  Output: {args.output}")


if __name__ == '__main__':
    main()
