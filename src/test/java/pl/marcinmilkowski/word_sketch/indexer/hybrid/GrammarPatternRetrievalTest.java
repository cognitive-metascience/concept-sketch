package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.marcinmilkowski.word_sketch.utils.LogDiceCalculator;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ground-truth validation test for grammar-pattern based collocation retrieval.
 *
 * Creates a controlled corpus with KNOWN grammatical patterns,
 * then validates that our pattern-based retrieval finds EXACTLY the expected collocates
 * with ZERO false positives and ZERO false negatives.
 *
 * Key insight: We test the DIRECT token matching approach that the grammar-pattern
 * builder will use, bypassing the complex CQL compilation for now.
 */
class GrammarPatternRetrievalTest {

    @TempDir
    Path tempDir;

    /**
     * A simple pattern matcher that finds grammatical relations in token sequences.
     * This mimics what the grammar-pattern precomputation will do.
     */
    static class SimplePatternMatcher {

        enum PatternType {
            ADJ_MODIFIER,    // JJ NN (adjective immediately before noun)
            NOUN_COMPOUND,   // NN NN (noun immediately before noun)
            SUBJECT_OF,      // NN VB (noun immediately before verb)
            OBJECT_OF,       // VB NN (verb immediately before noun)
            ADJ_PREDICATE    // NN VB JJ (noun + copula + adjective)
        }

        /**
         * Find all (headword, collocate) pairs matching the given pattern.
         */
        static Map<String, Map<String, Long>> findMatches(
                List<SentenceDocument> sentences,
                PatternType patternType) {

            Map<String, Map<String, Long>> results = new HashMap<>();

            for (SentenceDocument doc : sentences) {
                List<SentenceDocument.Token> tokens = doc.tokens();
                if (tokens == null || tokens.size() < 2) continue;

                for (int i = 0; i < tokens.size() - 1; i++) {
                    String tag1 = tokens.get(i).tag();
                    String tag2 = tokens.get(i + 1).tag();
                    String lemma1 = tokens.get(i).lemma();
                    String lemma2 = tokens.get(i + 1).lemma();

                    switch (patternType) {
                        case ADJ_MODIFIER:
                            // JJ immediately followed by NN
                            if (isAdjective(tag1) && isNoun(tag2)) {
                                results.computeIfAbsent(lemma2, k -> new HashMap<>())
                                       .merge(lemma1, 1L, Long::sum);
                            }
                            break;

                        case NOUN_COMPOUND:
                            // NN immediately followed by NN
                            if (isNoun(tag1) && isNoun(tag2)) {
                                results.computeIfAbsent(lemma2, k -> new HashMap<>())
                                       .merge(lemma1, 1L, Long::sum);
                            }
                            break;

                        case SUBJECT_OF:
                            // NN immediately followed by VB (subject)
                            if (isNoun(tag1) && isVerb(tag2)) {
                                results.computeIfAbsent(lemma1, k -> new HashMap<>())
                                       .merge(lemma2, 1L, Long::sum);
                            }
                            break;

                        case OBJECT_OF:
                            // VB immediately followed by NN (object)
                            if (isVerb(tag1) && isNoun(tag2)) {
                                results.computeIfAbsent(lemma1, k -> new HashMap<>())
                                       .merge(lemma2, 1L, Long::sum);
                            }
                            break;

                        case ADJ_PREDICATE:
                            // NN VB JJ (with copula) - need 3 tokens
                            if (i + 2 < tokens.size()) {
                                String tag3 = tokens.get(i + 2).tag();
                                String lemma3 = tokens.get(i + 2).lemma();
                                if (isNoun(tag1) && isVerb(tag2) && isAdjective(tag3)) {
                                    // Check if verb is a copula
                                    if (isCopula(lemma2)) {
                                        results.computeIfAbsent(lemma1, k -> new HashMap<>())
                                               .merge(lemma3, 1L, Long::sum);
                                    }
                                }
                            }
                            break;
                    }
                }
            }

            return results;
        }

        private static boolean isNoun(String tag) {
            return tag != null && (tag.startsWith("NN"));
        }

        private static boolean isVerb(String tag) {
            return tag != null && (tag.startsWith("VB"));
        }

