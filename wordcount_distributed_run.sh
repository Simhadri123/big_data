#!/bin/bash

# Distributed Mode Configuration
MASTER_NODE="hdfs://master-node-ip:9000"  # Replace with actual master IP
REPLICATION_FACTOR=3  # Number of replicas

echo "=== Running WordCount in Distributed Mode ==="
echo "Master Node: $MASTER_NODE"
echo "Replication Factor: $REPLICATION_FACTOR"

# Clean previous builds
rm -f *.class *.jar

# Compile Java files
javac -classpath $(hadoop classpath) *.java || exit 1

# Create proper directory structure and JAR file
mkdir -p org/dataalgorithms/chapB01/wordcount/mapreduce
cp *.class org/dataalgorithms/chapB01/wordcount/mapreduce/
jar cf wordcount.jar org/
rm -rf org/

# Prepare input file
INPUT_FILE="/home/simhadri/422247_big_data/input.txt"
if [ ! -f "$INPUT_FILE" ]; then
    cat > "$INPUT_FILE" << EOF
Hello world hello hadoop distributed computing
Hadoop cluster with multiple nodes running
MapReduce jobs across worker nodes
Distributed processing with HDFS replication
YARN resource management in cluster mode
EOF
fi

# Prepare HDFS directories with explicit HDFS URLs
hdfs dfs -rm -r /user/input /user/output 2>/dev/null
hdfs dfs -mkdir -p /user/input

# Set replication factor and copy input to HDFS
hdfs dfs -Ddfs.replication=$REPLICATION_FACTOR -put "$INPUT_FILE" /user/input/

# Verify replication
echo "=== Verifying HDFS Replication ==="
hdfs dfs -stat "Replication: %r, Size: %s bytes" /user/input/input.txt

# Run WordCount job with distributed configuration
echo "=== Running MapReduce Job ==="
hadoop jar wordcount.jar org.dataalgorithms.chapB01.wordcount.mapreduce.WordCountDriver 3 /user/input /user/output

# Show results and replication info
echo "=== Job Results ==="
hdfs dfs -cat /user/output/part-r-00000
echo ""
echo "=== HDFS Block Information ==="
hdfs fsck /user/output/part-r-00000 -files -blocks -locations

echo "=== Cluster Status ==="
hdfs dfsadmin -report