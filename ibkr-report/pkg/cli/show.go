package cli

import (
	"fmt"
	"os"
	"sort"
	"strings"

	"ibkr-report/pkg/parser"

	"github.com/spf13/cobra"
)

// NewShowCommand creates the show command
func NewShowCommand() *cobra.Command {
	var columns string
	var filters []string
	var sortBy string
	var descending bool
	var showAll bool
	var noColor bool

	cmd := &cobra.Command{
		Use:   "show <file> <section_id>",
		Short: "Show a specific section with its data",
		Args:  cobra.ExactArgs(2),
		Run: func(cmd *cobra.Command, args []string) {
			runShow(cmd, args, columns, filters, sortBy, descending, showAll, noColor)
		},
	}

	cmd.Flags().StringVarP(&columns, "columns", "c", "", "Comma-separated list of columns to display")
	cmd.Flags().StringArrayVarP(&filters, "col", "f", []string{}, "Filter by column (format: column=value)")
	cmd.Flags().StringVar(&sortBy, "sort", "", "Sort by column name")
	cmd.Flags().BoolVarP(&descending, "desc", "d", false, "Sort in descending order")
	cmd.Flags().BoolVarP(&showAll, "all", "a", false, "Show all columns (no truncation)")
	cmd.Flags().BoolVar(&noColor, "no-color", false, "Disable colored output")

	return cmd
}

func runShow(cmd *cobra.Command, args []string, columns string, filters []string, sortBy string, descending bool, showAll bool, noColor bool) {
	filePath := args[0]
	sectionID := strings.ToUpper(args[1])

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

	// Find section
	section, exists := file.Sections[sectionID]
	if !exists {
		fmt.Fprintf(os.Stderr, "Section '%s' not found. Available sections: %s\n", sectionID, strings.Join(getSectionIDs(file), ", "))
		os.Exit(1)
	}

	// Parse filters
	filterMap := parseFilters(filters)

	// Filter records
	records := filterRecords(section.Rows, filterMap)

	// Sort records
	if sortBy != "" {
		records = sortRecords(records, sortBy, descending)
	}

	// Select columns
	selectedColumns := section.Headers
	if columns != "" {
		selectedColumns = strings.Split(columns, ",")
		// Trim whitespace
		for i := range selectedColumns {
			selectedColumns[i] = strings.TrimSpace(selectedColumns[i])
		}
	}

	// Validate columns
	validColumns := validateColumns(selectedColumns, section.Headers)
	if len(validColumns) == 0 {
		fmt.Fprintf(os.Stderr, "No valid columns selected. Available columns: %s\n", strings.Join(section.Headers, ", "))
		os.Exit(1)
	}

	// Display table
	table := FormatTable(section.Name, validColumns, records, showAll, noColor)
	fmt.Println(table)
}

// getSectionIDs returns all section IDs in sorted order
func getSectionIDs(file *parser.File) []string {
	ids := make([]string, 0, len(file.Sections))
	for id := range file.Sections {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	return ids
}

// sortRecords sorts records by column
func sortRecords(records []parser.Record, column string, descending bool) []parser.Record {
	// Create a copy to avoid modifying original
	sorted := make([]parser.Record, len(records))
	copy(sorted, records)

	sort.SliceStable(sorted, func(i, j int) bool {
		valI, existsI := sorted[i][column]
		valJ, existsJ := sorted[j][column]

		// Handle missing values
		if !existsI {
			return !descending
		}
		if !existsJ {
			return descending
		}

		// Compare as strings (simpler approach)
		cmp := strings.Compare(valI, valJ)
		if descending {
			return cmp > 0
		}
		return cmp < 0
	})

	return sorted
}

// validateColumns filters and validates column names
func validateColumns(requested []string, available []string) []string {
	// Create a set of available columns
	availableSet := make(map[string]bool)
	for _, col := range available {
		availableSet[col] = true
	}

	var valid []string
	for _, col := range requested {
		if availableSet[col] {
			valid = append(valid, col)
		}
	}

	return valid
}
