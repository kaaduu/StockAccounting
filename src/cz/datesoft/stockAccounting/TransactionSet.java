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
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.io.File;
import java.io.PrintWriter;
import cz.datesoft.stockAccounting.imp.*;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.regex.Pattern;
import java.util.logging.Logger;

import cz.datesoft.stockAccounting.Settings;

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

  private static final class DeletedRow {
    final Transaction tx;
    final int originalIndex;

    DeletedRow(Transaction tx, int originalIndex) {
      this.tx = tx;
      this.originalIndex = originalIndex;
    }
  }

  private java.util.List<DeletedRow> lastDeletedRows = new java.util.ArrayList<>();

  /** Set of serials for recently updated transactions (for highlighting) */
  protected java.util.Set<Integer> updatedTransactionSerials = new java.util.HashSet<>();

  /** Per-transaction set of model columns that changed during updateDuplicateTransaction() */
  protected java.util.Map<Integer, java.util.Set<Integer>> updatedColumnsBySerial = new java.util.HashMap<>();

  /** Set of serials for recently inserted (imported) transactions (for highlighting) */
  protected java.util.Set<Integer> insertedTransactionSerials = new java.util.HashSet<>();

  /** Logger */
  private static final Logger logger = Logger.getLogger(TransactionSet.class.getName());

  /** Cache for ticker transformation relationships */
  private TransformationCache transformationCache;

  /** Stocks instance for transformation analysis (lazy initialized) */
  private Stocks stocksInstance;

   /** Serial counter */
   protected int serialCounter;

  // Serial repair info (set during load/loadAdd)
  private transient boolean serialsRepaired = false;
  private transient int serialDuplicatesFound = 0;

   /**
    * Last date we set
    */
   protected Date lastDateSet;

   /** Trading 212 API import constant */
   public final int IMPORT_FORMAT_TRADING212_API = 8;

  /** Column names */
  private String[] columnNames = { "Datum", "Typ", "Směr", "Ticker", "Množství", "Kurs", "Měna kursu", "Poplatky",
      "Měna poplatků", "Trh", "Datum vypořádání", "Broker", "ID účtu", "ID transakce", "Efekt", "Note", "Ignorovat" };

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

  /** Batch update mode for de-duplicating repeated updates. */
  private boolean batchUpdateInProgress = false;
  private java.util.Set<Integer> batchUpdatedTransactionSerials;

  /** Whether we are currently capturing undo data for an import operation. */
  private transient boolean importUndoCaptureActive = false;

  /** Creates a new instance of TransactionSet */
  public TransactionSet() {
    rows = new Vector<Transaction>();

    serialCounter = 1;

    cbmodel = new SortedSetComboBoxModel();

    transformationCache = new TransformationCache();
  }

  public boolean wereSerialsRepaired() {
    return serialsRepaired;
  }

  public int getSerialDuplicatesFound() {
    return serialDuplicatesFound;
  }

  public int getRowCountRaw() {
    return rows != null ? rows.size() : 0;
  }

  public int getSerialCounter() {
    return serialCounter;
  }

  private void normalizeSerialsIfNeeded() {
    // Detect duplicates / zeros
    java.util.Set<Integer> seen = new java.util.HashSet<>();
    int dups = 0;
    boolean hasZero = false;
    int max = 0;

    for (Transaction t : rows) {
      if (t == null) continue;
      int s = t.getSerial();
      if (s <= 0) {
        hasZero = true;
      }
      if (s > max) max = s;
      if (!seen.add(s)) {
        dups++;
      }
    }

    boolean counterMismatch = (serialCounter <= max);
    if (dups == 0 && !hasZero && !counterMismatch) {
      this.serialsRepaired = false;
      this.serialDuplicatesFound = 0;
      return;
    }

    System.out.println("INFO: Serial repair triggered: duplicates=" + dups + ", hasZero=" + hasZero + ", counterMismatch=" + counterMismatch + ", rows=" + (rows != null ? rows.size() : 0));

    // Renumber all rows deterministically in current order.
    int next = 1;
    for (Transaction t : rows) {
      if (t == null) continue;
      t.setSerial(next++);
    }
    serialCounter = next;

    // Clear serial-keyed runtime state
    clearHighlights();
    batchUpdateInProgress = false;
    batchUpdatedTransactionSerials = null;
    if (lastImportInserted != null) lastImportInserted.clear();
    if (lastImportUpdated != null) lastImportUpdated.clear();
    importUndoCaptureActive = false;

    this.serialsRepaired = true;
    this.serialDuplicatesFound = dups;
  }

  /**
   * Starts batch update mode. During batch updates, repeated updates of the
   * same transaction (by serial) are ignored.
   */
  public void startBatchUpdate() {
    batchUpdateInProgress = true;
    batchUpdatedTransactionSerials = new java.util.HashSet<>();
  }

  /** Ends batch update mode and refreshes the table. */
  public void endBatchUpdate() {
    batchUpdateInProgress = false;
    batchUpdatedTransactionSerials = null;
    fireTableDataChanged();
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
   * Parse date from text to date object.
   *
   * Supported formats:
   * - dd.MM.yyyy HH:mm
   * - dd.MM.yyyy HH:mm:ss
   */
  public static Date parseDate(String dateTxt) {
    GregorianCalendar cal = new GregorianCalendar();
    if (dateTxt == null) {
      return cal.getTime();
    }

    String a[] = dateTxt.split("[\\. :]");
    if (a.length != 5 && a.length != 6) {
      return cal.getTime();
    }

    cal.set(GregorianCalendar.DAY_OF_MONTH, Integer.parseInt(a[0]));
    cal.set(GregorianCalendar.MONTH, Integer.parseInt(a[1]) - 1);
    cal.set(GregorianCalendar.YEAR, Integer.parseInt(a[2]));
    cal.set(GregorianCalendar.HOUR_OF_DAY, Integer.parseInt(a[3]));
    cal.set(GregorianCalendar.MINUTE, Integer.parseInt(a[4]));
    if (a.length == 6) {
      cal.set(GregorianCalendar.SECOND, Integer.parseInt(a[5]));
    } else {
      cal.set(GregorianCalendar.SECOND, 0);
    }
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

  public void markInserted(Transaction tx) {
    if (tx == null) return;
    insertedTransactionSerials.add(tx.getSerial());
  }

  public boolean isRecentlyInserted(int row) {
    if (row < 0 || row >= rows.size()) {
      return false;
    }
    Transaction tx = getRowAt(row);
    if (tx == null) return false;
    return insertedTransactionSerials.contains(tx.getSerial());
  }

  /**
   * Returns the index of the first updated row, scanning in the current view order.
   * If filter is active, scans only visible rows.
   */
  public int findFirstUpdatedVisibleRowIndex() {
    if (filteredRows != null) {
      for (int i = 0; i < filteredRows.size(); i++) {
        Transaction tx = filteredRows.get(i);
        if (tx != null && updatedTransactionSerials.contains(tx.getSerial())) {
          return i;
        }
      }
      return -1;
    }

    for (int i = 0; i < rows.size(); i++) {
      Transaction tx = rows.get(i);
      if (tx != null && updatedTransactionSerials.contains(tx.getSerial())) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the index of the first inserted row, scanning in the current view order.
   * If filter is active, scans only visible rows.
   */
  public int findFirstInsertedVisibleRowIndex() {
    if (filteredRows != null) {
      for (int i = 0; i < filteredRows.size(); i++) {
        Transaction tx = filteredRows.get(i);
        if (tx != null && insertedTransactionSerials.contains(tx.getSerial())) {
          return i;
        }
      }
      return -1;
    }

    for (int i = 0; i < rows.size(); i++) {
      Transaction tx = rows.get(i);
      if (tx != null && insertedTransactionSerials.contains(tx.getSerial())) {
        return i;
      }
    }
    return -1;
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
        return tx.getBroker();
      case 12:
        return tx.getAccountId();
      case 13:
        return tx.getTxnId();
      case 14:
        return tx.getEffect();
      case 15:
        return tx.getNote();
      case 16:
        return tx.isDisabled();
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
      case 11: // Broker - read-only
      case 12: // AccountID - read-only
      case 13: // TxnID - read-only
      case 14: // Effect - read-only
        // These columns are derived from Note, cannot be edited directly
        return;
      case 15: // Note (moved from case 11)
        tx.setNote((String) value);
        fireTableCellUpdated(row, col);
        // Also notify derived columns to refresh
        fireTableCellUpdated(row, 11); // Broker
        fireTableCellUpdated(row, 12); // AccountID
        fireTableCellUpdated(row, 13); // TxnID
        fireTableCellUpdated(row, 14); // Effect
        break;
      case 16: // Ignorovat
        boolean disable;
        if (value instanceof Boolean) {
          disable = (Boolean) value;
        } else {
          String s2 = value == null ? "" : value.toString().trim();
          disable = s2.equalsIgnoreCase("ano") || s2.equals("1") || s2.equalsIgnoreCase("true") || s2.equalsIgnoreCase("y");
        }
        tx.setDisabled(disable);
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
        return java.util.Date.class;
      case 1:
      case 2:
      case 3:
      case 6:
      case 8:
      case 9:
      case 11:
        return String.class;
      case 10:
        return java.util.Date.class;
      case 5:
      case 7:
      case 4:
        return Double.class;
      case 16:
        return Boolean.class;
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
    // Columns 11-14 are read-only (derived from Note)
    if (column >= 11 && column <= 14) {
      return false;
    }
    // Ignorovat is editable only for real rows (not the extra last empty row)
    if (column == 16) {
      return row < ((filteredRows != null) ? filteredRows : rows).size();
    }
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

  public void toggleDisabledAt(int row) {
    Transaction tx = getRowAt(row);
    if (tx == null) return;
    tx.setDisabled(!tx.isDisabled());
    modified = true;
    fireTableRowsUpdated(row, row);
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

    // Reset Stocks instance and transformation cache for new data
    stocksInstance = null;
    transformationCache.invalidate();

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

    // Repair duplicate/broken serials (affects highlighting and batch update logic)
    normalizeSerialsIfNeeded();

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

  public int deleteRows(int[] viewRowIndices) {
    if (viewRowIndices == null || viewRowIndices.length == 0) {
      return 0;
    }

    lastDeletedRows.clear();

    // Resolve selected rows to Transaction objects first.
    java.util.LinkedHashSet<Transaction> selected = new java.util.LinkedHashSet<>();
    for (int viewRow : viewRowIndices) {
      try {
        if (filteredRows != null) {
          if (viewRow < 0 || viewRow >= filteredRows.size()) continue;
          selected.add(filteredRows.get(viewRow));
        } else {
          if (viewRow < 0 || viewRow >= rows.size()) continue;
          selected.add(rows.get(viewRow));
        }
      } catch (Exception e) {
        // ignore
      }
    }

    if (selected.isEmpty()) {
      return 0;
    }

    // Store original indices in 'rows' for undo.
    for (Transaction tx : selected) {
      int idx = rows.indexOf(tx);
      if (idx >= 0) {
        lastDeletedRows.add(new DeletedRow(tx, idx));
      }
    }

    // Remove from rows in descending index order so indices don't shift.
    lastDeletedRows.sort((a, b) -> Integer.compare(b.originalIndex, a.originalIndex));
    int deleted = 0;
    for (DeletedRow d : lastDeletedRows) {
      try {
        if (rows.remove(d.originalIndex) != null) {
          deleted++;
        }
      } catch (Exception e) {
        // ignore
      }
    }

    // Clear filtered view if active; UI will clear filter on undo as requested.
    if (filteredRows != null) {
      filteredRows = null;
    }

    if (deleted > 0) {
      modified = true;
      fireTableDataChanged();
    }

    return deleted;
  }

  public int undoLastDelete() {
    if (lastDeletedRows == null || lastDeletedRows.isEmpty()) {
      return 0;
    }

    // Reinsert in ascending original index.
    lastDeletedRows.sort((a, b) -> Integer.compare(a.originalIndex, b.originalIndex));
    int restored = 0;
    for (DeletedRow d : lastDeletedRows) {
      if (d == null || d.tx == null) continue;
      int idx = d.originalIndex;
      if (idx < 0) idx = 0;
      if (idx > rows.size()) idx = rows.size();
      rows.add(idx, d.tx);
      restored++;
    }

    lastDeletedRows.clear();
    modified = true;
    fireTableDataChanged();
    return restored;
  }

  /**
   * Get combo box model containing ticker names
   */
  public SortedSetComboBoxModel getTickersModel() {
    return cbmodel;
  }

  /**
   * Get combo box model containing unique broker names from all transactions
   */
  public SortedSetComboBoxModel getBrokersModel() {
    SortedSetComboBoxModel model = new SortedSetComboBoxModel();
    model.addElement(""); // Empty option for "no filter"
    
    for (Transaction tx : rows) {
      String broker = tx.getBroker();
      if (broker != null && !broker.isEmpty()) {
        model.putItem(broker);
      }
    }
    return model;
  }

  /**
   * Get combo box model containing unique account IDs from all transactions
   */
  public SortedSetComboBoxModel getAccountIdsModel() {
    SortedSetComboBoxModel model = new SortedSetComboBoxModel();
    model.addElement(""); // Empty option for "no filter"
    
    for (Transaction tx : rows) {
      String accountId = tx.getAccountId();
      if (accountId != null && !accountId.isEmpty()) {
        model.putItem(accountId);
      }
    }
    return model;
  }

  /**
   * Invalidates the transformation cache (called after imports/modifications)
   */
  public void invalidateTransformationCache() {
    if (transformationCache != null) {
      transformationCache.invalidate();
    }
  }

  /**
   * Gets the transformation cache for external access
   */
  public TransformationCache getTransformationCache() {
    return transformationCache;
  }

  /**
   * Gets all tickers related to the given ticker through transformations.
   * This is a convenience method for external access to transformation relationships.
   */
  public Set<String> getRelatedTickers(String ticker) {
    Stocks stocks = getStocksInstance();
    return transformationCache.getRelatedTickers(ticker, stocks);
  }

  /**
   * Gets or creates the Stocks instance for transformation analysis.
   * Lazy initialization - created only when first needed.
   */
  private Stocks getStocksInstance() {
    if (stocksInstance == null) {
      // Create Stocks instance and populate with all transactions
      // This builds the transformation history needed for smart filtering
      stocksInstance = new Stocks();
      try {
        for (Transaction tx : rows) {
          stocksInstance.applyTransaction(tx, false); // Don't throw on errors during filtering
        }
        logger.fine("Stocks instance created with " + rows.size() + " transactions for transformation analysis");

        // After building Stocks, analyze for additional TRANS-based transformations
        analyzeTransTransformations();

      } catch (Exception e) {
        logger.warning("Failed to build Stocks instance for transformations: " + e.getMessage());
        // Continue with null stocks - filtering will work but without transformations
      }
    }
    return stocksInstance;
  }

  /**
   * Analyzes transaction data for TRANS-based transformations that might not be
   * detected by the Stocks processing logic.
   */
  private void analyzeTransTransformations() {
    // Debug output
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("cz.datesoft.stockAccounting");
    if (logger.isLoggable(java.util.logging.Level.FINER)) {
      System.out.println("🔍 Starting TRANS transformation analysis for " + rows.size() + " transactions");
    }

    // Group transactions by date and time to find TRANS operation patterns
    Map<String, List<Transaction>> transactionsByTime = new HashMap<>();

    // Group transactions by minute bucket (ignore seconds) to find related operations
    int transOperationCount = 0;
    for (Transaction tx : rows) {
      if (tx.getDirection() == Transaction.DIRECTION_TRANS_ADD ||
          tx.getDirection() == Transaction.DIRECTION_TRANS_SUB) {

        java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
        cal.setTime(tx.getDate());
        cal.set(java.util.GregorianCalendar.SECOND, 0);
        cal.set(java.util.GregorianCalendar.MILLISECOND, 0);
        String timeKey = String.valueOf(cal.getTime().getTime()); // Group by minute bucket
        transactionsByTime.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(tx);
        transOperationCount++;

        // Debug SSL/RGLD transactions specifically
        if (logger.isLoggable(java.util.logging.Level.FINER) &&
            (tx.getTicker().equalsIgnoreCase("SSL") || tx.getTicker().equalsIgnoreCase("RGLD"))) {
          System.out.println("📊 Found SSL/RGLD TRANS operation: " +
                             (tx.getDirection() == Transaction.DIRECTION_TRANS_SUB ? "SUB" : "ADD") +
                             " " + tx.getTicker() + " " + tx.getAmount() + " at " + tx.getDate());
        }
      }
    }

     if (logger.isLoggable(java.util.logging.Level.FINER)) {
       System.out.println("📊 TRANS analysis: Found " + transOperationCount + " operations in " +
                          transactionsByTime.size() + " time groups");
     }

     // Analyze groups for transformation patterns
    for (Map.Entry<String, List<Transaction>> entry : transactionsByTime.entrySet()) {
      List<Transaction> group = entry.getValue();

       // Debug output for groups with SSL/RGLD
       boolean hasSslRgld = group.stream().anyMatch(tx ->
         tx.getTicker().equalsIgnoreCase("SSL") || tx.getTicker().equalsIgnoreCase("RGLD"));

       if (logger.isLoggable(java.util.logging.Level.FINER) && hasSslRgld) {
        System.out.println("🎯 Time group with SSL/RGLD: " + group.size() + " operations");
        for (Transaction tx : group) {
          System.out.println("   " +
                             (tx.getDirection() == Transaction.DIRECTION_TRANS_SUB ? "SUB" : "ADD") +
                             " " + tx.getTicker() + " " + tx.getAmount());
        }
      }

      // Look for TRANS_SUB followed by TRANS_ADD with different tickers
      Transaction transSub = null;
      Transaction transAdd = null;

      for (Transaction tx : group) {
        if (tx.getDirection() == Transaction.DIRECTION_TRANS_SUB) {
          transSub = tx;
        } else if (tx.getDirection() == Transaction.DIRECTION_TRANS_ADD) {
          transAdd = tx;
        }
      }

      // If we found both SUB and ADD with different tickers, it's a transformation
      if (transSub != null && transAdd != null &&
          !transSub.getTicker().equalsIgnoreCase(transAdd.getTicker())) {

        String fromTicker = transSub.getTicker().toUpperCase();
        String toTicker = transAdd.getTicker().toUpperCase();

        // Add the transformation relationship
        transformationCache.addRelationshipDirectly(fromTicker, toTicker);

        // Enhanced debug output for SSL->RGLD and general TRANS detections
        if (logger.isLoggable(java.util.logging.Level.FINER)) {
          System.out.println("🎯 DETECTED TRANS TRANSFORMATION: " + fromTicker + " → " + toTicker +
                             " (SUB: " + transSub.getAmount() + ", ADD: " + transAdd.getAmount() + ")");

          // Special highlighting for SSL->RGLD case
          if ((fromTicker.equals("SSL") && toTicker.equals("RGLD")) ||
              (fromTicker.equals("RGLD") && toTicker.equals("SSL"))) {
            System.out.println("🎉 SSL-RGLD TRANSFORMATION SUCCESSFULLY DETECTED!");
            System.out.println("   This will enable smart filtering for SSL ↔ RGLD");
          }
        }
      }
    }
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

      Transaction added = dstSet.addTransaction(tx.getDate(), tx.getDirection().intValue(), tx.getTicker(), tx.getAmount().doubleValue(),
          tx.getPrice().doubleValue(), tx.getPriceCurrency(), tx.getFee().doubleValue(), tx.getFeeCurrency(),
          tx.getMarket(), tx.getExecutionDate(), tx.getNote());

      // Preserve persisted metadata (Broker/AccountID/TxnID/Code) from the source.
      // This is important for imports where metadata is not encoded in Note (e.g. IBKR Flex corporate actions).
      String broker = tx.getBroker();
      if (broker != null && !broker.trim().isEmpty()) {
        added.setBroker(broker.trim());
      }
      String accountId = tx.getAccountId();
      if (accountId != null && !accountId.trim().isEmpty()) {
        added.setAccountId(accountId.trim());
      }
      String txnId = tx.getTxnId();
      if (txnId != null && !txnId.trim().isEmpty()) {
        added.setTxnId(txnId.trim());
      }
      String code = tx.getCode();
      if (code != null && !code.trim().isEmpty()) {
        added.setCode(code.trim());
      }

      // Mark as inserted for highlighting (import/merge only)
      dstSet.markInserted(added);

      // Record for undo (if enabled)
      dstSet.recordImportInserted(added);
    }

    // Sort destination
    dstSet.sort();
  }

  // --- Undo last import ---
  private static final class ImportUpdatedSnapshot {
    final Transaction target;
    final Transaction before;

    ImportUpdatedSnapshot(Transaction target, Transaction before) {
      this.target = target;
      this.before = before;
    }
  }

  private transient java.util.List<Transaction> lastImportInserted = new java.util.ArrayList<>();
  private transient java.util.List<ImportUpdatedSnapshot> lastImportUpdated = new java.util.ArrayList<>();

  public void beginImportUndoCapture() {
    if (lastImportInserted == null) lastImportInserted = new java.util.ArrayList<>();
    if (lastImportUpdated == null) lastImportUpdated = new java.util.ArrayList<>();
    lastImportInserted.clear();
    lastImportUpdated.clear();
    importUndoCaptureActive = true;
  }

  public void recordImportInserted(Transaction inserted) {
    if (inserted == null) return;
    if (lastImportInserted == null) lastImportInserted = new java.util.ArrayList<>();
    lastImportInserted.add(inserted);
  }

  public void recordImportUpdated(Transaction targetExisting, Transaction beforeSnapshot) {
    if (targetExisting == null || beforeSnapshot == null) return;
    if (lastImportUpdated == null) lastImportUpdated = new java.util.ArrayList<>();

    for (ImportUpdatedSnapshot s : lastImportUpdated) {
      if (s != null && s.target == targetExisting) {
        return;
      }
    }
    lastImportUpdated.add(new ImportUpdatedSnapshot(targetExisting, beforeSnapshot));
  }

  public boolean hasUndoImport() {
    return (lastImportInserted != null && !lastImportInserted.isEmpty())
        || (lastImportUpdated != null && !lastImportUpdated.isEmpty());
  }

  public int undoLastImport() {
    int changed = 0;

    if (lastImportInserted != null && !lastImportInserted.isEmpty()) {
      for (Transaction t : lastImportInserted) {
        if (t == null) continue;
        if (rows.remove(t)) {
          changed++;
        }
      }
      lastImportInserted.clear();
    }

    if (lastImportUpdated != null && !lastImportUpdated.isEmpty()) {
      for (ImportUpdatedSnapshot s : lastImportUpdated) {
        if (s == null || s.target == null || s.before == null) continue;
        s.target.restoreFrom(s.before);
        changed++;
      }
      lastImportUpdated.clear();
    }

    if (changed > 0) {
      clearHighlights();
      sort();
      modified = true;
      fireTableDataChanged();
    }

    // Undo capture is one-shot; keep lastImport* empty until next import.
    importUndoCaptureActive = false;

    return changed;
  }

  private boolean updateExistingInternal(Transaction existing, Transaction candidate, boolean forceUpdateDate) {
    if (existing == null || candidate == null) return false;

    if (batchUpdateInProgress && batchUpdatedTransactionSerials != null) {
      int serial = existing.getSerial();
      if (batchUpdatedTransactionSerials.contains(serial)) {
        return false;
      }
      batchUpdatedTransactionSerials.add(serial);
    }

    if (importUndoCaptureActive) {
      recordImportUpdated(existing, existing.deepCopy());
    }

    // Snapshot before values so we can highlight changed columns.
    java.util.Date oldDate = existing.getDate();
    java.util.Date oldExDate = existing.getExecutionDate();
    Double oldFee = existing.getFee();
    String oldFeeCur = existing.getFeeCurrency();
    String oldNote = existing.getNote();

    if (forceUpdateDate) {
      existing.updateFromTransactionWithTxnIdMatch(candidate);
    } else {
      // Default behavior from updateDuplicateTransaction
      String txnExisting = nullToEmpty(existing.getTxnId());
      String txnCandidate = nullToEmpty(candidate.getTxnId());
      boolean txnIdMatch = !txnExisting.isEmpty() && txnExisting.equalsIgnoreCase(txnCandidate);
      boolean ibLegacyMinuteMatch = isIbTradeLogLegacyMinuteMatch(candidate, existing);
      if (txnIdMatch || ibLegacyMinuteMatch) {
        existing.updateFromTransactionWithTxnIdMatch(candidate);
      } else {
        existing.updateFromTransaction(candidate);
      }
    }

    java.util.Set<Integer> changedCols = new java.util.HashSet<>();
    if (!objectsEqualDate(oldDate, existing.getDate())) {
      changedCols.add(0); // Datum
    }
    if (!objectsEqualDate(oldExDate, existing.getExecutionDate())) {
      changedCols.add(10); // Datum vypořádání
    }
    if (!doubleEqual(oldFee, existing.getFee())) {
      changedCols.add(7); // Poplatky
    }
    if (!stringEqualExact(oldFeeCur, existing.getFeeCurrency())) {
      changedCols.add(8); // Měna poplatků
    }
    if (!stringEqualExact(oldNote, existing.getNote())) {
      changedCols.add(15); // Note
    }
    if (!changedCols.isEmpty()) {
      updatedColumnsBySerial.put(existing.getSerial(), changedCols);
    } else {
      updatedColumnsBySerial.remove(existing.getSerial());
    }

    updatedTransactionSerials.add(existing.getSerial());
    return true;
  }

  /**
   * Batch update of duplicates during import.
   *
   * Includes special IB TradeLog legacy pairing when multiple identical trades exist
   * without TxnID (pairs candidates by TxnID order to existing rows by serial order
   * when group counts match).
   */
  public int updateDuplicateTransactions(java.util.Vector<Transaction> candidates) {
    if (candidates == null || candidates.isEmpty()) return 0;

    int updated = 0;
    java.util.ArrayList<Transaction> remaining = new java.util.ArrayList<>();

    for (Transaction candidate : candidates) {
      if (candidate == null) continue;
      if (updateDuplicateTransaction(candidate)) {
        updated++;
      } else {
        remaining.add(candidate);
      }
    }

    updated += updateIbTradeLogLegacyGroups(remaining);
    return updated;
  }

  /**
   * For IB TradeLog legacy rows (existing TxnID missing), determines which candidates can be
   * safely treated as updatable duplicates based on group-count equality.
   *
   * This is used by import preview to avoid showing "new" rows that will actually be
   * deterministically paired and updated during merge.
   */
  public java.util.Set<Transaction> getIbTradeLogLegacyBackfillableCandidates(java.util.List<Transaction> candidates) {
    java.util.Set<Transaction> res = new java.util.HashSet<>();
    if (candidates == null || candidates.isEmpty()) return res;

    java.util.Map<String, java.util.List<Transaction>> groups = new java.util.HashMap<>();
    for (Transaction c : candidates) {
      if (c == null) continue;
      if (!isIbBroker(c)) continue;
      String txn = nullToEmpty(c.getTxnId());
      if (txn.isEmpty()) continue;
      String key = ibLegacyGroupKey(c);
      groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(c);
    }

    for (java.util.Map.Entry<String, java.util.List<Transaction>> e : groups.entrySet()) {
      java.util.List<Transaction> groupCandidates = e.getValue();
      if (groupCandidates == null || groupCandidates.isEmpty()) continue;

      Transaction sample = groupCandidates.get(0);
      java.util.List<Transaction> existing = new java.util.ArrayList<>();
      for (Transaction ex : rows) {
        if (ex == null) continue;
        if (!isIbTradeLogLegacyMinuteMatch(sample, ex)) continue;
        existing.add(ex);
      }

      if (!existing.isEmpty() && existing.size() == groupCandidates.size()) {
        res.addAll(groupCandidates);
      }
    }

    return res;
  }

  private int updateIbTradeLogLegacyGroups(java.util.List<Transaction> candidates) {
    if (candidates == null || candidates.isEmpty()) return 0;

    // Group IB candidates by business key at minute precision (ignoring TxnID)
    java.util.Map<String, java.util.List<Transaction>> groups = new java.util.HashMap<>();
    for (Transaction c : candidates) {
      if (c == null) continue;
      if (!isIbBroker(c)) continue;
      String txn = nullToEmpty(c.getTxnId());
      if (txn.isEmpty()) continue;
      String key = ibLegacyGroupKey(c);
      groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(c);
    }

    int updated = 0;
    for (java.util.Map.Entry<String, java.util.List<Transaction>> e : groups.entrySet()) {
      java.util.List<Transaction> groupCandidates = e.getValue();
      if (groupCandidates == null || groupCandidates.isEmpty()) continue;

      // Find existing legacy rows (no TxnID) for this group.
      java.util.List<Transaction> existing = new java.util.ArrayList<>();
      Transaction sample = groupCandidates.get(0);
      for (Transaction ex : rows) {
        if (ex == null) continue;
        if (!isIbTradeLogLegacyMinuteMatch(sample, ex)) continue;
        existing.add(ex);
      }

      if (existing.size() != groupCandidates.size() || existing.isEmpty()) {
        continue; // do not guess
      }

      existing.sort((a, b) -> Integer.compare(a.getSerial(), b.getSerial()));
      groupCandidates.sort((a, b) -> compareTxnId(a.getTxnId(), b.getTxnId()));

      for (int i = 0; i < existing.size(); i++) {
        Transaction target = existing.get(i);
        Transaction cand = groupCandidates.get(i);
        if (updateExistingInternal(target, cand, true)) {
          updated++;
        }
      }
    }

    return updated;
  }

  private static int compareTxnId(String a, String b) {
    String sa = a == null ? "" : a.trim();
    String sb = b == null ? "" : b.trim();
    try {
      long la = Long.parseLong(sa);
      long lb = Long.parseLong(sb);
      return Long.compare(la, lb);
    } catch (Exception e) {
      return sa.compareToIgnoreCase(sb);
    }
  }

  private String ibLegacyGroupKey(Transaction tx) {
    // Minute-truncated timestamp + business fields (excluding TxnID)
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(tx.getDate());
    cal.set(GregorianCalendar.MILLISECOND, 0);
    cal.set(GregorianCalendar.SECOND, 0);
    long minute = cal.getTimeInMillis();

    return minute + "|" + tx.getDirection() + "|" + nullToEmpty(tx.getTicker()).toUpperCase() + "|"
        + safeD(tx.getAmount()) + "|" + safeD(tx.getPrice()) + "|" + nullToEmpty(tx.getPriceCurrency()).toUpperCase() + "|"
        + nullToEmpty(tx.getMarket()).toUpperCase() + "|" + nullToEmpty(tx.getAccountId()).toUpperCase();
  }

  private static String safeD(Double d) {
    if (d == null) return "";
    // normalize to avoid locale/grouping; tolerance matching is already in isIbTradeLogLegacyMinuteMatch
    return String.valueOf(d.doubleValue());
  }

  /**
   * Check if a transaction is a duplicate of any existing transaction
   * Uses business key comparison (excludes note, fee, and other non-essential fields)
   */
  public boolean isDuplicate(Transaction candidate) {
    if (candidate == null) return false;

    // 1) Prefer TxnID match when available.
    String txnC = nullToEmpty(candidate.getTxnId());
    if (!txnC.isEmpty()) {
      for (Transaction existing : rows) {
        if (existing == null) continue;
        String txnE = nullToEmpty(existing.getTxnId());
        if (!txnE.isEmpty() && txnC.equalsIgnoreCase(txnE)) {
          // If broker/account are present, require them to match too.
          String b1 = nullToEmpty(candidate.getBroker());
          String b2 = nullToEmpty(existing.getBroker());
          if (!b1.isEmpty() && !b2.isEmpty() && !b1.equalsIgnoreCase(b2)) {
            continue;
          }
          String a1 = nullToEmpty(candidate.getAccountId());
          String a2 = nullToEmpty(existing.getAccountId());
          if (!a1.isEmpty() && !a2.isEmpty() && !a1.equalsIgnoreCase(a2)) {
            continue;
          }
          return true;
        }
      }
    }

    // 2) Exact match (second precision).
    for (Transaction existing : rows) {
      if (existing == null) continue;
      if (isDuplicateTransaction(candidate, existing)) {
        return true;
      }
    }

    // 3) IB TradeLog legacy: allow matching old rows missing TxnID by minute (ignore seconds), but only if unique.
    Transaction legacy = findIbTradeLogLegacyUniqueMatch(candidate);
    return legacy != null;
  }

  /**
   * Filter duplicate transactions from a collection
   * Returns only transactions that are not duplicates in this set
   */
  public Vector<Transaction> filterDuplicates(Vector<Transaction> candidates) {
    Vector<Transaction> filtered = new Vector<>();
    int duplicatesSkipped = 0;

    for (Transaction candidate : candidates) {
      if (!isDuplicate(candidate)) {
        filtered.add(candidate);
      } else {
        duplicatesSkipped++;
        logger.info("Skipping duplicate transaction: " + candidate.getTicker() + " " +
                   candidate.getDate() + " " + candidate.getAmount());
      }
    }

    logger.info("Filtered " + duplicatesSkipped + " duplicate transactions, keeping " + filtered.size());
    return filtered;
  }

  /**
   * Find existing transaction that matches the candidate
   * Returns the matching transaction or null if no duplicate exists
   */
  public Transaction findDuplicateTransaction(Transaction candidate) {
    if (candidate == null) return null;

    // 1) Prefer TxnID match when available.
    String txnC = nullToEmpty(candidate.getTxnId());
    if (!txnC.isEmpty()) {
      for (Transaction existing : rows) {
        if (existing == null) continue;
        String txnE = nullToEmpty(existing.getTxnId());
        if (!txnE.isEmpty() && txnC.equalsIgnoreCase(txnE)) {
          // If broker/account are present, require them to match too.
          String b1 = nullToEmpty(candidate.getBroker());
          String b2 = nullToEmpty(existing.getBroker());
          if (!b1.isEmpty() && !b2.isEmpty() && !b1.equalsIgnoreCase(b2)) {
            continue;
          }
          String a1 = nullToEmpty(candidate.getAccountId());
          String a2 = nullToEmpty(existing.getAccountId());
          if (!a1.isEmpty() && !a2.isEmpty() && !a1.equalsIgnoreCase(a2)) {
            continue;
          }
          return existing;
        }
      }
    }

    // 2) Exact match (second precision).
    for (Transaction existing : rows) {
      if (existing == null) continue;
      if (isDuplicateTransaction(candidate, existing)) {
        return existing;
      }
    }

    // 3) IB TradeLog legacy: minute match (unique only).
    return findIbTradeLogLegacyUniqueMatch(candidate);
  }

  /**
   * Update existing transaction with data from candidate
   * Returns true if transaction was found and updated
   * Marks the transaction as recently updated for highlighting
   */
  public boolean updateDuplicateTransaction(Transaction candidate) {
    Transaction existing = findDuplicateTransaction(candidate);
    if (existing == null) return false;
    return updateExistingInternal(existing, candidate, false);
  }

  public boolean isRecentlyUpdatedColumn(int row, int modelCol) {
    if (!isRecentlyUpdated(row)) return false;
    Transaction tx = getRowAt(row);
    if (tx == null) return false;
    java.util.Set<Integer> cols = updatedColumnsBySerial.get(tx.getSerial());
    if (cols == null) return false;
    return cols.contains(modelCol);
  }

  private static boolean objectsEqualDate(java.util.Date d1, java.util.Date d2) {
    if (d1 == null && d2 == null) return true;
    if (d1 == null || d2 == null) return false;
    return d1.equals(d2);
  }

  private static boolean stringEqualExact(String s1, String s2) {
    if (s1 == null && s2 == null) return true;
    if (s1 == null || s2 == null) return false;
    return s1.equals(s2);
  }

  private static boolean doubleEqual(Double a, Double b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return Math.abs(a.doubleValue() - b.doubleValue()) < 0.0000001;
  }

  /**
   * Check if a transaction was recently updated (for highlighting)
   */
  public boolean isRecentlyUpdated(int row) {
    // Check if row is within bounds of actual data (excluding empty row)
    if (row < 0 || row >= rows.size()) {
      return false; // Out of bounds or empty row
    }
    
    Transaction tx = getRowAt(row);
    if (tx == null) return false;
    return updatedTransactionSerials.contains(tx.getSerial());
  }

  /**
   * Clear the recently updated set (called on app restart or explicit clear)
   */
  public void clearUpdatedHighlights() {
    updatedTransactionSerials.clear();
    fireTableDataChanged();
  }

  public void clearHighlights() {
    updatedTransactionSerials.clear();
    insertedTransactionSerials.clear();
    updatedColumnsBySerial.clear();
    fireTableDataChanged();
  }

  /**
   * Check if two transactions represent the same business transaction
   * Compares key business fields, excludes notes, fees, and auto-generated fields
   */
  private boolean isDuplicateTransaction(Transaction tx1, Transaction tx2) {
    // Prefer TxnID match when available (stable across time precision changes).
    // This prevents re-import from creating new rows when legacy data has :00 seconds
    // but new imports contain real seconds.
    String txn1 = nullToEmpty(tx1.getTxnId());
    String txn2 = nullToEmpty(tx2.getTxnId());
    if (!txn1.isEmpty() && txn1.equalsIgnoreCase(txn2)) {
      // If broker/account are present, require them to match too.
      String b1 = nullToEmpty(tx1.getBroker());
      String b2 = nullToEmpty(tx2.getBroker());
      if (!b1.isEmpty() && !b2.isEmpty() && !b1.equalsIgnoreCase(b2)) {
        return false;
      }
      String a1 = nullToEmpty(tx1.getAccountId());
      String a2 = nullToEmpty(tx2.getAccountId());
      if (!a1.isEmpty() && !a2.isEmpty() && !a1.equalsIgnoreCase(a2)) {
        return false;
      }
      return true;
    }

    // Compare dates (exact match, ignoring seconds/milliseconds)
    if (!datesEqual(tx1.getDate(), tx2.getDate())) {
      return false;
    }

    // Compare direction (buy/sell type)
    if (tx1.getDirection() != tx2.getDirection()) {
      return false;
    }

    // Compare ticker (case-insensitive)
    if (!stringEqual(tx1.getTicker(), tx2.getTicker())) {
      return false;
    }

    // Compare amount with tolerance (±0.01)
    if (!amountsEqual(tx1.getAmount(), tx2.getAmount())) {
      return false;
    }

    // Compare price with tolerance (±0.01)
    if (!amountsEqual(tx1.getPrice(), tx2.getPrice())) {
      return false;
    }

    // Compare currencies
    if (!stringEqual(tx1.getPriceCurrency(), tx2.getPriceCurrency())) {
      return false;
    }

    // Compare market (case-insensitive)
    if (!stringEqual(tx1.getMarket(), tx2.getMarket())) {
      return false;
    }

    // All key fields match - this is a duplicate
    return true;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s.trim();
  }

  private Transaction findIbTradeLogLegacyUniqueMatch(Transaction candidate) {
    if (candidate == null) return null;
    if (!isIbBroker(candidate)) return null;
    String txnC = nullToEmpty(candidate.getTxnId());
    if (txnC.isEmpty()) return null;

    Transaction match = null;
    int count = 0;
    for (Transaction existing : rows) {
      if (existing == null) continue;
      if (!isIbTradeLogLegacyMinuteMatch(candidate, existing)) continue;
      count++;
      if (count == 1) {
        match = existing;
      } else {
        // Ambiguous: do not auto-match.
        return null;
      }
    }
    return match;
  }

  private boolean isIbTradeLogLegacyMinuteMatch(Transaction candidate, Transaction existing) {
    if (candidate == null || existing == null) return false;
    if (!isIbBroker(candidate)) return false;

    // Candidate must have TxnID (new import). Existing must not (legacy data).
    String txnC = nullToEmpty(candidate.getTxnId());
    if (txnC.isEmpty()) return false;
    String txnE = nullToEmpty(existing.getTxnId());
    if (!txnE.isEmpty()) return false;

    // If existing broker/account are present, require IB / same account.
    String bE = nullToEmpty(existing.getBroker());
    if (!bE.isEmpty() && !bE.equalsIgnoreCase("IB")) return false;
    String accC = nullToEmpty(candidate.getAccountId());
    String accE = nullToEmpty(existing.getAccountId());
    if (!accC.isEmpty() && !accE.isEmpty() && !accC.equalsIgnoreCase(accE)) return false;

    // Minute-level date match (ignore seconds)
    if (!datesEqualMinute(candidate.getDate(), existing.getDate())) return false;

    // Business key fields must match
    if (candidate.getDirection() != existing.getDirection()) return false;
    if (!stringEqual(candidate.getTicker(), existing.getTicker())) return false;
    if (!amountsEqual(candidate.getAmount(), existing.getAmount())) return false;
    if (!amountsEqual(candidate.getPrice(), existing.getPrice())) return false;
    if (!stringEqual(candidate.getPriceCurrency(), existing.getPriceCurrency())) return false;
    if (!stringEqual(candidate.getMarket(), existing.getMarket())) return false;

    return true;
  }

  private static boolean isIbBroker(Transaction tx) {
    if (tx == null) return false;
    String b = nullToEmpty(tx.getBroker());
    if (!b.isEmpty()) {
      return b.equalsIgnoreCase("IB");
    }
    // Backward compatibility: some rows may only carry broker in note.
    java.util.Map<String, String> meta = Transaction.parseNoteMetadata(tx.getNote());
    String bn = meta.getOrDefault("broker", "");
    return bn != null && !bn.trim().isEmpty() && bn.trim().equalsIgnoreCase("IB");
  }

  private boolean datesEqualMinute(Date d1, Date d2) {
    if (d1 == null && d2 == null) return true;
    if (d1 == null || d2 == null) return false;

    GregorianCalendar cal1 = new GregorianCalendar();
    GregorianCalendar cal2 = new GregorianCalendar();
    cal1.setTime(d1);
    cal2.setTime(d2);
    cal1.set(GregorianCalendar.MILLISECOND, 0);
    cal2.set(GregorianCalendar.MILLISECOND, 0);
    cal1.set(GregorianCalendar.SECOND, 0);
    cal2.set(GregorianCalendar.SECOND, 0);
    return cal1.getTime().equals(cal2.getTime());
  }

  /**
   * Compare dates for equality at second precision (milliseconds ignored).
   */
  private boolean datesEqual(Date d1, Date d2) {
    if (d1 == null && d2 == null) return true;
    if (d1 == null || d2 == null) return false;

    GregorianCalendar cal1 = new GregorianCalendar();
    GregorianCalendar cal2 = new GregorianCalendar();

    cal1.setTime(d1);
    cal2.setTime(d2);

    // Clear milliseconds for comparison
    cal1.set(GregorianCalendar.MILLISECOND, 0);
    cal2.set(GregorianCalendar.MILLISECOND, 0);

    return cal1.getTime().equals(cal2.getTime());
  }

  /**
   * Compare strings for equality, handling null values
   */
  private boolean stringEqual(String s1, String s2) {
    if (s1 == null && s2 == null) return true;
    if (s1 == null || s2 == null) return false;
    return s1.equalsIgnoreCase(s2);
  }

  /**
   * Compare amounts with tolerance for floating point precision
   */
  private boolean amountsEqual(Double a1, Double a2) {
    if (a1 == null && a2 == null) return true;
    if (a1 == null || a2 == null) return false;

    final double TOLERANCE = 0.01; // Allow ±1 cent/penny difference
    return Math.abs(a1 - a2) <= TOLERANCE;
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
    ofl.println("Datum;Typ;Směr;Ticker;Množství;Kurs;Měna kursu;Poplatky;Měna poplatků;Trh;Vyporadani;Broker;ID účtu;ID transakce;Efekt;Poznamka");

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
  public void applyFilter(Date from, Date to, String ticker, String market, String type, String note, String broker, String accountId, String effect) {
    // DEFENSIVE: Handle null dates to prevent NullPointerException
    if (from == null) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.set(1900, 0, 1, 0, 0, 0); // 1900-01-01 00:00:00.000
      cal.set(GregorianCalendar.MILLISECOND, 0);
      from = cal.getTime();
      logger.warning("applyFilter: 'from' date was null, using default 1900-01-01");
    }

    if (to == null) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.setTime(new Date());
      cal.set(GregorianCalendar.HOUR_OF_DAY, 23);
      cal.set(GregorianCalendar.MINUTE, 59);
      cal.set(GregorianCalendar.SECOND, 59);
      cal.set(GregorianCalendar.MILLISECOND, 999);
      to = cal.getTime();
      logger.warning("applyFilter: 'to' date was null, using today end-of-day");
    }

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
            // Smart filtering: include transformation-related tickers
            // Get all tickers related to the filter ticker through transformations
            Stocks stocks = getStocksInstance();
            Set<String> relatedTickers = transformationCache.getRelatedTickers(ticker, stocks);

            // Debug output for filtering (only when debug enabled)
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("cz.datesoft.stockAccounting");
            if (logger.isLoggable(java.util.logging.Level.FINER)) {
                System.out.println("SMART FILTER: '" + ticker + "' -> related tickers: " + relatedTickers);
                System.out.println("SMART FILTER: Cache stats: " + transformationCache.getCacheStats());
            }
            if (!relatedTickers.contains(tx.ticker.toUpperCase())) {
              continue; // No match with ticker or its transformation relatives
            }
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

        // Check broker
        if (broker != null && !broker.isEmpty()) {
          String txBroker = tx.getBroker();
          if (txBroker == null || !txBroker.equalsIgnoreCase(broker)) {
            continue; // Broker doesn't match
          }
        }

        // Check account ID
        if (accountId != null && !accountId.isEmpty()) {
          String txAccountId = tx.getAccountId();
          if (txAccountId == null || !txAccountId.equalsIgnoreCase(accountId)) {
            continue; // Account ID doesn't match
          }
        }

        // Check effect
        if (effect != null && !effect.isEmpty()) {
          String txEffect = tx.getEffect();
          if (txEffect == null || txEffect.isEmpty()) {
            continue; // No effect to match against
          }
          // Check if the filter effect appears in the comma-separated effect string
          boolean effectMatches = false;
          String[] txEffects = txEffect.split(", ");
          for (String txEff : txEffects) {
            if (txEff.trim().equalsIgnoreCase(effect)) {
              effectMatches = true;
              break;
            }
          }
          if (!effectMatches) {
            continue; // Effect doesn't match
          }
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
