/*
 * TransactionSet.java
 *
 * Created on 6. říjen 2006, 17:36
 *
 * Main class holding data
 */

package cz.datesoft.stockAccounting;

import java.util.Date;
import java.util.Iterator;
import java.util.Vector;
import java.util.GregorianCalendar;
import java.io.File;
import java.io.PrintWriter;
import cz.datesoft.stockAccounting.imp.*;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 *
 * @author lemming2
 */
public class TransactionSet extends javax.swing.table.AbstractTableModel {
  /** Import format constants */
  public final int IMPORT_FORMAT_FIO = 1;
  public final int IMPORT_FORMAT_BJ_HTML = 2;
  public final int IMPORT_FORMAT_IB_TRADELOG = 3;
  public final int IMPORT_FORMAT_CUSTOMCSV = 4;
  public final int IMPORT_FORMAT_T212USD = 5;
  public final int IMPORT_FORMAT_T212CZK = 6;
  public final int IMPORT_FORMAT_REVOLUT_CSV = 7;

   /** Our set */
   protected Vector<Transaction> rows;

   /**
    * Filtered set. When not null, we are showing this filtered set
    * instead of the complete one. But we still hold / save / enumerate / ...
    * the complete, filtering affects only table model.
    */
   protected Vector<Transaction> filteredRows;

   /** Logger */
   private static final Logger logger = Logger.getLogger(TransactionSet.class.getName());

   /** Serial counter */
   protected int serialCounter;

   /**
    * Last date we set
    */
   protected Date lastDateSet;

   /** Trading 212 API import constant */
   public final int IMPORT_FORMAT_TRADING212_API = 8;

  /** Column names */
  private String[] columnNames = { "Datum", "Typ", "Směr", "Ticker", "Množství", "Kurs", "Měna kursu", "Poplatky",
      "Měna poplatků", "Trh", "Datum vypořádání", "Note" };

  /**
   * File we are stored in
   */
  private File diskFile;

  /**
   * Whether we are modified
   */
  boolean modified;

  /**
   * Combo box model we manage
   */
  SortedSetComboBoxModel cbmodel;

  /** Creates a new instance of TransactionSet */
  public TransactionSet() {
    rows = new Vector<Transaction>();

    serialCounter = 1;

    cbmodel = new SortedSetComboBoxModel();
  }

  /**
   * Read line
   */
  static public String[] readLine(BufferedReader ifl) {
    String res[] = new String[2];

    try {
      String line = ifl.readLine();
      int sp, p;

      if (line == null)
        return res; // Return initializes - two nulls

      // Skip spaces at the beginning
      sp = 0;
      while (sp < line.length() && (line.charAt(sp) == ' '))
        sp++;

      // Find =
      p = line.indexOf('=', sp);

      if (p < 0) {
        // Only one element
        res[0] = line.substring(sp);
        return res;
      }

      // name=value - split at =
      res[0] = line.substring(sp, p);
      res[1] = line.substring(p + 1);
    } catch (java.io.IOException e) {
    }

    return res;
  }

  /**
   * Parse date from text to date object
   */
  public static Date parseDate(String dateTxt) {
    GregorianCalendar cal = new GregorianCalendar();
    String a[] = dateTxt.split("[\\. :]");

    if (a.length != 5)
      return cal.getTime();

    cal.set(GregorianCalendar.DAY_OF_MONTH, Integer.parseInt(a[0]));
    cal.set(GregorianCalendar.MONTH, Integer.parseInt(a[1]) - 1);
    cal.set(GregorianCalendar.YEAR, Integer.parseInt(a[2]));
    cal.set(GregorianCalendar.HOUR_OF_DAY, Integer.parseInt(a[3]));
    cal.set(GregorianCalendar.MINUTE, Integer.parseInt(a[4]));
    cal.set(GregorianCalendar.SECOND, 0);
    cal.set(GregorianCalendar.MILLISECOND, 0);

    return cal.getTime();
  }

  /** Add new transaction */
  public synchronized Transaction addTransaction(Date date, int direction, String ticker, double amount, double price,
      String priceCurrency, double fee, String feeCurrency, String market, Date executionDate, String note)
      throws Exception {
    Transaction tx = new Transaction(serialCounter++, date, direction, ticker, amount, price, priceCurrency, fee,
        feeCurrency, market, executionDate, note);
    rows.add(tx);
    if (filteredRows != null)
      filteredRows.add(tx);

    if (date != null)
      lastDateSet = date;

    fireTableDataChanged();

    return tx;
  }

