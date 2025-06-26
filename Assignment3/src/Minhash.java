/**
 * Copyright (c) DTAI - KU Leuven – All rights reserved. Proprietary, do not
 * copy or distribute without permission. Written by Pieter Robberechts, 2025
 */

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Class for computing MinHash signatures.
 */
public final class Minhash {
    private Minhash(){
    }

    /**
     * Construct the table of hash values needed to construct the signature matrix.
     * Position (i,j) contains the result of applying function j to row number i.
     * @param numHashes number of hashes that will be used in the signature matrix
     * @param numValues number of unique values that occur in the object set representations (i.e. number of rows of the characteristic matrix)
     * @param seed should be used to generate any random numbers needed
     * @return the (numValues x numHashes) matrix of hash values
     */
    public static int[][] constructHashTable(int numHashes, int numValues, int seed) {
        Random rand1 = new Random(seed);
        // Initialize p, a and b
        // a and b will be consistent per hash function
        int p = Primes.findLeastPrimeNumber(numValues);
        int[] a = new int[numHashes];
        int[] b = new int[numHashes];
        for (int j = 0; j < numHashes; j++) {
            a[j] = rand1.nextInt(Integer.MAX_VALUE);
            b[j] = rand1.nextInt(Integer.MAX_VALUE);
        }
        int[][] hashes = new int[numValues][numHashes];

        for(int i = 0; i < numValues; i++){
            for(int j = 0; j < numHashes; j++){
                // Perform Universal hashing
                // Prevent overflowing of intermediate multiplication by casting
                // to long and then cast small value back to int 
                hashes[i][j] = (int)((((long) a[j] * i + b[j] ) % p ) % numValues);
            }
        }
        return hashes;
    }
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
    public static int[][] constructSignatureMatrix(Reader reader, int[][] hashValues) {
        reader.reset();
        // ReaderAll exceed heap size quite fast (and it makes sense also)
        // I wished i could afford 1TB of RAM but i don't so we will load each 
        // shingled doc in the loop

        // Create signature matrix
        int[][] signatureMatrix = new int[hashValues[0].length][reader.getMaxDocs()];
        // Initialize each row with maximum integer value (needed for the next comparisons)
        for (int[] r : signatureMatrix)
            Arrays.fill(r, Integer.MAX_VALUE);
        // Get each shingled document

        for (int o = 0; o < reader.getMaxDocs(); o++){
            Set<Integer> shingledDoc = reader.next();
            // Check for every hash function
            for(int hashFunc = 0; hashFunc < hashValues[0].length; hashFunc++){
                // The value of them for every shingle
                for(int shingle: shingledDoc) {
                    if(hashValues[shingle][hashFunc] < signatureMatrix[hashFunc][o]){
                        signatureMatrix[hashFunc][o] = hashValues[shingle][hashFunc];
                    }
                }
            }
        }
        return signatureMatrix;
    }
    public static int[] constructSignatureMatrix1D(
            Reader reader,
            int[] hashValues1D,
            int numHashes,
            int numDocs) {

        // Read all documents’ shingle‐sets
        reader.reset();
        List<Set<Integer>> docToShingle = reader.readAll();

        // Allocate and initialize signature array
        int[] signature1D = new int[numHashes * numDocs];
        Arrays.fill(signature1D, Integer.MAX_VALUE);

        // For each document d, for each hash function h, find the min-hash
        for (int d = 0; d < numDocs; d++) {
            Set<Integer> shingles = docToShingle.get(d);
            for (int h = 0; h < numHashes; h++) {
                int min = Integer.MAX_VALUE;
                // scan all shingles of doc d
                for (int shingle : shingles) {
                    // lookup hash value for this (shingle, h)
                    int hv = hashValues1D[shingle * numHashes + h];
                    if (hv < min) {
                        min = hv;
                    }
                }
                // write into signature1D at (h, d)
                signature1D[h * numDocs + d] = min;
            }
        }

        return signature1D;
    }

}
