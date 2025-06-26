/**
 * Copyright (c) DTAI - KU Leuven – All rights reserved. Proprietary, do not
 * copy or distribute without permission. Written by Pieter Robberechts, 2025
 */
import java.util.HashSet;
import java.util.Set;
import java.util.Map;


/**
 * Searching similar objects. Objects should be represented as a mapping from
 * an object identifier to a set containing the associated values.
 * 
 */
public abstract class SimilaritySearcher {

    Reader reader;

    public SimilaritySearcher(Reader reader) {
        this.reader = reader;
    }

    /**
     * Returns the pairs of the objectMapping that have a similarity coefficient exceeding threshold
     * @param threshold the similarity threshold
     * @return the pairs with similarity above the threshold
     */
    abstract public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold);

    /**
     * Jaccard similarity between two sets.
     * @param set1
     * @param set2
     * @return the similarity
     */
    public <T> double jaccardSimilarity(Set<T> set1, Set<T> set2) {
        double sim = 0;
        //if (set1.isEmpty() && set2.isEmpty()) {
        //    return 1.0; // Edge case, both sets empty, so equal
        //}
        // creating a copy of the first set so we don’t destroy the original
        Set<T> intersection = new HashSet<T>(set1); // A
        intersection.retainAll(set2);  // A ∩ B
        int intersectionSize = intersection.size(); // Count the size

        // No need for addAll, we can utilize |A ∪ B| = |A| + |B| − |A ∩ B|
        int unionSize = set1.size() + set2.size() - intersectionSize;

        sim = (double) intersectionSize / unionSize;

        return sim;
    }

}
