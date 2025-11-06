# Big Data Docker Container - Complete Setup Guide

## Overview

This repository now includes a complete Docker container setup that allows you to run any `run.sh` script from your workspace with all necessary dependencies (Java, Hadoop, etc.) pre-installed.

## 📁 Files Added

### Core Docker Files
- **`Dockerfile`** - Defines the container image with Ubuntu 22.04, Java 8, Hadoop 3.3.4, and all dependencies
- **`docker-compose.yml`** - Simplifies container management with pre-configured ports and volume mounts
- **`.dockerignore`** - Excludes unnecessary files from the Docker build context

### Scripts
- **`start-docker.sh`** - Quick start script to build and launch the container
- **`test-docker-setup.sh`** - Verification script to test the container setup
- **`/run.sh`** (in container) - Wrapper script that executes workspace scripts

### Documentation
- **`DOCKER_README.md`** - Complete usage guide and reference
- **`BUILD_NOTE.md`** - Important notes about building the Docker image
- **`USAGE_EXAMPLES.md`** - Step-by-step examples for common tasks
- **`README_DOCKER_SETUP.md`** (this file) - Quick overview and getting started

## 🚀 Quick Start

### One-Command Setup

```bash
./start-docker.sh
```

This script will:
1. Check if Docker is installed
2. Build the container image
3. Start the container
4. Wait for Hadoop services to initialize
5. Display access information

### Manual Setup

```bash
# Build the image
docker-compose build

# Start the container
docker-compose up -d

# Access the container
docker-compose exec hadoop-workspace /bin/bash

# Run a script
./run.sh /workspace/RepJoin/run.sh
```

## 🎯 Key Features

### 1. Workspace Mounting
Your local directory is mounted at `/workspace` in the container, so:
- All your files are accessible inside the container
- Changes made in the container reflect on your host machine
- No need to copy files back and forth

### 2. Script Execution
The wrapper script `/run.sh` in the container:
- Takes a path to any `run.sh` script in your workspace
- Changes to that script's directory
- Executes the script in its native context
- Handles permissions automatically

### 3. Pre-installed Dependencies
- **Java 8** - Required for Hadoop and MapReduce jobs
- **Hadoop 3.3.4** - Complete Hadoop distribution
- **HDFS** - Hadoop Distributed File System
- **YARN** - Resource management
- **Essential tools** - ssh, wget, vim, curl, etc.

### 4. Web UIs
Access Hadoop web interfaces from your browser:
- HDFS NameNode: http://localhost:9870
- YARN ResourceManager: http://localhost:8088
- YARN NodeManager: http://localhost:8042
- HDFS DataNode: http://localhost:9864

## 📝 Usage Examples

### Example 1: Run RepJoin Script
```bash
docker-compose exec hadoop-workspace ./run.sh /workspace/RepJoin/run.sh
```

### Example 2: Run Multiple Scripts
```bash
docker-compose exec hadoop-workspace ./run.sh /workspace/RepJoin/run.sh
docker-compose exec hadoop-workspace ./run.sh /workspace/map_reduce_join/run.sh
docker-compose exec hadoop-workspace ./run.sh /workspace/counting_triangles_no_partition/run.sh
```

### Example 3: Interactive Development
```bash
# Enter container
docker-compose exec hadoop-workspace /bin/bash

# Navigate and work
cd /workspace/RepJoin
vim CRepJoin.java
javac -classpath $(hadoop classpath) CRepJoin.java
./run.sh

# Exit
exit
```

### Example 4: List All Available Scripts
```bash
docker-compose exec hadoop-workspace find /workspace -name "run.sh" -type f
```

## 🔍 Verification

To verify the container is set up correctly:

```bash
docker-compose exec hadoop-workspace /test-docker-setup.sh
```

This will check:
- ✓ Java installation
- ✓ Hadoop installation
- ✓ HDFS availability
- ✓ Workspace mounting
- ✓ Hadoop services status

## 📚 Documentation

For more detailed information, see:

1. **[DOCKER_README.md](DOCKER_README.md)** - Complete reference guide
2. **[USAGE_EXAMPLES.md](USAGE_EXAMPLES.md)** - 12 detailed examples with commands
3. **[BUILD_NOTE.md](BUILD_NOTE.md)** - Build requirements and notes

## 🛠 Common Commands

```bash
# Start container
docker-compose up -d

# Stop container
docker-compose down

# View logs
docker-compose logs -f

# Execute command
docker-compose exec hadoop-workspace <command>

# Access container shell
docker-compose exec hadoop-workspace /bin/bash

# Run a script from host
docker-compose exec hadoop-workspace ./run.sh /workspace/<path-to-script>
```

## 🏗 Architecture

```
Host Machine
├── big_data/ (your workspace)
│   ├── RepJoin/
│   │   └── run.sh
│   ├── map_reduce_join/
│   │   └── run.sh
│   └── ... (other projects)
│
Docker Container
├── /workspace → (mounted from host)
├── /opt/hadoop → (Hadoop installation)
├── /run.sh → (wrapper script)
└── Services: NameNode, DataNode, ResourceManager, NodeManager
```

## 📋 Requirements

- **Docker** - Version 20.10 or higher
- **Docker Compose** - Version 1.29 or higher (optional but recommended)
- **System Resources** - At least 4GB RAM and 10GB disk space
- **Internet Access** - Required for initial build to download Hadoop

## 🔧 Troubleshooting

### Build Fails to Download Hadoop
- Ensure Docker has internet access
- Check if your network allows access to archive.apache.org
- See BUILD_NOTE.md for details

### Services Not Starting
```bash
docker-compose exec hadoop-workspace /bin/bash
$HADOOP_HOME/sbin/start-all.sh
```

### Port Conflicts
If ports are already in use, edit `docker-compose.yml` and change the port mappings:
```yaml
ports:
  - "19870:9870"  # Use different host port
  - "18088:8088"
```

### HDFS Issues
```bash
docker-compose exec hadoop-workspace /bin/bash
hdfs namenode -format -force
$HADOOP_HOME/sbin/start-dfs.sh
```

## 🎓 Learning Resources

The container provides a complete Hadoop environment for learning and development:

1. **HDFS Commands**: Practice distributed file system operations
2. **MapReduce Jobs**: Run and debug MapReduce programs
3. **YARN**: Understand resource management
4. **Hadoop Configuration**: Experiment with settings

## 🤝 Contributing

If you find issues or have improvements:
1. The Dockerfile can be customized for additional tools
2. The wrapper script can be enhanced with more features
3. Additional documentation examples are welcome

## 📄 License

This Docker setup is provided as-is for use with your big data projects.

## ✅ What's Included

- ✅ Complete Docker environment
- ✅ Java 8 installed
- ✅ Hadoop 3.3.4 with HDFS and YARN
- ✅ SSH configured for Hadoop
- ✅ Workspace auto-mounted
- ✅ Wrapper script for easy execution
- ✅ Web UIs accessible
- ✅ Comprehensive documentation
- ✅ Example usage scenarios
- ✅ Verification test script
- ✅ Quick start script

## 🎉 You're Ready!

Your Docker container setup is complete and ready to use. Run any of your `run.sh` scripts with:

```bash
./start-docker.sh  # First time setup
# Then:
docker-compose exec hadoop-workspace ./run.sh /workspace/your-project/run.sh
```

Happy coding! 🚀
