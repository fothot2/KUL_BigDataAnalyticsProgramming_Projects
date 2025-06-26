import java.util.Arrays;

public class IntBucket {
    private int[] data = new int[4];
    private int size = 0;
    void add(int x) {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = x;
    }
    void clear() {
        size = 0;
    }
    int size() {
        return size;
    }
    int[] data() {
        return data;
    }
}
