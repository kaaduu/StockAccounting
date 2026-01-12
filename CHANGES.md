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