  /**
   * Whether is set modified
   */
  public boolean isModified() {
    return modified;
  }

  /**
   * Sort transaction vector
   */
  public void sort() {
    // Get rows as an array
    Transaction[] arr = new Transaction[rows.size()];
    rows.copyInto(arr);

    // Sort the array
    java.util.Arrays.sort(arr);

    // Put array back to the rows
    for (int i = 0; i < arr.length; i++)
      rows.set(i, arr[i]);

    if (filteredRows == null)
      fireTableDataChanged();
  }

  /**
   * Get value for table
   */
  public Object getValueAt(int row, int col) {
    Vector<Transaction> v = (filteredRows != null) ? filteredRows : rows;

    if (row >= v.size())
      return null;

    Transaction tx = v.get(row);

    switch (col) {
      case 0:
        return tx.getDate();
      case 1:
        return tx.getStringType();
      case 2:
        return tx.getStringDirection();
      case 3:
        return tx.getTicker();
      case 4:
        return tx.getAmount();
      case 5:
        return tx.getPrice();
      case 6:
        return tx.getPriceCurrency();
      case 7:
        return tx.getFee();
      case 8:
        return tx.getFeeCurrency();
      case 9:
        return tx.getMarket();
      case 10:
        return tx.getExecutionDate();
      case 11:
        return tx.getNote();
      default:
        return null;
    }
  }

  /**
   * Set value for table
   */
  @Override
  public void setValueAt(Object value, int row, int col) {
    Transaction tx;
    Vector<Transaction> v = (filteredRows != null) ? filteredRows : rows;

    if (row == v.size()) {
      if (value == null)
        return; // Don't add when null is set

      // Add new transaction
      tx = new Transaction(serialCounter++);

      v.add(tx);

      fireTableRowsInserted(v.size() - 1, v.size() - 1);
    } else
      tx = v.get(row);

    switch (col) {
      case 0:
        if (value != null)
          lastDateSet = (Date) value; // Update last date set
        tx.setDate((Date) value);
        fireTableCellUpdated(row, col);
        break;
      case 1:
        tx.setType((String) value);
        fireTableCellUpdated(row, col);
        fireTableCellUpdated(row, col + 1); // Direction also might got updated
        break;
      case 2:
        tx.setDirection((String) value);
        fireTableCellUpdated(row, col);
        break;
      case 3:
        String s = (String) value;
        tx.setTicker(s);
        if (s != null) {
          if (s.length() > 0)
            cbmodel.putItem(((String) value).toUpperCase());
        }
        fireTableCellUpdated(row, col);
        break;
      case 4:
        tx.setAmount((Double) value);
        fireTableCellUpdated(row, col);
        break;
      case 5:
        tx.setPrice((Double) value);
        fireTableCellUpdated(row, col);
        break;
      case 6:
        tx.setPriceCurrency((String) value);
        if (tx.getFeeCurrency() == null) {
          tx.setFeeCurrency((String) value); // Default fee currency to price currency
          fireTableCellUpdated(row, 7);
        }
        fireTableCellUpdated(row, col);
        break;
      case 7:
        tx.setFee((Double) value);
        fireTableCellUpdated(row, col);
        break;
      case 8:
        tx.setFeeCurrency((String) value);
        if (tx.getPriceCurrency() == null) {
          tx.setPriceCurrency((String) value); // Default price currency to fee currency
          fireTableCellUpdated(row, 5);
        }
        fireTableCellUpdated(row, col);
        break;
      case 9:
        tx.setMarket((String) value);
        fireTableCellUpdated(row, col);
        break;
      case 10:
        tx.setExecutionDate((Date) value);
        fireTableCellUpdated(row, col);
        break;
      case 11:
        tx.setNote((String) value);
        fireTableCellUpdated(row, col);
        break;
    }

    modified = true;
  }

  /**
   * Get column count (for table)
   */
  public int getColumnCount() {
    return columnNames.length;
  }

  /**
   * Get row count (for table)
   */
  public int getRowCount() {
    return ((filteredRows != null) ? filteredRows : rows).size() + 1;
  }

  /**
   * Get column class (for table)
   */
  @Override
  public Class<?> getColumnClass(int c) {
    switch (c) {
      case 0:
      case 1:
      case 2:
      case 3:
      case 6:
      case 8:
      case 9:
      case 11:
      case 10:
        return String.class;
      case 5:
      case 7:
      case 4:
        return Double.class;
      default:
        return Object.class;
    }
  }

