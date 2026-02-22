import pl.marcinmilkowski.word_sketch.query.BlackLabQueryExecutor;
import pl.marcinmilkowski.word_sketch.query.QueryResults;
import java.io.File;
import java.util.List;

public class TestQuery {
    public static void main(String[] args) throws Exception {
        String indexPath = "D:\\corpora_philsci\\bi"; // from server.log
        File indexDir = new File(indexPath);
        if (!indexDir.exists()) {
            System.err.println("Index path not found: " + indexPath);
            return;
        }

        try (BlackLabQueryExecutor executor = new BlackLabQueryExecutor(indexPath)) {
            System.out.println("Executing pattern...");
            List<QueryResults.WordSketchResult> results = executor.executeSurfacePattern(
                    "test",
                    "[tag=\"NN.*\"] [tag=\"VB.*\"]", // subject_of pattern
                    1, // headPosition
                    2, // collocatePosition
                    0.0, // minLogDice
                    10 // maxResults
            );

            System.out.println("Results found: " + results.size());
            for (QueryResults.WordSketchResult res : results) {
                System.out
                        .println(res.getLemma() + " | freq: " + res.getFrequency() + " | logDice: " + res.getLogDice());
            }
        }
    }
}
