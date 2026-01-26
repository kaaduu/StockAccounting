#!/bin/bash

set -e

echo "Building StockAccounting (Gradle)..."

if [ ! -x "./gradlew" ]; then
  echo "ERROR: ./gradlew not found."
  echo "This project now builds via Gradle wrapper."
  exit 1
fi

./gradlew --no-daemon clean installDist

rm -rf dist
mkdir -p dist

# Preserve historical dist layout used by run.sh
cp -r build/install/StockAccounting/* dist/

echo "Build successful! Distribution ready in 'dist' folder."
