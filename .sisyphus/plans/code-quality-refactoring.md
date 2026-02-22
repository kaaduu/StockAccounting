# Refactoring Work Plan: StockAccounting Codebase Modernization

## TL;DR

> **Objective**: Systematically refactor the StockAccounting Java codebase to improve code quality, SOLID compliance, and maintainability while preserving all functionality.
>
> **Deliverables**: 
> - New git branch `refactor/code-quality-overhaul`
> - Phase-by-phase refactoring with atomic commits
> - Unit test infrastructure and initial test coverage
> - Resource leak fixes
> - Architecture improvements (SRP compliance)
>
> **Estimated Effort**: Large (20-30 hours across 5 phases)
> **Parallel Execution**: NO - phases must be sequential due to dependencies
> **Critical Path**: Phase 1 (setup) → Phase 2 (tests) → Phase 3 (fixes) → Phase 4 (refactor) → Phase 5 (cleanup)

---

## Context

### Original Request
Conduct code review of Java codebase and provide refactoring plan with immediate implementation starting with branch creation.

### Code Review Summary
- **54 Java files** analyzed (~15,000+ lines)
- **Critical issues**: God classes (SRP violations), missing tests, resource leaks
- **Major issues**: Deprecated APIs, long switch statements, mixed responsibilities
- **Minor issues**: Inconsistent naming, dead code, magic numbers

### Key Findings from Review
1. **Transaction.java** (1,256 lines) - Violates SRP with 5+ responsibilities
2. **TransactionSet.java** - Mixes UI (TableModel), business logic, and persistence
3. **Zero test coverage** - Only TestRenderer.java exists (24 lines, not a real test)
4. **Resource leaks** - File readers/writers not properly closed
5. **Deprecated APIs** - Uses pre-Java 8 Date/Calendar throughout

---

## Work Objectives

### Core Objective
Refactor the StockAccounting codebase to improve maintainability, testability, and code quality while preserving all existing functionality.

### Concrete Deliverables
1. New branch `refactor/code-quality-overhaul` created from `origin/master`
2. JUnit 5 test infrastructure added to build.gradle
3. Initial test suite covering critical classes (Transaction, TransactionSet)
4. All resource leaks fixed (try-with-resources)
5. Export logic extracted from Transaction class
6. Persistence logic separated from TransactionSet
7. Empty catch blocks fixed with proper logging

### Definition of Done
- [ ] `./gradlew test` runs successfully with passing tests
- [ ] `./gradlew build` compiles without warnings
- [ ] All resource leaks identified in code review are fixed
- [ ] At least 20% test coverage for core business logic
- [ ] No new compiler warnings introduced
- [ ] All existing functionality preserved (manual verification)

### Must Have
- Git branch created before any code changes
- Each phase committed separately with descriptive messages
- Tests written BEFORE refactoring (TDD approach where possible)
- Backward compatibility maintained for data files

### Must NOT Have (Guardrails)
- NO changes to existing .dat file format (backward compatibility required)
- NO UI behavior changes (keep existing Swing interface)
- NO breaking changes to import/export formats
- NO removal of features (only internal refactoring)
- NO changes to external API clients (T212, IBKR, etc.)

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: NO (needs to be added)
- **User wants tests**: YES (TDD approach for new code)
- **Framework**: JUnit 5 (Jupiter)
- **QA approach**: TDD for new code, tests-after for existing code

### Test Setup Task
- [ ] 0. Setup Test Infrastructure
  - Add to build.gradle: `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'`
  - Add to build.gradle: `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`
  - Create `src/test/java/cz/datesoft/stockAccounting/` directory structure
  - Create example test to verify setup: `TransactionTest.java`
  - Verify: `./gradlew test` → 1 test passes

### Automated Verification by Phase

**For each phase:**
1. Compile check: `./gradlew compileJava` → BUILD SUCCESS
2. Test check: `./gradlew test` → all tests pass
3. Build check: `./gradlew build` → no errors
4. Spotless check (if configured): `./gradlew spotlessCheck`

**For resource leak fixes:**
```bash
# After fixing file operations, verify no resource warnings:
./gradlew compileJava 2>&1 | grep -i "resource\|close\|try" || echo "No resource warnings"
```

**For refactoring verification:**
```bash
# Ensure no regression in functionality:
./gradlew run  # Manual smoke test - app should start
```

---

## Execution Strategy

### Phase Dependencies

```
Phase 1: Setup
└── Phase 2: Add Tests
    └── Phase 3: Fix Critical Issues
        └── Phase 4: Extract Responsibilities
            └── Phase 5: Cleanup & Documentation
```

### Critical Path
- Phase 1 must complete before any code changes
- Phase 2 tests must pass before Phase 3 refactoring
- Phase 3 fixes must be verified before Phase 4 restructuring
- Phase 4 must maintain test compatibility
- Phase 5 final validation

