#!/bin/bash

# Navigate to the src/Spark directory
cd src/Distance/ || { echo "Directory src/Distance not found!"; exit 1; }

# Run Maven clean and package with quiet output, but show errors if they occur
echo "Running 'mvn clean package'..."
ERROR_LOG=$(mktemp)  # Create a temporary file to capture the error logs
mvn clean package -q -DskipTests 2>"$ERROR_LOG" || { 
    echo "Maven build failed! Error details:"
    cat "$ERROR_LOG"
    rm "$ERROR_LOG"
    exit 1
}

# Define the name of the JAR file
JAR_FILE="target/Distance-1.0.jar"

# Check if the JAR file exists
if [[ ! -f "$JAR_FILE" ]]; then
    echo "JAR file not found: $JAR_FILE!"
    exit 1
fi

echo "Found JAR file: $JAR_FILE"

# Run spark-submit with the generated JAR
echo "Submitting Spark job..."
spark-submit --class com.example.App --master yarn --deploy-mode client "$JAR_FILE"