        private static boolean isAdjective(String tag) {
            return tag != null && (tag.startsWith("JJ"));
        }

        private static boolean isCopula(String lemma) {
            return lemma != null && (
                lemma.equals("is") || lemma.equals("are") || lemma.equals("was") || lemma.equals("were") ||
                lemma.equals("seem") || lemma.equals("seems") || lemma.equals("seemed") ||
                lemma.equals("appear") || lemma.equals("appears") || lemma.equals("appeared") ||
                lemma.equals("remain") || lemma.equals("remains") || lemma.equals("remained") ||
                lemma.equals("become") || lemma.equals("becomes") || lemma.equals("became") ||
                lemma.equals("prove") || lemma.equals("proves") || lemma.equals("proved") ||
                lemma.equals("look") || lemma.equals("looks") || lemma.equals("looked") ||
                lemma.equals("grow") || lemma.equals("grows") || lemma.equals("grew")
            );
        }
    }

    /**
     * Build test corpus with controlled sentences.
     */
    private List<SentenceDocument> buildAdjModifierCorpus() {
        List<SentenceDocument> sentences = new ArrayList<>();
        int id = 1;

        // POSITIVE: adjective immediately before noun
        // These SHOULD match ADJ_MODIFIER pattern
        addSentence(sentences, id++, "big dog runs", "big", "dog");
        addSentence(sentences, id++, "red house stands", "red", "house");
        addSentence(sentences, id++, "beautiful cat sleeps", "beautiful", "cat");
        addSentence(sentences, id++, "old man walks", "old", "man");
        addSentence(sentences, id++, "young child plays", "young", "child");
        addSentence(sentences, id++, "tall building stands", "tall", "building");
        addSentence(sentences, id++, "small book lies", "small", "book");
        addSentence(sentences, id++, "new car drives", "new", "car");
        addSentence(sentences, id++, "fast horse runs", "fast", "horse");
        addSentence(sentences, id++, "green tree grows", "green", "tree");

        // NEGATIVE: noun before verb (should NOT match ADJ_MODIFIER)
        addSentence(sentences, id++, "dog runs fast", "dog", "runs");
        addSentence(sentences, id++, "cat sleeps soundly", "cat", "sleeps");
        addSentence(sentences, id++, "bird flies high", "bird", "flies");

        // NEGATIVE: adjective AFTER noun (should NOT match - wrong order)
        // Note: "red color" IS actually a valid pattern (red=JJ, color=NN), so we use different examples
        addSentence(sentences, id++, "dog big", "dog", "big");
        addSentence(sentences, id++, "house red", "house", "red");

        // FILLER: random sentences
        for (int i = 0; i < 50; i++) {
            addSentence(sentences, id++, "random words flow here", "random", "words");
        }

        return sentences;
    }

    private List<SentenceDocument> buildNounCompoundCorpus() {
        List<SentenceDocument> sentences = new ArrayList<>();
        int id = 1;

        // POSITIVE: noun immediately before noun (compound)
        addSentence(sentences, id++, "coffee house opens", "coffee", "house");
        addSentence(sentences, id++, "stone wall stands", "stone", "wall");
        addSentence(sentences, id++, "book cover is blue", "book", "cover");
        addSentence(sentences, id++, "car engine runs", "car", "engine");
        addSentence(sentences, id++, "tree branch breaks", "tree", "branch");
        addSentence(sentences, id++, "door handle turns", "door", "handle");
        addSentence(sentences, id++, "eye doctor helps", "eye", "doctor");
        addSentence(sentences, id++, "computer screen shows", "computer", "screen");
        addSentence(sentences, id++, "phone charger works", "phone", "charger");
        addSentence(sentences, id++, "city government decides", "city", "government");

        // NEGATIVE: adjective before noun (not compound)
        addSentence(sentences, id++, "big house stands", "big", "house");
        addSentence(sentences, id++, "red book reads", "red", "book");

        // FILLER
        for (int i = 0; i < 50; i++) {
            addSentence(sentences, id++, "random words flow here", "random", "words");
        }

        return sentences;
    }

