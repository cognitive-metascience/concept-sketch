package pl.marcinmilkowski.word_sketch.tagging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.marcinmilkowski.word_sketch.indexer.LuceneIndexer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Processes a corpus: tokenizes, tags, and indexes it.
 *
 * Pipeline:
 * 1. Read raw text corpus
 * 2. Split into sentences
 * 3. POS tag each sentence
 * 4. Index the tagged tokens
 */
public class CorpusProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CorpusProcessor.class);

    private final PosTagger tagger;
    private final LuceneIndexer indexer;

    public CorpusProcessor(PosTagger tagger, LuceneIndexer indexer) {
        this.tagger = tagger;
        this.indexer = indexer;
    }

    /**
     * Process a corpus file and index it.
     *
     * @param inputFile Path to the input corpus file
     * @param outputIndex Path for the output index
     * @param batchSize Number of sentences to process between commits
     */
    public void processCorpus(String inputFile, String outputIndex, int batchSize) throws IOException {
        logger.info("Starting corpus processing: " + inputFile);

        Path inputPath = Paths.get(inputFile);
        int sentenceId = 0;
        int totalTokens = 0;
        int processedSentences = 0;
        long startTime = System.currentTimeMillis();

        // First pass: check if file has blank line separators
        boolean hasBlankLines = false;
        try (BufferedReader checkReader = Files.newBufferedReader(inputPath)) {
            String line;
            while ((line = checkReader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    hasBlankLines = true;
                    break;
                }
            }
        }

        // Second pass: process with appropriate strategy
        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            String line;
            List<String> sentenceBuffer = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (hasBlankLines) {
                    // Use blank line as sentence boundary
                    if (line.isEmpty()) {
                        if (!sentenceBuffer.isEmpty()) {
                            totalTokens += processSentenceBuffer(sentenceBuffer, sentenceId++);
                            processedSentences++;
                            sentenceBuffer.clear();
                        }
                    } else {
                        sentenceBuffer.add(line);
                    }
                } else {
                    // Each non-empty line is a sentence
                    if (!line.isEmpty()) {
                        totalTokens += processSentenceBuffer(Collections.singletonList(line), sentenceId++);
                        processedSentences++;
                    }
                }

                // Batch commit
                if (processedSentences % batchSize == 0) {
                    indexer.commit();
                    long elapsed = System.currentTimeMillis() - startTime;
                    double tokensPerSec = totalTokens / (elapsed / 1000.0);
                    logger.info("Processed " + processedSentences + " sentences, " +
                               totalTokens + " tokens (" + String.format("%.1f", tokensPerSec) + " tokens/sec)");
                }
            }
        }

        // Final commit and optimization
        indexer.commit();
        indexer.optimize();

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Corpus processing complete. Processed " + processedSentences +
                   " sentences, " + totalTokens + " tokens in " + (elapsed / 1000) + "s");

        indexer.close();
    }

    /**
     * Process a buffer of lines forming one or more sentences.
     * Returns the number of tokens indexed.
     */
    private int processSentenceBuffer(List<String> lines, int sentenceId) throws IOException {
        StringBuilder sentenceBuilder = new StringBuilder();
        for (String line : lines) {
            if (sentenceBuilder.length() > 0) {
                sentenceBuilder.append(" ");
            }
            sentenceBuilder.append(line);
        }

        String sentence = sentenceBuilder.toString();
        List<PosTagger.TaggedToken> tokens = tagger.tagSentence(sentence);

        int position = 0;
        for (PosTagger.TaggedToken token : tokens) {
            int startOffset = sentence.indexOf(token.getWord());
            int endOffset = startOffset + token.getWord().length();

            indexer.addWord(
                sentenceId,
                position,
                token.getWord(),
                token.getLemma(),
                token.getTag(),
                token.getPosGroup(),
                sentence,
                startOffset,
                endOffset
            );

            position++;
        }

        return tokens.size();
    }

    /**
     * Process sentences from memory.
     */
    public void processSentences(List<String> sentences) throws IOException {
        int sentenceId = 0;
        for (String sentence : sentences) {
            processSentenceBuffer(Collections.singletonList(sentence), sentenceId++);
        }
        indexer.commit();
    }

    /**
     * Get the total number of tokens processed so far.
     */
    public int getTotalTokens() {
        // This would require tracking, but for now just return 0
        return 0;
    }

    /**
     * Simple sentence splitter.
     */
    public static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();

        // Split on sentence boundaries
        String[] parts = text.split("(?<=[.!?])\\s+");

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }

        return sentences;
    }

    /**
     * Factory method to create a processor with UDPipe or fallback tagger.
     */
    public static CorpusProcessor create(String indexPath, String language) throws IOException {
        PosTagger tagger;

        // Try UDPipe first
        try {
            tagger = UDPipeTagger.createForLanguage(language);
            logger.info("Using UDPipe tagger for " + language);
        } catch (IOException e) {
            logger.warn("UDPipe not available, using simple tagger: " + e.getMessage());
            tagger = SimpleTagger.create();
        }

        LuceneIndexer indexer = new LuceneIndexer(indexPath);
        return new CorpusProcessor(tagger, indexer);
    }
}
