import java.util.Vector;

public class BloomFilter {
    
    /**
     * This class implements a bloom filter with k hash functions and m bits.
     * The bloom filter is used to check if an element is in a set or not.
     * The bloom filter can have false positives but no false negatives.
     */

    private int logNumBits; // log of the number of bits
    private int k; // number of hash functions
    private Vector<Integer> B; //"Bit" array B
    private int bits; //number of bits

    /**
     * Constructs a BloomFilter object with the specified number of bits and hash functions.
     *
     * @param logNumBits the log of the number of bits in the bloom filter
     * @param k the number of hash functions to be used
     */
    public BloomFilter(int logNumBits, int k) {
        
        this.logNumBits = logNumBits;
        this.k = k;
        this.bits = (int)Math.pow(2, logNumBits);
        this.B = new Vector<>(bits);

        for(int i = 0; i < bits; i++){
            B.add(0);
        }
    }

    /**
     * Adds the specified element to the bloom filter.
     *
     * @param s the element to be added
     */
    public void add(String s) {
        for(int i = 0; i < this.k; i++){
            //Parse to int to get index and set B[H_i] = 1
            B.set(getIndex(s, i * i), 1);
        }
    }

    /**
     * Checks if the specified element is present in the bloom filter.
     *
     * @param s the element to be checked
     * @return true if the element is present, false otherwise
     */
    public boolean check(String s) {
        for(int i = 0; i < this.k; i++){
            if (B.get(getIndex(s, i * i)) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the index of the specified element in the bloom filter.
     *
     * @param element the element to get the index for
     * @param seed the seed value for the hash function
     * @return the index of the element in the bloom filter
     */
    private int getIndex(String element, int seed) {
        Integer hash = MurmurHash.hash32(element, seed);

        // Convert hash to binarized string in order to manipulate it
        String hashToBin = Integer.toBinaryString(hash);
        // truncate the number to the 2^lognumBits bits 
        Integer lowerBound = Math.max(0, hashToBin.length() - logNumBits);
        hashToBin = hashToBin.substring(lowerBound, hashToBin.length());

        hash = Integer.parseInt(hashToBin, 2);

        return hash;
    }
}
