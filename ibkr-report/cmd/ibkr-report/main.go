package main

import (
	"fmt"
	"os"
	"path/filepath"

	"ibkr-report/pkg/cli"
	"ibkr-report/pkg/parser"

	"github.com/spf13/cobra"
)

var rootCmd = &cobra.Command{
	Use:   "ibkr-report",
	Short: "Interactive CLI tool for parsing IBKR stock reports",
	Long:  `ibkr-report is a CLI tool that parses IBKR CSV reports and displays them with various filtering and viewing options.`,
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			cmd.Help()
			os.Exit(1)
		}
	},
}

func main() {
	rootCmd.AddCommand(cli.NewListCommand())
	rootCmd.AddCommand(cli.NewShowCommand())
	rootCmd.AddCommand(cli.NewTUICommand())

	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
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
