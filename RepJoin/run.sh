#!/bin/bash
rm -f *.class *.jar
rm -rf build
mkdir -p build
javac -classpath $(hadoop classpath) -d build CRepJoin.java
cd build
jar cf ../crepjoin.jar *.class
cd ..
hdfs dfs -rm -r /user/spatial_input /user/spatial_output /user/spatial_output_phase1 2>/dev/null
hdfs dfs -mkdir -p /user/spatial_input
hdfs dfs -put spatial_data.txt /user/spatial_input/
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
hadoop jar crepjoin.jar CRepJoin /user/spatial_input /user/spatial_output
hdfs dfs -cat /user/spatial_output/part-r-00000