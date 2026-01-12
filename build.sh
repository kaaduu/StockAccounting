#!/bin/bash

# Exit on error
set -e

echo "Building StockAccounting..."

# Create build directory
mkdir -p build

# Compile Java files
echo "Compiling..."
javac -d build -cp "libjar/*" $(find src -name "*.java")

# Copy resources
echo "Copying resources..."
mkdir -p build/cz/datesoft/stockAccounting/images
cp -v src/cz/datesoft/stockAccounting/images/*.png build/cz/datesoft/stockAccounting/images/

echo "Build successful!"
