#!/bin/bash
set -e   # stop if any command fails

# Step 1: compile and build jar
javac -classpath `hadoop classpath` -d . MotifNoPartition.java
jar cf motifnopartition.jar MotifNoPartition*.class

# Step 2: prepare HDFS input
hdfs dfs -mkdir -p /input
hdfs dfs -rm -f /input/edges        # remove old copy if exists
hdfs dfs -put edges.txt /input/edges

# Step 3: cleanup old outputs if any
hdfs dfs -rm -r -f /tmp/adj /output/motifs

# Step 4: run the job
hadoop jar motifnopartition.jar MotifNoPartition /input/edges /tmp/adj /output/motifs

# Step 5: view results
echo "=== Motif Counting Output ==="
hdfs dfs -cat /output/motifs/part-r-00000
