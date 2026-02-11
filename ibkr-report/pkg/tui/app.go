package tui

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"ibkr-report/pkg/parser"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// ViewState represents different views in the TUI
type ViewState int

const (
	ViewList ViewState = iota
	ViewDetail
	ViewFilter
	ViewColumns
)

// Config stores user preferences for column selections per section
type Config struct {
	SectionColumns map[string][]string `json:"section_columns"` // sectionID -> selected columns
}

// State represents the TUI state
type State struct {
	file            *parser.File
	view            ViewState
	sectionID       string
	sectionList     []string
	selectedRow     int
	scroll          int
	columns         []string
	filter          map[string]string
	inputText       string
	errorMsg        string
	showHeaders     bool            // Toggle between data/headers view
	selectedColumns map[string]bool // Track which columns are selected for display
	inColumnSelect  bool            // If in column selection mode
	terminalHeight  int             // Track terminal height for scrolling
	scrollOffset    int             // Scroll offset for long lists
	config          Config          // User preferences for column selections
}

// Model is the main TUI model
type Model struct {
	state  State
	styles Styles
}

// Styles defines the UI styles
type Styles struct {
	base,
	title,
	subtitle,
	selected,
	dim,
	error,
	border lipgloss.Style
}

// NewModel creates a new TUI model
func NewModel(file *parser.File) Model {
	// Get sorted section IDs
	ids := make([]string, 0, len(file.Sections))
	for id := range file.Sections {
		ids = append(ids, id)
	}
	sort.Strings(ids)

	// Load saved config
	config := loadConfig()

	return Model{
		state: State{
			file:            file,
			view:            ViewList,
			sectionList:     ids,
			selectedRow:     0,
			scroll:          0,
			columns:         []string{},
			filter:          make(map[string]string),
			inputText:       "",
			showHeaders:     false,
			selectedColumns: make(map[string]bool),
			inColumnSelect:  false,
			terminalHeight:  25, // Default terminal height
			scrollOffset:    0,
			config:          config,
		},
		styles: defaultStyles(),
	}
}

// getConfigPath returns the path to the config file
func getConfigPath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return ".ibkr-report-config.json"
	}
	return filepath.Join(home, ".config", "ibkr-report", "config.json")
}

// loadConfig loads column preferences from config file
func loadConfig() Config {
	config := Config{
		SectionColumns: make(map[string][]string),
	}

	configPath := getConfigPath()
	data, err := os.ReadFile(configPath)
	if err != nil {
		// Config file doesn't exist yet, return empty config
		return config
	}

	json.Unmarshal(data, &config)
	if config.SectionColumns == nil {
		config.SectionColumns = make(map[string][]string)
	}

	return config
}

// saveConfig saves column preferences to config file
func saveConfig(config Config) error {
	configPath := getConfigPath()

	// Ensure directory exists
	dir := filepath.Dir(configPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	data, err := json.MarshalIndent(config, "", "  ")
	if err != nil {
		return err
	}

	return os.WriteFile(configPath, data, 0644)
}

// applySectionConfig loads saved column selections for current section
func (m *Model) applySectionConfig() {
	section := m.state.file.Sections[m.state.sectionID]
	if section == nil {
		return
	}

	// Clear current selections
	m.state.selectedColumns = make(map[string]bool)

	// Load saved columns for this section
	if savedCols, exists := m.state.config.SectionColumns[m.state.sectionID]; exists {
		for _, col := range savedCols {
			m.state.selectedColumns[col] = true
		}
	}
}

// saveSectionConfig saves current column selections for current section
func (m *Model) saveSectionConfig() {
	if m.state.sectionID == "" {
		return
	}

	// Convert selectedColumns map to slice
	var selectedCols []string
	for col, selected := range m.state.selectedColumns {
		if selected {
			selectedCols = append(selectedCols, col)
		}
	}

	// Save to config
	if len(selectedCols) > 0 {
		m.state.config.SectionColumns[m.state.sectionID] = selectedCols
	} else {
		delete(m.state.config.SectionColumns, m.state.sectionID)
	}

	// Persist to disk
	saveConfig(m.state.config)
}

// Init initializes the model
func (m Model) Init() tea.Cmd {
	return nil
}

// Update handles messages
func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		return m.handleKeyMsg(msg)
	case tea.WindowSizeMsg:
		// Store terminal height for scrolling calculations
		m.state.terminalHeight = msg.Height
		return m, nil
	}
	return m, nil
}

