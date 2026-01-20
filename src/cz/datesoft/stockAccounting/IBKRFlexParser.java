/*
 * IBKRFlexParser.java
 *
 * Parses IBKR Flex Query CSV reports
 * Maps IBKR CSV columns to Transaction object
 */

package cz.datesoft.stockAccounting;

import java.io.BufferedReader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parses IBKR Flex CSV and convert to Transaction objects
 */
public class IBKRFlexParser {

    private static final Logger logger = Logger.getLogger(IBKRFlexParser.class.getName());

    // Column indices - initialized to -1 (not found), will be detected from CSV header
    private int COL_DATE = -1;
    private int COL_SETTLEMENT_DATE = -1;
    private int COL_TRANSACTION_TYPE = -1;
    private int COL_SYMBOL = -1;
    private int COL_NAME = -1;
    private int COL_QUANTITY = -1;
    private int COL_PRICE = -1;
    private int COL_CURRENCY = -1;
    private int COL_PROCEEDS = -1;
    private int COL_COMMISSION = -1;
    private int COL_NET_PROCEEDS = -1;
    private int COL_CODE = -1;
    private int COL_EXCHANGE = -1;
    private int COL_BUY_SELL = -1;  // IBKR has dedicated Buy/Sell column
    private boolean columnsDetected = false;

