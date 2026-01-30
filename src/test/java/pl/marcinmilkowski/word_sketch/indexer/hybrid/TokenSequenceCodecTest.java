package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TokenSequenceCodec.
 */
class TokenSequenceCodecTest {

    @Test
    @DisplayName("Encode and decode empty token list")
    void encodeDecodeEmpty() throws IOException {
        List<SentenceDocument.Token> tokens = new ArrayList<>();
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        List<SentenceDocument.Token> decoded = TokenSequenceCodec.decode(encoded);
        
        assertTrue(decoded.isEmpty());
    }

    @Test
    @DisplayName("Encode and decode single token")
    void encodeDecodeSingleToken() throws IOException {
        List<SentenceDocument.Token> tokens = List.of(
            new SentenceDocument.Token(0, "Hello", "hello", "UH", 0, 5)
        );
        
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        List<SentenceDocument.Token> decoded = TokenSequenceCodec.decode(encoded);
        
        assertEquals(1, decoded.size());
        assertEquals("Hello", decoded.get(0).word());
        assertEquals("hello", decoded.get(0).lemma());
        assertEquals("UH", decoded.get(0).tag());
        assertEquals(0, decoded.get(0).position());
        assertEquals(0, decoded.get(0).startOffset());
        assertEquals(5, decoded.get(0).endOffset());
    }

    @Test
    @DisplayName("Encode and decode multiple tokens")
    void encodeDecodeMultipleTokens() throws IOException {
        List<SentenceDocument.Token> tokens = List.of(
            new SentenceDocument.Token(0, "The", "the", "DT", 0, 3),
            new SentenceDocument.Token(1, "quick", "quick", "JJ", 4, 9),
            new SentenceDocument.Token(2, "brown", "brown", "JJ", 10, 15),
            new SentenceDocument.Token(3, "fox", "fox", "NN", 16, 19),
            new SentenceDocument.Token(4, "jumps", "jump", "VBZ", 20, 25)
        );
        
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        List<SentenceDocument.Token> decoded = TokenSequenceCodec.decode(encoded);
        
        assertEquals(5, decoded.size());
        
        // Verify each token
        for (int i = 0; i < tokens.size(); i++) {
            assertEquals(tokens.get(i).position(), decoded.get(i).position());
            assertEquals(tokens.get(i).word(), decoded.get(i).word());
            assertEquals(tokens.get(i).lemma(), decoded.get(i).lemma());
            assertEquals(tokens.get(i).tag(), decoded.get(i).tag());
            assertEquals(tokens.get(i).startOffset(), decoded.get(i).startOffset());
            assertEquals(tokens.get(i).endOffset(), decoded.get(i).endOffset());
        }
    }

    @Test
    @DisplayName("Handle null and empty strings")
    void handleNullAndEmptyStrings() throws IOException {
        List<SentenceDocument.Token> tokens = List.of(
            new SentenceDocument.Token(0, "", "", "", 0, 0),
            new SentenceDocument.Token(1, "word", null, "NN", 1, 5)
        );
        
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        List<SentenceDocument.Token> decoded = TokenSequenceCodec.decode(encoded);
        
        assertEquals(2, decoded.size());
        assertEquals("", decoded.get(0).word());
        assertEquals("", decoded.get(0).lemma());
    }

    @Test
    @DisplayName("Get token at specific position")
    void getTokenAtPosition() throws IOException {
        List<SentenceDocument.Token> tokens = List.of(
            new SentenceDocument.Token(0, "The", "the", "DT", 0, 3),
            new SentenceDocument.Token(1, "cat", "cat", "NN", 4, 7),
            new SentenceDocument.Token(2, "sat", "sit", "VBD", 8, 11)
        );
        
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        
        // Get existing positions
        SentenceDocument.Token token0 = TokenSequenceCodec.getTokenAtPosition(encoded, 0);
        assertNotNull(token0);
        assertEquals("The", token0.word());
        
        SentenceDocument.Token token1 = TokenSequenceCodec.getTokenAtPosition(encoded, 1);
        assertNotNull(token1);
        assertEquals("cat", token1.word());
        
        SentenceDocument.Token token2 = TokenSequenceCodec.getTokenAtPosition(encoded, 2);
        assertNotNull(token2);
        assertEquals("sat", token2.word());
        
        // Non-existent position
        SentenceDocument.Token token3 = TokenSequenceCodec.getTokenAtPosition(encoded, 3);
        assertNull(token3);
    }