// View renders the UI
func (m Model) View() string {
	switch m.state.view {
	case ViewList:
		return m.renderListView()
	case ViewDetail:
		return m.renderDetailView()
	case ViewFilter:
		return m.renderFilterView()
	case ViewColumns:
		return m.renderColumnsView()
	default:
		return "Unknown view"
	}
}

// handleKeyMsg handles keyboard input
func (m Model) handleKeyMsg(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	switch msg.Type {
	case tea.KeyCtrlC, tea.KeyEsc:
		if m.state.view == ViewDetail {
			// Go back to list
			m.state.view = ViewList
			m.state.selectedRow = 0
			m.state.scroll = 0
		} else {
			return m, tea.Quit
		}
	case tea.KeyEnter:
		return m.handleEnter()
	case tea.KeyUp:
		m.state.selectedRow--
		if m.state.selectedRow < 0 {
			m.state.selectedRow = 0
		}
		// Adjust scroll offset if selection goes above visible area
		if m.state.selectedRow < m.state.scrollOffset {
			m.state.scrollOffset = m.state.selectedRow
		}
	case tea.KeyDown:
		m.state.selectedRow++
		maxRow := m.getMaxRow()
		if m.state.selectedRow > maxRow {
			m.state.selectedRow = maxRow
		}
		// Adjust scroll offset if selection goes below visible area (same logic for both views)
		pageSize := m.state.terminalHeight - 6
		if pageSize < 3 {
			pageSize = 3
		}
		if m.state.selectedRow >= m.state.scrollOffset+pageSize {
			m.state.scrollOffset = m.state.selectedRow - pageSize + 1
		}
	case tea.KeyLeft:
		if m.state.scroll > 0 {
			m.state.scroll--
		}
	case tea.KeyRight:
		m.state.scroll++
	case tea.KeyPgUp:
		// Page Up - scroll up by page size (works in both data and headers view)
		if m.state.view == ViewDetail {
			pageSize := m.state.terminalHeight - 6
			if pageSize < 3 {
				pageSize = 3
			}
			m.state.scrollOffset -= pageSize
			if m.state.scrollOffset < 0 {
				m.state.scrollOffset = 0
			}
			// Position cursor at top of new visible area
			m.state.selectedRow = m.state.scrollOffset
		}
	case tea.KeyPgDown:
		// Page Down - scroll down by page size (works in both data and headers view)
		if m.state.view == ViewDetail {
			section := m.state.file.Sections[m.state.sectionID]
			if section != nil {
				pageSize := m.state.terminalHeight - 6
				if pageSize < 3 {
					pageSize = 3
				}
				var totalItems int
				if m.state.showHeaders {
					totalItems = len(section.Headers)
				} else {
					totalItems = len(section.Rows)
				}
				maxScroll := totalItems - pageSize
				if maxScroll < 0 {
					maxScroll = 0
				}
				m.state.scrollOffset += pageSize
				if m.state.scrollOffset > maxScroll {
					m.state.scrollOffset = maxScroll
				}
				// Position cursor at top of new visible area
				m.state.selectedRow = m.state.scrollOffset
			}
		}
	case tea.KeySpace:
		// Handle Space key to toggle column selection in headers view
		if m.state.view == ViewDetail && m.state.showHeaders && m.state.inColumnSelect {
			section := m.state.file.Sections[m.state.sectionID]
			if section != nil && m.state.selectedRow < len(section.Headers) {
				header := section.Headers[m.state.selectedRow]
				m.state.selectedColumns[header] = !m.state.selectedColumns[header]
			}
		}
	case tea.KeyRunes:
		// Check for 'h' key when in detail view to toggle headers
		if m.state.view == ViewDetail && len(msg.Runes) == 1 && msg.Runes[0] == 'h' {
			if m.state.showHeaders {
				// 'h' does nothing in edit mode (only Enter or Esc return)
			} else {
				// Go directly to edit mode (skip preview)
				m.state.showHeaders = true
				m.state.inColumnSelect = true
				m.state.selectedRow = 0
				m.state.scroll = 0
				m.state.scrollOffset = 0
			}
		}
		// Handle character input for filter
		if m.state.view == ViewFilter {
			m.state.inputText += string(msg.Runes)
		}
	case tea.KeyBackspace:
		if m.state.view == ViewFilter && len(m.state.inputText) > 0 {
			m.state.inputText = m.state.inputText[:len(m.state.inputText)-1]
		}
	}

	// Auto-scroll
	m.autoScroll()

	return m, nil
}

