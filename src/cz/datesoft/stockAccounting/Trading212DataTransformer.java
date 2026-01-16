/*
 * Trading212DataTransformer.java
 *
 * Transforms Trading 212 API responses into Transaction objects
 * Uses manual JSON parsing since Java 8 doesn't have built-in JSON support
 */

package cz.datesoft.stockAccounting;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms Trading 212 API JSON responses into Transaction objects
 * Implements manual JSON parsing for Java 8 compatibility
 */
public class Trading212DataTransformer {

    private static final Logger logger = Logger.getLogger(Trading212DataTransformer.class.getName());

    /**
     * Transform API response JSON into Transaction objects
     */
    public Vector<Transaction> transformOrdersResponse(String jsonResponse) throws Exception {
        return transformOrdersResponse(jsonResponse, null, null);
    }

    /**
     * Transform API response JSON into Transaction objects with date filtering
     */
    public Vector<Transaction> transformOrdersResponse(String jsonResponse, LocalDateTime fromDate, LocalDateTime toDate) throws Exception {
        Vector<Transaction> transactions = new Vector<>();

        try {
            // Extract items array from JSON response
            String itemsJson = extractItemsArray(jsonResponse);
            if (itemsJson == null) {
                throw new Exception("No items found in API response");
            }

            // Parse individual order objects
            Vector<String> orderJsons = parseJsonArray(itemsJson);
            logger.info("Processing " + orderJsons.size() + " orders from API response");

            int processedCount = 0;
            int filteredCount = 0;

            for (String orderJson : orderJsons) {
                processedCount++;

                // Extract order date for filtering
                LocalDateTime orderDate = extractOrderDate(orderJson);

                // Apply date filtering if date range is specified
                if (fromDate != null && toDate != null) {
                    if (orderDate.isBefore(fromDate) || orderDate.isAfter(toDate)) {
                        filteredCount++;
                        continue; // Skip orders outside date range
                    }
                }

                Transaction transaction = transformOrderJsonToTransaction(orderJson);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }

            logger.info("Processed " + processedCount + " orders, filtered " + filteredCount +
                       " by date, created " + transactions.size() + " transactions");

        } catch (Exception e) {
            logger.severe("Failed to parse API response: " + e.getMessage());
            throw new Exception("Invalid API response format: " + e.getMessage(), e);
        }

        logger.info("Successfully transformed " + transactions.size() + " transactions");
        return transactions;
    }

    /**
     * Extract the "items" array from the JSON response
     */
    private String extractItemsArray(String json) {
        Pattern pattern = Pattern.compile("\"items\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return "[" + matcher.group(1) + "]";
        }
        return null;
    }

    /**
     * Parse a JSON array string into individual object strings
     */
    private Vector<String> parseJsonArray(String jsonArray) {
        Vector<String> objects = new Vector<>();
        int braceCount = 0;
        int startPos = -1;

        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);

            if (c == '{') {
                if (braceCount == 0) {
                    startPos = i;
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && startPos != -1) {
                    String obj = jsonArray.substring(startPos, i + 1);
                    objects.add(obj);
                    startPos = -1;
                }
            }
        }

        return objects;
    }

    /**
     * Transform a single order JSON string to Transaction
     */
    private Transaction transformOrderJsonToTransaction(String orderJson) throws Exception {
        try {
            // Extract fields using regex patterns
            String ticker = extractStringField(orderJson, "ticker");
            if (ticker == null) return null;

            Double quantity = extractDoubleField(orderJson, "quantity");
            if (quantity == null) return null;

            Double price = extractDoubleField(orderJson, "averagePrice");
            if (price == null) price = 0.0;

            Double fee = extractDoubleField(orderJson, "fee");
            if (fee == null) fee = 0.0;

            String timeStr = extractStringField(orderJson, "time");
            if (timeStr == null) return null;

            // Parse timestamp
            LocalDateTime orderTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            java.util.Date date = Timestamp.valueOf(orderTime);

            // Determine direction (BUY/SELL)
            int direction = determineDirection(quantity);

            // Convert negative quantity to positive for BUY orders
            if (direction == Transaction.DIRECTION_SBUY || direction == Transaction.DIRECTION_DBUY) {
                quantity = Math.abs(quantity);
            }

            // Default values for fields not provided by API
            String priceCurrency = "USD"; // Trading 212 typically uses USD
            String feeCurrency = "USD";
            String market = "TRADING212";
            java.util.Date executionDate = date; // Use same date for execution

            // Create transaction
            Transaction transaction = new Transaction(
                0, // serial will be set later
                date,
                direction,
                ticker,
                quantity,
                price,
                priceCurrency,
                fee,
                feeCurrency,
                market,
                executionDate,
                "Imported from Trading 212 API"
            );

            return transaction;

        } catch (Exception e) {
            logger.warning("Failed to transform order: " + orderJson + " - " + e.getMessage());
            return null; // Skip this order
        }
    }

    /**
     * Extract string field from JSON using regex
     */
    private String extractStringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract double field from JSON using regex
     */
    private Double extractDoubleField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*([0-9.-]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Determine transaction direction based on quantity sign
     * Trading 212 API uses negative quantity for sells
     */
    private int determineDirection(double quantity) {
        boolean isSell = quantity < 0;

        // For now, assume all trades are stocks (not derivatives)
        // This could be enhanced to detect based on ticker patterns
        return isSell ? Transaction.DIRECTION_SSELL : Transaction.DIRECTION_SBUY;
    }

    /**
     * Extract order date from JSON for filtering
     */
    private LocalDateTime extractOrderDate(String orderJson) {
        try {
            String timeStr = extractStringField(orderJson, "time");
            if (timeStr != null) {
                return LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
        } catch (Exception e) {
            logger.warning("Could not extract date from order: " + e.getMessage());
        }
        return LocalDateTime.now(); // Default fallback
    }

    /**
     * Extract year from a time string for debug purposes
     */
    public static int extractYearFromTimeString(String timeStr) {
        try {
            LocalDateTime time = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return time.getYear();
        } catch (Exception e) {
            return LocalDateTime.now().getYear();
        }
    }
}