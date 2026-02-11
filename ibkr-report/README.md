# IBKR Report Viewer

CLI tool for parsing and viewing IBKR (Interactive Brokers) CSV reports with interactive TUI mode.

## Features

- **CLI Mode**: List and view sections with filtering options
- **TUI Mode**: Interactive terminal interface for browsing reports
- **Column Selection**: Display only the columns you need
- **Filtering**: Filter records by any column value
- **Sorting**: Sort records by any column
- **Color Coding**: Green for positive PnL, red for negative

## Installation

### Build from source

```bash
# Build for Linux
make build-linux

# Or build for current platform
make build

# Install to /usr/local/bin (requires sudo)
make install
```

## Usage

### List all sections

```bash
ibkr-report list <file.csv>
```

Example:
```bash
ibkr-report list 2025_U15.csv
```

#### Show section headers

Use `--headers` flag to display all column headers for each section:

```bash
ibkr-report list <file.csv> --headers
```

Output:
```
ID   | ---------------------------------------------------------------
-----+---------------------------------------------------------------
ACCT | Account Information (1 rows)
FIFO | Realized & Unrealized Performance Summary in Base (257 rows)
POST | Position; trade date basis (54 rows)
TRNT | Trades; trade date basis (1738 rows)
...
```

### Show a section

```bash
ibkr-report show <file.csv> <section_id>
```

Example:
```bash
ibkr-report show 2025_U15.csv FIFO
```

### Select specific columns

```bash
ibkr-report show <file.csv> <section_id> --columns col1,col2,col3
```

Example:
```bash
ibkr-report show 2025_U15.csv FIFO --columns Symbol,Description,TotalFifoPnl
```

### Filter by column

```bash
ibkr-report show <file.csv> <section_id> --col column=value
```

Example:
```bash
# Show only stocks
ibkr-report show 2025_U15.csv FIFO --col AssetClass=STK

# Multiple filters
ibkr-report show 2025_U15.csv FIFO --col AssetClass=STK --col Symbol=NVDA
```

### Sort records

```bash
# Sort ascending
ibkr-report show <file.csv> <section_id> --sort column

# Sort descending
ibkr-report show <file.csv> <section_id> --sort column --desc
```

Example:
```bash
ibkr-report show 2025_U15.csv FIFO --sort TotalFifoPnl --desc
```

### Disable colors

```bash
ibkr-report show <file.csv> <section_id> --no-color
```

### TUI Mode

Launch interactive mode:

```bash
ibkr-report tui <file.csv>
```

#### TUI Controls

**Section List View:**
- `↑` / `↓`: Navigate sections
- `Enter`: View section details
- `q` / `Ctrl+C`: Quit

**Section Detail View:**
- `↑` / `↓`: Scroll through records
- `h`: Toggle headers view (show all column headers)
- `Esc`: Back to section list
- `q` / `Ctrl+C`: Quit

**Headers View:**
- `↑` / `↓`: Navigate through headers
- `Space`: Enter column selection mode (toggle checkboxes)
- In Column Selection:
  - `Space`: Toggle column selection
  - `Enter`: Apply selected columns and return to data view
  - `Esc`: Cancel column selection and return to headers view
- `h`: Back to data view
- `Esc`: Back to section list
- `q` / `Ctrl+C`: Quit

## Supported Sections

| ID   | Name                                          | Description                                    |
|------|-----------------------------------------------|------------------------------------------------|
| ACCT | Account Information                            | Client account details                          |
| FIFO | Realized & Unrealized Performance Summary       | Stock/options/futures PnL with realized/unrealized gains |
| POST | Position; trade date basis                     | Current portfolio positions                      |
| TRNT | Trades; trade date basis                      | Transaction history                              |
| OPTT | Option Exercises, Assignments and Expirations | Options-related events                           |
| PEND | Pending Exercises                             | Pending option exercises                        |
| PPPO | Prior Period Positions                        | Historical positions                            |
| CORP | Corporate Actions                             | Corporate actions data                          |
| CTRN | Cash Transactions                             | Cash movements                                  |

## Examples

### View account information
```bash
ibkr-report show 2025_U15.csv ACCT
```

### View all positions with PnL
```bash
ibkr-report show 2025_U15.csv FIFO --columns Symbol,Description,TotalFifoPnl
```

### View only stock positions
```bash
ibkr-report show 2025_U15.csv FIFO --col AssetClass=STK
```

### View NVIDIA trades sorted by PnL
```bash
ibkr-report show 2025_U15.csv FIFO --col Symbol=NVDA --sort TotalFifoPnl --desc
```

## Development

### Build

```bash
make build
```

### Run tests

```bash
make test
```

### Format code

```bash
make fmt
```

## Requirements

- Go 1.24+
- Linux, macOS, or Windows (with WSL)

## License

MIT