  /**
   * Get column name (for table)
   */
  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  /**
   * Get column editable
   */
  @Override
  public boolean isCellEditable(int row, int column) {
    return true;
  }

  /**
   * Get transaction at the given offset
   *
   * @param row Row to get
   *
   * @return Transaction at the given row
   */
  public Transaction getRowAt(int row) {
    Vector<Transaction> v = (filteredRows != null) ? filteredRows : rows;

    return v.get(row);
  }

  /**
   * Get last date we set so it can be used as a default for next date...
   */
  public Date getLastDateSet() {
    return lastDateSet;
  }

  /**
   * Get our file
   */
  public File getFile() {
    return diskFile;
  }

  /**
   * Save specified row set to a file
   */
  private void saveInternal(Vector<Transaction> rows, File dstFile)
      throws java.io.FileNotFoundException, java.io.IOException {
    PrintWriter ofl = new PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(dstFile)));

    try {
      GregorianCalendar cal = new GregorianCalendar();

      ofl.println("version=1");
      ofl.println("serialCounter=" + serialCounter);
      if (lastDateSet == null)
        lastDateSet = getMaxDate();
      if (lastDateSet == null) {
        ofl.println("lastDateSet=null");
      } else {
        cal.setTime(lastDateSet);
        ofl.println("lastDateSet=" + cal.get(GregorianCalendar.DAY_OF_MONTH) + "."
            + (cal.get(GregorianCalendar.MONTH) + 1) + "." + cal.get(GregorianCalendar.YEAR) + " "
            + cal.get(GregorianCalendar.HOUR_OF_DAY) + ":" + cal.get(GregorianCalendar.MINUTE));
      }

      for (int i = 0; i < rows.size(); i++) {
        Transaction tx = rows.elementAt(i);

        if (tx.isFilledIn()) {
          ofl.println("row (");
          tx.save(ofl, "  ");
          ofl.println(")");
        }
      }
    } finally {
      try {
        ofl.close();
      } catch (Exception e) {
      }
    }

    diskFile = dstFile;

    modified = false;
  }

  /**
   * Save all rows to a file
   */
  public void save(File dstFile) throws java.io.FileNotFoundException, java.io.IOException {
    saveInternal(rows, dstFile);
  }

  /**
   * Save all rows to a file
   */
  public void saveFiltered(File dstFile) throws java.io.FileNotFoundException, java.io.IOException {
    if (filteredRows == null)
      throw new IllegalStateException("Filter not active");
    saveInternal(filteredRows, dstFile);
  }

  /**
   * Clear data
   */
  public void clear() {
    rows.clear();
    filteredRows = null;
    cbmodel.removeAllElements();
    serialCounter = 1;

    fireTableDataChanged();
  }

  /**
   * Load from a file
   */
  public void load(File srcFile) throws java.io.FileNotFoundException, java.io.IOException {
    BufferedReader ifl = new BufferedReader(new java.io.FileReader(srcFile));
    String a[];

    // Initialize
    rows.clear();
    filteredRows = null;
    cbmodel.removeAllElements();
    serialCounter = 1;

    a = readLine(ifl);

    if (a[0] == null) {
      // No data(?)
      ifl.close();
      return;
    }

    if (!a[0].equals("version")) {
      // Version not first?
      ifl.close();
      return;
    }

    if (!a[1].equals("1")) {
      // Version not equal
      ifl.close();
      return;
    }

    // Start reading lines
    for (;;) {
      a = readLine(ifl);
      if (a[0] == null)
        break;

      if (a[0].equals("row (")) {
        // Add row
        Transaction tx = new Transaction(ifl);
        rows.add(tx);

        cbmodel.putItem(tx.ticker.toUpperCase());
      } else if (a[0].equals("serialCounter"))
        this.serialCounter = Integer.parseInt(a[1]);
      else if (a[0].equals("lastDateSet")) {
        if (a[1].equalsIgnoreCase("null"))
          this.lastDateSet = null;
        else
          this.lastDateSet = parseDate(a[1]);
      }
    }

    sort();

    // We don't fire data changed event, since it is alreday done by the sort()

    diskFile = srcFile;
    modified = false;
  }

  /**
   * Load from a file and merge with existing data
   */
  public void loadAdd(File srcFile) throws java.io.FileNotFoundException, java.io.IOException {
    BufferedReader ifl = new BufferedReader(new java.io.FileReader(srcFile));
    String a[];

    // Initialize
    // rows.clear();
    filteredRows = null;
    // cbmodel.removeAllElements();
    serialCounter = 1;

    a = readLine(ifl);

    if (a[0] == null) {
      // No data(?)
      ifl.close();
      return;
    }

    if (!a[0].equals("version")) {
      // Version not first?
      ifl.close();
      return;
    }

    if (!a[1].equals("1")) {
      // Version not equal
      ifl.close();
      return;
    }

    // Start reading lines
    for (;;) {
      a = readLine(ifl);
      if (a[0] == null)
        break;

      if (a[0].equals("row (")) {
        // Add row
        Transaction tx = new Transaction(ifl);
        rows.add(tx);

        cbmodel.putItem(tx.ticker.toUpperCase());
      } else if (a[0].equals("serialCounter"))
        this.serialCounter = Integer.parseInt(a[1]);
      else if (a[0].equals("lastDateSet")) {
        if (a[1].equalsIgnoreCase("null"))
          this.lastDateSet = null;
        else
          this.lastDateSet = parseDate(a[1]);
      }
    }

    sort();

    // We don't fire data changed event, since it is alreday done by the sort()

    diskFile = srcFile;
    modified = false;
  }

  /**
   * Delete row
   *
   * @param rowNo Row to delete
   *
   * @return Whether row was deleted
   */
  boolean deleteRow(int rowNo) {
    if (filteredRows != null) {
      if ((rowNo < 0) || (rowNo >= filteredRows.size()))
        return false; // Last row or out of range

      // Delete in original rows
      rows.remove(filteredRows.get(rowNo));

      // Delete in filtered rows
      filteredRows.remove(rowNo);
    } else {
      if ((rowNo < 0) || (rowNo >= rows.size()))
        return false; // Last row or out of range

      // Remove
      rows.remove(rowNo);
    }

    fireTableRowsDeleted(rowNo, rowNo);

    return true;
  }

  /**
   * Get combo box model containing ticker names
   */
  public SortedSetComboBoxModel getTickersModel() {
    return cbmodel;
  }

  /**
   * Get max date
   */
  public Date getMaxDate() {
    Date res = null;

    for (Iterator<Transaction> i = rows.iterator(); i.hasNext();) {
      if (res == null)
        res = i.next().getDate();
      else {
        Date d = i.next().getDate();
        if (d != null) {
          if (d.compareTo(res) > 0)
            res = d;
        }
      }
    }

    return res;
  }

  /**
   * Import from a file
   */
  public void importFile(File srcFile, Date startDate, Date endDate, int format, Vector<String[]> notImported)
      throws ImportException, java.io.IOException {
    ImportBase importer = null;

    if (format == IMPORT_FORMAT_FIO)
      importer = new ImportFio();
    else if (format == IMPORT_FORMAT_BJ_HTML)
      importer = new ImportBjHTML();
    else if (format == IMPORT_FORMAT_IB_TRADELOG)
      importer = new ImportIBTradeLog();
    else if (format == IMPORT_FORMAT_CUSTOMCSV)
      importer = new ImportCustomCSV();
    else if (format == IMPORT_FORMAT_T212USD)
      importer = new ImportT212();
    else if (format == IMPORT_FORMAT_T212CZK)
      importer = new ImportT212CZK();
    else if (format == IMPORT_FORMAT_REVOLUT_CSV)
      importer = new ImportRevolutCSV();
    else if (format == IMPORT_FORMAT_TRADING212_API) {
      // Handle Trading 212 API import
      handleTrading212ApiImport(startDate, endDate, notImported);
      return;
    }
    else
      throw new ImportException("Unrecognized import format number!");

    Vector<Transaction> txs = importer.doImport(srcFile, startDate, endDate, notImported);

    // Pass resulting transactions
    rows.clear();
    for (Transaction tx : txs) {
      tx.setSerial(serialCounter++);
      rows.add(tx);
    }

    sort();

    // We don't fire data changed event, since it is alreday done by the sort()
    diskFile = null;
    modified = false;
  }

  /**
   * Import from Trading 212 API for a specific year
   *
   * @param year Year to import
   * @param apiKey Trading 212 API key
   * @param apiSecret Trading 212 API secret
   * @param useDemo Whether to use demo environment
   * @return Import result
   */
  public Trading212Importer.ImportResult importFromTrading212Api(int year, String apiKey, String apiSecret, boolean useDemo) throws Exception {
    Trading212Importer importer = new Trading212Importer(apiKey, apiSecret, useDemo);
    Trading212Importer.ImportResult result = importer.importYear(year);

    if (result.success && result.transactionsImported > 0) {
      // Note: The current implementation doesn't return actual transactions
      // This needs to be enhanced to collect transactions from the importer
      logger.info("Trading 212 import successful: " + result.message);

      // Mark as modified
      modified = true;
    }

    return result;
  }

  /**
   * Handle Trading 212 API import from the import dialog
   * This is called when format == IMPORT_FORMAT_TRADING212_API
   */
  private void handleTrading212ApiImport(Date startDate, Date endDate, Vector<String[]> notImported)
      throws ImportException {

    try {
      // Validate input dates
      if (startDate == null || endDate == null) {
        throw new ImportException("Please select a valid date range for Trading 212 import.\n" +
            "Trading 212 API requires specific start and end dates.");
      }

      if (startDate.after(endDate)) {
        throw new ImportException("Start date cannot be after end date.");
      }

      // Validate date range doesn't exceed 1 year (API limitation)
      long daysBetween = (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24);
      if (daysBetween > 365) {
        throw new ImportException("Date range cannot exceed 1 year.\n" +
            "Trading 212 API limitation: maximum 1 year of data per request.");
      }

      // Get credentials from settings
      String apiKey = Settings.getTrading212ApiKey();
      String apiSecret = Settings.getTrading212ApiSecret();
      boolean useDemo = Settings.getTrading212UseDemo();

      if (apiKey == null || apiKey.trim().isEmpty() ||
          apiSecret == null || apiSecret.trim().isEmpty()) {
        throw new ImportException("Trading 212 API credentials not configured. " +
            "Please set them in Settings → Trading 212 API tab.");
      }

      // Extract year from date range
      GregorianCalendar cal = new GregorianCalendar();
      cal.setTime(startDate);
      int year = cal.get(GregorianCalendar.YEAR);

      // Perform import
      Trading212Importer.ImportResult result = importFromTrading212Api(year, apiKey, apiSecret, useDemo);

      if (!result.success) {
        throw new ImportException("Trading 212 import failed: " + result.message);
      }

      // Add imported transactions to our set
      if (result.transactions != null && !result.transactions.isEmpty()) {
        rows.clear(); // Clear existing transactions for this import
        for (Transaction tx : result.transactions) {
          tx.setSerial(serialCounter++);
          rows.add(tx);
        }

        sort();
        modified = true;
      }

      logger.info("Trading 212 API import completed: " + result.message);

    } catch (Exception e) {
      logger.severe("Trading 212 API import failed: " + e.getMessage());
      throw new ImportException("Trading 212 API import failed: " + e.getMessage());
    }
  }

  /**
   * Merge data of our set to another set
   */
  public void mergeTo(TransactionSet dstSet) throws Exception {
    for (int i = 0; i < rows.size(); i++) {
      Transaction tx = rows.get(i);

      dstSet.addTransaction(tx.getDate(), tx.getDirection().intValue(), tx.getTicker(), tx.getAmount().doubleValue(),
          tx.getPrice().doubleValue(), tx.getPriceCurrency(), tx.getFee().doubleValue(), tx.getFeeCurrency(),
          tx.getMarket(), tx.getExecutionDate(), tx.getNote());
    }

    // Sort destination
    dstSet.sort();
  }

  /**
   * Get iterator over transactions
   */
  public Iterator<Transaction> iterator() {
    return rows.iterator();
  }

  /**
   * Export set to a file
   */
  public void export(File file) throws Exception {
    java.io.PrintWriter ofl = new java.io.PrintWriter(new java.io.FileWriter(file));

    // Write header
    ofl.println("Datum;Typ;Směr;Ticker;Množství;Kurs;Měna kursu;Poplatky;Měna poplatků;Trh;Vyporadani;Poznamka");

    // Start writing rows
    for (Transaction t : rows) {

      t.export(ofl);
    }

    ofl.close();
  }

  public void exportFIO(File file) throws Exception {

    // Show warning message
    // if (JOptionPane.showConfirmDialog(rootPane, "Pozor! Export do FIO formatu je
    // aktualne v utf-8 je tedy nutne zkonvertovat, treba pres iconv\n #iconv -f
    // utf-8 -t WINDOWS-1250 ./FIO_export.csv -o FIO_export_win1250.csv\n\nDale se
    // exportuji pouze obchody typu Cenny Papir a veskere transformace split,reverse
    // split zatim filtrovane\n", "Špatný typ výpočtu",
    // JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) !=
    // JOptionPane.OK_OPTION) {
    // return;
    // }

    java.io.PrintWriter ofl = new java.io.PrintWriter(new java.io.FileWriter(file));

    // Write header
    String Header = "Datum obchodu;Směr;Symbol;Cena;Počet;Měna;Objem v CZK;Poplatky v CZK;Objem v USD;Poplatky v USD;Objem v EUR;Poplatky v EUR;Text FIO;Datum vypořádání";
    // String Header_CP1250 = new String(Header.getBytes("Windows-1250"), "UTF-8");
    ofl.println(Header);

    // Start writing rows
    for (Transaction t : rows) {

      t.exportFIO(ofl);
    }

    ofl.close();
  }

  /**
   * Clear filtering of the rows
   */
  public void clearFilter() {
    if (filteredRows != null) {
      filteredRows = null;
      fireTableDataChanged();
    }
  }

  /**
   * Apply filter
   *
   * @param from  From date (inclusive)
   * @param to    To date (inclusive)
   * @param ticker Ticker to filter (or null to filter all tickers)
   * @param market Market to filter (or null to filter all markets)
   * @param type   Type to filter (or null to filter all types)
   * @param note   note to filter (or null to filter all notes)
   */
  public void applyFilter(Date from, Date to, String ticker, String market, String type, String note) {
    /* Preprocess to date */
    GregorianCalendar gc = new GregorianCalendar();
    gc.setTime(to);

    // Move time to next midnight
    gc.add(GregorianCalendar.DAY_OF_MONTH, 1);
    gc.set(GregorianCalendar.HOUR_OF_DAY, 0);
    gc.set(GregorianCalendar.MINUTE, 0);
    gc.set(GregorianCalendar.SECOND, 0);
    gc.set(GregorianCalendar.MILLISECOND, 0);

    Date to2 = gc.getTime();

    // Preprocess ticker
    boolean tickerWild = false;
    String tickerPrefix = null;
    int tpLen = 0;
    if (ticker != null) {
      if (ticker.length() > 0) {
        if (ticker.charAt(ticker.length() - 1) == '*') {
          // Found wildcard
          tickerWild = true;
          tickerPrefix = ticker.substring(0, ticker.length() - 1);
          tpLen = tickerPrefix.length();
        }
      }
    }

    /*
     * boolean noteWild = false;
     * String notePrefix = null;
     * int noteLen = 0;
     * if (note != null) {
     * if (note.length() > 0) {
     * if (note.charAt(ticker.length()-1) == '*') {
     * // Found wildcard
     * noteWild = true;
     * notePrefix = note.substring(0, ticker.length()-1);
     * noteLen = notePrefix.length();
     * }
     * }
     * }
     */
    // String notePrefix = null;
    // notePrefix = note.substring(0, note.length());
    String noteRegex = ".*" + note + ".*";
    // String noteRegex = "^this$";

    // Create set
    // Vector<Transaction> v = new Vector<Transaction>();
    Vector<Transaction> v = new Vector<Transaction>();

    // Run filter
    for (Transaction tx : rows) {
      if ((!tx.date.before(from)) && (tx.date.before(to2))) {
        // Date OK

        // Check ticker
        if (tickerWild) {
          if (tx.ticker.length() < tpLen)
            continue; // Too short - cannot match
          if (!tx.ticker.substring(0, tpLen).equalsIgnoreCase(tickerPrefix))
            continue; // No match
        } else {
          if (ticker != null) {
            if (!tx.ticker.equalsIgnoreCase(ticker))
              continue; // Different ticker - does not pass filter
          }
        }

        // Check market
        if (market != null) {
          if (!tx.market.equalsIgnoreCase(market))
            continue; // Different ticker - does not pass filter
        }

        // Check type
        if (type != null) {
          if (!tx.getStringType().equals(type))
            continue; // Different type - does not pass filter
        }

        // Check note

        if (note != null) {
          // if (!tx.note.matches(noteRegex)) continue; // No match
          // Avoid triggering NullPointerException we can't search empty lines
          if (tx.note != null) {
            if (!tx.note.matches(noteRegex))
              continue; // No match
            // and null notes lines also skip via continue
          } else
            continue;
        }

        // OK, add
        v.add(tx);
      }
    }

    // Set filtered rows and notify
    filteredRows = v;
    fireTableDataChanged();
  }
}
