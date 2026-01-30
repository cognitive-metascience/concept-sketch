package pl.marcinmilkowski.word_sketch.query;

import java.io.IOException;

/**
 * Factory for creating QueryExecutor instances.
 * 
 * Supports different index types:
 * - LEGACY: Token-per-document index (current implementation)
 * - HYBRID: Sentence-per-document index (future implementation)
 * - DUAL: Runs both and compares results (for verification)
 */
public class QueryExecutorFactory {

    /**
     * Index type enumeration.
     */
    public enum IndexType {
        /** Token-per-document index (legacy) */
        LEGACY,
        
        /** Sentence-per-document index (hybrid) - not yet implemented */
        HYBRID,
        
        /** Dual mode: runs both implementations and compares */
        DUAL
    }

    /**
     * Create a QueryExecutor for the given index path and type.
     * 
     * @param indexPath Path to the Lucene index directory
     * @param type Type of executor to create
     * @return QueryExecutor instance
     * @throws IOException if index cannot be opened
     * @throws UnsupportedOperationException if type is not yet implemented
     */
    public static QueryExecutor create(String indexPath, IndexType type) throws IOException {
        return switch (type) {
            case LEGACY -> new WordSketchQueryExecutor(indexPath);
            case HYBRID -> throw new UnsupportedOperationException(
                "Hybrid index executor not yet implemented. See plans/hybrid-index-spec.md");
            case DUAL -> throw new UnsupportedOperationException(
                "Dual mode executor not yet implemented");
        };
    }

    /**
     * Create a legacy QueryExecutor (convenience method).
     * 
     * @param indexPath Path to the Lucene index directory
     * @return QueryExecutor instance
     * @throws IOException if index cannot be opened
     */
    public static QueryExecutor createLegacy(String indexPath) throws IOException {
        return create(indexPath, IndexType.LEGACY);
    }

    /**
     * Detect the index type from the index directory.
     * 
     * Future implementation will check for hybrid index markers.
     * 
     * @param indexPath Path to the Lucene index directory
     * @return Detected index type
     */
    public static IndexType detectIndexType(String indexPath) {
        // TODO: Implement detection by checking for sentence_id field or other markers
        // For now, always return LEGACY
        return IndexType.LEGACY;
    }

    /**
     * Create a QueryExecutor with auto-detected index type.
     * 
     * @param indexPath Path to the Lucene index directory
     * @return QueryExecutor instance for the detected index type
     * @throws IOException if index cannot be opened
     */
    public static QueryExecutor createAutoDetect(String indexPath) throws IOException {
        IndexType type = detectIndexType(indexPath);
        return create(indexPath, type);
    }
}
