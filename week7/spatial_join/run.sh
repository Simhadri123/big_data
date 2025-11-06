#!/usr/bin/env bash
set -euo pipefail

# Compile MapReduce spatial join and run it on HDFS input
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

BUILD_DIR=build/classes
JAR_FILE=build/spatial_join.jar
MAIN_CLASS=week6.rect_cont_join.rect_cont_Runner

mkdir -p "$BUILD_DIR"

# Compile
javac -encoding UTF-8 -cp "$(hadoop classpath)" -d "$BUILD_DIR" *.java

# Package
jar cf "$JAR_FILE" -C "$BUILD_DIR" .

# HDFS paths
IN_DIR=/user/week7/spatial_join/input
OUT_DIR=/user/week7/spatial_join/output

# Prepare HDFS
hdfs dfs -mkdir -p "$IN_DIR" || true
hdfs dfs -put -f input.txt "$IN_DIR"/input.txt
hdfs dfs -rm -r -f "$OUT_DIR" || true

# Args: <input_path> <output_path> <k> <cell_width> <cell_height>
K=3
CELL_W=100
CELL_H=100

echo "Running Hadoop job: $MAIN_CLASS"
hadoop jar "$JAR_FILE" "$MAIN_CLASS" "$IN_DIR" "$OUT_DIR" "$K" "$CELL_W" "$CELL_H"

echo "Job finished. Listing results:"
hdfs dfs -ls "$OUT_DIR"/final_output || true
hdfs dfs -cat "$OUT_DIR"/final_output/* 2>/dev/null | head -n 100 || true
