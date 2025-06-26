import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;

public class LSH1D extends SimilaritySearcher {
    int numHashes;
    int numBands;
    int numBuckets;
    int seed;
    // assume numHashes, numBands, numBuckets, seed, reader inherited
    public LSH1D(Reader reader, int numHashes, int numBands, int numBuckets, int seed){
        super(reader);

        this.numHashes = numHashes;
        this.numBands = numBands;
        this.numBuckets = numBuckets;
        this.seed = seed;
    }

    @Override
    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold) {
        Set<SimilarPair> similarPairs = new HashSet<>();
        int numHashes = this.numHashes;
        int numDocs = this.reader.getMaxDocs();

        long startTime = System.currentTimeMillis();
        
        // 1D Signature Matrix Construction
        int[] hashValues1D = MinHash1D.constructHashTable1D(numHashes, numDocs, this.seed);
        int[] signature1D = MinHash1D.constructSignatureMatrix1D(reader, hashValues1D, numHashes, numDocs);

        System.out.println("done with signature matrix creation. Took " +  (System.currentTimeMillis() - startTime)/1000.0 + " seconds.");


        // Banding parameters
        int r = (numHashes + numBands - 1) / numBands; // Ceiling division
        int numBuckets = this.numBuckets;
        
        // Reusable buffers for hashing
        ByteBuffer buf = ByteBuffer.allocate(r * Integer.BYTES);
        byte[] bandBytes = new byte[r * Integer.BYTES];
        IntBucket[] buckets = new IntBucket[numBuckets];
        for (int i = 0; i < numBuckets; i++) buckets[i] = new IntBucket();
        int avgBuckSize = 0;

        for (int band = 0; band < numBands; band++) {
            //System.out.print("Processing band: " + Integer.toString(band));
            //startTime = System.currentTimeMillis();

            int startRow = band * r;
            int endRow = Math.min(startRow + r, numHashes);
            int bandWidth = endRow - startRow;

            // Reset buckets
            for (IntBucket bucket : buckets) bucket.clear();

            // Hash documents into buckets
            for (int doc = 0; doc < numDocs; doc++) {
                // Directly serialize from 1D array
                buf.clear();
                for (int row = startRow; row < endRow; row++) {
                    buf.putInt(signature1D[doc * numHashes + row]);
                }
                buf.flip();
                buf.get(bandBytes);
                
                int hash = MurmurHash.hash32(bandBytes, bandBytes.length, seed);
                int bucketIdx = Math.floorMod(hash, numBuckets);
                buckets[bucketIdx].add(doc);
            }
            
            // Process candidate pairs
            for (IntBucket bucket : buckets) {
                int[] docs = bucket.data();
                int size = bucket.size();
                avgBuckSize += bucket.size();
                for (int i = 0; i < size; i++) {
                    int doc1 = docs[i];
                    int doc1Offset = doc1 * numHashes; // Precompute offset
                    for (int j = i + 1; j < size; j++) {
                        int doc2 = docs[j];
                        int doc2Offset = doc2 * numHashes;
                        
                        // Early exit similarity check
                        int required = (int) Math.ceil(threshold * numHashes);
                        int matches = 0;
                        for (int h = 0; h < numHashes; h++) {
                            if (signature1D[doc1Offset + h] == signature1D[doc2Offset + h]) {
                                if (++matches >= required) break;
                            }
                            if ((numHashes - h) < (required - matches)) break;
                        }
                        
                        if (matches >= required) {
                            similarPairs.add(new SimilarPair(
                                reader.getExternalId(doc1),
                                reader.getExternalId(doc2),
                                (double) matches / numHashes
                            ));
                        }
                    }
                }
            } 
            //System.out.println(". Took " +  (System.currentTimeMillis() - startTime)/1000.0 + " seconds.");
        }
        System.out.printf("Average bucket size: %f\n", (double)avgBuckSize/(numBuckets*numBands));
        return similarPairs;
    }
}