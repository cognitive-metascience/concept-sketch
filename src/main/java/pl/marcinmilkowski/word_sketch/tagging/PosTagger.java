package pl.marcinmilkowski.word_sketch.tagging;

import java.io.IOException;
import java.util.List;

/**
 * Interface for POS taggers.
 *
 * <p>Implementations include {@link SimpleTagger} (rule-based fallback, no external dependencies)
 * and UDPipe-backed taggers for production use. Use {@link SimpleTagger#create()} to obtain
 * a fallback tagger when UDPipe is unavailable.
 */
public interface PosTagger {

    /**
     * Tag a single sentence.
     *
     * @param sentence raw sentence text
     * @return list of tagged tokens in sentence order
     * @throws IOException if a model resource cannot be read
     */
    List<TaggedToken> tagSentence(String sentence) throws IOException;

    /**
     * Tag multiple sentences.
     *
     * @param sentences list of raw sentence texts
     * @return one token list per sentence, in input order
     * @throws IOException if a model resource cannot be read
     */
    List<List<TaggedToken>> tagSentences(List<String> sentences) throws IOException;

    /** Human-readable name for this tagger (for logging). */
    String getName();

    /** Name of the tagset produced (e.g. "Penn Treebank"). */
    String getTagset();
}