// handleEnter handles Enter key
func (m Model) handleEnter() (tea.Model, tea.Cmd) {
	switch m.state.view {
	case ViewList:
		if m.state.selectedRow < len(m.state.sectionList) {
			m.state.sectionID = m.state.sectionList[m.state.selectedRow]
			m.state.view = ViewDetail
			m.state.selectedRow = 0
			m.state.scroll = 0
			m.state.scrollOffset = 0
			section := m.state.file.Sections[m.state.sectionID]
			m.state.columns = section.Headers
			// Load saved column selections for this section
			m.applySectionConfig()
		}
	case ViewDetail:
		if m.state.showHeaders && m.state.inColumnSelect {
			// Enter returns to data view (applying column selection)
			m.saveSectionConfig() // Save before exiting
			m.state.showHeaders = false
			m.state.inColumnSelect = false
			m.state.selectedRow = 0
			m.state.scrollOffset = 0
		}
	}
	return m, nil
}

// getMaxRow returns the maximum row index
func (m Model) getMaxRow() int {
	switch m.state.view {
	case ViewList:
		return len(m.state.sectionList) - 1
	case ViewDetail:
		section := m.state.file.Sections[m.state.sectionID]
		if section != nil {
			if m.state.showHeaders {
				return len(section.Headers) - 1
			}
			return len(section.Rows) - 1
		}
	}
	return 0
}

// autoScroll adjusts scroll position to keep selection visible
func (m Model) autoScroll() {
	// Simple auto-scroll logic
	// In a real implementation, you'd calculate based on window height
}

// renderListView renders the section list
func (m Model) renderListView() string {
	var sb strings.Builder

	sb.WriteString(m.styles.title.Render("IBKR Report Viewer"))
	sb.WriteString("\n\n")
	sb.WriteString(m.styles.subtitle.Render("Select a section to view:"))
	sb.WriteString("\n\n")

	for i, id := range m.state.sectionList {
		section := m.state.file.Sections[id]
		prefix := "  "
		if i == m.state.selectedRow {
			prefix = "→ "
			sb.WriteString(m.styles.selected.Render(prefix + id))
		} else {
			sb.WriteString(m.styles.dim.Render(prefix + id))
		}

		if section != nil {
			sb.WriteString(m.styles.dim.Render(" - " + section.Name))
		}
		sb.WriteString("\n")
	}

	sb.WriteString("\n")
	sb.WriteString(m.styles.dim.Render("↑/↓: Navigate  |  Enter: View  |  q: Quit"))

	return sb.String()
}

