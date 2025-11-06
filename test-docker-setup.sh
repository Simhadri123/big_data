#!/bin/bash

# Test script to verify Docker setup works correctly
# This script should be run inside the Docker container

echo "============================================"
echo "Docker Container Verification Test"
echo "============================================"
echo ""

# Test 1: Check Java
echo "[Test 1] Checking Java installation..."
if java -version 2>&1 | grep -q "version"; then
    echo "✓ Java is installed"
    java -version 2>&1 | head -1
else
    echo "✗ Java is not installed correctly"
    exit 1
fi
echo ""

# Test 2: Check Hadoop
echo "[Test 2] Checking Hadoop installation..."
if [ -d "$HADOOP_HOME" ]; then
    echo "✓ HADOOP_HOME exists at $HADOOP_HOME"
else
    echo "✗ HADOOP_HOME not found"
    exit 1
fi

if hadoop version 2>&1 | grep -q "Hadoop"; then
    echo "✓ Hadoop command is available"
    hadoop version 2>&1 | head -1
else
    echo "✗ Hadoop command not working"
    exit 1
fi
echo ""

# Test 3: Check HDFS
echo "[Test 3] Checking HDFS..."
if hdfs version 2>&1 | grep -q "Hadoop"; then
    echo "✓ HDFS command is available"
else
    echo "✗ HDFS command not working"
    exit 1
fi
echo ""

# Test 4: Check workspace mount
echo "[Test 4] Checking workspace mount..."
if [ -d "/workspace" ]; then
    echo "✓ /workspace directory exists"
    file_count=$(find /workspace -name "run.sh" -type f 2>/dev/null | wc -l)
    echo "  Found $file_count run.sh scripts in workspace"
else
    echo "✗ /workspace directory not found"
    exit 1
fi
echo ""

# Test 5: Check wrapper script
echo "[Test 5] Checking wrapper script..."
if [ -f "/run.sh" ] && [ -x "/run.sh" ]; then
    echo "✓ Wrapper script exists and is executable"
else
    echo "✗ Wrapper script missing or not executable"
    exit 1
fi
echo ""

# Test 6: Check Hadoop services (if running)
echo "[Test 6] Checking Hadoop services..."
jps_output=$(jps 2>/dev/null)
if echo "$jps_output" | grep -q "NameNode"; then
    echo "✓ NameNode is running"
else
    echo "⚠ NameNode is not running (may need to start services)"
fi

if echo "$jps_output" | grep -q "DataNode"; then
    echo "✓ DataNode is running"
else
    echo "⚠ DataNode is not running (may need to start services)"
fi

if echo "$jps_output" | grep -q "ResourceManager"; then
    echo "✓ ResourceManager is running"
else
    echo "⚠ ResourceManager is not running (may need to start services)"
fi
echo ""

# Test 7: Test HDFS basic operations (if services are running)
if echo "$jps_output" | grep -q "NameNode"; then
    echo "[Test 7] Testing HDFS operations..."
    hdfs dfs -mkdir -p /test 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✓ Can create HDFS directories"
        hdfs dfs -rm -r /test 2>/dev/null
    else
        echo "⚠ Could not create HDFS directory (services may still be starting)"
    fi
else
    echo "[Test 7] Skipping HDFS operations test (services not running)"
fi
echo ""

echo "============================================"
echo "Verification Complete!"
echo "============================================"
echo ""
echo "Summary:"
echo "- Java: ✓ Installed"
echo "- Hadoop: ✓ Installed"
echo "- Workspace: ✓ Mounted"
echo "- Wrapper script: ✓ Ready"
echo ""
echo "You can now run your scripts with:"
echo "  ./run.sh /workspace/<path-to-script>"
echo ""
echo "To start Hadoop services (if not running):"
echo "  \$HADOOP_HOME/sbin/start-dfs.sh"
echo "  \$HADOOP_HOME/sbin/start-yarn.sh"
echo ""
