package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import org.apache.lucene.util.BytesRef;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Encodes/decodes per-sentence lemma ID sequences for BinaryDocValues.
 *
 * Format:
 * - tokenCount: varint
 * - lemmaId[tokenCount]: varint each
 */
public final class LemmaIdsCodec {

    private LemmaIdsCodec() {
    }

    public static BytesRef encode(int[] lemmaIds) throws IOException {
        if (lemmaIds == null || lemmaIds.length == 0) {
            return new BytesRef(new byte[0]);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(16, lemmaIds.length * 2));
        DataOutputStream dos = new DataOutputStream(baos);

        writeVarInt(dos, lemmaIds.length);
        for (int id : lemmaIds) {
            if (id < 0) {
                throw new IOException("Negative lemmaId: " + id);
            }
            writeVarInt(dos, id);
        }

        dos.flush();
        return new BytesRef(baos.toByteArray());
    }

    public static int[] decode(BytesRef bytesRef) throws IOException {
        if (bytesRef == null || bytesRef.length == 0) {
            return new int[0];
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(bytesRef.bytes, bytesRef.offset, bytesRef.length);
        DataInputStream dis = new DataInputStream(bais);

        int tokenCount;
        try {
            tokenCount = readVarInt(dis);
        } catch (EOFException eof) {
            return new int[0];
        }

        int[] ids = new int[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            ids[i] = readVarInt(dis);
        }
        return ids;
    }

    /**
     * Decodes into a reusable buffer to avoid per-document allocations.
     *
     * @return number of decoded IDs written into {@code out} (use {@code out.array()[0..len)}).
     */
    public static int decodeTo(BytesRef bytesRef, IntArrayBuffer out) throws IOException {
        if (bytesRef == null || bytesRef.length == 0) {
            out.setSize(0);
            return 0;
        }

        byte[] bytes = bytesRef.bytes;
        int pos = bytesRef.offset;
        int limit = bytesRef.offset + bytesRef.length;

        long r = readVarIntWithPos(bytes, pos, limit);
        int tokenCount = (int) r;
        pos = (int) (r >>> 32);

        out.ensureCapacity(tokenCount);
        int[] arr = out.array();
        for (int i = 0; i < tokenCount; i++) {
            r = readVarIntWithPos(bytes, pos, limit);
            arr[i] = (int) r;
            pos = (int) (r >>> 32);
        }
        out.setSize(tokenCount);
        return tokenCount;
    }

    // --- Fast byte-array varint decoding ---
    private static long readVarIntWithPos(byte[] bytes, int pos, int limit) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            if (pos >= limit) {
                throw new EOFException();
            }
            int b = bytes[pos++] & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return ((long) pos << 32) | (result & 0xffffffffL);
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        }
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
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        } while ((b & 0x80) != 0);
        return result;
    }
}
