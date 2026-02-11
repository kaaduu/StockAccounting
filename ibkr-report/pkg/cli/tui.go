package cli

import (
	"fmt"
	"os"

	"ibkr-report/pkg/tui"

	"github.com/charmbracelet/bubbletea"
	"github.com/spf13/cobra"
)

// NewTUICommand creates the TUI command
func NewTUICommand() *cobra.Command {
	return &cobra.Command{
		Use:   "tui <file>",
		Short: "Launch interactive TUI mode",
		Args:  cobra.ExactArgs(1),
		Run:   runTUI,
	}
}

func runTUI(cmd *cobra.Command, args []string) {
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

	// Start TUI
	p := tea.NewProgram(tui.NewModel(file))
	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "TUI error: %v\n", err)
		os.Exit(1)
	}
}
