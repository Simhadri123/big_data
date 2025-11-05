# Base image with Java and essential tools
FROM ubuntu:22.04

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV HADOOP_HOME=/opt/hadoop
ENV HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
ENV HADOOP_MAPRED_HOME=$HADOOP_HOME
ENV HADOOP_COMMON_HOME=$HADOOP_HOME
ENV HADOOP_HDFS_HOME=$HADOOP_HOME
ENV YARN_HOME=$HADOOP_HOME
ENV PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$JAVA_HOME/bin

# Install dependencies
RUN apt-get update && apt-get install -y \
    openjdk-8-jdk \
    wget \
    ssh \
    pdsh \
    rsync \
    vim \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Download and install Hadoop 3.3.6
RUN wget -q https://dlcdn.apache.org/hadoop/common/hadoop-3.3.6/hadoop-3.3.6.tar.gz && \
    tar -xzf hadoop-3.3.6.tar.gz && \
    mv hadoop-3.3.6 /opt/hadoop && \
    rm hadoop-3.3.6.tar.gz

# Configure SSH for Hadoop
RUN ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa && \
    cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys && \
    chmod 0600 ~/.ssh/authorized_keys

# Create necessary directories
RUN mkdir -p /opt/hadoop/hdfs/namenode && \
    mkdir -p /opt/hadoop/hdfs/datanode && \
    mkdir -p /workspace

# Configure Hadoop
RUN echo '<?xml version="1.0" encoding="UTF-8"?>\n\
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>\n\
<configuration>\n\
  <property>\n\
    <name>fs.defaultFS</name>\n\
    <value>hdfs://localhost:9000</value>\n\
  </property>\n\
  <property>\n\
    <name>hadoop.tmp.dir</name>\n\
    <value>/opt/hadoop/tmp</value>\n\
  </property>\n\
</configuration>' > $HADOOP_CONF_DIR/core-site.xml

RUN echo '<?xml version="1.0" encoding="UTF-8"?>\n\
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>\n\
<configuration>\n\
  <property>\n\
    <name>dfs.replication</name>\n\
    <value>1</value>\n\
  </property>\n\
  <property>\n\
    <name>dfs.namenode.name.dir</name>\n\
    <value>file:///opt/hadoop/hdfs/namenode</value>\n\
  </property>\n\
  <property>\n\
    <name>dfs.datanode.data.dir</name>\n\
    <value>file:///opt/hadoop/hdfs/datanode</value>\n\
  </property>\n\
</configuration>' > $HADOOP_CONF_DIR/hdfs-site.xml

RUN echo '<?xml version="1.0"?>\n\
<configuration>\n\
  <property>\n\
    <name>mapreduce.framework.name</name>\n\
    <value>yarn</value>\n\
  </property>\n\
  <property>\n\
    <name>mapreduce.application.classpath</name>\n\
    <value>$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/*:$HADOOP_MAPRED_HOME/share/hadoop/mapreduce/lib/*</value>\n\
  </property>\n\
</configuration>' > $HADOOP_CONF_DIR/mapred-site.xml

RUN echo '<?xml version="1.0"?>\n\
<configuration>\n\
  <property>\n\
    <name>yarn.nodemanager.aux-services</name>\n\
    <value>mapreduce_shuffle</value>\n\
  </property>\n\
  <property>\n\
    <name>yarn.nodemanager.env-whitelist</name>\n\
    <value>JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PREPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_MAPRED_HOME</value>\n\
  </property>\n\
</configuration>' > $HADOOP_CONF_DIR/yarn-site.xml

# Set JAVA_HOME in hadoop-env.sh
RUN echo "export JAVA_HOME=$JAVA_HOME" >> $HADOOP_CONF_DIR/hadoop-env.sh

# Format namenode (will be done in entrypoint if not already formatted)
RUN mkdir -p /opt/hadoop/tmp

# Create entrypoint script
RUN echo '#!/bin/bash\n\
\n\
# Start SSH service\n\
service ssh start\n\
\n\
# Format namenode if not already formatted\n\
if [ ! -d "/opt/hadoop/hdfs/namenode/current" ]; then\n\
  echo "Formatting HDFS namenode..."\n\
  $HADOOP_HOME/bin/hdfs namenode -format -force\n\
fi\n\
\n\
# Start Hadoop services\n\
echo "Starting Hadoop services..."\n\
$HADOOP_HOME/sbin/start-dfs.sh\n\
$HADOOP_HOME/sbin/start-yarn.sh\n\
\n\
# Wait for services to be ready\n\
sleep 10\n\
\n\
# Create HDFS user directory\n\
$HADOOP_HOME/bin/hdfs dfs -mkdir -p /user\n\
$HADOOP_HOME/bin/hdfs dfs -chmod 777 /user\n\
\n\
echo "Hadoop services started successfully!"\n\
echo "HDFS Web UI: http://localhost:9870"\n\
echo "YARN Web UI: http://localhost:8088"\n\
echo ""\n\
echo "Workspace mounted at: /workspace"\n\
echo "Usage: ./run.sh /workspace/<path-to-script>"\n\
\n\
# Execute command or keep container running\n\
if [ "$#" -eq 0 ]; then\n\
  exec /bin/bash\n\
else\n\
  exec "$@"\n\
fi' > /entrypoint.sh

RUN chmod +x /entrypoint.sh

# Create the wrapper run.sh script
RUN echo '#!/bin/bash\n\
\n\
if [ "$#" -ne 1 ]; then\n\
  echo "Usage: ./run.sh /workspace/<path-to-script>"\n\
  echo ""\n\
  echo "Available scripts:"\n\
  find /workspace -name "run.sh" -type f 2>/dev/null | sed "s|^/workspace/||"\n\
  exit 1\n\
fi\n\
\n\
SCRIPT_PATH="$1"\n\
\n\
if [ ! -f "$SCRIPT_PATH" ]; then\n\
  echo "Error: Script not found at $SCRIPT_PATH"\n\
  exit 1\n\
fi\n\
\n\
if [ ! -x "$SCRIPT_PATH" ]; then\n\
  echo "Making script executable..."\n\
  chmod +x "$SCRIPT_PATH"\n\
fi\n\
\n\
SCRIPT_DIR=$(dirname "$SCRIPT_PATH")\n\
SCRIPT_NAME=$(basename "$SCRIPT_PATH")\n\
\n\
echo "Changing to directory: $SCRIPT_DIR"\n\
cd "$SCRIPT_DIR" || exit 1\n\
\n\
echo "Executing: ./$SCRIPT_NAME"\n\
echo "=========================================="\n\
./"$SCRIPT_NAME"' > /run.sh

RUN chmod +x /run.sh

# Set working directory
WORKDIR /workspace

# Expose ports for Hadoop services
EXPOSE 9870 8088 9000 8042 9864

# Set entrypoint
ENTRYPOINT ["/entrypoint.sh"]
CMD ["/bin/bash"]
