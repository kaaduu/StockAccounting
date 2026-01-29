/*
 * IBKRFlexImporter.java
 *
 * Main orchestrator for IBKR Flex Query imports
 */

package cz.datesoft.stockAccounting;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Vector;
import java.util.logging.Logger;

public class IBKRFlexImporter {

    private static final Logger logger = Logger.getLogger(IBKRFlexImporter.class.getName());
    // Legacy constants (no longer used, but keep for compatibility/migration if referenced elsewhere)
    @SuppressWarnings("unused")
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.ibkr_flex";
    @SuppressWarnings("unused")
    private static final String CACHE_FILE = CACHE_DIR + "/year_cache.json";

    private final String flexToken;
    private final String queryId;
    private final IBKRFlexClient apiClient;
    private final IBKRFlexParser parser;
    private final IBKRFlexCache yearCache;
    private java.awt.Frame parentFrame;
    private boolean forceRefresh = false;

    // AssetClass filter for ExchTrade rows (null => all)
    private java.util.Set<String> allowedAssetClasses = null;

    // Corporate actions (transformations) inclusion
    private boolean includeCorporateActions = true;

    // Trades inclusion
    private boolean includeTrades = true;

    // Cash transactions (dividends/withholding/fees) inclusion
    private boolean includeCashTransactions = true;

    public IBKRFlexImporter(String flexToken, String queryId) {
        this.flexToken = flexToken;
        this.queryId = queryId;
        this.apiClient = new IBKRFlexClient(flexToken);
        this.parser = new IBKRFlexParser();
        this.yearCache = new IBKRFlexCache();
    }

    public void setAllowedAssetClasses(java.util.Set<String> allowedAssetClasses) {
        this.allowedAssetClasses = allowedAssetClasses;
        this.parser.setAllowedAssetClasses(allowedAssetClasses);
    }

    public void setIncludeCorporateActions(boolean includeCorporateActions) {
        this.includeCorporateActions = includeCorporateActions;
        this.parser.setIncludeCorporateActions(includeCorporateActions);
    }

    public void setIncludeTrades(boolean includeTrades) {
        this.includeTrades = includeTrades;
        this.parser.setIncludeTrades(includeTrades);
    }

    public void setIncludeCashTransactions(boolean includeCashTransactions) {
        this.includeCashTransactions = includeCashTransactions;
        this.parser.setIncludeCashTransactions(includeCashTransactions);
    }

    public boolean validateCredentials() {
        if (flexToken == null || flexToken.trim().isEmpty()) {
            logger.warning("Flex token is empty");
            return false;
        }
        if (queryId == null || queryId.trim().isEmpty()) {
            logger.warning("Query ID is empty");
            return false;
        }
        return true;
    }

    public String getValidationError() {
        if (flexToken == null || flexToken.trim().isEmpty()) {
            return "Flex Token není nastaven. Zadejte Flex Token v nastavení IBKR Flex.";
        }
        if (queryId == null || queryId.trim().isEmpty()) {
            return "Query ID není nastaveno. Zadejte Query ID v nastavení IBKR Flex.";
        }
        return null;
    }

    public void setParentFrame(java.awt.Frame parentFrame) {
        this.parentFrame = parentFrame;
    }

    public void setForceRefresh(boolean forceRefresh) {
        this.forceRefresh = forceRefresh;
        logger.info("Force refresh set to: " + forceRefresh);
    }

    public ImportResult importYears(Vector<Integer> years, javax.swing.SwingWorker worker) throws Exception {
        ImportResult result = new ImportResult();
        result.yearsRequested = years;
        result.yearsImported = new Vector<>();
        result.totalTransactions = 0;

        int totalYears = years.size();
        int currentYearIndex = 0;

        try {
            for (Integer year : years) {
                if (worker != null && worker.isCancelled()) {
                    throw new Exception("Import cancelled by user");
                }

                currentYearIndex++;
                logger.info("Importing year: " + year + " (" + currentYearIndex + "/" + totalYears + ")");
                ImportYearResult yearResult = importYear(year, worker);

                result.yearsImported.add(yearResult);
                result.totalTransactions += yearResult.transactions.size();
            }

            result.success = result.yearsImported.size() == years.size();
            result.message = String.format("Importováno %d z %d let: %d transakcí",
                    result.yearsImported.size(), years.size(), result.totalTransactions);

            return result;
        } finally {
            // Always clean up resources
            cleanup();
        }
    }

