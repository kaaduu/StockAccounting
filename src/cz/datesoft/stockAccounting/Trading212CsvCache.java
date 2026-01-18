/*
 * Trading212CsvCache.java
 *
 * Local cache for Trading 212 CSV exports per account and year
 */

package cz.datesoft.stockAccounting;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages local CSV file cache for Trading 212 exports
 * Caches downloaded CSV files per account and year to enable offline import
 * and reduce API calls
 */
public class Trading212CsvCache {

    private static final Logger logger = Logger.getLogger(Trading212CsvCache.class.getName());
    private static final String CACHE_BASE_DIR = System.getProperty("user.home") + "/.trading212/csv_cache";
    private static final String METADATA_FILE = "metadata.json";

    /**
     * Check if cached CSV exists for account and year
     */
    public boolean hasCachedCsv(String accountId, int year) {
        if (accountId == null || accountId.isEmpty()) {
            return false;
        }

        Path csvPath = getCsvPath(accountId, year);
        boolean exists = Files.exists(csvPath);
        
        if (exists) {
            logger.info("Cache HIT for account=" + accountId + ", year=" + year + " at " + csvPath);
        } else {
            logger.fine("Cache MISS for account=" + accountId + ", year=" + year);
        }
        
        return exists;
    }

    /**
     * Save CSV content to cache
     */
    public void saveCsv(String accountId, int year, String csvContent) throws IOException {
        if (accountId == null || accountId.isEmpty()) {
            logger.warning("Cannot save CSV cache: accountId is null or empty");
            return;
        }

        if (csvContent == null || csvContent.isEmpty()) {
            logger.warning("Cannot save CSV cache: content is null or empty");
            return;
        }

        // Create account directory
        Path accountDir = getAccountDir(accountId);
        Files.createDirectories(accountDir);

        // Write CSV file
        Path csvPath = getCsvPath(accountId, year);
        Files.writeString(csvPath, csvContent);

        // Update metadata
        updateMetadata(accountId, year, csvContent.length());

        logger.info("Saved CSV cache for account=" + accountId + ", year=" + year + 
                   ", size=" + csvContent.length() + " bytes at " + csvPath);
    }

    /**
     * Load CSV content from cache
     */
    public String loadCsv(String accountId, int year) throws IOException {
        if (accountId == null || accountId.isEmpty()) {
            throw new IOException("Cannot load CSV cache: accountId is null or empty");
        }

        Path csvPath = getCsvPath(accountId, year);
        
        if (!Files.exists(csvPath)) {
            throw new IOException("CSV cache file not found: " + csvPath);
        }

        String content = Files.readString(csvPath);
        logger.info("Loaded CSV cache for account=" + accountId + ", year=" + year + 
                   ", size=" + content.length() + " bytes from " + csvPath);
        
        return content;
    }