---

## TODOs

### Phase 1: Git Branch Setup & Infrastructure

- [ ] 1.1 Create and checkout new branch

  **What to do**:
  - Create branch `refactor/code-quality-overhaul` from `origin/master`
  - Push branch to origin
  - Verify clean working directory

  **Must NOT do**:
  - Do NOT commit directly to master/test branches
  - Do NOT start coding before branch is created

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `git-master`
    - Branch creation and management
    - Remote push operations

  **Parallelization**:
  - **Can Run In Parallel**: NO (must be first)
  - **Blocks**: All subsequent phases

  **Acceptance Criteria**:
  - [ ] Branch `refactor/code-quality-overhaul` exists locally
  - [ ] Branch pushed to `origin`
  - [ ] `git status` shows "On branch refactor/code-quality-overhaul"
  - [ ] No uncommitted changes from previous work

  **Commit**: YES
  - Message: `chore(git): create refactor/code-quality-overhaul branch`
  - No files changed (branch creation only)

---

- [ ] 1.2 Add JUnit 5 test infrastructure

  **What to do**:
  - Add JUnit 5 dependencies to build.gradle
  - Create test source directory structure
  - Create sample test class to verify setup
  - Configure test task in Gradle

  **Must NOT do**:
  - Do NOT add test dependencies to main classpath
  - Do NOT use JUnit 4 (use JUnit 5 Jupiter)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
    - Gradle configuration
    - JUnit 5 setup

  **Parallelization**:
  - **Can Run In Parallel**: NO (sequential within Phase 1)
  - **Blocked By**: 1.1 Branch creation
  - **Blocks**: Phase 2

  **Acceptance Criteria**:
  - [ ] `build.gradle` contains JUnit 5 dependencies
  - [ ] `src/test/java` directory exists
  - [ ] `src/test/java/cz/datesoft/stockAccounting/` package created
  - [ ] `./gradlew test` runs successfully (even if 0 tests)
  - [ ] Sample test `TransactionTest.java` created and passes

  **Commit**: YES
  - Message: `build(gradle): add JUnit 5 test infrastructure`
  - Files: `build.gradle`, test directory structure

---

### Phase 2: Write Initial Tests

- [ ] 2.1 Write tests for Transaction class

  **What to do**:
  - Create `TransactionTest.java` with comprehensive tests
  - Test constructors, getters, setters
  - Test validation logic
  - Test edge cases (null values, empty strings)
  - Test direction handling

  **Must NOT do**:
  - Do NOT skip testing edge cases
  - Do NOT test private methods directly (test public API)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
    - JUnit 5 testing patterns
    - Java unit testing best practices

  **Parallelization**:
  - **Can Run In Parallel**: NO (sequential within Phase 2)
  - **Blocked By**: 1.2 Test infrastructure
  - **Blocks**: Phase 3

  **Acceptance Criteria**:
  - [ ] `TransactionTest.java` created with minimum 10 test methods
  - [ ] All tests pass: `./gradlew test --tests TransactionTest`
  - [ ] Tests cover: construction, validation, direction handling
  - [ ] Code coverage report shows Transaction class coverage > 50%

  **Commit**: YES
  - Message: `test(transaction): add comprehensive unit tests`
  - Files: `src/test/java/cz/datesoft/stockAccounting/TransactionTest.java`

---

- [ ] 2.2 Write tests for TransactionSet class

  **What to do**:
  - Create `TransactionSetTest.java`
  - Test add, delete, sort operations
  - Test filtering functionality
  - Test persistence (save/load)
  - Mock file operations for testing

  **Must NOT do**:
  - Do NOT create tests that depend on actual files (use temp files)
  - Do NOT test Swing UI behavior in unit tests

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
    - JUnit 5 testing
    - Mockito for mocking (optional)
    - Temp file handling

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: 2.1

  **Acceptance Criteria**:
  - [ ] `TransactionSetTest.java` created with minimum 8 test methods
  - [ ] All tests pass: `./gradlew test --tests TransactionSetTest`
  - [ ] Tests cover: CRUD operations, sorting, filtering
  - [ ] Uses temporary files for persistence tests

  **Commit**: YES
  - Message: `test(transactionset): add unit tests for core operations`
  - Files: `src/test/java/cz/datesoft/stockAccounting/TransactionSetTest.java`

---

### Phase 3: Fix Critical Issues

