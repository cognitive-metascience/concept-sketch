package pl.marcinmilkowski.word_sketch.tagging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * UDPipe 2 POS tagger wrapper.
 *
 * Requires UDPipe 2.x to be installed. Downloads models automatically if not present.
 *
 * Usage:
 * - Install UDPipe 2: https://ufal.mff.cuni.cz/udpipe/2
 * - Or use the Docker image: ufal/udpipe
 */
public class UDPipeTagger implements PosTagger {

    private static final Logger logger = LoggerFactory.getLogger(UDPipeTagger.class);

    private final String modelPath;
    private final String language;
    private Process udpipeProcess;

    public UDPipeTagger(String modelPath, String language) {
        this.modelPath = modelPath;
        this.language = language;
    }

    /**
     * Create a UDPipe tagger with a model file.
     */
    public static UDPipeTagger create(String modelFile, String language) {
        return new UDPipeTagger(modelFile, language);
    }

    /**
     * Create a UDPipe tagger using a pretrained model.
     * Downloads the model if not present.
     */
    public static UDPipeTagger createForLanguage(String language) throws IOException {
        String modelDir = System.getProperty("user.home") + "/.udpipe";
        String modelFile = modelDir + "/" + language + ".udpipe";

        // Check if model exists, otherwise download
        java.nio.file.Path modelPath = Paths.get(modelFile);
        if (!modelPath.toFile().exists()) {
            logger.info("Downloading UDPipe model for " + language + "...");
            java.nio.file.Files.createDirectories(modelPath.getParent());

            String url = "https://raw.githubusercontent.com/ufal/udpipe/master/models/" + language + "-ud-2.0-170801.udpipe";
            try {
                java.net.URL downloadUrl = new java.net.URL(url);
                java.net.URLConnection conn = downloadUrl.openConnection();
                try (java.io.InputStream in = conn.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(modelFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                logger.info("Model downloaded to " + modelFile);
            } catch (IOException e) {
                throw new IOException("Failed to download UDPipe model for " + language +
                    ". Please download manually from: " + url, e);
            }
        }

        return new UDPipeTagger(modelFile, language);
    }

    @Override
    public List<TaggedToken> tagSentence(String sentence) throws IOException {
        // Tokenize and tag using UDPipe
        String input = sentence + "\n";
        String output = executeUdpipe("--tokenize", "--tag", "--lemma", input);

        return parseConllUOutput(output, sentence);
    }

    @Override
    public List<List<TaggedToken>> tagSentences(List<String> sentences) throws IOException {
        // Process sentences one at a time
        List<List<TaggedToken>> results = new ArrayList<>();
        for (String sentence : sentences) {
            results.add(tagSentence(sentence));
        }
        return results;
    }

    /**
     * Execute a UDPipe command.
     */
    private String executeUdpipe(String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("udpipe");
        command.add("--model=" + modelPath);
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Write input
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            writer.write("---\n");  // Document separator
        }

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("UDPipe exited with code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("UDPipe interrupted", e);
        }

        return output.toString();
    }

    /**
     * Parse UDPipe CoNLL-U format output.
     *
     * CoNLL-U format:
     * # comment
     * 1   word    lemma   UPOS    XPOS    ...   SpaceAfter=No
     * ...
     */
    private List<TaggedToken> parseConllUOutput(String output, String originalSentence) {
        List<TaggedToken> tokens = new ArrayList<>();
        String[] lines = output.split("\n");
        int position = 0;

        for (String line : lines) {
            // Skip comments and empty lines
            if (line.startsWith("#") || line.trim().isEmpty()) {
                continue;
            }

            // Parse CoNLL-U token line
            String[] fields = line.split("\t");
            if (fields.length < 10) {
                continue;
            }

            try {
                int id = Integer.parseInt(fields[0]);
                String word = fields[1];
                String lemma = fields[2];
                String upos = fields[3];
                String xpos = fields[4];

                // Use XPOS if available, otherwise UPOS
                String tag = xpos != null && !xpos.equals("_") ? xpos : upos;

                tokens.add(new TaggedToken(word, lemma, tag, position++));
            } catch (NumberFormatException e) {
                // Skip multi-word tokens (contain dots like 1-2)
            }
        }

        return tokens;
    }

    @Override
    public String getName() {
        return "UDPipe 2 (" + language + ")";
    }

    @Override
    public String getTagset() {
        return "Universal Dependencies";
    }
}
