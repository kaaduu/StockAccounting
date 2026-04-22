/*
 * ImportFirefish.java
 *
 * Created on 22. dubna 2026
 */

package cz.datesoft.stockAccounting.imp;

import cz.datesoft.stockAccounting.Transaction;
import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

/**
 * Import closed or liquidated Firefish loans as interest transactions.
 */
public class ImportFirefish extends ImportBase {
  public static final String NOT_IMPORTED_REASON_IGNORED_STATUS = "IGNORED_STATUS";
  public static final String NOT_IMPORTED_REASON_ERROR = "ERROR";

  private static final DateTimeFormatter DATE_FMT_DASH = DateTimeFormatter.ofPattern("MM-dd-yy", Locale.US);
  private static final DateTimeFormatter DATE_FMT_SLASH_2Y = DateTimeFormatter.ofPattern("M/d/yy", Locale.US);
  private static final DateTimeFormatter DATE_FMT_SLASH_4Y = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);

  private static final String STATUS_CLOSED = "CLOSED";
  private static final String STATUS_LIQUIDATED = "LIQUIDATED";

  private static final String TICKER_INTEREST = "Kreditni.Urok";

  private final int ID_INVESTMENT_ID = 1;
  private final int ID_START_DATE = 2;
  private final int ID_MATURITY_DATE = 3;
  private final int ID_INTEREST_RATE = 4;
  private final int ID_CURRENCY = 5;
  private final int ID_INVESTMENT_AMOUNT = 6;
  private final int ID_AMOUNT_DUE = 7;
  private final int ID_STATUS = 8;
  private final int ID_CLOSED_AT = 9;
  private final int ID_ACCOUNT_NUMBER = 10;
  private final int ID_COLLATERAL_SUM = 11;
  private final int ID_LIQUIDATION_PRICE = 12;
  private final int ID_INVESTOR_ID = 13;
  private final int ID_BORROWER_ID = 14;
  private final int ID_NOTE = 15;
  private final int ID_LOAN_TYPE = 16;

  public ImportFirefish() {
    super();
  }

  @Override
  public Vector<Transaction> doImport(File srcFile, Date startDate, Date endDate, Vector<String[]> notImported)
      throws ImportException, java.io.IOException {
    Vector<Transaction> res = new Vector<Transaction>();

    try (BufferedReader ifl = Files.newBufferedReader(Path.of(srcFile.toURI()))) {
      registerHeaders();

      String s;
      int neededLen = 0;
      boolean startFound = false;
      while ((s = ifl.readLine()) != null) {
        String[] a = parseCsvFields(s);
        if (a.length >= 10) {
          startFound = true;
          for (int i = 0; i < a.length; i++) {
            if (setColumnIdentity(i, stripBom(a[i]).trim())) {
              neededLen = i + 1;
            }
          }
          break;
        }
      }

      if (!startFound) {
        throw new ImportException("Firefish: Nemohu najít hlavičku dat - je soubor ve správném formátu?");
      }

      checkAllColumnsPresent();

      int investmentIdIdx = getColumnNo(ID_INVESTMENT_ID);
      int startDateIdx = getColumnNo(ID_START_DATE);
      int maturityDateIdx = getColumnNo(ID_MATURITY_DATE);
      int interestRateIdx = getColumnNo(ID_INTEREST_RATE);
      int currencyIdx = getColumnNo(ID_CURRENCY);
      int investmentAmountIdx = getColumnNo(ID_INVESTMENT_AMOUNT);
      int amountDueIdx = getColumnNo(ID_AMOUNT_DUE);
      int statusIdx = getColumnNo(ID_STATUS);
      int closedAtIdx = getColumnNo(ID_CLOSED_AT);
      int accountNumberIdx = getColumnNo(ID_ACCOUNT_NUMBER);
      int collateralSumIdx = getColumnNo(ID_COLLATERAL_SUM);
      int liquidationPriceIdx = getColumnNo(ID_LIQUIDATION_PRICE);
      int investorIdIdx = getColumnNo(ID_INVESTOR_ID);
      int borrowerIdIdx = getColumnNo(ID_BORROWER_ID);
      int noteIdx = getColumnNo(ID_NOTE);
      int loanTypeIdx = getColumnNo(ID_LOAN_TYPE);

      while ((s = ifl.readLine()) != null) {
        String[] a = parseCsvFields(s);

        if (isEmptyRow(a)) {
          continue;
        }

        if (a.length < neededLen) {
          addNotImported(notImported, NOT_IMPORTED_REASON_ERROR + ":SHORT_ROW", a);
          continue;
        }

        try {
          String status = safeField(a, statusIdx).trim().toUpperCase(Locale.ROOT);
          if (!STATUS_CLOSED.equals(status) && !STATUS_LIQUIDATED.equals(status)) {
            addNotImported(notImported, NOT_IMPORTED_REASON_IGNORED_STATUS + ":" + status, a);
            continue;
          }

          Date closedAt = parseFirefishDate(safeField(a, closedAtIdx));
          if (closedAt == null) {
            addNotImported(notImported, NOT_IMPORTED_REASON_ERROR + ":MISSING_CLOSED_AT", a);
            continue;
          }

          String currency = safeField(a, currencyIdx).trim().toUpperCase(Locale.ROOT);
          if (currency.isEmpty()) {
            addNotImported(notImported, NOT_IMPORTED_REASON_ERROR + ":MISSING_CURRENCY", a);
            continue;
          }

          double investmentAmount = parseNumber(safeField(a, investmentAmountIdx));
          double amountDue = parseNumber(safeField(a, amountDueIdx));
          double earnedInterest = amountDue - investmentAmount;

          DataRow drow = new DataRow();
          drow.date = closedAt;
          drow.executionDate = closedAt;
          drow.direction = Transaction.DIRECTION_INT_BRUTTO;
          drow.ticker = TICKER_INTEREST;
          drow.amount = 1.0;
          drow.price = earnedInterest;
          drow.currency = currency;
          drow.fee = 0.0;
          drow.feeCurrency = drow.currency;
          drow.market = "FIREFISH";
          drow.broker = "Firefish";
          drow.accountId = safeField(a, accountNumberIdx).trim();
          drow.txnId = safeField(a, investmentIdIdx).trim();
          drow.note = buildNote(
              safeField(a, investmentIdIdx),
              safeField(a, startDateIdx),
              safeField(a, maturityDateIdx),
              safeField(a, interestRateIdx),
              drow.currency,
              safeField(a, investmentAmountIdx),
              safeField(a, amountDueIdx),
              status,
              safeField(a, closedAtIdx),
              drow.accountId,
              safeField(a, collateralSumIdx),
              safeField(a, liquidationPriceIdx),
              safeField(a, investorIdIdx),
              safeField(a, borrowerIdIdx),
              safeField(a, noteIdx),
              safeField(a, loanTypeIdx),
              earnedInterest);

          addRow(res, drow);
        } catch (ImportException e) {
          addNotImported(notImported, NOT_IMPORTED_REASON_ERROR + ":" + sanitizeReason(e.getMessage()), a);
        } catch (Exception e) {
          addNotImported(notImported, NOT_IMPORTED_REASON_ERROR + ":" + sanitizeReason(e.getMessage()), a);
        }
      }
    }

    return res;
  }

  private void registerHeaders() {
    registerColumnName("Investment id", ID_INVESTMENT_ID);
    registerColumnName("Start date (mm/dd/yyyy)", ID_START_DATE);
    registerColumnName("Maturity date (mm/dd/yyyy)", ID_MATURITY_DATE);
    registerColumnName("Interest rate (% p.a.)", ID_INTEREST_RATE);
    registerColumnName("Currency", ID_CURRENCY);
    registerColumnName("Investment amount", ID_INVESTMENT_AMOUNT);
    registerColumnName("Amount due", ID_AMOUNT_DUE);
    registerColumnName("Status", ID_STATUS);
    registerColumnName("Closed at (mm/dd/yyyy)", ID_CLOSED_AT);
    registerColumnName("My account number", ID_ACCOUNT_NUMBER);
    registerColumnName("Collateral sum (BTC)", ID_COLLATERAL_SUM);
    registerColumnName("Liquidation price", ID_LIQUIDATION_PRICE);
    registerColumnName("Investor id", ID_INVESTOR_ID);
    registerColumnName("Borrower id", ID_BORROWER_ID);
    registerColumnName("Note", ID_NOTE);
    registerColumnName("Loan type", ID_LOAN_TYPE);
  }

  private static String stripBom(String value) {
    if (value == null) {
      return "";
    }
    return value.startsWith("\uFEFF") ? value.substring(1) : value;
  }

  private static String[] parseCsvFields(String line) {
    List<String> fields = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
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

  private static boolean isEmptyRow(String[] fields) {
    if (fields == null || fields.length == 0) {
      return true;
    }
    for (String field : fields) {
      if (field != null && field.trim().length() > 0) {
        return false;
      }
    }
    return true;
  }

  private static String safeField(String[] fields, int idx) {
    if (idx < 0 || idx >= fields.length || fields[idx] == null) {
      return "";
    }
    return fields[idx].trim();
  }

  private static Date parseFirefishDate(String value) throws ImportException {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      return null;
    }

    LocalDate localDate;
    try {
      localDate = LocalDate.parse(normalized, DATE_FMT_DASH);
    } catch (DateTimeParseException first) {
      try {
        localDate = LocalDate.parse(normalized, DATE_FMT_SLASH_2Y);
      } catch (DateTimeParseException second) {
        try {
          localDate = LocalDate.parse(normalized, DATE_FMT_SLASH_4Y);
        } catch (DateTimeParseException third) {
          throw new ImportException("Firefish: neplatné datum '" + normalized + "'.");
        }
      }
    }

    return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  private static void addNotImported(Vector<String[]> notImported, String reason, String[] row) {
    if (notImported == null) {
      return;
    }
    int len = row != null ? row.length : 0;
    String[] withReason = new String[len + 1];
    withReason[0] = reason;
    if (len > 0) {
      System.arraycopy(row, 0, withReason, 1, len);
    }
    notImported.add(withReason);
  }

  private static String sanitizeReason(String message) {
    if (message == null) {
      return "UNKNOWN";
    }
    String trimmed = message.trim();
    if (trimmed.isEmpty()) {
      return "UNKNOWN";
    }
    return trimmed.replace('|', '/');
  }

  private static String buildNote(String investmentId, String startDate, String maturityDate, String interestRate,
      String currency, String investmentAmount, String amountDue, String status, String closedAt, String accountId,
      String collateralSum, String liquidationPrice, String investorId, String borrowerId, String csvNote,
      String loanType, double earnedInterest) {
    List<String> parts = new ArrayList<String>();
    parts.add("Firefish loan closed");
    addNotePart(parts, "Broker", "Firefish");
    addNotePart(parts, "AccountID", accountId);
    addNotePart(parts, "TxnID", investmentId);
    addNotePart(parts, "StartDate", startDate);
    addNotePart(parts, "MaturityDate", maturityDate);
    addNotePart(parts, "ClosedAt", closedAt);
    addNotePart(parts, "Status", status);
    addNotePart(parts, "InterestRate", interestRate);
    addNotePart(parts, "Currency", currency);
    addNotePart(parts, "InvestmentAmount", investmentAmount);
    addNotePart(parts, "AmountDue", amountDue);
    addNotePart(parts, "EarnedInterest", Double.toString(earnedInterest));
    addNotePart(parts, "CollateralBTC", collateralSum);
    addNotePart(parts, "LiquidationPrice", liquidationPrice);
    addNotePart(parts, "LoanType", loanType);
    addNotePart(parts, "InvestorID", investorId);
    addNotePart(parts, "BorrowerID", borrowerId);
    addNotePart(parts, "CsvNote", csvNote);
    return String.join("|", parts);
  }

  private static void addNotePart(List<String> parts, String key, String value) {
    if (value == null) {
      return;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    parts.add(key + ":" + trimmed);
  }
}
