/*
 * Trading212ImportState.java
 *
 * Manages import state for Trading 212 API imports per year
 */

package cz.datesoft.stockAccounting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks import state for Trading 212 API imports
 * Persists which years have been imported and when
 */
public class Trading212ImportState {

    // Import status for each year
    private Map<Integer, YearImportStatus> yearStatuses;

    public Trading212ImportState() {
        this.yearStatuses = new HashMap<>();
        loadFromSettings();
    }

    /**
     * Get import status for a specific year
     */
    public YearImportStatus getYearStatus(int year) {
        return yearStatuses.computeIfAbsent(year, k -> new YearImportStatus());
    }

    /**
     * Check if a year has been fully imported
     */
    public boolean isYearFullyImported(int year) {
        YearImportStatus status = yearStatuses.get(year);
        return status != null && status.fullyImported;
    }

    /**
     * Get the last import date for a year
     */
    public LocalDateTime getLastImportDate(int year) {
        YearImportStatus status = yearStatuses.get(year);
        return status != null ? status.lastImportDate : null;
    }

    /**
     * Load import state from settings
     */
    public void loadFromSettings() {
        try {
            String stateJson = Settings.getTrading212ImportState();
            if (stateJson != null && !stateJson.trim().isEmpty()) {
                org.json.JSONObject json = new org.json.JSONObject(stateJson);
                org.json.JSONArray years = json.getJSONArray("years");

                for (int i = 0; i < years.length(); i++) {
                    org.json.JSONObject yearObj = years.getJSONObject(i);
                    int year = yearObj.getInt("year");
                    boolean fullyImported = yearObj.getBoolean("fullyImported");
                    int recordsImported = yearObj.optInt("recordsImported", 0);

                    YearImportStatus status = new YearImportStatus();
                    status.fullyImported = fullyImported;
                    status.recordsImported = recordsImported;

                    if (yearObj.has("lastImportDate")) {
                        String dateStr = yearObj.getString("lastImportDate");
                        status.lastImportDate = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }

                    yearStatuses.put(year, status);
                }
            }
        } catch (Exception e) {
            // If loading fails, start with empty state
            System.err.println("Failed to load Trading 212 import state: " + e.getMessage());
        }
    }

    /**
     * Save import state to settings
     */
    public void saveToSettings() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            org.json.JSONArray years = new org.json.JSONArray();

            for (Map.Entry<Integer, YearImportStatus> entry : yearStatuses.entrySet()) {
                org.json.JSONObject yearObj = new org.json.JSONObject();
                yearObj.put("year", entry.getKey());
                yearObj.put("fullyImported", entry.getValue().fullyImported);
                yearObj.put("recordsImported", entry.getValue().recordsImported);

                if (entry.getValue().lastImportDate != null) {
                    yearObj.put("lastImportDate",
                            entry.getValue().lastImportDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                years.put(yearObj);
            }

            json.put("years", years);
            Settings.setTrading212ImportState(json.toString());
        } catch (Exception e) {
            System.err.println("Failed to save Trading 212 import state: " + e.getMessage());
        }
    }

    /**
     * Mark a year as fully imported
     */
    public void markYearFullyImported(int year, LocalDateTime importDate) {
        YearImportStatus status = getYearStatus(year);
        status.fullyImported = true;
        status.lastImportDate = importDate;
        status.recordsImported = 0; // Reset count for fully imported years
        saveToSettings();
    }

    /**
     * Update last import date for current year (incremental imports)
     */
    public void updateLastImportDate(int year, LocalDateTime importDate, int recordsImported) {
        YearImportStatus status = getYearStatus(year);
        status.lastImportDate = importDate;
        status.recordsImported += recordsImported;
        saveToSettings();
    }

    /**
     * Get all year statuses
     */
    public Map<Integer, YearImportStatus> getAllYearStatuses() {
        return new HashMap<>(yearStatuses);
    }

    /**
     * Cache transactions for a year (session-only, not persisted)
     */
    public void cacheTransactions(int year, java.util.Vector<Transaction> transactions) {
        YearImportStatus status = getYearStatus(year);
        status.cachedTransactions = new java.util.Vector<>(transactions);
    }

    /**
     * Get cached transactions for a year
     */
    public java.util.Vector<Transaction> getCachedTransactions(int year) {
        YearImportStatus status = yearStatuses.get(year);
        return status != null ? status.cachedTransactions : null;
    }

    /**
     * Check if year has cached transactions
     */
    public boolean hasCachedTransactions(int year) {
        YearImportStatus status = yearStatuses.get(year);
        return status != null && status.cachedTransactions != null && !status.cachedTransactions.isEmpty();
    }

    /**
     * Clear cached transactions for a year
     */
    public void clearCache(int year) {
        YearImportStatus status = yearStatuses.get(year);
        if (status != null) {
            status.cachedTransactions = null;
        }
    }

    /**
     * Clear all cached transactions
     */
    public void clearAllCaches() {
        for (YearImportStatus status : yearStatuses.values()) {
            status.cachedTransactions = null;
        }
    }

    /**
     * Import status for a specific year
     */
    public static class YearImportStatus {
        public boolean fullyImported = false;
        public LocalDateTime lastImportDate = null;
        public int recordsImported = 0;

        // Session-only cache (not persisted to settings)
        public transient java.util.Vector<Transaction> cachedTransactions = null;

        @Override
        public String toString() {
            return String.format("YearImportStatus{fullyImported=%s, lastImportDate=%s, recordsImported=%d, cached=%s}",
                    fullyImported, lastImportDate, recordsImported,
                    (cachedTransactions != null ? cachedTransactions.size() + " transactions" : "none"));
        }
    }

    @Override
    public String toString() {
        return "Trading212ImportState{" +
                "yearStatuses=" + yearStatuses +
                '}';
    }
}