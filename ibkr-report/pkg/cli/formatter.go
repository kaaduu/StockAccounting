package cli

import (
	"fmt"
	"math"
	"sort"
	"strconv"
	"strings"

	"ibkr-report/pkg/parser"
)

// maxColumnWidth is the maximum width for any column
const maxColumnWidth = 50

// FormatSections creates a formatted ASCII table listing all sections
func FormatSections(sections map[string]*parser.Section, ids []string) string {
	if len(ids) == 0 {
		return "No sections found."
	}

	// Calculate column widths
	idWidth := len("ID")
	nameWidth := len("Name")

	for _, id := range ids {
		if section, exists := sections[id]; exists {
			if len(id) > idWidth {
				idWidth = len(id)
			}
			if len(section.Name) > nameWidth {
				nameWidth = len(section.Name)
			}
		}
	}

	// Build output
	var builder strings.Builder

	// Header
	builder.WriteString(fmt.Sprintf("%-*s | %s\n", idWidth, "ID", strings.Repeat("-", nameWidth)))

	// Separator
	builder.WriteString(strings.Repeat("-", idWidth))
	builder.WriteString("-+")
	builder.WriteString(strings.Repeat("-", nameWidth))
	builder.WriteString("\n")

	// Rows
	for _, id := range ids {
		if section, exists := sections[id]; exists {
			builder.WriteString(fmt.Sprintf("%-*s | %s (%d rows)\n", idWidth, id, section.Name, len(section.Rows)))
		}
	}

	return builder.String()
}

// FormatSectionsWithHeaders creates a formatted list showing sections with their headers
func FormatSectionsWithHeaders(sections map[string]*parser.Section, ids []string) string {
	if len(ids) == 0 {
		return "No sections found."
	}

	// Build output
	var builder strings.Builder

	// Header
	builder.WriteString("ID   | ")
	builder.WriteString(strings.Repeat("-", 60))
	builder.WriteString(" | Records | Headers\n")
	builder.WriteString("-----+")
	builder.WriteString(strings.Repeat("-", 60))
	builder.WriteString("-+---------+--------------------------------------------------\n")

	// Rows
	for _, id := range ids {
		if section, exists := sections[id]; exists {
			headersLine := strings.Join(section.Headers, "|")
			builder.WriteString(fmt.Sprintf("%-5s | %-60s | %-8d | %s\n",
				id,
				section.Name,
				len(section.Rows),
				headersLine))
		}
	}

	return builder.String()
}

// FormatTable creates a formatted ASCII table for section data
func FormatTable(sectionName string, headers []string, records []parser.Record, showAll bool, noColor bool) string {
	if len(records) == 0 {
		return fmt.Sprintf("Section: %s\nNo records found.", sectionName)
	}

	// Calculate column widths
	colWidths := calculateColumnWidths(headers, records, showAll)

	// Build output
	var builder strings.Builder

	// Section header
	builder.WriteString(fmt.Sprintf("Section: %s\n", sectionName))
	builder.WriteString(fmt.Sprintf("Records: %d\n", len(records)))
	builder.WriteString("\n")

	// Table header
	buildHeader(&builder, headers, colWidths)

	// Separator
	buildSeparator(&builder, colWidths)

	// Data rows
	for _, record := range records {
		buildRow(&builder, headers, record, colWidths, noColor)
	}

	return builder.String()
}

// calculateColumnWidths calculates the width for each column
func calculateColumnWidths(headers []string, records []parser.Record, showAll bool) map[int]int {
	widths := make(map[int]int)

	// Initialize with header widths
	for i, header := range headers {
		widths[i] = len(header)
	}

	// Find maximum width from data
	for _, record := range records {
		for i, header := range headers {
			value := record[header]
			displayWidth := len(value)
			if !showAll && displayWidth > maxColumnWidth {
				displayWidth = maxColumnWidth + 3 // For "..."
			}
			if displayWidth > widths[i] {
				widths[i] = displayWidth
			}
		}
	}

	return widths
}

// buildHeader builds the table header row
func buildHeader(builder *strings.Builder, headers []string, widths map[int]int) {
	for i, header := range headers {
		width := widths[i]
		if i > 0 {
			builder.WriteString(" | ")
		}
		builder.WriteString(fmt.Sprintf("%-*s", width, header))
	}
	builder.WriteString("\n")
}

// buildSeparator builds the table separator line
func buildSeparator(builder *strings.Builder, widths map[int]int) {
	keys := make([]int, 0, len(widths))
	for k := range widths {
		keys = append(keys, k)
	}
	sort.Ints(keys)

	for i, col := range keys {
		width := widths[col]
		if i > 0 {
			builder.WriteString("-+-")
		}
		builder.WriteString(strings.Repeat("-", width))
	}
	builder.WriteString("\n")
}

// buildRow builds a single data row
func buildRow(builder *strings.Builder, headers []string, record parser.Record, widths map[int]int, noColor bool) {
	for i, header := range headers {
		value := record[header]
		width := widths[i]

		// Truncate if too long
		if len(value) > maxColumnWidth {
			value = value[:maxColumnWidth-3] + "..."
		}

		// Apply color for numeric values (PnL columns)
		coloredValue := colorizeValue(value, header, noColor)

		if i > 0 {
			builder.WriteString(" | ")
		}
		builder.WriteString(fmt.Sprintf("%-*s", width, coloredValue))
	}
	builder.WriteString("\n")
}

// colorizeValue applies color coding for numeric values
func colorizeValue(value string, header string, noColor bool) string {
	if noColor {
		return value
	}

	// Check if this is a PnL column
	isPNL := strings.Contains(strings.ToLower(header), "pnl") ||
		strings.Contains(strings.ToLower(header), "profit") ||
		strings.Contains(strings.ToLower(header), "loss")

	if !isPNL {
		return value
	}

	// Try to parse as float
	floatVal, err := strconv.ParseFloat(value, 64)
	if err != nil {
		return value
	}

	// Color based on value
	if math.Abs(floatVal) < 0.01 {
		return value // Near zero, no color
	}

	if floatVal > 0 {
		return fmt.Sprintf("\033[32m%s\033[0m", value) // Green
	}
	return fmt.Sprintf("\033[31m%s\033[0m", value) // Red
}
