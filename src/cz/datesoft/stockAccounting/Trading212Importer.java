/*
 * Trading212Importer.java
 *
 * Main orchestrator for Trading 212 API imports
 */

package cz.datesoft.stockAccounting;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Month;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * Main importer class for Trading 212 API
 * Orchestrates the entire import process including state management
 */
public class Trading212Importer {

    private static final Logger logger = Logger.getLogger(Trading212Importer.class.getName());

    private final String apiKey;
    private final String apiSecret;
    private final boolean useDemo;
    private final Trading212ApiClient apiClient;
    private final Trading212DataTransformer transformer;
    private final Trading212ImportState importState;
    private final Trading212ReportCache reportCache;
    private final Trading212CsvCache csvCache;
    private java.awt.Frame parentFrame; // Parent frame for progress dialogs
    private String cachedAccountId = null; // Cached account ID from API
    private boolean forceRefresh = false; // Force download even if cache exists

    /**
     * Set the parent frame for progress dialogs
     */
    public void setParentFrame(java.awt.Frame parentFrame) {
        this.parentFrame = parentFrame;
    }

    /**
     * Create importer with API credentials
     */
    public Trading212Importer(String apiKey, String apiSecret, boolean useDemo) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.useDemo = useDemo;
        this.apiClient = new Trading212ApiClient(apiKey, apiSecret, useDemo);
        this.transformer = new Trading212DataTransformer();
        this.importState = new Trading212ImportState(); // In production, load from settings
        this.reportCache = new Trading212ReportCache();
        this.csvCache = new Trading212CsvCache();
    }
    
    /**
     * Set force refresh flag to bypass cache
     */
    public void setForceRefresh(boolean forceRefresh) {
        this.forceRefresh = forceRefresh;
        logger.info("Force refresh set to: " + forceRefresh);
    }
    
    /**
     * Get account ID (fetch from API if not cached)
     */
    public String getAccountId() throws Exception {
        if (cachedAccountId != null) {
            return cachedAccountId;
        }
        
        try {
            logger.info("Fetching account ID from Trading 212 API...");
            AccountSummary summary = apiClient.testConnection();
            cachedAccountId = summary.accountId;
            logger.info("Account ID: " + cachedAccountId);
            return cachedAccountId;
        } catch (Exception e) {
            logger.warning("Failed to fetch account ID: " + e.getMessage() + ", using fallback");
            // Fallback to generic ID based on environment
            cachedAccountId = useDemo ? "demo" : "live";
            return cachedAccountId;
        }
    }
    
    /**
     * Get CSV cache instance
     */
    public Trading212CsvCache getCsvCache() {
        return csvCache;
    }

    /**
     * Import data for a specific year
     */
    public ImportResult importYear(int year) throws Exception {
        return importYear(year, null);
    }

    /**
     * Import data for a specific year with optional worker for cancellation
     */
    public ImportResult importYear(int year, javax.swing.SwingWorker worker) throws Exception {
        // Validate year is reasonable
        int currentYear = LocalDate.now().getYear();
        if (year < 2015 || year > currentYear + 1) {
            throw new IllegalArgumentException(
                    "Year " + year + " is not valid for import. Valid range: 2015-" + (currentYear + 1));
        }

        // Don't check if already imported here - let the UI layer handle that decision
        // The UI may want to re-import or the user may have explicitly requested it

        return importYearWithRetry(year, 3, worker); // Retry up to 3 times
    }

    private ImportResult importYearWithRetry(int year, int maxRetries, javax.swing.SwingWorker worker) throws Exception {
        logger.info("Starting import for year: " + year);

        int currentYear = LocalDate.now().getYear();
        Trading212ImportState.YearImportStatus status = importState.getYearStatus(year);

        ImportResult result = new ImportResult();
        result.year = year;

        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                attempt++;

                if (year < currentYear) {
                    // Non-current year: full import
                    LocalDateTime yearStart = LocalDateTime.of(year, Month.JANUARY, 1, 0, 0);
                    LocalDateTime yearEnd = LocalDateTime.of(year, Month.DECEMBER, 31, 23, 59);

                    result.transactions = importDateRangeWithRetry(yearStart, yearEnd, maxRetries, worker);
                    result.transactionsImported = result.transactions.size();

                    // Don't update import state here - UI handles it
                    result.success = true;
                    result.message = "Úspěšně importováno " + result.transactions.size()
                            + " transakcí za rok " + year;
                } else if (year == currentYear) {
                    // Current year: always fetch full year to ensure we have everything
                    // (Previously did incremental, but for "Fetch Fresh Data" full year is
                    // safer/simpler for preview)
                    LocalDateTime yearStart = LocalDateTime.of(year, Month.JANUARY, 1, 0, 0);
                    LocalDateTime yearEnd = LocalDateTime.now();

                    result.transactions = importDateRangeWithRetry(yearStart, yearEnd, maxRetries, worker);
                    result.transactionsImported = result.transactions.size();
                    result.success = true;
                    result.message = "Úspěšně importováno " + result.transactions.size()
                            + " transakcí za rok " + year;
                } else {
                    // Future year
                    result.transactions = new Vector<>();
                    result.success = false;
                    result.message = "Nelze importovat budoucí rok: " + year;
                }

                // Success - break out of retry loop
                break;

            } catch (Exception e) {
                logger.warning("Import attempt " + attempt + " failed for year " + year + ": " + e.getMessage());

                if (attempt >= maxRetries) {
                    // All retries exhausted
                    result.transactions = new Vector<>();
                    result.success = false;
                    result.message = "Import selhal po " + maxRetries + " pokusech: " + e.getMessage();
                    logger.severe(
                            "Import selhal za rok " + year + " po " + maxRetries + " pokusech: " + e.getMessage());
                    throw e;
                } else {
                    // Wait before retry (exponential backoff)
                    int waitSeconds = (int) Math.pow(2, attempt - 1); // 1, 2, 4 seconds
                    logger.info("Retrying import for year " + year + " in " + waitSeconds + " seconds...");
                    try {
                        Thread.sleep(waitSeconds * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Import interrupted during retry", ie);
                    }
                }
            }
        }

        return result;
    }

    private Vector<Transaction> importDateRangeWithRetry(LocalDateTime fromDate, LocalDateTime toDate, int maxRetries, javax.swing.SwingWorker worker)
            throws Exception {

        // Choose import method based on date range size
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate);

        if (daysBetween > 90) {
            // Large range: use CSV reports
            return importCsvBulkImport(fromDate, toDate, worker);
        } else {
            // Small range: use API
            return importApiIncrementalImport(fromDate, toDate, maxRetries);
        }
    }

    /**
     * Import large date ranges using CSV reports (with caching support)
     */
    private Vector<Transaction> importCsvBulkImport(LocalDateTime fromDate, LocalDateTime toDate, javax.swing.SwingWorker worker)
            throws Exception {

        logger.info("Using CSV bulk import for date range: " + fromDate + " to " + toDate);

        // Get account ID for cache lookup
        String accountId = getAccountId();
        int year = fromDate.getYear();
        
        // Check if we can use cached CSV
        if (!forceRefresh && csvCache.hasCachedCsv(accountId, year)) {
            logger.info("✓ Using cached CSV for year " + year + " (account: " + accountId + ")");
            try {
                String cachedCsv = csvCache.loadCsv(accountId, year);
                Trading212CsvParser csvParser = new Trading212CsvParser();
                Vector<Transaction> transactions = csvParser.parseCsvReport(cachedCsv);
                logger.info("✓ Loaded " + transactions.size() + " transactions from cache (no API call needed)");
                return transactions;
            } catch (Exception e) {
                logger.warning("Failed to load from cache: " + e.getMessage() + ", downloading from API");
                // Fall through to API download
            }
        }
        
        // Download from API
        Trading212CsvClient csvClient = new Trading212CsvClient(apiKey, apiSecret, useDemo);
        Trading212CsvParser csvParser = new Trading212CsvParser();

        // Step 1: Request CSV report
        logger.info("Step 1/4: Requesting CSV report from Trading 212...");
        long reportId = csvClient.requestCsvReport(fromDate, toDate);

        // Step 2: Monitor progress (always prefer GUI dialog for user feedback)
        logger.info("Step 2/4: Monitoring report generation progress...");

        // Use stored parent frame (set by ImportWindow)
        // Always prefer GUI progress dialog when available for better user feedback
        Vector<Transaction> transactions;
        boolean canShowGui = (this.parentFrame != null) && !java.awt.GraphicsEnvironment.isHeadless();
        logger.info("GUI Detection: parentFrame=" + this.parentFrame + ", isHeadless=" +
                   java.awt.GraphicsEnvironment.isHeadless() + ", canShowGui=" + canShowGui);

        if (canShowGui) {
            // GUI mode: Use progress dialog (provides countdown, status updates, cancel option)
            logger.info("Using GUI progress dialog for status monitoring (recommended for user feedback)");
            CsvReportProgressDialog progressDialog = new CsvReportProgressDialog(
                    this.parentFrame, reportId, csvClient, csvParser, reportCache, worker);
            
            // Enable CSV caching in the dialog
            progressDialog.setCacheParameters(csvCache, accountId, year);
            
            transactions = progressDialog.waitForCompletion();
            logger.info("CSV bulk import completed via GUI: " + transactions.size() + " transactions imported");
        } else {
            // Headless mode: Poll status directly (fallback when no GUI available)
            logger.info("Using headless polling for status monitoring (no GUI available)");
            transactions = importCsvBulkImportHeadless(reportId, csvClient, csvParser, accountId, year);
        }

        return transactions;
    }

    /**
     * Headless CSV bulk import that polls status without GUI dialog
     */
    private Vector<Transaction> importCsvBulkImportHeadless(long reportId,
            Trading212CsvClient csvClient, Trading212CsvParser csvParser, String accountId, int year) throws Exception {

        logger.info("Starting headless CSV report monitoring for report ID: " + reportId);

        final int MAX_WAIT_MINUTES = 10; // Maximum wait time
        final int POLL_INTERVAL_SECONDS = 5; // Check every 5 seconds
        final int MAX_POLLS = (MAX_WAIT_MINUTES * 60) / POLL_INTERVAL_SECONDS;

        int pollCount = 0;
        while (pollCount < MAX_POLLS) {
            pollCount++;
            logger.info("Poll " + pollCount + "/" + MAX_POLLS + ": Checking report status...");

            try {
                // Check report status
                Trading212CsvClient.CsvReportStatus status = csvClient.checkReportStatus(reportId);
                logger.info("Report status: " + status.status.getDisplayText());

                if (status.status == Trading212CsvClient.CsvReportStatus.ReportStatus.FINISHED) {
                    // Report is ready, download and parse
                    if (status.downloadUrl == null || status.downloadUrl.isEmpty()) {
                        throw new Exception("Report finished but no download URL provided");
                    }

                    logger.info("Report finished, downloading CSV data from: " + status.downloadUrl);
                    String csvData = csvClient.downloadCsvReport(status.downloadUrl);

                    logger.info("Parsing CSV data...");
                    Vector<Transaction> transactions = csvParser.parseCsvReport(csvData);
                    
                    // Save CSV to cache for future use
                    try {
                        csvCache.saveCsv(accountId, year, csvData);
                        logger.info("✓ Saved CSV to cache for future use (account: " + accountId + ", year: " + year + ")");
                        // Also archive into unified broker cache for debugging/reuse
                        try {
                            CacheManager.archiveString("trading212", CacheManager.Source.API,
                                "api_single_" + accountId + "_" + year, ".csv", csvData);
                        } catch (Exception e) {
                            // Best effort
                        }
                    } catch (Exception cacheError) {
                        logger.warning("Failed to save CSV to cache: " + cacheError.getMessage());
                    }

                    logger.info("Headless CSV import completed successfully: " + transactions.size() + " transactions");
                    return transactions;

                } else if (status.status == Trading212CsvClient.CsvReportStatus.ReportStatus.FAILED) {
                    throw new Exception("CSV report generation failed");
                } else if (status.status == Trading212CsvClient.CsvReportStatus.ReportStatus.CANCELED) {
                    throw new Exception("CSV report generation was canceled");
                }

                // Still processing, wait before next poll
                logger.info("Report still processing (" + status.status.getDisplayText() +
                           "), waiting " + POLL_INTERVAL_SECONDS + " seconds before next check...");
                Thread.sleep(POLL_INTERVAL_SECONDS * 1000);

            } catch (Exception e) {
                logger.warning("Chyba během kontroly stavu " + pollCount + ": " + e.getMessage());
                if (pollCount >= MAX_POLLS) {
                    throw new Exception("CSV report monitoring failed after " + MAX_POLLS + " attempts: " + e.getMessage(), e);
                }
                // Continue polling on error
                Thread.sleep(POLL_INTERVAL_SECONDS * 1000);
            }
        }

        throw new Exception("CSV report generation timed out after " + MAX_WAIT_MINUTES + " minutes");
    }

    /**
     * Find the parent frame for dialogs (walks up component hierarchy)
     */
    private java.awt.Frame findParentFrame() {
        // This method would need access to a component in the UI hierarchy
        // For now, return null and let the dialog handle it
        return null;
    }

    /**
     * Import small date ranges using API
     */
    private Vector<Transaction> importApiIncrementalImport(LocalDateTime fromDate, LocalDateTime toDate, int maxRetries)
            throws Exception {

        logger.info("Using API incremental import for date range: " + fromDate + " to " + toDate);

        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                attempt++;
                return importDateRange(fromDate, toDate);
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    throw e;
                }

                int waitSeconds = (int) Math.pow(2, attempt - 1);
                logger.info("Retrying API import in " + waitSeconds + " seconds...");
                try {
                    Thread.sleep(waitSeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Import interrupted during retry", ie);
                }
            }
        }
        return new Vector<>(); // Should never reach here
    }

    /**
     * Import data for a specific date range using API
     */
    private Vector<Transaction> importDateRange(LocalDateTime fromDate, LocalDateTime toDate)
            throws Exception {

        logger.info("Importing date range using API: " + fromDate + " to " + toDate);

        // Fetch data from API (no date filtering - all orders)
        String apiResponse = apiClient.fetchHistoricalOrders();

        // Transform to transactions with client-side date filtering
        Vector<Transaction> transactions = transformer.transformOrdersResponse(apiResponse, fromDate, toDate);

        logger.info("Úspěšně importováno " + transactions.size() + " transakcí z rozsahu dat");

        return transactions;
    }

    /**
     * Get import status for all years
     */
    public Trading212ImportState getImportState() {
        return importState;
    }

    /**
     * Result of an import operation
     */
    public static class ImportResult {
        public int year;
        public boolean success;
        public String message;
        public int transactionsImported;
        public Vector<Transaction> transactions;

        // Default constructor
        public ImportResult() {
        }

        // Convenience constructor
        public ImportResult(int year, boolean success, String message, int transactionsImported,
                Vector<Transaction> transactions) {
            this.year = year;
            this.success = success;
            this.message = message;
            this.transactionsImported = transactionsImported;
            this.transactions = transactions;
        }

        @Override
        public String toString() {
            return "ImportResult{" +
                    "year=" + year +
                    ", success=" + success +
                    ", message='" + message + '\'' +
                    ", transactionsImported=" + transactionsImported +
                    ", transactions=" + (transactions != null ? transactions.size() : 0) + " items" +
                    '}';
        }
    }
}
