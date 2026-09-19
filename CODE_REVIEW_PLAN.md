# Code Review Remediation Plan

**Branch:** `fix/code-review-cleanup`  
**Created:** 2026-09-19  
**Status:** In Progress

---

## Progress Summary

| Phase | Tasks | Completed | Status |
|-------|-------|-----------|--------|
| **Phase 1: Critical Bugs** | 5 | 0 | ⏳ Not Started |
| **Phase 2: Dead Code Removal** | 6 | 0 | ⏳ Not Started |
| **Phase 3: Structural Improvements** | 4 | 0 | ⏳ Not Started |
| **Phase 4: Build & Repo Hygiene** | 8 | 0 | ⏳ Not Started |
| **TOTAL** | **23** | **0** | **0%** |

---

## Phase 1: Critical Bugs (Financial Correctness)

These bugs affect data integrity, file persistence, and financial calculations. **Highest priority.**

### 1.1 Fix IBKRFlexCache silent cache loss ✅
- [x] **Issue:** `saveCacheIndex()` writes nothing; `loadCacheFromDisk()` reads `cache_index.json` that never exists
- [x] **Files:** `IBKRFlexCache.java:199-201`, `:161-164`
- [x] **Fix:** Implement actual JSON serialization in `saveCacheIndex()`, parse it in `loadCacheFromDisk()`
- [x] **Test:** `CacheRoundTripTest` — save year → new instance → `hasCachedYear()` returns true
- [x] **Effort:** 2h
- [x] **Status:** ⏳ Pending

### 1.2 Fix Trading212ReportCache write-only persistence ✅
- [x] **Issue:** `loadCacheFromDisk()` reads file but never parses JSON
- [x] **Files:** `Trading212ReportCache.java:97-124`
- [x] **Fix:** Parse the JSON that `saveCacheToDisk()` writes
- [x] **Test:** Same `CacheRoundTripTest`
- [x] **Effort:** 1h
- [x] **Status:** ⏳ Pending

### 1.3 Unify NumberParser (5 conflicting implementations) ✅
- [x] **Issue:** `ComputeWindow.parseDouble`, `Trading212CsvParser.parseDouble`, `IBKRFlexParser.parseDouble`, `AccountStateWindow.parseDouble`, `ImportBase.parseNumber` all disagree
- [x] **Risk:** Czech `"1234,56"` → IBKRFlexParser drops comma → `"123456"` (1000× wrong)
- [x] **Fix:** Extract one `NumberParser` class using `ComputeWindow`'s robust logic (handles Unicode minus, NBSP, parentheses)
- [x] **Test:** `NumberParsingTest` — 20 inputs including `"1 234,56"`, `"(1 234.56)"`, `"−12,34"` (U+2212), NBSP variants
- [x] **Effort:** 3h
- [x] **Status:** ⏳ Pending

### 1.4 Fix ComputeWindow.saveHTML() resource leak ✅
- [x] **Issue:** `PrintWriter ofl = new PrintWriter(new FileWriter(file))` with no try-with-resources
- [x] **Files:** `ComputeWindow.java:630`
- [x] **Fix:** Wrap in try-with-resources (cf. `:678` which already does it correctly)
- [x] **Effort:** 15 min
- [x] **Status:** ⏳ Pending

### 1.5 Fix MainWindow FIO export char-by-char transcoding + leak ✅
- [x] **Issue:** `MainWindow.java:2551-2566` — manual char-by-char UTF-8→Windows-1250 conversion, no finally block
- [x] **Fix:** Use try-with-resources, buffered I/O, or write directly in Windows-1250
- [x] **Effort:** 30 min
- [x] **Status:** ⏳ Pending

---

## Phase 2: Dead Code Removal

**1,172 lines of dead methods + orphan classes/files.** Removing these reduces cognitive load and jar size.

