/*
 * ImportWindow.java
 *
 * Created on 8. listopad 2006, 21:57
 */

package cz.datesoft.stockAccounting;

import com.toedter.calendar.JDateChooser;
import java.awt.GridBagConstraints;
import java.awt.Dimension;
import java.io.File;
import java.util.Date;
import javax.swing.JOptionPane;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;
import java.util.GregorianCalendar;

/**
 *
 * @author lemming2
 */
public class ImportWindow extends javax.swing.JFrame {
  // Start / end date
  JDateChooser startDate;
  JDateChooser endDate;

  // Transaction database
  TransactionSet transactions;

  // File we are importing
  File currentFile;

   // Main window
   MainWindow mainWindow;

    // Flag to prevent multiple import triggers during file selection
    private boolean importInProgress = false;

    // Current import format (0 = none selected, overrides UI state when set programmatically)
    private int currentImportFormat = 0;

    // Store duplicates that will be updated if checkbox is checked
    private Vector<Transaction> duplicatesToUpdate = new Vector<>();

     // Trading 212 specific components
    javax.swing.JComboBox<String> cbTrading212Year;

  // Trading 212 import state
  private Trading212ImportState importState;
  
  // Trading 212 UI components for cache/refresh
  private javax.swing.JButton bRefreshFromApi;
  private javax.swing.JLabel lblCacheStatus;
  
  // IBKR Flex specific components
  private javax.swing.JButton bIBKRFlexFetch;          // "Načíst z IBKR"
  private javax.swing.JButton bIBKRFlexFile;           // "Načíst ze souboru"
  private javax.swing.JButton bIBKRFlexClear;          // "Vymazat náhled"
  private javax.swing.JButton bIBKRFlexRefreshPreview;  // "Obnovit náhled"
  private javax.swing.JCheckBox cbIBKRFlexIncludeCorporateActions; // include Transformace
  private javax.swing.JCheckBox cbIBKRFlexCaRS;
  private javax.swing.JCheckBox cbIBKRFlexCaTC;
  private javax.swing.JCheckBox cbIBKRFlexCaIC;
  private javax.swing.JCheckBox cbIBKRFlexCaTO;
  private javax.swing.JComboBox<String> cbIBKRFlexImportMode; // import mode (trades/transformations)
  private javax.swing.JLabel lblIBKRFlexStatus;        // Status label
  private javax.swing.JPanel pIBKRFlexButtons;         // Left-aligned container for IBKR buttons
  private javax.swing.JButton bIBKRFlexAssetFilter;          // AssetClass filter button
  private javax.swing.JPopupMenu pmIBKRFlexAssetFilter;      // Popup menu for multi-select
  private javax.swing.JCheckBoxMenuItem miIBKRAssetAll;
  private javax.swing.JCheckBoxMenuItem miIBKRAssetSTK;
  private javax.swing.JCheckBoxMenuItem miIBKRAssetOPT;
  private javax.swing.JCheckBoxMenuItem miIBKRAssetFUT;
  private javax.swing.JCheckBoxMenuItem miIBKRAssetCASH;
  private javax.swing.JCheckBox cbIBKRFlexUpdateDups;  // Update duplicates checkbox (reuse existing)
  private IBKRFlexParser lastIBKRParser = null;        // Store parser reference for statistics

  // Local file selection components (for file-based imports)
  private javax.swing.JButton bSelectFile;
  private javax.swing.JLabel lSelectedFile;

  private String lastIbkrCsvContent = null;
  private String lastIbkrSourceLabel = null;
  private boolean ibkrPreviewDirty = false;

  private static final int IBKR_MODE_TRADES_AND_TRANS = 0;
  private static final int IBKR_MODE_TRADES_ONLY = 1;
  private static final int IBKR_MODE_TRANS_ONLY = 2;

  // Obsolete format warning
  private static final String OBSOLETE_FORMAT_WARNING = "\u26a0\ufe0f Obsolete - code unmaintained";
  private static final String FORMAT_LABEL_DEFAULT = "Formát:";
  private static final String OBSOLETE_FORMAT_WARNING_HTML =
      "<html>Formát: <span style='color:#c00000;font-weight:bold'>" +
      OBSOLETE_FORMAT_WARNING +
      "</span></html>";

