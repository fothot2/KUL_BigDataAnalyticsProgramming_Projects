import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

public class RadixSort {
    private static final int cutoff = 5; // cutoff for MSD algorithm
    /////////////////////// START OF HELPER FUNCTIONS ///////////////////////////
    // Helper method to get the ASCII integer value at a specific digit of a string

    public static Integer getASCIIInteger(String s, Integer digit) {
        if (s.length() - 1 < digit) {
            return -1;
        }
        return (int) s.charAt(digit);
    }

    // for lsd the call will be with start = 0 and end = arr.length
    // Helper countsort function to avoid repetition.
    public static void countSort(String[] arr, int start, int end, int digit) {
        int[] countArray = new int[129]; // count array for countsort. Fixed size since we know we only have ASCII + outofbounds
        String[] output = new String[end - start];

        // store the count of each unique element of the input array at their respective
        // indices
        for (int i = start; i < end; i++) {
            countArray[getASCIIInteger(arr[i], digit) + 1]++;
        }

        // Cumsum to get indexes
        for (int i = 1; i < countArray.length; i++) {
            countArray[i] += countArray[i - 1];
        }

        // Map strings to indexes. Start from end 
        for (int i = end - 1; i >= start; i--) {
            int index = getASCIIInteger(arr[i], digit) + 1;
            output[countArray[index] - 1] = arr[i];
            countArray[index]--;
        }

        // Copy sorted subarray back into the original array
        System.arraycopy(output, 0, arr, start, output.length);
    }

    private static void insertionSort(String[] a, int lo, int hi, int d) {
        for (int i = lo; i <= hi; i++) {
            for (int j = i; j > lo; j--) {
                boolean isLess = false;
                String right = a[j];
                String left = a[j - 1];

                // check string character per character to see if right is bigger
                for (int k = d; k < Math.min(right.length(), left.length()); k++) {
                    if (right.charAt(k) != left.charAt(k)) {
                        // Break when found different character, return true on smaller 
                        isLess = right.charAt(k) < left.charAt(k);
                        break;
                    }
                }
                if (!isLess && right.length() >= left.length()) {
                    break; // We have exhausted left and is stil smaller. Exit (ensure stability)
                }

                // Swap in any other case
                String temp = a[j];
                a[j] = a[j - 1];
                a[j - 1] = temp;
            }
        }
    }
    /////////////////////// END OF HELPER FUNCTIONS ///////////////////////////

    /**
     * Sorts an array of strings using the Radix Sort algorithm.
     *
     * @param arr the array of strings to be sorted
     * @return the sorted array of strings
     */
    public static String[] radixSort(String[] arr) {
        // Find out maximum length of strings = find loop scope.
        int maxLength = Stream.of(arr).map(String::length).max(Integer::compareTo).get();

        for (int digit = maxLength - 1; digit >= 0; digit--) {
            countSort(arr, 0, arr.length, digit);
        }
        return arr;
    }

    // function that will be called and will return string
    public static String[] msdRadixSort(String[] arr) {
        // Create an output array of the same size as the input array
        String[] output = new String[arr.length];
        System.arraycopy(arr, 0, output, 0, arr.length);

        // Start sorting from the most significant digit (index 0)
        msdRadixSortHelper(output, 0, arr.length, 0);
        return output;
    }

    // Due to recursive nature of the algo this helper was created
    // to have the same return object for the 2 Radix sorts
    private static void msdRadixSortHelper(String[] arr, int start, int end, int digit) {
        // Check if reached the cutoff threshold and switch to insertionSort
        if (end <= start + cutoff) {
            insertionSort(arr, end, start, digit);
            return;
        }

        // count array for countsort. Fixed size since we know we only have ASCII + outofbounds
        int[] countArray = new int[129];

        countSort(arr, start, end, digit);

        // Recursively sort each bucket
        for (int i = 0; i < countArray.length - 1; i++) {
            int left = start + countArray[i];
            int right = start + countArray[i + 1] - 1;
            if (right > left) {
                msdRadixSortHelper(arr, left, right, digit + 1);
            }
        }
    }

    //////////////////////////// HELPER FUNCS FOR TESTING ////////////////////////
    public static String[] generateRandomArray(int size) {
        Random random = new Random();
        String[] arr = new String[size];
        for (int i = 0; i < size; i++) {
            arr[i] = generateRandomString(random, 1 + random.nextInt(25)); // Strings of random length (1–25)
        }
        return arr;
    }

    // Helper generateRandomString func to test efficacy of algorithms
    private static String generateRandomString(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = (char) (random.nextInt(26) + (random.nextBoolean() ? 'a' : 'A')); // Random upper/lowercase letter
            sb.append(c);
        }
        return sb.toString();
    }

    //addArrays and average Array are being used to mainpulate results table
    public static long[] addArrays(long[] arr1, long[] arr2) {
        for (int i = 0; i < arr1.length; i++) {
            arr1[i] += arr2[i];
        }
        return arr1;
    }

    public static long[] averageArray(long[] arr, int divisor) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] /= divisor;
        }
        return arr;
    }

    public static long[] testForString(String[] arr) {
        long startTime = System.nanoTime();
        radixSort(arr);
        long estimatedTimeLSD = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        msdRadixSort(arr);
        long estimatedTimeMSD = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        Arrays.sort(arr);
        long estimatedTimeJava = System.nanoTime() - startTime;

        return new long[] {estimatedTimeLSD, estimatedTimeMSD, estimatedTimeJava};
    }

    public static void printResults(String[] arr, long[] results) {
        System.out.println("\nResults after 10 repetitions:");
        System.out.println("Sorted array (first 10 elements): " + Arrays.toString(Arrays.copyOf(arr, Math.min(arr.length, 10))) + " ...");
        System.out.println("Sorted in " + results[0] + " nanoseconds with in-house LSD implementation.");
        System.out.println("Sorted in " + results[1] + " nanoseconds with in-house MSD implementation (cutoff = " + cutoff + ").");
        System.out.println("Sorted in " + results[2] + " nanoseconds with Java implementation.");
    }
    ///////////////////////END OF HELPER FUNCS FOR TESTING ////////////////////////

    public static void main(String[] args) {
        String[][] arrays = {
            { "apple", "banana", "pear", "kiwi", "avocado" },
            { "c3e", "bfr", "aAD", "Abb", "Rtr", "Ast", "ASb", "Arv", "ASf", "rr", "gtz", "tgk", "mdf",
              "wfrf", "rfrx", "nrfr", "ffro", "srf", "ufrf", "frry" },
            generateRandomArray(10000)
        };

        long[][] results = new long[arrays.length][3];
        int repetitions = 10;

        // Run tests and calculate averages
        for (int i = 0; i < arrays.length; i++) {
            for (int j = 0; j < repetitions; j++) {
                results[i] = addArrays(results[i], testForString(arrays[i]));
            }
            results[i] = averageArray(results[i], repetitions);
        }

        // Print results for each array
        for (int i = 0; i < arrays.length; i++) {
            printResults(arrays[i], results[i]);
        }
    }
}
