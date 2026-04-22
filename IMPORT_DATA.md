# Broker Import Data Formats and Note Construction

This document provides detailed technical specifications for all supported broker import formats, including exact field mappings, data parsing logic, and note construction algorithms.

## Table of Contents
1. [Overview](#overview)
2. [Trading 212 (T212)](#trading-212-t212)
3. [Interactive Brokers - TradeLog](#interactive-brokers-tradelog)
4. [Interactive Brokers - FlexQuery](#interactive-brokers-flexquery)
5. [FIO Bank](#fio-bank)
6. [Revolut](#revolut)
7. [Firefish](#firefish)
8. [Configuration Options](#configuration-options)
9. [Version History](#version-history)

## Overview

### Note Construction Philosophy
- **Standardization**: All notes include broker identification for source tracking
- **Rich Context**: Include relevant identifiers (ISIN, Account ID, Transaction ID) when available
- **Parseability**: Structured format with delimiters for automated processing
- **Localization**: Czech field names in documentation, English technical terms

### Field Mapping Convention
- ✅ **Used in Output**: Fields that populate transaction data
- ❌ **Not Used**: Available in source but not utilized
- ❓ **Variable**: Depends on broker's data quality/completeness

## Trading 212 (T212)

### File Format Specifications
- **File Type**: CSV with header row
- **Delimiter**: Comma (,)
- **Encoding**: UTF-8
- **Date Format**: YYYY-MM-DD HH:MM:SS
- **Supported Actions**: Market Buy, Limit Buy, Market Sell, Limit Sell

### Field Mapping Table

| Column | Field Name      | Data Type | Description               | Used In Output | Example Value       |
| ------ | --------------- | --------- | ------------------------- | -------------- | ------------------- |
| 0      | Action          | String    | Trade action type         | ❌             | Market Buy          |
| 1      | Time            | DateTime  | Trade timestamp           | ✅ (Date)      | 2024-01-15 14:30:25 |
| 2      | ISIN            | String    | International Security ID | ✅ (Note)      | US0378331005        |
| 3      | Ticker          | String    | Stock symbol              | ✅ (Ticker)    | AAPL                |
| 4      | Name            | String    | Company name              | ✅ (Note)      | APPLE INC.          |
| 5      | Notes           | String    | Internal T212 notes       | ❌             | -                   |
| 6      | ID              | String    | T212 transaction ID       | ❌             | 12345678            |
| 7      | Shares          | Decimal   | Quantity traded           | ✅ (Amount)    | 10.0                |
| 8      | Price           | Decimal   | Price per share           | ✅ (Price)     | 185.25              |
| 9      | Currency        | String    | Trade currency            | ✅ (Currency)  | USD                 |
| 10     | Exchange Rate   | Decimal   | FX conversion rate        | ❌             | 1.085               |
| 11     | Result          | Decimal   | Trade result amount       | ❌             | 1852.50             |
| 12     | Result Currency | String    | Result currency           | ❌             | USD                 |
| 13     | Total           | Decimal   | Total trade amount        | ❌             | 1852.50             |
| 14     | Total Currency  | String    | Total currency            | ❌             | USD                 |

### Note Construction Algorithm

**API Import Format**: `[Company Name]|Broker:T212|ISIN:[ISIN Code]|TxnID:[TransactionID]`

**Local File Format**: `[Company Name]|Broker:T212|ISIN:[ISIN Code]|TxnID:[TransactionID]`

**Logic**:
1. Extract company name from column 4
2. Extract ISIN from column 2
3. Extract Transaction ID from column 6
4. Construct note with broker identifier

**API Examples**:
- `"APPLE INC.|Broker:T212|ISIN:US0378331005|TxnID:12345678"`
- `"TESLA INC|Broker:T212|ISIN:US88160R1014|TxnID:87654321"`

**Local File Examples**:
- `"APPLE INC.|Broker:T212|ISIN:US0378331005|TxnID:12345678"`
- `"TESLA INC.|Broker:T212|ISIN:US88160R1014|TxnID:87654321"`

**Edge Cases**:
- Empty company name: `"|Broker:T212|ISIN:US0378331005|TxnID:12345678"`
- Empty ISIN: `"APPLE INC.|Broker:T212|ISIN:|TxnID:12345678"`
- Empty Transaction ID: `"APPLE INC.|Broker:T212|ISIN:US0378331005|TxnID:"`
- All empty: `"|Broker:T212|ISIN:|TxnID:"`

### Code Location
- **API Parser**: `Trading212CsvParser.java` (lines 134-138)
- **Local File Parser**: `ImportT212.java` & `ImportT212CZK.java` (enhanced with ISIN and Transaction ID)

## Interactive Brokers - TradeLog

### File Format Specifications
- **File Type**: Pipe-delimited text (.tlg files)
- **Delimiter**: Pipe (|)
- **Header Structure**: ACCOUNT_INFORMATION followed by ACT_INF line
- **No Column Headers**: Fixed field positions based on IB specification

### Header Format
```
Line 1: ACCOUNT_INFORMATION
Line 2: ACT_INF|[AccountID]|[Name]|[Type]|[Address...]
```

**Example Header**:
```
ACCOUNT_INFORMATION
ACT_INF|U393818|John Doe|Individual|123 Main St|Prague|CZ
```

### Trade Line Format
```
[Type]|[TxnID]|[Ticker]|[Description]|[Exchange]|[Action]|[Status]|[Date]|[Time]|[Currency]|[Price]|[Qty]|[Total]|[Fee]|[RealizedPnL]|[Additional...]
```

### Field Mapping Table

| Field | Index | Field Name     | Data Type | Description               | Used In Output | Example Value       |
| ----- | ----- | -------------- | --------- | ------------------------- | -------------- | ------------------- |
| 0     | 0     | Type           | String    | Trade type (STK_TRD, etc.)| ✅ (Logic)     | STK_TRD             |
| 1     | 1     | Transaction ID | String    | IB transaction identifier | ✅ (Note)      | 996706497           |
| 2     | 2     | Ticker         | String    | Stock symbol              | ✅ (Ticker)    | GE                  |
| 3     | 3     | Description    | String    | Company description       | ✅ (Note)      | GENERAL ELECTRIC CO |
| 4     | 4     | Exchange       | String    | Trading venue             | ✅ (Market)    | NYSE                |
| 5     | 5     | Action         | String    | Trade action              | ✅ (Logic)     | BUYTOOPEN           |
| 6     | 6     | Status Code    | String    | Order status (O,C,Ca,etc.)| ✅ (Note)      | O                   |
| 7     | 7     | Date           | String    | Trade date YYYYMMDD       | ✅ (Date)      | 20180206            |
| 8     | 8     | Time           | String    | Trade time HH:MM:SS       | ✅ (Date)      | 10:02:45            |
| 9     | 9     | Currency       | String    | Trade currency            | ✅ (Currency)  | USD                 |
| 10    | 10    | Price          | Decimal   | Execution price           | ✅ (Price)     | 331.00              |
| 11    | 11    | Quantity       | Decimal   | Shares traded             | ✅ (Amount)    | 1.00                |
| 12    | 12    | Total          | Decimal   | Total amount              | ❌             | 4994.79             |
| 13    | 13    | Fee            | Decimal   | Commission fee            | ✅ (Fee)       | -1.655              |
| 14    | 14    | Fee Currency   | String    | Fee currency              | ❌             | USD                 |
| 15+   | 15+   | Additional     | Various   | IB internal fields        | ❌             | -                   |

### Account ID Extraction
- **Source**: Field 1 of ACT_INF line (after ACCOUNT_INFORMATION)
- **Format**: U[Number] (e.g., U393818)
- **Scope**: Applied to all transactions in the file
- **Fallback**: "UNKNOWN" if extraction fails

### Note Construction Algorithm

**Enhanced Format**: `[Ticker]|Broker:IB|AccountID:[AccountID]|TxnID:[TxnID]|Code:[Status]`

**Logic**:
1. Extract ticker from field 2
2. Extract account ID from header ACT_INF line field 1 (e.g., U393818)
3. Extract transaction ID from field 1 (e.g., 996706497)
4. Include status code from field 6 (O, C, Ca, etc.)
5. Use broker identifier "IB"

**Examples**:
- `"GENERAL ELECTRIC CO|Broker:IB|AccountID:U393818|TxnID:996706497|Code:O"`
- `"APPLE INC|Broker:IB|AccountID:U393818|TxnID:1002441731|Code:C"`
- `"SHAKE SHACK INC - CLASS A|Broker:IB|AccountID:U393818|TxnID:997854933|Code:Ca"`

**Status Code Meanings**:
- `O`: Open (opening trade/position)
- `C`: Close (closing trade/position)
- `Ca`: Cancelled transaction
- Other codes: Various IB-specific order statuses

### Code Location
- **Parser**: `ImportIBTradeLog.java`
- **Note Logic**: Lines 75-78 (enhanced implementation)
- **Account ID Extraction**: Lines 44-52

## Interactive Brokers - FlexQuery

### File Format Specifications
- **File Type**: Comma-separated CSV
- **Delimiter**: Comma (,)
- **Headers**: Standard CSV with IB field names
- **Encoding**: UTF-8

### Note Construction Algorithm

**Format**: `[IB_Note_Field]|Broker:IB` or `[IB_Note_Field]|Broker:IB|Type:Warrant`

**Logic**:
1. Use IB's exported note field content
2. Add broker identifier
3. Special handling for warrant transactions

**Examples**:
- `"Apple Inc.|Broker:IB"`
- `"Apple Warrant|Broker:IB|Type:Warrant"`
- `"Microsoft Corporation|Broker:IB"`

**Edge Cases**:
- Empty IB note field: `"|Broker:IB"`
- Special transaction types use Type: modifier

### Code Location
- **Parser**: `ImportCustomCSV.java`
- **Note Logic**: Lines 200, 203

## Interactive Brokers - Flex API (CSV Export)

### File Format Specifications
- **Source**: IBKR Flex Web Service API (downloadable CSV reports)
- **File Type**: Comma-separated CSV with header row
- **Delimiter**: Comma (,)
- **Encoding**: UTF-8
- **Date Format**: YYYYMMDD;HHMMSS (DateTime column - compact format with time component)
- **Settlement Format**: YYYYMMDD (SettlementDate column - compact date only)

### Flex CSV Versions (v1 vs v2)

IBKR Flex CSV can exist in two closely related variants. StockAccounting supports both, but **does not allow mixing** the variants within a single import preview/session.

#### Version 1 (legacy)

- Starts directly with the section header row (usually beginning with `"ClientAccountID"...`).
- May contain multiple sections in one file; each section is separated by a repeated header row.
- There are **no explicit begin/end markers** for sections.

Example (first lines):

```csv
"ClientAccountID","AccountAlias",...
"U154...",...
...
```

#### Version 2 (headers and trailers)

- Adds explicit control records which clearly mark the file and section boundaries.
- File starts with `BOF/BOA` and ends with `EOA/EOF`.
- Each section is wrapped by:
  - `"BOS","<code>","<label>"` (begin section)
  - then the section CSV header row (often `"ClientAccountID"...`)
  - then data rows
  - `"EOS","<code>","<rowCount>",...` (end section)

Example (first lines):

```csv
"BOF","U154...",...
"BOA","U154..."
"BOS","TRNT","Trades; trade date basis"
"ClientAccountID","AccountAlias",...
"U154...",...
...
"EOS","TRNT","1738",...
...
"EOA","U154..."
"EOF"
```

**UI behavior**: when v2 is loaded, ImportWindow shows the file version next to the Format dropdown and lists included sections in a tooltip (one per line, derived from the section label before `;`, e.g. `Trades`, `Corporate Actions`).

**Mandatory sections** (v2): the file is expected to include at least `Trades` and `Corporate Actions` sections. If any of these are missing, StockAccounting shows a warning during import preview.

### Dividendy a daně (IBKR v2)

V novějších Flex exportech (v2) nejsou dividendy v sekci Trades (TRNT), ale typicky v sekci `CTRN` (Cash Transactions).

- `Type=Dividends` a `Type=Payment In Lieu Of Dividends` se importují jako **hrubá dividenda** (`DIRECTION_DIVI_BRUTTO`).
- `Type=Withholding Tax` a dividendové poplatky (`Type=Other Fees` s popisem obsahujícím `CASH DIVIDEND ... - FEE`) se importují jako **daň z dividendy** (`DIRECTION_DIVI_TAX`).
- Pokud je `Withholding Tax` bez tickeru/symbolu (např. úrokové srážky), importuje se s tickerem `CASH.internal`.

### Ignorované řádky

V hlavním okně lze řádky označit jako „Ignorovat“. Tyto řádky jsou zobrazeny šedě a nevstupují do výpočtů (dividendy, obchodní analýzy). Stav se ukládá do `.dat` souboru.

Poznámka (IBKR Flex): v importním okně je režim „Vše (obchody + transformace + dividendy)", který načítá všechny dostupné sekce (TRNT/CORP/CTRN) podle obsahu exportu.

### Export jednotných kurzů (JSON)

V Nastavení → Kurzy měn lze exportovat jednotné kurzy (měna/rok/kurz) do JSON souboru.

### Field Mapping Table

| Column Name     | Data Type | Description                          | Used In Output     | Example Value       |
| --------------- | --------- | ------------------------------------ | ------------------ | ------------------- |
| ClientAccountID | String    | IB account identifier                | ✅ (Note)          | U123456             |
| Symbol          | String    | Stock/option ticker (full contract)  | ✅ (Ticker)        | AAPL, ORCL  260116C00220000 |
| Description     | String    | Company/security name                | ✅ (Note)          | APPLE INC., ORCL 16JAN26 220 C |
| ISIN            | String    | International Security ID            | ✅ (Note)          | US0378331005        |
| AssetClass      | String    | Asset type (STK/OPT/FUT/CASH)        | ✅ (Direction)     | STK                 |
| DateTime        | DateTime  | Trade timestamp with time            | ✅ (Date)          | 20240115;143025 |
| SettlementDate  | Date      | Settlement date                      | ✅ (ExecutionDate) | 20240117          |
| TradeDate       | Date      | Trade date (fallback if no DateTime) | ✅ (Date)          | 20240115          |
| Quantity        | Decimal   | Shares/contracts traded              | ✅ (Amount)        | 10.0                |
| TradePrice      | Decimal   | Execution price                      | ✅ (Price)         | 185.25              |
| CurrencyPrimary | String    | Trade currency                       | ✅ (Currency)      | USD                 |
| IBCommission    | Decimal   | Commission fee (negative)            | ✅ (Fee)           | -1.50               |
| Multiplier      | Decimal   | Contract size (options/futures)      | ✅ (Amount calc)   | 100                 |
| Exchange        | String    | Trading venue                        | ✅ (Market)        | NASDAQ              |
| Buy/Sell        | String    | Trade direction                      | ✅ (Direction)     | BUY                 |
| TransactionType | String    | Transaction category                 | ✅ (Direction)     | ExchTrade           |
| TransactionID   | String    | IB transaction identifier            | ✅ (Note)          | 123456789           |
| Code            | String    | Status/notes code                    | ✅ (Note)          | O                   |

### Asset Class Mapping

IBKR AssetClass determines Transaction direction type:

| AssetClass | Description        | Buy Direction      | Sell Direction       |
| ---------- | ------------------ | ------------------ | -------------------- |
| STK        | Stocks (Typ CP)    | DIRECTION_SBUY (1) | DIRECTION_SSELL (-1) |
| OPT        | Options            | DIRECTION_DBUY (3) | DIRECTION_DSELL (-3) |
| FUT        | Futures            | DIRECTION_DBUY (3) | DIRECTION_DSELL (-3) |
| FOP        | Futures on Options | DIRECTION_DBUY (3) | DIRECTION_DSELL (-3) |
| WAR        | Warrants           | DIRECTION_DBUY (3) | DIRECTION_DSELL (-3) |
| CASH       | Cash/FX            | DIRECTION_CBUY (4) | DIRECTION_CSELL (-4) |
| FX         | Foreign Exchange   | DIRECTION_CBUY (4) | DIRECTION_CSELL (-4) |

### Note Construction Algorithm

**Format** (conditional field inclusion):
```
[Description]|Broker:IB|AccountID:[ClientAccountID]|ISIN:[ISIN]|TxnID:[TransactionID]|Code:[Code]|IssuerCountry:[Country]
```

**Logic**:
1. Extract company name from Description column (always included, even if empty)
2. Add broker identifier "IB" (always added)
3. **Conditionally add** ClientAccountID if present and non-empty
4. **Conditionally add** ISIN if present and non-empty
5. **Conditionally add** IssuerCountry if present and non-empty
6. **Conditionally add** TransactionID if present and non-empty (prefers IBOrderID over TransactionID)
7. **Conditionally add** Code if present and non-empty

**Key Differences from IB TradeLog**:
- Fields are **skipped entirely** if not present (no empty values like `|ISIN:|`)
- **IssuerCountry** field added (not present in TradeLog format)
- **IBOrderID** preferred over TransactionID for consolidation stability

**Examples**:
- Full data: `"APPLE INC.|Broker:IB|AccountID:U123456|ISIN:US0378331005|TxnID:987654321|Code:O|IssuerCountry:US"`
- No ISIN: `"APPLE INC.|Broker:IB|AccountID:U123456|TxnID:987654321|Code:O"`
- Minimal: `"APPLE INC.|Broker:IB|TxnID:987654321"`
- No description: `"|Broker:IB|AccountID:U123456|ISIN:US0378331005|TxnID:987654321"`

**Consolidated Transactions**:
- When multiple fills share the same IBOrderID, they are consolidated into a single transaction
- Note includes consolidation marker: `|Consolidated:N fills`
- Example: `"APPLE INC.|Broker:IB|AccountID:U123456|ISIN:US0378331005|TxnID:987654321|Code:O|Consolidated:3 fills"`

**Examples**:
- Full data: `"APPLE INC.|Broker:IB|AccountID:U123456|ISIN:US0378331005|TxnID:987654321|Code:O"`
- No ISIN: `"APPLE INC.|Broker:IB|AccountID:U123456|TxnID:987654321|Code:O"`
- Minimal: `"APPLE INC.|Broker:IB|TxnID:987654321"`
- No description: `"|Broker:IB|AccountID:U123456|ISIN:US0378331005|TxnID:987654321"`

**Edge Cases**:
- Missing AccountID: Field skipped entirely
- Missing ISIN: Field skipped entirely  
- Missing Code: Field skipped entirely
- Empty Description: Note starts with `"|Broker:IB|..."`

### Corporate Action Notes

Corporate action transactions use a different note format to capture the full action description from IBKR:

**Format for RS (Reverse Splits):**
```
RS: [Full Description from IBKR]
```

**Format for TC (Ticker Changes/Mergers) with Value:**
```
TC (Value: [Monetary Value]): [Full Description from IBKR]
```

**Format for TC without Value:**
```
TC: [Full Description from IBKR]
```

**Examples**:
- `"RS: CODX.OLD(US1897631057) SPLIT 1 FOR 30 (CODX.OLD, CO-DIAGNOSTICS INC, US1897631057)"`
- `"TC (Value: 1353.87054): CS(US2254011081) MERGED(Acquisition) WITH H42097107 100 FOR 2248 (UBS GROUP AG, CH0012032030)"`
- `"TC: APGN(US03759B1026) MERGED(Acquisition) WITH US7473241013 69 FOR 400 (PYXS THERAPEUTICS INC, US7473241013)"`

### Corporate Action Notes

Corporate action transactions use a different note format to capture the full action description from IBKR:

**Format for RS (Reverse Splits):**
```
RS: [Full Description from IBKR]
```

**Format for TC (Ticker Changes/Mergers) with Value:**
```
TC (Value: [Monetary Value]): [Full Description from IBKR]
```

**Format for TC without Value:**
```
TC: [Full Description from IBKR]
```

**Examples:**
- `"RS: CODX.OLD(US1897631057) SPLIT 1 FOR 30 (CODX.OLD, CO-DIAGNOSTICS INC, US1897631057)"`
- `"TC (Value: 1353.87054): CS(US2254011081) MERGED(Acquisition) WITH H42097107 100 FOR 2248 (UBS GROUP AG, CH0012032030)"`
- `"TC: APGN(US03759B1026) MERGED(Acquisition) WITH US7473241013 69 FOR 400 (PYXS THERAPEUTICS INC, US7473241013)"`

**Code References:**
- `IBKRFlexParser.java:2896-2903` - Corporate action note construction
- `IBKRFlexParser.java:2883-2886` - Full description extraction
- `IBKRFlexParser.java:2889` - Code column (RS/TC)

### Cash Transaction Notes (Dividends, Interest, Fees)

Cash transactions from CTRN section use a different note format:

**Format:**
```
[Description]|Broker:IB|Type:[TransactionType]|IssuerCountry:[Country]|ISIN:[ISIN]|ActionID:[ActionID]
```

**Logic:**
1. Description (IBKR's transaction description)
2. Broker identifier "IB"
3. Transaction type (Dividends, Withholding Tax, Broker Interest, etc.)
4. Conditionally add IssuerCountry if present
5. Conditionally add ISIN if present
6. Conditionally add ActionID if present

**Examples:**
- Dividend: `"AAPL Cash Dividend 0.92 USD|Broker:IB|Type:Dividends|IssuerCountry:US|ISIN:US0378331005|ActionID:123456789"`
- Withholding tax: `"Withholding Tax - Dividend|Broker:IB|Type:Withholding Tax|IssuerCountry:US|ISIN:US0378331005|ActionID:123456789"`
- Interest: `"Credit Interest|Broker:IB|Type:Broker Interest Received"`

**Transaction Types Imported:**
- **Dividends** - `DIRECTION_DIVI_BRUTTO`
- **Withholding Tax** - `DIRECTION_DIVI_TAX`
- **Broker Interest Received** - `DIRECTION_INT_BRUTTO`
- **Broker Interest Paid** - `DIRECTION_INT_PAID`
- **Broker Fees** - `DIRECTION_INT_FEE`

**Special Ticker Assignments:**
- `Kreditni.Urok` - Credit interest (receiving)
- `Debetni.Urok` - Debit interest (paying)
- `CASH.internal` - Withholding tax without ticker symbol

**Code References:**
- `IBKRFlexParser.java:3749-3762` - Cash transaction note construction
- `IBKRFlexParser.java:3692-3719` - Transaction type classification
- `IBKRFlexParser.java:2386-2401` - Special ticker assignment logic
- `IBKRFlexParser.java:2410-2436` - Direction mapping for cash transactions

### Transaction Direction Constants

```java
DIRECTION_TRANS_SUB = -2   // Transformation OUT (old ticker, shares removed)
DIRECTION_TRANS_ADD = +2   // Transformation IN (new ticker, shares added)
```

**When Zero-Net Occurs**:
1. User held shares before corporate action date
2. User sold **ALL shares** on or before corporate action date
3. Broker generated transformation records (for audit trail/compliance)
4. Broker immediately canceled transformation (user no longer holds shares)
5. Result: Canceling pairs that net to zero after filtering

**Why This Is Correct**:
- Zero-net events have no effect on portfolio holdings
- They represent "what would have happened if you still held shares"
- Importing them would create confusing zero-sum transactions
- Skipping them keeps the transaction history clean and accurate

**Logging** (verbose format as requested):
```
INFO: Skipped zero-net RS: EVFM (filtered 4→2 rows, net: 0.0 shares, date: 2022-05-06, reason: shares sold before split)
INFO: Skipped zero-net RS: MULN (filtered 4→2 rows, net: 0.0 shares, date: 2023-05-04, reason: shares sold before split)
INFO: Imported RS: MULN (2 rows, net: -15111.11 shares, date: 2023-08-11)
INFO: Imported RS: CALA (2 rows, net: -4940.0 shares, date: 2022-06-15)
```

**Statistics API**:
- `IBKRFlexParser.getSkippedZeroNetCount()`: Number of events skipped
- `IBKRFlexParser.getSkippedZeroNetTickers()`: List of ticker symbols (e.g., ["EVFM", "MULN"])
- `IBKRFlexParser.getImportedCorporateActionCount()`: Number of events successfully imported

**UI Feedback**:

*Status Label* (minimal format):
```
"Načteno: 2023_flex.csv (127 transakcí, 3 korp. akce, 2 přeskočeno)"
```
Displays:
- Total transactions imported: 127
- Corporate action events imported: 3
- Zero-net events skipped: 2

**Edge Cases**:
- **Partial sales**: If you sold only some shares (e.g., 5000 of 10000), net will be non-zero → Import normally
- **Fractional shares**: 0.001 tolerance handles floating-point rounding errors
- **Multiple tickers in one event**: ALL tickers must net to zero to skip (e.g., PLSE multi-asset conversion)
- **TC (Ticker Changes)**: Currently only RS events are checked (TC rarely exhibits this pattern since different companies are involved)
- **Zero-tolerance edge**: If net is exactly 0.001 shares, event is imported (conservative approach)

**Comparison Table**:

| Scenario                          | EVFM (Zero-Net)       | MULN Second (Normal)     |
| --------------------------------- | --------------------- | ------------------------ |
| Shares held at split              | 0 (sold May 5)        | 17,000                   |
| CSV rows                          | 4 (canceling pairs)   | 2 (transformation)       |
| After filtering                   | 2 rows                | 2 rows                   |
| Net effect calculation            | +666 - 10000 = -9334? | +1888.89 - 17000 = -15111|
| After ticker normalization (both EVFM) | +666 EVFM - 10000 EVFM = **0** net effect on EVFM! | Not applicable (different ISINs) |
| Zero-net detected?                | **Yes** → Skip        | **No** → Import          |
| Result                            | 0 transactions        | 2 transactions           |

**Note on EVFM Net Calculation**: The apparent -9334 net becomes 0 after realizing both transactions reference the same underlying security (EVFM) after .OLD suffix stripping. The +666 and -10000 with opposite ISINs actually represent the broker's internal accounting for a transformation that never happened because shares were already sold.

**Location**: `IBKRFlexParser.java` lines ~1110-1160 (isZeroNetEvent, extractTickerFromNote, statistics methods)

#### Real-World Examples

**Example 1: CODX Reverse Split 1-for-30**

**IBKR CSV:**
```
Line 75: Symbol="COMMON", Description="CODX", Column38="+45.6667", Code="RS"
Line 76: Symbol="COMMON", Description="CODX.OLD", Column38="-1370", Code="RS"
Line 6:  Symbol="CODX", Quantity="-0.6667", Code="LF" (regular trade)
```

**Imported Transactions:**
```
1. CODX.OLD   Transformation OUT  1370 shares     (Price: 0, Fee: 0)
2. CODX       Transformation IN   45.6667 shares  (Price: 0, Fee: 0)
3. CODX       Sell               0.6667 shares   (Price: 5.12, Fee: 0.29)
```

**Portfolio Math:**
- Remove: 1370 shares CODX.OLD
- Add: 45.6667 shares CODX (theoretical allocation from split)
- Sell: 0.6667 shares CODX (fractional disposal by broker)
- **Net holding: 45 whole shares CODX** ✅

**Note Field:**
```
RS: CODX.OLD(US1897631057) SPLIT 1 FOR 30 (CODX.OLD, CO-DIAGNOSTICS INC, US1897631057)
```

---

**Example 2: DGLY→KUST Reverse Split with Ticker Change (1-for-3)**

**IBKR CSV:**
```
Line 77: Symbol="COMMON", Description="DGLY.OLD", Column38="-325", Code="RS"
Line 78: Symbol="COMMON", Description="KUST", Column38="+108.3333", Code="RS"
Line 9:  Symbol="KUST", Quantity="-0.3333", Code="LF" (regular trade)
```

**Imported Transactions:**
```
1. DGLY.OLD   Transformation OUT  325 shares      (Price: 0)
2. KUST       Transformation IN   108.3333 shares (Price: 0)
3. KUST       Sell               0.3333 shares   (Price: 2.44)
```

**Portfolio Math:**
- Remove: 325 shares DGLY.OLD
- Add: 108.3333 shares KUST (325 ÷ 3 = 108.3333)
- Sell: 0.3333 shares KUST (fractional)
- **Net holding: 108 whole shares KUST** ✅

---

**Example 3: HUBC Edge Case - All Fractional Result (1-for-15)**

**IBKR CSV:**
```
Line 80: Symbol="COMMON", Description="HUBC.OLD", Column38="-5", Code="RS"
Line 79: Symbol="COMMON", Description="HUBC", Column38="+0.3333", Code="RS"
Line 7:  Symbol="HUBC", Quantity="-0.3333", Code="LF" (regular trade)
```

**Imported Transactions:**
```
1. HUBC.OLD   Transformation OUT  5 shares        (Price: 0)
2. HUBC       Transformation IN   0.3333 shares   (Price: 0)
3. HUBC       Sell               0.3333 shares   (Price: 4.57)
```

**Portfolio Math:**
- Remove: 5 shares HUBC.OLD
- Add: 0.3333 shares HUBC (5 ÷ 15 = 0.3333)
- Sell: 0.3333 shares HUBC (all shares fractional)
- **Net holding: 0 shares HUBC** (investor receives cash) ✅

**Why keep 0.3333 in transformation?**
- Shows what the split allocated (complete audit trail)
- Balances with the sell transaction (+0.3333 - 0.3333 = 0)
- If floored to 0, portfolio math breaks (can't sell 0.3333 shares you never received)

#### Detection Logic

Corporate actions are detected by:
1. **Code column = "RS"** (Reverse Split)
2. **Column count < 60** (51 columns vs 85 for regular trades)
3. **Symbol = "COMMON"** (generic marker for corporate actions)

#### Transaction Direction Constants

```java
DIRECTION_TRANS_SUB = -2   // Transformation OUT (old ticker, shares removed)
DIRECTION_TRANS_ADD = +2   // Transformation IN (new ticker, shares added)
```

#### Important Notes

- **Price = 0**: Transformations don't have transaction prices (not a trade, just a transfer)
- **Fee = 0**: No commissions on corporate actions
- **Fractional amounts preserved**: Keep 45.6667, not floor(45) - required for portfolio math
- **Separate transactions**: Each row creates independent transaction (not paired/linked)
- **Code="LF"**: "Lieu of Fractional" - broker sells fractional shares for cash

---

### Corporate Actions: Ticker Changes & Mergers (TC)

IBKR Flex Query reports mergers, acquisitions, and ticker changes with Code="TC".

#### How It Works

When a merger or ticker change occurs, IBKR creates **two separate CSV rows** (one for outgoing security, one for incoming):

1. **Old security row**: Shares removed (negative quantity)
   - Example: `CS  -1500 shares  Value: -$1328.7`
   - Imported as: **Transformation OUT** (DIRECTION_TRANS_SUB = -2)
   
2. **New security row**: Shares received (positive quantity)
   - Example: `UBS  +66.726 shares  Value: +$1353.87`
   - Imported as: **Transformation IN** (DIRECTION_TRANS_ADD = +2)
   - **Note**: Amount includes fractional shares from conversion

#### CSV Structure

Same 47-column structure as reverse splits, but includes important Value field:

| Aspect                | Reverse Split (RS) | Ticker Change (TC)                    |
| --------------------- | ------------------ | ------------------------------------- |
| **Purpose**               | Stock consolidation | Merger/acquisition/ticker change      |
| **Ticker**                | Same company       | Different companies/securities        |
| **Value field (Col 32)**  | Usually "0"        | **Contains monetary value** (for tax) |
| **Code field (Col 38)**   | "RS"               | "TC"                                  |
| **Conversion ratio**      | Simple (1:30)      | Can be complex (100:2248)             |

**Column structure:**
- Column 6: Symbol = "COMMON" (or "ADR", "RIGHT", "WAR")
- Column 7: Description = actual ticker
- Column 8: ActionDescription = full merger/conversion details
- Column 28: Date/Time
- Column 32: **Value** (cost basis transfer, market value)
- Column 33: Quantity change
- Column 38: Code = "TC"

#### Real-World Examples

**Example 1: Credit Suisse → UBS Acquisition (2023)**

**IBKR CSV:**
```
Row 1: Symbol="COMMON", Description="CS", Quantity="-1500", Value="-1328.7", Code="TC"
       ActionDescription: "CS(US2254011081) MERGED(Acquisition) WITH H42097107 100 FOR 2248"

Row 2: Symbol="COMMON", Description="UBS", Quantity="66.726", Value="1353.87054", Code="TC"
       ActionDescription: "CS(US2254011081) MERGED(Acquisition) WITH H42097107 100 FOR 2248"
```

**Imported Transactions:**
```
1. CS    Transformation OUT  1500 shares    (Price: 0, Fee: 0)
   Note: TC (Value: -1328.7): CS(US2254011081) MERGED(Acquisition) WITH H42097107 100 FOR 2248 (...)
   
2. UBS   Transformation IN   66.726 shares  (Price: 0, Fee: 0)
   Note: TC (Value: 1353.87054): CS(US2254011081) MERGED(Acquisition) WITH H42097107 100 FOR 2248 (...)
```

**Conversion Details:**
- Ratio: 100 CS shares → 2248 UBS shares
- User conversion: 1500 CS → 66.726 UBS (1500 × 2248 ÷ 100 = 33,720 ÷ 505 ≈ 66.726)
- Value transferred: ~$1328.70 cost basis

---

**Example 2: APGN → PYXS Merger (2023)**

**IBKR CSV:**
```
Row 1: APGN  -1000 shares, Value: -$385.1, Code: TC
Row 2: PYXS  +172.5 shares, Value: +$363.975, Code: TC
```

**Imported Transactions:**
```
1. APGN  Transformation OUT  1000 shares   (Price: 0)
   Note: TC (Value: -385.1): APGN(US03759B1026) MERGED(Acquisition) WITH US7473241013 69 FOR 400 (...)
   
2. PYXS  Transformation IN   172.5 shares  (Price: 0)
   Note: TC (Value: 363.975): APGN(US03759B1026) MERGED(Acquisition) WITH US7473241013 69 FOR 400 (...)
```

**Conversion Details:**
- Ratio: 400 APGN → 69 PYXS (17.25% conversion)
- User conversion: 1000 APGN → 172.5 PYXS
- Net portfolio: Lost 1000 APGN, gained 172.5 PYXS

---

**Example 3: Complex Multi-Security Conversion (PLSE Rights, 2022)**

**IBKR CSV:**
```
Row 1: PLSE.RTS2 (rights)  OUT -487 shares,  AssetClass: RIGHT, Code: TC
Row 2: PLSE (stock)        IN  +487 shares,  AssetClass: STK,   Code: TC
Row 3: PLSE.WT (warrants)  IN  +487 warrants, AssetClass: WAR,   Code: TC
```

**Imported Transactions:**
```
1. PLSE.RTS2  Transformation OUT  487 shares    (rights removed)
2. PLSE       Transformation IN   487 shares    (stock received)
3. PLSE.WT    Transformation IN   487 warrants  (warrants received)
```

**Note:** All use TRANS_ADD/TRANS_SUB regardless of asset class (transformations are universal)

#### Tax Implications

⚠️ **CRITICAL:** TC transactions include **Value field** which represents:
- **Cost basis transfer** from old to new security
- **Market value** at time of conversion
- **Important for tax reporting** - capital gains/losses may apply

The Value field is stored in the note for future reference:
```
TC (Value: 1353.87054): CS(US2254011081) MERGED...
```

This allows you to track the cost basis through corporate actions for tax calculations.

#### Detection Logic

Same detection as RS - corporate actions have:
1. **Code field = "TC"** (Ticker Change/Merger)
2. **Column count < 60** (47 columns vs 85 for regular trades)
3. **Symbol = "COMMON"** (or "ADR", "RIGHT", "WAR")

#### Transaction Direction Constants

```java
DIRECTION_TRANS_SUB = -2   // Transformation OUT (old security removed)
DIRECTION_TRANS_ADD = +2   // Transformation IN (new security received)
```

#### Important Notes

- **Price = 0**: Transformations don't involve cash transactions
- **Fee = 0**: No commissions on corporate actions
- **Fractional amounts preserved**: Keep exact conversion ratios (66.726, 172.5)
- **Value field in note**: Includes monetary value for tax reference when available
- **Asset class variations**: TC can apply to STK, ADR, RIGHT, WAR - all use same TRANS_ADD/SUB
- **Complex conversions**: One security can convert to multiple (e.g., stock + warrants)

### Corporate Action Types: RS / TC / IC / TO

IBKR Corporate Actions section contains a `Type` field that identifies the corporate action category.

Common types observed in real Flex reports:

| Type | Meaning | Typical Description Text | Recommendation |
| ---- | ------- | ------------------------ | -------------- |
| RS   | Reverse split / split | `SPLIT 1 FOR N` | ✅ Import (default) |
| TC   | Ticker change / merger / acquisition | `MERGED(Acquisition) WITH ...` | ✅ Import (default) |
| IC   | CUSIP/ISIN change (identifier change) | `CUSIP/ISIN CHANGE TO ...` | ❌ Ignore by default |
| TO   | Tender offer / tendered to new instrument or election | `TENDERED TO ...` / `RNDUP ELECTION` | ❌ Ignore by default |

Notes:
- `IC` and `TO` often create confusing transformations (or extra placeholder instruments) and are therefore recommended to be disabled by default.
- You can still enable them when you explicitly want to track these events.

### Date/Time Handling

**Trade Date Priority**:
1. **DateTime column** (preferred): Contains full timestamp with time component
   - Format: `YYYYMMDD;HHMMSS` (compact format)
   - Example: `20240115;143025` (Jan 15, 2024 at 14:30:25)
   - Used for: Transaction date field (preserves exact trade time)
   
2. **TradeDate column** (fallback): Date only, no time
   - Format: `YYYYMMDD` (compact format)
   - Example: `20240115`
   - Used when: DateTime column is empty or missing

**Settlement Date**:
- **SettlementDate column**: Maps to "datum vypořádání" in main database
- Format: `YYYYMMDD` (compact format)
- Example: `20240117`
- Fallback: Uses trade date if SettlementDate is missing

### Import Workflow (via ImportWindow)

### Důležité: IBKR CSV obsahuje více sekcí (více hlaviček)

IBKR Flex CSV soubory nejsou jeden homogenní CSV. V praxi obsahují několik samostatných podsekcí, které jsou v jednom souboru oddělené opakovanou hlavičkou (`"ClientAccountID"...`).

StockAccounting tyto sekce rozpoznává podle **konkrétního tvaru hlavičky** a zpracovává je rozdílně:

- **TRADES (85 sloupců)**: skutečné obchody/exekuce (obsahuje `TransactionType`, `Exchange`, `TradePrice`, `IBOrderID`).
  - Sem patří `ExchTrade`, `BookTrade` a `FracShare`.
  - Z těchto řádků se tvoří transakce typu **CP** (STK) nebo **Derivát** (OPT/FUT).
  - V této sekci se používá konsolidace podle `IBOrderID`.
- **OPTIONS SUMMARY (44 sloupců)**: souhrnné/pozicové řádky pro opce (obsahuje `Transaction Type` a `Trade Price` se mezerou, typicky bez `IBOrderID`).
  - Tyto řádky nejsou exekuce a **neimportují se jako transakce**.
- **CORPORATE ACTIONS (47 sloupců)**: korporátní akce (obsahuje `ActionDescription`, `ActionID`, `Code` např. RS/TC).
  - Importují se jako **Transformace** a v náhledu jsou viditelné i při filtru pouze na akcie.

### Důležité: více transformací ve stejný čas

IBKR (i další zdroje) mohou generovat více korporátních akcí (transformací) se stejným přesným časem (`Date/Time`).

StockAccounting historicky očekával maximálně jednu dvojici transformací (OUT+IN) na jeden timestamp. Aby bylo možné korektně zpracovat situace jako více RS událostí ve stejném čase (např. více tickerů ve stejnou minutu), engine nyní:

- Pokud v průběhu zpracování transakcí přijde **třetí** transformace se stejným timestampem, automaticky **dokončí** předchozí dvojici (OUT+IN) a začne novou.
- Zachovává se zpětná kompatibilita: běžné případy jedné dvojice se chovají stejně jako dříve; historická data s ručně upravenými časy nejsou ovlivněna.

#### Method 1: API Import (from IBKR servers)

1. User selects: **"Soubor" → "Import od brokera" → "IBKR Flex API/soubor"**
2. ImportWindow shows IBKR-specific UI with two buttons
3. Nastavte filtr **Typ** (např. pouze akcie STK). Volba se aplikuje při načtení dat do náhledu.
4. Click **"Načíst z IBKR"** to download from IBKR Flex Web Service
4. Data filtered to **current year only** (Year-to-Date)
5. Duplicate detection against existing database
6. Preview table shows filtered transactions
8. Click **"Sloučit do databáze"** to merge into main database
8. Transactions appear in main window with proper Note format

#### Method 2: Local File Import (from CSV file)

1. User selects: **"Soubor" → "Import od brokera" → "IBKR Flex API/soubor"**
2. ImportWindow shows IBKR-specific UI with two buttons
3. Nastavte filtr **Typ** (např. pouze akcie STK). Volba se aplikuje při načtení dat do náhledu.
4. Click **"Načíst ze souboru"** to select local IBKR Flex CSV file
4. File dialog opens - select previously downloaded CSV file from IBKR
5. File is parsed using same IBKRFlexParser (identical processing to API)
6. Preview table shows all transactions from file (no year filtering)
8. Click **"Sloučit do databáze"** to merge into main database
8. Transactions appear in main window with proper Note format

**File Import Requirements**:
- File must be IBKR Flex Query CSV format
- Same column structure as API downloads
- CSV encoding: UTF-8 (standard from IBKR, with ISO-8859-1 fallback)
- Supports all transaction types: trades, corporate actions (RS, TC), fractional shares

**File Import Advantages**:
- Historical data older than current year (API limitation workaround)
- Backup/archive of broker reports for long-term storage
- No need for API credentials (useful for shared computers)
- Faster for one-time imports (no network latency)
- Can import multiple years by selecting different CSV files

**How to Export CSV from IBKR**:
1. Log into IBKR Client Portal (https://www.interactivebrokers.com)
2. Go to **Performance & Reports → Flex Queries**
3. Select your Flex Query template (or create new one)
4. Click **Run** and wait for report generation
5. Click **Download** and save CSV file to your computer
6. Use **"Načíst ze souboru"** button in StockAccounting to import

### Duplicate Detection & Update

**Problem**: Re-importing the same data (e.g., refreshing current year data, re-importing after corrections) can create duplicate transactions in the database.

**Solution**: Automatic duplicate detection for both API and file imports.

**How It Works**:

1. **Duplicate Detection Algorithm**:
   ```
   A transaction is considered a duplicate if ALL key business fields match:
   - Date (exact match, ignoring seconds/milliseconds)
   - Direction (buy/sell/transformation type)
   - Ticker (case-insensitive)
   - Amount (±0.01 tolerance for floating-point)
   - Price (±0.01 tolerance)
   - Price Currency
   - Market (case-insensitive)
   
   Non-matching fields (Notes, Fees, Execution Date) are ignored for duplicate detection.
   ```

2. **Preview Filtering**:
   - Duplicates are automatically filtered from the preview table
   - Only new (non-duplicate) transactions are shown
   - UI label shows: `"Náhled (120 záznamů) - 15 duplikátů vyfiltrováno"`

3. **Update Existing Records** (Checkbox Option):
   - Checkbox: **"Aktualizovat duplikáty"** (Update duplicates)
   - When checked AND duplicates exist:
     - Preview label changes to: `"Náhled (120 záznamů) - 15 duplikátů k aktualizaci"`
     - Duplicates are marked for update (not filtered out)
   - On merge:
     - Existing records are updated with new values for:
       - **Note** (may contain updated metadata)
       - **Fee** (broker may correct fees after settlement)
       - **Execution Date** (may be updated post-trade)
     - Business key fields (Date, Ticker, Amount, Price) are NOT changed
     - Updated rows are **highlighted in yellow** in main window

4. **Status Label Feedback**:
   ```
   File import: "Načteno: 2023_flex.csv (127 transakcí, 15 duplikátů, 3 korp. akce, 2 přeskočeno)"
   ```
   Shows: Total new transactions, duplicate count, corporate actions, zero-net skipped

5. **Success Dialog**:
   ```
   Úspěšně importováno 127 transakcí z IBKR!
   Aktualizováno: 15 existujících záznamů
   
   Aktualizované řádky jsou zvýrazněny žlutě v hlavním okně.
   ```

**Use Cases**:

- **Refresh Current Year Data**: Re-import YTD data to get latest transactions and fee corrections
- **Broker Corrections**: Re-import after broker corrects fees or execution dates
- **Metadata Updates**: Update notes with latest ISIN or transaction codes
- **Duplicate Prevention**: Automatically prevent importing same data twice

**Yellow Highlighting**:
- Color: Light yellow (RGB: 255, 255, 200)
- Duration: Until application restart or explicit clear
- Applies to: All updated rows in main transaction table
- Purpose: Visual confirmation of which records were modified

**Implementation**:
- **Detection**: `TransactionSet.filterDuplicates()`, `findDuplicateTransaction()`
- **Update**: `TransactionSet.updateDuplicateTransaction()`
- **Tracking**: `updatedTransactionSerials` Set in TransactionSet
- **Rendering**: Custom cell renderers in MainWindow check `isRecentlyUpdated()`
- **Location**: TransactionSet.java lines ~1113-1208, MainWindow.java lines ~54-90

### Code Location
- **Parser**: `IBKRFlexParser.java` (CSV parsing, column detection, note construction - **shared by both API and file import**)
- **Importer**: `IBKRFlexImporter.java` (orchestration, caching, year filtering - **API only**)
- **UI Integration**: `ImportWindow.java` (format index 9, unified import interface)
  - `ibkrFlexFetchClicked()` - API import handler
  - `ibkrFlexFileClicked()` - **File import handler with duplicate detection (enhanced in 2026-01-21)**
  - `readFileToString()` - **File reader utility with UTF-8/ISO-8859-1 support**

### API Limitations
- **No date parameters**: IBKR Flex Web Service API does NOT accept date range parameters
- **Template configuration**: Date ranges MUST be set in Flex Query template in IBKR Client Portal
- **Recommended template**: "Year to Date" period for current year imports
- **Historical years**: User must create separate templates or temporarily change Query ID in Settings

### Version History
- **2026-01-21**: Local file import + Ticker normalization + Intelligent filtering
  - Format renamed from "IBKR Flex API" to "IBKR Flex API/soubor"
  - New "Načíst ze souboru" button for importing local CSV files
  - Same IBKRFlexParser used for both API and file imports (identical processing)
  - **NEW: Automatic .OLD suffix stripping for corporate actions**
  - **NEW: Intelligent filtering of redundant corporate action rows**
  - **NEW: Automatic SUB-before-ADD ordering for transformations**
  - **NEW: Sequential time offsets to prevent duplicate timestamps**
  - **NEW: Zero-net corporate action detection and automatic skipping**
  - **NEW: Verbose logging of skipped/imported events with full details**
  - **NEW: Statistics API for tracking corporate action processing**
  - **NEW: Enhanced UI status labels showing corporate action counts**
  - **NEW: Note markers `[Time: +N min]` for adjusted transactions**
  - **NEW: Duplicate detection for both API and file imports**
  - **NEW: Yellow highlighting of updated records in main window**
  - **NEW: Checkbox to enable updating existing duplicate records**
  - Ensures correct ticker matching with historical transactions
  - File import bypasses year filtering (imports all transactions from file)
  - UTF-8 encoding with ISO-8859-1 fallback for legacy files
  - Enables historical data import beyond API limitations
- **2026-01-20**: Initial implementation with unified ImportWindow integration
  - AssetClass-based direction detection (STK → Stock, OPT/FUT → Derivative)
  - Note format standardized to match IB TradeLog
  - DateTime support with time component preservation
  - SettlementDate mapped to "datum vypořádání"
  - ISIN field added (following Trading 212 pattern)

## FIO Bank

### File Format Specifications
- **File Type**: Semicolon-separated CSV
- **Delimiter**: Semicolon (;)
- **Headers**: Czech field names
- **Encoding**: Windows-1250 (typically)

### Key Field Mapping

| Column Name | Data Type | Description           | Used In Output | Example Value |
| ----------- | --------- | --------------------- | -------------- | ------------- |
| Symbol      | String    | Stock ticker          | ✅ (Ticker)    | AAPL          |
| Směr        | String    | Trade direction       | ✅ (Logic)     | Nákup         |
| Počet       | Decimal   | Quantity              | ✅ (Amount)    | 10            |
| Cena        | Decimal   | Price per share       | ✅ (Price)     | 150.25        |
| Měna        | String    | Currency              | ✅ (Currency)  | USD           |
| Text FIO    | String    | FIO description       | ✅ (Note)      | Nákup akcií   |
| Datum obchodu | Date    | Trade date            | ✅ (Date)      | 15.1.2024     |
| Poplatky v CZK | Decimal | Fees in CZK          | ✅ (Fee)       | -25.50        |

### Dividend Handling

FIO exports dividends with Czech text descriptions. The parser detects direction based on text:

| FIO Text | Direction | Description |
|-----------|-----------|-------------|
| "Výnos CP" | `DIRECTION_DIVI_BRUTTO` | Gross dividend |
| "Dividenda - USA" | `DIRECTION_DIVI_BRUTTO` | US dividend |
| "Daň z divid. zaplacená v USA" | `DIRECTION_DIVI_TAX` | US dividend tax |
| Negative price value | `DIRECTION_DIVI_TAX` | Tax (fallback) |
| Other positive | `DIRECTION_DIVI_UNKNOWN` | Unknown type |

### Transformation Handling

When "Trh" (market) field contains "Transformace":

| Original Direction | Transformed Direction | Description |
|------------------|---------------------|-------------|
| `DIRECTION_SBUY` (Nákup) | `DIRECTION_TRANS_ADD` | Shares added |
| `DIRECTION_SSELL` (Prodej) | `DIRECTION_TRANS_SUB` | Shares removed |

The market field is cleared to empty string for transformations.

### Fee Handling

FIO supports fees in multiple currencies with priority:

1. First check: "Poplatky v EUR" (Euro fees)
2. Second check: "Poplatky v USD" (US dollar fees)
3. Final check: "Poplatky v CZK" (Czech koruna fees)

Only the first non-zero fee amount is used.

### Note Construction Algorithm

**Format**: Raw content from "Text FIO" field (no broker prefix added)

**Logic**:
1. Use FIO's description field directly
2. No broker identification added
3. Preserve original Czech text

**Examples**:
- `"Nákup akcií"`
- `"Prodej cenných papírů"`
- `"Dividendy z USA"`

**Edge Cases**:
- Empty text field: Empty note
- Special characters: Preserved as-is

### Code Location
- **Parser**: `ImportFio.java`
- **Note Logic**: "Text FIO" column mapped to ID_NOTE

### Trading 212 CZK Variant (ImportT212CZK.java)

ImportT212CZK.java is a separate parser for Trading 212 exports with Czech-specific tax columns.

**File Format:** Same as regular Trading 212 (comma-delimited CSV)

**Additional Fee Columns:**

| Column Name | Description | Example |
|-------------|-------------|----------|
| "Stamp duty reserve tax (CZK)" | Czech stamp duty tax | 25.50 |
| "Currency conversion fee (CZK)" | Currency conversion fee | 12.30 |

**Fee Handling:**
```java
drow.fee = parseNumber(a[feeStampIdx]) + parseNumber(a[feeConversionIdx]);
```

Both fees are combined and stored in the fee field with "CZK" currency.

**Note Format:** Identical to regular T212
```
[Company Name]|Broker:T212|ISIN:[ISIN]|TxnID:[TransactionID]
```

**Code References:**
- `ImportT212CZK.java:1-229` - Complete CZK variant implementation
- `ImportT212CZK.java:77-81` - Czech-specific column registrations (stamp duty, conversion fee)
- `ImportT212CZK.java:192` - Note construction (same as ImportT212.java)
- `ImportT212CZK.java:202` - Fee handling: stamp duty + conversion fee

## Revolut

### File Format Specifications
- **File Type**: CSV
- **Delimiter**: Comma (,)
- **Headers**: Standard English field names
- **Encoding**: UTF-8

### Note Construction Algorithm

**Format**: `"Broker:Revolut"`

**Logic**:
1. Hardcoded broker identifier
2. No additional context from CSV data

**Examples**:
- `"Broker:Revolut"`

**Edge Cases**:
- Consistent across all Revolut transactions
- No variation based on trade data

### Code Location
- **Parser**: `ImportRevolutCSV.java`
- **Note Logic**: Hardcoded string on line 183

## Firefish

### File Format Specifications
- **File Type**: CSV with header row
- **Delimiter**: Comma (,)
- **Encoding**: UTF-8 (BOM tolerated)
- **Date Formats**:
  - `MM-dd-yy` in older exports
  - `M/d/yyyy` and `M/d/yy` in newer exports
- **Imported Statuses**: `CLOSED`, `LIQUIDATED`
- **Ignored Statuses**: Any other value stays visible in `Neimportované`

### Business Semantics

Firefish rows are imported as **interest transactions**, not as security trades.

- **Ticker**: always `Kreditni.Urok`
- **Direction**: `DIRECTION_INT_BRUTTO`
- **Date / ExecutionDate**: `Closed at`
- **Amount**: always `1`
- **Price**: realized interest only, calculated as `Amount due - Investment amount`
- **Broker**: `Firefish`
- **Account ID**: `My account number`
- **Transaction ID**: `Investment id`

### Field Mapping Table

| Column | Field Name | Data Type | Description | Used In Output | Example Value |
| ------ | ---------- | --------- | ----------- | -------------- | ------------- |
| 0 | Investment id | String | Firefish investment identifier | ✅ (TxnID, Note) | `1e2d1770` |
| 1 | Start date (mm/dd/yyyy) | Date | Loan opening date | ✅ (Note) | `01-22-26` |
| 2 | Maturity date (mm/dd/yyyy) | Date | Planned maturity date | ✅ (Note) | `01-22-27` |
| 3 | Interest rate (% p.a.) | Decimal | Annual rate | ✅ (Note) | `9.00` |
| 4 | Currency | String | Settlement currency | ✅ (PriceCurrency, FeeCurrency, Note) | `CZK` |
| 5 | Investment amount | Decimal | Original principal | ✅ (Note) | `40000` |
| 6 | Amount due | Decimal | Final repayment value | ✅ (Interest calculation, Note) | `43600` |
| 7 | Status | String | Loan status | ✅ (Import decision, Note) | `CLOSED` |
| 8 | Closed at (mm/dd/yyyy) | Date | Real closing date | ✅ (Date, ExecutionDate, Note) | `02-24-26` |
| 9 | My account number | String | Investor account number | ✅ (AccountID, Note) | `251168350/0600` |
| 10 | Collateral sum (BTC) | Decimal | BTC collateral amount | ✅ (Note) | `0.04325200` |
| 11 | Liquidation price | Decimal | Liquidation threshold | ✅ (Note) | `1061109` |
| 12 | Investor id | String | Firefish investor identifier | ✅ (Note) | `1800000.0000072` |
| 13 | Borrower id | String | Firefish borrower identifier | ✅ (Note) | `e4df3dae` |
| 14 | Note | String | Optional CSV note | ✅ (Note) | `` |
| 15 | Loan type | String | Firefish product type | ✅ (Note) | `Standard` |

### Note Construction Algorithm

**Format**:
`Firefish loan closed|Broker:Firefish|AccountID:[AccountID]|TxnID:[InvestmentId]|StartDate:[StartDate]|MaturityDate:[MaturityDate]|ClosedAt:[ClosedAt]|Status:[Status]|InterestRate:[Rate]|Currency:[Currency]|InvestmentAmount:[InvestmentAmount]|AmountDue:[AmountDue]|EarnedInterest:[Result]|CollateralBTC:[Collateral]|LiquidationPrice:[LiquidationPrice]|LoanType:[LoanType]|InvestorID:[InvestorID]|BorrowerID:[BorrowerID]|CsvNote:[CsvNote]`

**Logic**:
1. Accept only rows with `Status = CLOSED` or `Status = LIQUIDATED`
2. Parse `Closed at` and use it as both transaction date and execution date
3. Calculate realized interest as `Amount due - Investment amount`
4. Store Firefish identifiers and loan metadata in `Note`
5. Omit empty segments instead of writing blank values

**Examples**:
- `Firefish loan closed|Broker:Firefish|AccountID:251168350/0600|TxnID:1e2d1770|StartDate:01-22-26|MaturityDate:01-22-27|ClosedAt:02-24-26|Status:CLOSED|InterestRate:9.00|Currency:CZK|InvestmentAmount:40000|AmountDue:43600|EarnedInterest:3600.0|CollateralBTC:0.04325200|LiquidationPrice:1061109|LoanType:Standard|InvestorID:1800000.0000072|BorrowerID:e4df3dae`
- `Firefish loan closed|Broker:Firefish|AccountID:LT863250094146707728|TxnID:a5d88418|StartDate:07-22-25|MaturityDate:07-22-27|ClosedAt:02-06-26|Status:LIQUIDATED|InterestRate:15.00|Currency:EUR|InvestmentAmount:1500|AmountDue:1950|EarnedInterest:450.0|CollateralBTC:0.03851100|LiquidationPrice:53300|LoanType:Standard|InvestorID:1800000.0000072|BorrowerID:ecca114f`

### Non-Imported Row Behavior

Firefish intentionally keeps skipped rows visible in the import window.

- **Ignored business rows**: prefixed with `IGNORED_STATUS:[status]`
  - Example: `IGNORED_STATUS:ACTIVE`
- **Row-level parsing/validation errors**: prefixed with `ERROR:[reason]`
  - Example: `ERROR:Firefish: neplatné datum 'not-a-date'.`

### ImportWindow Behavior

- Format name in UI: **`Firefish - csv`**
- Firefish local CSV import **ignores the `Importovat od / do` date filter**
  - the whole file is scanned on each preview/import
  - filtering is based on status and duplicate detection only
- Preview summary shows:
  - new rows
  - duplicate rows / rows to update
  - not imported rows
  - Firefish candidate count before deduplication
- If all Firefish candidates match existing transactions, preview explicitly reports that all candidates ended as duplicates

### Code Location
- **Parser**: `ImportFirefish.java`
- **ImportWindow integration**: `ImportWindow.java`
- **Format dispatch**: `TransactionSet.java`

## Configuration Options

### Note Format Settings

- **T212 Enhanced Notes**: Always enabled (company name + ISIN)
- **IB TradeLog Enhancement**: Planned - Account ID + Transaction ID inclusion
- **FIO Notes**: Raw text preservation (no modification)
- **Revolut Notes**: Static broker identifier
- **IBKR Flex Parser**: Structured note with conditional field inclusion
- **IBKR Flex Corporate Actions**: Special format (RS/TC) with full description
- **IBKR Flex Cash Transactions**: Enhanced format (Type, IssuerCountry, ISIN, ActionID)
- **Firefish Notes**: Structured loan-closing note with account, TxnID, amounts, status, and collateral metadata

### Import Behavior

- **Duplicate Detection**: Applied to all imports
- **Date Range Filtering**: Supported by most parsers; Firefish local CSV intentionally ignores `Importovat od / do`
- **Currency Handling**: Automatic currency detection and fee separation

### Note Format Settings
- **T212 Enhanced Notes**: Always enabled (company name + ISIN)
- **IB TradeLog Enhancement**: Planned - Account ID + Transaction ID inclusion
- **FIO Notes**: Raw text preservation (no modification)
- **Revolut Notes**: Static broker identifier
- **Firefish Notes**: Structured loan-closing note with explicit reason markers for skipped rows

### Import Behavior
- **Duplicate Detection**: Applied to all imports
- **Date Range Filtering**: Supported by most parsers; Firefish local CSV intentionally ignores `Importovat od / do`
- **Currency Handling**: Automatic currency detection and fee separation

## Version History

### v2026.01.17 - Smart Filtering & Note Enhancements
- **Added**: T212 enhanced notes with company names, ISIN codes, and Transaction IDs
- **Added**: T212 local file imports now match API format with full note enhancement
- **Added**: IB TradeLog account ID extraction capability
- **Added**: Structured note formats with broker identifiers
- **Added**: Comprehensive import data documentation

### v2026.01.17 - Duplicate Detection
- **Added**: Business key duplicate detection for all imports
- **Added**: Tolerance-based amount comparison (±0.01)
- **Added**: Import preview with duplicate filtering

### v2026.04.22 - Firefish CSV Import
- **Added**: Firefish broker import format for closed and liquidated loan CSV exports
- **Added**: Mapping from Firefish loan rows to `Úrok` transactions with ticker `Kreditni.Urok`
- **Added**: Structured Firefish note format with `TxnID`, account, amounts, status, and collateral metadata
- **Added**: Firefish-specific preview behavior for skipped rows and duplicate diagnostics

### v2021-2025 - Core Features
- **Added**: Multi-broker support (IB, T212, FIO, Revolut, Firefish)
- **Added**: Currency exchange rate management
- **Added**: Tax calculation and reporting features
- **Added**: HTML and CSV export capabilities

---

*This document serves as the authoritative reference for all broker import specifications. Please update this document when modifying import logic or adding new brokers.*