  /** Creates new form ImportWindow */
  public ImportWindow(java.awt.Frame parent, boolean modal) {
    super("Import souboru");
    initComponents();

    mainWindow = (MainWindow) parent;

    // Make import window at least as large as main window (if available)
    adjustSizeToParent();

    // Set window properties
    this.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
    this.setLocationByPlatform(true);
    this.setSize(800, 550);
    this.setResizable(true); // Enable maximize button

    // Initialize import state (constructor automatically loads from Settings)
    importState = new Trading212ImportState();

    GridBagConstraints gbc;

    startDate = new JDateChooser();
    startDate.setPreferredSize(new Dimension(200, 20));

    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(startDate, gbc);

    // Clear header for not-imported rows
    // niTable.setTableHeader(null);
    // niScrollPane.setColumnHeaderView(null);

    // Removed automatic import trigger on date change - import should only happen on explicit user action

    endDate = new JDateChooser();
    endDate.setPreferredSize(new Dimension(100, 20));

    gbc = new GridBagConstraints();
    gbc.gridx = 3;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(endDate, gbc);

    endDate.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
      public void propertyChange(java.beans.PropertyChangeEvent evt) {
        loadImport();
      }
    });

    getContentPane().doLayout();

    transactions = new TransactionSet();
    // Preview table should not show row number column
    transactions.setShowRowNumberColumn(false);
    table.setModel(transactions);

    table.getColumnModel().getColumn(0).setPreferredWidth(200);
    table.getColumnModel().getColumn(0).setCellRenderer(new CZDateRenderer());

    table.getColumnModel().getColumn(10).setCellRenderer(new CZDateRenderer());

    // niTable.setTableHeader(new JTableHeader());
    
    // Restore last checkbox state from settings
    cbUpdateDuplicates.setSelected(cz.datesoft.stockAccounting.Settings.getUpdateDuplicatesOnImport());
    
    updateWindowTitle(); // Set initial window title

    // Initial warning state based on default selection
    updateObsoleteFormatWarning();
  }

  private static boolean matchesAnyExtension(String nameLower, String... extensions) {
    if (nameLower == null) return false;
    for (String ext : extensions) {
      if (ext == null || ext.isEmpty()) continue;
      if (nameLower.endsWith(ext)) return true;
    }
    return false;
  }

  private String[] getFileExtensionsForFormat(int formatIndex) {
    // Indexes follow cbFormat model:
    // 1 Fio CSV
    // 2 BrokerJet HTML (legacy)
    // 3 IB TradeLog
    // 4 IB FlexQuery Trades only CSV (legacy)
    // 5/6 T212 Invest CSV
    // 7 Revolut CSV
    // 8 Trading 212 API (no file)
    // 9 IBKR Flex (API/file via dedicated buttons)
    switch (formatIndex) {
      case 1:
        return new String[] { ".csv" };
      case 2:
        return new String[] { ".htm", ".html" };
      case 3:
        return new String[] { ".tlg" };
      case 4:
        return new String[] { ".csv" };
      case 5:
      case 6:
        return new String[] { ".csv" };
      case 7:
        return new String[] { ".csv" };
      default:
        return null;
    }
  }

  private boolean isLocalFileFormat(int formatIndex) {
    // Everything except "<vyberte>", API formats and IBKR Flex (which has its own API/file buttons).
    if (formatIndex <= 0) return false;
    if (formatIndex == 8) return false; // Trading 212 API
    if (formatIndex == 9) return false; // IBKR Flex
    return true;
  }

  private void updateSelectedFileLabel() {
    if (lSelectedFile == null) return;
    if (currentFile == null) {
      lSelectedFile.setText("(soubor nevybrán)");
    } else {
      lSelectedFile.setText(currentFile.getName());
    }
  }

  private void selectLocalImportFile() {
    int formatIndex = cbFormat != null ? cbFormat.getSelectedIndex() : 0;
    if (!isLocalFileFormat(formatIndex)) {
      return;
    }

    java.awt.FileDialog dialog = new java.awt.FileDialog(this, "Importovat soubor", java.awt.FileDialog.LOAD);

    String loc = Settings.getImportDirectory();
    if (loc != null) {
      dialog.setDirectory(loc);
    }

    String[] exts = getFileExtensionsForFormat(formatIndex);
    if (exts != null && exts.length > 0) {
      dialog.setFilenameFilter((dir, name) -> {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return matchesAnyExtension(n, exts);
      });
    }

    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName == null) {
      return;
    }

    currentFile = new java.io.File(dialog.getDirectory(), fileName);
    Settings.setImportDirectory(dialog.getDirectory());
    Settings.save();

    // Archive selected local file into unified cache for reproducibility.
    try {
      String brokerKey = "import";
      String prefix = "local";
      if (formatIndex == 1) {
        brokerKey = "fio";
        prefix = "fio";
      } else if (formatIndex == 2) {
        brokerKey = "brokerjet";
        prefix = "brokerjet";
      } else if (formatIndex == 3) {
        brokerKey = "ib";
        prefix = "ib_tradelog";
      } else if (formatIndex == 4) {
        brokerKey = "ib";
        prefix = "ib_flexquery_legacy";
      } else if (formatIndex == 5 || formatIndex == 6) {
        brokerKey = "trading212";
        prefix = "t212_csv";
      } else if (formatIndex == 7) {
        brokerKey = "revolut";
        prefix = "revolut_csv";
      }

      java.nio.file.Path cached = CacheManager.archiveFile(brokerKey, CacheManager.Source.FILE,
          prefix + "_" + currentFile.getName(), currentFile.toPath());
      // Use cached copy for import.
      currentFile = cached.toFile();
    } catch (Exception e) {
      // Best effort
    }

    updateSelectedFileLabel();

    // Refresh preview immediately after selecting the file.
    loadImport();
  }

  private void adjustSizeToParent() {
    try {
      // ImportWindow is a JFrame, so getOwner() is often null.
      // Use the provided MainWindow reference when available.
      java.awt.Window parent = mainWindow;
      if (parent == null) {
        parent = getOwner();
      }
      if (parent == null) return;

      java.awt.Dimension d = parent.getSize();
      if (d == null || d.width <= 0 || d.height <= 0) {
        return;
      }

      // Match the main window size for a consistent UX.
      setMinimumSize(d);
      setSize(d);
      setLocationRelativeTo(parent);
    } catch (Exception e) {
      // Best-effort only; sizing should not break import flow
    }
  }

  private void markIbkrPreviewDirty(String statusMessage) {
    if (!isIBKRFlexFormat()) return;
    if (lastIbkrCsvContent == null) return;

    ibkrPreviewDirty = true;
    if (bIBKRFlexRefreshPreview != null) {
      bIBKRFlexRefreshPreview.setEnabled(true);
    }
    if (statusMessage != null && lblIBKRFlexStatus != null) {
      lblIBKRFlexStatus.setText(statusMessage);
    }
  }

  private void clearIbkrCachedData() {
    lastIbkrCsvContent = null;
    lastIbkrSourceLabel = null;
    ibkrPreviewDirty = false;
    if (bIBKRFlexRefreshPreview != null) {
      bIBKRFlexRefreshPreview.setEnabled(false);
    }
  }

  private void refreshIbkrPreviewFromCachedCsv() {
    if (!isIBKRFlexFormat()) return;
    if (lastIbkrCsvContent == null) {
      if (lblIBKRFlexStatus != null) {
        lblIBKRFlexStatus.setText("Nejprve načtěte data (API nebo soubor)");
      }
      return;
    }

    if (lblIBKRFlexStatus != null) {
      lblIBKRFlexStatus.setText("Obnovuji náhled...");
    }

    try {
      IBKRFlexParser parser = new IBKRFlexParser();
      parser.setAllowedAssetClasses(getSelectedIbkrAssetClasses());
      parser.setIncludeCorporateActions(cbIBKRFlexIncludeCorporateActions == null || cbIBKRFlexIncludeCorporateActions.isSelected());
      parser.setAllowedCorporateActionTypes(getSelectedIbkrCorporateActionTypes());
      int mode = getIbkrImportMode();
      parser.setIncludeTrades(mode != IBKR_MODE_TRANS_ONLY);
      if (mode == IBKR_MODE_TRADES_ONLY) {
        parser.setIncludeCorporateActions(false);
      } else if (mode == IBKR_MODE_TRANS_ONLY) {
        parser.setIncludeCorporateActions(true);
      }
      Vector<Transaction> parsedTransactions = parser.parseCsvReport(lastIbkrCsvContent);
      lastIBKRParser = parser;

      clearPreview();

      // Disambiguate rare collisions caused by minute-level timestamp precision.
      // This keeps IBKR Flex re-import stable: update one existing row, insert the other(s).
      disambiguateIbkrDuplicateCollisions(mainWindow.getTransactionDatabase(), parsedTransactions);

      Vector<Transaction> filteredTransactions = mainWindow.getTransactionDatabase().filterDuplicates(parsedTransactions);
      int duplicatesFiltered = parsedTransactions.size() - filteredTransactions.size();

      duplicatesToUpdate.clear();
      if (cbUpdateDuplicates.isSelected() && duplicatesFiltered > 0) {
        for (Transaction candidate : parsedTransactions) {
          if (!filteredTransactions.contains(candidate)) {
            duplicatesToUpdate.add(candidate);
          }
        }
      }

      transactions.rows.addAll(filteredTransactions);
      transactions.fireTableDataChanged();

      String previewText = "Náhled (" + filteredTransactions.size() + " záznamů)";
      if (duplicatesFiltered > 0) {
        if (cbUpdateDuplicates.isSelected()) {
          previewText += " - " + duplicatesFiltered + " duplikátů k aktualizaci";
        } else {
          previewText += " - " + duplicatesFiltered + " duplikátů vyfiltrováno";
        }
      }
      previewText += ":";
      lPreview.setText(previewText);

      String statusMsg = "Náhled obnoven";
      if (lastIbkrSourceLabel != null) {
        statusMsg += " (" + lastIbkrSourceLabel + ")";
      }
      statusMsg += ": " + filteredTransactions.size() + " transakcí";
      if (duplicatesFiltered > 0) {
        statusMsg += ", " + duplicatesFiltered + " duplikátů";
      }
      if (parser.getImportedCorporateActionCount() > 0 || parser.getSkippedZeroNetCount() > 0) {
        statusMsg += ", " + parser.getImportedCorporateActionCount() + " korp. akce";
        if (parser.getSkippedZeroNetCount() > 0) {
          statusMsg += ", " + parser.getSkippedZeroNetCount() + " přeskočeno";
        }
      }
      lblIBKRFlexStatus.setText(statusMsg);

      updateIBKRFlexButtonState();

      ibkrPreviewDirty = false;
      if (bIBKRFlexRefreshPreview != null) {
        bIBKRFlexRefreshPreview.setEnabled(false);
      }

    } catch (Exception e) {
      System.err.println("[IBKR:REFRESH:ERROR] Failed to refresh preview: " + e.getMessage());
      e.printStackTrace();
      if (lblIBKRFlexStatus != null) {
        lblIBKRFlexStatus.setText("Chyba při obnovení náhledu: " + e.getMessage());
      }
    }
  }

  private java.util.Set<String> getSelectedIbkrAssetClasses() {
    // null => no filter (import everything including unknown AssetClass)
    if (miIBKRAssetAll == null) return null;
    if (miIBKRAssetAll.isSelected()) return null;

    java.util.Set<String> res = new java.util.HashSet<>();
    if (miIBKRAssetSTK != null && miIBKRAssetSTK.isSelected()) res.add("STK");
    if (miIBKRAssetOPT != null && miIBKRAssetOPT.isSelected()) res.add("OPT");
    if (miIBKRAssetFUT != null && miIBKRAssetFUT.isSelected()) res.add("FUT");
    if (miIBKRAssetCASH != null && miIBKRAssetCASH.isSelected()) res.add("CASH");

    // Never allow "none"; fallback to all/no-filter
    if (res.isEmpty()) {
      miIBKRAssetAll.setSelected(true);
      return null;
    }

    return res;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  /**
   * IBKR Flex can contain multiple consolidated trades within the same minute.
   * StockAccounting stores timestamps only to minute precision (seconds are cleared),
   * so two different trades can become indistinguishable and be treated as duplicates.
   *
   * When multiple IBKR candidates match the same existing transaction, keep one as
   * a true duplicate (to update the existing row) and shift the others by +N minutes
   * so they can be imported as separate rows.
   *
   * This is deterministic (stable on re-import) because ordering is based on TxnID.
   */
  private void disambiguateIbkrDuplicateCollisions(TransactionSet db, Vector<Transaction> candidates) {
    if (db == null || candidates == null || candidates.isEmpty()) return;

    java.util.Map<Integer, Transaction> existingBySerial = new java.util.HashMap<>();
    java.util.Map<Integer, java.util.List<Transaction>> matchesBySerial = new java.util.HashMap<>();

    for (Transaction c : candidates) {
      if (c == null) continue;
      if (!"IB".equalsIgnoreCase(c.getBroker())) continue;

      Transaction existing = db.findDuplicateTransaction(c);
      if (existing == null) continue;

      existingBySerial.putIfAbsent(existing.getSerial(), existing);
      matchesBySerial.computeIfAbsent(existing.getSerial(), k -> new java.util.ArrayList<>()).add(c);
    }

    for (java.util.Map.Entry<Integer, java.util.List<Transaction>> e : matchesBySerial.entrySet()) {
      java.util.List<Transaction> group = e.getValue();
      if (group == null || group.size() <= 1) continue;

      Transaction existing = existingBySerial.get(e.getKey());
      String existingTxnId = existing != null ? nullToEmpty(existing.getTxnId()) : "";

      Transaction base = null;
      if (!existingTxnId.isEmpty()) {
        for (Transaction t : group) {
          if (existingTxnId.equals(nullToEmpty(t.getTxnId()))) {
            base = t;
            break;
          }
        }
      }

      // Deterministic ordering for shifting.
      java.util.Comparator<Transaction> stableOrder = java.util.Comparator
          .comparing((Transaction t) -> nullToEmpty(t.getTxnId()))
          .thenComparing((Transaction t) -> t.getExecutionDate(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
          .thenComparing((Transaction t) -> t.getPrice(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
          .thenComparing((Transaction t) -> Math.abs(t.getAmount() == null ? 0.0 : t.getAmount()));

      if (base == null) {
        // No existing TxnID match (old TradeLog rows), fall back to stable ordering.
        group.sort(stableOrder);
        base = group.get(0);
      }

      java.util.List<Transaction> toShift = new java.util.ArrayList<>();
      for (Transaction t : group) {
        if (t != base) {
          toShift.add(t);
        }
      }
      toShift.sort(stableOrder);

      // Keep base as-is (updates existing). Shift the rest by +1s, +2s, ...
      for (int i = 0; i < toShift.size(); i++) {
        shiftTransactionBySeconds(toShift.get(i), i + 1);
      }
    }
  }

  private void shiftTransactionBySeconds(Transaction tx, int seconds) {
    if (tx == null) return;
    if (seconds <= 0) return;

    java.util.Date d = tx.getDate();
    if (d != null) {
      java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
      cal.setTime(d);
      cal.add(java.util.GregorianCalendar.SECOND, seconds);
      tx.setDate(cal.getTime());
    }

    java.util.Date ex = tx.getExecutionDate();
    if (ex != null) {
      java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
      cal.setTime(ex);
      cal.add(java.util.GregorianCalendar.SECOND, seconds);
      tx.setExecutionDate(cal.getTime());
    }

    String note = tx.getNote();
    String marker = "|TimeShift:+" + seconds + "s";
    if (note == null || note.isEmpty()) {
      tx.setNote(marker.substring(1));
    } else if (!note.contains(marker)) {
      tx.setNote(note + marker);
    }
  }

  private int getIbkrImportMode() {
    if (cbIBKRFlexImportMode == null) return IBKR_MODE_TRADES_AND_TRANS;
    int idx = cbIBKRFlexImportMode.getSelectedIndex();
    if (idx < 0) return IBKR_MODE_TRADES_AND_TRANS;
    return idx;
  }

  private void applyIbkrImportModeToUi() {
    if (!isIBKRFlexFormat()) return;

    int mode = getIbkrImportMode();
    boolean transOnly = (mode == IBKR_MODE_TRANS_ONLY);

    // Disable trade filters when importing only transformations
    if (bIBKRFlexAssetFilter != null) {
      bIBKRFlexAssetFilter.setEnabled(!transOnly);
    }

    if (cbUpdateDuplicates != null) {
      cbUpdateDuplicates.setEnabled(!transOnly);
    }

    boolean enableCaTypes = (cbIBKRFlexIncludeCorporateActions != null && cbIBKRFlexIncludeCorporateActions.isSelected());
    if (mode == IBKR_MODE_TRADES_ONLY) {
      enableCaTypes = false;
    }
    if (cbIBKRFlexCaRS != null) cbIBKRFlexCaRS.setEnabled(enableCaTypes);
    if (cbIBKRFlexCaTC != null) cbIBKRFlexCaTC.setEnabled(enableCaTypes);
    if (cbIBKRFlexCaIC != null) cbIBKRFlexCaIC.setEnabled(enableCaTypes);
    if (cbIBKRFlexCaTO != null) cbIBKRFlexCaTO.setEnabled(enableCaTypes);
  }

  private java.util.Set<String> getSelectedIbkrCorporateActionTypes() {
    java.util.Set<String> out = new java.util.HashSet<>();
    if (cbIBKRFlexCaRS != null && cbIBKRFlexCaRS.isSelected()) out.add("RS");
    if (cbIBKRFlexCaTC != null && cbIBKRFlexCaTC.isSelected()) out.add("TC");
    if (cbIBKRFlexCaIC != null && cbIBKRFlexCaIC.isSelected()) out.add("IC");
    if (cbIBKRFlexCaTO != null && cbIBKRFlexCaTO.isSelected()) out.add("TO");
    return out;
  }

  private void syncIbkrAssetFilterState(javax.swing.AbstractButton source) {
    // Rules:
    // - If "Vše" selected => deselect specific.
    // - If any specific selected => deselect "Vše".
    // - If none selected => select "Vše".
    if (miIBKRAssetAll == null) return;

    if (source == miIBKRAssetAll && miIBKRAssetAll.isSelected()) {
      if (miIBKRAssetSTK != null) miIBKRAssetSTK.setSelected(false);
      if (miIBKRAssetOPT != null) miIBKRAssetOPT.setSelected(false);
      if (miIBKRAssetFUT != null) miIBKRAssetFUT.setSelected(false);
      if (miIBKRAssetCASH != null) miIBKRAssetCASH.setSelected(false);
    } else if (source != null && source != miIBKRAssetAll) {
      // A specific one changed
      boolean anySpecific =
          (miIBKRAssetSTK != null && miIBKRAssetSTK.isSelected()) ||
          (miIBKRAssetOPT != null && miIBKRAssetOPT.isSelected()) ||
          (miIBKRAssetFUT != null && miIBKRAssetFUT.isSelected()) ||
          (miIBKRAssetCASH != null && miIBKRAssetCASH.isSelected());
      if (anySpecific) {
        miIBKRAssetAll.setSelected(false);
      }
    }

    boolean anySelected = miIBKRAssetAll.isSelected() ||
        (miIBKRAssetSTK != null && miIBKRAssetSTK.isSelected()) ||
        (miIBKRAssetOPT != null && miIBKRAssetOPT.isSelected()) ||
        (miIBKRAssetFUT != null && miIBKRAssetFUT.isSelected()) ||
        (miIBKRAssetCASH != null && miIBKRAssetCASH.isSelected());

    if (!anySelected) {
      miIBKRAssetAll.setSelected(true);
    }

    updateIbkrAssetFilterButtonText();

    // Mark preview dirty (requires explicit refresh)
    markIbkrPreviewDirty("Filtr změněn – náhled není aktuální, klikněte na Obnovit náhled");
  }

  private void updateIbkrAssetFilterButtonText() {
    if (bIBKRFlexAssetFilter == null) return;
    if (miIBKRAssetAll != null && miIBKRAssetAll.isSelected()) {
      bIBKRFlexAssetFilter.setText("Typ: Vše");
      return;
    }

    java.util.List<String> parts = new java.util.ArrayList<>();
    if (miIBKRAssetSTK != null && miIBKRAssetSTK.isSelected()) parts.add("STK");
    if (miIBKRAssetOPT != null && miIBKRAssetOPT.isSelected()) parts.add("OPT");
    if (miIBKRAssetFUT != null && miIBKRAssetFUT.isSelected()) parts.add("FUT");
    if (miIBKRAssetCASH != null && miIBKRAssetCASH.isSelected()) parts.add("CASH");
    if (parts.isEmpty()) {
      bIBKRFlexAssetFilter.setText("Typ: Vše");
    } else {
      bIBKRFlexAssetFilter.setText("Typ: " + String.join("+", parts));
    }
  }

  /**
   * Show/hide warning text near format selector for obsolete import formats.
   */
  private void updateObsoleteFormatWarning() {
    if (cbFormat == null || jLabel4 == null) {
      return;
    }

    int idx = cbFormat.getSelectedIndex();
    boolean obsolete = (idx == 2) || (idx == 4); // BrokerJet legacy, IB FlexQuery Trades only CSV

    if (obsolete) {
      jLabel4.setText(OBSOLETE_FORMAT_WARNING_HTML);
    } else {
      jLabel4.setText(FORMAT_LABEL_DEFAULT);
    }
  }

  /**
   * Get "records" word
   */
  private static String getRecordsWord(int n) {
    if (n == 1)
      return "záznam";
    if ((n >= 2) && (n <= 5))
      return "záznamy";
    else
      return "záznamů";
  }

  /**
   * Load import from a file or prepare API import
   */
  private void loadImport() {
    // Note: No importInProgress check here - internal method called from startImport()
    // which already manages concurrency protection for external calls

    System.out.println("[IMPORT:002] loadImport() called - UI format: " + cbFormat.getSelectedIndex() + ", file: " + (currentFile != null ? currentFile.getName() : "null"));

    // Log preview table state before clearing
    System.out.println("[IMPORT:PREVIEW] Preview table before clear: " + (transactions != null ? transactions.getRowCount() : "null") + " rows");

    // Clear not imported rows
    DefaultTableModel niTableModel = (DefaultTableModel) niTable.getModel();
    niTableModel.setNumRows(0);

    System.out.println("[IMPORT:PREVIEW] Preview table after clear: " + (transactions != null ? transactions.getRowCount() : "null") + " rows");

    // Clear not imported rows
    DefaultTableModel model = (DefaultTableModel) niTable.getModel();
    model.setNumRows(0);

    try {
      Vector<String[]> notImported = new Vector<String[]>();

      if (cbFormat.getSelectedIndex() == 0)
        return; // Bad format

      if (isTrading212Format()) {
        // For Trading 212 API imports, don't auto-fetch on date changes
        // User must explicitly click Import button after selecting year
        return;
      }
      
      if (isIBKRFlexFormat()) {
        // For IBKR Flex API imports, don't auto-fetch on date changes
        // User must explicitly click Fetch button
        return;
      }

      // File-based import logic
      if (currentFile == null) {
        // No file selected yet - user must choose one.
        updateSelectedFileLabel();
        return;
      }

      // Get dates, make start 00:00:00 and end 23:59:59
      Date startD, endD;
      GregorianCalendar cal = new GregorianCalendar();

      startD = startDate.getDate();
      if (startD != null) {
        cal.setTime(startD);
        cal.set(GregorianCalendar.HOUR_OF_DAY, 0);
        cal.set(GregorianCalendar.MINUTE, 0);
        cal.set(GregorianCalendar.SECOND, 0);
        cal.set(GregorianCalendar.MILLISECOND, 0);
        startD = cal.getTime();
      }

      endD = endDate.getDate();
      if (endD != null) {
        cal.setTime(endD);
        cal.set(GregorianCalendar.HOUR_OF_DAY, 23);
        cal.set(GregorianCalendar.MINUTE, 59);
        cal.set(GregorianCalendar.SECOND, 59);
        cal.set(GregorianCalendar.MILLISECOND, 999999);
        endD = cal.getTime();
      }

        // Get format - prefer programmatic override, fallback to UI state
        int formatIndex = (currentImportFormat > 0) ? currentImportFormat : cbFormat.getSelectedIndex();
        System.out.println("[FORMAT:004] About to call importFile with formatIndex=" + formatIndex + " (programmatic: " + currentImportFormat + ", UI: " + cbFormat.getSelectedIndex() + ") for file: " + currentFile.getName());
        transactions.importFile(currentFile, startD, endD, formatIndex, notImported);

       // Filter duplicate transactions that already exist in main database
       System.out.println("[DUPLICATE:001] Checking for duplicates in file import against main database");
       Vector<Transaction> originalTransactions = new Vector<>(transactions.rows); // Copy before filtering
       Vector<Transaction> filteredTransactions = mainWindow.getTransactionDatabase().filterDuplicates(originalTransactions);
       int duplicatesFiltered = originalTransactions.size() - filteredTransactions.size();

       // Clear previous duplicates list
       duplicatesToUpdate.clear();

       // If update checkbox is checked, store duplicates for later update
       if (cbUpdateDuplicates.isSelected() && duplicatesFiltered > 0) {
         for (Transaction candidate : originalTransactions) {
           if (!filteredTransactions.contains(candidate)) {
             duplicatesToUpdate.add(candidate);
           }
         }
         System.out.println("[DUPLICATE:002] " + duplicatesToUpdate.size() + " duplicates marked for update");
       }

       // Replace preview table with filtered transactions (only new ones)
       if (duplicatesFiltered > 0) {
         transactions.rows.clear();
         transactions.rows.addAll(filteredTransactions);
         transactions.fireTableDataChanged();
         System.out.println("[DUPLICATE:003] Filtered " + duplicatesFiltered + " duplicates from preview");
       }

        // Set labels
        int n = transactions.getRowCount();
        String previewText = "Náhled (" + n + " " + getRecordsWord(n) + ")";
        if (duplicatesFiltered > 0) {
          if (cbUpdateDuplicates.isSelected()) {
            previewText += " - " + duplicatesFiltered + " duplikátů k aktualizaci";
          } else {
            previewText += " - " + duplicatesFiltered + " duplikátů vyfiltrováno";
          }
        }
        previewText += ":";
        lPreview.setText(previewText);
        int rowCount = notImported.size();
        lUnimported.setText("Neimportované řádky (" + rowCount + " " + getRecordsWord(rowCount) + "):");

         // Reset import flag immediately on successful completion
         importInProgress = false;
         currentImportFormat = 0; // Clear programmatic override
         System.out.println("[IMPORT:SUCCESS] Import completed successfully - flags reset to false");

        // Log successful import completion
        System.out.println("[IMPORT:SUCCESS] Import completed successfully:");
        System.out.println("[IMPORT:SUCCESS]   - Preview transactions: " + n);
        System.out.println("[IMPORT:SUCCESS]   - Duplicates filtered: " + duplicatesFiltered);
        System.out.println("[IMPORT:SUCCESS]   - Not imported rows: " + rowCount);
        System.out.println("[IMPORT:SUCCESS]   - Final UI state:");
        logUIComponentStates();

      /* Fill in data model for not imported rows */

      // Get number of columns
      int colCount = 0;
      for (int i = 0; i < rowCount; i++) {
        n = notImported.get(i).length;
        if (n > colCount)
          colCount = n;
      }

       if (rowCount > 0) {
         niTableModel.setRowCount(rowCount);
         niTableModel.setColumnCount(colCount);

        // Make columns
        for (n = 0; n < colCount; n++) {
          niTable.getColumnModel().getColumn(n).setHeaderValue("Col " + n);
        }

        // Add data
        for (int i = 0; i < rowCount; i++) {
          String a[] = notImported.get(i);
          for (n = 0; n < a.length; n++) {
            model.setValueAt(a[n], i, n);
          }
          // Set nulls for not used columns
          for (; n < colCount; n++) {
            niTableModel.setValueAt(null, i, n);
          }
        }
      } else {
        niTableModel.setRowCount(0);
      }
     } catch (java.io.FileNotFoundException e) {
       System.out.println("[IMPORT:ERROR] FileNotFoundException during import: " + e.getMessage());
       JOptionPane.showMessageDialog(this, "Soubor nenalezen!");
       currentImportFormat = 0; // Clear programmatic override on error
     } catch (java.io.IOException e) {
       System.out.println("[IMPORT:ERROR] IOException during import: " + e.getMessage());
       JOptionPane.showMessageDialog(this, "Chyba čtení: " + e.getLocalizedMessage());
       currentImportFormat = 0; // Clear programmatic override on error
     } catch (cz.datesoft.stockAccounting.imp.ImportException e) {
       System.out.println("[IMPORT:ERROR] ImportException during import: " + e.getMessage());
       System.out.println("[IMPORT:ERROR] UI state at time of error:");
       logUIComponentStates();
       JOptionPane.showMessageDialog(this, "Chyba při importu: " + e.getMessage());
       currentImportFormat = 0; // Clear programmatic override on error
     }
  }

  /**
   * Start import - show ourselves and do first initial import if format was
   * already set
   */
  public void startImport(File file, Date startDateValue) {
    startImport(file, startDateValue, 0); // Default to no preselected format
  }

  /**
   * Start import with preselected format - handles both file-based and API
   * imports
   */
  public void startImport(File file, Date startDateValue, int preselectedFormat) {
    // Prevent multiple import triggers during file selection
    if (importInProgress) {
      System.out.println("[DUPE:001] Ignoring duplicate import trigger - import already in progress");
      return;
    }
    importInProgress = true;

    try {
      currentFile = file;

      System.out.println("[IMPORT:SESSION] ===== STARTING NEW IMPORT SESSION =====");
      System.out.println("[FORMAT:001] startImport called with preselectedFormat=" + preselectedFormat + ", file=" + (file != null ? file.getName() : "null"));

      // Log initial state before any changes
      logUIComponentStates();

      // Restore last selected format from settings (unless preselected format is specified)
      if (preselectedFormat == 0) {
        int savedFormat = cz.datesoft.stockAccounting.Settings.getLastImportFormat();
        if (savedFormat > 0 && savedFormat < cbFormat.getModel().getSize()) {
          cbFormat.setSelectedIndex(savedFormat);
          updateUiForFormat(savedFormat);
        }
      }

       // FORCE reset format selection FIRST - before setting dates to avoid premature loadImport() calls
       if (preselectedFormat > 0) {
         System.out.println("[FORMAT:002] Force setting cbFormat to index " + preselectedFormat);

         // Set programmatic format override
         currentImportFormat = preselectedFormat;

         // Direct UI updates on EDT (we're already on EDT from menu click)
         cbFormat.setSelectedIndex(preselectedFormat);
         updateUiForFormat(preselectedFormat);
         updateWindowTitle();

         System.out.println("[FORMAT:003] UI state reset complete, cbFormat.getSelectedIndex()=" + cbFormat.getSelectedIndex() + ", programmatic=" + currentImportFormat);
       }

      // Set dates for file-based imports (AFTER format is set to avoid triggering premature loadImport)
      System.out.println("[TIMING:001] About to set dates - programmatic format: " + currentImportFormat + ", UI format: " + cbFormat.getSelectedIndex());
      if (startDateValue != null) {
        startDate.setDate(startDateValue);
      }
      endDate.setDate(null);
      System.out.println("[TIMING:002] Dates set, about to trigger loadImport");

      // Single import trigger point - only when UI state is stable
      if (cbFormat.getSelectedIndex() != 0 && currentFile != null) {
        System.out.println("[IMPORT:001] Triggering single import attempt");
        loadImport(); // Only load if we have a file for file-based imports
      }

      setVisible(true);

    } finally {
      // Always reset the flag, even if an exception occurs
      importInProgress = false;
      System.out.println("[DUPE:002] Import trigger flag reset, ready for next import");
    }
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated
  // Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    jLabel1 = new javax.swing.JLabel();
    jLabel2 = new javax.swing.JLabel();
    jLabel4 = new javax.swing.JLabel();
    cbFormat = new javax.swing.JComboBox();
    bSelectFile = new javax.swing.JButton();
    lSelectedFile = new javax.swing.JLabel();
    bRefresh = new javax.swing.JButton();
    bImport = new javax.swing.JButton();
    jPanel2 = new javax.swing.JPanel();
    bCancel = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    table = new javax.swing.JTable();
    lPreview = new javax.swing.JLabel();
    lUnimported = new javax.swing.JLabel();
    niScrollPane = new javax.swing.JScrollPane();
    niTable = new javax.swing.JTable();

    setTitle("Import souboru");
    setMaximumSize(new java.awt.Dimension(1024, 1024));
    getContentPane().setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Importovat od:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(jLabel1, gridBagConstraints);

    jLabel2.setText("do:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(jLabel2, gridBagConstraints);

    jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    jLabel4.setText("Formát:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(jLabel4, gridBagConstraints);

    cbFormat.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "<vyberte formát>", "Fio - obchody export",
         "⚠️ BrokerJet - HTML export (legacy)", "IB - TradeLog", "⚠️ IB - FlexQuery Trades only CSV",
         "T212 Invest  - csv  mena: USD", "T212 Invest  - csv  mena: CZK", "Revolut - csv", "Trading 212 API", "IBKR Flex" }));
    cbFormat.setMinimumSize(new java.awt.Dimension(100, 20));
    cbFormat.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cbFormatActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(cbFormat, gridBagConstraints);

    bSelectFile.setText("Vybrat soubor...");
    bSelectFile.setToolTipText("Vybrat lokální soubor pro import (dle zvoleného formátu)");
    bSelectFile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        selectLocalImportFile();
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(bSelectFile, gridBagConstraints);

    lSelectedFile.setText("(soubor nevybrán)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    getContentPane().add(lSelectedFile, gridBagConstraints);

    bRefresh.setText("Aktualizovat náhled");
    bRefresh.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bRefreshActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(bRefresh, gridBagConstraints);

    bImport.setText("Importovat");
    bImport.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bImportActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(bImport, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    getContentPane().add(jPanel2, gridBagConstraints);

    bCancel.setText("Storno");
    bCancel.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bCancelActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(bCancel, gridBagConstraints);

    table.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null },
            { null },
            { null },
            { null }
        },
        new String[] {
            "Columns..."
        }) {
      boolean[] canEdit = new boolean[] {
          false
      };

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
      }
    });
    table.setEnabled(false);
    jScrollPane1.setViewportView(table);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 2.0;
    getContentPane().add(jScrollPane1, gridBagConstraints);

    lPreview.setText("Náhled (0 záznamů):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    getContentPane().add(lPreview, gridBagConstraints);

    cbUpdateDuplicates = new javax.swing.JCheckBox();
    cbUpdateDuplicates.setText("Aktualizovat duplikáty");
    cbUpdateDuplicates.setToolTipText("Přepíše Poznámky, Poplatky a Datum vypořádání u existujících záznamů. Pokud existuje TxnID, doplní se i přesný čas (sekundy) v poli Datum.");
    cbUpdateDuplicates.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cbUpdateDuplicatesActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    getContentPane().add(cbUpdateDuplicates, gridBagConstraints);

    lUnimported.setText("Neimportované řádky (0 záznamů):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(lUnimported, gridBagConstraints);

    niTable.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null },
            { null },
            { null },
            { null }
        },
        new String[] {
            "Columns..."
        }) {
      boolean[] canEdit = new boolean[] {
          false
      };

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
      }
    });
    niTable.setEnabled(false);
    niScrollPane.setViewportView(niTable);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    getContentPane().add(niScrollPane, gridBagConstraints);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private boolean isTrading212Format() {
    return cbFormat != null && cbFormat.getSelectedIndex() == 8; // Trading 212 API is index 8
  }
  
  private boolean isIBKRFlexFormat() {
    return cbFormat != null && cbFormat.getSelectedIndex() == 9; // IBKR Flex API is index 9
  }
  
  /**
   * Check if valid API credentials are configured
   */
  private boolean hasValidApiCredentials() {
    String apiKey = cz.datesoft.stockAccounting.Settings.getTrading212ApiKey();
    String apiSecret = cz.datesoft.stockAccounting.Settings.getTrading212ApiSecret();
    return apiKey != null && !apiKey.trim().isEmpty() 
        && apiSecret != null && !apiSecret.trim().isEmpty();
  }
  
  /**
   * Open settings window to Trading 212 API tab
   */
  private void openSettings_Trading212Tab() {
    SettingsWindow settingsWindow = new SettingsWindow(mainWindow, true);
    
    // Re-check credentials and update UI after settings window closes
    settingsWindow.addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowClosed(java.awt.event.WindowEvent e) {
        updateImportButtonState();
        updateRefreshButtonState();
      }
    });
    
    // Open settings - user can navigate to Trading 212 tab manually
    // Note: setSelectedTab() could be added to SettingsWindow for automatic navigation
    settingsWindow.showDialog();
  }
  
  /**
   * Update import button state based on API credentials
   */
  private void updateImportButtonState() {
    if (isTrading212Format()) {
      if (!hasValidApiCredentials()) {
        bImport.setText("⚙ Nastavit Trading 212 API...");
        bImport.setToolTipText("Klikněte pro nastavení API přístupu k Trading 212");
      } else {
        // Check if we have preview data
        boolean hasPreviewData = !transactions.rows.isEmpty();
        if (hasPreviewData) {
          bImport.setText("Sloučit do databáze");
          bImport.setToolTipText("Sloučit načtené transakce do hlavní databáze");
        } else {
          bImport.setText("API stažení");
          bImport.setToolTipText("Stáhnout data z Trading 212 API");
        }
      }
    } else {
      bImport.setText("Importovat");
      bImport.setToolTipText("Importovat data do databáze");
    }
  }
  
  /**
   * Update refresh button state based on cache availability
   */
  private void updateRefreshButtonState() {
    if (bRefreshFromApi == null || !isTrading212Format()) {
      return;
    }
    
    // Enable only if we have credentials and cached data for selected year
    boolean hasCredentials = hasValidApiCredentials();
    boolean hasCachedData = false;
    
    if (hasCredentials && cbTrading212Year != null) {
      String selectedItem = (String) cbTrading212Year.getSelectedItem();
      if (selectedItem != null) {
        try {
          String yearStr = selectedItem.split(" ")[0];
          int year = Integer.parseInt(yearStr);
          
          // Check cache (need account ID first)
          // For now, just check if status shows "Cached"
          hasCachedData = selectedItem.contains("Cached");
        } catch (Exception e) {
          // Ignore parsing errors
        }
      }
    }
    
    bRefreshFromApi.setEnabled(hasCachedData);
  }

  private void setupTrading212YearSelection() {
    if (cbTrading212Year == null) {
      cbTrading212Year = new javax.swing.JComboBox<>();
      bClearPreview = new javax.swing.JButton("Vymazat náhled");
      bRefreshFromApi = new javax.swing.JButton("🔄 Obnovit z API");
      bRefreshFromApi.setEnabled(false); // Initially disabled until cache is available
      lCacheStatus = new javax.swing.JLabel("Cache: None");

      populateTrading212YearDropdown();

      // Add selection listener to update cache status and button state
      cbTrading212Year.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          updateCacheStatus();
          updateRefreshButtonState();
        }
      });

      // Add action listeners for buttons
      bClearPreview.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          clearPreview();
        }
      });
      
      bRefreshFromApi.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          refreshFromApiClicked();
        }
      });

      // Add to UI - single horizontal row
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
      gbc.gridy = 12; // After existing controls
      gbc.gridwidth = 1;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      gbc.insets = new java.awt.Insets(5, 5, 5, 5);

      // Year selection
      gbc.gridx = 0;
      gbc.weightx = 0.0;
      jPanel2.add(new javax.swing.JLabel("Rok:"), gbc);

      gbc.gridx = 1;
      gbc.weightx = 1.0;
      jPanel2.add(cbTrading212Year, gbc);

      // Clear preview button
      gbc.gridx = 2;
      gbc.weightx = 0.0;
      jPanel2.add(bClearPreview, gbc);
      
      // Refresh from API button
      gbc.gridx = 3;
      gbc.weightx = 0.0;
      jPanel2.add(bRefreshFromApi, gbc);

      // Add status label on next row
      gbc.gridx = 0;
      gbc.gridy = 13;
      gbc.gridwidth = 4; // Span across all columns
      gbc.weightx = 1.0;
      jPanel2.add(lCacheStatus, gbc);

      // Repack to show new components
      pack();
    }

    // Ensure visibility
    cbTrading212Year.setVisible(true);
    bClearPreview.setVisible(true);
    lCacheStatus.setVisible(true);

    // Initial update
    updateCacheStatus();
    updateClearButtonState();
  }

  /**
   * Handle "Refresh from API" button click
   */
  private void refreshFromApiClicked() {
    if (!isTrading212Format() || cbTrading212Year == null) {
      return;
    }
    
    String selectedItem = (String) cbTrading212Year.getSelectedItem();
    if (selectedItem == null) {
      return;
    }
    
    try {
      String yearStr = selectedItem.split(" ")[0];
      int year = Integer.parseInt(yearStr);
      
      // Confirm with user
      int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
          "Znovu stáhnout data pro rok " + year + " z Trading 212?\n" +
          "Tato operace přepíše cache data.",
          "Potvrdit obnovení", javax.swing.JOptionPane.YES_NO_OPTION);
      
      if (confirm != javax.swing.JOptionPane.YES_OPTION) {
        return;
      }
      
      // Clear preview first
      clearPreview();
      
      // Perform import with force refresh flag
      System.out.println("[REFRESH:001] Forcing refresh from API for year " + year);
      performTrading212Import(false, true); // fetch mode, force refresh
      
    } catch (Exception e) {
      System.err.println("Failed to parse year from selection: " + e.getMessage());
      javax.swing.JOptionPane.showMessageDialog(this,
          "Chyba při obnovení: " + e.getMessage(),
          "Chyba", javax.swing.JOptionPane.ERROR_MESSAGE);
    }
  }
  
  private void updateCacheStatus() {
    if (lCacheStatus != null) {
      String selectedItem = (String) cbTrading212Year.getSelectedItem();
      if (selectedItem != null) {
        try {
          int year = Integer.parseInt(selectedItem.split(" ")[0]);
          if (importState.hasCachedTransactions(year)) {
            int count = importState.getCachedTransactions(year).size();
            lCacheStatus.setText("Cache: " + count + " transakcí (session)");
          } else {
            lCacheStatus.setText("Cache: Žádná data (klikněte na 'API stahnuti')");
          }
        } catch (NumberFormatException e) {
          lCacheStatus.setText("Cache: Neplatný rok");
        }
      }
    }
  }





  private void hideTrading212YearSelection() {
    if (cbTrading212Year != null) {
      cbTrading212Year.setVisible(false);
    }
    if (bClearPreview != null) {
      bClearPreview.setVisible(false);
    }
    if (bRefreshFromApi != null) {
      bRefreshFromApi.setVisible(false);
    }
    if (lCacheStatus != null) {
      lCacheStatus.setVisible(false);
    }
  }
  
  /**
   * Setup IBKR Flex UI components (simplified - current year only)
   */
  private void setupIBKRFlexUI() {
    if (bIBKRFlexFetch == null) {
      // Create UI components
      bIBKRFlexFetch = new javax.swing.JButton("Načíst z IBKR");
      bIBKRFlexFile = new javax.swing.JButton("Načíst ze souboru");
      bIBKRFlexClear = new javax.swing.JButton("Vymazat náhled");
      bIBKRFlexRefreshPreview = new javax.swing.JButton("Obnovit náhled");
      cbIBKRFlexIncludeCorporateActions = new javax.swing.JCheckBox("Transformace");
      cbIBKRFlexIncludeCorporateActions.setSelected(true);
      cbIBKRFlexIncludeCorporateActions.setToolTipText("Zahrnout korporátní akce (Transformace) do náhledu a importu");

      cbIBKRFlexCaRS = new javax.swing.JCheckBox("RS");
      cbIBKRFlexCaTC = new javax.swing.JCheckBox("TC");
      cbIBKRFlexCaIC = new javax.swing.JCheckBox("IC");
      cbIBKRFlexCaTO = new javax.swing.JCheckBox("TO");
      cbIBKRFlexCaRS.setToolTipText("Reverse split / split");
      cbIBKRFlexCaTC.setToolTipText("Ticker change / merger");
      cbIBKRFlexCaIC.setToolTipText("CUSIP/ISIN change (identifier change)");
      cbIBKRFlexCaTO.setToolTipText("Tender offer");

      // Defaults: RS+TC on, IC+TO off
      cbIBKRFlexCaRS.setSelected(true);
      cbIBKRFlexCaTC.setSelected(true);
      cbIBKRFlexCaIC.setSelected(false);
      cbIBKRFlexCaTO.setSelected(false);
      cbIBKRFlexImportMode = new javax.swing.JComboBox<>(new String[] {
        "Obchody + transformace",
        "Pouze obchody",
        "Pouze transformace"
      });
      cbIBKRFlexImportMode.setToolTipText("Určuje, zda se mají importovat obchody, transformace nebo obojí");
      lblIBKRFlexStatus = new javax.swing.JLabel("Vyberte zdroj dat: API nebo lokální soubor");
      pIBKRFlexButtons = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));

      // AssetClass filter (multi-select)
      bIBKRFlexAssetFilter = new javax.swing.JButton();
      bIBKRFlexAssetFilter.setToolTipText("Omezí import obchodů podle AssetClass (platí pro ExchTrade)");
      pmIBKRFlexAssetFilter = new javax.swing.JPopupMenu();

      miIBKRAssetAll = new javax.swing.JCheckBoxMenuItem("Vše (včetně neznámých)");
      miIBKRAssetSTK = new javax.swing.JCheckBoxMenuItem("Akcie (STK)");
      miIBKRAssetOPT = new javax.swing.JCheckBoxMenuItem("Opce (OPT)");
      miIBKRAssetFUT = new javax.swing.JCheckBoxMenuItem("Futures (FUT)");
      miIBKRAssetCASH = new javax.swing.JCheckBoxMenuItem("Cash/FX (CASH)");

      miIBKRAssetAll.setSelected(true);
      pmIBKRFlexAssetFilter.add(miIBKRAssetAll);
      pmIBKRFlexAssetFilter.addSeparator();
      pmIBKRFlexAssetFilter.add(miIBKRAssetSTK);
      pmIBKRFlexAssetFilter.add(miIBKRAssetOPT);
      pmIBKRFlexAssetFilter.add(miIBKRAssetFUT);
      pmIBKRFlexAssetFilter.add(miIBKRAssetCASH);

      java.awt.event.ActionListener assetListener = new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          Object src = evt.getSource();
          if (src instanceof javax.swing.AbstractButton) {
            syncIbkrAssetFilterState((javax.swing.AbstractButton) src);
          } else {
            syncIbkrAssetFilterState(null);
          }
        }
      };
      miIBKRAssetAll.addActionListener(assetListener);
      miIBKRAssetSTK.addActionListener(assetListener);
      miIBKRAssetOPT.addActionListener(assetListener);
      miIBKRAssetFUT.addActionListener(assetListener);
      miIBKRAssetCASH.addActionListener(assetListener);

      bIBKRFlexAssetFilter.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          if (pmIBKRFlexAssetFilter != null) {
            pmIBKRFlexAssetFilter.show(bIBKRFlexAssetFilter, 0, bIBKRFlexAssetFilter.getHeight());
          }
        }
      });
      updateIbkrAssetFilterButtonText();
      
      // Add action listeners
      bIBKRFlexFetch.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          ibkrFlexFetchClicked();
        }
      });
      
      bIBKRFlexFile.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          ibkrFlexFileClicked();
        }
      });
      
      bIBKRFlexClear.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          clearIbkrPreviewAndCache();
        }
      });

      bIBKRFlexRefreshPreview.setEnabled(false);
      bIBKRFlexRefreshPreview.setToolTipText("Znovu vytvořit náhled z načtených dat (bez stahování z API)");
      bIBKRFlexRefreshPreview.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          refreshIbkrPreviewFromCachedCsv();
        }
      });

      cbIBKRFlexIncludeCorporateActions.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          markIbkrPreviewDirty("Nastavení Transformací změněno – náhled není aktuální, klikněte na Obnovit náhled");
          applyIbkrImportModeToUi();
        }
      });

      java.awt.event.ActionListener caTypeListener = new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          markIbkrPreviewDirty("Výběr typů transformací změněn – náhled není aktuální, klikněte na Obnovit náhled");
        }
      };
      cbIBKRFlexCaRS.addActionListener(caTypeListener);
      cbIBKRFlexCaTC.addActionListener(caTypeListener);
      cbIBKRFlexCaIC.addActionListener(caTypeListener);
      cbIBKRFlexCaTO.addActionListener(caTypeListener);

      cbIBKRFlexImportMode.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          applyIbkrImportModeToUi();
          markIbkrPreviewDirty("Režim importu změněn – náhled není aktuální, klikněte na Obnovit náhled");
        }
      });
      
      // Build left-aligned button row (stable alignment even when other UI elements are hidden)
      pIBKRFlexButtons.add(bIBKRFlexFetch);
      pIBKRFlexButtons.add(bIBKRFlexFile);
      pIBKRFlexButtons.add(bIBKRFlexClear);
      pIBKRFlexButtons.add(bIBKRFlexRefreshPreview);
      pIBKRFlexButtons.add(cbIBKRFlexImportMode);
      pIBKRFlexButtons.add(cbIBKRFlexIncludeCorporateActions);
      pIBKRFlexButtons.add(new javax.swing.JLabel("Typy:"));
      pIBKRFlexButtons.add(cbIBKRFlexCaRS);
      pIBKRFlexButtons.add(cbIBKRFlexCaTC);
      pIBKRFlexButtons.add(cbIBKRFlexCaIC);
      pIBKRFlexButtons.add(cbIBKRFlexCaTO);
      pIBKRFlexButtons.add(bIBKRFlexAssetFilter);

      applyIbkrImportModeToUi();

      // Add to main layout directly below format selector (left-aligned)
      java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 2;
      gbc.gridwidth = 6;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      gbc.anchor = java.awt.GridBagConstraints.WEST;
      gbc.insets = new java.awt.Insets(5, 5, 0, 5);
      gbc.weightx = 1.0;
      getContentPane().add(pIBKRFlexButtons, gbc);

      // Status label on next row
      gbc = new java.awt.GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 3;
      gbc.gridwidth = 6;
      gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
      gbc.anchor = java.awt.GridBagConstraints.WEST;
      gbc.insets = new java.awt.Insets(0, 5, 5, 5);
      gbc.weightx = 1.0;
      getContentPane().add(lblIBKRFlexStatus, gbc);
      
      // Repack to show new components
      pack();
    }
    
    // Ensure visibility
    bIBKRFlexFetch.setVisible(true);
    bIBKRFlexFile.setVisible(true);
    bIBKRFlexClear.setVisible(true);
    if (bIBKRFlexRefreshPreview != null) {
      bIBKRFlexRefreshPreview.setVisible(true);
    }
    if (cbIBKRFlexImportMode != null) {
      cbIBKRFlexImportMode.setVisible(true);
    }
    if (cbIBKRFlexIncludeCorporateActions != null) {
      cbIBKRFlexIncludeCorporateActions.setVisible(true);
    }
    if (bIBKRFlexAssetFilter != null) {
      bIBKRFlexAssetFilter.setVisible(true);
    }
    if (pIBKRFlexButtons != null) {
      pIBKRFlexButtons.setVisible(true);
    }
    lblIBKRFlexStatus.setVisible(true);
    
    // Update button state based on preview data
    updateIBKRFlexButtonState();
  }
  
  /**
   * Hide IBKR Flex UI components
   */
  private void hideIBKRFlexUI() {
    if (bIBKRFlexFetch != null) {
      bIBKRFlexFetch.setVisible(false);
    }
    if (bIBKRFlexFile != null) {
      bIBKRFlexFile.setVisible(false);
    }
    if (bIBKRFlexClear != null) {
      bIBKRFlexClear.setVisible(false);
    }
    if (bIBKRFlexRefreshPreview != null) {
      bIBKRFlexRefreshPreview.setVisible(false);
    }
    if (cbIBKRFlexImportMode != null) {
      cbIBKRFlexImportMode.setVisible(false);
    }
    if (cbIBKRFlexIncludeCorporateActions != null) {
      cbIBKRFlexIncludeCorporateActions.setVisible(false);
    }
    if (pIBKRFlexButtons != null) {
      pIBKRFlexButtons.setVisible(false);
    }
    if (bIBKRFlexAssetFilter != null) {
      bIBKRFlexAssetFilter.setVisible(false);
    }
    if (lblIBKRFlexStatus != null) {
      lblIBKRFlexStatus.setVisible(false);
    }
  }
  
  /**
   * Show IBKR Flex UI components
   */
  private void showIBKRFlexUI() {
    setupIBKRFlexUI();
  }
  
  /**
   * Update IBKR Flex button state based on preview data
   */
  private void updateIBKRFlexButtonState() {
    if (bIBKRFlexFetch == null) {
      return;
    }
    
    boolean hasPreviewData = !transactions.rows.isEmpty();
    if (hasPreviewData) {
      bIBKRFlexFetch.setText("Sloučit do databáze");
      bIBKRFlexFetch.setToolTipText("Sloučit načtené transakce do hlavní databáze");
    } else {
      bIBKRFlexFetch.setText("Načíst z IBKR");
      bIBKRFlexFetch.setToolTipText("Stáhnout data z IBKR Flex API pro aktuální rok");
    }
    
    // Enable/disable clear button
    if (bIBKRFlexClear != null) {
      bIBKRFlexClear.setEnabled(hasPreviewData);
    }

    if (bIBKRFlexRefreshPreview != null) {
      bIBKRFlexRefreshPreview.setEnabled(ibkrPreviewDirty && lastIbkrCsvContent != null);
    }
  }
  
  /**
   * Handle IBKR Flex fetch/merge button click
   */
  private void ibkrFlexFetchClicked() {
    if (!isIBKRFlexFormat()) {
      return;
    }
    
    boolean hasPreviewData = !transactions.rows.isEmpty();
    if (hasPreviewData) {
      // MERGE MODE: Merge existing preview data to database
      System.out.println("[IBKR:001] Merging existing preview data to database");
      performIBKRFlexImport(true);
    } else {
      // FETCH MODE: Fetch fresh data from API
      System.out.println("[IBKR:001] Fetching fresh data from IBKR Flex API");
      performIBKRFlexImport(false);
    }
  }
  
  /**
   * Handle IBKR Flex file import button click
   */
  private void ibkrFlexFileClicked() {
    if (!isIBKRFlexFormat()) {
      return;
    }
    
    System.out.println("[IBKR:FILE:001] File import button clicked");
    
    // Show file dialog
    java.awt.FileDialog dialog = new java.awt.FileDialog(this, "Vybrat IBKR Flex CSV soubor", java.awt.FileDialog.LOAD);
    
    // Remember last directory from Settings
    String lastDir = Settings.getImportDirectory();
    if (lastDir != null) {
      dialog.setDirectory(lastDir);
    }
    
    // Show dialog
    dialog.setVisible(true);
    String fileName = dialog.getFile();
    
    if (fileName == null) {
      System.out.println("[IBKR:FILE:002] User cancelled file selection");
      return; // User cancelled
    }
    
    // Get selected file
    java.io.File selectedFile = new java.io.File(dialog.getDirectory(), fileName);
    Settings.setImportDirectory(dialog.getDirectory());
    Settings.save();

    // Archive selected file into unified cache and use cached copy.
    try {
      java.nio.file.Path cached = CacheManager.archiveFile("ib", CacheManager.Source.FILE,
          "flex_file_" + selectedFile.getName(), selectedFile.toPath());
      selectedFile = cached.toFile();
    } catch (Exception e) {
      // Best effort
    }
    
    System.out.println("[IBKR:FILE:003] Selected file: " + selectedFile.getAbsolutePath());
    
    // Update status label to show loading
    lblIBKRFlexStatus.setText("Načítání souboru: " + fileName + "...");
    
    try {
      // Read file content
      String csvContent = readFileToString(selectedFile);
      lastIbkrCsvContent = csvContent;
      lastIbkrSourceLabel = fileName;
      ibkrPreviewDirty = false;
      if (bIBKRFlexRefreshPreview != null) {
        bIBKRFlexRefreshPreview.setEnabled(false);
      }
      System.out.println("[IBKR:FILE:004] File read successfully, size: " + csvContent.length() + " chars");
      
      // Parse using IBKRFlexParser
      IBKRFlexParser parser = new IBKRFlexParser();
      parser.setAllowedAssetClasses(getSelectedIbkrAssetClasses());
      parser.setIncludeCorporateActions(cbIBKRFlexIncludeCorporateActions == null || cbIBKRFlexIncludeCorporateActions.isSelected());
      parser.setAllowedCorporateActionTypes(getSelectedIbkrCorporateActionTypes());
      int mode = getIbkrImportMode();
      parser.setIncludeTrades(mode != IBKR_MODE_TRANS_ONLY);
      if (mode == IBKR_MODE_TRADES_ONLY) {
        parser.setIncludeCorporateActions(false);
      } else if (mode == IBKR_MODE_TRANS_ONLY) {
        parser.setIncludeCorporateActions(true);
      }
      Vector<Transaction> parsedTransactions = parser.parseCsvReport(csvContent);
      System.out.println("[IBKR:FILE:005] Parsed " + parsedTransactions.size() + " transactions");

      // Disambiguate rare collisions caused by minute-level timestamp precision.
      // This keeps IBKR Flex re-import stable: update one existing row, insert the other(s).
      disambiguateIbkrDuplicateCollisions(mainWindow.getTransactionDatabase(), parsedTransactions);
      
      // Store parser reference for statistics access
      lastIBKRParser = parser;
      
      // Clear preview table
      clearPreview();
      
      // Filter out duplicate transactions that already exist in main database
      System.out.println("[IBKR:FILE:DUPLICATE:001] Checking for duplicates against main database");
      Vector<Transaction> filteredTransactions = mainWindow.getTransactionDatabase().filterDuplicates(parsedTransactions);
      int duplicatesFiltered = parsedTransactions.size() - filteredTransactions.size();
      System.out.println("[IBKR:FILE:DUPLICATE:002] Found " + duplicatesFiltered + " duplicates");
      
      // Clear previous duplicates list
      duplicatesToUpdate.clear();
      
      // If update checkbox is checked, store duplicates for later update
      if (cbUpdateDuplicates.isSelected() && duplicatesFiltered > 0) {
        for (Transaction candidate : parsedTransactions) {
          if (!filteredTransactions.contains(candidate)) {
            duplicatesToUpdate.add(candidate);
          }
        }
        System.out.println("[IBKR:FILE:DUPLICATE:003] " + duplicatesToUpdate.size() + " duplicates marked for update");
      }
      
      // Add filtered transactions to preview
      transactions.rows.addAll(filteredTransactions);
      transactions.fireTableDataChanged();
      
      // Update UI labels to show preview with duplicate count
      String previewText = "Náhled (" + filteredTransactions.size() + " záznamů)";
      if (duplicatesFiltered > 0) {
        if (cbUpdateDuplicates.isSelected()) {
          previewText += " - " + duplicatesFiltered + " duplikátů k aktualizaci";
        } else {
          previewText += " - " + duplicatesFiltered + " duplikátů vyfiltrováno";
        }
      }
      previewText += ":";
      lPreview.setText(previewText);
      
      // Update status label with statistics (minimal format)
      String statusMsg = "Načteno: " + fileName + " (" + filteredTransactions.size() + " transakcí";
      if (duplicatesFiltered > 0) {
        statusMsg += ", " + duplicatesFiltered + " duplikátů";
      }
      if (parser.getImportedCorporateActionCount() > 0 || parser.getSkippedZeroNetCount() > 0) {
          statusMsg += ", " + parser.getImportedCorporateActionCount() + " korp. akce";
          if (parser.getSkippedZeroNetCount() > 0) {
              statusMsg += ", " + parser.getSkippedZeroNetCount() + " přeskočeno";
          }
      }
      statusMsg += ")";
      lblIBKRFlexStatus.setText(statusMsg);

      // If preview now has data, switch IBKR button to merge mode
      updateIBKRFlexButtonState();
      
      // Update button state (shows "Sloučit do databáze" button)
      updateIBKRFlexButtonState();
      
      System.out.println("[IBKR:FILE:006] File import preview complete - " + 
                         filteredTransactions.size() + " new, " + 
                         duplicatesFiltered + " duplicates");
      
    } catch (Exception e) {
      System.err.println("[IBKR:FILE:ERROR] Failed to import file: " + e.getMessage());
      e.printStackTrace();
      
      // Show user-friendly error
      javax.swing.JOptionPane.showMessageDialog(this,
        "Soubor nelze načíst jako IBKR Flex CSV.\n" +
        "Zkontrolujte, že jste vybrali správný formát a soubor je z IBKR Flex Query.\n\n" +
        "Chyba: " + e.getMessage(),
        "Chyba při načítání souboru", javax.swing.JOptionPane.ERROR_MESSAGE);
      
      // Reset status label
      lblIBKRFlexStatus.setText("Vyberte zdroj dat: API nebo lokální soubor");
    }
  }
  
  /**
   * Read file content to String (UTF-8 encoding with fallback)
   */
  private String readFileToString(java.io.File file) throws java.io.IOException {
    StringBuilder content = new StringBuilder();
    
    // Try UTF-8 first (IBKR standard)
    try (java.io.BufferedReader reader = new java.io.BufferedReader(
         new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line).append("\n");
      }
    } catch (java.io.UnsupportedEncodingException e) {
      // Fallback to ISO-8859-1 if UTF-8 fails
      System.out.println("[FILE:READ] UTF-8 failed, trying ISO-8859-1");
      try (java.io.BufferedReader reader = new java.io.BufferedReader(
           new java.io.InputStreamReader(new java.io.FileInputStream(file), "ISO-8859-1"))) {
        String line;
        while ((line = reader.readLine()) != null) {
          content.append(line).append("\n");
        }
      }
    }
    
    return content.toString();
  }
  
  /**
   * Filter transactions to current year only
   */
  private Vector<Transaction> filterToCurrentYear(Vector<Transaction> transactions) {
    int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    Vector<Transaction> filtered = new Vector<>();
    
    java.util.Calendar cal = java.util.Calendar.getInstance();
    for (Transaction tx : transactions) {
      if (tx.getDate() != null) {
        cal.setTime(tx.getDate());
        int txYear = cal.get(java.util.Calendar.YEAR);
        if (txYear == currentYear) {
          filtered.add(tx);
        }
      }
    }
    
    System.out.println("[FILTER:001] Filtered to current year (" + currentYear + "): " + 
                       transactions.size() + " → " + filtered.size() + " transactions");
    return filtered;
  }
  
  /**
   * Perform IBKR Flex import (fetch from API or merge to database)
   * @param mergeMode true = merge preview to database, false = fetch from API
   */
  private void performIBKRFlexImport(boolean mergeMode) {
    System.out.println("[IBKR:VALIDATE:001] performIBKRFlexImport called with mergeMode=" + mergeMode);
    
    if (!isIBKRFlexFormat()) {
      System.out.println("[IBKR:VALIDATE:002] ❌ Format check: FAIL (not IBKR format)");
      return;
    }
    
    System.out.println("[IBKR:VALIDATE:003] ✅ Format check: PASS");
    
    if (mergeMode) {
      // MERGE MODE: Merge existing preview data to database
      System.out.println("[IBKR:MERGE:001] Starting merge mode - merging preview to database");
      
      try {
        // Track initial count for accurate reporting
        int initialTransactionCount = mainWindow.getTransactionDatabase().getRowCount();
        System.out.println("[IBKR:MERGE:002] Initial transaction count in database: " + initialTransactionCount);
        
        transactions.mergeTo(mainWindow.getTransactionDatabase());
        
        // Update duplicates if checkbox is checked
        int updatedCount = 0;
        if (cbUpdateDuplicates.isSelected() && !duplicatesToUpdate.isEmpty()) {
          TransactionSet mainDb = mainWindow.getTransactionDatabase();
          
          System.out.println("[IBKR:UPDATE:001] Updating " + duplicatesToUpdate.size() + " duplicate transactions");
          
          // Start batch update to prevent double-updating same transaction
          mainDb.startBatchUpdate();
          
          for (Transaction candidate : duplicatesToUpdate) {
            if (mainDb.updateDuplicateTransaction(candidate)) {
              updatedCount++;
            }
          }
          
          // End batch update
          mainDb.endBatchUpdate();
          
          System.out.println("[IBKR:UPDATE:002] Successfully updated " + updatedCount + " transactions");
        }
        
        // Clear duplicates list
        duplicatesToUpdate.clear();
        
        // Invalidate transformation cache after import
        System.out.println("[IBKR:MERGE:003] Invalidating transformation cache after IBKR import");
        mainWindow.getTransactionDatabase().invalidateTransformationCache();
        
        // Refresh metadata filter dropdowns after import
        System.out.println("[IBKR:MERGE:004] Refreshing metadata filters after IBKR import");
        mainWindow.refreshMetadataFilters();
        
        // Calculate actual transactions added
        int finalTransactionCount = mainWindow.getTransactionDatabase().getRowCount();
        int transactionsAdded = finalTransactionCount - initialTransactionCount;
        System.out.println("[IBKR:MERGE:005] Final transaction count: " + finalTransactionCount + ", added: " + transactionsAdded);
        
        // Force immediate table refresh on EDT
        javax.swing.SwingUtilities.invokeLater(() -> {
          mainWindow.refreshTable();
        });
        
        // Show success message with accurate count
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String message = "Úspěšně importováno " + transactionsAdded + " transakcí z IBKR!\n";
        if (updatedCount > 0) {
          message += "Aktualizováno: " + updatedCount + " existujících záznamů\n\n";
          message += "Aktualizované řádky jsou zvýrazněny žlutě v hlavním okně.\n\n";
        } else {
          message += "\n";
        }
        message += "Importován rok: " + currentYear + " (Year-to-Date)\n";
        message += "Metoda importu: IBKR Flex Query API\n\n";
        message += "Pro import historických let změňte Query ID v Nastavení.";
        
        javax.swing.JOptionPane.showMessageDialog(mainWindow,
            message,
            "Import dokončen", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        
        // Clear preview for next import
        clearPreview();
        updateIBKRFlexButtonState();
        
        System.out.println("[IBKR:MERGE:006] ✅ Merge completed successfully");
      } catch (Exception e) {
        System.out.println("[IBKR:MERGE:007] ❌ Merge failed: " + e.getMessage());
        e.printStackTrace();
        javax.swing.JOptionPane.showMessageDialog(this, 
            "Chyba při slučování: " + e.getMessage(),
            "Chyba", javax.swing.JOptionPane.ERROR_MESSAGE);
      }
      return;
    }
    
    // FETCH MODE: Download data from IBKR Flex API
    System.out.println("[IBKR:VALIDATE:004] ✅ Merge mode check: PASS (fetch mode)");
    
    // Get credentials
    String token = cz.datesoft.stockAccounting.Settings.getIbkrFlexToken();
    String queryId = cz.datesoft.stockAccounting.Settings.getIbkrFlexQueryId();
    
    System.out.println("[IBKR:VALIDATE:005] Credentials check:");
    System.out.println("   ├─ Token: " + (token != null && !token.trim().isEmpty() ? "✅ PRESENT" : "❌ MISSING"));
    System.out.println("   └─ Query ID: " + (queryId != null && !queryId.trim().isEmpty() ? "✅ PRESENT" : "❌ MISSING"));
    
    if (token == null || token.trim().isEmpty() || queryId == null || queryId.trim().isEmpty()) {
      System.out.println("[IBKR:VALIDATE:006] ❌ Credentials validation: FAIL - showing error dialog");
      javax.swing.JOptionPane.showMessageDialog(this,
          "IBKR Flex API přihlašovací údaje nejsou nakonfigurovány.\n" +
          "Nastavte je v Nastavení → IBKR Flex záložce.",
          "Chybí přihlašovací údaje", javax.swing.JOptionPane.WARNING_MESSAGE);
      return;
    }
    
    System.out.println("[IBKR:VALIDATE:007] ✅ All validations passed - proceeding to API call");
    
    try {
      // Create importer
      IBKRFlexImporter importer = new IBKRFlexImporter(token, queryId);
      importer.setAllowedAssetClasses(getSelectedIbkrAssetClasses());
      importer.setIncludeCorporateActions(cbIBKRFlexIncludeCorporateActions == null || cbIBKRFlexIncludeCorporateActions.isSelected());
      int mode = getIbkrImportMode();
      importer.setIncludeTrades(mode != IBKR_MODE_TRANS_ONLY);
      if (mode == IBKR_MODE_TRADES_ONLY) {
        importer.setIncludeCorporateActions(false);
      } else if (mode == IBKR_MODE_TRANS_ONLY) {
        importer.setIncludeCorporateActions(true);
      }
      importer.setParentFrame(mainWindow);
      
      // Import current year data
      int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
      Vector<Integer> years = new Vector<>();
      years.add(currentYear);
      
      System.out.println("[IBKR:PROGRESS:001] Creating progress dialog for IBKR Flex import");
      
      // Create progress dialog (modal) - this will show progress and wait for completion
      IBKRFlexProgressDialog progressDialog = new IBKRFlexProgressDialog(
        mainWindow, importer, years);
      
      // Start import first (runs in background)
      progressDialog.startImport();
      
      // Then show dialog and wait for completion (blocks until done)
      IBKRFlexImporter.ImportResult result = progressDialog.waitForCompletion();
      
      System.out.println("[IBKR:RESULT:001] Progress dialog completed - result.success=" + result.success);
      
      if (!result.success) {
        System.out.println("[IBKR:ERROR:001] Import failed: " + result.message);
        javax.swing.JOptionPane.showMessageDialog(this,
          "Import selhal: " + result.message,
          "Chyba", javax.swing.JOptionPane.ERROR_MESSAGE);
        return;
      }
      
      // Collect all transactions from all years
      Vector<Transaction> allTransactions = new Vector<>();
      for (IBKRFlexImporter.ImportYearResult yearResult : result.yearsImported) {
        allTransactions.addAll(yearResult.transactions);
      }
      
      System.out.println("[IBKR:RESULT:002] Downloaded " + allTransactions.size() + " total transactions");

      // Store cached CSV if available so preview can be refreshed without re-downloading
      if (result.yearsImported != null && !result.yearsImported.isEmpty()) {
        IBKRFlexImporter.ImportYearResult yr = result.yearsImported.get(0);
        if (yr != null && yr.csvData != null && !yr.csvData.isEmpty()) {
          lastIbkrCsvContent = yr.csvData;
          lastIbkrSourceLabel = "IBKR API";
          ibkrPreviewDirty = false;
          if (bIBKRFlexRefreshPreview != null) {
            bIBKRFlexRefreshPreview.setEnabled(false);
          }
        }
      }
      
      // Filter to current year only
      Vector<Transaction> currentYearTransactions = filterToCurrentYear(allTransactions);
      System.out.println("[IBKR:RESULT:003] Filtered to current year: " + currentYearTransactions.size() + " transactions");

      // Disambiguate rare collisions caused by minute-level timestamp precision.
      // This keeps IBKR Flex re-import stable: update one existing row, insert the other(s).
      disambiguateIbkrDuplicateCollisions(mainWindow.getTransactionDatabase(), currentYearTransactions);
      
      // Clear existing transactions from preview table
      System.out.println("[IBKR:UI:001] Clearing existing transactions from preview table");
      transactions.clear();
      
      // Filter out duplicate transactions that already exist in main database
      System.out.println("[IBKR:DUPLICATE:001] Checking for duplicates against main database");
      Vector<Transaction> filteredTransactions = mainWindow.getTransactionDatabase().filterDuplicates(currentYearTransactions);
      int duplicatesFiltered = currentYearTransactions.size() - filteredTransactions.size();
      
      // Clear previous duplicates list
      duplicatesToUpdate.clear();
      
      // If update checkbox is checked, store duplicates for later update
      if (cbUpdateDuplicates.isSelected() && duplicatesFiltered > 0) {
        for (Transaction candidate : currentYearTransactions) {
          if (!filteredTransactions.contains(candidate)) {
            duplicatesToUpdate.add(candidate);
          }
        }
        System.out.println("[IBKR:DUPLICATE:002] " + duplicatesToUpdate.size() + " duplicates marked for update");
      }
      
      System.out.println("[IBKR:DUPLICATE:003] Filtered " + duplicatesFiltered + " duplicates, adding " + 
                         filteredTransactions.size() + " new transactions to preview table");
      
      // Add filtered transactions to preview table
      transactions.rows.addAll(filteredTransactions);
      transactions.fireTableDataChanged();
      System.out.println("[IBKR:UI:002] All new transactions added to preview table");
      
      // Update UI labels to show preview with duplicate count
      System.out.println("[IBKR:UI:003] Updating UI labels");
      String previewText = "Náhled (" + filteredTransactions.size() + " záznamů)";
      if (duplicatesFiltered > 0) {
        if (cbUpdateDuplicates.isSelected()) {
          previewText += " - " + duplicatesFiltered + " duplikátů k aktualizaci";
        } else {
          previewText += " - " + duplicatesFiltered + " duplikátů vyfiltrováno";
        }
      }
      previewText += ":";
      lPreview.setText(previewText);
      lUnimported.setText("Neimportované řádky (0 záznamů):");
      
      // Update status label
      if (lblIBKRFlexStatus != null) {
        lblIBKRFlexStatus.setText("Staženo " + filteredTransactions.size() + " transakcí pro rok " + currentYear);
      }
      
      // Update button to show merge mode
      updateIBKRFlexButtonState();
      
      System.out.println("[IBKR:COMPLETE:001] Preview population finished successfully");
      
    } catch (Exception e) {
      String errorMessage = "Chyba při importu z IBKR: " + e.getMessage();
      System.out.println("[IBKR:ERROR:003] Exception during import: " + errorMessage);
      e.printStackTrace();
      javax.swing.JOptionPane.showMessageDialog(this,
          errorMessage,
          "Chyba importu", javax.swing.JOptionPane.ERROR_MESSAGE);
      if (lblIBKRFlexStatus != null) {
        lblIBKRFlexStatus.setText("Chyba: " + e.getMessage());
      }
    }
  }

  private void setInterfaceEnabled(boolean enabled) {
    if (bImport != null) {
      bImport.setEnabled(enabled);
      System.out.println("[UI-STATE:001] Import button: " + (enabled ? "ENABLED" : "DISABLED"));
    }
    if (bClearPreview != null) {
      bClearPreview.setEnabled(enabled);
      System.out.println("[UI-STATE:005] Clear Preview button: " + (enabled ? "ENABLED" : "DISABLED"));
    }
    if (cbTrading212Year != null) {
      cbTrading212Year.setEnabled(enabled);
      System.out.println("[UI-STATE:006] Year dropdown: " + (enabled ? "ENABLED" : "DISABLED"));
    }
    if (bCancel != null) {
      bCancel.setEnabled(enabled);
      System.out.println("[UI-STATE:007] Cancel button: " + (enabled ? "ENABLED" : "DISABLED"));
    }
  }

  private void handleImportError(String errorMessage) {
    if (errorMessage.toLowerCase().contains("timeout")) {
      errorMessage += "\n\nSuggestion: Try a smaller date range or try again later when the service is less busy.";
    } else if (errorMessage.toLowerCase().contains("credentials") || errorMessage.toLowerCase().contains("auth")) {
      errorMessage += "\n\nSuggestion: Verify your API key and secret in Settings → Trading 212 API tab.";
    } else if (errorMessage.toLowerCase().contains("range") || errorMessage.toLowerCase().contains("2 years")) {
      errorMessage += "\n\nSuggestion: Select a shorter date range (maximum 2 years per import).";
    } else if (errorMessage.toLowerCase().contains("network") || errorMessage.toLowerCase().contains("connection")) {
      errorMessage += "\n\nSuggestion: Check your internet connection and try again.";
    }

    javax.swing.JOptionPane.showMessageDialog(ImportWindow.this, errorMessage, "Import Error",
        javax.swing.JOptionPane.ERROR_MESSAGE);
  }

  private void performTrading212Import(boolean mergeMode) {
    performTrading212Import(mergeMode, false); // Default: no force refresh
  }
  
  private void performTrading212Import(boolean mergeMode, boolean forceRefresh) {
    // Note: No importInProgress check here - internal method called from startImport()
    // which already manages concurrency protection for external calls

    System.out.println("[VALIDATE:001] performTrading212Import called with mergeMode=" + mergeMode + ", forceRefresh=" + forceRefresh);

    if (!isTrading212Format()) {
        System.out.println("[VALIDATE:002] ❌ Format check: FAIL (not Trading212 format)");
      return;
    }

    System.out.println("[VALIDATE:003] ✅ Format check: PASS");

    if (mergeMode) {
        // MERGE MODE: Merge existing preview data to database
        System.out.println("[MERGE:001] Starting merge mode - merging preview to database");

        try {
            // Track initial count for accurate reporting
            int initialTransactionCount = mainWindow.getTransactionDatabase().getRowCount();
            System.out.println("[MERGE:001b] Initial transaction count in database: " + initialTransactionCount);

            transactions.mergeTo(mainWindow.getTransactionDatabase());

            // Update duplicates if checkbox is checked
            int updatedCount = 0;
            if (cbUpdateDuplicates.isSelected() && !duplicatesToUpdate.isEmpty()) {
              TransactionSet mainDb = mainWindow.getTransactionDatabase();
              
              System.out.println("[UPDATE:001] Updating " + duplicatesToUpdate.size() + " duplicate transactions");
              
              // Start batch update to prevent double-updating same transaction
              mainDb.startBatchUpdate();
              
              for (Transaction candidate : duplicatesToUpdate) {
                if (mainDb.updateDuplicateTransaction(candidate)) {
                  updatedCount++;
                }
              }
              
              // End batch update
              mainDb.endBatchUpdate();
              
              System.out.println("[UPDATE:002] Successfully updated " + updatedCount + " transactions");
            }

            // Clear duplicates list
            duplicatesToUpdate.clear();

            // Invalidate transformation cache after import (new transactions may have transformations)
            System.out.println("DEBUG: Invalidating transformation cache after API import");
            mainWindow.getTransactionDatabase().invalidateTransformationCache();

            // Refresh metadata filter dropdowns after API import
            System.out.println("DEBUG: Refreshing metadata filters after API import");
            mainWindow.refreshMetadataFilters();

            // Calculate actual transactions added
            int finalTransactionCount = mainWindow.getTransactionDatabase().getRowCount();
            int transactionsAdded = finalTransactionCount - initialTransactionCount;
            System.out.println("[MERGE:001c] Final transaction count: " + finalTransactionCount + ", added: " + transactionsAdded);

            // Force immediate table refresh on EDT
            javax.swing.SwingUtilities.invokeLater(() -> {
              mainWindow.refreshTable();
            });

            // Get the year from the dropdown to update status
            String selectedItem = (String) cbTrading212Year.getSelectedItem();
            if (selectedItem != null) {
              String yearStr = selectedItem.split(" ")[0];
              try {
                int year = Integer.parseInt(yearStr);

                // Update import state and persist to Settings (only after successful merge)
                importState.markYearFullyImported(year, java.time.LocalDateTime.now());

                // Refresh year dropdown to show updated status
                refreshTrading212YearStatuses();
              } catch (NumberFormatException e) {
                // Ignore if year parsing fails
              }
            }

            // Show success message with accurate count
            String message = "Úspěšně importováno " + transactionsAdded + " transakcí!\n";
            if (updatedCount > 0) {
              message += "Aktualizováno: " + updatedCount + " existujících záznamů\n\n";
              message += "Aktualizované řádky jsou zvýrazněny žlutě v hlavním okně.\n\n";
            } else {
              message += "\n";
            }
            message += "Metoda importu: CSV Report (komplexní data včetně objednávek, dividend a úroků)\n\n";
            message += "Nyní můžete importovat další rok nebo zavřít toto okno.";
            
            javax.swing.JOptionPane.showMessageDialog(mainWindow,
                message,
                "Import dokončen", javax.swing.JOptionPane.INFORMATION_MESSAGE);

            // Clear preview for next import
            clearPreviewAfterMerge();

            System.out.println("[MERGE:002] ✅ Merge completed successfully");
        } catch (Exception e) {
            System.out.println("[MERGE:003] ❌ Merge failed: " + e.getMessage());
            javax.swing.JOptionPane.showMessageDialog(this, "Merge failed: " + e.getMessage());
        }
        return;
    }

    System.out.println("[VALIDATE:004] ✅ Merge mode check: PASS (preview mode)");

    // Get selected year
    String selectedItem = (String) cbTrading212Year.getSelectedItem();
    System.out.println("[VALIDATE:006] Year selection raw: '" + selectedItem + "'");

    if (selectedItem == null) {
        System.out.println("[VALIDATE:007] ❌ Year selection: NULL - showing 'select year' dialog");
      javax.swing.JOptionPane.showMessageDialog(this, "Please select a year to import.");
      return;
    }

    // Extract year number from selection (format: "2024 (Not Imported)")
    String yearStr = selectedItem.split(" ")[0];
    System.out.println("[VALIDATE:009] Year string extracted: '" + yearStr + "'");

    final int year;
    try {
      year = Integer.parseInt(yearStr);
      System.out.println("[VALIDATE:010] ✅ Year parsing: SUCCESS - year = " + year);
    } catch (NumberFormatException e) {
      System.out.println("[VALIDATE:011] ❌ Year parsing: FAILED - '" + yearStr + "' not numeric - showing error dialog");
      javax.swing.JOptionPane.showMessageDialog(this, "Invalid year selection: " + selectedItem);
      return;
    }

    System.out.println("[VALIDATE:012] ✅ Year validation: PASSED");

    // Get credentials
    String apiKey = cz.datesoft.stockAccounting.Settings.getTrading212ApiKey();
    String apiSecret = cz.datesoft.stockAccounting.Settings.getTrading212ApiSecret();

    System.out.println("[VALIDATE:013] Credentials check:");
    System.out.println("   ├─ API Key: " + (apiKey != null && !apiKey.trim().isEmpty() ? "✅ PRESENT" : "❌ MISSING"));
    System.out.println("   ├─ API Secret: " + (apiSecret != null && !apiSecret.trim().isEmpty() ? "✅ PRESENT" : "❌ MISSING"));

    if (apiKey == null || apiKey.trim().isEmpty() ||
        apiSecret == null || apiSecret.trim().isEmpty()) {
      System.out.println("[VALIDATE:014] ❌ Credentials validation: FAIL - showing error dialog");
      javax.swing.JOptionPane.showMessageDialog(this,
          "Trading 212 API credentials not configured.\n" +
              "Please set them in Settings → Trading 212 API tab.");
      return;
    }

    System.out.println("[VALIDATE:015] ✅ All validations passed - proceeding to API call");

    // Disable UI during import
    setInterfaceEnabled(false);
    System.out.println("[ASYNC:001] UI disabled, creating background worker for API fetch");

    // Perform the import in a background thread to avoid blocking EDT
    final javax.swing.SwingWorker<cz.datesoft.stockAccounting.Trading212Importer.ImportResult, Void> worker = new javax.swing.SwingWorker<cz.datesoft.stockAccounting.Trading212Importer.ImportResult, Void>() {

      @Override
      protected cz.datesoft.stockAccounting.Trading212Importer.ImportResult doInBackground() throws Exception {
        System.out.println("[API:001] Background thread started - doInBackground()");
        cz.datesoft.stockAccounting.Trading212Importer importer = new cz.datesoft.stockAccounting.Trading212Importer(
            apiKey, apiSecret, cz.datesoft.stockAccounting.Settings.getTrading212UseDemo());
        importer.setParentFrame(mainWindow); // Pass MainWindow reference for progress dialogs (more reliable than getOwner())
        importer.setForceRefresh(forceRefresh); // Set force refresh flag to bypass cache if requested
        cz.datesoft.stockAccounting.Trading212Importer.ImportResult result = importer.importYear(year, this);
        System.out.println("[API:005] importYear() completed");
        System.out.println("[API:006] Result object: " + (result != null ? "NOT NULL" : "NULL"));
        System.out.println("[API:007] result.success = " + (result != null ? result.success : "N/A"));
        System.out.println("[API:008] result.transactionsImported = " + (result != null ? result.transactionsImported : "N/A"));
        System.out.println("[API:009] result.transactions size = " + (result != null && result.transactions != null ? result.transactions.size() : "N/A"));
        return result;
      }

      @Override
      protected void done() {
        System.out.println("[RESULT:001] done() method called - switching to EDT");

        // Check if this worker was canceled (e.g., via progress dialog cancel button)
        if (isCancelled()) {
          System.out.println("[CANCEL:002] Worker was canceled, not populating preview");
          setInterfaceEnabled(true); // Re-enable UI
          return; // Don't process results if canceled
        }

        try {
          cz.datesoft.stockAccounting.Trading212Importer.ImportResult result = get();
          System.out.println("[RESULT:004] get() succeeded - result retrieved");
          System.out.println("[RESULT:005] Processing result - success: " + result.success);

           if (result.success) {
             System.out.println("[RESULT:006] ✅ API fetch successful - processing " + result.transactions.size() + " transactions");

             // PREVIEW MODE: Filter duplicates and populate transactions table for display
             System.out.println("[UI:001] Clearing existing transactions from preview table");
             transactions.clear();

             // Filter out duplicate transactions that already exist in main database
             System.out.println("[DUPLICATE:001] Checking for duplicates against main database");
             Vector<Transaction> filteredTransactions = mainWindow.getTransactionDatabase().filterDuplicates(result.transactions);
             int duplicatesFiltered = result.transactions.size() - filteredTransactions.size();

             // Clear previous duplicates list
             duplicatesToUpdate.clear();

             // If update checkbox is checked, store duplicates for later update
             if (cbUpdateDuplicates.isSelected() && duplicatesFiltered > 0) {
               for (Transaction candidate : result.transactions) {
                 if (!filteredTransactions.contains(candidate)) {
                   duplicatesToUpdate.add(candidate);
                 }
               }
               System.out.println("[DUPLICATE:002] " + duplicatesToUpdate.size() + " duplicates marked for update");
             }

             System.out.println("[DUPLICATE:003] Filtered " + duplicatesFiltered + " duplicates, adding " + filteredTransactions.size() + " new transactions to preview table");
             for (Transaction tx : filteredTransactions) {
               transactions.addTransaction(tx.getDate(), tx.getDirection().intValue(), tx.getTicker(),
                   tx.getAmount().doubleValue(), tx.getPrice().doubleValue(), tx.getPriceCurrency(),
                   tx.getFee().doubleValue(), tx.getFeeCurrency(), tx.getMarket(), tx.getExecutionDate(), tx.getNote());
             }
             System.out.println("[UI:003] All new transactions added to table");

             // Update UI labels to show preview with duplicate count
             System.out.println("[UI:004] Updating UI labels");
             String previewText = "Náhled (" + filteredTransactions.size() + " záznamů)";
             if (duplicatesFiltered > 0) {
               if (cbUpdateDuplicates.isSelected()) {
                 previewText += " - " + duplicatesFiltered + " duplikátů k aktualizaci";
               } else {
                 previewText += " - " + duplicatesFiltered + " duplikátů vyfiltrováno";
               }
             }
             previewText += ":";
             lPreview.setText(previewText);
             lUnimported.setText("Neimportované řádky (0 záznamů):");
             System.out.println("[UI:005] UI labels updated");

            // Cache the filtered transactions for this year (session-only)
            // This ensures merge mode uses the same filtered set as preview
            System.out.println("[CACHE:001] Caching " + filteredTransactions.size() + " filtered transactions for year " + year);
            importState.cacheTransactions(year, filteredTransactions);

            // Update cache status UI
            System.out.println("[CACHE:002] Updating cache status display");
            updateCacheStatus();
            System.out.println("[CACHE:003] Cache operations completed");

            // Update import button to show "Sloučit do databáze" now that we have preview data
            System.out.println("[UI:006] Updating import button text for merge mode");
            updateImportButtonText();
            System.out.println("[UI:007] Import button text updated");

            System.out.println("[COMPLETE:001] Preview population finished successfully");

          } else {
            System.out.println("[RESULT:007] ❌ API fetch failed - message: " + result.message);
            handleImportError("Import z Trading 212 selhal: " + result.message);
          }
        } catch (InterruptedException e) {
          System.out.println("[ERROR:001] InterruptedException: " + e.getMessage());
          handleImportError("Import zrušen.");
        } catch (java.util.concurrent.ExecutionException e) {
          Throwable cause = e.getCause();
          String errorMessage = "Chyba během importu: " + (cause != null ? cause.getMessage() : e.getMessage());
          System.out.println("[ERROR:002] ExecutionException: " + errorMessage);
          handleImportError(errorMessage);
        } catch (Exception e) {
          String errorMessage = "Chyba při zpracování dat: " + e.getMessage();
          System.out.println("[ERROR:003] Processing exception: " + errorMessage);
          e.printStackTrace();
          handleImportError(errorMessage);
        } finally {
          System.out.println("[CLEANUP:001] Re-enabling UI controls");
          setInterfaceEnabled(true);
        }
      }
    };

    worker.execute();
  }



  /**
   * Clear preview data after successful merge
   */
  private void clearPreviewAfterMerge() {
    transactions.clear();
    lPreview.setText("Náhled (0 záznamů):");
    lUnimported.setText("Neimportované řádky (0 záznamů):");
    updateImportButtonText();
    updateImportButtonState();
    updateClearButtonState();
  }

  /**
   * Clear preview data manually
   */
  private void clearPreview() {
    System.out.println("[CLEAR:001] Clearing preview data manually");
    transactions.clear();
    duplicatesToUpdate.clear();
    lPreview.setText("Náhled (0 záznamů):");
    lUnimported.setText("Neimportované řádky (0 záznamů):");
    updateImportButtonText();
    updateImportButtonState();
    updateClearButtonState();
    System.out.println("[CLEAR:002] Preview cleared successfully");
  }

  private void clearIbkrPreviewAndCache() {
    clearPreview();
    clearIbkrCachedData();

    if (lblIBKRFlexStatus != null) {
      lblIBKRFlexStatus.setText("Vyberte zdroj dat: API nebo lokální soubor");
    }
  }

  /**
   * Update UI components visibility based on selected format
   */
  private void updateUiForFormat(int formatIndex) {
     boolean isApiFormat = (formatIndex == 8 || formatIndex == 9); // Trading 212 API or IBKR Flex API
     boolean isTrading212 = (formatIndex == 8);
     boolean isIBKR = (formatIndex == 9);

    // Clear preview when switching to API format to prevent data contamination from previous imports
    if (isApiFormat && transactions.getRowCount() > 0) {
      System.out.println("[FORMAT:001] Clearing preview when switching to API format");
      clearPreview();
    }

    // Hide date selection UI for API formats (we use year dropdown/status instead)
    jLabel1.setVisible(!isApiFormat); // "Importovat od:"
    jLabel2.setVisible(!isApiFormat); // "do:"
    startDate.setVisible(!isApiFormat);
    endDate.setVisible(!isApiFormat);

     // Keep preview tables visible for API formats - they show fetched data
     // IBKR Flex uses its own status line; hide the generic preview header to avoid layout conflicts.
     lPreview.setVisible(!isIBKR);
     jScrollPane1.setVisible(true);
     lUnimported.setVisible(true);
     niScrollPane.setVisible(true);

     // Hide refresh button for API (not needed - user clicks Import to fetch)
     bRefresh.setVisible(!isApiFormat);

      // For IBKR Flex we use dedicated buttons (API/file/merge).
     // Hide the generic bottom "Importovat" button to avoid duplicate merge paths.
     bImport.setVisible(!isIBKR);
     // Cancel is redundant when bImport is hidden; user can close the window directly.
     bCancel.setVisible(!isIBKR);

     // Hide duplicates checkbox for IBKR (we already support duplicates via existing checkbox logic)
     cbUpdateDuplicates.setVisible(!isIBKR);

    // Show/hide API-specific UI
    if (isTrading212) {
      setupTrading212YearSelection();
      hideIBKRFlexUI();
    } else if (isIBKR) {
      showIBKRFlexUI();
      hideTrading212YearSelection();
    } else {
      hideTrading212YearSelection();
      hideIBKRFlexUI();
    }

    boolean isLocalFile = isLocalFileFormat(formatIndex);
    if (bSelectFile != null) {
      bSelectFile.setVisible(isLocalFile);
    }
    if (lSelectedFile != null) {
      lSelectedFile.setVisible(isLocalFile);
      updateSelectedFileLabel();
    }

    // Update button text and state based on current state
    updateImportButtonText();
    updateImportButtonState(); // Check API credentials

    // Repack to adjust window size
    pack();
  }

  /**
   * Update import button text based on current state
   */
  private void updateImportButtonText() {
    if (isTrading212Format()) {
      // Fix: Check actual transaction data, not getRowCount() which always returns >= 1
      if (transactions.rows.isEmpty()) {
        bImport.setText("API stahnuti"); // Fetch mode - no data loaded yet (more specific for API)
      } else {
        bImport.setText("Sloučit do databáze"); // Merge mode - data ready to import
      }
    } else {
      bImport.setText("Importovat"); // File import
    }
  }

  /**
   * Update clear preview button state
   */
  private void updateClearButtonState() {
    if (bClearPreview != null) {
      bClearPreview.setEnabled(transactions.getRowCount() > 0);
    }
  }

  /**
   * Update window title based on selected format
   */
  private void updateWindowTitle() {
    if (isTrading212Format()) {
      setTitle("Import z Trading 212 API");
    } else if (isIBKRFlexFormat()) {
      setTitle("Import z IBKR Flex");
    } else {
      setTitle("Import souboru");
    }
  }

  /**
   * Log current UI component states for debugging format switching issues
   */
  private void logUIComponentStates() {
    System.out.println("[UI:STATE] Current UI component states:");
    System.out.println("[UI:STATE]   - Format dropdown: " + cbFormat.getSelectedIndex() + " (" + cbFormat.getSelectedItem() + ")");
    System.out.println("[UI:STATE]   - Window title: '" + getTitle() + "'");
    System.out.println("[UI:STATE]   - T212 year selector visible: " + (cbTrading212Year != null ? cbTrading212Year.isVisible() : "null"));
    System.out.println("[UI:STATE]   - Clear preview button visible: " + (bClearPreview != null ? bClearPreview.isVisible() : "null"));
    System.out.println("[UI:STATE]   - Cache status label visible: " + (lCacheStatus != null ? lCacheStatus.isVisible() : "null"));
    System.out.println("[UI:STATE]   - Preview table row count: " + (transactions != null ? transactions.getRowCount() : "null"));
    System.out.println("[UI:STATE]   - Import in progress flag: " + importInProgress);
    System.out.println("[UI:STATE]   - Current file: " + (currentFile != null ? currentFile.getName() : "null"));
  }

  private void populateTrading212YearDropdown() {
    cbTrading212Year.removeAllItems();

    int currentYear = java.time.LocalDate.now().getYear();

    // Add years from current year back to 2020 (reasonable limit)
    for (int year = currentYear; year >= 2020; year--) {
      String status = getTrading212YearStatus(year);
      cbTrading212Year.addItem(year + " " + status);
    }

    cbTrading212Year.setSelectedIndex(0); // Select current year
  }

  /**
   * Refresh the year dropdown with updated import statuses
   */
  private void refreshTrading212YearStatuses() {
    // Repopulate the dropdown to show updated statuses
    populateTrading212YearDropdown();
  }

  private String getTrading212YearStatus(int year) {
    boolean imported = importState.isYearFullyImported(year);
    boolean partial = importState.getLastImportDate(year) != null;
    
    // Check if cached (requires account ID, which we get from importState)
    boolean cached = false;
    String accountId = importState.getAccountId();
    if (accountId != null && !accountId.isEmpty()) {
      Trading212CsvCache csvCache = new Trading212CsvCache();
      cached = csvCache.hasCachedCsv(accountId, year);
    }
    
    // Build status string
    if (imported && cached) {
      return "(Imported • Cached)";
    } else if (imported) {
      return "(Imported)";
    } else if (cached) {
      return "(Cached)";
    } else if (partial) {
      return "(Partial)";
    } else {
      return "(Not Imported)";
    }
  }

  private void bImportActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bImportActionPerformed
  {// GEN-HEADEREND:event_bImportActionPerformed
    // Really do import
    System.out.println("[BUTTON:001] Importovat button clicked");
    try {
      int formatIndex = cbFormat != null ? cbFormat.getSelectedIndex() : 0;
      if (isLocalFileFormat(formatIndex) && currentFile == null) {
        selectLocalImportFile();
        return;
      }

      if (isTrading212Format()) {
        // Check if API credentials are configured
        if (!hasValidApiCredentials()) {
          System.out.println("[BUTTON:002] No API credentials - opening settings");
          openSettings_Trading212Tab();
          return; // Don't proceed with import
        }
        
        System.out.println("[BUTTON:002] Trading212 format detected");
        System.out.println("[BUTTON:003] Current transactions count: " + transactions.rows.size());

        // Check if we already have preview data
        boolean hasPreviewData = !transactions.rows.isEmpty();
        System.out.println("[BUTTON:004] Preview data present: " + hasPreviewData);

        if (hasPreviewData) {
          // MERGE MODE: Merge existing preview data to database
          System.out.println("[BUTTON:005] Merging existing preview data to database");
          performTrading212Import(true); // Merge mode
        } else {
          // FETCH MODE: Fetch fresh data from API
          System.out.println("[BUTTON:005] Fetching fresh data from API");
          performTrading212Import(false); // Fetch mode
        }
        // Keep window open for sequential API imports
      } else {
        // Handle regular file-based import
        transactions.mergeTo(mainWindow.getTransactionDatabase());

        // Update duplicates if checkbox is checked
        if (cbUpdateDuplicates.isSelected() && !duplicatesToUpdate.isEmpty()) {
          int updatedCount = 0;
          TransactionSet mainDb = mainWindow.getTransactionDatabase();
          
          System.out.println("[UPDATE:001] Updating " + duplicatesToUpdate.size() + " duplicate transactions");
          
          // Start batch update to prevent double-updating same transaction
          mainDb.startBatchUpdate();
          
          for (Transaction candidate : duplicatesToUpdate) {
            if (mainDb.updateDuplicateTransaction(candidate)) {
              updatedCount++;
            }
          }
          
          // End batch update
          mainDb.endBatchUpdate();
          
          // Notify table of changes
          mainDb.fireTableDataChanged();
          
          System.out.println("[UPDATE:002] Successfully updated " + updatedCount + " transactions");
          
          // Show success message to user
          if (updatedCount > 0) {
            JOptionPane.showMessageDialog(this, 
              "Importováno: " + transactions.getRowCount() + " nových záznamů\n" +
              "Aktualizováno: " + updatedCount + " existujících záznamů\n\n" +
              "Aktualizované řádky jsou zvýrazněny žlutě v hlavním okně.",
              "Import dokončen", 
              JOptionPane.INFORMATION_MESSAGE);
          }
        }

        // Clear duplicates list
        duplicatesToUpdate.clear();

        // Invalidate transformation cache after import
        System.out.println("DEBUG: Invalidating transformation cache after file import");
        mainWindow.getTransactionDatabase().invalidateTransformationCache();

        // Refresh metadata filter dropdowns after import
        System.out.println("DEBUG: Refreshing metadata filters after import");
        mainWindow.refreshMetadataFilters();

        dispose(); // Close window after file import
      }
    } catch (Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this, "Při importu došlo k chybě: " + e + "\nByla importována jen číst záznamů.");
      // Keep window open for retry on errors
    }
  }// GEN-LAST:event_bImportActionPerformed

  private void bRefreshActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bRefreshActionPerformed
  {// GEN-HEADEREND:event_bRefreshActionPerformed
    // For local file imports we may not have a file selected yet.
    int formatIndex = cbFormat != null ? cbFormat.getSelectedIndex() : 0;
    if (isLocalFileFormat(formatIndex) && currentFile == null) {
      selectLocalImportFile();
      return;
    }

    loadImport();
  }// GEN-LAST:event_bRefreshActionPerformed

  private void bCancelActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bCancelActionPerformed
  {// GEN-HEADEREND:event_bCancelActionPerformed
    dispose();
  }// GEN-LAST:event_bCancelActionPerformed

   private void cbFormatActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_cbFormatActionPerformed

     System.out.println("[FORMAT:SWITCH] cbFormatActionPerformed triggered - new format index: " + cbFormat.getSelectedIndex());

      // Clear programmatic format override when user manually changes format
      currentImportFormat = 0;

      updateObsoleteFormatWarning();

    if (cbFormat.getSelectedIndex() != 0) {
      System.out.println("[FORMAT:SWITCH] Updating UI for format: " + cbFormat.getSelectedIndex());
      updateUiForFormat(cbFormat.getSelectedIndex());
      updateWindowTitle(); // Update window title based on format

      // Log UI component states after format change
      logUIComponentStates();

      if (isTrading212Format()) {
        // For Trading 212 API format, show the year selection UI
        // Don't auto-fetch - wait for user to select year and click Import button
       } else if (isIBKRFlexFormat()) {
        // For IBKR Flex API format, show the IBKR UI
        // Don't auto-fetch - wait for user to click Import button
       } else {
         loadImport(); // For file-based formats
       }
     }

     // Save the selected format for persistence across app restarts
     cz.datesoft.stockAccounting.Settings.setLastImportFormat(cbFormat.getSelectedIndex());
     cz.datesoft.stockAccounting.Settings.save();

  }// GEN-LAST:event_cbFormatActionPerformed

  private void cbUpdateDuplicatesActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_cbUpdateDuplicatesActionPerformed
    // Save checkbox state to settings
    cz.datesoft.stockAccounting.Settings.setUpdateDuplicatesOnImport(cbUpdateDuplicates.isSelected());
    cz.datesoft.stockAccounting.Settings.save();
    
    if (isIBKRFlexFormat()) {
      markIbkrPreviewDirty("Nastavení duplikátů změněno – náhled není aktuální, klikněte na Obnovit náhled");
      return;
    }

    // Update preview label to reflect current mode if there are duplicates
    if (!duplicatesToUpdate.isEmpty() || transactions.getRowCount() > 0) {
      loadImport(); // Reload to update the label text
    }
  }// GEN-LAST:event_cbUpdateDuplicatesActionPerformed

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        new ImportWindow(new javax.swing.JFrame(), true).setVisible(true);
      }
    });
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bCancel;
  private javax.swing.JButton bImport;
  private javax.swing.JButton bRefresh;
  private javax.swing.JComboBox cbFormat;
  private javax.swing.JCheckBox cbUpdateDuplicates;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel4;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel lPreview;
  private javax.swing.JLabel lUnimported;
  private javax.swing.JScrollPane niScrollPane;
  private javax.swing.JTable niTable;
  private javax.swing.JButton bClearPreview;
  private javax.swing.JLabel lCacheStatus;
  private javax.swing.JTable table;
  // End of variables declaration//GEN-END:variables

}
