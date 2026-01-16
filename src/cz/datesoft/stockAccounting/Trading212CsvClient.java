/*
 * Trading212CsvClient.java
 *
 * Client for Trading 212 CSV report functionality
 * Handles requesting, polling, and downloading CSV reports
 */

package cz.datesoft.stockAccounting;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Client for Trading 212 CSV report generation and download
 */
public class Trading212CsvClient {

    private static final Logger logger = Logger.getLogger(Trading212CsvClient.class.getName());

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authHeader;

    // CSV report rate limiting: 1 request per 30 seconds
    private static final int CSV_RATE_LIMIT_REQUESTS = 1;
    private static final int CSV_RATE_LIMIT_WINDOW_SECONDS = 30;
    private long lastCsvRequestTime = 0;

    // Status check rate limiting: 1 request per minute (65s for safety)
    private long lastStatusCheckTime = 0;

    /**
     * Create CSV client
     */
    public Trading212CsvClient(String apiKey, String apiSecret, boolean useDemo) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = useDemo
            ? "https://demo.trading212.com/api/v0"
            : "https://live.trading212.com/api/v0";

        // Create Basic Auth header
        String credentials = apiKey + ":" + apiSecret;
        this.authHeader = "Basic " + java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes());

        logger.info("Initialized Trading212 CSV client for " +
            (useDemo ? "demo" : "live") + " environment");
    }

    /**
     * Request a CSV report for the specified date range
     */
    public long requestCsvReport(LocalDateTime fromDate, LocalDateTime toDate)
        throws IOException, InterruptedException {

        enforceCsvRateLimit();

        String url = baseUrl + "/equity/history/exports";

        // Create request body
        String requestBody = createCsvRequestBody(fromDate, toDate);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        logger.info("Requesting CSV report from: " + fromDate + " to " + toDate);

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("CSV report request failed with status " + response.statusCode() +
                ": " + response.body());
        }

        // Parse report ID from response
        long reportId = parseReportId(response.body());
        logger.info("CSV report requested successfully, ID: " + reportId);

        return reportId;
    }

    /**
     * Check the status of a CSV report
     */
    public CsvReportStatus checkReportStatus(long reportId)
        throws IOException, InterruptedException {

        enforceStatusRateLimit();

        String url = baseUrl + "/equity/history/exports";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body();

        // Always save response for debugging
        Trading212DebugStorage.storeApiResponse(responseBody, (int) reportId, "status_response");

        if (response.statusCode() != 200) {
            if (response.statusCode() == 429) {
                throw new IOException("Rate limit exceeded. Status checks are limited to 1 per minute.");
            }
            throw new IOException("Status check failed with HTTP " + response.statusCode() + ": " + responseBody);
        }

        return parseReportStatus(responseBody, reportId);
    }

    /**
     * Download completed CSV report
     */
    public String downloadCsvReport(String downloadUrl)
        throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .GET()
            .build();

        logger.info("Downloading CSV report from: " + downloadUrl);

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("CSV download failed with status " + response.statusCode());
        }

        String csvContent = response.body();
        logger.info("Downloaded CSV report, size: " + csvContent.length() + " characters");

        return csvContent;
    }

    /**
     * Create JSON request body for CSV report
     */
    private String createCsvRequestBody(LocalDateTime fromDate, LocalDateTime toDate) {
        // Validate inputs
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("Date parameters cannot be null");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        // Check date range doesn't exceed reasonable limits (prevent API rejection)
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate);
        if (daysBetween > 365 * 2) { // Max 2 years to be safe
            throw new IllegalArgumentException("Date range cannot exceed 2 years. Please select a shorter range.");
        }

        // Format as ISO 8601 with UTC timezone (always use 'Z' suffix)
        String timeFrom = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
        String timeTo = toDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";

        return String.format(
            "{\"timeFrom\":\"%s\",\"timeTo\":\"%s\",\"dataIncluded\":{\"includeOrders\":true,\"includeTransactions\":true,\"includeDividends\":true,\"includeInterest\":true}}",
            timeFrom, timeTo
        );
    }

    /**
     * Parse report ID from request response
     */
    private long parseReportId(String responseBody) throws IOException {
        // Simple JSON parsing for {"reportId": 12345}
        int idStart = responseBody.indexOf("\"reportId\":");
        if (idStart == -1) {
            throw new IOException("Invalid response: reportId not found");
        }

        int valueStart = responseBody.indexOf(':', idStart) + 1;
        int valueEnd = responseBody.indexOf('}', valueStart);

        if (valueEnd == -1) valueEnd = responseBody.length();

        String idStr = responseBody.substring(valueStart, valueEnd).trim();
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid reportId format: " + idStr);
        }
    }

    /**
     * Parse report status from status response using proper JSON parsing
     */
    private CsvReportStatus parseReportStatus(String responseBody, long targetReportId)
        throws IOException {

        try {
            org.json.JSONArray reports = new org.json.JSONArray(responseBody);

            // Find our report by ID
            for (int i = 0; i < reports.length(); i++) {
                org.json.JSONObject report = reports.getJSONObject(i);
                long reportId = report.getLong("reportId");

                if (reportId == targetReportId) {
                    return parseReportObject(report);
                }
            }

            throw new IOException("Report " + targetReportId + " not found in status response");

        } catch (org.json.JSONException e) {
            // Fallback: save the problematic response for debugging
            Trading212DebugStorage.storeApiResponse(responseBody, (int) targetReportId, "status_parse_error");
            throw new IOException("Failed to parse status response JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parse status from a JSON report object
     */
    private CsvReportStatus parseReportObject(org.json.JSONObject report) {
        CsvReportStatus status = new CsvReportStatus();

        String statusStr = report.getString("status");
        switch (statusStr) {
            case "Queued":
                status.status = CsvReportStatus.ReportStatus.QUEUED;
                break;
            case "Processing":
                status.status = CsvReportStatus.ReportStatus.PROCESSING;
                break;
            case "Running":
                status.status = CsvReportStatus.ReportStatus.RUNNING;
                break;
            case "Finished":
                status.status = CsvReportStatus.ReportStatus.FINISHED;
                status.downloadUrl = report.optString("downloadLink", null);
                break;
            case "Failed":
                status.status = CsvReportStatus.ReportStatus.FAILED;
                break;
            case "Canceled":
                status.status = CsvReportStatus.ReportStatus.CANCELED;
                break;
            default:
                logger.warning("Unknown report status: " + statusStr);
                status.status = CsvReportStatus.ReportStatus.FAILED;
        }

        return status;
    }

    /**
     * Enforce CSV report rate limiting (1 request per 30 seconds)
     */
    private void enforceCsvRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastCsvRequestTime;

        if (timeSinceLastRequest < CSV_RATE_LIMIT_WINDOW_SECONDS * 1000) {
            long waitTime = (CSV_RATE_LIMIT_WINDOW_SECONDS * 1000) - timeSinceLastRequest;
            logger.info("CSV rate limit: waiting " + waitTime + "ms");
            Thread.sleep(waitTime);
        }

        lastCsvRequestTime = System.currentTimeMillis();
    }

    /**
     * Enforce status check rate limiting (1 request per minute)
     */
    private void enforceStatusRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastStatusCheck = currentTime - lastStatusCheckTime;

        if (timeSinceLastStatusCheck < 65000) { // 65 seconds for safety margin
            long waitTime = 65000 - timeSinceLastStatusCheck;
            logger.info("Status check rate limit: waiting " + waitTime + "ms");
            Thread.sleep(waitTime);
        }

        lastStatusCheckTime = System.currentTimeMillis();
    }

    /**
     * Status of a CSV report
     */
    public static class CsvReportStatus {
        public enum ReportStatus {
            QUEUED("Ve frontě - požadavek na report přijat, čeká se na zahájení zpracování"),
            PROCESSING("Zpracovává se - generování reportu zahájeno, analyzují se vaše data"),
            RUNNING("Běží - generování reportu probíhá"),
            FINISHED("Dokončeno - report připraven ke stažení"),
            FAILED("Selhalo - generování reportu selhalo"),
            CANCELED("Canceled - Report generation canceled");

            private final String displayText;

            ReportStatus(String displayText) {
                this.displayText = displayText;
            }

            public String getDisplayText() {
                return displayText;
            }
        }

        public ReportStatus status;
        public String downloadUrl;

        public CsvReportStatus() {}

        public CsvReportStatus(ReportStatus status, String downloadUrl) {
            this.status = status;
            this.downloadUrl = downloadUrl;
        }
    }
}