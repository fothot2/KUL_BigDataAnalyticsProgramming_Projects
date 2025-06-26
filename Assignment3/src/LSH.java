/**
 * Copyright (c) DTAI - KU Leuven – All rights reserved. Proprietary, do not
 * copy or distribute without permission. Written by Pieter Robberechts, 2025
 */

import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Implementation of minhash and locality sensitive hashing (LSH) to find
 * similar objects.
 *
 * The LSH should first construct a signature matrix. Based on this, LSH is
 * performed resulting in a mapping of band ids to hash tables (stored in
 * bandToBuckets). From this bandsToBuckets mapping, the most similar items
 * should then be retrieved.
 *
 */
public class LSH extends SimilaritySearcher {

    int numHashes;
    int numBands;
    int numBuckets;
    int seed;

    /**
     * Construct an LSH similarity searcher.
     *
     * @param reader the document reader
     * @param numHashes number of hashes to use to construct the signature matrix
     * @param numBands number of bands to use during locality sensitive hashing
     * @param numBuckets number of buckets to use during locality sensitive hashing
     * @param seed should be used to generate any random numbers needed
     */
    public LSH(Reader reader, int numHashes, int numBands, int numBuckets, int seed){
        super(reader);

        this.numHashes = numHashes;
        this.numBands = numBands;
        this.numBuckets = numBuckets;
        this.seed = seed;
    }


    /**
     * Returns the pairs with similarity above threshold (approximate).
     */
    @Override
    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold) {
        Set<SimilarPair> similarPairsAboveThreshold = new HashSet<SimilarPair>();
        Random rand = new Random(this.seed);
        int numHashes = this.numHashes;
        int numObjects = this.reader.maxDocs;
    
        // Generate hash functions and signature matrix
        int[][] hashValues = Minhash.constructHashTable(numHashes, numObjects, this.seed);
        int[][] signatureMatrix = Minhash.constructSignatureMatrix(this.reader, hashValues);

        // Figuring out number of rows r that will comprise a band
        // Instead of doing casting to double and then taking the ceil
        // We are using this approach, 
        // thanks to: https://stackoverflow.com/questions/7139382/java-rounding-up-to-an-int-using-math-ceil
        int r = (this.numBands + numHashes - 1) / this.numBands;
        int numBuckets = this.numBuckets;

        // Initialize p, a and b
        int p = Primes.findLeastPrimeNumber(this.numBuckets);
        int a = rand.nextInt(Integer.MAX_VALUE);
        int b = rand.nextInt(Integer.MAX_VALUE);

        // Preallocate primitive buckets
        //int[][] buckets = new int[numBuckets][numObjects];
        //int[] bucketCounts = new int[numBuckets];

        IntBucket[] buckets = new IntBucket[numBuckets];
        for (int i = 0; i < numBuckets; i++) buckets[i] = new IntBucket();

        // Temp buffer to serialize band slice to bytes
        ByteBuffer buf = ByteBuffer.allocate(r * Integer.BYTES);
        byte[] bandBytes = new byte[r * Integer.BYTES];

        // For each band b (each band has r rows)
        for (int band = 0; band < this.numBands; band++) {
            //System.out.print("Band: ");
            //System.out.println(band);
            int startRow = band * r;
            int endRow = Math.min(startRow + r, numHashes);

            // clear each bucket for this band
            for (IntBucket bucket : buckets) {
                bucket.clear();
            }

            // Initialize buckets for this band. Start with empty lists
            //List<List<Integer>> buckets = new ArrayList<>(numBuckets);
            //for (int i = 0; i < numBuckets; i++) {
            //    buckets.add(new ArrayList<>());
            //}

            // Hash each document (column) into a bucket for this band
            for (int j = 0; j < numObjects; j++) {
                // Step 1: Extract the band slice
                int[] bandSlice = new int[endRow - startRow];
                for (int i = startRow; i < endRow; i++) {
                    bandSlice[i - startRow] = signatureMatrix[i][j];
                }
                buf.clear();
                // serialize the band slice of ints
                for (int row = startRow; row < endRow; row++) {
                    buf.putInt(signatureMatrix[row][j]);
                }
                buf.flip();
                buf.get(bandBytes);

                // compute 32-bit MurmurHash on the byte slice
                int combinedHash = MurmurHash.hash32(bandBytes, bandBytes.length, seed);
                int bucketIdx = Math.floorMod(combinedHash, numBuckets);
                buckets[bucketIdx].add(j);

                // Step 2: Hash the band slice (this avoids overflow)
                //int combinedHash = Arrays.hashCode(bandSlice); // deterministic hash for int[]

                // Step 3: Map hash to a bucket using unsigned math
                //int bucketIdx = Math.floorMod(((int)(((long) a * combinedHash + b) % p)), numBuckets);

                // Add document j to the bucket
                //buckets[bucketIdx].add(j);
                // add doc to primitive bucket
                //int pos = bucketCounts[bucketIdx];
                //buckets[bucketIdx][pos] = j;
                //bucketCounts[bucketIdx] = pos + 1;
            }
            int bindex = 0;
            // Find candidate pairs in each bucket
            for (IntBucket bucket : buckets) {
                if (bindex % 10 == 0){
                    System.out.println("Band: " + 
                    Integer.toString(band) + 
                    " Bucket: " + 
                    Integer.toString(bindex) + 
                    " Size: " + bucket.size());
                }
                //System.out.print("Bucket: ");
                //System.out.println(bindex);
                bindex++;
                int size = bucket.size();
                // If bucket has less than 2 docs then no pairs
                if (size < 2) continue;
                int[] docs = bucket.data();
                for (int index1 = 0; index1 < size - 1; index1++){
                    for (int index2 = index1 + 1; index2 < size; index2++){
                        int doc1 = docs[index1];
                        int doc2 = docs[index2];
                        int matches = 0;
                        int remaining = numHashes;
                        // Iterate over all hash functions (rows)
                        for (int hash = 0; hash < numHashes; hash++) {
                            if (signatureMatrix[hash][doc1] == signatureMatrix[hash][doc2]) {
                                matches++;
                            }
                            remaining--;
                            // If we reach a point where the remaining functions do not
                            // suffice to break the threshold break.
                            if (matches + remaining < threshold * numHashes) break;
                        }
                        //double match_rate = matches / (double) numHashes;
                        //System.out.println(matches);
                        
                        if (matches > threshold * numHashes) {
                            SimilarPair pair = new SimilarPair(
                                reader.getExternalId(doc1),
                                reader.getExternalId(doc2),
                                matches / (double) numHashes);
                            similarPairsAboveThreshold.add(pair);
                        }
                    }
                }
            }
        }
        return similarPairsAboveThreshold;
    }
}