### 2.1 Delete dead methods (93 methods, 1,172 lines) ✅
- [x] **Top offenders:**
  - `ImportWindow.normalizeIbkrMinuteCollisions` (146 lines)
  - `IBKRFlexParser.processCorporateActions` (129 lines)
  - `CurrencyRateFetcher.fetchAnnualDailyRates` (72 lines)
  - `ImportWindow.disambiguateIbkrDuplicateCollisions` (68 lines)
  - `IBKRFlexParser.parseCorporateActionRow` (54 lines)
  - `Settings.showDeleteDailyRatesDialog` (41 lines)
  - `EncryptionUtils.encryptStream`/`decryptStream` (62 lines)
  - `ComputeWindow.saveHTMLHeaderNewTrades` (31 lines)
  - `CloudSyncManager.backupTransactionFile` (31 lines)
  - `Trading212ApiClient.fetchHistoricalOrdersPaginated` (28 lines)
- [x] **Full list:** See review section B
- [x] **Effort:** 2h
- [x] **Status:** ⏳ Pending

### 2.2 Delete orphan class CloudBackupData ✅
- [x] **Files:** `CloudBackupData.java` (63 lines, 10 accessors)
- [x] **Issue:** Referenced only by its own file and `CHANGES.md:168`; not used by `CloudSyncManager`, not Gson-serialised
- [x] **Effort:** 15 min
- [x] **Status:** ⏳ Pending

### 2.3 Delete dead fields (14 total) ✅
- [x] `ImportWindow.cbIBKRFlexUpdateDups` — declared but never instantiated (functional gap: IBKR Flex "update duplicates" has no widget)
- [x] `ImportWindow.lblCacheStatus` — declared but never used
- [x] `Trading212CsvParser.H_EXCHANGE_RATE`, `H_RESULT`, `H_RESULT_CUR`, `H_FEE_CONV`, `H_FEE_CONV_CUR` — header constants never read
- [x] `ImportBase.DECODER_WIN1250`, `DECODER_ISO88592` — eagerly constructed, never used
- [x] `CloudSyncDialog.progressTimer`, `GoogleDriveClient.TOKENS_DIRECTORY_PATH`, `IBKRFlexClient.POLL_INTERVAL_SECONDS`, `IBKRFlexImporter.CACHE_FILE`, `Trading212CsvClient.CSV_RATE_LIMIT_REQUESTS`
- [x] **Effort:** 1h
- [x] **Status:** ⏳ Pending

### 2.4 Delete unused imports (23 across 15 files) ✅
- [x] **Worst:** `TransactionSet.java` (3), `IBKRFlexImporter.java` (3), `UiDialogs.java` (2), `Trading212ApiClient.java` (2), `MainWindow.java` (2), `CsvReportProgressDialog.java` (2)
- [x] **Effort:** 30 min
- [x] **Status:** ⏳ Pending

### 2.5 Delete orphan files outside build ✅
- [x] `TestRenderer.java` (repo root) — outside `srcDirs`, would not compile (missing `import java.awt.Font`)
- [x] `tools/VerifyIbkrLabel.java` — one-off bytecode grep tool, javadoc says *"Not used by the application"*
- [x] `untitled folder/` — empty directory
- [x] `tools/__pycache__/` — should be gitignored
- [x] `.codex` — empty file, mode `-r--r--r--`
- [x] **Effort:** 15 min
- [x] **Status:** ⏳ Pending

### 2.6 Delete RateManagementDialog.java.backup ✅
- [x] **Issue:** 40,941 bytes shipped inside production jar (verified in `build/libs/StockAccounting.jar`)
- [x] **Fix:** Delete file, add `exclude '**/*.backup'` to `build.gradle:38`
- [x] **Effort:** 15 min
- [x] **Status:** ⏳ Pending

---

## Phase 3: Structural Improvements

These reduce duplication and improve maintainability. **Medium priority.**

