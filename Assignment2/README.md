# Assignment 2 – SIMD and Distributed Data Processing

This assignment focuses on two major components:

1. SIMD Programming in C++
2. Distributed Data Processing using Apache Spark

---

## Part 1: SIMD Programming (C++)

### Objective
Implement two versions of a function to find the closest example in a dataset:
- A scalar version
- A SIMD version using AVX/AVX2 intrinsics

### Implementation Notes
- SIMD version uses AVX registers and falls back to scalar for remaining columns.
- Row-major layout was chosen for better cache locality.
- Square roots were skipped to save computation, comparing squared distances instead.
- Experiments were run on an Intel i7-8700H (macOS), using a Python driver to avoid cache warm-up bias.

### Results
- Speedup was most significant for wide datasets (e.g., 3.2× on MNIST with 784 columns).
- Even small datasets (e.g., Callhouse with 8 columns) benefited from SIMD.

---

## Part 2: Distributed Computing with Spark

### Dataset
- NBA SportVU tracking data for 84 games
- Includes player positions, ball coordinates and play-by-play events

---

### a. Distance Traveled

- **Goal**: Compute total and normalized distance traveled per player using Euclidean distance in the xy-plane.
- **Method**:
  - Calculate distance between consecutive positions
  - Normalize by minutes played to get average distance per quarter
- **Output**: `distance_per_player.csv`  
  Format:
  ```
  <player ID> <average distance per quarter in meters>
  ```

---

### b. Speed Zones

- **Goal**: Classify player movement into three speed zones:
  - Slow: < 2 m/s
  - Normal: 2–8 m/s
  - Fast: ≥ 8 m/s
- **Method**:
  - Apply a moving average filter (window size = 10 frames)
  - Ignore unrealistic speeds (> 12 m/s)
  - Define runs as continuous movement within the same speed zone
  - Use a flag to detect substitutions via clock jumps > 15s
- **Output**: `speed_zones.csv`  
  Format:
  ```
  <player ID> <speed zone> <number of runs> <total distance>
  ```

### Observations
- Many players never reached the “fast” zone; lowering the threshold to 7 m/s includes more.
- Kristaps Porzingis (ID: 204001) had the most distance in fast runs (28.84m).
- Draymond Green (ID: 203110) had the most fast runs (71) and greatest fast-zone distance (108.32m).
- 95 players never entered the fast zone.

---

### c. Rebounds

- **Goal**: Analyze rebound events and compute:
  - Total offensive and defensive rebounds per player
  - Farthest rebound location from the basket
- **Method**:
  - Use play-by-play data to identify rebounds
  - Use tracking data to locate rebound positions
  - Add monotonically increasing ID to preserve order due to unreliable game clock
- **Output**: `rebounds.csv`  
  Format:
  ```
  <player ID> <nb offensive rebounds> <nb defensive rebounds> <dist farthest rebound>
  ```

### Observations
- Some rebounds were recorded at distances > 13m due to misclassified team rebounds.
- These were retained for consistency with SportVU’s classification.
- Spurs had no data due to their only game in the dataset being one of the missing matches.

---

## Report Summary

- SIMD benchmark: average runtime, standard deviation and speedup
- Spark analytics:
  - Player with median number of high-speed runs: Kristaps Porzingis (ID: 204001)
  - Player with most high-speed runs and distance: Draymond Green (ID: 203110)
  - 95 players never reached the fast speed zone
- Bar chart: average rebounds per team (Spurs missing due to dataset gap)
- Table: top 10 farthest rebounds with player IDs and distances

---

## Notes

- NBA tracking data is confidential and is not redistributed.
- Use monotonically increasing IDs to preserve order when clocks are unreliable.
- SIMD performance depends on dataset width and memory alignment.
