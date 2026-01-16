/*
 * Trading212ApiClient.java
 *
 * HTTP client for Trading 212 API communication
 */

package cz.datesoft.stockAccounting;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Client for Trading 212 API communication
 */
public class Trading212ApiClient {

    private static final Logger logger = Logger.getLogger(Trading212ApiClient.class.getName());

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authHeader;

    // Rate limiting: 6 requests per minute
    private static final int RATE_LIMIT_REQUESTS = 6;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;
    private long lastRequestTime = 0;
    private int requestsInWindow = 0;

    /**
     * Create API client
     */
    public Trading212ApiClient(String apiKey, String apiSecret, boolean useDemo) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = useDemo
            ? "https://demo.trading212.com/api/v0"
            : "https://live.trading212.com/api/v0";

        // Create Basic Auth header
        String credentials = apiKey + ":" + apiSecret;
        this.authHeader = "Basic " + Base64.getEncoder()
            .encodeToString(credentials.getBytes());

        logger.info("Initialized Trading212 API client for " +
            (useDemo ? "demo" : "live") + " environment");
    }

    /**
     * Fetch historical orders for a specific time range
     */
    public String fetchHistoricalOrders()
        throws IOException, InterruptedException {

        enforceRateLimit();

        String url = baseUrl + "/equity/history/orders";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .GET()
            .build();

        logger.info("Fetching historical orders from: " + url);

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status " + response.statusCode() +
                ": " + response.body());
        }

        String responseBody = response.body();

        // Store debug data
        Trading212DebugStorage.storeApiResponse(responseBody, 0, "test_connection");

        return responseBody;
    }

    /**
     * Fetch historical orders with pagination support
     */
    public String fetchHistoricalOrdersPaginated(String nextPagePath)
        throws IOException, InterruptedException {

        enforceRateLimit();

        String url = nextPagePath.startsWith("http")
            ? nextPagePath
            : baseUrl + nextPagePath;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .GET()
            .build();

        logger.info("Fetching paginated orders from: " + url);

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status " + response.statusCode() +
                ": " + response.body());
        }

        return response.body();
    }

    /**
     * Enforce rate limiting (6 requests per minute)
     */
    private void enforceRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();

        // Reset counter if we're in a new time window
        if (currentTime - lastRequestTime > RATE_LIMIT_WINDOW_SECONDS * 1000) {
            requestsInWindow = 0;
            lastRequestTime = currentTime;
        }

        // Check if we've hit the limit
        if (requestsInWindow >= RATE_LIMIT_REQUESTS) {
            long waitTime = (RATE_LIMIT_WINDOW_SECONDS * 1000) -
                (currentTime - lastRequestTime);
            if (waitTime > 0) {
                logger.info("Rate limit reached, waiting " + waitTime + "ms");
                Thread.sleep(waitTime);
                // Reset after waiting
                requestsInWindow = 0;
                lastRequestTime = System.currentTimeMillis();
            }
        }

        requestsInWindow++;
    }

    /**
     * Test API connection by fetching account summary and return account information
     */
    public cz.datesoft.stockAccounting.AccountSummary testConnection() throws IOException, InterruptedException {
        enforceRateLimit();

        String url = baseUrl + "/equity/account/summary";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .GET()
            .build();

        logger.info("Testing connection to Trading 212 API");

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API test failed with status " + response.statusCode() +
                ": " + response.body());
        }

        // Parse account summary from response
        AccountSummary accountSummary = parseAccountSummary(response.body());

        // Store debug data for test connection
        Trading212DebugStorage.storeApiResponse(response.body(), 0, "test_connection");

        logger.info("Trading 212 API connection test successful: " + accountSummary);
        return accountSummary;
    }

    /**
     * Parse account summary from JSON response
     */
    private cz.datesoft.stockAccounting.AccountSummary parseAccountSummary(String jsonResponse) {
        cz.datesoft.stockAccounting.AccountSummary summary = new cz.datesoft.stockAccounting.AccountSummary();
        try {
            org.json.JSONObject json = new org.json.JSONObject(jsonResponse);

            // Basic account information
            summary.accountId = String.valueOf(json.optLong("id", 0));
            summary.currency = json.optString("currency", "N/A");
            summary.totalValue = json.optDouble("totalValue", 0.0);

            // Cash breakdown
            if (json.has("cash")) {
                org.json.JSONObject cash = json.getJSONObject("cash");
                summary.availableToTrade = cash.optDouble("availableToTrade", 0.0);
                summary.reservedForOrders = cash.optDouble("reservedForOrders", 0.0);
                summary.cashInPies = cash.optDouble("inPies", 0.0);
                summary.totalCash = summary.availableToTrade + summary.reservedForOrders + summary.cashInPies;
            }

            // Investment details
            if (json.has("investments")) {
                org.json.JSONObject investments = json.getJSONObject("investments");
                summary.investmentsCurrentValue = investments.optDouble("currentValue", 0.0);
                summary.investmentsTotalCost = investments.optDouble("totalCost", 0.0);
                summary.realizedProfitLoss = investments.optDouble("realizedProfitLoss", 0.0);
                summary.unrealizedProfitLoss = investments.optDouble("unrealizedProfitLoss", 0.0);
            }

            // Legacy fields for backward compatibility
            summary.accountType = "Trading Account"; // Default type
            summary.cashBalance = summary.availableToTrade; // Use available cash as balance
            summary.accountStatus = "Active"; // Default status

            logger.info("Parsed account summary: " + summary);
        } catch (Exception e) {
            logger.warning("Failed to parse account summary JSON: " + e.getMessage());
            // Return basic summary if parsing fails
            summary.accountId = "N/A";
            summary.currency = "N/A";
            summary.totalValue = 0.0;
            summary.totalCash = 0.0;
            summary.investmentsCurrentValue = 0.0;
            summary.accountType = "N/A";
            summary.cashBalance = 0.0;
            summary.accountStatus = "Parse error";
        }
        return summary;
    }
}