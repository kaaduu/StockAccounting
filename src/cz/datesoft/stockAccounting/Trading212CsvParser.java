/*
 * Trading212CsvParser.java
 *
 * Parses Trading 212 CSV reports (Activity export / API CSV report) and converts them
 * into Transaction objects.
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

    // Activity CSV uses named headers; we resolve indices from the header row.
    private static final String H_ACTION = "Action";
    private static final String H_TIME = "Time";
    private static final String H_ISIN = "ISIN";
    private static final String H_TICKER = "Ticker";
    private static final String H_NAME = "Name";
    private static final String H_NOTES = "Notes";
    private static final String H_ID = "ID";
    private static final String H_SHARES = "No. of shares";
    private static final String H_PRICE = "Price / share";
    private static final String H_PRICE_CUR = "Currency (Price / share)";
    private static final String H_EXCHANGE_RATE = "Exchange rate";
    private static final String H_RESULT = "Result";
    private static final String H_RESULT_CUR = "Currency (Result)";
    private static final String H_TOTAL = "Total";
    private static final String H_TOTAL_CUR = "Currency (Total)";
    private static final String H_WITHHOLDING = "Withholding tax";
    private static final String H_WITHHOLDING_CUR = "Currency (Withholding tax)";
    private static final String H_FEE_CONV = "Currency conversion fee";
    private static final String H_FEE_CONV_CUR = "Currency (Currency conversion fee)";

    private java.util.Map<String, Integer> headerIndex;

    // Interest tickers (no symbol in T212 export for cash items)
    public static final String TICKER_INTEREST_CASH = "Kreditni.Urok";
    public static final String TICKER_INTEREST_LENDING = "CP.Urok";

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
                    // Parse header row and build index map.
                    isHeader = false;
                    String[] header = parseCsvFields(line);
                    headerIndex = new java.util.HashMap<>();
                    for (int i = 0; i < header.length; i++) {
                        String h = header[i] != null ? header[i].trim() : "";
                        if (!h.isEmpty()) {
                            headerIndex.put(h, i);
                        }
                    }
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    java.util.List<Transaction> parsed = parseCsvLine(line);
                    if (parsed != null && !parsed.isEmpty()) {
                        transactions.addAll(parsed);
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
    private java.util.List<Transaction> parseCsvLine(String line) throws Exception {
        String[] fields = parseCsvFields(line);

        if (headerIndex == null || headerIndex.isEmpty()) {
            logger.warning("CSV header is missing; cannot parse rows");
            return null;
        }

        String action = getField(fields, H_ACTION);
        if (action.isEmpty()) {
            return null;
        }

        String lower = action.toLowerCase();
        java.util.ArrayList<Transaction> out = new java.util.ArrayList<>();

        // Trades
        if (lower.equals("market buy") || lower.equals("limit buy")) {
            Transaction t = createTradeTransaction(fields, Transaction.DIRECTION_SBUY);
            if (t != null) out.add(t);
            return out;
        }
        if (lower.equals("market sell") || lower.equals("limit sell")) {
            Transaction t = createTradeTransaction(fields, Transaction.DIRECTION_SSELL);
            if (t != null) out.add(t);
            return out;
        }

        // Dividends
        if (lower.equals("dividend (dividend)")) {
            java.util.List<Transaction> div = createDividendTransactions(fields);
            if (div != null) out.addAll(div);
            return out;
        }

        if (lower.equals("dividend (dividend manufactured payment)")) {
            java.util.List<Transaction> div = createDividendTransactions(fields);
            if (div != null) {
                for (Transaction t : div) {
                    if (t == null) continue;
                    String note = t.getNote();
                    String tag = "|Type:ManufacturedDividend";
                    if (note == null || note.isEmpty()) {
                        t.setNote(tag.substring(1));
                    } else if (!note.contains(tag)) {
                        t.setNote(note + tag);
                    }
                }
                out.addAll(div);
            }
            return out;
        }

        // Corporate actions (transformations)
        if (lower.equals("stock split close")) {
            Transaction t = createSplitTransaction(fields, Transaction.DIRECTION_TRANS_SUB);
            if (t != null) out.add(t);
            return out;
        }
        if (lower.equals("stock split open")) {
            Transaction t = createSplitTransaction(fields, Transaction.DIRECTION_TRANS_ADD);
            if (t != null) out.add(t);
            return out;
        }

        // Interests
        if (lower.equals("interest on cash")) {
            Transaction t = createInterestTransaction(fields, TICKER_INTEREST_CASH);
            if (t != null) out.add(t);
            return out;
        }
        if (lower.equals("lending interest")) {
            Transaction t = createInterestTransaction(fields, TICKER_INTEREST_LENDING);
            if (t != null) out.add(t);
            return out;
        }

        // Ignore cash movements / conversion lines by default.
        if (lower.equals("deposit") || lower.equals("withdrawal") || lower.equals("currency conversion")
            || lower.equals("card debit") || lower.equals("spending cashback")) {
            return null;
        }

        logger.fine("Skipping unsupported action: " + action);
        return null;
    }

    /**
     * Create a buy transaction from CSV fields
     */
    private Transaction createTradeTransaction(String[] fields, int direction) {
        try {
            String ticker = getField(fields, H_TICKER);
            if (ticker.isEmpty()) return null;

            double shares = parseDouble(getField(fields, H_SHARES));
            double price = parseDouble(getField(fields, H_PRICE));
            String priceCur = getField(fields, H_PRICE_CUR);

            if (priceCur.equalsIgnoreCase("GBX")) {
                // Some exports may contain GBX; store as GBP with scaled price.
                priceCur = "GBP";
                price = price / 100.0;
            }

            String timeStr = getField(fields, H_TIME);
            LocalDateTime tradeTime = parseDateTime(timeStr);
            java.util.Date date = Timestamp.valueOf(tradeTime);

            String companyName = getField(fields, H_NAME);
            String isin = getField(fields, H_ISIN);
            String transactionId = getField(fields, H_ID);
            String note = companyName + "|Broker:T212|ISIN:" + isin;
            if (!transactionId.isEmpty()) {
                note += "|TxnID:" + transactionId;
            }

            // Fee: ignore T212 currency conversion fee (it depends on FX context and should not be used
            // for tax-rate recalculation). Keep fee at 0.
            double fee = 0.0;
            String feeCur = priceCur;

            Transaction tx = new Transaction(
                    0,
                    date,
                    direction,
                    ticker,
                    shares,
                    price,
                    priceCur,
                    fee,
                    feeCur,
                    "TRADING212",
                    date,
                    note);

            tx.setBroker("T212");
            if (!transactionId.isEmpty()) {
                tx.setTxnId(transactionId);
            }
            return tx;
        } catch (Exception e) {
            logger.warning("Failed to create trade transaction: " + e.getMessage());
            return null;
        }
    }

    private java.util.List<Transaction> createDividendTransactions(String[] fields) {
        try {
            String ticker = getField(fields, H_TICKER);
            if (ticker.isEmpty()) return null;

            double shares = parseDouble(getField(fields, H_SHARES));
            double divPerShare = parseDouble(getField(fields, H_PRICE));
            String divCur = getField(fields, H_PRICE_CUR);
            if (divCur.equalsIgnoreCase("GBX")) {
                divCur = "GBP";
                divPerShare = divPerShare / 100.0;
            }
            double brutto = shares * divPerShare;

            String timeStr = getField(fields, H_TIME);
            LocalDateTime dt = parseDateTime(timeStr);
            java.util.Date date = Timestamp.valueOf(dt);

            String companyName = getField(fields, H_NAME);
            String isin = getField(fields, H_ISIN);
            String transactionId = getField(fields, H_ID);
            String note = companyName + "|Broker:T212|ISIN:" + isin;
            if (!transactionId.isEmpty()) {
                note += "|TxnID:" + transactionId;
            }

            java.util.ArrayList<Transaction> out = new java.util.ArrayList<>();

            // Brutto dividend
            Transaction div = new Transaction(
                    0,
                    date,
                    Transaction.DIRECTION_DIVI_BRUTTO,
                    ticker,
                    1.0,
                    brutto,
                    divCur,
                    0.0,
                    divCur,
                    "TRADING212",
                    date,
                    note);
            div.setBroker("T212");
            if (!transactionId.isEmpty()) div.setTxnId(transactionId);
            out.add(div);

            // Withholding tax (native currency)
            String wtStr = getField(fields, H_WITHHOLDING);
            String wtCur = getField(fields, H_WITHHOLDING_CUR);
            if (!wtStr.isEmpty()) {
                double wt = parseDouble(wtStr);
                if (wt != 0.0) {
                    Transaction tax = new Transaction(
                            0,
                            date,
                            Transaction.DIRECTION_DIVI_TAX,
                            ticker,
                            1.0,
                            -Math.abs(wt),
                            wtCur.isEmpty() ? divCur : wtCur,
                            0.0,
                            wtCur.isEmpty() ? divCur : wtCur,
                            "TRADING212",
                            date,
                            note);
                    tax.setBroker("T212");
                    if (!transactionId.isEmpty()) tax.setTxnId(transactionId);
                    out.add(tax);
                }
            }

            return out;
        } catch (Exception e) {
            logger.warning("Failed to create dividend transactions: " + e.getMessage());
            return null;
        }
    }

    private Transaction createInterestTransaction(String[] fields, String ticker) {
        try {
            String timeStr = getField(fields, H_TIME);
            LocalDateTime dt = parseDateTime(timeStr);
            java.util.Date date = Timestamp.valueOf(dt);

            String totalStr = getField(fields, H_TOTAL);
            String totalCur = getField(fields, H_TOTAL_CUR);
            if (totalStr.isEmpty() || totalCur.isEmpty()) {
                return null;
            }
            double amt = parseDouble(totalStr);
            if (amt == 0.0) return null;

            int dir = amt < 0 ? Transaction.DIRECTION_INT_PAID : Transaction.DIRECTION_INT_BRUTTO;

            // Note: interest lines usually have no Name/ISIN.
            String transactionId = getField(fields, H_ID);
            String note = getField(fields, H_NOTES);
            if (note.isEmpty()) {
                note = "Broker:T212";
            } else if (!note.contains("Broker:")) {
                note = note + "|Broker:T212";
            }
            if (!transactionId.isEmpty() && !note.contains("TxnID:")) {
                note += "|TxnID:" + transactionId;
            }

            Transaction tx = new Transaction(
                    0,
                    date,
                    dir,
                    ticker,
                    1.0,
                    amt,
                    totalCur,
                    0.0,
                    totalCur,
                    "TRADING212",
                    date,
                    note);
            tx.setBroker("T212");
            if (!transactionId.isEmpty()) tx.setTxnId(transactionId);
            return tx;
        } catch (Exception e) {
            logger.warning("Failed to create interest transaction: " + e.getMessage());
            return null;
        }
    }

    private Transaction createSplitTransaction(String[] fields, int direction) {
        try {
            String ticker = getField(fields, H_TICKER);
            if (ticker.isEmpty()) return null;

            double shares = parseDouble(getField(fields, H_SHARES));
            if (shares == 0.0) return null;

            String timeStr = getField(fields, H_TIME);
            LocalDateTime dt = parseDateTime(timeStr);
            java.util.Date date = Timestamp.valueOf(dt);

            String companyName = getField(fields, H_NAME);
            String isin = getField(fields, H_ISIN);
            String transactionId = getField(fields, H_ID);

            String action = getField(fields, H_ACTION);
            String note = companyName + "|Broker:T212|ISIN:" + isin + "|Action:" + action;
            if (!transactionId.isEmpty()) {
                note += "|TxnID:" + transactionId;
            }

            // Transformations: price/fee do not represent any value.
            String cur = getField(fields, H_PRICE_CUR);
            if (cur == null || cur.isEmpty()) cur = "CZK";

            Transaction tx = new Transaction(
                    0,
                    date,
                    direction,
                    ticker,
                    Math.abs(shares),
                    0.0,
                    cur,
                    0.0,
                    cur,
                    "TRADING212",
                    date,
                    note);
            tx.setBroker("T212");
            if (!transactionId.isEmpty()) tx.setTxnId(transactionId);
            return tx;
        } catch (Exception e) {
            logger.warning("Failed to create split transaction: " + e.getMessage());
            return null;
        }
    }

    private String getField(String[] fields, String headerName) {
        if (headerIndex == null) return "";
        Integer i = headerIndex.get(headerName);
        if (i == null) return "";
        int idx = i.intValue();
        if (idx < 0 || idx >= fields.length) return "";
        String v = fields[idx];
        return v == null ? "" : v.trim();
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
            // Remove any non-numeric characters except decimal point, comma and minus.
            String cleanValue = value.replaceAll("[^0-9,.-]", "").replace(',', '.');
            if (cleanValue.isEmpty() || cleanValue.equals("-") || cleanValue.equals(".")) return 0.0;
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
