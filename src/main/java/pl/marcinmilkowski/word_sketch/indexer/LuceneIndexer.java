package pl.marcinmilkowski.word_sketch.indexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Lucene indexer for creating word sketch indexes.
 * This class handles the creation of Lucene indexes with the appropriate field configuration.
 */
public class LuceneIndexer {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexer.class);

    private static final String FIELD_DOC_ID = "doc_id";
    private static final String FIELD_POSITION = "position";
    private static final String FIELD_WORD = "word";
    private static final String FIELD_LEMMA = "lemma";
    private static final String FIELD_TAG = "tag";
    private static final String FIELD_POS_GROUP = "pos_group";
    private static final String FIELD_SENTENCE = "sentence";
    private static final String FIELD_START_OFFSET = "start_offset";
    private static final String FIELD_END_OFFSET = "end_offset";

    private final Directory directory;
    private final IndexWriter writer;

    public LuceneIndexer(String indexPath) throws IOException {
        this.directory = FSDirectory.open(Paths.get(indexPath));
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setRAMBufferSizeMB(256.0);
        this.writer = new IndexWriter(directory, config);
        logger.info("Lucene indexer initialized at: " + indexPath);
    }

    /**
     * Adds a word occurrence to the index.
     */
    public void addWord(int docId, int position, String word, String lemma,
                       String tag, String posGroup, String sentence,
                       int startOffset, int endOffset) throws IOException {
        Document doc = createDocument(docId, position, word, lemma, tag, posGroup, sentence,
                                     startOffset, endOffset);
        writer.addDocument(doc);
    }

    private Document createDocument(int docId, int position, String word, String lemma,
                                    String tag, String posGroup, String sentence,
                                    int startOffset, int endOffset) {
        Document doc = new Document();

        // Stored fields for display
        doc.add(new StoredField(FIELD_DOC_ID, docId));
        doc.add(new StoredField(FIELD_POSITION, position));
        doc.add(new StoredField(FIELD_WORD, word));
        doc.add(new StoredField(FIELD_LEMMA, lemma));
        doc.add(new StoredField(FIELD_TAG, tag));
        doc.add(new StoredField(FIELD_POS_GROUP, posGroup));
        doc.add(new StoredField(FIELD_SENTENCE, sentence));
        doc.add(new StoredField(FIELD_START_OFFSET, startOffset));
        doc.add(new StoredField(FIELD_END_OFFSET, endOffset));

        // Indexed fields for searching
        // Note: StandardAnalyzer lowercases text, so we lowercase tags for matching
        doc.add(new TextField(FIELD_LEMMA, lemma.toLowerCase(), Field.Store.NO));
        doc.add(new TextField(FIELD_TAG, tag.toLowerCase(), Field.Store.NO));
        doc.add(new TextField(FIELD_POS_GROUP, posGroup.toLowerCase(), Field.Store.NO));

        return doc;
    }

    /**
     * Commits the changes to the index.
     */
    public void commit() throws IOException {
        writer.commit();
        logger.info("Index committed.");
    }

    /**
     * Optimizes the index (optional but recommended for large indexes).
     */
    public void optimize() throws IOException {
        writer.forceMerge(1);
        logger.info("Index optimized.");
    }

    /**
     * Closes the index writer and directory.
     */
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
        if (directory != null) {
            directory.close();
        }
        logger.info("Indexer closed.");
    }

    /**
     * Gets the number of documents in the index.
     */
    public long getDocumentCount() throws IOException {
        return writer.getDocStats().numDocs;
    }
}