    private List<SentenceDocument> buildSubjectOfCorpus() {
        List<SentenceDocument> sentences = new ArrayList<>();
        int id = 1;

        // POSITIVE: noun immediately before verb (subject relation)
        addSentence(sentences, id++, "dog runs fast", "dog", "runs");
        addSentence(sentences, id++, "cat sleeps soundly", "cat", "sleeps");
        addSentence(sentences, id++, "bird flies high", "bird", "flies");
        addSentence(sentences, id++, "fish swims quickly", "fish", "swims");
        addSentence(sentences, id++, "child plays well", "child", "plays");
        addSentence(sentences, id++, "man reads book", "man", "reads");
        addSentence(sentences, id++, "woman writes letter", "woman", "writes");
        addSentence(sentences, id++, "boy kicks ball", "boy", "kicks");
        addSentence(sentences, id++, "girl sees movie", "girl", "sees");
        addSentence(sentences, id++, "teacher teaches students", "teacher", "teaches");

        // NEGATIVE: noun AFTER verb (object, not subject)
        addSentence(sentences, id++, "chases dog quickly", "dog", "chases");
        addSentence(sentences, id++, "kicks ball far", "ball", "kicks");
        addSentence(sentences, id++, "reads book often", "book", "reads");

        // FILLER
        for (int i = 0; i < 50; i++) {
            addSentence(sentences, id++, "random words flow here", "random", "words");
        }

        return sentences;
    }

    private List<SentenceDocument> buildObjectOfCorpus() {
        List<SentenceDocument> sentences = new ArrayList<>();
        int id = 1;

        // POSITIVE: verb immediately before noun (object relation)
        addSentence(sentences, id++, "chases dog quickly", "chases", "dog");
        addSentence(sentences, id++, "kicks ball far", "kicks", "ball");
        addSentence(sentences, id++, "reads book often", "reads", "book");
        addSentence(sentences, id++, "builds house tall", "builds", "house");
        addSentence(sentences, id++, "cooks dinner well", "cooks", "dinner");
        addSentence(sentences, id++, "eats food hungry", "eats", "food");
        addSentence(sentences, id++, "grades papers fair", "grades", "papers");
        addSentence(sentences, id++, "treats patient kind", "treats", "patient");
        addSentence(sentences, id++, "defends client well", "defends", "client");
        addSentence(sentences, id++, "paints picture beautiful", "paints", "picture");

        // NEGATIVE: noun before verb (subject, not object)
        addSentence(sentences, id++, "dog runs fast", "dog", "runs");
        addSentence(sentences, id++, "cat sleeps sound", "cat", "sleeps");
        addSentence(sentences, id++, "bird flies high", "bird", "flies");

        // FILLER
        for (int i = 0; i < 50; i++) {
            addSentence(sentences, id++, "random words flow here", "random", "words");
        }

        return sentences;
    }

    private List<SentenceDocument> buildAdjPredicateCorpus() {
        List<SentenceDocument> sentences = new ArrayList<>();
        int id = 1;

        // POSITIVE: noun + copula + adjective (predicate)
        addSentence(sentences, id++, "dog is big", "dog");
        addSentence(sentences, id++, "theory is correct", "theory");
        addSentence(sentences, id++, "solution seems simple", "solution");
        addSentence(sentences, id++, "child appears happy", "child");
        addSentence(sentences, id++, "house remains empty", "house");
        addSentence(sentences, id++, "data is clear", "data");
        addSentence(sentences, id++, "hypothesis is valid", "hypothesis");
        addSentence(sentences, id++, "result appears positive", "result");
        addSentence(sentences, id++, "system looks stable", "system");
        addSentence(sentences, id++, "theory proves useful", "theory");

        // NEGATIVE: adjective as modifier (not predicate)
        addSentence(sentences, id++, "big dog runs", "big");
        addSentence(sentences, id++, "red house stands", "red");
        addSentence(sentences, id++, "happy child plays", "happy");

        // FILLER
        for (int i = 0; i < 50; i++) {
            addSentence(sentences, id++, "random words flow here", "random");
        }

        return sentences;
    }

