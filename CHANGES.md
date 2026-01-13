# Changes

All notable changes to the StockAccounting project will be documented in this file.

## [Modernization & Java 21 Migration] - 2026-01-12

### Added
- **Command-Line Build System**: Added `build.sh` and `run.sh` to allow building and running the project without NetBeans.
- **Native File Choosers**: Integrated `java.awt.FileDialog` across the application for a native and responsive file selection experience (Open, Save, Import, Export).
- **UX Shortcut**: Added "Enter to filter" functionality to date fields in `MainWindow`.
- **Modern Dependencies**: Added `jcalendar-1.4.jar`.

### Changed
- **Java 21 Compatibility**: 
    - Replaced deprecated `Integer` and `Double` constructors with `valueOf()`.
    - Added generics to `SortedSetComboBoxModel`, `JComboBox`, and `DefaultComboBoxModel` to reduce raw type warnings.
    - Updated `TransactionSet.java` and `ComputeWindow.java` to fix raw `Class` type warnings.
- **DatePicker Replacement**: Replaced the obscure `com.n1logic.date.DatePicker` with `com.toedter.calendar.JDateChooser`.
- **Library Upgrades**: Upgraded `jcalendar` from 1.3.2 to 1.4.
- **Improved MainWindow UI**: Migrated `MainWindow.java` and `MainWindow.form` to use `JDateChooser`.

### Removed
- **Obsolete Libraries**:
    - `DatePicker.jar` (replaced by JDateChooser).
    - `looks-2.0.1.jar` (unused JGoodies library).
    - `swing-layout-1.0.3.jar` (redundant, version 1.0.4 is kept).
## [Currency Rate Fetching Feature] - 2026-01-13

### Added
- **CNB Integration**: New `CurrencyRateFetcher` utility to automatically fetch official "jednotný kurz" (unified exchange rate) from the Czech National Bank (CNB).
- **Automated Calculations**: Calculates "jednotný kurz" as an arithmetic mean of 12 month-end rates according to Ministry of Finance (GFŘ) guidelines.
- **Enhanced Settings UI**:
    - Added "Načíst kurzy" button to the currency settings table.
    - Added modal progress bar with real-time feedback during data fetching.
    - Implemented **Single Year Fetching**: Users can now fetch rates for a specific selected year or all at once.
- **Preview & Rollback**: 
    - Fetched rates are previewed in the table with yellow highlighting for modified values.
    - Confirmation dialog allows users to either apply the changes or rollback to original values.
- **Precision Grounding**: Exchange rates are automatically rounded to 2 decimal places to match official tax requirements.

### Changed
- **SettingsWindow Improvements**: Integrated `HighlightedDoubleRenderer` to visually distinguish fetched data from manual entries.
- **Validation**: Added comparison logic (0.001 tolerance) to identify and highlight modified exchange rates.
## [Daily Exchange Rates Support] - 2026-01-13

### Added
- **Daily Rate Maintenance**: New "Denní kurzy" tab in Settings to manage precise daily exchange rates.
- **Bulk Fetching**: Efficient annual bulk download of daily rates from CNB.
- **Smart Fetch Tool**: Automatically identifies years with existing trades and downloads missing daily rates for them.
- **Calculation Wrapper**: Implemented a centralized exchange rate provider that switches between Daily and Unified rates based on global settings.
- **Persistence**: Daily rates are stored in a dedicated `daily_rates.dat` file in the data directory.
- **Global Toggle**: Added a "Používat denní kurzy" setting to control precision level across all calculations (trades, dividends, taxes).

### Changed
- **Core Calculation Integration**: Migrated `Stocks.java` and `ComputeWindow.java` to use the new exchange rate wrapper.
- **Data Persistence**: Updated `Settings.java` to handle new settings and daily rate storage.
