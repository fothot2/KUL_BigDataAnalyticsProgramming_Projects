import numpy as np
import sys


def generate_random_matrix(N):
    # Generate an NxN matrix with random values between 0 and 1
    matrix = np.arange(1, N*N + 1).reshape(N, N)

    # Save the matrix to a text file
    filename = f"m{N}by{N}.txt"
    np.savetxt(filename, matrix, delimiter=' ', fmt='%d')
    print(f"Arranged {N}x{N} matrix saved to {filename}")


if __name__ == "__main__":
    # Check if N is provided as an argument
    if len(sys.argv) != 2:
        print("Usage: python generate_matrix.py N")
    else:
        N = int(sys.argv[1])  # Get N from command-line arguments
        generate_random_matrix(N)
