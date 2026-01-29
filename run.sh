#!/bin/bash

# Determine script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

echo "Starting StockAccounting..."
echo "Working directory: $(pwd)"

# Determine JAR location and lib directory
if [ -f "dist/lib/StockAccounting.jar" ]; then
    # Running from project root, use Gradle-style dist directory
    echo "Using JAR from dist/lib/"
    JAR_FILE="dist/lib/StockAccounting.jar"
    LIB_DIR="dist/lib"
elif [ -f "dist/StockAccounting.jar" ]; then
    # Legacy dist layout
    echo "Using JAR from dist/ directory"
    cd dist
    JAR_FILE="StockAccounting.jar"
    LIB_DIR="lib"
elif [ -f "StockAccounting.jar" ]; then
    # JAR in current directory
    echo "Using JAR from current directory"
    JAR_FILE="StockAccounting.jar"
    LIB_DIR="lib"
else
    # Fallback to build directory
    echo "No JAR found, trying build directory..."
    java -cp "build:libjar/*" cz.datesoft.stockAccounting.Main 2>&1
    exit $?
fi

# Verify lib directory exists
if [ ! -d "$LIB_DIR" ]; then
    echo "ERROR: Library directory '$LIB_DIR' not found"
    exit 1
fi

echo "Library directory: $LIB_DIR ($(ls "$LIB_DIR"/*.jar 2>/dev/null | wc -l) JARs)"

# Build explicit classpath
CLASSPATH="$JAR_FILE"
for jar in "$LIB_DIR"/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
        echo "Including: $(basename "$jar")"
    fi
done

echo "Final classpath: $CLASSPATH"
echo ""

# Check for debug flag
DEBUG_ARGS=""
if [ "$1" = "--debug" ] || [ "$1" = "-d" ]; then
    echo "Debug mode enabled - setting FINER logging level"
    DEBUG_ARGS="-Dcz.datesoft.stockAccounting.TransformationCache.level=FINER"
fi

# Run application with error capture
java $DEBUG_ARGS -cp "$CLASSPATH" cz.datesoft.stockAccounting.Main 2>&1
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    echo "ERROR: Application exited with code $EXIT_CODE"
    echo "Common issues:"
    echo "  - Missing dependencies in $LIB_DIR/"
    echo "  - Java version compatibility"
    echo "  - Classpath issues"
    exit $EXIT_CODE
fi
