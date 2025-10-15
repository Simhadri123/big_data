#!/bin/bash
rm -f *.class *.jar
javac -classpath $(hadoop classpath) SortJoin.java
jar cf sortjoin.jar *.class
hdfs dfs -rm -r /user/inputR /user/inputS /user/output_sortjoin 2>/dev/null
hdfs dfs -mkdir -p /user/inputR /user/inputS
hdfs dfs -put inputR/R.txt /user/inputR/
hdfs dfs -put inputS/S.txt /user/inputS/
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
hadoop jar sortjoin.jar SortJoin hdfs://localhost:9000/user/inputR hdfs://localhost:9000/user/inputS hdfs://localhost:9000/user/output_sortjoin
hdfs dfs -cat /user/output_sortjoin/part-r-00000