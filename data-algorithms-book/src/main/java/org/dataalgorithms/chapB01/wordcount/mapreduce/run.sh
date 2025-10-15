#!/bin/bash

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
Hello world hello hadoop
Hadoop is a powerful framework
MapReduce is part of hadoop
Hello world programming
Java programming with hadoop
EOF
fi

# Prepare HDFS directories
hdfs dfs -rm -r /user/input /user/output 2>/dev/null
hdfs dfs -mkdir -p /user/input

# Copy input to HDFS
hdfs dfs -put "$INPUT_FILE" /user/input/

# Run WordCount job
hadoop jar wordcount.jar org.dataalgorithms.chapB01.wordcount.mapreduce.WordCountDriver 3 /user/input /user/output

# Show results
hdfs dfs -cat /user/output/part-r-00000