- [ ] 3.1 Fix resource leaks with try-with-resources

  **What to do**:
  - Fix `TransactionSet.java:777` - BufferedReader in load()
  - Fix `TransactionSet.java:706` - PrintWriter in saveInternal()
  - Fix `ImportFio.java:58` - BufferedReader in doImport()
  - Search for other FileReader/FileWriter/BufferedReader/PrintWriter usages
  - Apply try-with-resources pattern to all

  **Must NOT do**:
  - Do NOT change file format or logic, only resource management
  - Do NOT remove existing exception handling, just improve it

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
    - Java resource management
    - Try-with-resources pattern

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: Phase 2 tests
  - **Blocks**: Phase 4

  **Acceptance Criteria**:
  - [ ] All identified resource leaks fixed
  - [ ] `./gradlew compileJava` shows no resource warnings
  - [ ] Existing tests still pass
  - [ ] Manual test: save/load file still works

  **Commit**: YES
  - Message: `fix(resources): close file streams with try-with-resources`
  - Files: `TransactionSet.java`, `ImportFio.java`, others as needed

---

- [ ] 3.2 Fix empty catch blocks

  **What to do**:
  - Fix `Main.java:33` - empty catch after UiTheme.applyFromSettings()
  - Fix `TransactionSet.java:265` - empty catch in readLine()
  - Find and fix all empty catch blocks in codebase
  - Add proper logging (SLF4J or java.util.logging)

  **Must NOT do**:
  - Do NOT silently swallow exceptions
  - Do NOT change control flow dramatically

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
    - Java exception handling
    - Logging best practices

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: 3.1

  **Acceptance Criteria**:
  - [ ] No empty catch blocks remain
  - [ ] All exceptions logged appropriately
  - [ ] Tests still pass
  - [ ] App still runs without new errors

  **Commit**: YES
  - Message: `fix(error-handling): log exceptions instead of swallowing`
  - Files: Multiple files with empty catches

---

### Phase 4: Extract Responsibilities (SRP)

- [ ] 4.1 Extract export logic from Transaction class

  **What to do**:
  - Create new class `TransactionExporter` in `export/` package
  - Move `export()` method logic to `TransactionExporter.exportTransaction()`
  - Move `exportFIO()` method logic to `TransactionExporter.exportToFIO()`
  - Keep `Transaction.export()` as deprecated delegate to new class
  - Update all callers gradually

  **Must NOT do**:
  - Do NOT delete old methods immediately (deprecate first)
  - Do NOT change export format behavior

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
    - Refactoring techniques
    - SRP principles
    - API design

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: Phase 3
  - **Blocks**: 4.2

  **Acceptance Criteria**:
  - [ ] `TransactionExporter.java` created with export methods
  - [ ] Original `Transaction.export()` delegates to new class
  - [ ] All existing tests pass
  - [ ] Export functionality verified manually
  - [ ] Old methods marked `@Deprecated`

  **Commit**: YES
  - Message: `refactor(export): extract export logic from Transaction class`
  - Files: `TransactionExporter.java`, `Transaction.java`

---

- [ ] 4.2 Extract persistence logic from TransactionSet

  **What to do**:
  - Create `TransactionRepository` class for persistence operations
  - Move `save()`, `load()`, `loadAdd()` to repository
  - Create `TransactionTableModel` that wraps TransactionSet for Swing
  - Keep TransactionSet as pure data model
  - Update MainWindow to use new model classes

  **Must NOT do**:
  - Do NOT change file format
  - Do NOT break existing UI behavior

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
  - **Skills**: []
    - Repository pattern
    - MVC architecture
    - Complex refactoring

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: 4.1

  **Acceptance Criteria**:
  - [ ] `TransactionRepository.java` created
  - [ ] `TransactionTableModel.java` created
  - [ ] `TransactionSet` no longer extends `AbstractTableModel`
  - [ ] All persistence tests pass
  - [ ] UI still works correctly (manual test)

  **Commit**: YES (potentially multiple commits)
  - Message: `refactor(persistence): extract repository and table model from TransactionSet`
  - Files: New classes, `TransactionSet.java`, `MainWindow.java`

---

### Phase 5: Cleanup & Documentation

- [ ] 5.1 Remove dead code and comments

  **What to do**:
  - Remove commented-out code blocks (e.g., Transaction.java:342-356)
  - Remove unused imports
  - Remove unused private methods
  - Clean up TODOs (implement or document)

  **Must NOT do**:
  - Do NOT remove "unused" methods that are actually used via reflection
  - Do NOT change public API without deprecation

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
    - Code cleanup
    - Static analysis

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: Phase 4

  **Acceptance Criteria**:
  - [ ] No commented-out code blocks remain
  - [ ] No unused imports
  - [ ] `./gradlew compileJava` still succeeds
  - [ ] All tests pass

  **Commit**: YES
  - Message: `chore(cleanup): remove dead code and unused imports`
  - Files: Multiple files

---

