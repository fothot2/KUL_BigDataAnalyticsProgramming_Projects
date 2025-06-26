/**
 * Copyright (c) DTAI - KU Leuven – All rights reserved. Proprietary, do not
 * copy or distribute without permission. Written by Pieter Robberechts, 2025
 */
import java.util.*;
import java.io.*;

/**
 * The Runner can be ran from the commandline to find the most similar pairs
 * of documents in a directory.
 *
 * Example command to run with brute force similarity search:
 *  java Runner -threshold 0.5 -method bf -maxTweets 100 -dataFile data -shingleLength 5
 * Example command to run with LSH similarity search:
 *  java Runner -threshold 0.5 -method lsh -maxTweets 100 -dataFile data -shingleLength 5 -numHashes 100 -numBands 20
 */
public class Runner {

    public static void main(String[] args) {

        String inputFile = "";
        String outputFile = "";
        String method = "";
        int numShingles = 1000;
        int numHashes = -1;
        int numBands = -1;
        int numBuckets = 2000;
        int seed = 1234;
        int maxTweets = -1;
        int shingleLength = -1;
        float threshold = -1;

        int i = 0;
        while (i < args.length && args[i].startsWith("-")) {
            String arg = args[i];
            if (arg.equals("-method")) {
                if (!args[i+1].equals("bf") && !args[i+1].equals("lsh")){
                    System.err.println("The search method should either be brute force (bf) or minhash and locality sensitive hashing (lsh)");
                }
                method = args[i+1];
            } else if(arg.equals("-numHashes")) {
                numHashes = Integer.parseInt(args[i+1]);
            } else if(arg.equals("-numBands")) {
                numBands = Integer.parseInt(args[i+1]);
            } else if(arg.equals("-numBuckets")) {
                numBuckets = Integer.parseInt(args[i+1]);
            } else if(arg.equals("-numShingles")) {
                numShingles = Integer.parseInt(args[i+1]);
            } else if(arg.equals("-seed")) {
                seed = Integer.parseInt(args[i+1]);
            } else if(arg.equals("-dataFile")) {
                inputFile = args[i + 1];
            } else if(arg.equals("-maxTweets")) {
                maxTweets = Integer.parseInt(args[i+1]);
            } else if(arg.equals("-shingleLength")) {
                shingleLength = Integer.parseInt(args[i+1]);
            } else if(arg.equals("-threshold")) {
                threshold = Float.parseFloat(args[i+1]);
            } else if(arg.equals("-outputFile")) {
                outputFile = args[i + 1];
            }

            i += 2;
        }
        if (threshold <= 0.0 || threshold >= 1.0) {
            throw new IllegalArgumentException("General Configuration error: Threshold must be > 0 and < 1. Current value: " + threshold);
        }
        if (numShingles < 1 || maxTweets < 1 || shingleLength < 1) {
            throw new IllegalArgumentException("General Configuration error: All integer parameters must be >= 1.\n" +
                "Current values:\n" +
                "  numShingles = " + numShingles + "\n" +
                "  maxTweets = " + maxTweets + "\n" +
                "  shingleLength = " + shingleLength);
        }

        Shingler shingler = new Shingler(shingleLength, numShingles, seed);
        Reader reader = new TwitterReader(maxTweets, shingler, inputFile);

        SimilaritySearcher searcher = null;
        if (method.equals("bf")) {
            searcher = new BruteForceSearch(reader);
        } else if(method.equals("lsh")) {
            if (numHashes == -1 || numBands == -1) {
                throw new Error("Both -numHashes and -numBands are mandatory arguments for the LSH method");
            }
            if (numHashes < 1 || numBands < 1 || numBuckets < 1) {
                throw new IllegalArgumentException("LSH Configuration error: All integer parameters must be >= 1.\n" +
                    "Current values:\n" +
                    "  numHashes = " + numHashes + "\n" +
                    "  numBands = " + numBands + "\n" +
                    "  numBuckets = " + numBuckets);
            }
            if (numHashes % numBands != 0) {
                System.out.println("Warning! Number of hashes (" + numHashes + ") is not divisible by number of bands (" + numBands + ").");

                // Find the closest smaller number of bands that divides numHashes
                int correctedBands = numBands - 1;
                while (correctedBands > 1 && numHashes % correctedBands != 0) {
                    correctedBands--;
                }

                if (correctedBands > 1) {
                    System.out.println("Will Use " + correctedBands + " bands instead, as it evenly divides " + numHashes + ".");
                    numBands = correctedBands;
                } else {
                    System.out.println("No smaller number of bands found that evenly divides " + numHashes + ". WIll use 1");
                    numBands = 1;
                }
            }
            searcher = new LSH1D(reader, numHashes, numBands, numBuckets, seed);
        }

        long startTime = System.currentTimeMillis();
        System.out.println("Searching items more similar than " + threshold + " ... ");
        Set<SimilarPair> similarItems = searcher.getSimilarPairsAboveThreshold(threshold);
        System.out.println("done! Took " +  (System.currentTimeMillis() - startTime)/1000.0 + " seconds.");
        System.out.println("--------------");
        printPairs(similarItems, outputFile);
    }


    /**
     * Prints pairs and their similarity.
     * @param similarItems A set of similar pairs
     * @param outputFile The file to write the output to
     */
    public static void printPairs(Set<SimilarPair> similarItems, String outputFile){
        try {
            File fout = new File(outputFile);
            FileOutputStream fos = new FileOutputStream(fout);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

            List<SimilarPair> sim = new ArrayList<SimilarPair>(similarItems);
            Collections.sort(sim, Collections.reverseOrder());
            for(SimilarPair p : sim) {
                bw.write(p.getId1() + "\t" + p.getId2() + "\t" + p.getSimilarity());
                bw.newLine();
            }

            bw.close();
            System.out.println("Found " + similarItems.size() + " similar pairs, saved to '" + outputFile + "'");
            System.out.println("--------------");
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

}
