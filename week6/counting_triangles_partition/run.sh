#!/bin/bash
set -e  # stop if any command fails

# ===== Configuration =====
RHO=8                # number of partitions (can change)
INPUT_FILE="edges.txt"
HDFS_INPUT="/input/edges"
OUTPUT_DIR="/output/partition"
TEMP_DIR="/output/partition_temp"

# ===== Step 1: Compile Java code and build jar =====
echo "Compiling Partition.java..."
javac -classpath `hadoop classpath` -d . Partition.java
echo "Creating partition.jar..."
jar cf partition.jar Partition*.class

# ===== Step 2: Prepare HDFS input =====
echo "Creating HDFS input directory..."
hdfs dfs -mkdir -p /input
echo "Removing old HDFS input (if any)..."
hdfs dfs -rm -f $HDFS_INPUT
echo "Uploading $INPUT_FILE to HDFS..."
hdfs dfs -put $INPUT_FILE $HDFS_INPUT

# ===== Step 3: Cleanup old outputs =====
echo "Removing old output directories (if any)..."
hdfs dfs -rm -r -f $OUTPUT_DIR
hdfs dfs -rm -r -f $TEMP_DIR

# ===== Step 4: Run the Partition job =====
echo "Running Partition job..."
hadoop jar partition.jar Partition $HDFS_INPUT $RHO $OUTPUT_DIR

# ===== Step 5: Show final results =====
echo "=== Partition Motif Counting Output ==="
hdfs dfs -cat $OUTPUT_DIR/part-r-00000