- [ ] 5.2 Update CHANGES.md

  **What to do**:
  - Document all refactoring changes in CHANGES.md (in Czech per project requirements)
  - List new classes added
  - List breaking changes (should be none)
  - List improvements made

  **Must NOT do**:
  - Do NOT write in English (project requires Czech for CHANGES.md)
  - Do NOT omit any significant changes

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: []
    - Technical writing in Czech
    - Documentation standards

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: 5.1

  **Acceptance Criteria**:
  - [ ] CHANGES.md updated with new section
  - [ ] Written in Czech language
  - [ ] Documents all major changes from this refactoring

  **Commit**: YES
  - Message: `docs(changes): document refactoring changes`
  - Files: `CHANGES.md`

---

- [ ] 5.3 Final validation and merge preparation

  **What to do**:
  - Run full test suite: `./gradlew test`
  - Run build: `./gradlew build`
  - Verify no compiler warnings
  - Create summary of changes for PR
  - Prepare merge to master

  **Must NOT do**:
  - Do NOT merge without review
  - Do NOT skip validation steps

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
    - Build verification
    - Git workflow

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocked By**: 5.2

  **Acceptance Criteria**:
  - [ ] `./gradlew clean build` succeeds
  - [ ] All tests pass
  - [ ] No compiler errors or warnings
  - [ ] Manual smoke test: app starts and basic operations work
  - [ ] Branch ready for PR/review

  **Commit**: NO (validation only)

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1.1 | `chore(git): create refactor/code-quality-overhaul branch` | N/A | `git branch` |
| 1.2 | `build(gradle): add JUnit 5 test infrastructure` | build.gradle, test dirs | `./gradlew test` |
| 2.1 | `test(transaction): add comprehensive unit tests` | TransactionTest.java | `./gradlew test` |
| 2.2 | `test(transactionset): add unit tests for core operations` | TransactionSetTest.java | `./gradlew test` |
| 3.1 | `fix(resources): close file streams with try-with-resources` | TransactionSet.java, ImportFio.java | `./gradlew build` |
| 3.2 | `fix(error-handling): log exceptions instead of swallowing` | Multiple files | `./gradlew build` |
| 4.1 | `refactor(export): extract export logic from Transaction class` | TransactionExporter.java, Transaction.java | `./gradlew test` |
| 4.2 | `refactor(persistence): extract repository and table model` | New classes, TransactionSet.java | `./gradlew test`, manual UI test |
| 5.1 | `chore(cleanup): remove dead code and unused imports` | Multiple files | `./gradlew build` |
| 5.2 | `docs(changes): document refactoring changes` | CHANGES.md | Review |

---

## Success Criteria

### Verification Commands
```bash
# Full build and test
./gradlew clean build

# Run all tests
./gradlew test

# Check for compiler warnings
./gradlew compileJava 2>&1 | grep -i warning || echo "No warnings"

# Manual smoke test
./gradlew run
```

### Final Checklist
- [ ] All tests pass (minimum 18 test methods total)
- [ ] No compiler warnings
- [ ] CHANGES.md updated in Czech
- [ ] Resource leaks fixed (verified via code inspection)
- [ ] Export logic extracted to TransactionExporter
- [ ] Persistence logic extracted to TransactionRepository
- [ ] TransactionSet no longer extends AbstractTableModel
- [ ] App launches and basic operations work (manual test)
- [ ] Branch `refactor/code-quality-overhaul` ready for PR

---

## Risk Mitigation

### High Risk Areas
1. **TransactionSet refactoring** - Core class, affects entire app
   - Mitigation: Comprehensive tests first, gradual extraction
2. **File persistence changes** - Risk of data corruption
   - Mitigation: Do NOT change format, only resource management
3. **Export format changes** - External compatibility
   - Mitigation: Maintain exact same output format

### Rollback Strategy
If critical issues found:
1. Each phase is committed separately - can revert individual commits
2. Branch can be abandoned without affecting master
3. Original code preserved in master branch

### Testing Strategy
- Write tests BEFORE refactoring (Phase 2 before Phase 4)
- Manual testing after each phase
- Full regression test before merge

---

## Notes for Executor

### Git Workflow
1. Always work on `refactor/code-quality-overhaul` branch
2. Commit after each phase/task
3. Use conventional commit messages as specified
4. Push regularly to origin for backup
5. Before merge: squash or clean up commit history if needed

### Communication
- Update CHANGES.md in Czech after significant changes
- Document any deviations from plan
- Flag any unexpected issues immediately

### Time Estimates
- Phase 1: 30 minutes
- Phase 2: 4-6 hours (writing good tests takes time)
- Phase 3: 2-3 hours
- Phase 4: 8-12 hours (most complex phase)
- Phase 5: 1-2 hours
- **Total: 16-24 hours of work**

---

**Plan Version**: 1.0  
**Created**: 2026-02-16  
**Branch**: `refactor/code-quality-overhaul` (to be created)  
**Base Commit**: origin/master
