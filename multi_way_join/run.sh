#!/bin/bash
hdfs dfs -rm -r /user/inputR /user/inputS /user/inputT /user/output_multiway 2>/dev/null
hdfs dfs -mkdir -p /user/inputR /user/inputS /user/inputT
hdfs dfs -put multiway/inputR/R.txt /user/inputR/
hdfs dfs -put multiway/inputS/S.txt /user/inputS/
hdfs dfs -put multiway/inputT/T.txt /user/inputT/
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
hadoop jar multiwayjoin.jar MultiwayJoin hdfs://localhost:9000/user/inputR hdfs://localhost:9000/user/inputS hdfs://localhost:9000/user/inputT hdfs://localhost:9000/user/output_multiway
hdfs dfs -cat /user/output_multiway/part-r-00000