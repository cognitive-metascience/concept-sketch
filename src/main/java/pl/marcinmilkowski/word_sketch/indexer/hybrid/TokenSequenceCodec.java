package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.apache.lucene.util.BytesRef;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for encoding/decoding token sequences into binary format for DocValues.
 * 
 * This enables efficient storage and retrieval of all tokens in a sentence
 * as a single binary DocValues field, supporting O(1) position-based lookups.
 * 
 * Binary format per token:
 * - position: varint (1-5 bytes)
 * - word length: varint (1-2 bytes)
 * - word: UTF-8 bytes
 * - lemma length: varint (1-2 bytes)
 * - lemma: UTF-8 bytes
 * - tag length: varint (1-2 bytes)
 * - tag: UTF-8 bytes
 * - startOffset: varint (1-5 bytes)
 * - endOffset: varint (1-5 bytes)
 * 
 * Typical sentence (~20 tokens) encodes to ~400-600 bytes.
 */
public class TokenSequenceCodec {

    /**
     * Encodes a list of tokens into a binary format suitable for DocValues.
     * 
     * @param tokens The tokens to encode
     * @return BytesRef containing the encoded token sequence
     */
    public static BytesRef encode(List<SentenceDocument.Token> tokens) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(tokens.size() * 30);
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Write token count
        writeVarInt(dos, tokens.size());
        
        for (SentenceDocument.Token token : tokens) {
            writeVarInt(dos, token.position());
            writeString(dos, token.word());
            writeString(dos, token.lemma());
            writeString(dos, token.tag());
            writeVarInt(dos, token.startOffset());
            writeVarInt(dos, token.endOffset());
        }
        
        dos.flush();
        return new BytesRef(baos.toByteArray());
    }

    /**
     * Decodes a binary token sequence back to a list of tokens.
     * 
     * @param bytesRef The encoded token sequence
     * @return List of decoded tokens
     */
    public static List<SentenceDocument.Token> decode(BytesRef bytesRef) throws IOException {
        if (bytesRef == null || bytesRef.length == 0) {
            return new ArrayList<>();
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(
            bytesRef.bytes, bytesRef.offset, bytesRef.length);
        DataInputStream dis = new DataInputStream(bais);
        
        int tokenCount = readVarInt(dis);
        List<SentenceDocument.Token> tokens = new ArrayList<>(tokenCount);
        
        for (int i = 0; i < tokenCount; i++) {
            int position = readVarInt(dis);
            String word = readString(dis);
            String lemma = readString(dis);
            String tag = readString(dis);
            int startOffset = readVarInt(dis);
            int endOffset = readVarInt(dis);
            
            tokens.add(new SentenceDocument.Token(position, word, lemma, tag, startOffset, endOffset));
        }
        
        return tokens;
    }

    /**
     * Gets a token at a specific position without decoding all tokens.
     * This is an optimization for when we only need one token.
     * 
     * @param bytesRef The encoded token sequence
     * @param targetPosition The position to look up
     * @return The token at that position, or null if not found
     */
    public static SentenceDocument.Token getTokenAtPosition(BytesRef bytesRef, int targetPosition) 
            throws IOException {
        if (bytesRef == null || bytesRef.length == 0) {
            return null;
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(
            bytesRef.bytes, bytesRef.offset, bytesRef.length);
        DataInputStream dis = new DataInputStream(bais);
        
        int tokenCount = readVarInt(dis);
        
        for (int i = 0; i < tokenCount; i++) {
            int position = readVarInt(dis);
            String word = readString(dis);
            String lemma = readString(dis);
            String tag = readString(dis);
            int startOffset = readVarInt(dis);
            int endOffset = readVarInt(dis);
            
            if (position == targetPosition) {
                return new SentenceDocument.Token(position, word, lemma, tag, startOffset, endOffset);
            }
            
            // Optimization: if positions are sorted and we've passed the target, stop
            if (position > targetPosition) {
                return null;
            }
        }
        
        return null;
    }

    /**
     * Gets tokens within a position range (inclusive).
     * Useful for extracting collocates within a window.
     * 
     * @param bytesRef The encoded token sequence
     * @param startPos Start position (inclusive)
     * @param endPos End position (inclusive)
     * @return List of tokens in the range
     */
    public static List<SentenceDocument.Token> getTokensInRange(BytesRef bytesRef, 
            int startPos, int endPos) throws IOException {
        if (bytesRef == null || bytesRef.length == 0) {
            return new ArrayList<>();
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(
            bytesRef.bytes, bytesRef.offset, bytesRef.length);
        DataInputStream dis = new DataInputStream(bais);
        
        int tokenCount = readVarInt(dis);
        List<SentenceDocument.Token> result = new ArrayList<>();
        
        for (int i = 0; i < tokenCount; i++) {
            int position = readVarInt(dis);
            String word = readString(dis);
            String lemma = readString(dis);
            String tag = readString(dis);
            int startOffset = readVarInt(dis);
            int endOffset = readVarInt(dis);
            
            if (position >= startPos && position <= endPos) {
                result.add(new SentenceDocument.Token(position, word, lemma, tag, startOffset, endOffset));
            }
            
            // Optimization: if positions are sorted and we've passed the end, stop
            if (position > endPos) {
                break;
            }
        }
        
        return result;
    }

    // Variable-length integer encoding (similar to Protocol Buffers varint)
    private static void writeVarInt(DataOutputStream dos, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            dos.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        dos.writeByte(value);
    }

    private static int readVarInt(DataInputStream dis) throws IOException {
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = dis.readByte() & 0xFF;
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    private static void writeString(DataOutputStream dos, String s) throws IOException {
        if (s == null || s.isEmpty()) {
            writeVarInt(dos, 0);
        } else {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            writeVarInt(dos, bytes.length);
            dos.write(bytes);
        }
    }

    private static String readString(DataInputStream dis) throws IOException {
        int len = readVarInt(dis);
        if (len == 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Estimates the encoded size in bytes for a list of tokens.
     * Useful for buffer pre-allocation.
     */
    public static int estimateSize(List<SentenceDocument.Token> tokens) {
        int size = 5; // token count varint
        for (SentenceDocument.Token token : tokens) {
            size += 5; // position varint
            size += 2 + (token.word() != null ? token.word().length() * 2 : 0);
            size += 2 + (token.lemma() != null ? token.lemma().length() * 2 : 0);
            size += 2 + (token.tag() != null ? token.tag().length() : 0);
            size += 10; // offsets
        }
        return size;
    }
}
