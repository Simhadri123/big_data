#!/bin/bash
set -e

# Build jar if missing
if [ ! -f multiwayjoin.jar ]; then
	rm -f *.class multiwayjoin.jar || true
	echo "Compiling MultiwayJoin.java..."
	javac -classpath $(hadoop classpath) MultiwayJoin.java
	jar cf multiwayjoin.jar *.class
fi

# Prepare HDFS input and output
hdfs dfs -rm -r /user/inputR /user/inputS /user/inputT /user/output_multiway 2>/dev/null || true
hdfs dfs -mkdir -p /user/inputR /user/inputS /user/inputT
hdfs dfs -put multiway/inputR/R.txt /user/inputR/
hdfs dfs -put multiway/inputS/S.txt /user/inputS/
hdfs dfs -put multiway/inputT/T.txt /user/inputT/
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop

# Explicit HDFS URIs to avoid local FS resolution inside job
hadoop jar multiwayjoin.jar MultiwayJoin hdfs://localhost:8020/user/inputR hdfs://localhost:8020/user/inputS hdfs://localhost:8020/user/inputT hdfs://localhost:8020/user/output_multiway
hdfs dfs -cat /user/output_multiway/part-r-00000