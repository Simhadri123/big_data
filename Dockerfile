# Big Data Workspace Dockerfile
# Ubuntu + Java 8 + Hadoop 3.3.x + Spark 3.5.x

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Versions
ARG HADOOP_VERSION=3.3.6
ARG SPARK_VERSION=3.5.1
ARG SPARK_PACKAGE=spark-${SPARK_VERSION}-bin-hadoop3

# Base packages
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       openjdk-8-jdk \
       ssh \
       rsync \
       curl \
       wget \
       vim \
       nano \
       ca-certificates \
       net-tools \
       iputils-ping \
       gnupg \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# Hadoop
WORKDIR /opt
RUN set -eux; \
    wget -q https://dlcdn.apache.org/hadoop/common/hadoop-3.4.1/hadoop-3.4.1.tar.gz; \
    tar -xzf hadoop-3.4.1.tar.gz; \
    rm hadoop-3.4.1.tar.gz; \
    ln -s /opt/hadoop-3.4.1 /opt/hadoop

ENV HADOOP_HOME=/opt/hadoop
ENV HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
ENV PATH=$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH

# Spark
RUN set -eux; \
    wget -q https://dlcdn.apache.org/spark/spark-3.5.7/spark-3.5.7-bin-hadoop3.tgz; \
    tar -xzf spark-3.5.7-bin-hadoop3.tgz; \
    rm spark-3.5.7-bin-hadoop3.tgz; \
    ln -s /opt/spark-3.5.7-bin-hadoop3 /opt/spark

ENV SPARK_HOME=/opt/spark
ENV PATH=$SPARK_HOME/bin:$PATH

# Hadoop configuration
RUN set -eux; \
    mkdir -p /hadoop/dfs/name /hadoop/dfs/data; \
    sed -i "s|^export JAVA_HOME=.*|export JAVA_HOME=${JAVA_HOME}|" $HADOOP_CONF_DIR/hadoop-env.sh; \
    bash -lc 'cat > $HADOOP_CONF_DIR/core-site.xml <<EOF\n<?xml version="1.0"?>\n<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>\n<configuration>\n  <property>\n    <name>fs.defaultFS</name>\n    <value>hdfs://localhost:8020</value>\n  </property>\n</configuration>\nEOF'; \
    bash -lc 'cat > $HADOOP_CONF_DIR/hdfs-site.xml <<EOF\n<?xml version="1.0"?>\n<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>\n<configuration>\n  <property>\n    <name>dfs.replication</name>\n    <value>1</value>\n  </property>\n  <property>\n    <name>dfs.namenode.name.dir</name>\n    <value>file:///hadoop/dfs/name</value>\n  </property>\n  <property>\n    <name>dfs.datanode.data.dir</name>\n    <value>file:///hadoop/dfs/data</value>\n  </property>\n</configuration>\nEOF'; \
    bash -lc 'cat > $HADOOP_CONF_DIR/yarn-site.xml <<EOF\n<?xml version="1.0"?>\n<configuration>\n  <property>\n    <name>yarn.nodemanager.aux-services</name>\n    <value>mapreduce_shuffle</value>\n  </property>\n  <property>\n    <name>yarn.resourcemanager.hostname</name>\n    <value>0.0.0.0</value>\n  </property>\n</configuration>\nEOF'; \
    bash -lc 'cat > $HADOOP_CONF_DIR/mapred-site.xml <<EOF\n<?xml version="1.0"?>\n<configuration>\n  <property>\n    <name>mapreduce.framework.name</name>\n    <value>yarn</value>\n  </property>\n</configuration>\nEOF'

# SSH setup for Hadoop
RUN set -eux; \
    mkdir -p /var/run/sshd /root/.ssh; \
    ssh-keygen -t rsa -P "" -f /root/.ssh/id_rsa; \
    cat /root/.ssh/id_rsa.pub >> /root/.ssh/authorized_keys; \
    chmod 600 /root/.ssh/authorized_keys

# Wrapper script to run workspace jobs (embedded to avoid COPY issues)
RUN bash -lc 'cat > /run.sh <<"EOF"\n#!/usr/bin/env bash\nset -euo pipefail\n\nif [ $# -lt 1 ]; then\n  echo "Usage: /run.sh /workspace/path/to/run.sh [args...]"\n  exit 1\nfi\n\nSCRIPT_PATH="$1"\nshift || true\n\nif [ ! -f "$SCRIPT_PATH" ]; then\n  echo "Error: script not found: $SCRIPT_PATH"\n  exit 1\nfi\n\n# Start SSH\nservice ssh start || true\n\n# Ensure HDFS formatted\nif [ ! -d /hadoop/dfs/name/current ]; then\n  echo "[run.sh] Formatting HDFS namenode..."\n  ${HADOOP_HOME}/bin/hdfs namenode -format -force\nfi\n\n# Start Hadoop services (idempotent)\n${HADOOP_HOME}/sbin/start-dfs.sh || true\n${HADOOP_HOME}/sbin/start-yarn.sh || true\n\n# Show running JVM processes for visibility\ncommand -v jps >/dev/null 2>&1 && jps || true\n\n# Change to the script directory and execute\nSCRIPT_DIR="$(dirname "$SCRIPT_PATH")"\nSCRIPT_BASE="$(basename "$SCRIPT_PATH")"\ncd "$SCRIPT_DIR"\nchmod +x "$SCRIPT_BASE" || true\nexec "./$SCRIPT_BASE" "$@"\nEOF' \
    && chmod +x /run.sh

# Entrypoint: start SSH, format HDFS if needed, then start HDFS & YARN, keep container alive
RUN bash -lc 'cat > /usr/local/bin/docker-entrypoint.sh <<"EOF"\n#!/usr/bin/env bash\nset -euo pipefail\n\nservice ssh start\n\n# Ensure JAVA_HOME is set in Hadoop env\nsed -i "s|^export JAVA_HOME=.*|export JAVA_HOME=${JAVA_HOME}|" ${HADOOP_CONF_DIR}/hadoop-env.sh || true\n\n# Format HDFS only if not formatted\nif [ ! -d /hadoop/dfs/name/current ]; then\n  echo "Formatting HDFS namenode..."\n  ${HADOOP_HOME}/bin/hdfs namenode -format -force\nfi\n\n# Start Hadoop services\n${HADOOP_HOME}/sbin/start-dfs.sh || true\n${HADOOP_HOME}/sbin/start-yarn.sh || true\n\n# Print jps for visibility\ncommand -v jps >/dev/null 2>&1 && jps || true\n\nexec "$@"\nEOF' \
    && chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 9870 8088 9000 8042 9864 4040 18080

WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["bash", "-lc", "tail -f /dev/null"]
