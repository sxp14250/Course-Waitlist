#!/usr/bin/env bash
# Runs every manual acceptance script through the CLI and prints the transcripts.
# Usage (from the project root):  ./mvnw -q package -DskipTests && manual-tests/run-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=target/course-waitlist-1.0.0.jar
[ -f "$JAR" ] || { echo "Build first: ./mvnw package"; exit 1; }
for script in manual-tests/ac*.txt; do
  echo "=================================================================="
  echo " $script"
  echo "=================================================================="
  java -jar "$JAR" < "$script"
  echo
done
