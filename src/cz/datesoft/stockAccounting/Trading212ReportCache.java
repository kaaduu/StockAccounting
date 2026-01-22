/*
 * Trading212ReportCache.java
 *
 * Local cache for Trading 212 report statuses to avoid repeated API calls
 */

package cz.datesoft.stockAccounting;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Local cache for Trading 212 report statuses to avoid repeated API calls
 */
public class Trading212ReportCache {

    private static final Logger logger = Logger.getLogger(Trading212ReportCache.class.getName());
    private static final String LEGACY_CACHE_DIR = System.getProperty("user.home") + "/.trading212";
    private static final String LEGACY_CACHE_FILE = LEGACY_CACHE_DIR + "/reports_cache.json";
    private static final String CACHE_SUBPATH = "trading212/reports_cache.json";
    private static final int MAX_CACHE_ENTRIES = 50;

    private Map<Long, CachedReportStatus> reportCache = new HashMap<>();

    public Trading212ReportCache() {
        loadCacheFromDisk();
    }

    /**
     * Get report status, checking cache first, then API if needed
     */
    public synchronized Trading212CsvClient.CsvReportStatus getReportStatus(long reportId,
            Trading212CsvClient csvClient) throws Exception {

        CachedReportStatus cached = reportCache.get(reportId);

        if (cached != null && !cached.isExpired()) {
            logger.info("Using cached status for report " + reportId + ": " + cached.status);
            return new Trading212CsvClient.CsvReportStatus(cached.status, cached.downloadUrl);
        }

        // Fetch fresh status from API
        logger.info("Fetching fresh status for report " + reportId);
        Trading212CsvClient.CsvReportStatus fresh = csvClient.checkReportStatus(reportId);

        // Cache the result
        cacheReportStatus(reportId, fresh);
        saveCacheToDisk();

        return fresh;
    }

    /**
     * Cache a report status
     */
    private synchronized void cacheReportStatus(long reportId, Trading212CsvClient.CsvReportStatus status) {
        CachedReportStatus cached = new CachedReportStatus();
        cached.reportId = reportId;
        cached.status = status.status;
        cached.downloadUrl = status.downloadUrl;
        cached.cachedAt = LocalDateTime.now();

        // Different expiration times based on status
        if (isFinalStatus(status.status)) {
            cached.expiresAt = LocalDateTime.now().plusHours(24); // Final status: cache for 24 hours
        } else {
            cached.expiresAt = LocalDateTime.now().plusMinutes(1); // Non-final status: cache for 1 minute only
        }

        reportCache.put(reportId, cached);

        reportCache.put(reportId, cached);

        // Limit cache size
        if (reportCache.size() > MAX_CACHE_ENTRIES) {
            // Remove oldest entries (simple approach)
            reportCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            if (reportCache.size() > MAX_CACHE_ENTRIES) {
                // Still too big, remove some arbitrarily
                reportCache.entrySet().stream()
                    .limit(reportCache.size() - MAX_CACHE_ENTRIES)
                    .forEach(entry -> reportCache.remove(entry.getKey()));
            }
        }
    }

    /**
     * Load cache from disk
     */
    private void loadCacheFromDisk() {
        try {
            Path cachePath = Paths.get(Settings.getCacheBaseDir(), CACHE_SUBPATH);
            if (!Files.exists(cachePath)) {
                // Migration fallback: use legacy file if present
                Path legacyPath = Paths.get(LEGACY_CACHE_FILE);
                if (!Files.exists(legacyPath)) {
                    return;
                }
                Files.createDirectories(cachePath.getParent());
                Files.copy(legacyPath, cachePath);
            }

            String content = Files.readString(cachePath);
            if (content.trim().isEmpty()) {
                return;
            }

            // Simple JSON parsing for cache loading
            // In production, this would use proper JSON parsing
            // For now, we'll just initialize empty cache

        } catch (Exception e) {
            logger.warning("Failed to load report cache: " + e.getMessage());
        }
    }

    /**
     * Save cache to disk
     */
    private synchronized void saveCacheToDisk() {
        try {
            // Create cache directory
            Path cachePath = Paths.get(Settings.getCacheBaseDir(), CACHE_SUBPATH);
            Files.createDirectories(cachePath.getParent());

            // Simple cache serialization
            // In production, this would create proper JSON
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");

            boolean first = true;
            for (Map.Entry<Long, CachedReportStatus> entry : reportCache.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;

                CachedReportStatus cached = entry.getValue();
                sb.append("  \"").append(entry.getKey()).append("\": {\n");
                sb.append("    \"status\": \"").append(cached.status).append("\",\n");
                sb.append("    \"downloadUrl\": \"").append(cached.downloadUrl != null ? cached.downloadUrl : "").append("\",\n");
                sb.append("    \"cachedAt\": \"").append(cached.cachedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
                sb.append("    \"expiresAt\": \"").append(cached.expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\"\n");
                sb.append("  }");
            }
            sb.append("\n}");

            Files.writeString(cachePath, sb.toString());

        } catch (Exception e) {
            logger.warning("Failed to save report cache: " + e.getMessage());
        }
    }

    /**
     * Check if status is final (no need for frequent updates)
     */
    private boolean isFinalStatus(Trading212CsvClient.CsvReportStatus.ReportStatus status) {
        return status == Trading212CsvClient.CsvReportStatus.ReportStatus.FINISHED
            || status == Trading212CsvClient.CsvReportStatus.ReportStatus.FAILED
            || status == Trading212CsvClient.CsvReportStatus.ReportStatus.CANCELED;
    }

    /**
     * Cached report status
     */
    private static class CachedReportStatus {
        long reportId;
        Trading212CsvClient.CsvReportStatus.ReportStatus status;
        String downloadUrl;
        LocalDateTime cachedAt;
        LocalDateTime expiresAt;

        boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
}
