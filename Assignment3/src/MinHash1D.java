import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class MinHash1D {
    private MinHash1D(){
    }

    /**
     * Construct the table of hash values needed to construct the signature matrix.
     * Position (i,j) contains the result of applying function j to row number i.
     * @param numHashes number of hashes that will be used in the signature matrix
     * @param numValues number of unique values that occur in the object set representations (i.e. number of rows of the characteristic matrix)
     * @param seed should be used to generate any random numbers needed
     * @return the (numValues x numHashes) matrix of hash values
     */
    public static int[] constructHashTable1D(int numHashes, int numValues, int seed) {
        Random rand = new Random(seed);
        int p = Primes.findLeastPrimeNumber(numValues);
        int[] a = new int[numHashes], b = new int[numHashes];
        for (int j = 0; j < numHashes; j++) {
            a[j] = rand.nextInt(Integer.MAX_VALUE);
            b[j] = rand.nextInt(Integer.MAX_VALUE);
        }
        int[] hashes = new int[numValues * numHashes];
        for (int i = 0; i < numValues; i++) {
            for (int j = 0; j < numHashes; j++) {
                int idx = i * numHashes + j;
                hashes[idx] = (int)((((long) a[j] * i + b[j]) % p) % numValues);
            }
        }
        return hashes;
    }

    /**
     * Construct the signature matrix.
     *
     * @param reader iterator returning the set represenation of objects for which the signature matrix should be constructed
     * @param hashValues (numValues x numHashes) matrix of hash values
     * @return the (numHashes x numObjects) signature matrix
     */
    public static int[] constructSignatureMatrix1D(
            Reader reader,
            int[] hashValues1D,
            int numHashes,
            int numDocs) {

        // Reset the reader 
        reader.reset();

        // Allocate and initialize signature array
        int[] signature1D = new int[numDocs * numHashes];
        Arrays.fill(signature1D, Integer.MAX_VALUE);

        // For each document d, for each hash function h, find the min-hash
        for (int d = 0; d < numDocs; d++) {
            Set<Integer> shingledDoc = reader.next();
            final int docOffset = d * numHashes; // Start position for doc's hashes

            for (int shingle : shingledDoc){
                final int hashOffset = shingle * numHashes; // Start of this shingle's hash values

                for (int h = 0; h < numHashes; h++) {
                    final int currentHash = hashValues1D[hashOffset + h];
                    if (currentHash < signature1D[docOffset + h]) {
                        signature1D[docOffset + h] = currentHash;
                    }
                }

            }
        }
        return signature1D;
    }
}