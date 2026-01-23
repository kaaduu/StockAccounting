/*
 * Trading212CsvParser.java
 *
 * Parses Trading 212 CSV reports and converts to Transaction objects
 */

package cz.datesoft.stockAccounting;

import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * Parses Trading 212 CSV reports containing orders, transactions, dividends, and interest
 */
public class Trading212CsvParser {

    private static final Logger logger = Logger.getLogger(Trading212CsvParser.class.getName());

    // CSV column indices based on actual Trading 212 CSV format
    private static final int COL_ACTION = 0;
    private static final int COL_TIME = 1;
    private static final int COL_ISIN = 2;
    private static final int COL_TICKER = 3;
    private static final int COL_NAME = 4;
    private static final int COL_NOTES = 5;
    private static final int COL_ID = 6;
    private static final int COL_SHARES = 7;
    private static final int COL_PRICE = 8;
    private static final int COL_PRICE_CURRENCY = 9;
    private static final int COL_EXCHANGE_RATE = 10;
    private static final int COL_RESULT = 11;
    private static final int COL_RESULT_CURRENCY = 12;
    private static final int COL_TOTAL = 13;
    private static final int COL_TOTAL_CURRENCY = 14;

    /**
     * Parse CSV content and convert to Transaction objects
     */
    public Vector<Transaction> parseCsvReport(String csvContent) throws Exception {
        Vector<Transaction> transactions = new Vector<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    // Skip header row
                    isHeader = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    Transaction transaction = parseCsvLine(line);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to parse CSV line: " + line + " - " + e.getMessage());
                    // Continue with other lines
                }
            }
        }

        logger.info("Parsed " + transactions.size() + " transactions from CSV");
        return transactions;
    }

    /**
     * Parse a single CSV line into a Transaction object
     */
    private Transaction parseCsvLine(String line) throws Exception {
        String[] fields = parseCsvFields(line);

        if (fields.length < COL_TOTAL_CURRENCY + 1) {
            logger.warning("CSV line has insufficient fields: " + fields.length);
            return null;
        }

        String action = fields[COL_ACTION].trim();
        if (action.isEmpty()) {
            return null;
        }

        // Handle different action types
        switch (action.toLowerCase()) {
            case "market buy":
            case "limit buy":
                return createBuyTransaction(fields);
            case "market sell":
            case "limit sell":
                return createSellTransaction(fields);
            case "deposit":
            case "withdrawal":
                // Skip non-trade transactions for now
                logger.info("Skipping non-trade action: " + action);
                return null;
            default:
                logger.warning("Unknown action type: " + action);
                return null;
        }
    }

    /**
     * Create a buy transaction from CSV fields
     */
    private Transaction createBuyTransaction(String[] fields) throws Exception {
        try {
            String ticker = fields[COL_TICKER].trim();
            if (ticker.isEmpty()) {
                return null; // No ticker = not a trade
            }

            double shares = parseDouble(fields[COL_SHARES]);
            double price = parseDouble(fields[COL_PRICE]);
            String timeStr = fields[COL_TIME].trim();

            // Parse time: "2022-04-06 14:36:21" (no timezone)
            LocalDateTime tradeTime = parseDateTime(timeStr);
            java.util.Date date = Timestamp.valueOf(tradeTime);

            // Create note with company name, broker, and ISIN
            String companyName = fields[COL_NAME].trim();
            String isin = fields[COL_ISIN].trim();
            String transactionId = (COL_ID >= 0 && COL_ID < fields.length) ? fields[COL_ID].trim() : "";
            String note = companyName + "|Broker:T212|ISIN:" + isin;
            if (!transactionId.isEmpty()) {
                note += "|TxnID:" + transactionId;
            }

            // Create transaction
            Transaction transaction = new Transaction(
                0, // serial will be set later
                date,
                Transaction.DIRECTION_SBUY,    // Stock buy
                ticker,
                shares,
                price,
                "USD",                         // Trading 212 primarily uses USD
                0.0,                          // Fee not directly available in CSV
                "USD",
                "TRADING212",
                date,                         // execution date same as order date
                note
            );

            transaction.setBroker("T212");
            if (!transactionId.isEmpty()) {
                transaction.setTxnId(transactionId);
            }

            return transaction;

        } catch (Exception e) {
            logger.warning("Failed to create buy transaction: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create a sell transaction from CSV fields
     */
    private Transaction createSellTransaction(String[] fields) throws Exception {
        try {
            String ticker = fields[COL_TICKER].trim();
            if (ticker.isEmpty()) {
                return null; // No ticker = not a trade
            }

            double shares = parseDouble(fields[COL_SHARES]);
            double price = parseDouble(fields[COL_PRICE]);
            String timeStr = fields[COL_TIME].trim();

            // Parse time: "2022-04-06 14:36:21" (no timezone)
            LocalDateTime tradeTime = parseDateTime(timeStr);
            java.util.Date date = Timestamp.valueOf(tradeTime);

            // Create note with company name, broker, and ISIN
            String companyName = fields[COL_NAME].trim();
            String isin = fields[COL_ISIN].trim();
            String transactionId = (COL_ID >= 0 && COL_ID < fields.length) ? fields[COL_ID].trim() : "";
            String note = companyName + "|Broker:T212|ISIN:" + isin;
            if (!transactionId.isEmpty()) {
                note += "|TxnID:" + transactionId;
            }

            // Create transaction
            Transaction transaction = new Transaction(
                0, // serial will be set later
                date,
                Transaction.DIRECTION_SSELL,   // Stock sell
                ticker,
                shares,                        // Shares are positive in CSV
                price,
                "USD",                         // Trading 212 primarily uses USD
                0.0,                          // Fee not directly available in CSV
                "USD",
                "TRADING212",
                date,                         // execution date same as order date
                note
            );

            transaction.setBroker("T212");
            if (!transactionId.isEmpty()) {
                transaction.setTxnId(transactionId);
            }

            return transaction;

        } catch (Exception e) {
            logger.warning("Failed to create sell transaction: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse CSV fields handling quoted values and commas within quotes
     */
    private String[] parseCsvFields(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }

        // Add last field
        fields.add(currentField.toString().trim());

        return fields.toArray(new String[0]);
    }

    /**
     * Parse double safely, handling empty strings and invalid formats
     */
    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        try {
            // Remove any non-numeric characters except decimal point and minus
            String cleanValue = value.replaceAll("[^0-9.-]", "");
            return Double.parseDouble(cleanValue);
        } catch (NumberFormatException e) {
            logger.warning("Failed to parse double: '" + value + "' - " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Parse date/time from Trading 212 CSV format: "2022-04-06 14:36:21"
     */
    private LocalDateTime parseDateTime(String dateStr) {
        try {
            if (dateStr == null || dateStr.trim().isEmpty()) {
                logger.warning("Empty date string provided");
                return LocalDateTime.now();
            }

            // Trading 212 CSV uses format: "2022-04-06 14:36:21"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(dateStr.trim(), formatter);

        } catch (Exception e) {
            logger.warning("Failed to parse date: '" + dateStr + "' - " + e.getMessage());
            return LocalDateTime.now();
        }
    }
}
