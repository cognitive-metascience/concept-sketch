package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.SentenceDocument.Token;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds precomputed collocations index from a hybrid Lucene index.
 * 
 * For each lemma in the corpus:
 * 1. Find all documents containing the lemma
 * 2. Extract tokens within window of each occurrence
 * 3. Aggregate cooccurrence counts
 * 4. Compute logDice association scores
 * 5. Keep top-K collocates by logDice
 * 6. Write to binary file with hash index for O(1) lookup
 * 
 * Usage:
 *   CollocationsBuilder builder = new CollocationsBuilder(indexPath, statsPath);
 *   builder.setWindowSize(5);
 *   builder.setTopK(100);
 *   builder.setMinFrequency(10);
 *   builder.build(outputPath);
 */
public class CollocationsBuilder {

    private static final Logger logger = LoggerFactory.getLogger(CollocationsBuilder.class);

    private static final int MAGIC_NUMBER = 0x434F4C4C; // "COLL"
    private static final int VERSION = 1;

    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final StatisticsReader statsReader;
    private static final long HEADER_SIZE = 64;

    // Configuration
    private int windowSize = 5;
    private int topK = 100;
    private int minFrequency = 10;
    private int minCooccurrence = 2;
    private int batchSize = 1000;
    private int threads = Runtime.getRuntime().availableProcessors();
    private int checkpointEvery = 5_000;
    private boolean resume = false;

    /**
     * Create a collocations builder.
     * 
     * @param indexPath Path to hybrid Lucene index
     * @param statsPath Path to stats.bin or stats.tsv
     */
    public CollocationsBuilder(String indexPath, String statsPath) throws IOException {
        Path path = Paths.get(indexPath);
        this.reader = DirectoryReader.open(MMapDirectory.open(path));
        this.searcher = new IndexSearcher(reader);
        this.statsReader = new StatisticsReader(statsPath);

        logger.info("CollocationsBuilder initialized:");
        logger.info("  Index: {} sentences", reader.numDocs());
        logger.info("  Stats: {} lemmas, {} tokens",
            statsReader.getUniqueLemmaCount(), statsReader.getTotalTokens());
    }

    // Configuration setters
    public void setWindowSize(int size) { this.windowSize = size; }
    public void setTopK(int k) { this.topK = k; }
    public void setMinFrequency(int freq) { this.minFrequency = freq; }
    public void setMinCooccurrence(int cooc) { this.minCooccurrence = cooc; }
    public void setBatchSize(int size) { this.batchSize = size; }
    public void setThreads(int n) { this.threads = n; }
    public void setCheckpointEvery(int n) { this.checkpointEvery = Math.max(100, n); }
    public void setResume(boolean resume) { this.resume = resume; }

    /**
     * Build collocations for all lemmas and write to binary file.
     */
    public void build(String outputPath) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        String offsetsTmpPath = outputPath + ".offsets.tmp";
        Path outFile = Paths.get(outputPath);
        Path offsetsTmpFile = Paths.get(offsetsTmpPath);

        // Get all lemmas meeting frequency threshold
        List<String> lemmas = statsReader.getLemmasByMinFrequency(minFrequency);
        logger.info("Building collocations for {} lemmas (freq >= {})", lemmas.size(), minFrequency);
        logger.info("Configuration: window={}, top-K={}, min-cooc={}, threads={}",
            windowSize, topK, minCooccurrence, threads);

        // Streaming build: write entries to output file as we go, and stream the offset table to a temp file.
        // This avoids holding all CollocationEntry objects in memory and makes long builds recoverable.
        final Object writeLock = new Object();