// renderHeadersView renders the section headers with checkboxes (always in edit mode)
func (m Model) renderHeadersView() string {
	var sb strings.Builder

	section := m.state.file.Sections[m.state.sectionID]
	if section == nil {
		sb.WriteString(m.styles.error.Render("Section not found"))
		return sb.String()
	}

	sb.WriteString(m.styles.title.Render("Section: " + section.Name))
	sb.WriteString("\n")

	sb.WriteString(m.styles.subtitle.Render("Select columns to display (Space: toggle, Enter/Esc: back)"))
	sb.WriteString("\n")

	// Calculate visible range based on terminal height
	pageSize := m.state.terminalHeight - 10 // Reserve space for header/footer
	if pageSize < 5 {
		pageSize = 5
	}

	totalHeaders := len(section.Headers)
	startIdx := m.state.scrollOffset
	endIdx := startIdx + pageSize
	if endIdx > totalHeaders {
		endIdx = totalHeaders
	}

	// Show visible headers with checkboxes
	sb.WriteString("\n")
	for i := startIdx; i < endIdx; i++ {
		header := section.Headers[i]
		checked := " "
		if m.state.selectedColumns[header] {
			checked = "[✓]"
		} else {
			checked = "[ ]"
		}

		prefix := "  "
		if i == m.state.selectedRow {
			prefix = "→ "
			sb.WriteString(m.styles.selected.Render(prefix + checked + " " + header))
		} else {
			sb.WriteString(m.styles.dim.Render(prefix + checked + " " + header))
		}
		sb.WriteString("\n")
	}

	// Show pagination info if needed
	if totalHeaders > pageSize {
		sb.WriteString("\n")
		sb.WriteString(m.styles.dim.Render(fmt.Sprintf("Showing %d-%d of %d headers", startIdx+1, endIdx, totalHeaders)))
	}

	// Show selected columns count
	sb.WriteString("\n")
	selectedCount := 0
	for _, header := range section.Headers {
		if m.state.selectedColumns[header] {
			selectedCount++
		}
	}
	if selectedCount > 0 {
		sb.WriteString(m.styles.dim.Render(fmt.Sprintf("Selected: %d columns", selectedCount)))
	}

	sb.WriteString("\n")
	sb.WriteString(m.styles.dim.Render("Space: toggle  |  ↑/↓: Navigate  |  PgUp/PgDn: Scroll  |  Enter/Esc: back  |  q: Quit"))

	return sb.String()
}

// renderDetailView renders the section detail
func (m Model) renderDetailView() string {
	var sb strings.Builder

	section := m.state.file.Sections[m.state.sectionID]
	if section == nil {
		sb.WriteString(m.styles.error.Render("Section not found"))
		return sb.String()
	}

	// Show section header
	sb.WriteString(m.styles.title.Render("Section: " + section.Name))
	sb.WriteString("\n")

	// If showing headers view, display headers
	if m.state.showHeaders {
		return m.renderHeadersView()
	}

	// Otherwise, show data preview with scrolling
	sb.WriteString(m.styles.subtitle.Render("ID: " + section.ID + " | Records: " + fmt.Sprint(len(section.Rows))))
	sb.WriteString("\n\n")

	if len(section.Rows) > 0 {
		// Calculate how many rows can fit in terminal
		availableHeight := m.state.terminalHeight - 6 // Reserve space for header/footer
		if availableHeight < 3 {
			availableHeight = 3
		}
		sb.WriteString(m.renderTablePreview(section, availableHeight))
	} else {
		sb.WriteString(m.styles.dim.Render("No records in this section."))
	}

	sb.WriteString("\n")
	sb.WriteString(m.styles.dim.Render("↑/↓: Scroll  |  h: Show headers  |  Esc: Back to sections  |  q: Quit"))

	return sb.String()
}