### 3.1 Replace magic formatIndex integers with ImportFormat enum ✅
- [x] **Issue:** 32 hard-coded `formatIndex == N` comparisons coupled to combobox order at `ImportWindow.java:3514-3517`
- [x] **Risk:** Inserting/reordering a combobox entry silently mis-archives files, mis-filters dates, shows wrong help button
- [x] **Fix:** Extract `enum ImportFormat { NONE, FIO, BROKERJET, IB_TRADELOG, IB_FLEXQUERY_LEGACY, T212_USD, T212_CZK, REVOLUT, T212_API, IBKR_FLEX, FIREFISH }` with `displayName`, `brokerKey`, `prefix`, `extensions[]`, `isLocalFile`, `ignoresDateFilter`
- [x] **Test:** `ImportFormatTest` — assert combobox order ↔ formatIndex mapping for all 11 entries
- [x] **Effort:** 4h
- [x] **Status:** ⏳ Pending

### 3.2 Merge ImportT212 and ImportT212CZK ✅
- [x] **Issue:** 84% identical (232 vs 229 lines, only 37 differ)
- [x] **Delta:** Class name, three fee column registrations, uncommenting `Deposit`/`Withdrawal` branch, fee expression
- [x] **Fix:** Extract common base with `registerCurrencyColumns()` and `computeFee(row)` hooks
- [x] **Effort:** 1.5h
- [x] **Status:** ⏳ Pending

### 3.3 Unify 4 parallel cache implementations ✅
- [x] **Classes:** `CacheManager` (157 lines), `IBKRFlexCache` (208), `Trading212CsvCache` (427), `Trading212ReportCache` (183)
- [x] **Duplicates:** Path sanitisation (`CacheManager.sanitize` vs `Trading212CsvCache.sanitizeAccountId`), legacy-dir migration (same block twice in `IBKRFlexCache.loadCacheFromDisk`), hand-rolled JSON (while `org.json` is a dependency)
- [x] **Fix:** One `FileCache<K, V>` abstraction on top of `CacheManager`
- [x] **Effort:** 6h
- [x] **Status:** ⏳ Pending

### 3.4 Extract Formats class with DateTimeFormatter constants ✅
- [x] **Issue:** 20+ sites with repeated `"dd.MM.yyyy"` / `"yyyy-MM-dd"` literals; 14 `new SimpleDateFormat(...)` instantiations (not thread-safe, re-parse pattern each time)
- [x] **Fix:** `Formats` class with `DateTimeFormatter` constants (immutable + thread-safe)
- [x] **Benefit:** Aligns with AGENTS.md rule 3 (*"prefer `java.time`"*)
- [x] **Effort:** 2h
- [x] **Status:** ⏳ Pending

---

## Phase 4: Build & Repo Hygiene

**Low priority** but improves developer experience and reduces confusion.

### 4.1 Fix .gitignore ✅
- [x] **Issue:** `build/` listed twice (lines 2 and 9); `wiki/` ignored but tracked and populated
- [x] **Add:** `.codegraph/`, `.gradle-test/`, `.zcode/`, `.vscode/`, `.sisyphus/drafts/`, `.codex`, `untitled folder/`, `tools/__pycache__/`
- [x] **Remove:** Duplicate `build/`; `wiki/` (or keep if intentionally ignored)
- [x] **Effort:** 15 min
- [x] **Status:** ⏳ Pending

### 4.2 Fix run.sh classpath hazard ✅
- [x] **Issue:** `run.sh:52` uses `java -cp "build:libjar/*"` which puts **both** `ib-twsapi-1042.01.jar` and `ib-twsapi-1042.01-debug.jar` on classpath (same classes twice, 974 protobuf entries each)
- [x] **Fix:** Mirror Gradle exclusion: `libjar/*.jar` minus `*debug*.jar`, or delete the debug jar
- [x] **Effort:** 15 min
- [x] **Status:** ⏳ Pending

### 4.3 Delete or document stale Ant/NetBeans build system ✅
- [x] **Files:** `build.xml` (3,557 B), `nbproject/`, `manifest.mf` (wrong `Class-Path`), `lib/nblibraries.properties` (references missing jar)
- [x] **Issue:** Nothing references them; project builds via Gradle
- [x] **Fix:** Delete, or add README section explaining they are intentionally retained for legacy reasons
- [x] **Effort:** 30 min
- [x] **Status:** ⏳ Pending

