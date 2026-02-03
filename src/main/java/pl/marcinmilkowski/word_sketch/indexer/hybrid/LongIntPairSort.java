package pl.marcinmilkowski.word_sketch.indexer.hybrid;

/**
 * In-place sort of parallel long/int arrays by key.
 */
public final class LongIntPairSort {

    private LongIntPairSort() {
    }

    public static void sort(long[] keys, int[] values, int from, int toExclusive) {
        quicksort(keys, values, from, toExclusive - 1);
    }

    private static void quicksort(long[] keys, int[] values, int left, int right) {
        while (left < right) {
            int i = left;
            int j = right;
            long pivot = keys[left + ((right - left) >>> 1)];

            while (i <= j) {
                while (keys[i] < pivot) i++;
                while (keys[j] > pivot) j--;
                if (i <= j) {
                    swap(keys, values, i, j);
                    i++;
                    j--;
                }
            }

            // Tail recursion elimination: sort smaller partition first
            if (j - left < right - i) {
                if (left < j) quicksort(keys, values, left, j);
                left = i;
            } else {
                if (i < right) quicksort(keys, values, i, right);
                right = j;
            }
        }
    }

    private static void swap(long[] keys, int[] values, int i, int j) {
        long tk = keys[i];
        keys[i] = keys[j];
        keys[j] = tk;

        int tv = values[i];
        values[i] = values[j];
        values[j] = tv;
    }
}
