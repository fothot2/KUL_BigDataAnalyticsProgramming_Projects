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

| # | Hashes | Bands | Buckets | Shingles | t_sig (s) | t_rest (s) | t_total (s) | AvgBucket | Pairs     |
|---|--------|--------|---------|----------|-----------|------------|-------------|-----------|-----------|
| 1 | 8      | 1      | 300K    | 2000     | 140.26    | 5.31       | 145.57      | 16.67     | 2,918,728 |
| 7 | 12     | 2      | 500K    | 3000     | 156.87    | 10.58      | 167.46      | 10.00     | 3,464,715 |
| 9 | 12     | 2      | 100K    | 2000     | 154.27    | 16.33      | 170.59      | 50.00     | 3,494,542 |
| 12| 16     | 2      | 500K    | 2000     | 165.02    | 9.97       | 174.99      | 10.00     | 2,909,917 |

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
