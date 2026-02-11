package cli

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"ibkr-report/pkg/parser"

	"github.com/spf13/cobra"
)

// NewListCommand creates the list command
func NewListCommand() *cobra.Command {
	var showHeaders bool
	cmd := &cobra.Command{
		Use:   "list <file>",
		Short: "List all sections in CSV file",
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			runList(cmd, args, showHeaders)
		},
	}
	cmd.Flags().BoolVarP(&showHeaders, "headers", "H", false, "Show headers for each section")
	return cmd
}

func runList(cmd *cobra.Command, args []string, showHeaders bool) {
	filePath := args[0]

	// Validate file
	if err := validateFile(filePath); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	// Parse file
	file, err := parseFile(filePath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	// Get all sections
	sections := file.Sections
	if len(sections) == 0 {
		fmt.Println("No sections found in the file.")
		return
	}

	// Get sorted section IDs
	ids := make([]string, 0, len(sections))
	for id := range sections {
		ids = append(ids, id)
	}
	sort.Strings(ids)

	// Display sections
	if showHeaders {
		fmt.Println(FormatSectionsWithHeaders(sections, ids))
	} else {
		fmt.Println(FormatSections(sections, ids))
	}
}

// validateFile checks if the file exists and is readable
func validateFile(filePath string) error {
	absPath, err := filepath.Abs(filePath)
	if err != nil {
		return fmt.Errorf("failed to get absolute path: %w", err)
	}

	if _, err := os.Stat(absPath); os.IsNotExist(err) {
		return fmt.Errorf("file does not exist: %s", absPath)
	}

	return nil
}

// parseFile parses the CSV file
func parseFile(filePath string) (*parser.File, error) {
	p := parser.NewParser(filePath)
	file, err := p.Parse()
	if err != nil {
		return nil, fmt.Errorf("failed to parse file: %w", err)
	}
	return file, nil
}

// parseFilters parses filter flags from a slice
func parseFilters(filters []string) map[string]string {
	result := make(map[string]string)
	for _, f := range filters {
		parts := strings.SplitN(f, "=", 2)
		if len(parts) == 2 {
			result[parts[0]] = parts[1]
		}
	}
	return result
}

// filterRecords filters records based on column values
func filterRecords(records []parser.Record, filters map[string]string) []parser.Record {
	if len(filters) == 0 {
		return records
	}

	var filtered []parser.Record
	for _, record := range records {
		matches := true
		for col, value := range filters {
			if recordValue, exists := record[col]; !exists || !strings.EqualFold(recordValue, value) {
				matches = false
				break
			}
		}
		if matches {
			filtered = append(filtered, record)
		}
	}
	return filtered
}
