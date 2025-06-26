/*
 * Copyright 2024 BDAP team.
 *
 * Author: Luca Stradiotti
 * Version: 0.1
 */
#include <chrono>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <vector>
#include <tuple>
#include <immintrin.h>

using std::chrono::steady_clock;
using std::chrono::microseconds;
using std::chrono::duration_cast;

/**
 * A matrix representation.
 *
 * Based on:
 * https://github.com/laudv/veritas/blob/main/src/cpp/basics.hpp#L39
 */
template <typename T>
struct matrix {
private:
    std::vector<T> vec_;

public:
    size_t nrows, ncols;
    size_t stride_row, stride_col; // in num of elems, not bytes

    /** Compute the index of an element. */
    inline size_t index(size_t row, size_t col) const
    {
        if (row >= nrows)
            throw std::out_of_range("out of bounds row");
        if (col >= ncols)
            throw std::out_of_range("out of bounds column");
        return row * stride_row + col * stride_col;
    }

    /** Get a pointer to the data */
    inline const T *ptr() const { return vec_.data(); }

    /** Get a pointer to an element */
    inline const T *ptr(size_t row, size_t col) const
    { return &ptr()[index(row, col)]; }

    /** Get a pointer to the data */
    inline T *ptr_mut() { return vec_.data(); }

    /** Get a pointer to an element */
    inline T *ptr_mut(size_t row, size_t col)
    { return &ptr_mut()[index(row, col)]; }

    /** Access element in data matrix without bounds checking. */
    inline T get_elem(size_t row, size_t col) const
    { return ptr()[index(row, col)]; }

    /** Access element in data matrix without bounds checking. */
    inline void set_elem(size_t row, size_t col, T value)
    { ptr_mut()[index(row, col)] = value; }

    /** Access elements linearly (e.g. for when data is vector). */
    inline T operator[](size_t i) const
    { return ptr()[i]; }

    /** Access elements linearly (e.g. for when data is vector). */
    inline T& operator[](size_t i)
    { return ptr_mut()[i]; }

    /** Access elements linearly (e.g. for when data is vector). */
    inline T operator[](std::pair<size_t, size_t> p) const
    { auto &&[i, j] = p; return get_elem(i, j); }

    matrix(std::vector<T>&& vec, size_t nr, size_t nc, size_t sr, size_t sc)
        : vec_(std::move(vec))
        , nrows(nr)
        , ncols(nc)
        , stride_row(sr)
        , stride_col(sc) {}

    matrix(size_t nr, size_t nc, size_t sr, size_t sc)
        : vec_(nr * nc)
        , nrows(nr)
        , ncols(nc)
        , stride_row(sr)
        , stride_col(sc) {}
};

using fmatrix = matrix<float>;

fmatrix read_bin_data_row_major(const char *fname)
{
    std::ifstream f(fname, std::ios::binary);

    char buf[8];
    f.read(buf, 8);

    int num_ex = *reinterpret_cast<int *>(&buf[0]);
    int num_feat = *reinterpret_cast<int *>(&buf[4]);

    std::cout << "num_ex " << num_ex << ", num_feat " << num_feat << std::endl;

    fmatrix x(num_ex, num_feat, num_feat, 1);
    int num_numbers = num_ex * num_feat;
    f.read(reinterpret_cast<char *>(x.ptr_mut()), num_numbers * sizeof(float));

    return x;
}

fmatrix read_bin_data_col_major(const char *fname)
{
    std::ifstream f(fname, std::ios::binary);

    char buf[8];
    f.read(buf, 8);

    int num_ex = *reinterpret_cast<int *>(&buf[0]);
    int num_feat = *reinterpret_cast<int *>(&buf[4]);

    float tmp;

    std::cout << "num_ex " << num_ex << ", num_feat " << num_feat << std::endl;

    fmatrix x(num_feat, num_ex, num_ex, 1);

    for(int i = 0; i < num_ex; i++){
            
        for(int j = 0; j < num_feat; j++){
            f.read(reinterpret_cast<char*>(&tmp), sizeof(float));
            x.set_elem(j,i, tmp);
        }
    }

    return x;
}

int find_closest_scalar(const fmatrix& x, int index) {
    // Check if requested row is out of bounds
    if (index >= x.nrows) {
        return -1;
    }

    // Initialize variables
    float best_dist = INFINITY;
    int best_index = -1;

    // Get a pointer to the requested row
    const float* element = x.ptr() + index * x.stride_row;

    // iterate through the matrix and calculate eucl. distances
    for (size_t i = 0; i < x.nrows; i++) {
        if (i == index) continue; // Skip comparison with itself

        float temp_dist = 0.0; //keep temporary distance to compare with best
        const float* row_i = x.ptr() + i * x.stride_row;

        for (size_t j = 0; j < x.ncols; j++) {
            float diff = row_i[j] - element[j];
            temp_dist += diff * diff;
        }

        if (temp_dist < best_dist) {
            best_dist = temp_dist;
            best_index = i;
        }
    }

    return best_index;
}