// renderTablePreview renders a simple table preview with aligned columns
func (m Model) renderTablePreview(section *parser.Section, maxRows int) string {
	var sb strings.Builder

	// Determine which columns to show
	var headers []string
	selectedCount := 0
	for _, header := range section.Headers {
		if m.state.selectedColumns[header] {
			selectedCount++
		}
	}

	if selectedCount > 0 {
		// Show only selected columns
		headers = make([]string, 0, selectedCount)
		for _, header := range section.Headers {
			if m.state.selectedColumns[header] {
				headers = append(headers, header)
			}
		}
	} else {
		// No selection, show first 5 columns for preview
		maxCols := 5
		if len(section.Headers) > maxCols {
			headers = section.Headers[:maxCols]
		} else {
			headers = section.Headers
		}
	}

	// Calculate visible range using scroll offset
	totalRows := len(section.Rows)
	startIdx := m.state.scrollOffset
	endIdx := startIdx + maxRows
	if endIdx > totalRows {
		endIdx = totalRows
	}

	// Calculate column widths based on headers and visible data
	colWidths := make([]int, len(headers))
	for i, header := range headers {
		colWidths[i] = len(header)
	}

	// Check data values to find maximum width needed
	for i := startIdx; i < endIdx && i < totalRows; i++ {
		row := section.Rows[i]
		for j, header := range headers {
			value := row[header]
			if len(value) > 20 {
				value = value[:17] + "..."
			}
			if len(value) > colWidths[j] {
				colWidths[j] = len(value)
				if colWidths[j] > 20 {
					colWidths[j] = 20
				}
			}
		}
	}

	// Build formatted header
	var headerParts []string
	for i, header := range headers {
		headerParts = append(headerParts, fmt.Sprintf("%-*s", colWidths[i], header))
	}
	sb.WriteString(m.styles.border.Render(strings.Join(headerParts, " | ")))
	sb.WriteString("\n")

	// Build separator line
	var sepParts []string
	for _, width := range colWidths {
		sepParts = append(sepParts, strings.Repeat("-", width))
	}
	sb.WriteString(m.styles.border.Render(strings.Join(sepParts, "-+-")))
	sb.WriteString("\n")

	// Data rows with alignment
	for i := startIdx; i < endIdx; i++ {
		row := section.Rows[i]
		var values []string
		for j, header := range headers {
			value := row[header]
			if len(value) > 20 {
				value = value[:17] + "..."
			}
			// Left-align text, pad to column width
			values = append(values, fmt.Sprintf("%-*s", colWidths[j], value))
		}

		prefix := "  "
		rowStr := strings.Join(values, " | ")
		// Check if this is the selected row (accounting for scroll offset)
		if i == m.state.selectedRow {
			prefix = "→ "
			sb.WriteString(m.styles.selected.Render(prefix + rowStr))
		} else {
			sb.WriteString(m.styles.dim.Render(prefix + rowStr))
		}
		sb.WriteString("\n")
	}

	// Show pagination info
	if totalRows > maxRows {
		sb.WriteString("\n")
		sb.WriteString(m.styles.dim.Render(fmt.Sprintf("Showing %d-%d of %d records", startIdx+1, endIdx, totalRows)))
		sb.WriteString("\n")
	}

	return sb.String()
}

func (m Model) renderFilterView() string {
	var sb strings.Builder

	sb.WriteString(m.styles.title.Render("Filter Records"))
	sb.WriteString("\n\n")
	sb.WriteString("Enter filter value: ")
	sb.WriteString(m.state.inputText)
	sb.WriteString("\n\n")
	sb.WriteString(m.styles.dim.Render("Type filter and press Enter  |  Esc: Cancel"))

	return sb.String()
}

// renderColumnsView renders the column selector
func (m Model) renderColumnsView() string {
	var sb strings.Builder

	sb.WriteString(m.styles.title.Render("Select Columns"))
	sb.WriteString("\n\n")
	sb.WriteString(m.styles.dim.Render("Use arrow keys and Enter  |  Esc: Back"))

	return sb.String()
}

// defaultStyles creates the default UI styles with high contrast for readability
func defaultStyles() Styles {
	return Styles{
		base:     lipgloss.NewStyle(),
		title:    lipgloss.NewStyle().Foreground(lipgloss.Color("51")).Bold(true),  // Cyan
		subtitle: lipgloss.NewStyle().Foreground(lipgloss.Color("15")),             // Bright white
		selected: lipgloss.NewStyle().Foreground(lipgloss.Color("82")).Bold(true),  // Bright green
		dim:      lipgloss.NewStyle().Foreground(lipgloss.Color("252")),            // Light gray (not dark)
		error:    lipgloss.NewStyle().Foreground(lipgloss.Color("196")).Bold(true), // Bright red
		border:   lipgloss.NewStyle().Foreground(lipgloss.Color("248")),            // Medium gray
	}
}
