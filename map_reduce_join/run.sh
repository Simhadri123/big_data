#!/bin/bash

# Clean previous builds
rm -f *.class *.jar

# Compile Java file
javac -classpath $(hadoop classpath) SGPACGPAJoin.java || exit 1

# Create JAR
jar cf SGPACGPAJoin.jar *.class || exit 1

# Prepare HDFS directories
hdfs dfs -rm -r /user/gpa_input /user/gpa_output 2>/dev/null
hdfs dfs -mkdir -p /user/gpa_input

# Copy CSV files to HDFS
hdfs dfs -put *.csv /user/gpa_input/

# Run SGPA/CGPA calculation job
hadoop jar SGPACGPAJoin.jar SGPACGPAJoin /user/gpa_input /user/gpa_output

# Display results
hdfs dfs -head /user/gpa_output/part-r-00000