    @Test
    @DisplayName("Get tokens in range")
    void getTokensInRange() throws IOException {
        List<SentenceDocument.Token> tokens = List.of(
            new SentenceDocument.Token(0, "The", "the", "DT", 0, 3),
            new SentenceDocument.Token(1, "quick", "quick", "JJ", 4, 9),
            new SentenceDocument.Token(2, "brown", "brown", "JJ", 10, 15),
            new SentenceDocument.Token(3, "fox", "fox", "NN", 16, 19),
            new SentenceDocument.Token(4, "jumps", "jump", "VBZ", 20, 25)
        );
        
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        
        // Get range [1, 3]
        List<SentenceDocument.Token> range = TokenSequenceCodec.getTokensInRange(encoded, 1, 3);
        assertEquals(3, range.size());
        assertEquals("quick", range.get(0).word());
        assertEquals("brown", range.get(1).word());
        assertEquals("fox", range.get(2).word());
        
        // Get range [0, 1]
        range = TokenSequenceCodec.getTokensInRange(encoded, 0, 1);
        assertEquals(2, range.size());
        
        // Get range outside bounds
        range = TokenSequenceCodec.getTokensInRange(encoded, 10, 20);
        assertTrue(range.isEmpty());
    }

    @Test
    @DisplayName("Handle Unicode characters")
    void handleUnicode() throws IOException {
        List<SentenceDocument.Token> tokens = List.of(
            new SentenceDocument.Token(0, "日本語", "日本語", "NN", 0, 3),
            new SentenceDocument.Token(1, "café", "café", "NN", 4, 8),
            new SentenceDocument.Token(2, "über", "über", "JJ", 9, 13)
        );
        
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        List<SentenceDocument.Token> decoded = TokenSequenceCodec.decode(encoded);
        
        assertEquals(3, decoded.size());
        assertEquals("日本語", decoded.get(0).word());
        assertEquals("café", decoded.get(1).word());
        assertEquals("über", decoded.get(2).word());
    }

    @Test
    @DisplayName("Estimate size is reasonable")
    void estimateSizeIsReasonable() {
        List<SentenceDocument.Token> tokens = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tokens.add(new SentenceDocument.Token(i, "word" + i, "lemma" + i, "NN", i * 5, i * 5 + 4));
        }
        
        int estimated = TokenSequenceCodec.estimateSize(tokens);
        
        // Should be reasonable (not too small, not too large)
        assertTrue(estimated > 100, "Estimate too small: " + estimated);
        assertTrue(estimated < 2000, "Estimate too large: " + estimated);
    }

    @Test
    @DisplayName("Decode null BytesRef returns empty list")
    void decodeNullReturnsEmpty() throws IOException {
        List<SentenceDocument.Token> decoded = TokenSequenceCodec.decode(null);
        assertTrue(decoded.isEmpty());
    }

    @Test
    @DisplayName("Large token sequence roundtrip")
    void largeTokenSequence() throws IOException {
        // Simulate a long sentence with 100 tokens
        List<SentenceDocument.Token> tokens = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(new SentenceDocument.Token(
                i, 
                "word" + i, 
                "lemma" + i, 
                i % 2 == 0 ? "NN" : "VB", 
                i * 10, 
                i * 10 + 6
            ));
        }
        
        BytesRef encoded = TokenSequenceCodec.encode(tokens);
        List<SentenceDocument.Token> decoded = TokenSequenceCodec.decode(encoded);
        
        assertEquals(100, decoded.size());
        
        // Verify first and last
        assertEquals("word0", decoded.get(0).word());
        assertEquals("word99", decoded.get(99).word());
        assertEquals(0, decoded.get(0).position());
        assertEquals(99, decoded.get(99).position());
    }
}