### 4.4 Delete legacy src/version.properties ✅
- [x] **Issue:** `build.gradle:96` generates `version.properties` and explicitly excludes the checked-in `src/version.properties` as *"legacy file"*
- [x] **Fix:** Delete `src/version.properties`
- [x] **Effort:** 5 min
- [x] **Status:** ⏳ Pending

### 4.5 Exclude .form files from jar ✅
- [x] **Issue:** 137 KB of NetBeans `.form` files shipped in jar (`MainWindow.form`, `SettingsWindow.form`, etc.) — never loaded at runtime
- [x] **Fix:** Add `exclude '**/*.form'` to `build.gradle:38`
- [x] **Effort:** 10 min
- [x] **Status:** ⏳ Pending

### 4.6 Route 69 swallowed exceptions through AppLog ✅
- [x] **Issue:** Empty or comment-only `catch` bodies. Worst: `SettingsWindow` (14), `ImportWindow` (9), `MainWindow` (8), `ComputeWindow` (6), `TransactionSet` (6)
- [x] **Notable:** `imp/ImportBase.java:101` — `catch(Exception e) {}` on charset round-trip (silent column-detection failure); `ImportWindow.java:742-743` — `catch (Exception e) { // Best effort }` around archive-to-cache block
- [x] **Fix:** Replace with `AppLog.warn("...: " + e.getMessage())` or `AppLog.error(...)` with stack trace
- [x] **Effort:** 3h
- [x] **Status:** ⏳ Pending

### 4.7 Fix 14 resource leaks (raw stream opens outside try-with-resources) ✅
- [x] **Files:** `CurrencyRateFetcher.java:91,300,379` (3 copies), `StockPriceFetcher.java:68`, `MainWindow.java:2551-2558`, `TransactionRepository.java:35,141`, `AboutWindow.java:119`, `GoogleDriveClient.java:51`
- [x] **Fix:** Wrap in try-with-resources
- [x] **Effort:** 2h
- [x] **Status:** ⏳ Pending

### 4.8 Commit pending ComputeWindow change ✅
- [x] **Issue:** `ComputeWindow.java` has uncommitted changes (+47/-11) on `master` — adds `getColumnClass` override, switches `addRow(new String[])` → `addRow(new Object[])`, adds `rightTotalNative` renderer
- [x] **Fix:** Commit on `fix/code-review-cleanup` branch
- [x] **Effort:** 5 min
- [x] **Status:** ⏳ Pending

---

## Verification Checklist

After each phase, verify:

- [ ] `./gradlew compileJava` → BUILD SUCCESSFUL
- [ ] `./gradlew test` → all tests pass (including new tests)
- [ ] `./build.sh` → distribution builds successfully
- [ ] `unzip -l build/libs/StockAccounting.jar | grep -E "backup|\.form"` → no `.backup` or `.form` files (after Phase 2.6 and 4.5)
- [ ] Manual smoke test: app starts, can open a `.dat` file, can import a CSV

---

## Notes

- **AGENTS.md compliance:** Every completed task must update `CHANGES.md` (Czech language, per rule 2).
- **Test-first:** For Phase 1, write failing tests **before** fixes (TDD approach).
- **Backward compatibility:** Do not change `.dat` file format (per existing refactoring plan guardrails).
- **No UI behavior changes:** Keep existing Swing interface (per existing refactoring plan guardrails).
- **Git workflow:** Commit each phase separately with descriptive messages (per AGENTS.md rule 6).

---

## Estimated Total Effort

| Phase | Effort |
|-------|--------|
| Phase 1: Critical Bugs | 6.75h |
| Phase 2: Dead Code Removal | 4h |
| Phase 3: Structural Improvements | 13.5h |
| Phase 4: Build & Repo Hygiene | 7h |
| **TOTAL** | **31.25h** |

---

## Actual Progress Log

| Date | Task | Time Spent | Notes |
|------|------|------------|-------|
| 2026-09-19 | Plan created | 1h | Initial review and plan authoring |
| | | | |

---

**Next action:** Start with Phase 1.1 (IBKRFlexCache fix) + write `CacheRoundTripTest` first.