    private ImportYearResult importYear(int year, javax.swing.SwingWorker worker) throws Exception {
        logger.info("Starting import for year " + year);
        int currentYear = LocalDate.now().getYear();

        if (year < 2015 || year > currentYear + 1) {
            throw new IllegalArgumentException(
                    "Year " + year + " is not valid. Valid range: 2015-" + (currentYear + 1));
        }

        logger.fine("Checking cache for year " + year);
        if (!forceRefresh && yearCache.hasCachedYear(year)) {
            logger.info("Using cached data for year " + year);
            try {
                String cachedCsv = yearCache.loadYear(year);
                Vector<Transaction> transactions = parser.parseCsvReport(cachedCsv);

                ImportYearResult result = new ImportYearResult();
                result.year = year;
                result.success = true;
                result.message = "Použit cache pro rok " + year;
                result.transactions = transactions;
                result.fromCache = true;
                result.csvData = cachedCsv;

                logger.info("Cache loaded for year " + year + ": " + transactions.size() + " transactions");
                return result;
            } catch (Exception e) {
                logger.warning("Cache load failed, downloading from API: " + e.getMessage());
            }
        }

        logger.info("Downloading data for year " + year + " from IBKR API...");
        logger.info("IMPORTANT: Date range is configured in the Flex Query template in Client Portal");
        logger.info("The API does not accept date parameters - ensure your template covers year " + year);

        logger.fine("Calling apiClient.requestAndDownloadReport");
        try {
            // Note: Per IBKR API documentation, date ranges must be configured in the 
            // Flex Query template itself (e.g., "Last Year", "Last Month", "Custom Range")
            // The API only triggers generation based on the template configuration
            String csvData = apiClient.requestAndDownloadReport(queryId, worker);

            logger.fine("Parsing CSV data");
            Vector<Transaction> transactions = parser.parseCsvReport(csvData);
            yearCache.saveYear(year, csvData);

            // Also archive into unified broker cache for debugging/reuse
            try {
                CacheManager.archiveString("ib", CacheManager.Source.API,
                    "flex_api_single_" + year, ".csv", csvData);
            } catch (Exception e) {
                // Best effort
            }

            ImportYearResult result = new ImportYearResult();
            result.year = year;
            result.success = true;
            result.message = String.format("Importováno %d transakcí z IBKR Flex za rok %d",
                        transactions.size(), year);
            result.transactions = transactions;
            result.fromCache = false;
            result.csvData = csvData;

            logger.info("Import completed for year " + year + ": " + transactions.size() + " transactions");
            return result;

        } catch (Exception e) {
            logger.warning("Import failed for year " + year + ": " + e.getMessage());
            ImportYearResult result = new ImportYearResult();
            result.year = year;
            result.success = false;
            result.message = "Import selhal: " + e.getMessage();
            result.transactions = new Vector<>();
            result.fromCache = false;

            throw e;
        }
    }

    public IBKRFlexCache getCache() {
        return yearCache;
    }

    /**
     * Clean up resources (executor service, connections, etc.)
     * Should be called when importer is no longer needed
     */
    public void cleanup() {
        if (apiClient != null) {
            logger.info("Cleaning up IBKR API client resources");
            apiClient.shutdown();
        }
    }

    public static class ImportResult {
        public Vector<Integer> yearsRequested;
        public Vector<ImportYearResult> yearsImported;
        public boolean success;
        public String message;
        public int totalTransactions;
    }

    public static class ImportYearResult {
        public int year;
        public boolean success;
        public String message;
        public Vector<Transaction> transactions;
        public boolean fromCache;
        public String csvData;
    }
}
