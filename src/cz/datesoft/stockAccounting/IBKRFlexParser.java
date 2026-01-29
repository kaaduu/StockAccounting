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

    public enum FlexCsvVersion {
        UNKNOWN,
        V1_LEGACY,
        V2_HEADERS_AND_TRAILERS
    }

    public static final class FlexSection {
        public final String code;
        public final String label;
        public Integer rows; // from EOS when available

        public FlexSection(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    public static final class FlexAccountSections {
        public final String accountId;
        public final java.util.List<FlexSection> sections;
        public final java.util.List<String> missingMandatorySections;

        FlexAccountSections(String accountId,
                            java.util.List<FlexSection> sections,
                            java.util.List<String> missingMandatorySections) {
            this.accountId = accountId;
            this.sections = sections;
            this.missingMandatorySections = missingMandatorySections;
        }
    }

    private FlexCsvVersion flexCsvVersion = FlexCsvVersion.UNKNOWN;
    private final java.util.List<FlexSection> flexSections = new java.util.ArrayList<>();
    private final java.util.Map<String, java.util.List<FlexSection>> flexSectionsByAccount = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, java.util.List<String>> missingMandatoryV2SectionsByAccount = new java.util.LinkedHashMap<>();

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

    // Cash transactions (CTRN) section columns
    private int COL_CTRN_DATETIME = -1;      // "Date/Time"
    private int COL_CTRN_SETTLE_DATE = -1;   // "SettleDate"
    private int COL_CTRN_AMOUNT = -1;        // "Amount"
    private int COL_CTRN_TYPE = -1;          // "Type" (Dividends / Withholding Tax / Payment In Lieu / Other Fees)
    private int COL_CTRN_TRANSACTION_ID = -1; // "TransactionID"

    private void resetDetectedColumns() {
        COL_DATE = -1;
        COL_SETTLEMENT_DATE = -1;
        COL_TRANSACTION_TYPE = -1;
        COL_SYMBOL = -1;
        COL_NAME = -1;
        COL_QUANTITY = -1;
        COL_PRICE = -1;
        COL_CURRENCY = -1;
        COL_PROCEEDS = -1;
        COL_COMMISSION = -1;
        COL_NET_PROCEEDS = -1;
        COL_CODE = -1;
        COL_EXCHANGE = -1;
        COL_BUY_SELL = -1;
        COL_CLIENT_ACCOUNT_ID = -1;
        COL_ISIN = -1;
        COL_ASSET_CLASS = -1;
        COL_DATETIME = -1;
        COL_TRANSACTION_ID = -1;
        COL_IB_ORDER_ID = -1;
        COL_MULTIPLIER = -1;
        COL_CA_REPORT_DATE = -1;
        COL_CA_DATETIME = -1;
        COL_CA_ACTION_DESCRIPTION = -1;
        COL_CA_TYPE = -1;
        COL_ACTION_ID = -1;
        COL_CTRN_DATETIME = -1;
        COL_CTRN_SETTLE_DATE = -1;
        COL_CTRN_AMOUNT = -1;
        COL_CTRN_TYPE = -1;
        COL_CTRN_TRANSACTION_ID = -1;

        COL_FXTR_CLIENT_ACCOUNT_ID = -1;
        COL_FXTR_FX_CURRENCY = -1;
        COL_FXTR_ACTIVITY_DESCRIPTION = -1;
        COL_FXTR_DATETIME = -1;
        COL_FXTR_QUANTITY = -1;
        columnsDetected = false;
    }

    // Statistics tracking (populated during parsing, queryable after parseCsvReport() completes)
    private int skippedZeroNetEvents = 0;
    private List<String> skippedZeroNetTickers = new ArrayList<>();
    private int importedCorporateActionEvents = 0;

    // Detailed content statistics (best-effort; based on raw CSV rows)
    private final java.util.Map<String, Integer> tradeTransactionTypeCounts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> cashTypeSeenCounts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> cashTypeImportedCounts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> cashTypeIgnoredCounts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> cashTypeDisabledCounts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Integer> corporateActionTypeCounts = new java.util.LinkedHashMap<>();

    public FlexCsvVersion getFlexCsvVersion() {
        return flexCsvVersion;
    }

    public java.util.List<FlexSection> getFlexSections() {
        return java.util.Collections.unmodifiableList(flexSections);
    }

    /**
     * v2 only: Missing mandatory sections per account (Trades + Corporate Actions).
     */
    public java.util.Map<String, java.util.List<String>> getMissingMandatoryV2SectionsByAccount() {
        return java.util.Collections.unmodifiableMap(missingMandatoryV2SectionsByAccount);
    }

    public java.util.List<FlexAccountSections> getFlexAccountSections() {
        java.util.List<FlexAccountSections> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.List<FlexSection>> e : flexSectionsByAccount.entrySet()) {
            String accountId = e.getKey();
            java.util.List<FlexSection> secs = e.getValue() == null ? java.util.List.of() : e.getValue();
            java.util.List<String> missing = missingMandatoryV2SectionsByAccount.get(accountId);
            if (missing == null) missing = java.util.List.of();
            out.add(new FlexAccountSections(accountId,
                java.util.Collections.unmodifiableList(secs),
                java.util.Collections.unmodifiableList(missing)));
        }
        return java.util.Collections.unmodifiableList(out);
    }

    private static String normalizeSectionLabel(String label) {
        if (label == null) return "";
        String t = label.trim();
        if (t.isEmpty()) return "";
        int semi = t.indexOf(';');
        if (semi >= 0) {
            t = t.substring(0, semi).trim();
        }
        return t;
    }

    private void validateMandatoryV2Sections() {
        missingMandatoryV2SectionsByAccount.clear();
        if (flexCsvVersion != FlexCsvVersion.V2_HEADERS_AND_TRAILERS) return;

        for (java.util.Map.Entry<String, java.util.List<FlexSection>> e : flexSectionsByAccount.entrySet()) {
            String accountId = e.getKey();
            java.util.List<FlexSection> secs = e.getValue();
            boolean hasTrades = false;
            boolean hasCorporateActions = false;
            if (secs != null) {
                for (FlexSection s : secs) {
                    if (s == null) continue;
                    String code = s.code != null ? s.code.trim() : "";
                    String label = normalizeSectionLabel(s.label);
                    if (!hasTrades && (code.equalsIgnoreCase("TRNT") || label.equalsIgnoreCase("Trades"))) {
                        hasTrades = true;
                    }
                    if (!hasCorporateActions && (code.equalsIgnoreCase("CORP") || label.equalsIgnoreCase("Corporate Actions"))) {
                        hasCorporateActions = true;
                    }
                }
            }
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (!hasTrades) missing.add("Trades");
            if (!hasCorporateActions) missing.add("Corporate Actions");
            if (!missing.isEmpty()) {
                missingMandatoryV2SectionsByAccount.put(accountId, missing);
                logger.warning("IBKR Flex v2: Account " + accountId + " missing mandatory sections: " + missing);
            }
        }
    }

    private static String stripOuterQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        // Handle UTF-8 BOM that may appear at the beginning of the first token
        if (!t.isEmpty() && t.charAt(0) == '\uFEFF') {
            t = t.substring(1);
        }
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    // Static helpers for lightweight, best-effort parsing outside of an instance.
    // (Used by ImportWindow "Detaily..." legacy section summary.)
    public static String[] splitCsvLineStatic(String line) {
        if (line == null) return new String[0];
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    public static String controlTypeStatic(String[] fields) {
        if (fields == null || fields.length == 0) return null;
        return stripOuterQuotes(fields[0]);
    }

    private static boolean isFlexControlLine(String[] fields) {
        if (fields == null || fields.length == 0) return false;
        String f0 = stripOuterQuotes(fields[0]);
        if (f0 == null) return false;
        return f0.equals("BOF") || f0.equals("BOA") || f0.equals("BOS") || f0.equals("EOS") || f0.equals("EOA") || f0.equals("EOF");
    }

    private static String controlType(String[] fields) {
        if (fields == null || fields.length == 0) return null;
        return stripOuterQuotes(fields[0]);
    }

    private void noteFlexControlLine(String[] fields) {
        String type = controlType(fields);
        if (type == null) return;
        if (type.equals("BOF") || type.equals("BOA") || type.equals("EOA") || type.equals("EOF")) {
            // ignore
            return;
        }
        if (type.equals("BOS")) {
            String code = (fields.length > 1) ? stripOuterQuotes(fields[1]) : "";
            String label = (fields.length > 2) ? stripOuterQuotes(fields[2]) : "";
            if (code == null) code = "";
            if (label == null) label = "";
            flexSections.add(new FlexSection(code, label));
            return;
        }
        if (type.equals("EOS")) {
            // EOS,"CODE","rowCount",...
            String code = (fields.length > 1) ? stripOuterQuotes(fields[1]) : null;
            Integer rows = null;
            if (fields.length > 2) {
                try {
                    String n = stripOuterQuotes(fields[2]);
                    if (n != null && !n.isEmpty()) rows = Integer.parseInt(n);
                } catch (Exception ignored) {
                }
            }
            if (code != null && rows != null) {
                for (int i = flexSections.size() - 1; i >= 0; i--) {
                    FlexSection s = flexSections.get(i);
                    if (s != null && code.equalsIgnoreCase(s.code) && s.rows == null) {
                        s.rows = rows;
                        break;
                    }
                }
            }
        }
    }

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
        CASH_TRANSACTIONS,
        FXTR,
        ACCOUNT_INFO,
        UNKNOWN
    }

    private SectionType currentSectionType = SectionType.UNKNOWN;
    private int ignoredOptionsSummaryRows = 0;

    private boolean includeCorporateActions = true;

    private boolean includeTrades = true;

    // Cash transactions (CTRN) inclusion (dividends/withholding/fees)
    private boolean includeCashTransactions = true;

    // Fallback: some v2 exports place dividend brutto rows into FXTR instead of CTRN.
    private boolean enableFxtrDividendFallback = true;

    // FXTR (Forex P/L Details) section columns
    private int COL_FXTR_CLIENT_ACCOUNT_ID = -1;
    private int COL_FXTR_FX_CURRENCY = -1;
    private int COL_FXTR_ACTIVITY_DESCRIPTION = -1;
    private int COL_FXTR_DATETIME = -1;
    private int COL_FXTR_QUANTITY = -1;

    private static class RawFxtrDividendRow {
        String accountId;
        String dateTimeStr;
        String fxCurrency;
        String activityDescription;
        double quantity;
    }

    private final java.util.List<RawFxtrDividendRow> fxtrDividendRows = new java.util.ArrayList<>();
    private final java.util.Set<String> accountsWithCtrnDividendBrutto = new java.util.HashSet<>();
    private int fxtrDividendFallbackCount = 0;
    private final java.util.Set<String> fxtrDividendFallbackAccounts = new java.util.HashSet<>();

    private void resetFxtrDetectedColumns() {
        COL_FXTR_CLIENT_ACCOUNT_ID = -1;
        COL_FXTR_FX_CURRENCY = -1;
        COL_FXTR_ACTIVITY_DESCRIPTION = -1;
        COL_FXTR_DATETIME = -1;
        COL_FXTR_QUANTITY = -1;
    }

    private void detectFxtrColumnIndices(String headerLine) {
        resetFxtrDetectedColumns();
        String[] headers = splitCsvLine(headerLine);
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i] == null ? "" : headers[i].trim().toLowerCase();
            if (h.equals("clientaccountid") || h.equals("client account id") || h.equals("accountid")) {
                COL_FXTR_CLIENT_ACCOUNT_ID = i;
            } else if (h.equals("fxcurrency") || h.equals("fx currency")) {
                COL_FXTR_FX_CURRENCY = i;
            } else if (h.equals("activitydescription") || h.equals("activity description")) {
                COL_FXTR_ACTIVITY_DESCRIPTION = i;
            } else if (h.equals("datetime")) {
                COL_FXTR_DATETIME = i;
            } else if (h.equals("quantity")) {
                COL_FXTR_QUANTITY = i;
            }
        }
    }

    // v2 ACCT section (account/owner details)
    private String[] currentAcctHeaders = null;
    private final java.util.Map<String, java.util.Map<String, String>> accountInfoByAccountId = new java.util.LinkedHashMap<>();

    // IBOrderID consolidation stats
    private int ibOrderGroupCount = 0;
    private int ibOrderConsolidatedGroupCount = 0;
    private int ibOrderConsolidatedFillCount = 0;

    private java.util.Set<String> allowedCorporateActionTypes = null; // null => allow all

    public void setIncludeCorporateActions(boolean includeCorporateActions) {
        this.includeCorporateActions = includeCorporateActions;
    }

    public void setIncludeTrades(boolean includeTrades) {
        this.includeTrades = includeTrades;
    }

    public void setIncludeCashTransactions(boolean includeCashTransactions) {
        this.includeCashTransactions = includeCashTransactions;
    }

    public void setEnableFxtrDividendFallback(boolean enableFxtrDividendFallback) {
        this.enableFxtrDividendFallback = enableFxtrDividendFallback;
    }

    public int getFxtrDividendFallbackCount() {
        return fxtrDividendFallbackCount;
    }

    public java.util.Set<String> getFxtrDividendFallbackAccounts() {
        return java.util.Collections.unmodifiableSet(fxtrDividendFallbackAccounts);
    }

    public boolean isIncludeCashTransactions() {
        return includeCashTransactions;
    }

    public java.util.Map<String, java.util.Map<String, String>> getAccountInfoByAccountId() {
        java.util.Map<String, java.util.Map<String, String>> out = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.Map<String, String>> e : accountInfoByAccountId.entrySet()) {
            out.put(e.getKey(), java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(e.getValue())));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    public int getIbOrderGroupCount() {
        return ibOrderGroupCount;
    }

    public int getIbOrderConsolidatedGroupCount() {
        return ibOrderConsolidatedGroupCount;
    }

    public int getIbOrderConsolidatedFillCount() {
        return ibOrderConsolidatedFillCount;
    }

    public java.util.Map<String, Integer> getTradeTransactionTypeCounts() {
        return java.util.Collections.unmodifiableMap(tradeTransactionTypeCounts);
    }

    public java.util.Map<String, Integer> getCashTypeSeenCounts() {
        return java.util.Collections.unmodifiableMap(cashTypeSeenCounts);
    }

    public java.util.Map<String, Integer> getCashTypeImportedCounts() {
        return java.util.Collections.unmodifiableMap(cashTypeImportedCounts);
    }

    public java.util.Map<String, Integer> getCashTypeIgnoredCounts() {
        return java.util.Collections.unmodifiableMap(cashTypeIgnoredCounts);
    }

    public java.util.Map<String, Integer> getCashTypeDisabledCounts() {
        return java.util.Collections.unmodifiableMap(cashTypeDisabledCounts);
    }

    public java.util.Map<String, Integer> getCorporateActionTypeCounts() {
        return java.util.Collections.unmodifiableMap(corporateActionTypeCounts);
    }

    public int getIgnoredOptionsSummaryRows() {
        return ignoredOptionsSummaryRows;
    }

    public int getFxtrDividendCandidateCount() {
        return fxtrDividendRows.size();
    }

    private static void incCount(java.util.Map<String, Integer> map, String key) {
        if (map == null) return;
        if (key == null) return;
        String k = key.trim();
        if (k.isEmpty()) return;
        map.put(k, map.getOrDefault(k, 0) + 1);
    }

    private enum CashRowOutcome {
        IMPORTED,
        DISABLED,
        IGNORED
    }

    private CashRowOutcome classifyCashRow(String type, String ticker, String description, double amount) {
        if (type == null || type.trim().isEmpty()) return CashRowOutcome.IGNORED;
        if (amount == 0.0) return CashRowOutcome.IGNORED;

        String t = type.trim();
        String tick = ticker == null ? "" : ticker.trim();
        String desc = description == null ? "" : description.trim();

        // Some withholding tax / interest rows are not tied to a symbol.
        if (tick.isEmpty()) {
            String descLower = desc.toLowerCase();
            boolean looksLikeInterest = descLower.contains("credit int") || descLower.contains("interest");

            if (t.equalsIgnoreCase("Withholding Tax") && looksLikeInterest) {
                tick = "Kreditni.Urok";
            } else if (t.equalsIgnoreCase("Withholding Tax")) {
                // Legacy behavior: tax without symbol.
                tick = "CASH.internal";
            } else if (t.equalsIgnoreCase("Broker Interest Received")) {
                tick = "Kreditni.Urok";
            } else if (t.equalsIgnoreCase("Broker Interest Paid")) {
                tick = "Debetni.Urok";
            } else if (t.equalsIgnoreCase("Broker Fees")) {
                tick = "Debetni.Urok";
            }

            if (tick.isEmpty()) {
                // Do not import cash rows that we cannot attribute.
                return CashRowOutcome.IGNORED;
            }
        }

        if (t.equalsIgnoreCase("Dividends") || t.equalsIgnoreCase("Payment In Lieu Of Dividends")) {
            return CashRowOutcome.IMPORTED;
        }
        if (t.equalsIgnoreCase("Withholding Tax")) {
            return CashRowOutcome.IMPORTED;
        }
        if (t.equalsIgnoreCase("Other Fees")) {
            return CashRowOutcome.IMPORTED;
        }
        if (t.equalsIgnoreCase("Broker Interest Received")) {
            return CashRowOutcome.IMPORTED;
        }
        if (t.equalsIgnoreCase("Broker Interest Paid")) {
            return CashRowOutcome.DISABLED;
        }
        if (t.equalsIgnoreCase("Broker Fees")) {
            return CashRowOutcome.DISABLED;
        }

        return CashRowOutcome.IGNORED;
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
        boolean hasTransactionType = lower.contains("\"transactiontype\"") || lower.contains(",transactiontype,") || lower.contains("transactiontype,") || lower.contains(",transactiontype");
        boolean hasTradePrice = lower.contains("\"tradeprice\"") || lower.contains(",tradeprice,") || lower.contains("tradeprice,") || lower.contains(",tradeprice");
        boolean hasIbOrderId = lower.contains("\"iborderid\"") || lower.contains(",iborderid,") || lower.contains("iborderid,") || lower.contains(",iborderid");
        boolean hasExchange = lower.contains("\"exchange\"") || lower.contains(",exchange,") || lower.contains("exchange,") || lower.contains(",exchange");
        if (hasTransactionType && hasTradePrice && hasIbOrderId && hasExchange) {
            return SectionType.TRADES;
        }

        // Options/positions summary section (note the spaces in column names)
        boolean hasTransactionTypeSpaced = lower.contains("\"transaction type\"") || lower.contains("transaction type");
        boolean hasTradePriceSpaced = lower.contains("\"trade price\"") || lower.contains("trade price");
        if (hasTransactionTypeSpaced && hasTradePriceSpaced && !hasIbOrderId) {
            return SectionType.OPTIONS_SUMMARY;
        }

        // Corporate actions section
        boolean hasActionDescription = lower.contains("\"actiondescription\"") || lower.contains("actiondescription");
        boolean hasActionId = lower.contains("\"actionid\"") || lower.contains("actionid");
        if (hasActionDescription && hasActionId) {
            return SectionType.CORPORATE_ACTIONS;
        }

        // Cash transactions section (dividends, withholding tax, fees, interest)
        boolean hasAvailableForTradingDate = lower.contains("availablefortradingdate") || lower.contains("available for trading date");
        boolean hasSettleDate = lower.contains("settledate") || lower.contains("settle date");
        boolean hasAmount = lower.contains(",amount,") || lower.contains("\"amount\"") || lower.contains("amount,") || lower.contains(",amount");
        boolean hasDateTimeSlash = lower.contains("date/time") || lower.contains("date / time");
        boolean hasTransactionId = lower.contains("transactionid") || lower.contains("transaction id");
        boolean hasType = lower.contains(",type,") || lower.contains("\"type\"") || lower.contains("type,") || lower.contains(",type");
        // CTRN appears in multiple Flex variants:
        // - legacy: includes AvailableForTradingDate
        // - newer: may omit AvailableForTradingDate but still includes TransactionID/Type/Amount
        if (hasDateTimeSlash && hasSettleDate && hasAmount && (hasAvailableForTradingDate || (hasType && hasTransactionId))) {
            return SectionType.CASH_TRANSACTIONS;
        }

        // FXTR (Forex P/L Details) section
        boolean hasFxCurrency = lower.contains("fxcurrency") || lower.contains("fx currency");
        boolean hasActivityDescription = lower.contains("activitydescription") || lower.contains("activity description");
        boolean hasReportDate = lower.contains("reportdate") || lower.contains("report date");
        boolean hasFunctionalCurrency = lower.contains("functionalcurrency") || lower.contains("functional currency");
        boolean hasRealizedPl = lower.contains("realizedp/l") || lower.contains("realized p/l") || lower.contains("realizedpl");
        if (hasFxCurrency && hasActivityDescription && hasReportDate && hasFunctionalCurrency && hasRealizedPl) {
            return SectionType.FXTR;
        }

        return SectionType.UNKNOWN;
    }

    private void detectAccountInfoHeaders(String headerLine) {
        currentAcctHeaders = splitCsvLine(headerLine);
    }

    private void parseAccountInfoRow(String currentAccountId, String[] fields) {
        if (currentAcctHeaders == null || fields == null) return;

        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        int n = Math.min(currentAcctHeaders.length, fields.length);
        for (int i = 0; i < n; i++) {
            String k = currentAcctHeaders[i] != null ? currentAcctHeaders[i].trim() : "";
            if (k.isEmpty()) continue;
            String v = fields[i] != null ? fields[i].trim() : "";
            if (v.isEmpty()) continue;
            m.put(k, v);
        }
        if (m.isEmpty()) return;

        String acc = null;
        if (m.containsKey("ClientAccountID")) {
            acc = m.get("ClientAccountID");
        } else if (m.containsKey("AccountId")) {
            acc = m.get("AccountId");
        }
        if (acc == null || acc.trim().isEmpty()) {
            acc = currentAccountId;
        }
        if (acc == null) acc = "";
        acc = acc.trim();
        if (acc.isEmpty()) return;

        accountInfoByAccountId.putIfAbsent(acc, m);
    }

    private static class RawCorporateActionRow {
        String actionId;
        String accountId;
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
            row.accountId = (COL_CLIENT_ACCOUNT_ID >= 0 && COL_CLIENT_ACCOUNT_ID < fields.length)
                ? fields[COL_CLIENT_ACCOUNT_ID].trim()
                : "";
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

            // Keep note pipe-delimited and store ActionID in the dedicated TxnID column.
            String notePrefix = type + "|" + desc;
            if (!origSymbols.isEmpty()) {
                notePrefix += "|OrigSymbols:" + String.join(",", origSymbols);
            }

            String accountId = grp.get(0).accountId;
            if (accountId == null) accountId = "";
            accountId = accountId.trim();

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
                    if (!accountId.isBlank()) {
                        t.setAccountId(accountId);
                    }
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
                    if (!accountId.isBlank()) {
                        t.setAccountId(accountId);
                    }
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

        tradeTransactionTypeCounts.clear();
        cashTypeSeenCounts.clear();
        cashTypeImportedCounts.clear();
        cashTypeIgnoredCounts.clear();
        cashTypeDisabledCounts.clear();
        corporateActionTypeCounts.clear();
        flexCsvVersion = FlexCsvVersion.UNKNOWN;
        flexSections.clear();
        flexSectionsByAccount.clear();
        missingMandatoryV2SectionsByAccount.clear();
        resetDetectedColumns();

        ibOrderGroupCount = 0;
        ibOrderConsolidatedGroupCount = 0;
        ibOrderConsolidatedFillCount = 0;
        accountInfoByAccountId.clear();
        currentAcctHeaders = null;
        
        Vector<Transaction> transactions = new Vector<>();
        int corporateActionCount = 0;
        ignoredOptionsSummaryRows = 0;
        fxtrDividendRows.clear();
        accountsWithCtrnDividendBrutto.clear();
        fxtrDividendFallbackCount = 0;
        fxtrDividendFallbackAccounts.clear();
        
    // For IBOrderID consolidation
    Map<String, List<RawExchTradeRow>> orderGroups = new HashMap<>();

        // Corporate actions raw rows (ActionID-based netting)
        List<RawCorporateActionRow> corporateActionRows = new ArrayList<>();

        // v2: track current account and current section context
        String currentAccountId = "";
        boolean inV2 = false;
        String currentV2SectionCode = "";

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            boolean headerDetected = false;
            boolean v2ExpectSectionHeader = false;

            while ((line = reader.readLine()) != null) {
                if (line == null) continue;
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fieldsForControl = splitCsvLine(line);
                if (fieldsForControl != null && fieldsForControl.length > 0) {
                    String ctl = controlType(fieldsForControl);
                    if (ctl != null && (ctl.equals("BOF") || ctl.equals("BOA") || ctl.equals("BOS") || ctl.equals("EOS") || ctl.equals("EOA") || ctl.equals("EOF"))) {
                        if (flexCsvVersion == FlexCsvVersion.UNKNOWN) {
                            flexCsvVersion = FlexCsvVersion.V2_HEADERS_AND_TRAILERS;
                            inV2 = true;
                        } else if (flexCsvVersion == FlexCsvVersion.V1_LEGACY) {
                            throw new Exception("IBKR Flex CSV: mixed versions detected (legacy header CSV + BOF/BOS trailer records)");
                        }

                        if (ctl.equals("BOA")) {
                            currentAccountId = (fieldsForControl.length > 1) ? stripOuterQuotes(fieldsForControl[1]) : "";
                            if (currentAccountId == null) currentAccountId = "";
                            currentAccountId = currentAccountId.trim();
                            flexSectionsByAccount.computeIfAbsent(currentAccountId, k -> new java.util.ArrayList<>());
                        }
                        if (ctl.equals("BOS")) {
                            String code = (fieldsForControl.length > 1) ? stripOuterQuotes(fieldsForControl[1]) : "";
                            String label = (fieldsForControl.length > 2) ? stripOuterQuotes(fieldsForControl[2]) : "";
                            if (code == null) code = "";
                            if (label == null) label = "";
                            code = code.trim();
                            label = label.trim();
                            FlexSection s = new FlexSection(code, label);
                            flexSectionsByAccount.computeIfAbsent(currentAccountId, k -> new java.util.ArrayList<>()).add(s);
                        }

                        noteFlexControlLine(fieldsForControl);
                        if (ctl.equals("BOS")) {
                            currentV2SectionCode = (fieldsForControl.length > 1) ? stripOuterQuotes(fieldsForControl[1]) : "";
                            if (currentV2SectionCode == null) currentV2SectionCode = "";
                            currentV2SectionCode = currentV2SectionCode.trim();
                            v2ExpectSectionHeader = true;
                            // Next non-empty line should be the section header.
                        }
                        if (ctl.equals("EOS")) {
                            // Section ended; next parsed section must re-detect columns.
                            headerDetected = false;
                            v2ExpectSectionHeader = false;
                            currentSectionType = SectionType.UNKNOWN;
                            resetDetectedColumns();
                            currentAcctHeaders = null;
                            currentV2SectionCode = "";
                        }
                        continue;
                    }
                }

                // If we got here we have a non-control line.
                if (!inV2 && flexCsvVersion == FlexCsvVersion.UNKNOWN) {
                    // Decide based on the first meaningful non-control line.
                    if (line.startsWith("\"ClientAccountID\"")) {
                        flexCsvVersion = FlexCsvVersion.V1_LEGACY;
                    } else {
                        flexCsvVersion = FlexCsvVersion.V1_LEGACY;
                    }
                } else if (inV2) {
                    // In v2, allow unquoted headers as well.
                    if (!headerDetected && !v2ExpectSectionHeader &&
                        (line.startsWith("\"ClientAccountID\"") || line.startsWith("ClientAccountID,"))) {
                        // Header-like line without BOS marker => likely mixed or malformed.
                        throw new Exception("IBKR Flex CSV: malformed v2 file (section header without BOS marker)");
                    }
                }

                // Header handling
                if (!headerDetected) {
                    if (flexCsvVersion == FlexCsvVersion.V2_HEADERS_AND_TRAILERS && !v2ExpectSectionHeader) {
                        // In v2 we must wait for BOS before the first header.
                        continue;
                    }
                    currentSectionType = detectSectionType(line);
                    if (inV2 && currentSectionType == SectionType.UNKNOWN) {
                        // v2 can contain arbitrary sections (ACCT/POST/CTRN/...) that we do not parse.
                        if ("ACCT".equalsIgnoreCase(currentV2SectionCode)) {
                            currentSectionType = SectionType.ACCOUNT_INFO;
                            detectAccountInfoHeaders(line);
                            headerDetected = true;
                            v2ExpectSectionHeader = false;
                            continue;
                        }
                        headerDetected = true;
                        v2ExpectSectionHeader = false;
                        continue;
                    }

                    if (currentSectionType == SectionType.FXTR) {
                        detectFxtrColumnIndices(line);
                        validateRequiredColumnsForSection(currentSectionType);
                        headerDetected = true;
                        v2ExpectSectionHeader = false;
                        continue;
                    }

                    if (currentSectionType == SectionType.ACCOUNT_INFO) {
                        detectAccountInfoHeaders(line);
                        headerDetected = true;
                        v2ExpectSectionHeader = false;
                        continue;
                    }
                    detectColumnIndices(line);
                    validateRequiredColumnsForSection(currentSectionType);
                    headerDetected = true;
                    v2ExpectSectionHeader = false;
                    continue;
                }

                // Handle repeated header sections inside the same CSV
                if (line.startsWith("\"ClientAccountID\"") || (inV2 && line.startsWith("ClientAccountID,"))) {
                    currentSectionType = detectSectionType(line);
                    if (inV2 && currentSectionType == SectionType.UNKNOWN) {
                        // v2 can contain arbitrary sections (ACCT/POST/CTRN/...) that we do not parse.
                        // For ACCT, reuse the BOS section code context.
                        if ("ACCT".equalsIgnoreCase(currentV2SectionCode)) {
                            currentSectionType = SectionType.ACCOUNT_INFO;
                            detectAccountInfoHeaders(line);
                            v2ExpectSectionHeader = false;
                            continue;
                        }
                        v2ExpectSectionHeader = false;
                        continue;
                    }

                    if (currentSectionType == SectionType.FXTR) {
                        detectFxtrColumnIndices(line);
                        validateRequiredColumnsForSection(currentSectionType);
                        v2ExpectSectionHeader = false;
                        continue;
                    }
                    if (currentSectionType == SectionType.ACCOUNT_INFO) {
                        detectAccountInfoHeaders(line);
                        v2ExpectSectionHeader = false;
                        continue;
                    }
                    detectColumnIndices(line);
                    validateRequiredColumnsForSection(currentSectionType);
                    v2ExpectSectionHeader = false;
                    continue;
                }

                try {
                    String[] fields = fieldsForControl;

                // Ignore options/positions summary section (not executions)
                if (currentSectionType == SectionType.OPTIONS_SUMMARY) {
                    ignoredOptionsSummaryRows++;
                    continue;
                }

                // v2: ignore unsupported sections entirely
                if (inV2 && currentSectionType == SectionType.UNKNOWN) {
                    continue;
                }

                if (currentSectionType == SectionType.ACCOUNT_INFO) {
                    parseAccountInfoRow(currentAccountId, fieldsForControl);
                    continue;
                }

                    // Collect raw content stats (best-effort, independent of import filters)
                    if (currentSectionType == SectionType.TRADES) {
                        if (COL_TRANSACTION_TYPE >= 0 && COL_TRANSACTION_TYPE < fields.length) {
                            String txType = fields[COL_TRANSACTION_TYPE] != null ? fields[COL_TRANSACTION_TYPE].trim() : "";
                            if (!txType.isEmpty()) {
                                incCount(tradeTransactionTypeCounts, txType);
                            }
                        }
                    } else if (currentSectionType == SectionType.CORPORATE_ACTIONS) {
                        if (COL_CA_TYPE >= 0 && COL_CA_TYPE < fields.length) {
                            String caType = fields[COL_CA_TYPE] != null ? fields[COL_CA_TYPE].trim() : "";
                            if (!caType.isEmpty()) {
                                incCount(corporateActionTypeCounts, caType);
                            }
                        }
                    } else if (currentSectionType == SectionType.CASH_TRANSACTIONS) {
                        String cashType = (COL_CTRN_TYPE >= 0 && COL_CTRN_TYPE < fields.length)
                            ? (fields[COL_CTRN_TYPE] != null ? fields[COL_CTRN_TYPE].trim() : "")
                            : "";
                        if (!cashType.isEmpty()) {
                            incCount(cashTypeSeenCounts, cashType);
                            String tick = (COL_SYMBOL >= 0 && COL_SYMBOL < fields.length)
                                ? (fields[COL_SYMBOL] != null ? fields[COL_SYMBOL].trim() : "")
                                : "";
                            String desc = (COL_NAME >= 0 && COL_NAME < fields.length)
                                ? (fields[COL_NAME] != null ? fields[COL_NAME].trim() : "")
                                : "";
                            double amt = (COL_CTRN_AMOUNT >= 0 && COL_CTRN_AMOUNT < fields.length)
                                ? parseDouble(fields[COL_CTRN_AMOUNT])
                                : 0.0;
                            CashRowOutcome outcome = classifyCashRow(cashType, tick, desc, amt);
                            if (outcome == CashRowOutcome.IMPORTED) {
                                incCount(cashTypeImportedCounts, cashType);
                            } else if (outcome == CashRowOutcome.DISABLED) {
                                incCount(cashTypeDisabledCounts, cashType);
                            } else {
                                incCount(cashTypeIgnoredCounts, cashType);
                            }
                        }
                    }

                    // Allow importing only corporate actions
                    if (!includeTrades && currentSectionType == SectionType.TRADES) {
                        continue;
                    }

                    if (!includeCashTransactions && currentSectionType == SectionType.CASH_TRANSACTIONS) {
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
                            String key = inV2 ? (currentAccountId + "|" + ibOrderId) : ibOrderId;
                            orderGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                        }
                        continue; // Skip adding individual transaction
                    }
                    
                    // Regular parsing for non-groupable rows
                    if (currentSectionType == SectionType.CORPORATE_ACTIONS) {
                        RawCorporateActionRow ca = parseCorporateActionRow(fields);
                        if (ca != null) {
                            if (inV2 && (ca.accountId == null || ca.accountId.trim().isEmpty())) {
                                ca.accountId = currentAccountId;
                            }
                            corporateActionRows.add(ca);
                        }
                    } else {
                        Transaction transaction = parseCsvLine(line);
                        if (transaction != null) {
                            if (inV2 && (transaction.getAccountId() == null || transaction.getAccountId().trim().isEmpty())) {
                                transaction.setAccountId(currentAccountId);
                            }
                            transactions.add(transaction);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to parse CSV line: " + line + " - " + e.getMessage());
                }
            }
        }

        // Process grouped orders: create consolidated transactions
        ibOrderGroupCount = orderGroups.size();
        for (Map.Entry<String, List<RawExchTradeRow>> entry : orderGroups.entrySet()) {
            List<RawExchTradeRow> group = entry.getValue();
            if (group.size() > 1) { // Only consolidate if multiple fills
                ibOrderConsolidatedGroupCount++;
                ibOrderConsolidatedFillCount += group.size();
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
            if (flexCsvVersion == FlexCsvVersion.V2_HEADERS_AND_TRAILERS) {
                // v2: group by accountId|ActionID to avoid cross-account netting.
                Map<String, List<RawCorporateActionRow>> byAccAndAction = new LinkedHashMap<>();
                for (RawCorporateActionRow r : corporateActionRows) {
                    String acc = r.accountId == null ? "" : r.accountId.trim();
                    String key = acc + "|" + r.actionId;
                    byAccAndAction.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
                }
                Vector<Transaction> caTx = new Vector<>();
                for (List<RawCorporateActionRow> grp : byAccAndAction.values()) {
                    caTx.addAll(buildCorporateActionsFromActionIdNetting(grp));
                }
                transactions.addAll(caTx);
                corporateActionCount = caTx.size();
            } else {
                Vector<Transaction> caTx = buildCorporateActionsFromActionIdNetting(corporateActionRows);
                transactions.addAll(caTx);
                corporateActionCount = caTx.size();
            }
        }

        // Validate file structure for v2 reports regardless of import filters.
        validateMandatoryV2Sections();

        // FXTR dividend fallback: some exports don't include dividend brutto in CTRN.
        if (flexCsvVersion == FlexCsvVersion.V2_HEADERS_AND_TRAILERS) {
            applyFxtrDividendFallback(transactions);
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
            else if (h.equals("settledatetarget") || h.equals("settledate") || h.equals("settledatesource")
                    || (h.contains("settlement") && h.contains("date"))) {
                COL_SETTLEMENT_DATE = i;
                // v2 CTRN uses SettleDate for cash transaction settlement.
                if (COL_CTRN_SETTLE_DATE < 0) {
                    COL_CTRN_SETTLE_DATE = i;
                }
            }
            // Transaction type
            else if (h.equals("transactiontype") || h.equals("transaction type")) {
                COL_TRANSACTION_TYPE = i;
            }
            // NOTE: Do not use a broad "contains('transaction')" fallback here.
            // CTRN headers include TransactionID, which would be incorrectly captured as TransactionType
            // and then prevent TransactionID from being detected.
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

            // FXTR columns are detected by detectFxtrColumnIndices() to avoid
            // interfering with Trades/CTRN/Corporate Actions column detection.
            // Date/Time (note the slash) - used by corporate actions and cash transactions
            else if (h.equals("date/time") || h.equals("date / time")) {
                COL_CA_DATETIME = i;
                COL_CTRN_DATETIME = i;
            }
            // Cash transactions (CTRN)
            else if (h.equals("settledate") || h.equals("settle date") || h.equals("settledatetarget") || h.equals("settledatesource")) {
                COL_CTRN_SETTLE_DATE = i;
            }
            else if (h.equals("amount")) {
                COL_CTRN_AMOUNT = i;
            }
            // Note: "Type" is ambiguous (Corporate Actions type vs Cash transaction type).
            // Bind both to the current header; section validation will ensure we use the right one.
            else if (h.equals("type")) {
                COL_CTRN_TYPE = i;
                COL_CA_TYPE = i;
            }
            // Transaction ID (prefer column detection over hardcoded index)
            else if (h.equals("transactionid") || h.equals("transaction id") || h.equals("ibexecid")) {
                COL_TRANSACTION_ID = i;
                // IMPORTANT:
                // Column indices are section-specific (Trades vs CTRN vs Corporate Actions).
                // Flex v2 can repeat multiple headers with different column counts.
                // Always bind CTRN TransactionID to the currently detected header index;
                // otherwise a stale index from a previous section can prevent TxnID population
                // and cause duplicates on re-import (especially after TimeShift logic).
                COL_CTRN_TRANSACTION_ID = i;
            }
            // IB Order ID (shared across fills, needed for consolidation)
            else if (h.equals("iborderid") || h.equals("ib order id")) {
                COL_IB_ORDER_ID = i;
            }
            // Multiplier (contract size for options/futures)
            else if (h.equals("multiplier")) {
                COL_MULTIPLIER = i;
            }
            // CA type is handled by the shared "type" handler above.
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

        logger.info("  CTRN Date/Time: " + COL_CTRN_DATETIME);
        logger.info("  CTRN SettleDate: " + COL_CTRN_SETTLE_DATE);
        logger.info("  CTRN Amount: " + COL_CTRN_AMOUNT);
        logger.info("  CTRN Type: " + COL_CTRN_TYPE);
        logger.info("  CTRN TransactionID: " + COL_CTRN_TRANSACTION_ID);
        
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
        
        // Do not validate required columns here. Validation depends on the section type
        // (Trades vs Corporate Actions vs ignored sections) and is handled by the caller.
    }

    private void validateRequiredColumnsForSection(SectionType sectionType) {
        if (sectionType == null) sectionType = SectionType.UNKNOWN;

        if (sectionType == SectionType.TRADES) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (COL_DATE < 0 && COL_DATETIME < 0) missing.add("Date/DateTime");
            if (COL_SYMBOL < 0) missing.add("Symbol");
            if (COL_QUANTITY < 0) missing.add("Quantity");
            if (COL_PRICE < 0) missing.add("TradePrice/Price");
            if (!missing.isEmpty()) {
                throw new RuntimeException("Cannot parse IBKR CSV Trades section - missing required columns: " + missing);
            }
            return;
        }

        if (sectionType == SectionType.CORPORATE_ACTIONS) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (COL_ACTION_ID < 0) missing.add("ActionID");
            if (COL_CA_TYPE < 0) missing.add("Type");
            if (COL_CA_DATETIME < 0 && COL_CA_REPORT_DATE < 0) missing.add("Date/Time");
            if (COL_SYMBOL < 0) missing.add("Symbol");
            if (COL_QUANTITY < 0) missing.add("Quantity");
            if (!missing.isEmpty()) {
                throw new RuntimeException("Cannot parse IBKR CSV Corporate Actions section - missing required columns: " + missing);
            }
        }

        if (sectionType == SectionType.CASH_TRANSACTIONS) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (COL_CTRN_DATETIME < 0 && COL_CA_DATETIME < 0) missing.add("Date/Time");
            if (COL_CTRN_SETTLE_DATE < 0 && COL_SETTLEMENT_DATE < 0) missing.add("SettleDate");
            if (COL_CTRN_AMOUNT < 0) missing.add("Amount");
            if (COL_CTRN_TYPE < 0) missing.add("Type");
            if (!missing.isEmpty()) {
                throw new RuntimeException("Cannot parse IBKR CSV Cash Transactions section - missing required columns: " + missing);
            }
        }

        if (sectionType == SectionType.FXTR) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (COL_FXTR_DATETIME < 0) missing.add("DateTime");
            if (COL_FXTR_ACTIVITY_DESCRIPTION < 0) missing.add("ActivityDescription");
            if (COL_FXTR_FX_CURRENCY < 0) missing.add("FXCurrency");
            if (COL_FXTR_QUANTITY < 0) missing.add("Quantity");
            if (!missing.isEmpty()) {
                throw new RuntimeException("Cannot parse IBKR CSV FXTR section - missing required columns: " + missing);
            }
        }

        if (sectionType == SectionType.ACCOUNT_INFO) {
            // Free-form; no strict requirements.
            return;
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

            // Cash transactions (CTRN) can be treated as dividends/taxes in newer v2 exports.
            // We only parse these when the current section is explicitly detected as CASH_TRANSACTIONS.
            if (currentSectionType == SectionType.CASH_TRANSACTIONS) {
                return parseCashTransaction(fields);
            }

            // FXTR (Forex P/L Details) is not imported as transactions directly,
            // but we may use it as a fallback source for dividend brutto rows.
            if (currentSectionType == SectionType.FXTR) {
                noteFxtrDividendCandidate(fields);
                return null;
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

    private Transaction parseCashTransaction(String[] fields) {
        try {
            // Required fields
            int idxDateTime = COL_CTRN_DATETIME >= 0 ? COL_CTRN_DATETIME : COL_CA_DATETIME;
            if (idxDateTime < 0 || idxDateTime >= fields.length) return null;
            if (COL_CTRN_AMOUNT < 0 || COL_CTRN_AMOUNT >= fields.length) return null;
            if (COL_CTRN_TYPE < 0 || COL_CTRN_TYPE >= fields.length) return null;

            String type = fields[COL_CTRN_TYPE].trim();
            if (type.isEmpty()) return null;

            // Track presence of dividend brutto types in CTRN (per account) for FXTR fallback suppression.
            if (type.equalsIgnoreCase("Dividends") || type.equalsIgnoreCase("Payment In Lieu Of Dividends")) {
                if (COL_CLIENT_ACCOUNT_ID >= 0 && COL_CLIENT_ACCOUNT_ID < fields.length) {
                    String acc = fields[COL_CLIENT_ACCOUNT_ID] != null ? fields[COL_CLIENT_ACCOUNT_ID].trim() : "";
                    if (!acc.isEmpty()) {
                        accountsWithCtrnDividendBrutto.add(acc);
                    }
                }
            }

            String dateTimeStr = fields[idxDateTime].trim();
            if (dateTimeStr.isEmpty()) return null;

            Date tradeDate = parseDate(dateTimeStr);

            Date settlementDate = tradeDate;
            int idxSettle = COL_CTRN_SETTLE_DATE >= 0 ? COL_CTRN_SETTLE_DATE : COL_SETTLEMENT_DATE;
            if (idxSettle >= 0 && idxSettle < fields.length) {
                String settleStr = fields[idxSettle].trim();
                if (!settleStr.isEmpty()) {
                    try {
                        settlementDate = parseDate(settleStr);
                    } catch (Exception ignored) {
                    }
                }
            }

            String currency = (COL_CURRENCY >= 0 && COL_CURRENCY < fields.length)
                ? fields[COL_CURRENCY].trim()
                : "USD";
            if (currency.isEmpty()) currency = "USD";

            String ticker = (COL_SYMBOL >= 0 && COL_SYMBOL < fields.length)
                ? fields[COL_SYMBOL].trim()
                : "";

            String description = (COL_NAME >= 0 && COL_NAME < fields.length) ? fields[COL_NAME].trim() : "";
            String code = (COL_CODE >= 0 && COL_CODE < fields.length) ? fields[COL_CODE].trim() : "";

            boolean disabled = false;
            String forcedTicker = "";

            // Some withholding tax / interest rows are not tied to a symbol.
            if (ticker.isEmpty()) {
                String descLower = (description == null) ? "" : description.toLowerCase();
                boolean looksLikeInterest = descLower.contains("credit int") || descLower.contains("interest");

                if (type.equalsIgnoreCase("Withholding Tax") && looksLikeInterest) {
                    forcedTicker = "Kreditni.Urok";
                } else if (type.equalsIgnoreCase("Withholding Tax")) {
                    // Legacy behavior: tax without symbol.
                    forcedTicker = "CASH.internal";
                } else if (type.equalsIgnoreCase("Broker Interest Received")) {
                    forcedTicker = "Kreditni.Urok";
                } else if (type.equalsIgnoreCase("Broker Interest Paid")) {
                    forcedTicker = "Debetni.Urok";
                } else if (type.equalsIgnoreCase("Broker Fees")) {
                    forcedTicker = "Debetni.Urok";
                }

                if (forcedTicker == null || forcedTicker.trim().isEmpty()) {
                    // Do not import cash rows that we cannot attribute.
                    return null;
                }
                ticker = forcedTicker;
            }

            double amount = parseDouble(fields[COL_CTRN_AMOUNT]);
            if (amount == 0.0) {
                return null;
            }

            int direction;
            if (type.equalsIgnoreCase("Dividends") || type.equalsIgnoreCase("Payment In Lieu Of Dividends")) {
                direction = Transaction.DIRECTION_DIVI_BRUTTO;
            } else if (type.equalsIgnoreCase("Withholding Tax")) {
                String descLower = (description == null) ? "" : description.toLowerCase();
                boolean looksLikeInterest = descLower.contains("credit int") || descLower.contains("interest");
                if (ticker.equalsIgnoreCase("Kreditni.Urok") && looksLikeInterest) {
                    direction = Transaction.DIRECTION_INT_TAX;
                } else {
                    direction = Transaction.DIRECTION_DIVI_TAX;
                }
            } else if (type.equalsIgnoreCase("Other Fees")) {
                // Import dividend-related fees as tax-like costs.
                direction = Transaction.DIRECTION_DIVI_TAX;
            } else if (type.equalsIgnoreCase("Broker Interest Received")) {
                ticker = "Kreditni.Urok";
                direction = Transaction.DIRECTION_INT_BRUTTO;
            } else if (type.equalsIgnoreCase("Broker Interest Paid")) {
                ticker = "Debetni.Urok";
                direction = Transaction.DIRECTION_INT_PAID;
                disabled = true;
            } else if (type.equalsIgnoreCase("Broker Fees")) {
                ticker = "Debetni.Urok";
                direction = Transaction.DIRECTION_INT_FEE;
                disabled = true;
            } else {
                return null;
            }

            // Amount for dividend/interest engine is stored in price, with amount=1.
            double price = amount;

            Transaction tx = new Transaction(
                0,
                tradeDate,
                direction,
                ticker,
                1.0,
                price,
                currency,
                0.0,
                currency,
                "",
                settlementDate,
                description
            );

            tx.setBroker("IB");
            if (COL_CLIENT_ACCOUNT_ID >= 0 && COL_CLIENT_ACCOUNT_ID < fields.length) {
                String accountId = fields[COL_CLIENT_ACCOUNT_ID].trim();
                if (!accountId.isEmpty()) tx.setAccountId(accountId);
            }
            // Use TransactionID when present.
            // Be defensive: column detection can be influenced by previous sections; prefer
            // COL_TRANSACTION_ID when the CTRN-specific index is out of bounds.
            int idxTxnId = COL_CTRN_TRANSACTION_ID;
            if (idxTxnId < 0 || idxTxnId >= fields.length) {
                idxTxnId = COL_TRANSACTION_ID;
            }
            if (idxTxnId >= 0 && idxTxnId < fields.length) {
                String txnId = fields[idxTxnId].trim();
                if (!txnId.isEmpty()) {
                    tx.setTxnId(txnId);
                }
            }
            if (!code.isEmpty()) tx.setCode(code);

            // Add extra context so user can distinguish cash types
            if (!type.isEmpty()) {
                String base = description == null ? "" : description;
                String note = base + "|Broker:IB|Type:" + type;
                tx.setNote(note);
            }

            if (disabled) {
                tx.setDisabled(true);
            }

            return tx;
        } catch (Exception e) {
            logger.warning("Failed to parse cash transaction row: " + e.getMessage());
            return null;
        }
    }

    private void noteFxtrDividendCandidate(String[] fields) {
        if (!enableFxtrDividendFallback) return;
        if (fields == null) return;

        if (COL_FXTR_ACTIVITY_DESCRIPTION < 0 || COL_FXTR_ACTIVITY_DESCRIPTION >= fields.length) return;
        if (COL_FXTR_FX_CURRENCY < 0 || COL_FXTR_FX_CURRENCY >= fields.length) return;
        if (COL_FXTR_DATETIME < 0 || COL_FXTR_DATETIME >= fields.length) return;
        if (COL_FXTR_QUANTITY < 0 || COL_FXTR_QUANTITY >= fields.length) return;

        String desc = fields[COL_FXTR_ACTIVITY_DESCRIPTION] != null ? fields[COL_FXTR_ACTIVITY_DESCRIPTION].trim() : "";
        if (desc.isEmpty()) return;
        String upper = desc.toUpperCase();
        boolean isDividend = upper.contains("CASH DIVIDEND") || upper.contains("PAYMENT IN LIEU OF DIVIDEND");
        if (!isDividend) return;

        // Only brutto: ignore explicit tax lines.
        if (upper.contains("- TAX")) return;

        RawFxtrDividendRow r = new RawFxtrDividendRow();
        if (COL_FXTR_CLIENT_ACCOUNT_ID >= 0 && COL_FXTR_CLIENT_ACCOUNT_ID < fields.length) {
            r.accountId = fields[COL_FXTR_CLIENT_ACCOUNT_ID] != null ? fields[COL_FXTR_CLIENT_ACCOUNT_ID].trim() : "";
        } else {
            r.accountId = "";
        }
        r.dateTimeStr = fields[COL_FXTR_DATETIME] != null ? fields[COL_FXTR_DATETIME].trim() : "";
        r.fxCurrency = fields[COL_FXTR_FX_CURRENCY] != null ? fields[COL_FXTR_FX_CURRENCY].trim() : "";
        r.activityDescription = desc;
        r.quantity = parseDouble(fields[COL_FXTR_QUANTITY]);
        if (r.dateTimeStr.isEmpty() || r.fxCurrency.isEmpty()) return;
        if (r.quantity == 0.0) return;

        fxtrDividendRows.add(r);
    }

    private static String extractTickerFromDividendActivityDescription(String activityDescription) {
        if (activityDescription == null) return "";
        String s = activityDescription.trim();
        if (s.isEmpty()) return "";
        int idx = s.indexOf('(');
        if (idx <= 0) return "";
        String t = s.substring(0, idx).trim();
        // FXTR can contain non-ticker descriptions (e.g. "Net cash activity"); reject multi-word prefixes.
        if (t.contains(" ")) return "";
        return t;
    }

    private void applyFxtrDividendFallback(Vector<Transaction> transactions) {
        if (!enableFxtrDividendFallback) return;
        if (!includeCashTransactions) return;
        if (fxtrDividendRows.isEmpty()) return;

        // If any account has dividend brutto in CTRN, do not fallback for that account.
        for (RawFxtrDividendRow r : fxtrDividendRows) {
            if (r == null) continue;
            String acc = r.accountId == null ? "" : r.accountId.trim();
            if (!acc.isEmpty() && accountsWithCtrnDividendBrutto.contains(acc)) {
                continue;
            }

            String ticker = extractTickerFromDividendActivityDescription(r.activityDescription);
            if (ticker.isEmpty()) {
                continue;
            }

            Date d;
            try {
                d = parseDate(r.dateTimeStr);
            } catch (Exception e) {
                continue;
            }

            String currency = r.fxCurrency;
            if (currency == null || currency.trim().isEmpty()) {
                continue;
            }
            currency = currency.trim();

            Transaction tx;
            try {
                tx = new Transaction(
                    0,
                    d,
                    Transaction.DIRECTION_DIVI_BRUTTO,
                    ticker,
                    1.0,
                    r.quantity,
                    currency,
                    0.0,
                    currency,
                    "",
                    d,
                    r.activityDescription
                );
            } catch (Exception e) {
                continue;
            }
            tx.setBroker("IB");
            if (!acc.isEmpty()) {
                tx.setAccountId(acc);
            }
            tx.setNote(r.activityDescription + "|Broker:IB|Type:Dividends|Src:FXTR");

            // Let TransactionSet dedupe decide; but keep stats for UI.
            transactions.add(tx);
            fxtrDividendFallbackCount++;
            if (!acc.isEmpty()) {
                fxtrDividendFallbackAccounts.add(acc);
            }
        }
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