float reduce_sum_avx(__m256 vec) {
    // Perform a single horizontal add to sum pairs within the vector
    vec = _mm256_hadd_ps(vec, vec);
    // Second horizontal add to sum pairs of pairs
    vec = _mm256_hadd_ps(vec, vec);
    
    // Extract the sum from the lower 128 bits
    float sum_low = _mm_cvtss_f32(_mm256_castps256_ps128(vec));
    // Extract the sum from the upper 128 bits
    float sum_high = _mm256_extractf128_ps(vec, 1)[0];
    
    // Return the final sum
    return sum_low + sum_high;
}


int find_closest_simd(fmatrix& x, int index) {
    // TODO: Implement the blocked rotation here.
    if (index >= x.nrows || index < 0) {
        return -1;
    }

    // Initialize variables
    float best_dist = INFINITY;
    int best_index = -1;

    for (size_t i = 0; i < x.nrows; i++) { //Loop peel if can!
        if (i == index) continue; // Skip comparison with itself
        float temp_dist = 0.0; //keep temporary distance to compare with best
        //process most of the matrix until the last <8 elements
        size_t j = 0;
        for (; j < x.ncols - 8; j += 8) {
            __m256 element = _mm256_loadu_ps (( float *) &x[index * x.stride_row + j]);
            __m256 row_ij = _mm256_loadu_ps (( float *) &x[i * x.stride_row + j]);
            row_ij = _mm256_sub_ps (element, row_ij);
            row_ij = _mm256_mul_ps (row_ij, row_ij);
            temp_dist += reduce_sum_avx(row_ij);
        }
        // Process remaining elements (1-7) using scalar code
        for (; j < x.ncols; j++) {
            float elem = x[index * x.stride_row + j];
            float row_val = x[i * x.stride_row + j];
            float diff = elem - row_val;
            temp_dist += diff * diff;
        }

        if (temp_dist < best_dist) {
            best_dist = temp_dist;
            best_index = i;
        }
    }
    return best_index;
}


//int main(int argc, char *argv[])
//{
//    // These are four matrices in /cw/bdap/assignment2/simd/:
//    // calhouse.bin, allstate.bin, diamonds.bin, cpusmall.bin
//
//    // TODO: choose how to read the data
//    // TODO: repeat the number of time measurements to get a more accurate
//    // estimate of the runtime.
//
//    auto x = read_bin_data_row_major("../data/allstate.bin");
//
//    steady_clock::time_point tbegin, tend;
//
//    // SCALAR
//    tbegin = steady_clock::now();
//    auto output_scalar = find_closest_scalar(x, 7);
//    tend = steady_clock::now();
//
//    std::cout << "Closest element: " << output_scalar << std::endl; 
//    std::cout << "Evaluated scalar in "
//        << (duration_cast<microseconds>(tend-tbegin).count()/1000.0)
//        << "ms" << std::endl;
//
//    // SIMD
//    tbegin = steady_clock::now();
//    auto output_simd = find_closest_simd(x, 7);
//    tend = steady_clock::now();
//    
//    std::cout  << "Closest element: " << output_simd << std::endl; 
//    std::cout << "Evaluated SIMD in "
//        << (duration_cast<microseconds>(tend-tbegin).count()/1000.0)
//        << "ms" << std::endl;
//
//    return 0;
//}
int main(int argc, char *argv[]) {
    if (argc < 3) {
        std::cerr << "Usage: " << argv[0] << " <data_file> <index>" << std::endl;
        return 1;
    }
    
    std::string data_file = argv[1];
    const char * data_path = data_file.c_str();
    int index = std::stoi(argv[2]);
    
    // Read the data
    auto x = read_bin_data_row_major(data_path);
    
    // Check if index is valid
    if (index < 0 || index >= x.nrows) {
        std::cerr << "Error: Index " << index << " out of bounds [0, " << x.nrows-1 << "]" << std::endl;
        return -1;
    }
    
    // Perform scalar measurement (single run)
    steady_clock::time_point tbegin = steady_clock::now();
    auto output_scalar = find_closest_scalar(x, index);
    steady_clock::time_point tend = steady_clock::now();
    
    double scalar_time_ms = duration_cast<microseconds>(tend-tbegin).count() / 1000.0;
    
    // Perform SIMD measurement (single run)
    tbegin = steady_clock::now();
    auto output_simd = find_closest_simd(x, index);
    tend = steady_clock::now();
    
    double simd_time_ms = duration_cast<microseconds>(tend-tbegin).count() / 1000.0;
    
    // Print results in a format easy to parse by the Python script
    std::cout << "RESULT," << data_file << "," << x.nrows << "," << x.ncols 
              << "," << output_scalar << "," << scalar_time_ms 
              << "," << output_simd << "," << simd_time_ms << std::endl;
    
    return 0;
}