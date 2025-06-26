
# param 1: file name
# param 2-N: different block sizes

import sys
import subprocess
import matplotlib.pyplot as plt
import numpy as np

REPEATS = 20

file_name = sys.argv[1]
block_sizes = list(map(lambda block_size: int(block_size), sys.argv[2:]))


def plot_line_chart(data):
    # Extract matrix sizes
    matrix_sizes = list(data.keys())
    # Get methods from the first matrix size
    methods = list(next(iter(data.values())).keys())
    num_methods = len(methods)

    # Prepare data for plotting
    # Create a 2D array to hold times
    times = np.zeros((len(matrix_sizes), num_methods))
    stds = np.zeros((len(matrix_sizes), num_methods))

    # Fill the times array directly from the data dictionary
    for i, size in enumerate(matrix_sizes):
        for j, method in enumerate(methods):
            times[i, j] = data[size][method]['mean']
            stds[i, j] = data[size][method]['std']

    plt.figure(figsize=(25, 16))

    # Plot a line for each method
    for j in range(num_methods):
        plt.errorbar(
            matrix_sizes, times[:, j], yerr=stds[:, j], marker='o', linestyle='-', label=methods[j]
        )

    # Labeling the plot
    plt.xlabel('Matrix Size', fontsize=14)
    plt.ylabel('Execution Time (seconds)', fontsize=14)
    plt.title('Matrix Size vs Execution Time by Method for ' + str(REPEATS) + ' repeats', fontsize=16)

    # Center the x-ticks
    plt.xticks(matrix_sizes)
    plt.legend(title='Methods', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.grid(True)

    plt.savefig("graph.png")

    # Display the plot
    plt.tight_layout()
    plt.show()


def run_subprocess(algorithm, n, filename, block_size=None):
    """
    Run the specified algorithm on the matrix file and return the exec time.
    If block_size is None, it assumes a naive algorithm.
    """
    if algorithm == "naive":
        result = subprocess.run(["./matrix_rotation", "naive", n, filename],
                                capture_output=True, text=True)
    else:
        result = subprocess.run(["./matrix_rotation", "blocked", str(n),
                                 filename, str(block_size)],
                                capture_output=True, text=True)
    if "Unable to open file:" in result.stdout:
        raise ValueError("Unable to open file: ", filename)
    time_index = result.stdout.find("time")
    if time_index == -1:
        raise ValueError("Time not found in output")

    return float(result.stdout[time_index + 6:time_index + 13].rstrip())


print("This script executes matrix rotation for the matrixes in file",
      "'" + file_name + "'", "using the naive strategy and the blocked",
      "strategy for block sizes:", ", ".join(sys.argv[2:]))
print("The result of this script is a graph 'graph.png' in the current",
      "directory that shows the results with matrix size on the x-axis and",
      "time on the y-axis. Create one line for each block size and one line",
      "for the naive strategy.")
matrix_filenames = []
results = {}
with open(file_name, 'r', encoding='UTF-8') as file:
    while line := file.readline():
        matrix_filenames.append(line.rstrip().split(" "))

for n, filename in matrix_filenames:
    results[n + " * " + n] = {}  # ∀n keep a separate dict entry for every algo
    temp_times = []
    for _ in range(REPEATS):
        temp_times.append(run_subprocess("naive", str(n), filename))
    results[n + " * " + n]["naive"] = {"mean": sum(temp_times) / REPEATS}
    results[n + " * " + n]["naive"].update({'std': np.std(temp_times)})
    for block_size in block_sizes:
        temp_times = []
        for _ in range(REPEATS):
            temp_times.append(run_subprocess("blocked", str(n),
                                             filename, block_size))
            results[n + " * " + n]["Blocked " + str(block_size)] =\
                {"mean": sum(temp_times) / REPEATS}
            results[n + " * " + n]["Blocked " + str(block_size)]\
                .update({'std': np.std(temp_times)})

plot_line_chart(results)
