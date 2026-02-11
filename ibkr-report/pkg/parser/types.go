package parser

import (
	"encoding/csv"
	"fmt"
	"os"
)

// Record represents a single data row with header-to-value mapping
type Record map[string]string

// Section represents a data section in the IBKR CSV file
type Section struct {
	ID      string   // Section ID (e.g., ACCT, FIFO, POST)
	Name    string   // Section name from BOS line
	Headers []string // Column names from HEADER line
	Rows    []Record // Data rows from DATA lines
	Count   int      // Total record count from EOS line
}

// File represents the entire parsed IBKR CSV file
type File struct {
	Path     string              // File path
	Sections map[string]*Section // All sections keyed by ID
	Metadata FileMetadata        // BOF/BOA metadata
}

// FileMetadata contains metadata from BOF and BOA lines
type FileMetadata struct {
	FileID    string
	AccountID string
	Account   string
	StartDate string
	EndDate   string
	Timestamp string
}

// Parser parses IBKR CSV files
type Parser struct {
	filePath string
	file     *File
}

// NewParser creates a new CSV parser
func NewParser(filePath string) *Parser {
	return &Parser{
		filePath: filePath,
		file: &File{
			Path:     filePath,
			Sections: make(map[string]*Section),
		},
	}
}

// openCSV opens the CSV file for reading
func (p *Parser) openCSV() (*os.File, error) {
	file, err := os.Open(p.filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to open file: %w", err)
	}
	return file, nil
}

// Parse reads and parses the CSV file
func (p *Parser) Parse() (*File, error) {
	reader, err := p.openCSV()
	if err != nil {
		return nil, err
	}
	defer reader.Close()

	csvReader := csv.NewReader(reader)
	csvReader.LazyQuotes = true
	csvReader.TrimLeadingSpace = true
	csvReader.FieldsPerRecord = -1 // Allow variable number of fields

	var currentSection *Section

	for {
		record, err := csvReader.Read()
		if err != nil {
			if err.Error() == "EOF" {
				break
			}
			return nil, err
		}

		if len(record) == 0 {
			continue
		}

		recordType := record[0]

		switch recordType {
		case "BOF":
			p.parseBOF(record)
		case "BOA":
			p.parseBOA(record)
		case "BOS":
			currentSection = p.parseBOS(record)
		case "HEADER":
			if currentSection != nil {
				// Create a new slice to avoid shared references
				headers := make([]string, len(record[2:]))
				copy(headers, record[2:])
				currentSection.Headers = headers
			}
		case "DATA":
			if currentSection != nil && len(record) > 2 {
				row := p.parseDataRow(currentSection, record)
				currentSection.Rows = append(currentSection.Rows, row)
			}
		case "EOS":
			if currentSection != nil && len(record) >= 2 {
				p.parseEOS(currentSection, record)
			}
		}
	}

	return p.file, nil
}

// parseBOF parses the Beginning of File line
func (p *Parser) parseBOF(record []string) {
	if len(record) >= 2 {
		p.file.Metadata.FileID = record[1]
	}
	if len(record) >= 8 {
		p.file.Metadata.AccountID = record[1]
		p.file.Metadata.Account = record[2]
		p.file.Metadata.StartDate = record[4]
		p.file.Metadata.EndDate = record[5]
		p.file.Metadata.Timestamp = record[6]
	}
}

// parseBOA parses the Beginning of Account line
func (p *Parser) parseBOA(record []string) {
	// Account info is already in BOF
}

// parseBOS parses the Beginning of Section line
func (p *Parser) parseBOS(record []string) *Section {
	if len(record) >= 3 {
		sectionID := record[1]
		sectionName := record[2]

		section := &Section{
			ID:      sectionID,
			Name:    sectionName,
			Headers: []string{},
			Rows:    []Record{},
		}

		p.file.Sections[sectionID] = section
		return section
	}
	return nil
}

// parseDataRow parses a DATA line into a Record
func (p *Parser) parseDataRow(section *Section, record []string) Record {
	row := make(Record)

	// Skip the first 2 fields: DATA, sectionID
	dataFields := record[2:]

	for i, value := range dataFields {
		if i < len(section.Headers) {
			row[section.Headers[i]] = value
		}
	}

	return row
}

// parseEOS parses the End of Section line
func (p *Parser) parseEOS(section *Section, record []string) {
	if len(record) >= 2 {
		var count int
		fmt.Sscanf(record[1], "%d", &count)
		section.Count = count
	}
}

// GetSection returns a section by ID
func (p *Parser) GetSection(id string) *Section {
	return p.file.Sections[id]
}

// GetAllSections returns all sections
func (p *Parser) GetAllSections() map[string]*Section {
	return p.file.Sections
}

// GetSectionIDs returns all section IDs
func (p *Parser) GetSectionIDs() []string {
	ids := make([]string, 0, len(p.file.Sections))
	for id := range p.file.Sections {
		ids = append(ids, id)
	}
	return ids
}
