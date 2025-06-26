import subprocess
import matplotlib.pyplot as plt
import numpy as np

# Parameters
n = 9999  # Number of real elements
fake_users = 9999  # Number of fake users generated in data/fake_users.txt

# Range of k values (number of hash functions) and lognumBits values (log of bits in the Bloom filter)
k_values = range(1, 21)
lognumBits_values = range(12, 21)

colors = plt.cm.nipy_spectral(np.linspace(0, 1, len(lognumBits_values)))

# Initialize lists to store results
theoretical_fprs = []
actual_fprs = []

for lognumBits in lognumBits_values:
    theoretical_fprs_for_m = []
    actual_fprs_for_m = []

    m = 2 ** lognumBits  # Bloom filter size in bits

    for k in k_values:
        # Calculate theoretical FPR
        theoretical_fpr = (1 - (1 - 1/m) ** (k * n)) ** k
        #print(theoretical_fpr)
        theoretical_fprs_for_m.append(theoretical_fpr)

        # Run the Java program with makefile and capture output
        result = subprocess.run(
            ["make", "run", f"lognumBits={lognumBits}", f"k={k}"],
            capture_output=True, text=True
        )

        # Process Java program output
        output = result.stdout.strip()
        falsePositives = int(output.split("\n")[-1])

        # Calculate actual FPR
        actual_fpr = falsePositives / fake_users
        actual_fprs_for_m.append(actual_fpr)

    # Store FPRs for the current m
    theoretical_fprs.append(theoretical_fprs_for_m)
    actual_fprs.append(actual_fprs_for_m)

# Plotting
plt.figure(figsize=(12, 8))

for i, lognumBits in enumerate(lognumBits_values):
    color = colors[i]
    plt.plot(k_values, theoretical_fprs[i], color=color,
             label=f"Theoretical FPR (m = 2^{lognumBits})", linestyle="--")
    plt.plot(k_values, actual_fprs[i], color=color,
             label=f"Actual FPR (m = 2^{lognumBits})", marker='o')

plt.xlabel("Number of Hash Functions (k)",  fontsize=22)
plt.ylabel("False Positive Rate (FPR)",  fontsize=22)
plt.title("Theoretical vs Actual Bloom Filter FPR for n=" +
          str(n) + " and " + str(fake_users) + " fake users",  fontsize=22)
plt.legend(loc="upper right", fontsize=20, title_fontsize=22)
plt.yticks(fontsize=20)
plt.xticks(fontsize=20)
plt.grid(True)
plt.show()
