package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LemmaIdsCodecTest {

    @Test
    @DisplayName("LemmaIdsCodec encodes and decodes round-trip")
    void roundTrip() throws Exception {
        int[] ids = new int[] {0, 1, 2, 127, 128, 16384};
        BytesRef encoded = LemmaIdsCodec.encode(ids);
        int[] decoded = LemmaIdsCodec.decode(encoded);
        assertArrayEquals(ids, decoded);
    }

    @Test
    @DisplayName("LemmaIdsCodec handles empty")
    void empty() throws Exception {
        BytesRef encoded = LemmaIdsCodec.encode(new int[0]);
        assertEquals(0, encoded.length);
        assertArrayEquals(new int[0], LemmaIdsCodec.decode(encoded));
    }
}
