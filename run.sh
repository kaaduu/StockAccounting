#!/bin/bash

echo "Starting StockAccounting..."

# Run the application
java -cp "build:libjar/*" cz.datesoft.stockAccounting.Main
