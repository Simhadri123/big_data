# Big Data Docker Workspace

This Docker setup provides a complete Hadoop environment for running your big data scripts. The container includes Java, Hadoop, and all necessary dependencies.

## Features

- **Java 8**: Required for Hadoop and MapReduce jobs
- **Hadoop 3.3.6**: Complete Hadoop distribution with HDFS and YARN
- **Workspace Mounting**: Your local directory is mounted at `/workspace` in the container
- **Easy Script Execution**: Run any `run.sh` script using the wrapper command
- **Web UIs**: Access Hadoop web interfaces from your browser

## Prerequisites

- Docker installed on your system
- Docker Compose (optional, but recommended)

## Quick Start

### Using Docker Compose (Recommended)

1. **Build and start the container:**
   ```bash
   docker-compose up -d
   ```

2. **Access the container:**
   ```bash
   docker-compose exec hadoop-workspace /bin/bash
   ```

3. **Run any script from your workspace:**
   ```bash
   ./run.sh /workspace/<path-to-script>
   ```

   For example:
   ```bash
   ./run.sh /workspace/RepJoin/run.sh
   ./run.sh /workspace/map_reduce_join/run.sh
   ./run.sh /workspace/counting_triangles_no_partition/run.sh
   ```

4. **Stop the container:**
   ```bash
   docker-compose down
   ```

### Using Docker CLI

1. **Build the image:**
   ```bash
   docker build -t big-data-workspace .
   ```

2. **Run the container:**
   ```bash
   docker run -it --rm \
     -v $(pwd):/workspace \
     -p 9870:9870 \
     -p 8088:8088 \
     -p 9000:9000 \
     --name big-data-container \
     big-data-workspace
   ```

3. **Run scripts inside the container:**
   ```bash
   ./run.sh /workspace/<path-to-script>
   ```

## Available Scripts

Your workspace contains multiple `run.sh` scripts in different directories:

- `RepJoin/run.sh` - Replicated join implementation
- `map_reduce_join/run.sh` - MapReduce join example
- `counting_triangles_no_partition/run.sh` - Triangle counting without partitioning
- `counting_triangles_partition/run.sh` - Triangle counting with partitioning
- `multi_way_join/run.sh` - Multi-way join implementation
- `join_no_sort/run.sh` - Join without sorting
- And more...

## Web UIs

Once the container is running, you can access the following web interfaces:

- **HDFS NameNode UI**: http://localhost:9870
- **YARN ResourceManager UI**: http://localhost:8088
- **YARN NodeManager UI**: http://localhost:8042
- **HDFS DataNode UI**: http://localhost:9864

## How the Wrapper Script Works

The `/run.sh` wrapper script in the container:

1. Validates that the script path exists
2. Changes to the script's directory
3. Makes the script executable if needed
4. Executes the script in its native directory

This ensures that scripts can access their local files and dependencies correctly.

## Example Workflow

```bash
# Start the container
docker-compose up -d

# Enter the container
docker-compose exec hadoop-workspace /bin/bash

# Inside the container, list available scripts
find /workspace -name "run.sh" -type f

# Run a specific script
./run.sh /workspace/RepJoin/run.sh

# Or navigate directly and run
cd /workspace/RepJoin
./run.sh

# Exit the container
exit

# Stop the container when done
docker-compose down
```

## Troubleshooting

### HDFS Issues

If you encounter HDFS errors, the namenode might need formatting:
```bash
$HADOOP_HOME/bin/hdfs namenode -format -force
$HADOOP_HOME/sbin/start-dfs.sh
```

### Service Status

Check if Hadoop services are running:
```bash
jps  # Should show NameNode, DataNode, ResourceManager, NodeManager
```

### Restart Services

If services are down:
```bash
$HADOOP_HOME/sbin/stop-all.sh
$HADOOP_HOME/sbin/start-all.sh
```

### Permission Issues

If scripts aren't executable:
```bash
chmod +x /workspace/path/to/run.sh
```

## Notes

- The container automatically starts Hadoop services on startup
- HDFS data is stored inside the container (not persistent by default)
- Scripts run with the permissions of the container user
- The workspace directory is mounted from your host machine

## Advanced Usage

### Running in Background

The container can run detached with services always available:
```bash
docker-compose up -d
# Use docker-compose exec to run commands as needed
```

### Direct Script Execution

You can also run scripts without entering the container:
```bash
docker-compose exec hadoop-workspace ./run.sh /workspace/RepJoin/run.sh
```

### Viewing Logs

```bash
docker-compose logs -f hadoop-workspace
```

## Environment Variables

The container includes these environment variables:

- `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64`
- `HADOOP_HOME=/opt/hadoop`
- `HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop`
- All Hadoop binaries are in the PATH

## Support

For issues or questions about specific scripts, refer to the individual script documentation or comments within each `run.sh` file.
