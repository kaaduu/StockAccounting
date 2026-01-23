/*
 * IBKRFlexParser.java
 *
 * Parses IBKR Flex Query CSV reports
 * Maps IBKR CSV columns to Transaction object
 */

package cz.datesoft.stockAccounting;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private int COL_CLIENT_ACCOUNT_ID = -1;  // ClientAccountID
    private int COL_ISIN = -1;               // ISIN
    private int COL_ASSET_CLASS = -1;        // AssetClass (STK, OPT, FUT)
    private int COL_DATETIME = -1;           // DateTime (with time component)
    private int COL_TRANSACTION_ID = -1;     // TransactionID (per-fill ID)
    private int COL_IB_ORDER_ID = -1;        // IBOrderID (order-level ID, shared across fills)
    private int COL_MULTIPLIER = -1;         // Contract size multiplier (for options/futures)
    // Corporate actions section columns (47-col header)
    private int COL_CA_REPORT_DATE = -1;     // "Report Date"
    private int COL_CA_DATETIME = -1;        // "Date/Time"
    private int COL_CA_ACTION_DESCRIPTION = -1; // "ActionDescription"
    private int COL_CA_TYPE = -1;            // "Type" (RS/TC/IC/TO)
    private int COL_ACTION_ID = -1;          // "ActionID"
    private boolean columnsDetected = false;

    // Statistics tracking (populated during parsing, queryable after parseCsvReport() completes)
    private int skippedZeroNetEvents = 0;
    private List<String> skippedZeroNetTickers = new ArrayList<>();
    private int importedCorporateActionEvents = 0;

    private static double roundTo(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null) return BigDecimal.ZERO;
        String t = value.trim();
        if (t.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(t);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal normalizeFee(BigDecimal feeAbs) {
        // Match IB TradeLog behavior: fees stored with 6 decimals, effectively truncated.
        // This avoids +0.000001 differences for ...xxxxx50 cases.
        return feeAbs.setScale(6, RoundingMode.DOWN);
    }

    private java.util.Set<String> allowedAssetClasses = null; // null => allow all

    private enum SectionType {
        TRADES,
        OPTIONS_SUMMARY,
        CORPORATE_ACTIONS,
        UNKNOWN
    }

    private SectionType currentSectionType = SectionType.UNKNOWN;
    private int ignoredOptionsSummaryRows = 0;

    private boolean includeCorporateActions = true;

    private boolean includeTrades = true;

    private java.util.Set<String> allowedCorporateActionTypes = null; // null => allow all

    public void setIncludeCorporateActions(boolean includeCorporateActions) {
        this.includeCorporateActions = includeCorporateActions;
    }

    public void setIncludeTrades(boolean includeTrades) {
        this.includeTrades = includeTrades;
    }

    public void setAllowedCorporateActionTypes(java.util.Set<String> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            this.allowedCorporateActionTypes = null;
            return;
        }
        java.util.Set<String> norm = new java.util.HashSet<>();
        for (String t : allowedTypes) {
            if (t != null && !t.trim().isEmpty()) {
                norm.add(t.trim().toUpperCase());
            }
        }
        this.allowedCorporateActionTypes = norm.isEmpty() ? null : norm;
    }

    private boolean isCorporateActionTypeAllowed(String type) {
        if (allowedCorporateActionTypes == null) return true;
        if (type == null) return false;
        String t = type.trim().toUpperCase();
        if (t.isEmpty()) return false;
        return allowedCorporateActionTypes.contains(t);
    }

    public void setAllowedAssetClasses(java.util.Set<String> allowedAssetClasses) {
        if (allowedAssetClasses == null || allowedAssetClasses.isEmpty()) {
            this.allowedAssetClasses = null;
        } else {
            java.util.Set<String> norm = new java.util.HashSet<>();
            for (String a : allowedAssetClasses) {
                if (a != null && !a.trim().isEmpty()) {
                    norm.add(a.trim().toUpperCase());
                }
            }
            this.allowedAssetClasses = norm.isEmpty() ? null : norm;
        }
    }

    private boolean isAssetClassAllowed(String assetClass) {
        if (allowedAssetClasses == null) return true;
        if (assetClass == null) return false;
        return allowedAssetClasses.contains(assetClass.trim().toUpperCase());
    }

    private SectionType detectSectionType(String headerLine) {
        String lower = headerLine.toLowerCase();

        // Trades / executions section
        boolean hasTransactionType = lower.contains("\"transactiontype\"");
        boolean hasTradePrice = lower.contains("\"tradeprice\"");
        boolean hasIbOrderId = lower.contains("\"iborderid\"");
        boolean hasExchange = lower.contains("\"exchange\"");
        if (hasTransactionType && hasTradePrice && hasIbOrderId && hasExchange) {
            return SectionType.TRADES;
        }

        // Options/positions summary section (note the spaces in column names)
        boolean hasTransactionTypeSpaced = lower.contains("\"transaction type\"");
        boolean hasTradePriceSpaced = lower.contains("\"trade price\"");
        if (hasTransactionTypeSpaced && hasTradePriceSpaced && !hasIbOrderId) {
            return SectionType.OPTIONS_SUMMARY;
        }

        // Corporate actions section
        boolean hasActionDescription = lower.contains("\"actiondescription\"");
        boolean hasActionId = lower.contains("\"actionid\"");
        if (hasActionDescription && hasActionId) {
            return SectionType.CORPORATE_ACTIONS;
        }

        return SectionType.UNKNOWN;
    }

    private static class RawCorporateActionRow {
        String actionId;
        String type;
        String code;
        String symbol;
        BigDecimal quantity;
        String dateTimeStr;
        String actionDescription;
    }

    private String normalizeCorporateActionSymbol(String type, String symbol) {
        if (symbol == null) return "";
        String s = symbol.trim();
        if (s.isEmpty()) return "";

        // IBKR internal suffixes
        if (s.endsWith(".OLD")) {
            s = s.substring(0, s.length() - 4);
        }

        // IC: treat as same ticker identity (e.g. GAME.NEW -> GAME)
        if (type != null && type.equalsIgnoreCase("IC") && s.endsWith(".NEW")) {
            s = s.substring(0, s.length() - 4);
        }

        // Tender / rounding placeholder
        if (s.endsWith(".RND")) {
            s = s.substring(0, s.length() - 4);
        }

        return s;
    }

    private RawCorporateActionRow parseCorporateActionRow(String[] fields) {
        try {
            if (COL_ACTION_ID < 0 || COL_ACTION_ID >= fields.length) {
                return null;
            }
            if (COL_CA_TYPE < 0 || COL_CA_TYPE >= fields.length) {
                return null;
            }
            if (COL_CA_DATETIME < 0 || COL_CA_DATETIME >= fields.length) {
                return null;
            }
            if (COL_SYMBOL < 0 || COL_SYMBOL >= fields.length) {
                return null;
            }
            if (COL_QUANTITY < 0 || COL_QUANTITY >= fields.length) {
                return null;
            }

            RawCorporateActionRow row = new RawCorporateActionRow();
            row.actionId = fields[COL_ACTION_ID].trim();
            row.type = fields[COL_CA_TYPE].trim();
            row.code = (COL_CODE >= 0 && COL_CODE < fields.length) ? fields[COL_CODE].trim() : "";
            row.symbol = fields[COL_SYMBOL].trim();
            row.quantity = parseDecimal(fields[COL_QUANTITY]);
            row.dateTimeStr = fields[COL_CA_DATETIME].trim();
            if (row.dateTimeStr.isEmpty() && COL_CA_REPORT_DATE >= 0 && COL_CA_REPORT_DATE < fields.length) {
                row.dateTimeStr = fields[COL_CA_REPORT_DATE].trim();
            }
            row.actionDescription = (COL_CA_ACTION_DESCRIPTION >= 0 && COL_CA_ACTION_DESCRIPTION < fields.length)
                ? fields[COL_CA_ACTION_DESCRIPTION].trim()
                : ((COL_NAME >= 0 && COL_NAME < fields.length) ? fields[COL_NAME].trim() : "");

            if (row.actionId.isEmpty() || row.type.isEmpty() || row.symbol.isEmpty()) {
                return null;
            }

            // Type filter (RS/TC/IC/TO)
            if (!isCorporateActionTypeAllowed(row.type)) {
                return null;
            }
            if (row.dateTimeStr.isEmpty()) {
                // Should not happen for well-formed exports
                logger.warning("Corporate action row missing Date/Time for ActionID=" + row.actionId);
                return null;
            }
            return row;
        } catch (Exception e) {
            logger.warning("Failed to parse corporate action row: " + e.getMessage());
            return null;
        }
    }

    private Vector<Transaction> buildCorporateActionsFromActionIdNetting(List<RawCorporateActionRow> rows) {
        Map<String, List<RawCorporateActionRow>> byActionId = new LinkedHashMap<>();
        for (RawCorporateActionRow r : rows) {
            byActionId.computeIfAbsent(r.actionId, k -> new ArrayList<>()).add(r);
        }

        Vector<Transaction> result = new Vector<>();
        for (Map.Entry<String, List<RawCorporateActionRow>> e : byActionId.entrySet()) {
            String actionId = e.getKey();
            List<RawCorporateActionRow> grp = e.getValue();
            if (grp.isEmpty()) continue;

            // Use the earliest datetime in the group
            Date eventDate;
            try {
                grp.sort(Comparator.comparing(r -> r.dateTimeStr));
                eventDate = parseDate(grp.get(0).dateTimeStr);
            } catch (Exception ex) {
                logger.warning("Failed to parse corporate action Date/Time for ActionID=" + actionId + ": " + ex.getMessage());
                continue;
            }

            // Net quantities per raw symbol (INCLUDING Code=Ca rows)
            Map<String, BigDecimal> netByRawSymbol = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (RawCorporateActionRow r : grp) {
                if (r.symbol == null || r.symbol.trim().isEmpty()) continue;
                netByRawSymbol.merge(r.symbol.trim(), r.quantity, BigDecimal::add);
            }

            // Build a shared note for the event
            String type = grp.get(0).type;
            String desc = grp.get(0).actionDescription;
            if (desc == null) desc = "";

            // Aggregate by normalized ticker, but keep OUT and IN separately (needed for splits)
            Map<String, BigDecimal> outByTicker = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            Map<String, BigDecimal> inByTicker = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            List<String> origSymbols = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> n : netByRawSymbol.entrySet()) {
                BigDecimal net = n.getValue();
                if (net == null) continue;
                if (net.abs().compareTo(new BigDecimal("0.000001")) <= 0) {
                    continue;
                }

                String rawSymbol = n.getKey();
                origSymbols.add(rawSymbol);

                String normSymbol;
                if (type != null && type.equalsIgnoreCase("TC")) {
                    // For TC keep tickers distinct (rename event)
                    normSymbol = normalizeCorporateActionSymbol("", rawSymbol);
                } else {
                    normSymbol = normalizeCorporateActionSymbol(type, rawSymbol);
                }

                if (net.signum() < 0) {
                    outByTicker.merge(normSymbol, net.abs(), BigDecimal::add);
                } else {
                    inByTicker.merge(normSymbol, net, BigDecimal::add);
                }
            }

            // Drop no-op events after normalization: IC and TO often become same ticker with equal OUT/IN.
            if (type != null && (type.equalsIgnoreCase("IC") || type.equalsIgnoreCase("TO"))) {
                boolean onlyOneTicker = outByTicker.size() == 1 && inByTicker.size() == 1 &&
                    outByTicker.keySet().iterator().next().equalsIgnoreCase(inByTicker.keySet().iterator().next());
                if (onlyOneTicker) {
                    BigDecimal out = outByTicker.values().iterator().next();
                    BigDecimal in = inByTicker.values().iterator().next();
                    if (out != null && in != null && out.subtract(in).abs().compareTo(new BigDecimal("0.000001")) <= 0) {
                        // No-op for FIFO; skip
                        continue;
                    }
                }
            }

            String notePrefix = type + "|ActionID:" + actionId + ": " + desc;
            if (!origSymbols.isEmpty()) {
                notePrefix += "|OrigSymbols:" + String.join(",", origSymbols);
            }

            // Emit OUT first, then IN
            List<Transaction> outs = new ArrayList<>();
            List<Transaction> ins = new ArrayList<>();

            for (Map.Entry<String, BigDecimal> o : outByTicker.entrySet()) {
                try {
                    Transaction t = new Transaction(
                        0,
                        eventDate,
                        Transaction.DIRECTION_TRANS_SUB,
                        o.getKey(),
                        o.getValue().doubleValue(),
                        0.0,
                        "USD",
                        0.0,
                        "USD",
                        "",
                        eventDate,
                        notePrefix
                    );
                    t.setBroker("IB");
                    t.setTxnId(actionId);
                    if (type != null && !type.trim().isEmpty()) {
                        t.setCode(type.trim());
                    }
                    outs.add(t);
                } catch (Exception ex) {
                    logger.warning("Failed to build corporate OUT transaction: " + ex.getMessage());
                }
            }
            for (Map.Entry<String, BigDecimal> i2 : inByTicker.entrySet()) {
                try {
                    Transaction t = new Transaction(
                        0,
                        eventDate,
                        Transaction.DIRECTION_TRANS_ADD,
                        i2.getKey(),
                        i2.getValue().doubleValue(),
                        0.0,
                        "USD",
                        0.0,
                        "USD",
                        "",
                        eventDate,
                        notePrefix
                    );
                    t.setBroker("IB");
                    t.setTxnId(actionId);
                    if (type != null && !type.trim().isEmpty()) {
                        t.setCode(type.trim());
                    }
                    ins.add(t);
                } catch (Exception ex) {
                    logger.warning("Failed to build corporate IN transaction: " + ex.getMessage());
                }
            }

            outs.sort(Comparator.comparing(Transaction::getTicker, String.CASE_INSENSITIVE_ORDER));
            ins.sort(Comparator.comparing(Transaction::getTicker, String.CASE_INSENSITIVE_ORDER));
            result.addAll(outs);
            result.addAll(ins);
        }

        // Final stable ordering: day then OUT before IN
        result.sort((t1, t2) -> {
            Date d1 = t1.getDate();
            Date d2 = t2.getDate();
            if (d1 != null && d2 != null) {
                java.util.Calendar c1 = java.util.Calendar.getInstance();
                java.util.Calendar c2 = java.util.Calendar.getInstance();
                c1.setTime(d1);
                c2.setTime(d2);
                int y1 = c1.get(java.util.Calendar.YEAR);
                int y2 = c2.get(java.util.Calendar.YEAR);
                if (y1 != y2) return Integer.compare(y1, y2);
                int m1 = c1.get(java.util.Calendar.MONTH);
                int m2 = c2.get(java.util.Calendar.MONTH);
                if (m1 != m2) return Integer.compare(m1, m2);
                int day1 = c1.get(java.util.Calendar.DAY_OF_MONTH);
                int day2 = c2.get(java.util.Calendar.DAY_OF_MONTH);
                if (day1 != day2) return Integer.compare(day1, day2);
            }
            int dirCmp = Integer.compare(t1.getDirection(), t2.getDirection());
            if (dirCmp != 0) return dirCmp;
            if (d1 != null && d2 != null) {
                int timeCmp = d1.compareTo(d2);
                if (timeCmp != 0) return timeCmp;
            }
            int tickerCmp = String.valueOf(t1.getTicker()).compareToIgnoreCase(String.valueOf(t2.getTicker()));
            if (tickerCmp != 0) return tickerCmp;
            return Double.compare(Math.abs(t2.getAmount()), Math.abs(t1.getAmount()));
        });

        return result;
    }

    public Vector<Transaction> parseCsvReport(String csvContent) throws Exception {
        // Reset statistics for new parse
        skippedZeroNetEvents = 0;
        skippedZeroNetTickers.clear();
        importedCorporateActionEvents = 0;
        
        Vector<Transaction> transactions = new Vector<>();
        int corporateActionCount = 0;
        ignoredOptionsSummaryRows = 0;
        
    // For IBOrderID consolidation
    Map<String, List<RawExchTradeRow>> orderGroups = new HashMap<>();

        // Corporate actions raw rows (ActionID-based netting)
        List<RawCorporateActionRow> corporateActionRows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    detectColumnIndices(line);
                    currentSectionType = detectSectionType(line);
                    isHeader = false;
                    continue;
                }

                // Handle repeated header sections inside the same CSV
                if (line.startsWith("\"ClientAccountID\"")) {
                    detectColumnIndices(line);
                    currentSectionType = detectSectionType(line);
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    String[] fields = splitCsvLine(line);

                // Ignore options/positions summary section (not executions)
                if (currentSectionType == SectionType.OPTIONS_SUMMARY) {
                    ignoredOptionsSummaryRows++;
                    continue;
                }

                    // Allow importing only corporate actions
                    if (!includeTrades && currentSectionType == SectionType.TRADES) {
                        continue;
                    }

                    // Allow hiding corporate actions from import preview/merge
                    if (!includeCorporateActions && currentSectionType == SectionType.CORPORATE_ACTIONS) {
                        continue;
                    }

                    // Filter by AssetClass for trade section only. Corporate actions must remain visible.
                    // Include ExchTrade, BookTrade, FracShare (all are trade-like).
                    if (currentSectionType == SectionType.TRADES &&
                        COL_TRANSACTION_TYPE >= 0 && COL_TRANSACTION_TYPE < fields.length) {
                        String assetClass = (COL_ASSET_CLASS >= 0 && COL_ASSET_CLASS < fields.length)
                            ? fields[COL_ASSET_CLASS].trim()
                            : "";
                        String txType = fields[COL_TRANSACTION_TYPE].trim();
                        boolean isTradeLike = txType.equals("ExchTrade") || txType.equals("BookTrade") || txType.equals("FracShare");
                        if (isTradeLike && !isAssetClassAllowed(assetClass)) {
                            continue;
                        }
                    }
                    
                    // Check if this is ExchTrade with IBOrderID for grouping
                    if (isExchTradeWithOrderId(fields)) {
                        RawExchTradeRow row = createRawRow(fields);
                        if (row != null) {
                            String ibOrderId = row.ibOrderId;
                            orderGroups.computeIfAbsent(ibOrderId, k -> new ArrayList<>()).add(row);
                        }
                        continue; // Skip adding individual transaction
                    }
                    
                    // Regular parsing for non-groupable rows
                    if (currentSectionType == SectionType.CORPORATE_ACTIONS) {
                        RawCorporateActionRow ca = parseCorporateActionRow(fields);
                        if (ca != null) {
                            corporateActionRows.add(ca);
                        }
                    } else {
                        Transaction transaction = parseCsvLine(line);
                        if (transaction != null) {
                            transactions.add(transaction);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to parse CSV line: " + line + " - " + e.getMessage());
                }
            }
        }

        // Process grouped orders: create consolidated transactions
        for (Map.Entry<String, List<RawExchTradeRow>> entry : orderGroups.entrySet()) {
            List<RawExchTradeRow> group = entry.getValue();
            if (group.size() > 1) { // Only consolidate if multiple fills
                Transaction consolidated = consolidateGroup(group);
                if (consolidated != null) {
                    transactions.add(consolidated);
                    logger.fine("Consolidated " + group.size() + " fills for IBOrderID: " + entry.getKey());
                }
            } else {
                // Single fill: create individual transaction
                RawExchTradeRow single = group.get(0);
                Transaction transaction = createTransactionFromRow(single);
                transactions.add(transaction);
            }
        }

        // Convert corporate actions using ActionID-based netting
        if (includeCorporateActions && !corporateActionRows.isEmpty()) {
            Vector<Transaction> caTx = buildCorporateActionsFromActionIdNetting(corporateActionRows);
            transactions.addAll(caTx);
            corporateActionCount = caTx.size();
        }

        logger.info("Parsed " + transactions.size() + " transactions from IBKR Flex CSV " +
                    "(consolidated " + orderGroups.size() + " order groups, " + corporateActionCount + " corporate actions, " +
                    ignoredOptionsSummaryRows + " ignored options summary rows)");
        
        return transactions;
    }

    private void detectColumnIndices(String headerLine) {
        String[] headers = splitCsvLine(headerLine);

        logger.info("Detecting columns from header with " + headers.length + " fields");
        if (headers.length > 0) {
            logger.fine("First few header fields: " + 
                       (headers.length >= 1 ? "'" + headers[0] + "'" : "") +
                       (headers.length >= 2 ? ", '" + headers[1] + "'" : "") +
                       (headers.length >= 3 ? ", '" + headers[2] + "'" : ""));
        }

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
            // Corporate actions type (RS/TC/IC/TO) - in corporate section "Type" means action type
            else if (h.equals("type")) {
                COL_CA_TYPE = i;
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
            else if (h.equals("actionid") || h.equals("action id")) {
                COL_ACTION_ID = i;
            }
            else if (h.equals("actiondescription") || h.equals("action description")) {
                COL_CA_ACTION_DESCRIPTION = i;
            }
            else if (h.equals("report date")) {
                COL_CA_REPORT_DATE = i;
            }
            // Client Account ID
            else if (h.equals("clientaccountid") || h.equals("client account id") || h.equals("accountid")) {
                COL_CLIENT_ACCOUNT_ID = i;
            }
            // ISIN
            else if (h.equals("isin")) {
                COL_ISIN = i;
            }
            // Asset Class (CRITICAL: if missing, likely wrong header section - skip parsing)
            else if (h.equals("assetclass") || h.equals("asset class") || h.equals("assetcategory")) {
                COL_ASSET_CLASS = i;
            }
            // DateTime (trade timestamp with time component)
            else if (h.equals("datetime") || h.equals("tradedatetime")) {
                COL_DATETIME = i;
            }
            // Corporate actions: Date/Time (note the slash)
            else if (h.equals("date/time") || h.equals("date / time")) {
                COL_CA_DATETIME = i;
            }
            // Transaction ID (prefer column detection over hardcoded index)
            else if (h.equals("transactionid") || h.equals("transaction id") || h.equals("ibexecid")) {
                COL_TRANSACTION_ID = i;
            }
            // IB Order ID (shared across fills, needed for consolidation)
            else if (h.equals("iborderid") || h.equals("ib order id")) {
                COL_IB_ORDER_ID = i;
            }
            // Multiplier (contract size for options/futures)
            else if (h.equals("multiplier")) {
                COL_MULTIPLIER = i;
            }
        }

        columnsDetected = true;
        
        // CRITICAL: AssetClass should always be present in trade data
        // If missing, we're likely reading a different section (summary, positions, etc.)
        if (COL_ASSET_CLASS < 0) {
            logger.warning("AssetClass column not found - likely parsing wrong header section");
            logger.warning("This header may be for account summary, positions, or other non-trade data");
            logger.warning("Trade data headers should include: TradeDate, Symbol, Quantity, AssetClass, etc.");
            // Don't throw exception yet - let validation catch missing required columns
        }
        
        // Log detected columns for debugging
        logger.info("Detected IBKR CSV columns:");
        logger.info("  Date: " + COL_DATE);
        logger.info("  DateTime: " + COL_DATETIME);
        logger.info("  SettlementDate: " + COL_SETTLEMENT_DATE);
        logger.info("  Symbol: " + COL_SYMBOL);
        logger.info("  Name: " + COL_NAME);
        logger.info("  Quantity: " + COL_QUANTITY);
        logger.info("  Price: " + COL_PRICE);
        logger.info("  Currency: " + COL_CURRENCY);
        logger.info("  Commission: " + COL_COMMISSION);
        logger.info("  Buy/Sell: " + COL_BUY_SELL);
        logger.info("  TransactionType: " + COL_TRANSACTION_TYPE);
        logger.info("  Exchange: " + COL_EXCHANGE);
        logger.info("  ClientAccountID: " + COL_CLIENT_ACCOUNT_ID);
        logger.info("  ISIN: " + COL_ISIN);
        logger.info("  AssetClass: " + COL_ASSET_CLASS);
        logger.info("  TransactionID: " + COL_TRANSACTION_ID);
        logger.info("  IBOrderID: " + COL_IB_ORDER_ID);
        logger.info("  Multiplier: " + COL_MULTIPLIER);
        logger.info("  CA Report Date: " + COL_CA_REPORT_DATE);
        logger.info("  CA Date/Time: " + COL_CA_DATETIME);
        logger.info("  CA ActionDescription: " + COL_CA_ACTION_DESCRIPTION);
        logger.info("  CA Type: " + COL_CA_TYPE);
        logger.info("  ActionID: " + COL_ACTION_ID);
        
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

    private boolean isExchTradeWithOrderId(String[] fields) {
        // Check if TransactionType == "ExchTrade"
        if (COL_TRANSACTION_TYPE < 0 || COL_TRANSACTION_TYPE >= fields.length) return false;
        if (!"ExchTrade".equals(fields[COL_TRANSACTION_TYPE].trim())) return false;
        
        // Check if IBOrderID is present and non-empty
        if (COL_IB_ORDER_ID < 0 || COL_IB_ORDER_ID >= fields.length) return false;
        String ibOrderId = fields[COL_IB_ORDER_ID].trim();
        return !ibOrderId.isEmpty();
    }
    
    private RawExchTradeRow createRawRow(String[] fields) {
        try {
            RawExchTradeRow row = new RawExchTradeRow();
            
            // Essential fields
            row.ibOrderId = fields[COL_IB_ORDER_ID].trim();
            row.quantity = parseDouble(fields[COL_QUANTITY]);
            row.price = parseDouble(fields[COL_PRICE]);
            row.commission = (COL_COMMISSION >= 0 && COL_COMMISSION < fields.length)
                ? parseDecimal(fields[COL_COMMISSION])
                : BigDecimal.ZERO; // Keep original sign
            row.exchange = (COL_EXCHANGE >= 0 && COL_EXCHANGE < fields.length) ? fields[COL_EXCHANGE].trim() : "";
            
            // Shared fields from first row
            row.symbol = (COL_SYMBOL >= 0 && COL_SYMBOL < fields.length) ? fields[COL_SYMBOL].trim() : "";
            row.currency = (COL_CURRENCY >= 0 && COL_CURRENCY < fields.length) ? fields[COL_CURRENCY].trim() : "USD";
            row.dateStr = getDateStr(fields);
            row.settlementStr = (COL_SETTLEMENT_DATE >= 0 && COL_SETTLEMENT_DATE < fields.length) ? fields[COL_SETTLEMENT_DATE].trim() : row.dateStr;
            row.buySell = (COL_BUY_SELL >= 0 && COL_BUY_SELL < fields.length) ? fields[COL_BUY_SELL].trim() : "";
            row.assetClass = (COL_ASSET_CLASS >= 0 && COL_ASSET_CLASS < fields.length) ? fields[COL_ASSET_CLASS].trim() : "STK";
            row.description = (COL_NAME >= 0 && COL_NAME < fields.length) ? fields[COL_NAME].trim() : "";
            row.noteParts = buildNoteParts(fields); // For building consolidated note
            
            // Multiplier
            double multiplier = (COL_MULTIPLIER >= 0 && COL_MULTIPLIER < fields.length) 
                ? parseDouble(fields[COL_MULTIPLIER]) : 1.0;
            row.quantity *= multiplier;
            
            return row;
        } catch (Exception e) {
            logger.warning("Failed to create raw row: " + e.getMessage());
            return null;
        }
    }
    
    private String getDateStr(String[] fields) {
        if (COL_DATETIME >= 0 && COL_DATETIME < fields.length && !fields[COL_DATETIME].trim().isEmpty()) {
            return fields[COL_DATETIME];
        } else if (COL_DATE >= 0 && COL_DATE < fields.length) {
            return fields[COL_DATE];
        }
        return "";
    }
    
    private List<String> buildNoteParts(String[] fields) {
        List<String> parts = new ArrayList<>();
        // Build similar to buildNote but collect parts for reuse
        String description = (COL_NAME >= 0 && COL_NAME < fields.length) ? fields[COL_NAME].trim() : "";
        if (!description.isEmpty()) parts.add(description);
        parts.add("Broker:IB");
        
        String accountId = (COL_CLIENT_ACCOUNT_ID >= 0 && COL_CLIENT_ACCOUNT_ID < fields.length) ? fields[COL_CLIENT_ACCOUNT_ID].trim() : "";
        if (!accountId.isEmpty()) parts.add("AccountID:" + accountId);
        
        String isin = (COL_ISIN >= 0 && COL_ISIN < fields.length) ? fields[COL_ISIN].trim() : "";
        if (!isin.isEmpty()) parts.add("ISIN:" + isin);
        
        // TxnID shown in UI: prefer IBOrderID (shared across fills) so consolidated trades have stable ID
        String txnId = "";
        if (COL_IB_ORDER_ID >= 0 && COL_IB_ORDER_ID < fields.length) {
            txnId = fields[COL_IB_ORDER_ID].trim();
        }
        if (txnId.isEmpty() && COL_TRANSACTION_ID >= 0 && COL_TRANSACTION_ID < fields.length) {
            txnId = fields[COL_TRANSACTION_ID].trim();
        }
        if (!txnId.isEmpty()) parts.add("TxnID:" + txnId);
        
        String code = (COL_CODE >= 0 && COL_CODE < fields.length) ? fields[COL_CODE].trim() : "";
        if (!code.isEmpty()) parts.add("Code:" + code);
        
        return parts;
    }
    
    private Transaction consolidateGroup(List<RawExchTradeRow> group) {
        if (group.isEmpty()) return null;
        
        // Sort by date to get earliest
        group.sort(Comparator.comparing(r -> r.dateStr));
        RawExchTradeRow first = group.get(0);
        
        // Direction: assume all same, take from first or quantity sign
        int direction = determineGroupDirection(group, first);
        
        // Totals
        double totalQuantity = 0;
        double totalAbsQuantity = 0;
        double weightedSum = 0;
        BigDecimal commissionSum = BigDecimal.ZERO;
        Set<String> exchanges = new TreeSet<>(); // Sorted unique
        
        for (RawExchTradeRow row : group) {
            totalQuantity += row.quantity;
            double absQ = Math.abs(row.quantity);
            totalAbsQuantity += absQ;
            weightedSum += absQ * row.price;
            // Commission can be negative (cost) or positive (rebate). Match TradeLog: abs(sum(signed)).
            commissionSum = commissionSum.add(row.commission);
            if (!row.exchange.isEmpty()) exchanges.add(row.exchange);
        }
        
        double avgPrice = totalAbsQuantity > 0 ? weightedSum / totalAbsQuantity : 0;
        // Display-friendly rounding: avoid binary floating artifacts.
        // Price precision: keep up to 6 decimals (TradePrice can have 4+ decimals),
        // Fees precision: 6 decimals.
        avgPrice = roundTo(avgPrice, 6);
        double totalFee = normalizeFee(commissionSum.abs()).doubleValue();
        double amount = Math.abs(totalQuantity);
        
        // Dates
        Date tradeDate;
        try {
            tradeDate = parseDate(first.dateStr);
        } catch (Exception e) {
            logger.warning("Failed to parse trade date in consolidation: " + e.getMessage());
            tradeDate = new Date();
        }
        Date settlementDate;
        try {
            settlementDate = parseDate(first.settlementStr);
        } catch (Exception e) {
            logger.warning("Failed to parse settlement date in consolidation: " + e.getMessage());
            settlementDate = tradeDate;
        }
        
        // Exchanges for Trh
        String consolidatedMarket = String.join(",", exchanges);
        
        // Note: use first's parts, but indicate consolidation
        List<String> noteParts = new ArrayList<>(first.noteParts);
        noteParts.add("Consolidated:" + group.size() + "fills");
        String note = String.join("|", noteParts);
        
        try {
            Transaction t = new Transaction(
                0,
                tradeDate,
                direction,
                first.symbol,
                amount,
                avgPrice,
                first.currency,
                totalFee,
                first.currency,
                consolidatedMarket,
                settlementDate,
                note
            );
            // Persisted metadata
            // Persisted metadata is set from parsed fields in buildNoteParts(); note hydration is a fallback only.
            t.setBroker("IB");
            t.hydrateMetadataFromNote();
            return t;
        } catch (Exception e) {
            logger.warning("Failed to create consolidated transaction: " + e.getMessage());
            return null;
        }
    }
    
    private int determineGroupDirection(List<RawExchTradeRow> group, RawExchTradeRow first) {
        // All should have same sign of quantity
        boolean allBuy = group.stream().allMatch(r -> r.quantity > 0);
        boolean allSell = group.stream().allMatch(r -> r.quantity < 0);
        
        if (allBuy) {
            return getDirectionForAsset(first.assetClass, true); // Buy
        } else if (allSell) {
            return getDirectionForAsset(first.assetClass, false); // Sell
        } else {
            logger.warning("Mixed directions in group for IBOrderID: " + first.ibOrderId);
            // Fallback to first row's determination
            return mapTransactionTypeToDirection("ExchTrade", first.buySell, first.assetClass);
        }
    }
    
    private int getDirectionForAsset(String assetClass, boolean isBuy) {
        String upper = assetClass.toUpperCase().trim();
        boolean isDerivative = upper.equals("OPT") || upper.equals("FUT") || upper.equals("FOP") || upper.equals("WAR");
        boolean isCash = upper.equals("CASH") || upper.equals("FX");
        
        if (isBuy) {
            if (isCash) return Transaction.DIRECTION_CBUY;
            if (isDerivative) return Transaction.DIRECTION_DBUY;
            return Transaction.DIRECTION_SBUY;
        } else {
            if (isCash) return Transaction.DIRECTION_CSELL;
            if (isDerivative) return Transaction.DIRECTION_DSELL;
            return Transaction.DIRECTION_SSELL;
        }
    }
    
    private Transaction createTransactionFromRow(RawExchTradeRow row) {
        // Similar to parseCsvLine but for single row
        int direction = getDirectionForAsset(row.assetClass, row.quantity > 0);
        double amount = Math.abs(row.quantity);
        Date tradeDate;
        try {
            tradeDate = parseDate(row.dateStr);
        } catch (Exception e) {
            logger.warning("Failed to parse trade date for single row: " + e.getMessage());
            tradeDate = new Date();
        }
        Date settlementDate;
        try {
            settlementDate = parseDate(row.settlementStr);
        } catch (Exception e) {
            logger.warning("Failed to parse settlement date for single row: " + e.getMessage());
            settlementDate = tradeDate;
        }
        String note = String.join("|", row.noteParts);
        
        try {
            double fee = normalizeFee(row.commission.abs()).doubleValue();
            Transaction t = new Transaction(
                0,
                tradeDate,
                direction,
                row.symbol,
                amount,
                row.price,
                row.currency,
                fee,
                row.currency,
                row.exchange,
                settlementDate,
                note
            );

            t.setBroker("IB");
            t.hydrateMetadataFromNote();
            return t;
        } catch (Exception e) {
            logger.warning("Failed to create single transaction: " + e.getMessage());
            return null;
        }
    }
    
    // Inner class for raw rows (only populated during grouping)
    private static class RawExchTradeRow {
        String ibOrderId;
        double quantity;
        double price;
        BigDecimal commission;
        String exchange;
        String symbol;
        String currency;
        String dateStr;
        String settlementStr;
        String buySell;
        String assetClass;
        String description;
        List<String> noteParts;
    }
    
    private Transaction parseCsvLine(String line) throws Exception {
        String[] fields = splitCsvLine(line);

        if (!columnsDetected) {
            logger.warning("Columns not detected, skipping line");
            return null;
        }

        // Validate array bounds for all required columns
        int maxRequiredIndex = Math.max(COL_DATE, Math.max(COL_SYMBOL, 
                                Math.max(COL_QUANTITY, COL_PRICE)));
        if (fields.length <= maxRequiredIndex) {
            logger.warning("Line has insufficient fields: " + fields.length + 
                          " (need at least " + (maxRequiredIndex + 1) + ")");
            return null;
        }

        // Check if this is a corporate action (reverse split, ticker change, merger, etc.)
        // Corporate action rows have fewer columns (~47 vs 85 for regular trades)
        // Common codes: RS (Reverse Split), TC (Ticker Change/Merger)
        // Also, Symbol column (index 6) is typically "COMMON" instead of actual ticker
        // Note: Corporate actions have their own header section in the CSV, so we use
        // hardcoded indices based on the corporate action CSV structure (after splitCsvLine):
        // Symbol at index 6, Description at 7, Full desc at 8, Code at 38, share change at 33
        if (fields.length < 60 && fields.length > 40) {
            // Might be corporate action - check Code field at index 38 and Symbol at 6
            String codeValue = fields.length > 38 ? fields[38].trim() : "";
            String symbolValue = fields.length > 6 ? fields[6].trim() : "";
            if (codeValue.equals("RS") || codeValue.equals("TC") || symbolValue.equals("COMMON")) {
                // This is a corporate action row - parse differently
                logger.info("Detected corporate action row (Code=" + codeValue + ", Symbol=" + symbolValue + ", fields=" + fields.length + ")");
                return parseCorporateAction(fields);
            }
        }

        // Parse trade date - prefer DateTime (has time component) over Date
        String dateStr;
        if (COL_DATETIME >= 0 && COL_DATETIME < fields.length && !fields[COL_DATETIME].trim().isEmpty()) {
            dateStr = fields[COL_DATETIME];
            logger.fine("Using DateTime column for trade date: " + dateStr);
        } else if (COL_DATE >= 0 && COL_DATE < fields.length) {
            dateStr = fields[COL_DATE];
            logger.fine("Using Date column for trade date: " + dateStr);
        } else {
            logger.warning("No date column found");
            dateStr = "";
        }
        Date tradeDate = parseDate(dateStr);
        
        // Parse settlement date for "datum vypořádání" column
        Date settlementDate = tradeDate; // Default to trade date
        if (COL_SETTLEMENT_DATE >= 0 && COL_SETTLEMENT_DATE < fields.length) {
            String settlementStr = fields[COL_SETTLEMENT_DATE].trim();
            if (!settlementStr.isEmpty()) {
                try {
                    settlementDate = parseDate(settlementStr);
                    logger.fine("Using SettlementDate: " + settlementStr);
                } catch (Exception e) {
                    logger.warning("Failed to parse settlement date: " + settlementStr);
                    // Keep default (trade date)
                }
            }
        }
        
        // Safe access with bounds checking for optional columns
        String transactionType = (COL_TRANSACTION_TYPE >= 0 && COL_TRANSACTION_TYPE < fields.length) 
            ? fields[COL_TRANSACTION_TYPE] : "";
        String buySell = (COL_BUY_SELL >= 0 && COL_BUY_SELL < fields.length) 
            ? fields[COL_BUY_SELL] : "";
        String assetClass = (COL_ASSET_CLASS >= 0 && COL_ASSET_CLASS < fields.length) 
            ? fields[COL_ASSET_CLASS] : "STK";
        
        // Determine direction using AssetClass to distinguish Stock/Derivative/Cash
        int direction = mapTransactionTypeToDirection(transactionType, buySell, assetClass);
        
        // Use full ticker string from Symbol column (preserves option contract details)
        // For stocks: "AMZN"
        // For options: "ORCL  260116C00220000" (full contract string)
        String ticker = (COL_SYMBOL >= 0 && COL_SYMBOL < fields.length) 
            ? fields[COL_SYMBOL].trim() : "";
        
        // Parse raw quantity (may be negative for SELL orders)
        double rawQuantity = parseDouble(fields[COL_QUANTITY]);
        
        // Apply multiplier (contract size for options/futures, typically 1 for stocks)
        double multiplier = (COL_MULTIPLIER >= 0 && COL_MULTIPLIER < fields.length) 
            ? parseDouble(fields[COL_MULTIPLIER]) : 1.0;
        double quantity = rawQuantity * multiplier;
        
        double price = parseDouble(fields[COL_PRICE]);
        
        String currency = (COL_CURRENCY >= 0 && COL_CURRENCY < fields.length) 
            ? fields[COL_CURRENCY].trim() : "USD";
        
        // IBKR sends commissions as negative values (e.g., -1.50 for $1.50 fee)
        // Transaction model expects positive fee values, so we negate/abs them
        // IBKR commission can be negative (cost) or positive (rebate). Match TradeLog: fee = abs(commission).
        BigDecimal commissionSigned = (COL_COMMISSION >= 0 && COL_COMMISSION < fields.length)
            ? parseDecimal(fields[COL_COMMISSION])
            : BigDecimal.ZERO;
        double commission = normalizeFee(commissionSigned.abs()).doubleValue();
        
        String exchange = (COL_EXCHANGE >= 0 && COL_EXCHANGE < fields.length) 
            ? fields[COL_EXCHANGE].trim() : "";
        String code = (COL_CODE >= 0 && COL_CODE < fields.length) 
            ? fields[COL_CODE].trim() : "";

        // If direction is still 0 (unknown), try to infer from quantity sign or Buy/Sell column
        if (direction == 0) {
            if (!buySell.isEmpty()) {
                String buySellLower = buySell.toLowerCase().trim();
                if (buySellLower.equals("buy")) {
                    direction = Transaction.DIRECTION_SBUY;
                } else if (buySellLower.equals("sell")) {
                    direction = Transaction.DIRECTION_SSELL;
                }
            }
            
            // If still unknown, use quantity sign
            if (direction == 0) {
                if (quantity > 0) {
                    direction = Transaction.DIRECTION_SBUY;
                } else if (quantity < 0) {
                    direction = Transaction.DIRECTION_SSELL;
                } else {
                    logger.warning("Cannot determine direction - no valid indicators found");
                    throw new Exception("Bad direction constant: 0");
                }
            }
        }

        double amount = Math.abs(quantity);
        
        // Calculate total for validation/logging (not passed to Transaction)
        // Transaction calculates total internally from price × amount
        double total = 0;
        if (direction > 0) {
            // Buy: total = price × amount
            total = price * amount;
        } else {
            // Sell: use net proceeds if available, otherwise calculate
            total = (COL_NET_PROCEEDS >= 0 && COL_NET_PROCEEDS < fields.length) ? 
                parseDouble(fields[COL_NET_PROCEEDS]) : price * amount;
        }

        Transaction transaction = new Transaction(
                0,
                tradeDate,           // Trade date with time component from DateTime
                direction,
                ticker,
                amount,
                price,
                currency,
                commission,
                currency,
                exchange,
                settlementDate,      // Settlement date for "datum vypořádání"
                buildNote(fields, code)
        );

        // Persisted metadata (independent of note format)
        transaction.setBroker("IB");
        if (COL_CLIENT_ACCOUNT_ID >= 0 && COL_CLIENT_ACCOUNT_ID < fields.length) {
            String acc = fields[COL_CLIENT_ACCOUNT_ID].trim();
            if (!acc.isEmpty()) {
                transaction.setAccountId(acc);
            }
        }
        String txnId = "";
        if (COL_IB_ORDER_ID >= 0 && COL_IB_ORDER_ID < fields.length) {
            txnId = fields[COL_IB_ORDER_ID].trim();
        }
        if (txnId.isEmpty() && COL_TRANSACTION_ID >= 0 && COL_TRANSACTION_ID < fields.length) {
            txnId = fields[COL_TRANSACTION_ID].trim();
        }
        if (!txnId.isEmpty()) {
            transaction.setTxnId(txnId);
        }
        if (code != null && !code.trim().isEmpty()) {
            transaction.setCode(code.trim());
        }

        return transaction;
    }

    /**
     * Parse date from IBKR Flex CSV
     * Supports multiple formats:
     * - Compact DateTime: "20240115;143025" (YYYYMMDD;HHMMSS)
     * - Standard DateTime: "2024-01-15 14:30:25" or "2024-01-15;14:30:25"
     * - Standard date: "2024-01-15"
     * - Compact date: "20240115"
     * 
     * @param dateStr Date string from CSV
     * @return Parsed Date with time component if available
     */
    private Date parseDate(String dateStr) throws Exception {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            logger.warning("Empty date string, using current date");
            return new Date();
        }
        
        String trimmed = dateStr.trim();
        
        // Format 1: Compact DateTime with semicolon (YYYYMMDD;HHMMSS)
        // Example: "20260116;114042"
        if (trimmed.contains(";") && trimmed.matches("\\d{8};\\d{6}")) {
            SimpleDateFormat compactDateTimeFormat = new SimpleDateFormat("yyyyMMdd;HHmmss");
            try {
                return compactDateTimeFormat.parse(trimmed);
            } catch (Exception e) {
                logger.warning("Failed to parse as compact datetime: " + trimmed + " - " + e.getMessage());
                // Fall through to try other formats
            }
        }
        
        // Format 2: Standard DateTime with space or semicolon (YYYY-MM-DD HH:MM:SS or YYYY-MM-DD;HH:MM:SS)
        if ((trimmed.contains(" ") || trimmed.contains(";")) && trimmed.contains("-")) {
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                return dateTimeFormat.parse(trimmed.replace(";", " "));
            } catch (Exception e) {
                logger.warning("Failed to parse as standard datetime: " + trimmed + " - " + e.getMessage());
                // Fall through to try other formats
            }
        }
        
        // Format 3: Compact date format (YYYYMMDD)
        if (trimmed.length() == 8 && trimmed.matches("\\d{8}")) {
            SimpleDateFormat compactFormat = new SimpleDateFormat("yyyyMMdd");
            return compactFormat.parse(trimmed);
        }
        
        // Format 4: Standard date format (YYYY-MM-DD)
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            SimpleDateFormat standardFormat = new SimpleDateFormat("yyyy-MM-dd");
            return standardFormat.parse(trimmed);
        }
        
        throw new Exception("Unrecognized date format: " + trimmed);
    }

    /**
     * Map IBKR transaction type to Transaction direction constant
     * Uses AssetClass to determine if trade is Stock (S), Derivative (D), or Cash (C)
     * 
     * @param txType Transaction type (e.g., "ExchTrade", "Trade")
     * @param buySell Buy/Sell indicator
     * @param assetClass Asset class (STK, OPT, FUT, CASH, FX)
     * @return Transaction direction constant
     */
    private int mapTransactionTypeToDirection(String txType, String buySell, String assetClass) {
        if (txType == null) {
            txType = "";
        }
        
        String typeLower = txType.toLowerCase().trim();
        String buySellLower = (buySell != null) ? buySell.toLowerCase().trim() : "";
        String assetClassUpper = (assetClass != null) ? assetClass.toUpperCase().trim() : "STK";
        
        // Determine transaction category based on AssetClass
        // STK = Stock (Typ CP) → DIRECTION_SBUY/SSELL
        // OPT, FUT, FOP, WAR = Derivatives (Typ Derivát) → DIRECTION_DBUY/DSELL  
        // CASH, FX = Cash/FX → DIRECTION_CBUY/CSELL
        boolean isDerivative = assetClassUpper.equals("OPT") || 
                              assetClassUpper.equals("FUT") || 
                              assetClassUpper.equals("FOP") || 
                              assetClassUpper.equals("WAR");
        boolean isCash = assetClassUpper.equals("CASH") || assetClassUpper.equals("FX");
        
        logger.fine("Direction mapping: txType=" + txType + ", buySell=" + buySell + 
                   ", assetClass=" + assetClass + " → isDerivative=" + isDerivative + 
                   ", isCash=" + isCash);
        
        // Determine buy vs sell
        boolean isBuy = false;
        boolean isSell = false;
        
        // Priority 1: Buy/Sell column (most reliable for ExchTrade)
        if (!buySellLower.isEmpty()) {
            isBuy = buySellLower.equals("buy");
            isSell = buySellLower.equals("sell");
        }
        
        // Priority 2: Transaction type keywords
        if (!isBuy && !isSell) {
            isBuy = typeLower.contains("buy") || typeLower.contains("purchase");
            isSell = typeLower.contains("sell") || typeLower.contains("sale");
        }
        
        // Map to appropriate direction constant
        if (isBuy) {
            if (isCash) return Transaction.DIRECTION_CBUY;
            if (isDerivative) return Transaction.DIRECTION_DBUY;
            return Transaction.DIRECTION_SBUY;
        }
        
        if (isSell) {
            if (isCash) return Transaction.DIRECTION_CSELL;
            if (isDerivative) return Transaction.DIRECTION_DSELL;
            return Transaction.DIRECTION_SSELL;
        }
        
        logger.warning("Could not determine direction from txType='" + txType + 
                      "', buySell='" + buySell + "', assetClass='" + assetClass + "'");
        return 0; // Unknown - will be inferred from quantity sign in parseCsvLine()
    }

    /**
     * Parse corporate action row from IBKR Flex CSV
     * 
     * Handles reverse splits (RS) and ticker changes/mergers (TC).
     * 
     * Corporate action rows have different structure (47 columns vs 85 for regular trades).
     * After our CSV parser handles quoted fields with commas, the indices are:
     * - Column 6: Symbol = "COMMON" (generic marker for corporate actions)
     * - Column 7: Description = actual ticker (e.g., "CODX.OLD", "CS", "UBS")
     * - Column 8: Full description with split/merger details
     * - Column 27: Date
     * - Column 28: DateTime (format: "YYYYMMDD;HHMMSS")
     * - Column 32: Value field (important for TC - cost basis transfer, market value)
     * - Column 33: Quantity/Share change (e.g., "45.6667", "-1370", "66.726")
     * - Column 38: Code = "RS" (Reverse Split) or "TC" (Ticker Change/Merger)
     * 
     * @param fields CSV fields array (after splitCsvLine processing)
     * @return Transaction object (TRANS_ADD or TRANS_SUB) or null if parsing fails
     */
    private Transaction parseCorporateAction(String[] fields) throws Exception {
        logger.fine("Parsing corporate action row with " + fields.length + " fields");
        
        if (fields.length < 40) {
            logger.warning("Corporate action row has too few fields: " + fields.length);
            return null;
        }
        
        // Extract ticker from Description column (index 7)
        // For corporate actions, Symbol column (index 6) is "COMMON", Description has actual ticker
        String originalTicker = fields[7].trim();
        if (originalTicker.isEmpty()) {
            logger.warning("Corporate action has empty ticker in Description column");
            return null;
        }
        
        // Strip .OLD suffix (IBKR internal notation for corporate actions)
        // We need base ticker name to match against historical trades
        String ticker = stripOldSuffix(originalTicker);
        if (!ticker.equals(originalTicker)) {
            logger.fine("Stripped .OLD suffix: " + originalTicker + " → " + ticker);
        }
        
        // Extract share change from Quantity column (index 33)
        // Negative = shares removed (old ticker, e.g., CODX.OLD: -1370)
        // Positive = shares added (new ticker, e.g., CODX: +45.6667)
        double shareChange = parseDouble(fields[33]);
        
        if (shareChange == 0) {
            logger.fine("Skipping corporate action with zero share change for " + ticker);
            return null;
        }
        
        // Extract Value field (column 32) - important for TC (ticker changes/mergers)
        // This represents cost basis transfer or market value at conversion
        // Used for tax reporting purposes
        String valueField = "";
        if (fields.length > 32 && fields[32] != null) {
            valueField = fields[32].trim();
        }
        
        // Parse date from DateTime column (index 28)
        // Format: "20260101;202500" (YYYYMMDD;HHMMSS)
        String dateStr = fields[28].trim();
        if (dateStr.isEmpty() && fields.length > 27) {
            // Fallback to Date column (index 27) if DateTime is empty
            dateStr = fields[27].trim();
        }
        Date actionDate = parseDate(dateStr);
        
        // Determine direction based on share change sign
        int direction;
        double amount;
        
        if (shareChange < 0) {
            // Old ticker: shares being removed
            // Example: CODX.OLD -1370 shares
            direction = Transaction.DIRECTION_TRANS_SUB;
            amount = Math.abs(shareChange); // Store as positive (1370)
        } else {
            // New ticker: shares being added
            // Example: CODX +45.6667 shares
            // Keep full fractional amount to maintain portfolio math integrity
            // The fractional portion will be sold in a separate transaction (Code="LF")
            direction = Transaction.DIRECTION_TRANS_ADD;
            amount = shareChange; // Keep fractional (45.6667, not floor to 45)
        }
        
        // Extract full description for note field (index 8)
        // Example: "CODX.OLD(US1897631057) SPLIT 1 FOR 30 (CODX.OLD, CO-DIAGNOSTICS INC, US1897631057)"
        String fullDescription = "";
        if (fields.length > 8 && fields[8] != null) {
            fullDescription = fields[8].trim();
        }
        
        // Get code column (index 38)
        String code = (fields.length > 38) ? fields[38].trim() : ""; // "RS" or "TC"
        
        // Build note with code prefix, optional value, and full description
        // For TC (ticker changes/mergers), include Value field if present for tax reference
        // For RS (reverse splits), typically no value
        String note;
        if (code.equals("TC") && !valueField.isEmpty() && 
            !valueField.equals("0") && !valueField.equals("0.0")) {
            // TC with value - include for tax reference
            note = code + " (Value: " + valueField + "): " + fullDescription;
        } else {
            // RS or TC without value - standard format
            note = code + ": " + fullDescription;
        }
        
        // Create transformation transaction
        // Price = 0 (transformations don't have transaction prices)
        // Fee = 0 (no commission on corporate actions)
        Transaction t = new Transaction(
            0,                    // serial (will be assigned by TransactionSet)
            actionDate,          // date
            direction,           // TRANS_SUB or TRANS_ADD
            ticker,              // actual ticker from Description column
            amount,              // shares (positive, with fractional for TRANS_ADD)
            0.0,                 // price = 0 for transformations
            "USD",               // currency
            0.0,                 // fee = 0
            "USD",               // fee currency
            "",                  // market (not relevant for corporate actions)
            actionDate,          // executionDate = same as date
            note                 // full description from IBKR
        );
        
        // Log with indication if .OLD was stripped
        String tickerDisplay = ticker;
        if (!ticker.equals(originalTicker)) {
            tickerDisplay = ticker + " (was: " + originalTicker + ")";
        }
        
        logger.info("Parsed corporate action: " + tickerDisplay + " " + 
                    (direction == Transaction.DIRECTION_TRANS_SUB ? "OUT" : "IN") + 
                    " " + amount + " shares (Code: " + code + ")");
        
        return t;
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

    /**
     * Strip .OLD suffix from ticker names used in corporate actions.
     * 
     * IBKR uses .OLD suffix internally to distinguish old vs new tickers
     * in corporate actions (e.g., EVFM.OLD vs EVFM). For StockAccounting,
     * we need to use the base ticker name to match against historical trades.
     * 
     * Examples:
     *   EVFM.OLD  → EVFM
     *   CALA.OLD  → CALA
     *   SYN.OLD   → SYN
     *   EVFM      → EVFM (unchanged)
     * 
     * @param ticker Original ticker from IBKR CSV
     * @return Ticker with .OLD suffix removed
     */
    private String stripOldSuffix(String ticker) {
        if (ticker != null && ticker.endsWith(".OLD")) {
            return ticker.substring(0, ticker.length() - 4);
        }
        return ticker;
    }

    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                // Don't append the quote character itself - it's just a delimiter
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

    /**
     * Build Note field matching IB TradeLog format
     * Format: [Description]|Broker:IB|AccountID:[ID]|ISIN:[ISIN]|TxnID:[ID]|Code:[Code]
     * 
     * Fields are omitted entirely if not present (no empty values)
     * This matches the format used by ImportIBTradeLog for consistency
     * 
     * @param fields CSV fields array
     * @param code Status/notes code from CSV
     * @return Formatted note string
     */
    private String buildNote(String[] fields, String code) {
        StringBuilder note = new StringBuilder();
        
        // 1. Description (company name) - always first, even if empty
        String description = (COL_NAME >= 0 && COL_NAME < fields.length) 
            ? fields[COL_NAME].trim() : "";
        note.append(description);
        
        // 2. Broker identifier - ALWAYS use "IB" for consistency with IB TradeLog
        note.append("|Broker:IB");
        
        // 3. Account ID - skip if not present
        if (COL_CLIENT_ACCOUNT_ID >= 0 && COL_CLIENT_ACCOUNT_ID < fields.length) {
            String accountId = fields[COL_CLIENT_ACCOUNT_ID].trim();
            if (!accountId.isEmpty()) {
                note.append("|AccountID:").append(accountId);
            }
        }
        
        // 4. ISIN - skip if not present (following Trading 212 pattern)
        if (COL_ISIN >= 0 && COL_ISIN < fields.length) {
            String isin = fields[COL_ISIN].trim();
            if (!isin.isEmpty()) {
                note.append("|ISIN:").append(isin);
            }
        }
        
        // 5. Transaction/order ID
        // Prefer IBOrderID so the UI TxnID matches consolidated orders.
        // Fallback to TransactionID (per-fill) and then legacy hardcoded index.
        String txnId = "";
        if (COL_IB_ORDER_ID >= 0 && COL_IB_ORDER_ID < fields.length) {
            txnId = fields[COL_IB_ORDER_ID].trim();
        }
        if (txnId.isEmpty() && COL_TRANSACTION_ID >= 0 && COL_TRANSACTION_ID < fields.length) {
            txnId = fields[COL_TRANSACTION_ID].trim();
        }
        if (txnId.isEmpty() && fields.length > 15) {
            // Fallback to hardcoded index for backward compatibility
            txnId = fields[15].trim();
        }
        if (!txnId.isEmpty()) {
            note.append("|TxnID:").append(txnId);
        }
        
        // 6. Code/Status - skip if not present
        if (code != null && !code.trim().isEmpty()) {
            note.append("|Code:").append(code.trim());
        }
        
        logger.fine("Built note: " + note.toString());
        return note.toString();
    }

    /**
     * Process corporate actions to remove redundant rows and apply time offsets.
     * 
     * Handles two main patterns:
     * 1. RS (Reverse Splits): May have 4 rows with canceling pairs (e.g., EVFM)
     * 2. TC (Ticker Changes): Each ticker is a different asset, keep all
     * 
     * Also ensures:
     * - TRANS_SUB (removals) always come before TRANS_ADD (additions)
     * - Sequential +1 second time offsets to prevent duplicate timestamps
     * - Notes updated to indicate time adjustments
     * 
     * @param transactions All parsed transactions including corporate actions
     * @return Processed transactions with filtering and time offsets applied
     */
    private Vector<Transaction> processCorporateActions(Vector<Transaction> transactions) {
        Vector<Transaction> regularTrades = new Vector<>();
        Vector<Transaction> corporateActions = new Vector<>();
        
        // Separate corporate actions from regular trades
        for (Transaction t : transactions) {
            if (t.getDirection() == Transaction.DIRECTION_TRANS_ADD ||
                t.getDirection() == Transaction.DIRECTION_TRANS_SUB) {
                corporateActions.add(t);
            } else {
                regularTrades.add(t);
            }
        }
        
        if (corporateActions.isEmpty()) {
            return transactions; // No corporate actions to process
        }
        
        logger.info("Processing " + corporateActions.size() + " corporate action transactions");
        
        // Group corporate actions by event (same date and note prefix)
        Map<String, Vector<Transaction>> events = groupCorporateActionsByEvent(corporateActions);
        
        Vector<Transaction> processedCA = new Vector<>();
        int filteredCount = 0;
        
        for (Map.Entry<String, Vector<Transaction>> entry : events.entrySet()) {
            Vector<Transaction> eventTxns = entry.getValue();
            int originalCount = eventTxns.size();
            
            // Determine if this is RS or TC based on note content
            boolean isReversSplit = eventTxns.get(0).getNote().contains("RS:");
            
            Vector<Transaction> filtered;
            if (isReversSplit) {
                filtered = filterReverseSplit(eventTxns);
            } else {
                filtered = filterTickerChange(eventTxns);
            }
            
            filteredCount += (originalCount - filtered.size());
            
            // Check if event has zero net effect (shares sold before corporate action)
            // Only check RS events (TC events rarely have this pattern)
            if (isReversSplit && isZeroNetEvent(filtered)) {
                String ticker = extractTickerFromNote(filtered.get(0).getNote());
                String eventDate = new SimpleDateFormat("yyyy-MM-dd").format(filtered.get(0).getDate());
                
                skippedZeroNetEvents++;
                skippedZeroNetTickers.add(ticker);
                
                // Verbose logging as requested
                logger.info(String.format(
                    "Skipped zero-net RS: %s (filtered %d→%d rows, net: 0.0 shares, date: %s, reason: shares sold before split)",
                    ticker, originalCount, filtered.size(), eventDate
                ));
                
                // Skip adding to result - don't import these transactions
                continue;
            }
            
            // This is a legitimate corporate action - count it
            importedCorporateActionEvents++;
            
            // Sort: SUB before ADD (critical for portfolio calculations)
            sortTransactionsByDirection(filtered);
            
            // Apply time offsets to prevent duplicate timestamps
            applyTimeOffsets(filtered);
            
            processedCA.addAll(filtered);
        }

        // Final ordering for preview/merge stability:
        // Always show OUT (TRANS_SUB) before IN (TRANS_ADD) within the same day.
        // This improves readability in import preview and keeps transformation pairs consistent.
        processedCA.sort((t1, t2) -> {
            Date d1 = t1.getDate();
            Date d2 = t2.getDate();
            if (d1 != null && d2 != null) {
                java.util.Calendar c1 = java.util.Calendar.getInstance();
                java.util.Calendar c2 = java.util.Calendar.getInstance();
                c1.setTime(d1);
                c2.setTime(d2);
                // Compare only by day first
                int y1 = c1.get(java.util.Calendar.YEAR);
                int y2 = c2.get(java.util.Calendar.YEAR);
                if (y1 != y2) return Integer.compare(y1, y2);
                int m1 = c1.get(java.util.Calendar.MONTH);
                int m2 = c2.get(java.util.Calendar.MONTH);
                if (m1 != m2) return Integer.compare(m1, m2);
                int day1 = c1.get(java.util.Calendar.DAY_OF_MONTH);
                int day2 = c2.get(java.util.Calendar.DAY_OF_MONTH);
                if (day1 != day2) return Integer.compare(day1, day2);
            }

            // Same day (or missing dates): OUT before IN
            int dirCmp = Integer.compare(t1.getDirection(), t2.getDirection());
            if (dirCmp != 0) return dirCmp;

            // Same direction: sort by time
            if (d1 != null && d2 != null) {
                int timeCmp = d1.compareTo(d2);
                if (timeCmp != 0) return timeCmp;
            }

            // Stable tie-breakers
            int tickerCmp = String.valueOf(t1.getTicker()).compareToIgnoreCase(String.valueOf(t2.getTicker()));
            if (tickerCmp != 0) return tickerCmp;
            return Double.compare(Math.abs(t2.getAmount()), Math.abs(t1.getAmount()));
        });
        
        if (filteredCount > 0) {
            logger.info("Filtered " + filteredCount + " redundant corporate action rows");
        }
        
        // Merge regular trades and processed corporate actions
        Vector<Transaction> result = new Vector<>();
        result.addAll(regularTrades);
        result.addAll(processedCA);
        
        return result;
    }

    /**
     * Group corporate actions by event (same date and description pattern).
     * 
     * Events are grouped by combining date and the first part of the note
     * to identify transactions that belong to the same corporate action event.
     */
    private Map<String, Vector<Transaction>> groupCorporateActionsByEvent(Vector<Transaction> corporateActions) {
        Map<String, Vector<Transaction>> events = new LinkedHashMap<>();
        
        for (Transaction t : corporateActions) {
            // Create event key from date and note prefix
            String dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(t.getDate());
            String notePrefix = t.getNote().length() > 50 
                ? t.getNote().substring(0, 50) 
                : t.getNote();
            String eventKey = dateStr + "_" + notePrefix;
            
            events.computeIfAbsent(eventKey, k -> new Vector<>()).add(t);
        }
        
        return events;
    }

    /**
     * Filter reverse split transactions to remove canceling pairs.
     * 
     * RS anomalies (like EVFM) may have 4 rows:
     *   +666, -666, -10000, +10000
     * 
     * We detect exact canceling pairs and remove them, keeping only:
     *   -10000 (largest removal), +666 (largest addition)
     * 
     * @param transactions RS transactions for a single event
     * @return Filtered transactions (typically 2: one SUB, one ADD)
     */
    private Vector<Transaction> filterReverseSplit(Vector<Transaction> transactions) {
        // Group by ticker
        Map<String, Vector<Transaction>> byTicker = new HashMap<>();
        for (Transaction t : transactions) {
            byTicker.computeIfAbsent(t.getTicker(), k -> new Vector<>()).add(t);
        }
        
        Vector<Transaction> kept = new Vector<>();
        
        for (Map.Entry<String, Vector<Transaction>> entry : byTicker.entrySet()) {
            Vector<Transaction> tickerTxns = entry.getValue();
            
            Vector<Transaction> positives = new Vector<>();
            Vector<Transaction> negatives = new Vector<>();
            
            for (Transaction t : tickerTxns) {
                if (t.getAmount() > 0) {
                    positives.add(t);
                } else {
                    negatives.add(t);
                }
            }
            
            // Find exact canceling pairs
            Set<Transaction> canceling = new HashSet<>();
            for (Transaction pos : positives) {
                for (Transaction neg : negatives) {
                    if (Math.abs(pos.getAmount() - Math.abs(neg.getAmount())) < 0.0001) {
                        canceling.add(pos);
                        canceling.add(neg);
                    }
                }
            }
            
            // Keep non-canceling rows
            Vector<Transaction> nonCanceling = new Vector<>();
            for (Transaction t : tickerTxns) {
                if (!canceling.contains(t)) {
                    nonCanceling.add(t);
                }
            }
            
            // If everything canceled, use fallback: keep largest absolute values
            if (nonCanceling.isEmpty() && !negatives.isEmpty() && !positives.isEmpty()) {
                Transaction largestNeg = negatives.get(0);
                for (Transaction t : negatives) {
                    if (Math.abs(t.getAmount()) > Math.abs(largestNeg.getAmount())) {
                        largestNeg = t;
                    }
                }
                
                Transaction largestPos = positives.get(0);
                for (Transaction t : positives) {
                    if (t.getAmount() > largestPos.getAmount()) {
                        largestPos = t;
                    }
                }
                
                nonCanceling.add(largestNeg);
                nonCanceling.add(largestPos);
                
                logger.fine("RS fallback for " + entry.getKey() + ": kept largest values");
            }
            
            kept.addAll(nonCanceling);
        }
        
        return kept;
    }

    /**
     * Filter ticker change transactions (keep all rows).
     * 
     * For TC (mergers/conversions), different tickers represent different assets.
     * All rows are meaningful, so we keep them all.
     * 
     * Example: CS→UBS, APGN→PYXS, or complex PLSE multi-asset conversion.
     * 
     * @param transactions TC transactions for a single event
     * @return Same transactions (no filtering for TC)
     */
    private Vector<Transaction> filterTickerChange(Vector<Transaction> transactions) {
        // For TC, each ticker is a different asset
        // Don't filter - all rows are meaningful
        return transactions;
    }

    /**
     * Sort transactions to ensure TRANS_SUB before TRANS_ADD.
     * 
     * Critical for portfolio calculations:
     * 1. First remove old shares (TRANS_SUB)
     * 2. Then add new shares (TRANS_ADD)
     * 
     * Secondary sort by absolute amount (descending) for consistency.
     * 
     * @param transactions Transactions to sort in-place
     */
    private void sortTransactionsByDirection(Vector<Transaction> transactions) {
        transactions.sort((t1, t2) -> {
            int dir1 = t1.getDirection();
            int dir2 = t2.getDirection();
            
            // Primary: SUB (direction -2) before ADD (direction +2)
            // SUB is negative, ADD is positive, so natural sort works
            if (dir1 != dir2) {
                return Integer.compare(dir1, dir2);
            }
            
            // Secondary: larger absolute amounts first
            return Double.compare(Math.abs(t2.getAmount()), Math.abs(t1.getAmount()));
        });
    }

    /**
     * Apply time offsets to prevent duplicate timestamps.
     * 
     * First transaction keeps original time.
     * Each subsequent transaction gets +1, +2, +3... second offset.
     * 
     * Note field is updated with "[Time: +N s]" to indicate adjustment.
     * 
     * @param transactions Transactions to apply time offsets (modified in-place)
     */
    private void applyTimeOffsets(Vector<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return;
        }
        
        // First transaction keeps original timestamp
        Date originalTime = transactions.get(0).getDate();
        
        for (int i = 1; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            
            // Add i seconds to original time
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(originalTime);
            cal.add(java.util.Calendar.SECOND, i);
            
            // Update transaction date and execution date
            Date newTime = cal.getTime();
            t.setDate(newTime);
            t.setExecutionDate(newTime);
            
            // Append time adjustment to note
            String currentNote = t.getNote();
            if (currentNote == null) {
                currentNote = "";
            }
            t.setNote(currentNote + " [Time: +" + i + " s]");
            
            logger.fine("Applied +" + i + " s offset to " + t.getTicker() + 
                       " transaction (new time: " + newTime + ")");
        }
    }
    
    /**
     * Detect if a corporate action event has zero net effect
     * (all transactions cancel out to zero shares)
     * 
     * This occurs when shares were sold before the corporate action date,
     * causing IBKR to generate canceling transactions.
     * 
     * @param transactions Corporate action transactions (after filtering redundant pairs)
     * @return true if net effect is zero for all tickers
     */
    private boolean isZeroNetEvent(Vector<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return true;
        }
        
        // Group by ticker and calculate net share change
        Map<String, Double> netByTicker = new HashMap<>();
        
        for (Transaction t : transactions) {
            String ticker = t.getTicker();
            double amount = t.getAmount();
            
            // SUB (direction=-2): negative amount
            // ADD (direction=+2): positive amount
            if (t.getDirection() == Transaction.DIRECTION_TRANS_SUB) {
                amount = -amount;
            }
            
            netByTicker.merge(ticker, amount, Double::sum);
        }
        
        // Check if ALL tickers net to zero (within epsilon for floating point)
        for (Map.Entry<String, Double> entry : netByTicker.entrySet()) {
            double net = entry.getValue();
            if (Math.abs(net) > 0.001) { // 0.001 shares tolerance
                logger.fine("Ticker " + entry.getKey() + " has non-zero net: " + net);
                return false;
            }
        }
        
        logger.info("Zero-net event detected: All tickers cancel out (net ≈ 0)");
        return true;
    }
    
    /**
     * Extract primary ticker symbol from corporate action note field
     * 
     * Examples:
     * - "RS: EVFM(US30048L1044) SPLIT..." → "EVFM"
     * - "TC: CS(US2254011081) MERGED..." → "CS"
     * 
     * @param note Corporate action note field
     * @return Ticker symbol, or "UNKNOWN" if extraction fails
     */
    private String extractTickerFromNote(String note) {
        if (note == null || note.isEmpty()) {
            return "UNKNOWN";
        }
        
        // Pattern: "RS: TICKER(...)" or "TC: TICKER(...)"
        // Match 2-letter code, colon, whitespace, then ticker (letters/numbers/dots), then open paren
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^[A-Z]{2}:\\s*([A-Z][A-Z0-9.]+)\\(");
        java.util.regex.Matcher matcher = pattern.matcher(note);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Fallback: try to extract first word after colon
        String[] parts = note.split(":");
        if (parts.length > 1) {
            String afterColon = parts[1].trim();
            String[] words = afterColon.split("\\s+");
            if (words.length > 0) {
                // Remove non-alphanumeric except dots
                return words[0].replaceAll("[^A-Z0-9.]", "");
            }
        }
        
        return "UNKNOWN";
    }
    
    /**
     * Get count of zero-net corporate action events skipped in last parse
     * 
     * @return Number of events skipped (each event may have had multiple rows)
     */
    public int getSkippedZeroNetCount() {
        return skippedZeroNetEvents;
    }
    
    /**
     * Get list of ticker symbols with zero-net events skipped in last parse
     * 
     * @return List of ticker symbols (e.g., ["EVFM", "MULN"])
     */
    public List<String> getSkippedZeroNetTickers() {
        return new ArrayList<>(skippedZeroNetTickers);
    }
    
    /**
     * Get count of corporate action events successfully imported in last parse
     * 
     * @return Number of corporate action events imported (each event = 1+ transactions)
     */
    public int getImportedCorporateActionCount() {
        return importedCorporateActionEvents;
    }
}