    public Vector<Transaction> parseCsvReport(String csvContent) throws Exception {
        Vector<Transaction> transactions = new Vector<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    detectColumnIndices(line);
                    isHeader = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    Transaction transaction = parseCsvLine(line);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to parse CSV line: " + line + " - " + e.getMessage());
                }
            }
        }

        logger.info("Parsed " + transactions.size() + " transactions from IBKR Flex CSV");
        return transactions;
    }

    private void detectColumnIndices(String headerLine) {
        String[] headers = splitCsvLine(headerLine);

        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].toLowerCase().trim();

            // Date - prioritize "tradedate" over generic "date"
            if (h.equals("tradedate") || h.equals("dateoftrade")) {
                COL_DATE = i;
            } else if (COL_DATE < 0 && h.equals("date")) {
                COL_DATE = i;
            } else if (COL_DATE < 0 && h.contains("date") && h.contains("trade") && !h.contains("orig")) {
                COL_DATE = i;
            }
            // Settlement date
            else if (h.contains("settlement") && h.contains("date")) {
                COL_SETTLEMENT_DATE = i;
            }
            // Transaction type
            else if (h.equals("transactiontype") || h.equals("transaction type")) {
                COL_TRANSACTION_TYPE = i;
            } else if (COL_TRANSACTION_TYPE < 0 && (h.contains("transaction") || h.equals("type"))) {
                COL_TRANSACTION_TYPE = i;
            }
            // Symbol
            else if (h.equals("symbol") || h.equals("ticker")) {
                COL_SYMBOL = i;
            }
            // Description/Name
            else if (h.equals("description") || h.equals("name")) {
                COL_NAME = i;
            }
            // Quantity
            else if (h.equals("quantity") || h.equals("shares")) {
                COL_QUANTITY = i;
            }
            // Price - prioritize "tradeprice" over generic "price"
            else if (h.equals("tradeprice") || h.equals("trade price")) {
                COL_PRICE = i;
            } else if (COL_PRICE < 0 && h.equals("price")) {
                COL_PRICE = i;
            } else if (COL_PRICE < 0 && h.contains("trade") && h.contains("price")) {
                COL_PRICE = i;
            }
            // Currency - prioritize account currency
            else if (h.equals("currencyprimary") || h.equals("currency primary")) {
                COL_CURRENCY = i;
            } else if (COL_CURRENCY < 0 && h.equals("currency")) {
                COL_CURRENCY = i;
            } else if (COL_CURRENCY < 0 && h.contains("currency") && !h.contains("commission")) {
                COL_CURRENCY = i;
            }
            // Proceeds
            else if (h.contains("proceeds") || h.contains("gross")) {
                COL_PROCEEDS = i;
            }
            // Commission - prioritize main commission field
            else if (h.equals("ibcommission") || h.equals("commission")) {
                COL_COMMISSION = i;
            } else if (COL_COMMISSION < 0 && (h.contains("commission") || h.contains("fee")) && !h.contains("currency")) {
                COL_COMMISSION = i;
            }
            // Net proceeds
            else if (h.contains("net") && (h.contains("cash") || h.contains("proceeds"))) {
                COL_NET_PROCEEDS = i;
            }
            // Exchange
            else if (h.equals("exchange") || h.equals("listingexchange")) {
                COL_EXCHANGE = i;
            }
            // Buy/Sell - IBKR specific column
            else if (h.equals("buy/sell") || h.equals("buysell")) {
                COL_BUY_SELL = i;
            }
            // Code
            else if (h.equals("code") || h.contains("notes/codes")) {
                COL_CODE = i;
            }
        }

        columnsDetected = true;
        
        // Log detected columns for debugging
        logger.info("Detected IBKR CSV columns:");
        logger.info("  Date: " + COL_DATE);
        logger.info("  Symbol: " + COL_SYMBOL);
        logger.info("  Quantity: " + COL_QUANTITY);
        logger.info("  Price: " + COL_PRICE);
        logger.info("  Currency: " + COL_CURRENCY);
        logger.info("  Commission: " + COL_COMMISSION);
        logger.info("  Buy/Sell: " + COL_BUY_SELL);
        logger.info("  TransactionType: " + COL_TRANSACTION_TYPE);
        logger.info("  Exchange: " + COL_EXCHANGE);
        
        // Validate required columns were found
        boolean allRequiredFound = true;
        StringBuilder missing = new StringBuilder();
        
        if (COL_DATE < 0) {
            missing.append("Date, ");
            allRequiredFound = false;
        }
        if (COL_SYMBOL < 0) {
            missing.append("Symbol, ");
            allRequiredFound = false;
        }
        if (COL_QUANTITY < 0) {
            missing.append("Quantity, ");
            allRequiredFound = false;
        }
        if (COL_PRICE < 0) {
            missing.append("Price, ");
            allRequiredFound = false;
        }
        
        // Warnings for optional but useful columns
        if (COL_BUY_SELL < 0) {
            logger.warning("Buy/Sell column not found - will infer direction from TransactionType");
        }
        if (COL_COMMISSION < 0) {
            logger.warning("Commission column not found");
        }
        if (COL_CURRENCY < 0) {
            logger.warning("Currency column not found - will use default");
        }
        
        if (!allRequiredFound) {
            logger.severe("CRITICAL: Missing required CSV columns: " + missing.toString());
            throw new RuntimeException("Cannot parse IBKR CSV - missing required columns: " + missing.toString());
        } else {
            logger.info("Successfully detected all required IBKR Flex CSV columns");
        }
    }

    private Transaction parseCsvLine(String line) throws Exception {
        String[] fields = splitCsvLine(line);

        if (fields.length <= COL_SYMBOL || !columnsDetected) {
            return null;
        }

        Date tradeDate = parseDate(fields[COL_DATE]);
        int direction = mapTransactionTypeToDirection(fields[COL_TRANSACTION_TYPE]);
        String ticker = cleanTicker(fields[COL_SYMBOL]);
        double quantity = parseDouble(fields[COL_QUANTITY]);
        double price = parseDouble(fields[COL_PRICE]);
        String currency = fields[COL_CURRENCY].trim();
        double commission = parseDouble(fields[COL_COMMISSION]);
        String exchange = fields.length > COL_EXCHANGE ? fields[COL_EXCHANGE].trim() : "";
        String code = fields.length > COL_CODE ? fields[COL_CODE].trim() : "";

        double amount = Math.abs(quantity);
        double total = 0;

        if (direction > 0) {
            total = price * amount;
        } else {
            total = fields.length > COL_NET_PROCEEDS ? 
                parseDouble(fields[COL_NET_PROCEEDS]) : price * amount;
        }

        Transaction transaction = new Transaction(
                0,
                tradeDate,
                direction,
                ticker,
                amount,
                price,
                currency,
                commission,
                currency,
                exchange,
                tradeDate,
                buildNote(fields, code)
        );

        return transaction;
    }

    private Date parseDate(String dateStr) throws Exception {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return new Date();
        }

        SimpleDateFormat ibkrFormat = new SimpleDateFormat("yyyyMMdd");
        return ibkrFormat.parse(dateStr.trim());
    }

    private int mapTransactionTypeToDirection(String txType) {
        if (txType == null) {
            logger.warning("Transaction type is null, defaulting to 0");
            return 0;
        }

        String typeLower = txType.toLowerCase().trim();

        // Stock/equity transactions
        if (typeLower.contains("buy") || typeLower.contains("purchase") || 
                typeLower.contains("long")) {
            return Transaction.DIRECTION_SBUY;
        } else if (typeLower.contains("sell") || typeLower.contains("short") ||
                   typeLower.contains("cover")) {
            return Transaction.DIRECTION_SSELL;
        } 
        // Income transactions
        else if (typeLower.contains("dividend") || typeLower.contains("div")) {
            return Transaction.DIRECTION_DIVI_BRUTTO;
        } else if (typeLower.contains("interest") || typeLower.contains("int")) {
            return Transaction.DIRECTION_DIVI_BRUTTO;
        } 
        // Fees and taxes
        else if (typeLower.contains("fee") || typeLower.contains("commission")) {
            return Transaction.DIRECTION_TRANS_SUB;
        } else if (typeLower.contains("tax") || typeLower.contains("withholding")) {
            return Transaction.DIRECTION_TRANS_SUB;
        } 
        // Deposits and transfers
        else if (typeLower.contains("deposit") || typeLower.contains("deposits")) {
            return Transaction.DIRECTION_TRANS_ADD;
        } else if (typeLower.contains("withdrawal") || typeLower.contains("withdrawals")) {
            return Transaction.DIRECTION_TRANS_SUB;
        } else if (typeLower.contains("transfer")) {
            return Transaction.DIRECTION_TRANS_ADD;
        }
        // Options and other instruments
        else if (typeLower.contains("option") || typeLower.contains("call") || 
                 typeLower.contains("put")) {
            logger.info("Option transaction detected: " + txType + " - treating as buy/sell");
            return typeLower.contains("sell") ? Transaction.DIRECTION_SSELL : Transaction.DIRECTION_SBUY;
        }
        // Corporate actions
        else if (typeLower.contains("split") || typeLower.contains("merger") || 
                 typeLower.contains("spinoff")) {
            logger.info("Corporate action detected: " + txType + " - may require manual review");
            return 0;
        }

        // Unknown type - log warning
        logger.warning("Unknown transaction type '" + txType + "' - defaulting to 0. This transaction may need manual review.");
        return 0;
    }

    private String cleanTicker(String ticker) {
        if (ticker == null) return "";
        return ticker.trim().split("\\s+")[0].trim();
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        try {
            String cleaned = value.replaceAll("[^0-9.-]", "");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private String buildNote(String[] fields, String code) {
        StringBuilder note = new StringBuilder();
        note.append("Broker:IBKR");

        if (fields.length > 15) {
            String txnId = fields[15].trim();
            if (!txnId.isEmpty()) {
                note.append("|TxnID:").append(txnId);
            }
        }

        if (!code.isEmpty()) {
            note.append("|Code:").append(code);
        }

        return note.toString();
    }
}
