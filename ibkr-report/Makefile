BINARY=ibkr-report
VERSION=$(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")
LDFLAGS=-ldflags "-X main.Version=$(VERSION)"

.PHONY: build build-linux clean install run test deps

# Build for current platform
build:
	@echo "Building $(BINARY)..."
	@go build $(LDFLAGS) -o bin/$(BINARY) cmd/ibkr-report/main.go
	@echo "Built bin/$(BINARY)"

# Build for Linux AMD64
build-linux:
	@echo "Building $(BINARY) for Linux AMD64..."
	@GOOS=linux GOARCH=amd64 go build $(LDFLAGS) -o bin/$(BINARY)-linux-amd64 cmd/ibkr-report/main.go
	@echo "Built bin/$(BINARY)-linux-amd64"

# Clean build artifacts
clean:
	@echo "Cleaning..."
	@rm -rf bin/
	@echo "Cleaned"

# Install to /usr/local/bin (requires sudo)
install: build
	@echo "Installing $(BINARY) to /usr/local/bin..."
	@sudo cp bin/$(BINARY) /usr/local/bin/
	@echo "Installed $(BINARY)"

# Run the binary
run:
	@go run cmd/ibkr-report/main.go

# Run tests
test:
	@echo "Running tests..."
	@go test -v ./...

# Download dependencies
deps:
	@echo "Downloading dependencies..."
	@go mod download
	@go mod tidy
	@echo "Dependencies ready"

# Format code
fmt:
	@echo "Formatting code..."
	@go fmt ./...

# Lint code
lint:
	@echo "Linting code..."
	@golangci-lint run ./...

# Development: build and test with sample file
dev: build
	@echo "Testing with sample CSV..."
	@./bin/$(BINARY) list ../2025_U15.csv

# Help
help:
	@echo "Available targets:"
	@echo "  build        - Build for current platform"
	@echo "  build-linux  - Build for Linux AMD64"
	@echo "  clean        - Remove build artifacts"
	@echo "  install      - Install to /usr/local/bin"
	@echo "  run          - Run with go run"
	@echo "  test         - Run tests"
	@echo "  deps         - Download dependencies"
	@echo "  fmt          - Format code"
	@echo "  lint         - Lint code"
	@echo "  dev          - Build and test with sample file"
	@echo "  help         - Show this help message"
