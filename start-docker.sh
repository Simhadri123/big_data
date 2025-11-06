#!/bin/bash

# Quick start script for Big Data Docker Workspace

echo "==========================================="
echo "Big Data Docker Workspace - Quick Start"
echo "==========================================="
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Error: Docker is not installed"
    echo "Please install Docker from: https://docs.docker.com/get-docker/"
    exit 1
fi

echo "✓ Docker is installed"

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "⚠ Warning: Docker Compose is not installed"
    echo "You can still use Docker CLI commands"
    USE_COMPOSE=false
else
    echo "✓ Docker Compose is installed"
    USE_COMPOSE=true
fi

echo ""
echo "Building Docker image (this may take a few minutes)..."

if [ "$USE_COMPOSE" = true ]; then
    docker-compose build
    if [ $? -ne 0 ]; then
        echo "❌ Build failed"
        exit 1
    fi
    
    echo ""
    echo "✓ Build successful!"
    echo ""
    echo "Starting container..."
    docker-compose up -d
    
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start container"
        exit 1
    fi
    
    echo ""
    echo "✓ Container is running!"
    echo ""
    echo "Waiting for Hadoop services to start (15 seconds)..."
    sleep 15
    
    echo ""
    echo "==========================================="
    echo "Setup Complete! 🎉"
    echo "==========================================="
    echo ""
    echo "Access the container:"
    echo "  docker-compose exec hadoop-workspace /bin/bash"
    echo ""
    echo "Run a script:"
    echo "  docker-compose exec hadoop-workspace ./run.sh /workspace/RepJoin/run.sh"
    echo ""
    echo "View web UIs:"
    echo "  HDFS: http://localhost:9870"
    echo "  YARN: http://localhost:8088"
    echo ""
    echo "Stop the container:"
    echo "  docker-compose down"
    echo ""
else
    docker build --progress=plain -t bigdata-hadoop .
    
    if [ $? -ne 0 ]; then
        echo "❌ Build failed"
        exit 1
    fi
    
    echo ""
    echo "✓ Build successful!"
    echo ""
    echo "Starting container..."
    docker run -d \
        --name big-data-container \
        -v $(pwd):/workspace \
        -p 9870:9870 \
        -p 8088:8088 \
        -p 9000:9000 \
        -p 8042:8042 \
        -p 9864:9864 \
        big-data-workspace
    
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start container"
        exit 1
    fi
    
    echo ""
    echo "✓ Container is running!"
    echo ""
    echo "Waiting for Hadoop services to start (15 seconds)..."
    sleep 15
    
    echo ""
    echo "==========================================="
    echo "Setup Complete! 🎉"
    echo "==========================================="
    echo ""
    echo "Access the container:"
    echo "  docker exec -it big-data-container /bin/bash"
    echo ""
    echo "Run a script:"
    echo "  docker exec -it big-data-container ./run.sh /workspace/RepJoin/run.sh"
    echo ""
    echo "View web UIs:"
    echo "  HDFS: http://localhost:9870"
    echo "  YARN: http://localhost:8088"
    echo ""
    echo "Stop the container:"
    echo "  docker stop big-data-container && docker rm big-data-container"
    echo ""
fi

echo "For more information, see DOCKER_README.md"
echo ""
