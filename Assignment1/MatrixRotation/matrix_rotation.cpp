#include <iostream>
#include <fstream>
#include <cstring>
#include <ctime>

/**
 * Rotate a square matrix in a naive way.
 * 
 * This function rotates a square matrix in a naive way by swapping the elements
 * based on their indices. The rotation is performed in-place, meaning the original
 * matrix is modified.
 * 
 * @param a Pointer to the input matrix.
 * @param b Pointer to the output matrix.
 * @param n The size of the square matrix.
 */
void rotate_naive(double *src, double *dest, int n) {
  for (int i = 0; i < n; i++) {
    for (int j = 0; j < n; j++) {
      dest[j * n + i] = src[(n - 1 - i) * n + j];
    }
  }
}


/**
 * Rotate a square matrix in blocked fashion.
 *
 * This function rotates a square matrix by 90 degrees clockwise in a blocked fashion, where each block of size B x B is rotated individually.
 * The input matrix 'a' is rotated and the result is stored in the output matrix 'b'.
 *
 * @param a     Pointer to the input matrix.
 * @param b     Pointer to the output matrix.
 * @param n     Size of the square matrix.
 * @param B     Size of the block.
 */

void rotate_blocked(double *src, double *dest, int n, int B) {
  for (int j = 0; j < n; j += B) {
    for (int i = 0; i < n; i += B) {
      //out of bounds check
      int i_max = std::min(i + B, n);
      int j_max = std::min(j + B, n);

      for(int c_j = j; c_j < j_max; c_j++){
        for(int c_i = i; c_i < i_max; c_i++){
          dest[n - 1 + c_i * n - c_j] = src[c_j * n + c_i];
        }
      }
    }
  }
}


/**
 * Reads a matrix from a file.
 * 
 * @param filename The name of the file to read the matrix from.
 * @param matrix   A pointer to the array where the matrix will be stored.
 * @param n        The size of the matrix (n x n).
 */
void readMatrix(std::string filename, double *matrix, int n) {
  std::ifstream file(filename);
  if (file.is_open()) {
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        file >> matrix[i * n + j];
      }
    }
    file.close();
  } else {
    std::cout << "Unable to open file: " << filename << std::endl;
  }
}


int main(int argc, char **argv) {
    std::string method = argv[1];
    int n = std::atoi(argv[2]);
    std::string filename = argv[3];
    int block_size = n;

    if (method == "blocked") {
      block_size = std::atoi(argv[4]);
    }

    std::cout << "method=" << method << std::endl;
    std::cout << "N=" << n << std::endl;
    std::cout << "B=" << block_size << std::endl;
    std::cout << "matrix: " << filename << std::endl;

    double *src = new double[n * n];
    double *dest = new double[n * n];

    readMatrix(filename, src, n);

  clock_t begin, end;
  double time_spent;

  begin = clock();
  
  if (method == "blocked") {
    rotate_blocked(src, dest, n, block_size);
  } 
  else {
    rotate_naive(src, dest, n);
  }
  
  end = clock();

  time_spent = static_cast<double>(end - begin) / CLOCKS_PER_SEC;
  std::cout << "time: " << time_spent << std::endl;

  free(src);
  free(dest);

  return 0;
}
