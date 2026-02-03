package pl.marcinmilkowski.word_sketch.indexer.hybrid;

/**
 * A small reusable int[] buffer to avoid allocations in tight loops.
 */
public final class IntArrayBuffer {

    private int[] array;
    private int size;

    public IntArrayBuffer(int initialCapacity) {
        this.array = new int[Math.max(0, initialCapacity)];
        this.size = 0;
    }

    public IntArrayBuffer() {
        this(32);
    }

    public int[] array() {
        return array;
    }

    public int size() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void ensureCapacity(int capacity) {
        if (array.length >= capacity) {
            return;
        }
        int newCap = Math.max(capacity, array.length * 2 + 1);
        int[] n = new int[newCap];
        System.arraycopy(array, 0, n, 0, size);
        array = n;
    }
}
