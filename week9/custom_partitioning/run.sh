#!/usr/bin/env bash
set -euo pipefail

# Build and run Spark Java apps for retail data joins (default & custom partitioner)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v spark-submit >/dev/null 2>&1; then
  echo "spark-submit not found in PATH. Ensure SPARK is installed in the container and SPARK_HOME/bin is on PATH."
  exit 1
fi

BUILD_DIR=build/classes
JAR_FILE=build/custom_partitioning.jar
SPARK_CONF=(--conf spark.hadoop.fs.defaultFS=file:///)

mkdir -p "$BUILD_DIR"

# Compile against Spark distribution jars
javac -encoding UTF-8 -cp "$SPARK_HOME/jars/*" -d "$BUILD_DIR" \
  RetailDataJoinDefault.java CustomPartitionerRetailDataJoin.java

# Package the compiled classes (no external deps needed at runtime)
jar cf "$JAR_FILE" -C "$BUILD_DIR" .

# Run default join
echo "Running RetailDataJoinDefault..."
spark-submit "${SPARK_CONF[@]}" --class RetailDataJoinDefault "$JAR_FILE"

# Run custom partitioner join
echo "Running CustomPartitionerRetailDataJoin..."
spark-submit "${SPARK_CONF[@]}" --class CustomPartitionerRetailDataJoin "$JAR_FILE"
