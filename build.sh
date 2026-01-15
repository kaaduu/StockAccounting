#!/bin/bash

# Exit on error
set -e

echo "Building StockAccounting..."

# Create build directory
mkdir -p build
rm -rf build/*

 # Compile Java files
 echo "Compiling with Java 17 compatibility..."
 /usr/lib/jvm/java-17-openjdk-amd64/bin/javac --release 17 -d build -cp "libjar/*" $(find src -name "*.java")

 # Copy resources
 echo "Copying resources..."
 mkdir -p build/cz/datesoft/stockAccounting/images
 cp -v src/cz/datesoft/stockAccounting/images/*.png build/cz/datesoft/stockAccounting/images/

 # Generate version information (before JAR creation)
 echo "Generating version information..."
 if command -v git >/dev/null 2>&1; then
     git describe --tags --always > build/version.txt 2>/dev/null || echo "dev-build" > build/version.txt
 else
     echo "dev-build" > build/version.txt
 fi

 # Create version.properties for runtime access
 echo "version=$(cat build/version.txt)" > build/version.properties

 # Create distribution directory
 mkdir -p dist/lib
 rm -rf dist/*
 mkdir -p dist/lib

 # Package JAR (now includes version.properties)
 echo "Packaging JAR..."
 jar cfm dist/StockAccounting.jar manifest.mf -C build .
 echo "JAR packaged successfully"

 # Copy dependencies
 echo "Copying dependencies..."
 cp libjar/*.jar dist/lib/

 # Copy version.properties to dist for reference
 cp build/version.properties dist/ 2>/dev/null || true

 # Copy launchers
 echo "Copying launchers..."
 cp run.sh dist/
 cp run.bat dist/ 2>/dev/null || true
 chmod +x dist/run.sh

echo "Build successful! Distribution ready in 'dist' folder."
