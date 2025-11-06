#!/usr/bin/env bash
set -euo pipefail

# Build and run Spark Java app for SetSimilarityJoin
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v spark-submit >/dev/null 2>&1; then
  echo "spark-submit not found in PATH. Ensure SPARK is installed in the container and SPARK_HOME/bin is on PATH."
  exit 1
fi

BUILD_DIR=build/classes
JAR_FILE=build/set_similarity_join.jar
SPARK_CONF=(--conf spark.hadoop.fs.defaultFS=file:///)

mkdir -p "$BUILD_DIR"

# Compile
javac -encoding UTF-8 -cp "$SPARK_HOME/jars/*" -d "$BUILD_DIR" SetSimilarityJoin.java

# Package
jar cf "$JAR_FILE" -C "$BUILD_DIR" .

# Run
echo "Running SetSimilarityJoin..."
spark-submit "${SPARK_CONF[@]}" --class SetSimilarityJoin "$JAR_FILE"
