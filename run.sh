#!/bin/bash

# Determine script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

echo "Starting StockAccounting..."

# Run the application (checks for JAR first, then build folder)
if [ -f "StockAccounting.jar" ]; then
    java -cp "StockAccounting.jar:lib/*" cz.datesoft.stockAccounting.Main
else
    java -cp "build:libjar/*" cz.datesoft.stockAccounting.Main
fi