    /**
     * Get cache metadata for account and year
     */
    public CacheMetadata getMetadata(String accountId, int year) {
        try {
            Map<Integer, CacheMetadata> accountMetadata = loadAccountMetadata(accountId);
            return accountMetadata.get(year);
        } catch (Exception e) {
            logger.warning("Failed to load metadata for account=" + accountId + ", year=" + year + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get all cached years for an account
     */
    public List<Integer> getCachedYears(String accountId) {
        try {
            Map<Integer, CacheMetadata> accountMetadata = loadAccountMetadata(accountId);
            return new ArrayList<>(accountMetadata.keySet());
        } catch (Exception e) {
            logger.warning("Failed to load cached years for account=" + accountId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get all cached account IDs
     */
    public List<String> getCachedAccounts() {
        List<String> accounts = new ArrayList<>();
        
        try {
            Path baseDir = Paths.get(CACHE_BASE_DIR);
            if (!Files.exists(baseDir)) {
                return accounts;
            }

            Files.list(baseDir)
                .filter(Files::isDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .forEach(accounts::add);
        } catch (IOException e) {
            logger.warning("Failed to list cached accounts: " + e.getMessage());
        }

        return accounts;
    }

    /**
     * Get total cache size for an account in bytes
     */
    public long getAccountCacheSize(String accountId) {
        long totalSize = 0;
        
        try {
            Path accountDir = getAccountDir(accountId);
            if (!Files.exists(accountDir)) {
                return 0;
            }

            totalSize = Files.walk(accountDir)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        } catch (IOException e) {
            logger.warning("Failed to calculate cache size for account=" + accountId + ": " + e.getMessage());
        }

        return totalSize;
    }

    /**
     * Get total cache size across all accounts in bytes
     */
    public long getTotalCacheSize() {
        return getCachedAccounts().stream()
            .mapToLong(this::getAccountCacheSize)
            .sum();
    }

    /**
     * Clear cache for specific year
     */
    public void clearYear(String accountId, int year) throws IOException {
        Path csvPath = getCsvPath(accountId, year);
        
        if (Files.exists(csvPath)) {
            Files.delete(csvPath);
            logger.info("Cleared cache for account=" + accountId + ", year=" + year);
        }

        // Remove from metadata
        removeFromMetadata(accountId, year);
    }

    /**
     * Clear all cache for an account
     */
    public void clearAccount(String accountId) throws IOException {
        Path accountDir = getAccountDir(accountId);
        
        if (Files.exists(accountDir)) {
            Files.walk(accountDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warning("Failed to delete: " + path + ": " + e.getMessage());
                    }
                });
            
            logger.info("Cleared all cache for account=" + accountId);
        }
    }

    /**
     * Clear all cache for all accounts
     */
    public void clearAllCache() throws IOException {
        Path baseDir = Paths.get(CACHE_BASE_DIR);
        
        if (Files.exists(baseDir)) {
            Files.walk(baseDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warning("Failed to delete: " + path + ": " + e.getMessage());
                    }
                });
            
            logger.info("Cleared all CSV cache");
        }
    }

    // ========== Private Helper Methods ==========

    private Path getAccountDir(String accountId) {
        return Paths.get(CACHE_BASE_DIR, sanitizeAccountId(accountId));
    }

    private Path getCsvPath(String accountId, int year) {
        return getAccountDir(accountId).resolve(year + ".csv");
    }

    private Path getMetadataPath(String accountId) {
        return getAccountDir(accountId).resolve(METADATA_FILE);
    }

    private String sanitizeAccountId(String accountId) {
        // Remove any characters that might be problematic in file paths
        return accountId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void updateMetadata(String accountId, int year, long size) {
        try {
            Map<Integer, CacheMetadata> metadata = loadAccountMetadata(accountId);
            
            CacheMetadata yearMetadata = new CacheMetadata();
            yearMetadata.year = year;
            yearMetadata.downloadedAt = LocalDateTime.now();
            yearMetadata.sizeBytes = size;
            
            metadata.put(year, yearMetadata);
            
            saveAccountMetadata(accountId, metadata);
        } catch (Exception e) {
            logger.warning("Failed to update metadata: " + e.getMessage());
        }
    }

    private void removeFromMetadata(String accountId, int year) {
        try {
            Map<Integer, CacheMetadata> metadata = loadAccountMetadata(accountId);
            metadata.remove(year);
            saveAccountMetadata(accountId, metadata);
        } catch (Exception e) {
            logger.warning("Failed to remove from metadata: " + e.getMessage());
        }
    }

    private Map<Integer, CacheMetadata> loadAccountMetadata(String accountId) throws IOException {
        Map<Integer, CacheMetadata> metadata = new HashMap<>();
        Path metadataPath = getMetadataPath(accountId);
        
        if (!Files.exists(metadataPath)) {
            return metadata;
        }

        try {
            String json = Files.readString(metadataPath);
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray years = root.getJSONArray("years");

            for (int i = 0; i < years.length(); i++) {
                org.json.JSONObject yearObj = years.getJSONObject(i);
                
                CacheMetadata meta = new CacheMetadata();
                meta.year = yearObj.getInt("year");
                meta.sizeBytes = yearObj.getLong("sizeBytes");
                
                if (yearObj.has("downloadedAt")) {
                    meta.downloadedAt = LocalDateTime.parse(
                        yearObj.getString("downloadedAt"),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    );
                }
                
                metadata.put(meta.year, meta);
            }
        } catch (Exception e) {
            logger.warning("Failed to parse metadata for account=" + accountId + ": " + e.getMessage());
        }

        return metadata;
    }

    private void saveAccountMetadata(String accountId, Map<Integer, CacheMetadata> metadata) throws IOException {
        Path accountDir = getAccountDir(accountId);
        Files.createDirectories(accountDir);

        org.json.JSONObject root = new org.json.JSONObject();
        org.json.JSONArray years = new org.json.JSONArray();

        for (CacheMetadata meta : metadata.values()) {
            org.json.JSONObject yearObj = new org.json.JSONObject();
            yearObj.put("year", meta.year);
            yearObj.put("sizeBytes", meta.sizeBytes);
            
            if (meta.downloadedAt != null) {
                yearObj.put("downloadedAt", meta.downloadedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            
            years.put(yearObj);
        }

        root.put("years", years);
        
        Path metadataPath = getMetadataPath(accountId);
        Files.writeString(metadataPath, root.toString(2)); // Pretty print with indent=2
    }

    /**
     * Metadata for a cached year
     */
    public static class CacheMetadata {
        public int year;
        public LocalDateTime downloadedAt;
        public long sizeBytes;

        public String getFormattedSize() {
            if (sizeBytes < 1024) {
                return sizeBytes + " B";
            } else if (sizeBytes < 1024 * 1024) {
                return String.format("%.1f KB", sizeBytes / 1024.0);
            } else {
                return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
            }
        }

        public String getFormattedDate() {
            if (downloadedAt == null) {
                return "Unknown";
            }
            return downloadedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }

        @Override
        public String toString() {
            return String.format("CacheMetadata{year=%d, size=%s, downloaded=%s}",
                year, getFormattedSize(), getFormattedDate());
        }
    }
}
