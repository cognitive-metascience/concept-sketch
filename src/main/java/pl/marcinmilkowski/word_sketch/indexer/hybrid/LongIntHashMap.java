package pl.marcinmilkowski.word_sketch.indexer.hybrid;

import java.util.Arrays;

/**
 * Primitive hash map from long -> int using open addressing.
 *
 * Designed for aggregation (counts) during collocations scan.
 */
public final class LongIntHashMap {

    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys;
    private int[] values;
    private int size;
    private int mask;
    private int resizeAt;

    public LongIntHashMap(int expectedSize) {
        int cap = 1;
        int need = Math.max(4, (int) (expectedSize / 0.65) + 1);
        while (cap < need) cap <<= 1;
        init(cap);
    }

    public LongIntHashMap() {
        this(1024);
    }

    private void init(int capacity) {
        keys = new long[capacity];
        values = new int[capacity];
        Arrays.fill(keys, EMPTY);
        size = 0;
        mask = capacity - 1;
        resizeAt = (int) (capacity * 0.65);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        Arrays.fill(keys, EMPTY);
        Arrays.fill(values, 0);
        size = 0;
    }

    public void addTo(long key, int delta) {
        if (key == EMPTY) {
            throw new IllegalArgumentException("Key cannot be Long.MIN_VALUE");
        }
        if (size >= resizeAt) {
            rehash(keys.length * 2);
        }

        int slot = mix64(key) & mask;
        while (true) {
            long k = keys[slot];
            if (k == EMPTY) {
                keys[slot] = key;
                values[slot] = delta;
                size++;
                return;
            }
            if (k == key) {
                values[slot] += delta;
                return;
            }
            slot = (slot + 1) & mask;
        }
    }

    public void forEach(EntryConsumer consumer) {
        for (int i = 0; i < keys.length; i++) {
            long k = keys[i];
            if (k != EMPTY) {
                consumer.accept(k, values[i]);
            }
        }
    }

    public long[] keysArray() {
        return keys;
    }

    public int[] valuesArray() {
        return values;
    }

    public int capacity() {
        return keys.length;
    }

    private void rehash(int newCapacity) {
        long[] oldKeys = keys;
        int[] oldValues = values;

        init(newCapacity);

        for (int i = 0; i < oldKeys.length; i++) {
            long k = oldKeys[i];
            if (k == EMPTY) continue;
            int v = oldValues[i];

            int slot = mix64(k) & mask;
            while (keys[slot] != EMPTY) {
                slot = (slot + 1) & mask;
            }
            keys[slot] = k;
            values[slot] = v;
            size++;
        }
    }

    // Murmur3 finalizer-like mix; returns int hash
    private static int mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }

    @FunctionalInterface
    public interface EntryConsumer {
        void accept(long key, int value);
    }
}