    /**
     * Helper to add a sentence with POS tagging.
     */
    private void addSentence(List<SentenceDocument> sentences, int id, String text, String... expectedTokens) {
        String[] words = text.split("\\s+");
        SentenceDocument.Builder builder = SentenceDocument.builder()
                .sentenceId(id)
                .text(text);

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            String pos = guessPOS(word);
            builder.addToken(i, word, word, pos, 0, word.length());
        }

        sentences.add(builder.build());
    }

    /**
     * Simple rule-based POS tagging.
     */
    private String guessPOS(String word) {
        if (word.equals("the") || word.equals("a") || word.equals("an") || word.equals("this") ||
            word.equals("that") || word.equals("these") || word.equals("those")) return "DT";
        if (word.equals("is") || word.equals("are") || word.equals("was") || word.equals("were") ||
            word.equals("be") || word.equals("been") || word.equals("being")) return "VB";
        if (word.equals("seem") || word.equals("seems") || word.equals("seemed") ||
            word.equals("appear") || word.equals("appears") || word.equals("appeared") ||
            word.equals("remain") || word.equals("remains") || word.equals("remained") ||
            word.equals("prove") || word.equals("proves") || word.equals("proved") ||
            word.equals("look") || word.equals("looks") || word.equals("looked") ||
            word.equals("become") || word.equals("becomes") || word.equals("became") ||
            word.equals("grow") || word.equals("grows") || word.equals("grew") ||
            word.equals("runs") || word.equals("sleeps") || word.equals("flies") ||
            word.equals("swims") || word.equals("plays") || word.equals("walks") ||
            word.equals("stands") || word.equals("lies") || word.equals("drives") ||
            word.equals("grows") || word.equals("chases") || word.equals("kicks") ||
            word.equals("sees") || word.equals("see") ||
            word.equals("reads") || word.equals("writes") || word.equals("builds") ||
            word.equals("cooks") || word.equals("eats") || word.equals("grades") ||
            word.equals("treats") || word.equals("defends") || word.equals("paints") ||
            word.equals("helps") || word.equals("shows") || word.equals("works") ||
            word.equals("decides") || word.equals("turns") || word.equals("breaks") ||
            word.equals("teaches") || word.equals("teach")) return "VB";
        if (word.equals("big") || word.equals("red") || word.equals("beautiful") || word.equals("old") ||
            word.equals("young") || word.equals("tall") || word.equals("small") || word.equals("new") ||
            word.equals("correct") || word.equals("simple") || word.equals("happy") || word.equals("empty") ||
            word.equals("clear") || word.equals("valid") || word.equals("positive") || word.equals("stable") ||
            word.equals("useful") || word.equals("blue") || word.equals("fast") || word.equals("green") ||
            word.equals("strong") || word.equals("sound") || word.equals("high") || word.equals("quickly") ||
            word.equals("well") || word.equals("far") || word.equals("hungry") || word.equals("kind") ||
            word.equals("beautiful") || word.equals("fair")) return "JJ";
        return "NN";
    }

    // ==================== TESTS ====================

    @Test
    @DisplayName("ADJ_MODIFIER: Zero false positives and negatives")
    void testAdjModifierPattern() {
        List<SentenceDocument> sentences = buildAdjModifierCorpus();

        // Find matches
        Map<String, Map<String, Long>> found = SimplePatternMatcher.findMatches(
            sentences, SimplePatternMatcher.PatternType.ADJ_MODIFIER);

        // Expected: each noun should have exactly one adjective collocate
        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("dog", Set.of("big"));
        expected.put("house", Set.of("red"));
        expected.put("cat", Set.of("beautiful"));
        expected.put("man", Set.of("old"));
        expected.put("child", Set.of("young"));
        expected.put("building", Set.of("tall"));
        expected.put("book", Set.of("small"));
        expected.put("car", Set.of("new"));
        expected.put("horse", Set.of("fast"));
        expected.put("tree", Set.of("green"));

        // Verify
        System.out.println("ADJ_MODIFIER found: " + found);

        for (String headword : expected.keySet()) {
            Set<String> expectedCollocates = expected.get(headword);
            Map<String, Long> foundCollocates = found.get(headword);

            if (foundCollocates == null) {
                fail("Missing headword: " + headword);
            }

            Set<String> foundSet = foundCollocates.keySet();

            // Check for false positives
            Set<String> falsePositives = new HashSet<>(foundSet);
            falsePositives.removeAll(expectedCollocates);
            assertTrue(falsePositives.isEmpty(),
                "False positives for " + headword + ": " + falsePositives);

            // Check for false negatives
            Set<String> falseNegatives = new HashSet<>(expectedCollocates);
            falseNegatives.removeAll(foundSet);
            assertTrue(falseNegatives.isEmpty(),
                "False negatives for " + headword + ": " + falseNegatives);
        }

        // Verify no extra headwords
        Set<String> extraHeadwords = new HashSet<>(found.keySet());
        extraHeadwords.removeAll(expected.keySet());
        assertTrue(extraHeadwords.isEmpty(), "Extra headwords found: " + extraHeadwords);
    }

    @Test
    @DisplayName("NOUN_COMPOUND: Zero false positives and negatives")
    void testNounCompoundPattern() {
        List<SentenceDocument> sentences = buildNounCompoundCorpus();

        Map<String, Map<String, Long>> found = SimplePatternMatcher.findMatches(
            sentences, SimplePatternMatcher.PatternType.NOUN_COMPOUND);

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("house", Set.of("coffee"));
        expected.put("wall", Set.of("stone"));
        expected.put("cover", Set.of("book"));
        expected.put("engine", Set.of("car"));
        expected.put("branch", Set.of("tree"));
        expected.put("handle", Set.of("door"));
        expected.put("doctor", Set.of("eye"));
        expected.put("screen", Set.of("computer"));
        expected.put("charger", Set.of("phone"));
        expected.put("government", Set.of("city"));

        System.out.println("NOUN_COMPOUND found: " + found);

        for (String headword : expected.keySet()) {
            Set<String> expectedCollocates = expected.get(headword);
            Map<String, Long> foundCollocates = found.get(headword);

            assertNotNull(foundCollocates, "Missing headword: " + headword);

            Set<String> foundSet = foundCollocates.keySet();

            Set<String> falsePositives = new HashSet<>(foundSet);
            falsePositives.removeAll(expectedCollocates);
            assertTrue(falsePositives.isEmpty(),
                "False positives for " + headword + ": " + falsePositives);

            Set<String> falseNegatives = new HashSet<>(expectedCollocates);
            falseNegatives.removeAll(foundSet);
            assertTrue(falseNegatives.isEmpty(),
                "False negatives for " + headword + ": " + falseNegatives);
        }
    }

    @Test
    @DisplayName("SUBJECT_OF: Zero false positives and negatives")
    void testSubjectOfPattern() {
        List<SentenceDocument> sentences = buildSubjectOfCorpus();

        Map<String, Map<String, Long>> found = SimplePatternMatcher.findMatches(
            sentences, SimplePatternMatcher.PatternType.SUBJECT_OF);

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("dog", Set.of("runs"));
        expected.put("cat", Set.of("sleeps"));
        expected.put("bird", Set.of("flies"));
        expected.put("fish", Set.of("swims"));
        expected.put("child", Set.of("plays"));
        expected.put("man", Set.of("reads"));
        expected.put("woman", Set.of("writes"));
        expected.put("boy", Set.of("kicks"));
        expected.put("girl", Set.of("sees"));
        expected.put("teacher", Set.of("teaches"));

        System.out.println("SUBJECT_OF found: " + found);

        for (String headword : expected.keySet()) {
            Set<String> expectedCollocates = expected.get(headword);
            Map<String, Long> foundCollocates = found.get(headword);

            assertNotNull(foundCollocates, "Missing headword: " + headword);

            Set<String> foundSet = foundCollocates.keySet();

            Set<String> falsePositives = new HashSet<>(foundSet);
            falsePositives.removeAll(expectedCollocates);
            assertTrue(falsePositives.isEmpty(),
                "False positives for " + headword + ": " + falsePositives);

            Set<String> falseNegatives = new HashSet<>(expectedCollocates);
            falseNegatives.removeAll(foundSet);
            assertTrue(falseNegatives.isEmpty(),
                "False negatives for " + headword + ": " + falseNegatives);
        }
    }

    @Test
    @DisplayName("OBJECT_OF: Zero false positives and negatives")
    void testObjectOfPattern() {
        List<SentenceDocument> sentences = buildObjectOfCorpus();

        Map<String, Map<String, Long>> found = SimplePatternMatcher.findMatches(
            sentences, SimplePatternMatcher.PatternType.OBJECT_OF);

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("chases", Set.of("dog"));
        expected.put("kicks", Set.of("ball"));
        expected.put("reads", Set.of("book"));
        expected.put("builds", Set.of("house"));
        expected.put("cooks", Set.of("dinner"));
        expected.put("eats", Set.of("food"));
        expected.put("grades", Set.of("papers"));
        expected.put("treats", Set.of("patient"));
        expected.put("defends", Set.of("client"));
        expected.put("paints", Set.of("picture"));

        System.out.println("OBJECT_OF found: " + found);

        for (String headword : expected.keySet()) {
            Set<String> expectedCollocates = expected.get(headword);
            Map<String, Long> foundCollocates = found.get(headword);

            assertNotNull(foundCollocates, "Missing headword: " + headword);

            Set<String> foundSet = foundCollocates.keySet();

            Set<String> falsePositives = new HashSet<>(foundSet);
            falsePositives.removeAll(expectedCollocates);
            assertTrue(falsePositives.isEmpty(),
                "False positives for " + headword + ": " + falsePositives);

            Set<String> falseNegatives = new HashSet<>(expectedCollocates);
            falseNegatives.removeAll(foundSet);
            assertTrue(falseNegatives.isEmpty(),
                "False negatives for " + headword + ": " + falseNegatives);
        }
    }

    @Test
    @DisplayName("ADJ_PREDICATE: Zero false positives and negatives")
    void testAdjPredicatePattern() {
        List<SentenceDocument> sentences = buildAdjPredicateCorpus();

        Map<String, Map<String, Long>> found = SimplePatternMatcher.findMatches(
            sentences, SimplePatternMatcher.PatternType.ADJ_PREDICATE);

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("dog", Set.of("big"));
        expected.put("theory", Set.of("correct", "useful"));
        expected.put("solution", Set.of("simple"));
        expected.put("child", Set.of("happy"));
        expected.put("house", Set.of("empty"));
        expected.put("data", Set.of("clear"));
        expected.put("hypothesis", Set.of("valid"));
        expected.put("result", Set.of("positive"));
        expected.put("system", Set.of("stable"));

        System.out.println("ADJ_PREDICATE found: " + found);

        for (String headword : expected.keySet()) {
            Set<String> expectedCollocates = expected.get(headword);
            Map<String, Long> foundCollocates = found.get(headword);

            assertNotNull(foundCollocates, "Missing headword: " + headword);

            Set<String> foundSet = foundCollocates.keySet();

            Set<String> falsePositives = new HashSet<>(foundSet);
            falsePositives.removeAll(expectedCollocates);
            assertTrue(falsePositives.isEmpty(),
                "False positives for " + headword + ": " + falsePositives);

            Set<String> falseNegatives = new HashSet<>(expectedCollocates);
            falseNegatives.removeAll(foundSet);
            assertTrue(falseNegatives.isEmpty(),
                "False negatives for " + headword + ": " + falseNegatives);
        }
    }

    @Test
    @DisplayName("logDice calculation is correct")
    void testLogDiceCalculation() {
        // Test: dog(10), big(5), cooccurrence(3)
        // logDice = log2(2*3/(10+5)) + 14 = log2(6/15) + 14
        // log2(0.4) = -1.3219... + 14 = 12.678...
        double logDice = LogDiceCalculator.compute(3, 10, 5);
        assertTrue(logDice > 12.6 && logDice < 12.7,
                "logDice should be ~12.68, got: " + logDice);

        // Zero cooccurrence -> 0
        assertEquals(0.0, LogDiceCalculator.compute(0, 10, 5),
            "Zero cooccurrence should return 0");

        // Zero frequency -> 0
        assertEquals(0.0, LogDiceCalculator.compute(5, 0, 10),
            "Zero frequency should return 0");
    }
}
