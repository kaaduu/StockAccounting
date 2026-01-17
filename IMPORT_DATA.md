# Broker Import Data Formats and Note Construction

This document provides detailed technical specifications for all supported broker import formats, including exact field mappings, data parsing logic, and note construction algorithms.

## Table of Contents
1. [Overview](#overview)
2. [Trading 212 (T212)](#trading-212-t212)
3. [Interactive Brokers - TradeLog](#interactive-brokers-tradelog)
4. [Interactive Brokers - FlexQuery](#interactive-brokers-flexquery)
5. [FIO Bank](#fio-bank)
6. [Revolut](#revolut)
7. [Configuration Options](#configuration-options)
8. [Version History](#version-history)

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

**Format**: `[Company Name]|Broker:T212|ISIN:[ISIN Code]`

**Logic**:
1. Extract company name from column 4
2. Extract ISIN from column 2
3. Construct note with broker identifier

**Examples**:
- `"APPLE INC.|Broker:T212|ISIN:US0378331005"`
- `"TESLA INC|Broker:T212|ISIN:US88160R1014"`
- `"MICROSOFT CORPORATION|Broker:T212|ISIN:US5949181045"`

**Edge Cases**:
- Empty company name: `"|Broker:T212|ISIN:US0378331005"`
- Empty ISIN: `"APPLE INC.|Broker:T212|ISIN:"`
- Both empty: `"|Broker:T212|ISIN:"`

### Code Location
- **Parser**: `Trading212CsvParser.java`
- **Note Logic**: Lines 134-138

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

**Current Format**: `[Description]|Broker:IB` or `[Description]|Broker:IB|Code:[Status]`

**Planned Enhanced Format**: `[Ticker]|Broker:IB|AccountID:[AccountID]|TxnID:[TxnID]|Code:[Status]`

**Logic**:
1. Extract ticker from field 2
2. Extract account ID from header ACT_INF line field 1
3. Extract transaction ID from field 1
4. Include status code from field 6
5. Use broker identifier "IB"

**Examples**:
- `"GE|Broker:IB|AccountID:U393818|TxnID:996706497|Code:O"`
- `"AAPL|Broker:IB|AccountID:U393818|TxnID:1002441731|Code:C"`
- `"SHAK|Broker:IB|AccountID:U393818|TxnID:997854933|Code:Ca"`

**Status Code Meanings**:
- `O`: Open (opening trade/position)
- `C`: Close (closing trade/position)
- `Ca`: Cancelled transaction
- Other codes: Various IB-specific order statuses

### Code Location
- **Parser**: `ImportIBTradeLog.java`
- **Note Logic**: Lines 73-76
- **Enhancement**: Account ID extraction needed from header

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
- **Note Logic**: Hardcoded string

## Configuration Options

### Note Format Settings
- **T212 Enhanced Notes**: Always enabled (company name + ISIN)
- **IB TradeLog Enhancement**: Planned - Account ID + Transaction ID inclusion
- **FIO Notes**: Raw text preservation (no modification)
- **Revolut Notes**: Static broker identifier

### Import Behavior
- **Duplicate Detection**: Applied to all imports
- **Date Range Filtering**: Supported by all parsers
- **Currency Handling**: Automatic currency detection and fee separation

## Version History

### v2026.01.17 - Smart Filtering & Note Enhancements
- **Added**: T212 enhanced notes with company names and ISIN codes
- **Added**: IB TradeLog account ID extraction capability
- **Added**: Structured note formats with broker identifiers
- **Added**: Comprehensive import data documentation

### v2026.01.17 - Duplicate Detection
- **Added**: Business key duplicate detection for all imports
- **Added**: Tolerance-based amount comparison (±0.01)
- **Added**: Import preview with duplicate filtering

### v2021-2025 - Core Features
- **Added**: Multi-broker support (IB, T212, FIO, Revolut)
- **Added**: Currency exchange rate management
- **Added**: Tax calculation and reporting features
- **Added**: HTML and CSV export capabilities

---

*This document serves as the authoritative reference for all broker import specifications. Please update this document when modifying import logic or adding new brokers.*