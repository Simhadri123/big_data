# Docker Setup Usage Examples

This document provides step-by-step examples of using the Docker container for your big data workspace.

## Example 1: First Time Setup

```bash
# 1. Navigate to your repository directory
cd /path/to/big_data

# 2. Build the Docker image (first time only, takes 5-10 minutes)
docker-compose build

# 3. Start the container
docker-compose up -d

# 4. Wait for services to initialize (about 10-15 seconds)
sleep 15

# 5. Verify the setup
docker-compose exec hadoop-workspace /test-docker-setup.sh
```

## Example 2: Running a Specific Script

### Run RepJoin Example

```bash
# Enter the container
docker-compose exec hadoop-workspace /bin/bash

# Inside the container, run the script
./run.sh /workspace/RepJoin/run.sh

# Exit when done
exit
```

### Run Map Reduce Join Example

```bash
docker-compose exec hadoop-workspace ./run.sh /workspace/map_reduce_join/run.sh
```

### Run Triangle Counting Example

```bash
docker-compose exec hadoop-workspace ./run.sh /workspace/counting_triangles_no_partition/run.sh
```

## Example 3: Interactive Development

```bash
# Start container
docker-compose up -d

# Enter container with interactive shell
docker-compose exec hadoop-workspace /bin/bash

# Now you're inside the container, you can:

# 1. Navigate to any project directory
cd /workspace/RepJoin

# 2. Make changes to files (they're synced with your host)
vim CRepJoin.java

# 3. Compile manually
javac -classpath $(hadoop classpath) -d build CRepJoin.java

# 4. Run the local run.sh
./run.sh

# 5. Check HDFS
hdfs dfs -ls /user

# 6. View Hadoop logs
cat $HADOOP_HOME/logs/*.log

# Exit when done
exit
```

## Example 4: Access Web UIs

While the container is running, you can access these web interfaces from your browser:

```bash
# Start container
docker-compose up -d

# Open in browser:
# - HDFS NameNode: http://localhost:9870
# - YARN ResourceManager: http://localhost:8088
# - YARN NodeManager: http://localhost:8042
# - HDFS DataNode: http://localhost:9864
```

## Example 5: Running Multiple Scripts

```bash
# Run scripts one after another
docker-compose exec hadoop-workspace ./run.sh /workspace/RepJoin/run.sh
docker-compose exec hadoop-workspace ./run.sh /workspace/map_reduce_join/run.sh
docker-compose exec hadoop-workspace ./run.sh /workspace/multi_way_join/run.sh
```

## Example 6: Checking Hadoop Status

```bash
# Enter container
docker-compose exec hadoop-workspace /bin/bash

# Check which Java processes are running
jps

# Expected output should include:
# - NameNode
# - DataNode
# - ResourceManager
# - NodeManager
# - SecondaryNameNode

# Check HDFS status
hdfs dfsadmin -report

# Check YARN status
yarn node -list

# Exit
exit
```

## Example 7: Troubleshooting

### Services Not Running

```bash
# Enter container
docker-compose exec hadoop-workspace /bin/bash

# Stop all services
$HADOOP_HOME/sbin/stop-all.sh

# Start all services
$HADOOP_HOME/sbin/start-all.sh

# Or start individually
$HADOOP_HOME/sbin/start-dfs.sh
$HADOOP_HOME/sbin/start-yarn.sh

# Check status
jps
```

### HDFS Issues

```bash
# Enter container
docker-compose exec hadoop-workspace /bin/bash

# Format namenode (WARNING: deletes all HDFS data)
hdfs namenode -format -force

# Restart HDFS
$HADOOP_HOME/sbin/start-dfs.sh

# Verify
hdfs dfs -ls /
```

### Clean Start

```bash
# Stop and remove container
docker-compose down

# Optionally, remove the image to rebuild fresh
docker rmi big-data-workspace

# Rebuild and start
docker-compose build
docker-compose up -d
```

## Example 8: Listing All Available Scripts

```bash
# From host machine
docker-compose exec hadoop-workspace find /workspace -name "run.sh" -type f

# Or inside container
docker-compose exec hadoop-workspace /bin/bash
find /workspace -name "run.sh" -type f
```

## Example 9: Viewing Output

```bash
# Run a script
docker-compose exec hadoop-workspace ./run.sh /workspace/RepJoin/run.sh

# The output will be displayed directly in your terminal
# To save output to a file on host:
docker-compose exec hadoop-workspace ./run.sh /workspace/RepJoin/run.sh > output.txt 2>&1
```

## Example 10: Working with HDFS from Host

```bash
# Enter container
docker-compose exec hadoop-workspace /bin/bash

# Put file to HDFS
hdfs dfs -put /workspace/input.txt /user/input.txt

# Get file from HDFS
hdfs dfs -get /user/output/part-r-00000 /workspace/output.txt

# Now output.txt is available on your host machine too
exit
ls output.txt  # on host
```

## Example 11: Daily Workflow

```bash
# Morning: Start the container
docker-compose up -d

# Work: Run your scripts as needed
docker-compose exec hadoop-workspace ./run.sh /workspace/your-project/run.sh

# Evening: Stop the container (keeps data)
docker-compose stop

# Next day: Restart
docker-compose start

# Weekend: Full cleanup
docker-compose down
```

## Example 12: Debugging a Failed Script

```bash
# Enter container interactively
docker-compose exec hadoop-workspace /bin/bash

# Navigate to the script directory
cd /workspace/RepJoin

# Check what files are there
ls -la

# Check if data files exist
cat spatial_data.txt

# Try compiling manually to see errors
javac -classpath $(hadoop classpath) CRepJoin.java

# Check HDFS
hdfs dfs -ls /user

# Run with verbose output
bash -x ./run.sh

# Check Hadoop logs
tail -f $HADOOP_HOME/logs/hadoop-*-namenode-*.log
```

## Common Commands Reference

```bash
# Container management
docker-compose up -d          # Start container in background
docker-compose down           # Stop and remove container
docker-compose stop           # Stop container (keep data)
docker-compose start          # Restart stopped container
docker-compose restart        # Restart container
docker-compose logs -f        # View container logs

# Execute commands in container
docker-compose exec hadoop-workspace <command>
docker-compose exec hadoop-workspace /bin/bash

# Run scripts
./run.sh /workspace/<path-to-script>  # Inside container
docker-compose exec hadoop-workspace ./run.sh /workspace/<path>  # From host

# Hadoop commands (inside container)
hadoop version                # Check Hadoop version
hdfs dfs -ls /                # List HDFS root
hdfs dfsadmin -report         # HDFS status
yarn node -list               # YARN nodes
jps                           # Java processes
```

## Tips

1. **Always start the container before working**: `docker-compose up -d`
2. **Your files are synced**: Changes on host reflect in container and vice versa
3. **HDFS data is inside the container**: It's lost when you `docker-compose down` (use volumes to persist)
4. **Port conflicts**: If ports 9870, 8088, etc. are in use, stop the container or modify docker-compose.yml
5. **Performance**: Container runs with host resources, adjust Docker Desktop settings if needed
