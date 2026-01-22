/*
 * IBKRFlexClient.java
 *
 * HTTP client for IBKR Flex Web Service
 * Handles two-step async workflow: request report -> poll status -> download
 */

package cz.datesoft.stockAccounting;

import java.net.URI;
import java.net.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.io.IOException;

/**
 * HTTP client for IBKR Flex Web Service
 * Handles two-step async workflow: request report -> poll status -> download
 */
public class IBKRFlexClient {

    private static final Logger logger = Logger.getLogger(IBKRFlexClient.class.getName());
    private static final String BASE_URL = "https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService";
    private static final int MAX_WAIT_MINUTES = 10;
    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;

    private final HttpClient httpClient;
    private final String flexToken;
    private final ExecutorService executorService;  // Used for polling loop cancellation only

    public IBKRFlexClient(String flexToken) {
        this.flexToken = flexToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.executorService = Executors.newCachedThreadPool();
        logger.info("Initialized IBKR Flex Web Service client with timeout: " + CONNECT_TIMEOUT_SECONDS + "s connect, " + READ_TIMEOUT_SECONDS + "s read, " + REQUEST_TIMEOUT_SECONDS + "s request");
    }

    /**
     * Shuts down the executor service and releases resources.
     * Should be called when the client is no longer needed.
     */
    public void shutdown() {
        try {
            logger.info("Shutting down IBKRFlexClient executor service");
            executorService.shutdown();
            // Wait up to 5 seconds for clean shutdown
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Executor service did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warning("Interrupted during shutdown, forcing shutdown");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute HTTP request operation with timeout handling.
     * Note: HttpClient already has built-in timeout, this just provides consistent error handling.
     */
    private <T> T executeWithTimeout(Callable<T> callable, int timeoutSeconds, String operationName) throws Exception {
        logger.fine("Starting " + operationName + " with " + timeoutSeconds + "s timeout");

        try {
            // HttpClient has built-in timeout configured, so we just execute directly
            return callable.call();
        } catch (java.net.http.HttpConnectTimeoutException e) {
            throw new Exception(operationName + " connection timed out - check network connectivity");
        } catch (java.net.http.HttpTimeoutException e) {
            throw new Exception(operationName + " timed out after " + timeoutSeconds + " seconds - IBKR API may be slow or unreachable");
        } catch (Exception e) {
            throw new Exception(operationName + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Request generation of a Flex Query report.
     * 
     * IMPORTANT: Date ranges must be configured in the Flex Query template in Client Portal.
     * The API does NOT accept date parameters - it generates reports based on the template configuration.
     * 
     * Testing confirmed that fromDate/toDate parameters are ignored by the IBKR API.
     * To import specific date ranges, create separate Flex Query templates in IBKR Client Portal
     * with different period configurations (e.g., "Year to Date", "Last Year", custom ranges).
     * 
     * @param queryId The Flex Query ID from Client Portal
     * @return FlexRequestResult containing reference code or error information
     */
    public FlexRequestResult requestReport(String queryId)
            throws Exception {

        String url = BASE_URL + "/SendRequest";
        
        // Per IBKR API documentation: only t (token), q (queryId), and v (version) are supported
        String params = String.format(
            "?t=%s&q=%s&v=3",
            URLEncoder.encode(flexToken, StandardCharsets.UTF_8),
            URLEncoder.encode(queryId, StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url + params))
            .header("User-Agent", "StockAccounting/1.0")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .GET()
            .build();

        logger.info("Requesting Flex report for query: " + queryId);

        return executeWithTimeout(() -> {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Flex request failed with status " + response.statusCode() +
                        ": " + response.body());
            }

            return parseReferenceCode(response.body());
        }, REQUEST_TIMEOUT_SECONDS, "requestReport");
    }

    private FlexRequestResult parseReferenceCode(String responseBody) {
        // First check for failure status per IBKR API documentation
        if (responseBody.contains("<Status>Fail</Status>")) {
            FlexRequestResult errorResult = new FlexRequestResult();
            errorResult.success = false;
            
            // Extract error code
            if (responseBody.contains("<ErrorCode>")) {
                int start = responseBody.indexOf("<ErrorCode>") + 11;
                int end = responseBody.indexOf("</ErrorCode>", start);
                if (end > start) {
                    errorResult.errorCode = responseBody.substring(start, end).trim();
                }
            }
            
            // Extract error message
            if (responseBody.contains("<ErrorMessage>")) {
                int start = responseBody.indexOf("<ErrorMessage>") + 14;
                int end = responseBody.indexOf("</ErrorMessage>", start);
                if (end > start) {
                    errorResult.errorMessage = responseBody.substring(start, end).trim();
                }
            }
            
            logger.warning("Flex request failed: [" + errorResult.errorCode + "] " + errorResult.errorMessage);
            logger.warning("Full response: " + responseBody);
            return errorResult;
        }

        // Check for success status
        if (!responseBody.contains("<Status>Success</Status>")) {
            logger.warning("Unexpected response format (no Success or Fail status): " + responseBody);
            FlexRequestResult errorResult = new FlexRequestResult();
            errorResult.success = false;
            errorResult.errorMessage = "Unexpected response format";
            return errorResult;
        }

        // Extract reference code
        String referenceCode = null;
        if (responseBody.contains("<ReferenceCode>") && responseBody.contains("</ReferenceCode>")) {
            int startIdx = responseBody.indexOf("<ReferenceCode>") + 15;
            int endIdx = responseBody.indexOf("</ReferenceCode>", startIdx);
            if (endIdx > startIdx) {
                referenceCode = responseBody.substring(startIdx, endIdx).trim();
            }
        }

        if (referenceCode == null || referenceCode.isEmpty()) {
            logger.warning("Failed to parse Reference Code from successful response: " + responseBody);
            FlexRequestResult errorResult = new FlexRequestResult();
            errorResult.success = false;
            errorResult.errorMessage = "No reference code in response";
            return errorResult;
        }

        // Clean reference code - remove any leading > or whitespace
        referenceCode = referenceCode.replaceAll("^[>\\s]+", "").trim();

        // Validate cleaned reference code
        if (referenceCode.isEmpty() || !referenceCode.matches("^[a-zA-Z0-9]+$")) {
            logger.warning("Invalid Reference Code after cleaning: " + referenceCode);
            FlexRequestResult errorResult = new FlexRequestResult();
            errorResult.success = false;
            errorResult.errorMessage = "Invalid reference code format";
            return errorResult;
        }

        logger.info("Received Reference Code: " + referenceCode);
        FlexRequestResult result = new FlexRequestResult();
        result.referenceCode = referenceCode;
        result.success = true;
        return result;
    }

    /**
     * Custom exception for when report is still being generated (error code 1019)
     */
    public static class ReportNotReadyException extends Exception {
        public ReportNotReadyException(String message) {
            super(message);
        }
    }

    /**
     * Downloads a Flex report using the /GetStatement endpoint.
     * Per IBKR API: This single endpoint both checks status AND retrieves the report.
     * Returns CSV content if ready, throws ReportNotReadyException if still generating.
     */
    public String downloadReport(String referenceCode)
            throws Exception {

        // Per IBKR API docs: /GetStatement with params t (token), q (referenceCode), v (version)
        String url = BASE_URL + "/GetStatement";
        String params = String.format(
            "?t=%s&q=%s&v=3",
            URLEncoder.encode(flexToken, StandardCharsets.UTF_8),
            URLEncoder.encode(referenceCode, StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url + params))
            .header("User-Agent", "StockAccounting/1.0")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .GET()
            .build();

        logger.fine("Calling /GetStatement for reference: " + referenceCode);

        return executeWithTimeout(() -> {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("GetStatement failed with HTTP status " + response.statusCode());
            }

            String body = response.body();

            // Check if response is an error (XML format)
            if (body.contains("<Status>Fail</Status>")) {
                String errorCode = extractXmlTag(body, "ErrorCode");
                String errorMessage = extractXmlTag(body, "ErrorMessage");
                
                logger.fine("GetStatement returned error: [" + errorCode + "] " + errorMessage);
                
                // Error 1019 means report is still being generated - should retry
                if ("1019".equals(errorCode)) {
                    throw new ReportNotReadyException("Report generation in progress");
                }
                
                // Error 1018 means rate limited
                if ("1018".equals(errorCode)) {
                    throw new Exception("Rate limit exceeded: " + errorMessage);
                }
                
                // Any other error is fatal
                throw new Exception("Flex report error [" + errorCode + "]: " + errorMessage);
            }

            // Success - body contains CSV content
            logger.info("Report downloaded successfully, size: " + body.length() + " bytes");
            return body;
        }, REQUEST_TIMEOUT_SECONDS, "downloadReport");
    }

    /**
     * Helper to extract content from simple XML tags
     */
    private String extractXmlTag(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        int end = xml.indexOf(closeTag);
        if (start >= 0 && end > start) {
            return xml.substring(start + openTag.length(), end).trim();
        }
        return "";
    }

    /**
     * Complete workflow to request and download a Flex report.
     * Implements the correct IBKR Flex Web Service workflow:
     * 1. Call /SendRequest to trigger report generation
     * 2. Wait initial delay (per documentation recommendation)
     * 3. Poll /GetStatement until report is ready or timeout
     * 
     * Note: Date ranges must be configured in the Flex Query template in Client Portal.
     */
    public String requestAndDownloadReport(String queryId, javax.swing.SwingWorker worker) throws Exception {

        logger.info("=== Starting IBKR Flex report request ===");
        logger.info("Query ID: " + queryId);
        logger.info("NOTE: Date range is determined by Flex Query template configuration in Client Portal");

        // Step 1: Request report generation
        logger.info("Step 1: Requesting report generation via /SendRequest");
        FlexRequestResult requestResult = requestReport(queryId);
        
        if (!requestResult.success) {
            String errorMsg = "Failed to request Flex report";
            if (requestResult.errorCode != null) {
                errorMsg += " [" + requestResult.errorCode + "]: " + requestResult.errorMessage;
            }
            throw new Exception(errorMsg);
        }

        logger.info("Report generation initiated. Reference Code: " + requestResult.referenceCode);

        // Step 2: Initial wait (per IBKR documentation example: 20 seconds)
        int initialWaitSeconds = 20;
        logger.info("Step 2: Waiting " + initialWaitSeconds + " seconds for report generation to complete...");
        try {
            Thread.sleep(initialWaitSeconds * 1000);
        } catch (InterruptedException e) {
            if (worker != null && worker.isCancelled()) {
                throw new Exception("Import cancelled by user");
            }
            Thread.currentThread().interrupt();
        }

        // Step 3: Poll /GetStatement until ready
        logger.info("Step 3: Polling /GetStatement for completed report");
        int maxRetries = 60;  // 60 retries * 10s = 10 minutes max
        int retryDelaySeconds = 10;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (worker != null && worker.isCancelled()) {
                throw new Exception("Import cancelled by user");
            }

            logger.info("Attempt " + attempt + "/" + maxRetries + ": Calling /GetStatement...");

            try {
                // downloadReport throws ReportNotReadyException (error 1019) if still generating
                String csvData = downloadReport(requestResult.referenceCode);
                logger.info("Report successfully retrieved after " + attempt + " attempts");
                return csvData;
                
            } catch (ReportNotReadyException e) {
                // Error 1019: Report still being generated - this is expected
                logger.info("Report not ready yet (attempt " + attempt + "/" + maxRetries + "), waiting " + retryDelaySeconds + "s...");
                
                if (attempt >= maxRetries) {
                    throw new Exception("Report generation timed out after " + MAX_WAIT_MINUTES + " minutes");
                }
                
                try {
                    Thread.sleep(retryDelaySeconds * 1000);
                } catch (InterruptedException ie) {
                    if (worker != null && worker.isCancelled()) {
                        throw new Exception("Import cancelled by user");
                    }
                    Thread.currentThread().interrupt();
                }
            }
            // Other exceptions (rate limit, errors, etc.) will propagate up
        }

        throw new Exception("Report generation timed out after " + MAX_WAIT_MINUTES + " minutes");
    }

    public static class FlexRequestResult {
        public boolean success;
        public String referenceCode;
        public String errorCode;      // IBKR error code (1001-1021)
        public String errorMessage;   // Human-readable error description
    }
}
