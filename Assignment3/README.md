# Big Data Analytics Programming – Project 3

## Project Overview

This project implements **Locality Sensitive Hashing (LSH)** to efficiently identify similar tweets from a dataset of 5 million entries. The goal is to approximate Jaccard similarity using MinHash signatures and LSH banding, optimizing for both speed and accuracy under memory and runtime constraints.

---

## Key Components

### 1. Dataset

- Source: Twitter CIKM 2010 dataset (not disclosed)
- Format: `UserID \t TweetID \t Tweet \t CreatedAt`
- Only the tweet text (3rd column) is used

### 2. Implementation

- **MinHash.java**: Computes signature matrix
- **LSH.java**: Implements banding and candidate filtering
- **SimilaritySearcher.java**: Abstract base class
- **Runner.java**: CLI interface for running brute-force or LSH
- **BruteForceSearch.java**: Naive baseline for correctness verification

---

## Development Highlights

### Optimized Data Structures

- **1D Signature Matrix**: Improved cache locality by flattening 2D matrices into 1D arrays
- **Custom IntBucket**: Dynamic int arrays to avoid Java autoboxing overhead
- **Early Exit Strategy**: Stops comparisons early if similarity threshold is met or unreachable

### Verification

- LSH results were validated against brute-force on a 5K tweet subset
- High accuracy achieved with 420 hash functions (some FP/FN expected)

---

## Experimental Strategy

### Parameter Tuning

- **Hash Functions**: 20 max (due to 2GB memory limit)
- **Buckets**: Started with √N ≈ 2236, tuned to 300K for better precision
- **Bands & Rows**: Tuned using LSH S-curve to minimize false negatives
- **Shingles**: 2000–3000 unique shingles tested; 2000 found optimal

### Python Driver Script

- Automated hyperparameter sweep
- Captured runtime, bucket size and pair count
- Enforced 15-minute timeout per config

---

## Results Summary

| #  | Hashes | Bands | Buckets | Shingles | t_sig (s) | t_rest (s) | t_tot (s) | Pairs     |
|----|--------|--------|---------|----------|-----------|------------|-----------|-----------|
| 1  | 8      | 1      | 300000  | 2000     | 140.26    | 5.31       | 145.57    |  2918728   |
| 2  | 8      | 1      | 500000  | 3000     | 147.26    | 4.85       | 152.11    | 2837893   |
| 3  | 8      | 1      | 100000  | 2000     | 150.43    | 6.56       | 156.99    | 2918728   |
| 4  | 12     | 1      | 300000  | 2000     | 154.00    | 5.73       | 159.72    |  2559372   |
| 5  | 12     | 1      | 300000  | 1000     | 155.68    | 5.84       | 161.51    |2519239   |
| 6  | 8      | 2      | 500000  | 3000     | 146.32    | 18.21      | 164.53    | 2837893   |
| 7  | 12     | 2      | 500000  | 3000     | 156.87    | 10.58      | 167.46    | 3464715   |
| 8  | 8      | 2      | 100000  | 3000     | 148.26    | 20.31      | 168.57    | 2837893   |
| 9  | 12     | 2      | 100000  | 2000     | 154.27    | 16.33      | 170.59    |3494542   |
| 10 | 16     | 1      | 500000  | 3000     | 165.28    | 5.71       | 170.99    | 2436582   |
| 11 | 8      | 2      | 300000  | 1000     | 147.95    | 24.42      | 172.36    |2900101   |
| 12 | 16     | 2      | 500000  | 2000     | 165.02    | 9.97       | 174.99    | 2909917   |
| 13 | 20     | 1      | 300000  | 1000     | 177.59    | 6.34       | 183.93    | 2327880   |
| 14 | 20     | 1      | 500000  | 3000     | 178.15    | 6.17       | 184.32    | 2342210   |
| 15 | 20     | 2      | 100000  | 1000     | 176.48    | 15.61      | 192.08    | 2832343   |
| 16 | 20     | 2      | 300000  | 3000     | 177.81    | 11.02      | 188.83    | 2827430   |
| 17 | 20     | 4      | 100000  | 3000     | 179.36    | 30.90      | 210.26    | 3044182   |
| 18 | 20     | 4      | 300000  | 1000     | 176.08    | 28.36      | 204.44    | 3047325   |
| 19 | 16     | 4      | 100000  | 1000     | 164.20    | 70.71      | 234.90    | 2856288   |
| 20 | 8      | 2      | 300000  | 2000     | 145.82    | 92.44      | 238.27    | 2918728   |
| 21 | 8      | 2      | 100000  | 2000     | 147.41    | 103.25     | 250.66    | 2918728   |
| 22 | 20     | 5      | 300000  | 3000     | 178.62    | 104.68     | 283.30    |3044182   |
| 23 | 12     | 3      | 300000  | 2000     | 156.67    | 142.68     | 299.35    |3494542   |
| 24 | 8      | 4      | 100000  | 1000     | —         | —          | —         | — (T)     |
| 25 | 8      | 4      | 100000  | 2000     | —         | —          | —         | — (T)     |
| 26 | 8      | 4      | 100000  | 3000     | —         | —          | —         | — (T)     |

- **t_sig**: Time for Signature matrix creation
- **t_rest**: Time for the rest of the algorithm 
- **t_tot**: Total time
- **(T)**: configuration exceeded 15 minutes and therefore killed 


> Best configuration:  
> - **Hash functions**: 12–16  
> - **Bands**: 1–3  
> - **Buckets**: ~300,000  
> - **Shingles**: 2000  
> - **Runtime**: < 5 minutes  
> - **Memory**: < 2 GB

---

## How to Run

### Compile

```bash
javac -d bin src/*.java
```

### Run LSH

```bash
java -cp bin Runner \
  -method lsh \
  -dataFile tweets.tsv \
  -outputFile output.tsv \
  -threshold 0.9 \
  -maxTweets 5000000 \
  -shingleLength 3 \
  -numShingles 2000 \
  -numHashes 12 \
  -numBands 2 \
  -numBuckets 300000 \
  -seed 42
```

> ✅ Output format:  
> `tweetID1 \t tweetID2 \t similarity`

---

## License

This work is licensed under the Creative Commons Attribution 4.0 International License.

---

## Contributions

This repository is a personal academic archive and is not open for contributions.