        // Resume support: load already-written headwords from offsets tmp (if present)
        final Set<String> alreadyDone;
        final AtomicInteger entryCount;
        if (resume && java.nio.file.Files.exists(outFile) && java.nio.file.Files.exists(offsetsTmpFile)) {
            alreadyDone = new HashSet<>();

            int restoredCount = 0;
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(java.nio.file.Files.newInputStream(offsetsTmpFile)))) {
                // The first int is a checkpoint count, which may be stale if the process was killed.
                // For robustness, scan until EOF and count actual records.
                dis.readInt();
                while (true) {
                    try {
                        int len = dis.readUnsignedShort();
                        byte[] bytes = dis.readNBytes(len);
                        alreadyDone.add(new String(bytes, StandardCharsets.UTF_8));
                        dis.readLong();
                        restoredCount++;
                    } catch (EOFException eof) {
                        break;
                    }
                }
            }

            entryCount = new AtomicInteger(restoredCount);
            logger.info("Resuming build: {} headwords already written (scanned {})", entryCount.get(), offsetsTmpPath);
        } else {
            alreadyDone = null;
            entryCount = new AtomicInteger(0);
        }

        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger withResults = new AtomicInteger(entryCount.get());

        try (RandomAccessFile outRaf = new RandomAccessFile(outputPath, "rw");
             FileChannel outChannel = outRaf.getChannel();
             RandomAccessFile offsetsRaf = new RandomAccessFile(offsetsTmpPath, "rw")) {

            if (resume && alreadyDone != null) {
                // Append mode
                if (outRaf.length() < HEADER_SIZE) {
                    throw new IOException("Cannot resume: output file is too small: " + outputPath);
                }
                outChannel.position(outRaf.length());
                offsetsRaf.seek(offsetsRaf.length());
            } else {
                // Fresh build: truncate and write placeholder header
                outRaf.setLength(0);
                outChannel.position(0);
                writeHeader(outChannel, 0, 0L, 0L);
                outChannel.position(HEADER_SIZE);

                offsetsRaf.setLength(0);
                offsetsRaf.seek(0);
                offsetsRaf.writeInt(0); // placeholder count
            }

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();

            for (String lemma : lemmas) {
                if (alreadyDone != null && alreadyDone.contains(lemma)) {
                    int count = processed.incrementAndGet();
                    if (count % 1000 == 0) {
                        logger.info("Progress: {}/{} lemmas processed, {} with collocates",
                            count, lemmas.size(), withResults.get());
                    }
                    continue;
                }

                Future<?> future = executor.submit(() -> {
                    try {
                        CollocationEntry entry = buildForLemma(lemma);
                        if (entry != null && !entry.isEmpty()) {
                            synchronized (writeLock) {
                                long offset = outChannel.position();
                                writeEntry(outChannel, entry);
                                writeOffsetRecord(offsetsRaf, entry.headword(), offset);

                                int newCount = entryCount.incrementAndGet();
                                withResults.incrementAndGet();

                                // Periodically checkpoint the offset tmp count to enable resume after power loss
                                if (newCount % checkpointEvery == 0) {
                                    long pos = offsetsRaf.getFilePointer();
                                    offsetsRaf.seek(0);
                                    offsetsRaf.writeInt(newCount);
                                    offsetsRaf.seek(pos);
                                    outChannel.force(false);
                                }
                            }
                        }

                        int count = processed.incrementAndGet();
                        if (count % 1000 == 0) {
                            logger.info("Progress: {}/{} lemmas processed, {} with collocates",
                                count, lemmas.size(), withResults.get());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to build collocations for '{}'", lemma, e);
                    }
                });
                futures.add(future);
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.error("Task execution failed", e);
                }
            }
            executor.shutdown();

            // Finalize offset tmp count
            synchronized (writeLock) {
                long pos = offsetsRaf.getFilePointer();
                offsetsRaf.seek(0);
                offsetsRaf.writeInt(entryCount.get());
                offsetsRaf.seek(pos);
                outChannel.force(false);
            }

            logger.info("Completed: {}/{} lemmas have collocates", withResults.get(), lemmas.size());

            // Append offset table (temp file) to the output
            long dataEnd = outChannel.position();
            long offsetTableStart = dataEnd;
            long offsetTableSize;
            try (FileChannel offsetsChannel = java.nio.channels.FileChannel.open(offsetsTmpFile, StandardOpenOption.READ)) {
                offsetTableSize = offsetsChannel.size();
                long transferred = 0;
                while (transferred < offsetTableSize) {
                    transferred += offsetsChannel.transferTo(transferred, offsetTableSize - transferred, outChannel);
                }
            }

            long fileEnd = outChannel.position();

            // Write final header
            outChannel.position(0);
            writeHeader(outChannel, entryCount.get(), offsetTableStart, offsetTableSize);

            logger.info("Written:");
            logger.info("  Header: {} bytes", HEADER_SIZE);
            logger.info("  Data: {} bytes", dataEnd - HEADER_SIZE);
            logger.info("  Offset table: {} bytes", offsetTableSize);
            logger.info("  Total: {} bytes ({} MB)", fileEnd, fileEnd / (1024 * 1024));
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Build completed in {} minutes", String.format(Locale.ROOT, "%.1f", elapsed / 60000.0));
        logger.info("Output: {}", outputPath);
    }

    /**
     * Build collocations for a single lemma.
     */
    private CollocationEntry buildForLemma(String lemma) throws IOException {
        long headwordFreq = statsReader.getFrequency(lemma);
        if (headwordFreq == 0) return null;

        // Find all documents containing this lemma.
        // IMPORTANT: do NOT use searcher.search(..., Integer.MAX_VALUE) because it materializes a huge ScoreDoc[]
        // for frequent lemmas (e.g., "the"), which can OOM the JVM. Stream matches via a collector.
        TermQuery query = new TermQuery(new Term("lemma", lemma.toLowerCase()));
        Map<String, Long> cooccurrences = new HashMap<>();
        final String lemmaLower = lemma.toLowerCase(Locale.ROOT);
        final int[] hitCount = new int[] {0};

        searcher.search(query, new SimpleCollector() {
            private BinaryDocValues tokensDv;

            @Override
            protected void doSetNextReader(LeafReaderContext context) throws IOException {
                this.tokensDv = context.reader().getBinaryDocValues("tokens");
            }

            @Override
            public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
            }

            @Override
            public void collect(int doc) throws IOException {
                if (tokensDv == null) return;
                if (!tokensDv.advanceExact(doc)) return;

                hitCount[0]++;

                BytesRef bytesRef = tokensDv.binaryValue();
                List<Token> tokens = TokenSequenceCodec.decode(bytesRef);
                if (tokens.isEmpty()) return;

                // Find all positions of headword in this sentence token sequence
                List<Integer> headwordPositions = new ArrayList<>();
                for (int i = 0; i < tokens.size(); i++) {
                    String tLemma = tokens.get(i).lemma();
                    if (tLemma != null && lemmaLower.equals(tLemma.toLowerCase(Locale.ROOT))) {
                        headwordPositions.add(i);
                    }
                }

                if (headwordPositions.isEmpty()) return;

                // Collect collocates within window for each occurrence
                for (int hwPos : headwordPositions) {
                    int start = Math.max(0, hwPos - windowSize);
                    int end = Math.min(tokens.size(), hwPos + windowSize + 1);

                    for (int i = start; i < end; i++) {
                        if (i == hwPos) continue;

                        String collocateLemma = tokens.get(i).lemma();
                        if (collocateLemma == null) continue;
                        String collLower = collocateLemma.toLowerCase(Locale.ROOT);
                        if (collLower.equals(lemmaLower)) continue;

                        cooccurrences.merge(collLower, 1L, Long::sum);
                    }
                }
            }
        });

        if (hitCount[0] == 0) {
            return null;
        }

        // Filter by minimum cooccurrence
        cooccurrences.entrySet().removeIf(e -> e.getValue() < minCooccurrence);

        if (cooccurrences.isEmpty()) {
            return null;
        }

        // Compute logDice and create Collocation objects
        List<Collocation> collocations = new ArrayList<>();
        for (Map.Entry<String, Long> entry : cooccurrences.entrySet()) {
            String collocateLemma = entry.getKey();
            long coocCount = entry.getValue();

            long collocateFreq = statsReader.getFrequency(collocateLemma);
            if (collocateFreq == 0) continue;

            double logDice = calculateLogDice(coocCount, headwordFreq, collocateFreq);

            // Get most frequent POS tag
            TermStatistics stats = statsReader.getStatistics(collocateLemma);
            String pos = getMostFrequentPos(stats);

            collocations.add(new Collocation(
                collocateLemma, pos, coocCount, collocateFreq, (float) logDice));
        }

        // Sort by logDice descending and keep top-K
        Collections.sort(collocations);
        if (collocations.size() > topK) {
            collocations = collocations.subList(0, topK);
        }

        return new CollocationEntry(lemma, headwordFreq, collocations);
    }

    private void writeOffsetRecord(RandomAccessFile offsetsRaf, String headword, long offset) throws IOException {
        byte[] headwordBytes = headword.getBytes(StandardCharsets.UTF_8);
        if (headwordBytes.length > 65535) {
            throw new IOException("Headword too long: " + headword);
        }
        offsetsRaf.writeShort(headwordBytes.length);
        offsetsRaf.write(headwordBytes);
        offsetsRaf.writeLong(offset);
    }

    /**
     * Calculate logDice association score.
     */
    private double calculateLogDice(long cooccurrence, long freq1, long freq2) {
        if (cooccurrence <= 0 || freq1 <= 0 || freq2 <= 0) {
            return 0.0;
        }
        double dice = (2.0 * cooccurrence) / (freq1 + freq2);
        double logDice = Math.log(dice) / Math.log(2) + 14;
        return Math.max(0, Math.min(14, logDice));
    }

    /**
     * Get most frequent POS tag for a lemma.
     */
    private String getMostFrequentPos(TermStatistics stats) {
        if (stats == null || stats.posDistribution() == null || stats.posDistribution().isEmpty()) {
            return "";
        }
        return stats.posDistribution().entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");
    }

    private void writeHeader(FileChannel channel, int entryCount, long offsetTableOffset, long offsetTableSize)
            throws IOException {
        var buffer = java.nio.ByteBuffer.allocate(64);
        buffer.putInt(MAGIC_NUMBER);
        buffer.putInt(VERSION);
        buffer.putInt(entryCount);
        buffer.putInt(windowSize);
        buffer.putInt(topK);
        buffer.putLong(statsReader.getTotalTokens());
        buffer.putLong(offsetTableOffset);
        buffer.putLong(offsetTableSize);
        buffer.flip();
        channel.write(buffer);
    }

    private void writeEntry(FileChannel channel, CollocationEntry entry) throws IOException {
        var buffer = java.nio.ByteBuffer.allocate(32768); // 32KB buffer

        // Write headword
        byte[] headwordBytes = entry.headword().getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) headwordBytes.length);
        buffer.put(headwordBytes);

        // Write frequency
        buffer.putLong(entry.headwordFrequency());

        // Write collocates
        buffer.putShort((short) entry.collocates().size());

        for (Collocation c : entry.collocates()) {
            byte[] lemmaBytes = c.lemma().getBytes(StandardCharsets.UTF_8);
            byte[] posBytes = c.pos().getBytes(StandardCharsets.UTF_8);

            buffer.put((byte) lemmaBytes.length);
            buffer.put(lemmaBytes);

            buffer.put((byte) posBytes.length);
            buffer.put(posBytes);

            buffer.putLong(c.cooccurrence());
            buffer.putLong(c.frequency());
            buffer.putFloat((float) c.logDice());
        }

        buffer.flip();
        channel.write(buffer);
    }

    public void close() throws IOException {
        reader.close();
        statsReader.close();
    }

    /**
     * Main method for CLI.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: CollocationsBuilder <indexPath> <outputPath> [options]");
            System.err.println("Options:");
            System.err.println("  --window N        Window size (default: 5)");
            System.err.println("  --top-k N         Top-K collocates (default: 100)");
            System.err.println("  --min-freq N      Minimum headword frequency (default: 10)");
            System.err.println("  --min-cooc N      Minimum cooccurrence (default: 2)");
            System.err.println("  --threads N       Parallel threads (default: CPU cores)");
            System.err.println("  --checkpoint N    Update resume checkpoint every N entries (default: 5000)");
            System.err.println("  --resume true|false  Resume from existing outputPath.offsets.tmp (default: false)");
            System.exit(1);
        }

        String indexPath = args[0];
        String outputPath = args[1];

        // Parse options
        int window = 5;
        int topK = 100;
        int minFreq = 10;
        int minCooc = 2;
        int threads = Runtime.getRuntime().availableProcessors();
        int checkpointEvery = 5_000;
        boolean resume = false;

        for (int i = 2; i < args.length; i += 2) {
            if (i + 1 >= args.length) break;
            String opt = args[i];
            String val = args[i + 1];

            switch (opt) {
                case "--window" -> window = Integer.parseInt(val);
                case "--top-k" -> topK = Integer.parseInt(val);
                case "--min-freq" -> minFreq = Integer.parseInt(val);
                case "--min-cooc" -> minCooc = Integer.parseInt(val);
                case "--threads" -> threads = Integer.parseInt(val);
                case "--checkpoint" -> checkpointEvery = Integer.parseInt(val);
                case "--resume" -> resume = Boolean.parseBoolean(val);
            }
        }

        // Determine stats path
        String statsPath = indexPath + "/stats.bin";
        if (!java.nio.file.Files.exists(Paths.get(statsPath))) {
            statsPath = indexPath + "/stats.tsv";
        }

        CollocationsBuilder builder = new CollocationsBuilder(indexPath, statsPath);
        builder.setWindowSize(window);
        builder.setTopK(topK);
        builder.setMinFrequency(minFreq);
        builder.setMinCooccurrence(minCooc);
        builder.setThreads(threads);
        builder.setCheckpointEvery(checkpointEvery);
        builder.setResume(resume);

        builder.build(outputPath);
        builder.close();
    }
}
