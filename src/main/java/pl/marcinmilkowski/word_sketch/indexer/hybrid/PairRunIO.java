package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spill run file I/O for sorted (pairKey,count) records.
 */
public final class PairRunIO {

    public static final int MAGIC = 0x50414952; // "PAIR"
    public static final int VERSION = 1;

    private PairRunIO() {
    }

    public static void writeRun(Path path, long[] keys, int[] values, int recordCount) throws IOException {
        Files.createDirectories(path.getParent());
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
            dos.writeInt(MAGIC);
            dos.writeInt(VERSION);
            dos.writeInt(recordCount);
            for (int i = 0; i < recordCount; i++) {
                dos.writeLong(keys[i]);
                dos.writeInt(values[i]);
            }
        }
    }

    public static RunCursor openCursor(Path path) throws IOException {
        return new RunCursor(path);
    }

    public static final class RunCursor implements AutoCloseable {
        private final DataInputStream dis;
        private int remaining;
        public long key;
        public int value;

        public RunCursor(Path path) throws IOException {
            this.dis = new DataInputStream(new BufferedInputStream(new FileInputStream(path.toFile())));
            int magic = dis.readInt();
            if (magic != MAGIC) {
                throw new IOException("Bad run file magic: " + path);
            }
            int version = dis.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported run file version: " + version);
            }
            this.remaining = dis.readInt();
        }

        public boolean advance() throws IOException {
            if (remaining <= 0) {
                return false;
            }
            try {
                key = dis.readLong();
                value = dis.readInt();
                remaining--;
                return true;
            } catch (EOFException eof) {
                remaining = 0;
                return false;
            }
        }

        @Override
        public void close() throws IOException {
            dis.close();
        }
    }
}
