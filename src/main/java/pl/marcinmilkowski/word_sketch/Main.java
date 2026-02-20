package pl.marcinmilkowski.word_sketch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.marcinmilkowski.word_sketch.config.GrammarConfigLoader;
import pl.marcinmilkowski.word_sketch.indexer.blacklab.BlackLabConllUIndexer;
import pl.marcinmilkowski.word_sketch.query.BlackLabQueryExecutor;
import pl.marcinmilkowski.word_sketch.query.QueryExecutor;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Main entry point for Word Sketch Lucene using BlackLab backend.
 * 
 * BlackLab provides native CoNLL-U dependency indexing and CQL query support.
 * 
 * Commands:
 *   blacklab-index --input corpus.conllu --output data/index/
 *   blacklab-query --index data/index/ --lemma theory --deprel amod
 *   server --index data/index/ --port 8080
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   Word Sketch Lucene - BlackLab Edition  ");
        System.out.println("==========================================");
        System.out.println();

        if (args.length == 0) {
            showUsage();
            return;
        }

        try {
            String command = args[0].toLowerCase();

            switch (command) {
                case "blacklab-index":
                    handleBlackLabIndexCommand(args);
                    break;
                case "blacklab-query":
                    handleBlackLabQueryCommand(args);
                    break;
                case "help":
                    showUsage();
                    break;
                default:
                    logger.error("Unknown command: " + command);
                    showUsage();
            }
        } catch (Exception e) {
            logger.error("Application error", e);
            System.err.println("Error: " + e.getMessage());
            System.err.println("Use 'help' command for usage information.");
        }
    }

    private static void showUsage() {
        System.out.println("Usage: java -jar word-sketch-lucene.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  blacklab-index --input <file.conllu> --output <index-dir>");
        System.out.println("      Index a CoNLL-U file with BlackLab");
        System.out.println();
        System.out.println("  blacklab-query --index <dir> --lemma <word> [--deprel <rel>]");
        System.out.println("      Query the index for collocations");
        System.out.println("      Options:");
        System.out.println("        --deprel <rel>   Dependency relation (e.g., amod, nsubj, obj)");
        System.out.println("        --min-logdice <n>  Minimum logDice score (default: 0)");
        System.out.println("        --limit <n>      Max results (default: 20)");
        System.out.println();
        System.out.println("  server --index <dir> [--port <port>]");
        System.out.println("      Start REST API server");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Tag corpus with Stanza (Python required)");
        System.out.println("  python tag_with_stanza.py -i corpus.txt -o corpus.conllu");
        System.out.println();
        System.out.println("  # Index CoNLL-U file");
        System.out.println("  java -jar word-sketch-lucene.jar blacklab-index \\");
        System.out.println("    --input corpus.conllu --output data/index/");
        System.out.println();
        System.out.println("  # Query for adjectival modifiers of 'theory'");
        System.out.println("  java -jar word-sketch-lucene.jar blacklab-query \\");
        System.out.println("    --index data/index/ --lemma theory --deprel amod");
        System.out.println();
        System.out.println("  # Start API server");
        System.out.println("  java -jar word-sketch-lucene.jar server \\");
        System.out.println("    --index data/index/ --port 8080");
        System.out.println();
        System.out.println("  # Query API");
        System.out.println("  curl 'http://localhost:8080/api/sketch/theory?deprel=amod'");
    }

    private static void handleBlackLabIndexCommand(String[] args) {
        System.out.println("=== BlackLab Indexer ===");
        System.out.println();
        System.out.println("Note: BlackLab indexing requires the format configuration to be registered.");
        System.out.println("For CoNLL-U indexing, please use BlackLab's command-line tools:");
        System.out.println();
        System.out.println("  # Create index from CoNLL-U file");
        System.out.println("  blacklab-index-create --format conllu output-dir/ input.conllu");
        System.out.println();
        System.out.println("Or use the BlackLab Server web interface for indexing.");
        System.out.println();
        System.out.println("Once indexed, you can query with:");
        System.out.println("  java -jar word-sketch-lucene.jar blacklab-query --index output-dir/ --lemma theory --deprel amod");
        System.out.println();
        
        // Legacy indexing disabled due to BlackLab classpath scanning issues
        // Users should use BlackLab CLI tools directly
    }

    private static void handleBlackLabQueryCommand(String[] args) throws IOException {
        String indexPath = null;
        String lemma = null;
        String deprel = null;
        double minLogDice = 0;
        int limit = 20;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--index":
                case "-i":
                    indexPath = args[++i];
                    break;
                case "--lemma":
                case "-w":
                    lemma = args[++i];
                    break;
                case "--deprel":
                    deprel = args[++i];
                    break;
                case "--min-logdice":
                    minLogDice = Double.parseDouble(args[++i]);
                    break;
                case "--limit":
                    limit = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
            }
        }

        if (indexPath == null || lemma == null) {
            System.err.println("Error: --index and --lemma are required");
            return;
        }

        System.out.println("=== BlackLab Query ===");
        System.out.println("Index: " + indexPath);
        System.out.println("Lemma: " + lemma);
        if (deprel != null) {
            System.out.println("Dependency: " + deprel);
        }
        System.out.println("Min logDice: " + minLogDice);
        System.out.println("Limit: " + limit);
        System.out.println();

        try (BlackLabQueryExecutor executor = new BlackLabQueryExecutor(indexPath)) {
            var results = deprel != null
                ? executor.findDependencyCollocations(lemma, deprel, minLogDice, limit)
                : executor.findCollocations(lemma, "[]", minLogDice, limit);

            if (results.isEmpty()) {
                System.out.println("No results found.");
                return;
            }

            System.out.println("Results:");
            System.out.println("--------");
            for (var result : results) {
                System.out.printf("  %s: freq=%d, logDice=%.2f, relFreq=%.4f%n",
                    result.getLemma(),
                    result.getFrequency(),
                    result.getLogDice(),
                    result.getRelativeFrequency()
                );
            }
        }
    }
}
