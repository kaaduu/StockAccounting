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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

  // Files we are importing (TradeLog multi-select)
  private List<File> currentFiles;
  private boolean tradeLogMultiSelection;

  // Busy overlay for long-running preview loads
  private javax.swing.JComponent busyGlass;
  private javax.swing.JLabel busyLabel;
  private javax.swing.JProgressBar busyBar;
  private boolean previewLoadInProgress;
  private boolean previewReloadRequested;

  // Preview: updates (duplicates to update)
  // (declared in variables section)
  private java.util.List<UpdatePair> previewUpdatePairs = new java.util.ArrayList<>();

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
  private javax.swing.JLabel lblIbkrFlexCsvInfo = null;
  private javax.swing.JButton bIbkrFlexCsvDetails = null;
  private javax.swing.JLabel lblIbkrFlexCsvSpacer = null;

  // Deduplicate IBKR Flex structural warnings per loaded CSV content
  private String lastIbkrFlexStructureWarnKey = null;

  // Local file selection components (for file-based imports)
  private javax.swing.JButton bSelectFile;
  private javax.swing.JLabel lSelectedFile;

  private String lastIbkrCsvContent = null;
  private String lastIbkrSourceLabel = null;
  private boolean ibkrPreviewDirty = false;

  private static final int IBKR_MODE_TRADES_AND_TRANS = 0;
  private static final int IBKR_MODE_TRADES_ONLY = 1;
  private static final int IBKR_MODE_TRANS_ONLY = 2;
  private static final int IBKR_MODE_DIVI_ONLY = 3;

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
    // Prefer pack-based sizing + reasonable minimum.
    this.setResizable(true); // Enable maximize button

    // Initialize import state (constructor automatically loads from Settings)
    importState = new Trading212ImportState();

    GridBagConstraints gbc;

    startDate = new JDateChooser();
    // Let layout decide width

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
    // Let layout decide width

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
        if (evt != null && evt.getPropertyName() != null && !"date".equals(evt.getPropertyName())) {
          return;
        }
        loadImport();
      }
    });

    startDate.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
      public void propertyChange(java.beans.PropertyChangeEvent evt) {
        if (evt != null && evt.getPropertyName() != null && !"date".equals(evt.getPropertyName())) {
          return;
        }
        loadImport();
      }
    });

    getContentPane().doLayout();

    transactions = new TransactionSet();
    table.setModel(transactions);

    table.getColumnModel().getColumn(0).setPreferredWidth(200);
    table.getColumnModel().getColumn(0).setCellRenderer(new CZDateRenderer());

    table.getColumnModel().getColumn(10).setCellRenderer(new CZDateRenderer());

    // niTable.setTableHeader(new JTableHeader());
    
    // Restore last checkbox state from settings
    cbUpdateDuplicates.setSelected(cz.datesoft.stockAccounting.Settings.getUpdateDuplicatesOnImport());

    // File chooser preference is configured in Settings
    
    updateWindowTitle(); // Set initial window title

    // Initial warning state based on default selection
    updateObsoleteFormatWarning();

    // Busy overlay (glass pane) for long-running preview loads
    initBusyOverlay();

    pack();
    setMinimumSize(new java.awt.Dimension(900, 620));
  }

  private void initBusyOverlay() {
    javax.swing.JPanel p = new javax.swing.JPanel(new java.awt.GridBagLayout());
    p.setOpaque(true);
    p.setBackground(new java.awt.Color(255, 255, 255, 200));

    javax.swing.JPanel card = new javax.swing.JPanel(new java.awt.GridBagLayout());
    card.setOpaque(true);
    card.setBackground(new java.awt.Color(255, 255, 255));
    card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
        javax.swing.BorderFactory.createLineBorder(new java.awt.Color(180, 180, 180)),
        javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14)));

    busyLabel = new javax.swing.JLabel("Načítám…");
    busyLabel.setFont(busyLabel.getFont().deriveFont(java.awt.Font.BOLD));
    busyBar = new javax.swing.JProgressBar();
    busyBar.setIndeterminate(true);
    busyBar.setPreferredSize(new java.awt.Dimension(260, 14));

    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = java.awt.GridBagConstraints.WEST;
    gbc.insets = new java.awt.Insets(0, 0, 8, 0);
    card.add(busyLabel, gbc);

    gbc = new java.awt.GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    card.add(busyBar, gbc);

    gbc = new java.awt.GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = java.awt.GridBagConstraints.CENTER;
    p.add(card, gbc);

    // Eat mouse events so user can't interact while busy.
    p.addMouseListener(new java.awt.event.MouseAdapter() {
    });

    busyGlass = p;
    setGlassPane(busyGlass);
    busyGlass.setVisible(false);
  }

  private void showBusy(String message) {
    if (busyGlass == null) return;
    if (busyLabel != null && message != null) {
      busyLabel.setText(message);
    }
    previewLoadInProgress = true;
    java.awt.Cursor c = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR);
    setCursor(c);
    busyGlass.setVisible(true);

    // Disable key controls during preview loading
    if (cbFormat != null) cbFormat.setEnabled(false);
    if (bSelectFile != null) bSelectFile.setEnabled(false);
    if (bRefresh != null) bRefresh.setEnabled(false);
    if (startDate != null) startDate.setEnabled(false);
    if (endDate != null) endDate.setEnabled(false);
    if (cbUpdateDuplicates != null) cbUpdateDuplicates.setEnabled(false);
  }

  private void hideBusy() {
    if (busyGlass != null) {
      busyGlass.setVisible(false);
    }
    setCursor(java.awt.Cursor.getDefaultCursor());
    previewLoadInProgress = false;

    if (cbFormat != null) cbFormat.setEnabled(true);
    if (bSelectFile != null) bSelectFile.setEnabled(true);
    if (bRefresh != null) bRefresh.setEnabled(true);
    if (startDate != null) startDate.setEnabled(true);
    if (endDate != null) endDate.setEnabled(true);
    if (cbUpdateDuplicates != null) cbUpdateDuplicates.setEnabled(true);
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

    if (tradeLogMultiSelection && currentFiles != null && !currentFiles.isEmpty()) {
      int n = currentFiles.size();
      lSelectedFile.setText("Vybráno souborů: " + n);
      StringBuilder tip = new StringBuilder();
      for (int i = 0; i < currentFiles.size(); i++) {
        if (i > 0) tip.append("\n");
        tip.append(currentFiles.get(i).getName());
      }
      lSelectedFile.setToolTipText(tip.toString());
      return;
    }

    lSelectedFile.setToolTipText(null);
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

    // IB TradeLog: allow selecting any files and validate by header (not by extension).
    if (formatIndex == 3) {
      java.util.List<java.io.File> selected = chooseFilesForOpen("Importovat soubor", null, true);
      if (selected == null || selected.isEmpty()) return;

      java.util.List<java.io.File> valid = new java.util.ArrayList<>();
      java.util.List<String> invalid = new java.util.ArrayList<>();

      for (java.io.File f : selected) {
        String err = validateIbTradeLogHeader(f);
        if (err != null) {
          invalid.add(f.getName() + ": " + err);
          continue;
        }
        valid.add(f);
      }

      if (!invalid.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        msg.append("Některé soubory byly přeskočeny (nejsou ve formátu IB TradeLog):\n\n");
        for (int i = 0; i < invalid.size(); i++) {
          msg.append("- ").append(invalid.get(i)).append("\n");
        }
        JOptionPane.showMessageDialog(this, msg.toString(), "Neplatný soubor", JOptionPane.WARNING_MESSAGE);
      }

      if (valid.isEmpty()) {
        JOptionPane.showMessageDialog(this,
            "Nebyl vybrán žádný platný IB TradeLog soubor (očekávám hlavičku ACCOUNT_INFORMATION).",
            "Neplatný výběr", JOptionPane.ERROR_MESSAGE);
        return;
      }

      // Archive selected local files into unified cache for reproducibility.
      java.util.List<java.io.File> cachedFiles = new java.util.ArrayList<>();
      for (java.io.File f : valid) {
        try {
          java.nio.file.Path cached = CacheManager.archiveFile("ib", CacheManager.Source.FILE,
              "ib_tradelog_" + f.getName(), f.toPath());
          cachedFiles.add(cached.toFile());
        } catch (Exception e) {
          // Best effort: fallback to original file
          cachedFiles.add(f);
        }
      }

      currentFiles = cachedFiles;
      tradeLogMultiSelection = true;
      currentFile = currentFiles.get(0);
      updateSelectedFileLabel();
      loadImport();
      return;
    }

    String[] exts = getFileExtensionsForFormat(formatIndex);
    java.io.File selected = chooseFileForOpen("Importovat soubor", exts);
    if (selected == null) return;

    currentFiles = null;
    tradeLogMultiSelection = false;
    currentFile = selected;

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

  private String validateIbTradeLogHeader(java.io.File file) {
    if (file == null) return "chybí soubor";
    try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(file))) {
      String l1 = readFirstNonEmptyLine(r);
      if (l1 == null) return "prázdný soubor";
      if (!"ACCOUNT_INFORMATION".equals(l1.trim())) {
        return "chybí hlavička ACCOUNT_INFORMATION";
      }
      String l2 = readFirstNonEmptyLine(r);
      if (l2 == null) return "chybí řádek ACT_INF";
      if (!l2.trim().startsWith("ACT_INF|")) {
        return "chybí řádek ACT_INF|...";
      }
      return null;
    } catch (Exception e) {
      return "nelze číst soubor";
    }
  }

  private static String readFirstNonEmptyLine(java.io.BufferedReader r) throws java.io.IOException {
    String s;
    while ((s = r.readLine()) != null) {
      if (s.trim().isEmpty()) continue;
      return s;
    }
    return null;
  }

  private void importIbTradeLogFilesIntoPreview(java.util.List<java.io.File> files, java.util.Date startD,
      java.util.Date endD, java.util.Vector<String[]> notImported) throws cz.datesoft.stockAccounting.imp.ImportException, java.io.IOException {
    System.out.println("[IMPORT:IBTLG] Importing multiple TradeLog files: " + (files != null ? files.size() : 0));

    // Combine transactions and keep origin info for notImported
    cz.datesoft.stockAccounting.imp.ImportIBTradeLog importer = new cz.datesoft.stockAccounting.imp.ImportIBTradeLog();
    java.util.Vector<Transaction> all = new java.util.Vector<>();
    java.util.Vector<String[]> allNotImported = new java.util.Vector<>();

    if (files != null) {
      for (java.io.File f : files) {
        if (f == null) continue;
        String err = validateIbTradeLogHeader(f);
        if (err != null) {
          // Should not happen (validated earlier), but be defensive.
          continue;
        }
        java.util.Vector<String[]> perFileNotImported = new java.util.Vector<>();
        java.util.Vector<Transaction> txs = importer.doImport(f, startD, endD, perFileNotImported);
        all.addAll(txs);

        if (perFileNotImported != null && !perFileNotImported.isEmpty()) {
          for (String[] row : perFileNotImported) {
            String[] withFile = new String[(row != null ? row.length : 0) + 1];
            withFile[0] = f.getName();
            if (row != null && row.length > 0) {
              System.arraycopy(row, 0, withFile, 1, row.length);
            }
            allNotImported.add(withFile);
          }
        }
      }
    }

    // De-duplicate within the selected files (avoid overlap when user selects e.g. monthly exports twice).
    TransactionSet tmp = new TransactionSet();
    java.util.Vector<Transaction> unique = new java.util.Vector<>();
    for (Transaction candidate : all) {
      if (candidate == null) continue;
      if (!tmp.isDuplicate(candidate)) {
        tmp.rows.add(candidate);
        unique.add(candidate);
      }
    }

    // Update notImported output with our combined list.
    if (notImported != null) {
      notImported.clear();
      notImported.addAll(allNotImported);
    }

    // Assign serials and replace preview rows
    transactions.rows.clear();
    for (Transaction tx : unique) {
      tx.setSerial(transactions.serialCounter++);
      transactions.rows.add(tx);
    }
    transactions.sort();
    transactions.modified = false;
  }

  private java.io.File chooseFileForOpen(String title, String[] dotExtensions) {
    // Prefer native OS chooser by default, configurable in Settings.
    if (cz.datesoft.stockAccounting.Settings.getFileChooserMode() == cz.datesoft.stockAccounting.Settings.FILE_CHOOSER_SWING) {
      javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
      chooser.setDialogTitle(title);
      chooser.setAcceptAllFileFilterUsed(true);
      String lastDir = Settings.getImportDirectory();
      if (lastDir != null && !lastDir.trim().isEmpty()) {
        chooser.setCurrentDirectory(new java.io.File(lastDir));
      }
      if (dotExtensions != null && dotExtensions.length > 0) {
        java.util.ArrayList<String> exts = new java.util.ArrayList<>();
        for (String e : dotExtensions) {
          if (e == null) continue;
          String s = e.trim();
          if (s.startsWith(".")) s = s.substring(1);
          if (!s.isEmpty()) exts.add(s);
        }
        if (!exts.isEmpty()) {
          chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Soubory importu", exts.toArray(new String[0])));
        }
      }
      int r = chooser.showOpenDialog(this);
      if (r != javax.swing.JFileChooser.APPROVE_OPTION) return null;
      java.io.File f = chooser.getSelectedFile();
      if (f != null) {
        java.io.File dir = f.getParentFile();
        if (dir != null) {
          Settings.setImportDirectory(dir.getAbsolutePath());
          Settings.save();
        }
      }
      return f;
    }

    java.awt.FileDialog dialog = new java.awt.FileDialog(this, title, java.awt.FileDialog.LOAD);

    String loc = Settings.getImportDirectory();
    if (loc != null) {
      dialog.setDirectory(loc);
    }

    // For native dialogs we deliberately avoid suffix filtering: users can switch file visibility in the OS UI.
    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName == null) {
      return null;
    }

    java.io.File f = new java.io.File(dialog.getDirectory(), fileName);
    Settings.setImportDirectory(dialog.getDirectory());
    Settings.save();
    return f;
  }

  private java.util.List<java.io.File> chooseFilesForOpen(String title, String[] dotExtensions, boolean allowMultiple) {
    if (!allowMultiple) {
      java.io.File f = chooseFileForOpen(title, dotExtensions);
      if (f == null) return null;
      java.util.List<java.io.File> res = new java.util.ArrayList<>();
      res.add(f);
      return res;
    }

    if (cz.datesoft.stockAccounting.Settings.getFileChooserMode() == cz.datesoft.stockAccounting.Settings.FILE_CHOOSER_SWING) {
      javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
      chooser.setDialogTitle(title);
      chooser.setAcceptAllFileFilterUsed(true);
      chooser.setMultiSelectionEnabled(true);
      String lastDir = Settings.getImportDirectory();
      if (lastDir != null && !lastDir.trim().isEmpty()) {
        chooser.setCurrentDirectory(new java.io.File(lastDir));
      }
      if (dotExtensions != null && dotExtensions.length > 0) {
        java.util.ArrayList<String> exts = new java.util.ArrayList<>();
        for (String e : dotExtensions) {
          if (e == null) continue;
          String s = e.trim();
          if (s.startsWith(".")) s = s.substring(1);
          if (!s.isEmpty()) exts.add(s);
        }
        if (!exts.isEmpty()) {
          chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Soubory importu", exts.toArray(new String[0])));
        }
      }
      int r = chooser.showOpenDialog(this);
      if (r != javax.swing.JFileChooser.APPROVE_OPTION) return null;
      java.io.File[] files = chooser.getSelectedFiles();
      if (files == null || files.length == 0) return null;
      java.io.File dir = files[0].getParentFile();
      if (dir != null) {
        Settings.setImportDirectory(dir.getAbsolutePath());
        Settings.save();
      }
      java.util.List<java.io.File> res = new java.util.ArrayList<>();
      for (java.io.File f : files) {
        if (f != null) res.add(f);
      }
      return res;
    }

    java.awt.FileDialog dialog = new java.awt.FileDialog(this, title, java.awt.FileDialog.LOAD);
    dialog.setMultipleMode(true);
    String loc = Settings.getImportDirectory();
    if (loc != null) {
      dialog.setDirectory(loc);
    }
    dialog.setVisible(true);
    java.io.File[] files = dialog.getFiles();
    if (files == null || files.length == 0) return null;
    Settings.setImportDirectory(dialog.getDirectory());
    Settings.save();
    java.util.List<java.io.File> res = new java.util.ArrayList<>();
    for (java.io.File f : files) {
      if (f != null) res.add(f);
    }
    return res;
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
      bIBKRFlexRefreshPreview.setText("🟢 Obnovit náhled");
    }
    if (statusMessage != null && lblIBKRFlexStatus != null) {
      lblIBKRFlexStatus.setText(statusMessage);
    }
  }

  private void clearIbkrCachedData() {
    lastIbkrCsvContent = null;
    lastIbkrSourceLabel = null;
    ibkrPreviewDirty = false;
    if (lblIbkrFlexCsvInfo != null) {
      lblIbkrFlexCsvInfo.setText("");
      lblIbkrFlexCsvInfo.setToolTipText(null);
    }
    if (lblIbkrFlexCsvSpacer != null) {
      lblIbkrFlexCsvSpacer.setText("");
    }
    if (bIbkrFlexCsvDetails != null) {
      bIbkrFlexCsvDetails.setEnabled(false);
    }
    lastIbkrFlexStructureWarnKey = null;
    if (bIBKRFlexRefreshPreview != null) {
      bIBKRFlexRefreshPreview.setEnabled(false);
      bIBKRFlexRefreshPreview.setText("Obnovit náhled");
    }
  }

  private void updateIbkrFlexCsvInfoLabel() {
    if (lblIbkrFlexCsvInfo == null) return;
    if (!isIBKRFlexFormat()) {
      lblIbkrFlexCsvInfo.setText("");
      if (bIbkrFlexCsvDetails != null) {
        bIbkrFlexCsvDetails.setEnabled(false);
      }
      return;
    }
    if (lastIBKRParser == null) {
      lblIbkrFlexCsvInfo.setText("");
      lblIbkrFlexCsvInfo.setToolTipText(null);
      if (bIbkrFlexCsvDetails != null) {
        bIbkrFlexCsvDetails.setEnabled(false);
      }
      return;
    }
    IBKRFlexParser.FlexCsvVersion v = lastIBKRParser.getFlexCsvVersion();
    StringBuilder sb = new StringBuilder();
    if (v == IBKRFlexParser.FlexCsvVersion.V2_HEADERS_AND_TRAILERS) {
      sb.append("Flex csv file - version with headers and trailers");
      // Details are shown in a dedicated dialog.
      lblIbkrFlexCsvInfo.setToolTipText(null);
      if (bIbkrFlexCsvDetails != null) {
        bIbkrFlexCsvDetails.setEnabled(true);
      }
    } else if (v == IBKRFlexParser.FlexCsvVersion.V1_LEGACY) {
      sb.append("");
      lblIbkrFlexCsvInfo.setToolTipText(null);
      if (bIbkrFlexCsvDetails != null) {
        bIbkrFlexCsvDetails.setEnabled(false);
      }
    } else {
      sb.append("");
      lblIbkrFlexCsvInfo.setToolTipText(null);
      if (bIbkrFlexCsvDetails != null) {
        bIbkrFlexCsvDetails.setEnabled(false);
      }
    }
    lblIbkrFlexCsvInfo.setText(sb.toString());
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
      parser.setIncludeTrades(mode != IBKR_MODE_TRANS_ONLY && mode != IBKR_MODE_DIVI_ONLY);
      if (mode == IBKR_MODE_TRADES_ONLY) {
        parser.setIncludeCorporateActions(false);
      } else if (mode == IBKR_MODE_TRANS_ONLY) {
        parser.setIncludeCorporateActions(true);
      } else if (mode == IBKR_MODE_DIVI_ONLY) {
        parser.setIncludeCorporateActions(false);
      }
      Vector<Transaction> parsedTransactions = parser.parseCsvReport(lastIbkrCsvContent);
      lastIBKRParser = parser;
      updateIbkrFlexCsvInfoLabel();

      // Warn if mandatory v2 sections are missing per account (Trades + Corporate Actions)
      if (parser.getFlexCsvVersion() == IBKRFlexParser.FlexCsvVersion.V2_HEADERS_AND_TRAILERS) {
        java.util.Map<String, java.util.List<String>> missingByAcc = parser.getMissingMandatoryV2SectionsByAccount();
        if (missingByAcc != null && !missingByAcc.isEmpty()) {
          String key = (lastIbkrSourceLabel != null ? lastIbkrSourceLabel : "") + "|" + missingByAcc.toString();
          if (lastIbkrFlexStructureWarnKey == null || !lastIbkrFlexStructureWarnKey.equals(key)) {
            lastIbkrFlexStructureWarnKey = key;
            StringBuilder msg = new StringBuilder();
            msg.append("IBKR Flex CSV (v2): chybí povinné sekce v některých účtech:\n");
            for (java.util.Map.Entry<String, java.util.List<String>> e : missingByAcc.entrySet()) {
              String acc = e.getKey() != null ? e.getKey().trim() : "";
              if (acc.isEmpty()) acc = "(unknown)";
              msg.append("- ").append(acc).append(": ").append(String.join(", ", e.getValue())).append("\n");
            }
            msg.append("\nZkontrolujte nastavení Flex Query šablony v IBKR.");
            javax.swing.JOptionPane.showMessageDialog(this,
                msg.toString(),
                "IBKR Flex", javax.swing.JOptionPane.WARNING_MESSAGE);
          }
        }
      }

      clearPreview();

      // Enforce minute-level uniqueness rules for transformations.
      // If a transformation pair exists at a minute, it must be the only content of that minute.
      // Any colliding trades/other rows are shifted forward by +N minutes.
      normalizeIbkrMinuteCollisions(mainWindow.getTransactionDatabase(), parsedTransactions);

      // Disambiguate rare collisions caused by minute-level timestamp precision.
      // This keeps IBKR Flex re-import stable: update one existing row, insert the other(s).
      disambiguateIbkrDuplicateCollisions(mainWindow.getTransactionDatabase(), parsedTransactions);

      Vector<Transaction> filteredTransactions = mainWindow.getTransactionDatabase().filterDuplicates(parsedTransactions);
      int duplicatesFiltered = parsedTransactions.size() - filteredTransactions.size();

      duplicatesToUpdate.clear();
      // IBKR Flex: always treat duplicates as "to update" (TxnID-based re-import).
      if (duplicatesFiltered > 0) {
        for (Transaction candidate : parsedTransactions) {
          if (!filteredTransactions.contains(candidate)) {
            duplicatesToUpdate.add(candidate);
          }
        }
      }

      // Populate new preview UI elements (summary + side-by-side update table)
      previewUpdatePairs = new java.util.ArrayList<>();
      if (!duplicatesToUpdate.isEmpty()) {
        for (Transaction incoming : duplicatesToUpdate) {
          if (incoming == null) continue;
          Transaction existing = mainWindow.getTransactionDatabase().findDuplicateTransaction(incoming);
          String match = computeMatchKind(existing, incoming);
          previewUpdatePairs.add(new UpdatePair(existing, incoming, match));
        }
      }

      transactions.rows.addAll(filteredTransactions);
      transactions.fireTableDataChanged();

      String previewText = "Náhled (" + filteredTransactions.size() + " záznamů)";
      if (duplicatesFiltered > 0) {
        previewText += " - " + duplicatesFiltered + " duplikátů k aktualizaci";
      }
      previewText += ":";
      lPreview.setText(previewText);

      if (lSummary != null) {
        int newCnt = filteredTransactions.size();
        int updCnt = duplicatesToUpdate.size();
        int ni = 0;
        lSummary.setText("Nové: " + newCnt + " | K aktualizaci: " + updCnt + " | Neimportované: " + ni);
      }

      updateUpdatePreviewSection();

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
        bIBKRFlexRefreshPreview.setText("Obnovit náhled");
      }

    } catch (Exception e) {
      System.err.println("[IBKR:REFRESH:ERROR] Failed to refresh preview: " + e.getMessage());
      e.printStackTrace();
      if (lblIBKRFlexStatus != null) {
        lblIBKRFlexStatus.setText("Chyba při obnovení náhledu: " + e.getMessage());
      }
    }
  }

  private void refreshIbkrPreviewFromCachedCsvAsync() {
    if (!isIBKRFlexFormat()) return;
    if (lastIbkrCsvContent == null) {
      if (lblIBKRFlexStatus != null) {
        lblIBKRFlexStatus.setText("Nejprve načtěte data (API nebo soubor)");
      }
      return;
    }
    if (previewLoadInProgress) {
      return;
    }

    showBusy("Obnovuji náhled IBKR Flex…");
    if (bIBKRFlexFile != null) bIBKRFlexFile.setEnabled(false);
    if (bIBKRFlexRefreshPreview != null) bIBKRFlexRefreshPreview.setEnabled(false);
    if (bIBKRFlexClear != null) bIBKRFlexClear.setEnabled(false);

    javax.swing.SwingWorker<Void, Void> w = new javax.swing.SwingWorker<>() {
      @Override
      protected Void doInBackground() {
        refreshIbkrPreviewFromCachedCsv();
        return null;
      }

      @Override
      protected void done() {
        try {
          get();
        } catch (Exception e) {
          // refreshIbkrPreviewFromCachedCsv already set status; keep this minimal
        } finally {
          hideBusy();
          if (bIBKRFlexFile != null) bIBKRFlexFile.setEnabled(true);
          if (bIBKRFlexClear != null) bIBKRFlexClear.setEnabled(lastIbkrCsvContent != null);
          updateIBKRFlexButtonState();
        }
      }
    };
    w.execute();
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

      // Keep base as-is (updates existing). Shift the rest by +1m, +2m, ...
      for (int i = 0; i < toShift.size(); i++) {
        shiftTransactionByMinutes(toShift.get(i), i + 1);
      }
    }
  }

  /**
   * StockAccounting stores timestamps only to minute precision (seconds are effectively ignored).
   *
   * For correctness of transformation pairing, we enforce:
   * - At most one transformation pair can exist within a given minute.
   * - If a transformation (TRANS_SUB/TRANS_ADD) exists at a minute, no other transaction may share that minute.
   * - If there is a collision, shift the other transaction(s) forward by +N minutes.
   *
   * This is applied to IBKR Flex import candidates before duplicate filtering/merge.
   */
  private void normalizeIbkrMinuteCollisions(TransactionSet db, Vector<Transaction> candidates) {
    if (candidates == null || candidates.isEmpty()) return;

    // Occupied minutes from DB.
    java.util.Set<Long> occupied = new java.util.HashSet<>();
    if (db != null) {
      // TransactionSet.getRowCount() includes an extra empty row at the end.
      int max = Math.max(0, db.getRowCount() - 1);
      for (int i = 0; i < max; i++) {
        Transaction t = db.getRowAt(i);
        if (t == null) continue;
        occupied.add(minuteKey(t.getDate()));
      }
    }

    // Group candidates by minute.
    java.util.Map<Long, java.util.List<Transaction>> byMinute = new java.util.TreeMap<>();
    for (Transaction t : candidates) {
      if (t == null) continue;
      if (!"IB".equalsIgnoreCase(t.getBroker())) continue;
      byMinute.computeIfAbsent(minuteKey(t.getDate()), k -> new java.util.ArrayList<>()).add(t);
    }

    // Stable ordering so shifts are deterministic.
    java.util.Comparator<Transaction> stableOrder = java.util.Comparator
        .comparing((Transaction t) -> isTransformation(t) ? 0 : 1)
        .thenComparing((Transaction t) -> t.getDirection() == Transaction.DIRECTION_TRANS_SUB ? 0
            : (t.getDirection() == Transaction.DIRECTION_TRANS_ADD ? 1 : 2))
        .thenComparing((Transaction t) -> nullToEmpty(t.getTicker()), String.CASE_INSENSITIVE_ORDER)
        .thenComparing((Transaction t) -> nullToEmpty(t.getTxnId()))
        .thenComparing((Transaction t) -> t.getExecutionDate(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
        .thenComparing((Transaction t) -> Math.abs(t.getAmount() == null ? 0.0 : t.getAmount()));

    java.util.Set<Long> reservedTransMinutes = new java.util.HashSet<>();
    for (java.util.Map.Entry<Long, java.util.List<Transaction>> e : byMinute.entrySet()) {
      java.util.List<Transaction> group = e.getValue();
      if (group == null || group.isEmpty()) continue;

      group.sort(stableOrder);
      boolean hasTrans = group.stream().anyMatch(ImportWindow::isTransformation);
      if (hasTrans) {
        reservedTransMinutes.add(e.getKey());
      }
    }

    // Process minutes in order, place transactions while maintaining occupied minutes.
    for (java.util.Map.Entry<Long, java.util.List<Transaction>> e : byMinute.entrySet()) {
      long minute = e.getKey();
      java.util.List<Transaction> group = e.getValue();
      if (group == null || group.isEmpty()) continue;

      group.sort(stableOrder);
      boolean hasTrans = group.stream().anyMatch(ImportWindow::isTransformation);
      if (hasTrans) {
        // Transformations must occupy an exclusive minute. If the original minute is already
        // occupied (e.g., by existing DB rows), shift the whole pair forward by +N minutes.
        long targetMinute = minute;
        int shiftMinutes = 0;
        while (true) {
          boolean collides = occupied.contains(targetMinute);
          // Avoid landing on another transformation minute (except the original one).
          if (shiftMinutes > 0 && reservedTransMinutes.contains(targetMinute)) {
            collides = true;
          }
          if (!collides) break;
          java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
          cal.setTimeInMillis(targetMinute);
          cal.add(java.util.GregorianCalendar.MINUTE, 1);
          targetMinute = cal.getTimeInMillis();
          shiftMinutes++;
          if (shiftMinutes > 24 * 60) break;
        }

        for (Transaction t : group) {
          if (t == null) continue;
          if (!isTransformation(t)) continue;
          alignToMinute(t, targetMinute);
          if (shiftMinutes > 0) {
            appendTimeShiftMinutesMarker(t, shiftMinutes);
          }
        }
        occupied.add(targetMinute);

        // Shift everything else away from this minute.
        for (Transaction t : group) {
          if (t == null) continue;
          if (isTransformation(t)) continue;
          // Trades/other rows must be after the transformation minute.
          shiftForwardToFreeMinute(t, occupied, reservedTransMinutes, targetMinute);
        }
      } else {
        // No transformations: ensure no collisions with already occupied minutes.
        for (Transaction t : group) {
          if (t == null) continue;
          long mk = minuteKey(t.getDate());
          if (!occupied.contains(mk) && !reservedTransMinutes.contains(mk)) {
            occupied.add(mk);
            continue;
          }
          shiftForwardToFreeMinute(t, occupied, reservedTransMinutes, mk);
        }
      }
    }
  }

  private static boolean isTransformation(Transaction t) {
    if (t == null) return false;
    int d = t.getDirection();
    return d == Transaction.DIRECTION_TRANS_ADD || d == Transaction.DIRECTION_TRANS_SUB;
  }

  private static long minuteKey(java.util.Date d) {
    if (d == null) return Long.MIN_VALUE;
    java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
    cal.setTime(d);
    cal.set(java.util.GregorianCalendar.SECOND, 0);
    cal.set(java.util.GregorianCalendar.MILLISECOND, 0);
    return cal.getTimeInMillis();
  }

  private static boolean datesEqualMinute(java.util.Date d1, java.util.Date d2) {
    if (d1 == null && d2 == null) return true;
    if (d1 == null || d2 == null) return false;
    return minuteKey(d1) == minuteKey(d2);
  }

  private static void alignToMinute(Transaction t, long minuteKeyMillis) {
    if (t == null) return;
    java.util.Date oldDate = t.getDate();
    java.util.Date oldEx = t.getExecutionDate();

    java.util.Date newDate = new java.util.Date(minuteKeyMillis);
    t.setDate(newDate);

    // Do not overwrite settlement date for trades.
    // Only shift executionDate when it was effectively the same as trade timestamp (transformations / legacy rows).
    if (oldEx == null) {
      // Preserve unknown settlement date. For transformations, keep execution date aligned with trade timestamp.
      if (isTransformation(t)) {
        t.setExecutionDate(newDate);
      }
    } else if (oldDate != null && datesEqualMinute(oldEx, oldDate)) {
      t.setExecutionDate(newDate);
    }
  }

  private void shiftForwardToFreeMinute(Transaction tx, java.util.Set<Long> occupied, java.util.Set<Long> reservedTransMinutes,
      long baseMinuteKeyMillis) {
    if (tx == null) return;

    java.util.Date d = tx.getDate();
    if (d == null) return;
    java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
    cal.setTime(d);
    cal.set(java.util.GregorianCalendar.SECOND, 0);
    cal.set(java.util.GregorianCalendar.MILLISECOND, 0);

    // Ensure we only move forward, and also respect base minute (e.g. transformation minute).
    if (baseMinuteKeyMillis != Long.MIN_VALUE && cal.getTimeInMillis() < baseMinuteKeyMillis) {
      cal.setTimeInMillis(baseMinuteKeyMillis);
    }

    long start = cal.getTimeInMillis();
    int minutes = 0;
    while (true) {
      long mk = cal.getTimeInMillis();
      if (!occupied.contains(mk) && !reservedTransMinutes.contains(mk)) {
        break;
      }
      cal.add(java.util.GregorianCalendar.MINUTE, 1);
      minutes++;
      // Safety: avoid infinite loops in pathological datasets.
      if (minutes > 24 * 60) {
        break;
      }
    }

    long target = cal.getTimeInMillis();
    if (target != start) {
      java.util.Date oldDate = tx.getDate();
      java.util.Date oldEx = tx.getExecutionDate();

      java.util.Date newDate = cal.getTime();
      tx.setDate(newDate);
      if (oldEx != null && oldDate != null && datesEqualMinute(oldEx, oldDate)) {
        tx.setExecutionDate(newDate);
      }
      appendTimeShiftMinutesMarker(tx, minutes);
    }

    occupied.add(cal.getTimeInMillis());
  }

  private void appendTimeShiftMinutesMarker(Transaction tx, int minutes) {
    if (tx == null) return;
    if (minutes <= 0) return;
    String note = tx.getNote();
    String marker = "|TimeShift:+" + minutes + "m";
    if (note == null || note.isEmpty()) {
      tx.setNote(marker.substring(1));
    } else if (!note.contains(marker)) {
      tx.setNote(note + marker);
    }
  }

  private void shiftTransactionByMinutes(Transaction tx, int minutes) {
    if (tx == null) return;
    if (minutes <= 0) return;

    java.util.Date d = tx.getDate();
    java.util.Date oldEx = tx.getExecutionDate();
    java.util.Date oldDate = d;

    if (d != null) {
      java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
      cal.setTime(d);
      cal.set(java.util.GregorianCalendar.SECOND, 0);
      cal.set(java.util.GregorianCalendar.MILLISECOND, 0);
      cal.add(java.util.GregorianCalendar.MINUTE, minutes);
      tx.setDate(cal.getTime());
    }

    // Preserve settlement date for trades; only shift when executionDate equals trade timestamp.
    if (oldEx != null && oldDate != null && datesEqualMinute(oldEx, oldDate)) {
      tx.setExecutionDate(tx.getDate());
    }

    String note = tx.getNote();
    String marker = "|TimeShift:+" + minutes + "m";
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
    boolean diviOnly = (mode == IBKR_MODE_DIVI_ONLY);

    // Disable trade filters when importing only transformations
    if (bIBKRFlexAssetFilter != null) {
      bIBKRFlexAssetFilter.setEnabled(!transOnly && !diviOnly);
    }

    if (cbUpdateDuplicates != null) {
      cbUpdateDuplicates.setEnabled(!transOnly && !diviOnly);
    }

    boolean enableCaTypes = (cbIBKRFlexIncludeCorporateActions != null && cbIBKRFlexIncludeCorporateActions.isSelected());
    if (mode == IBKR_MODE_TRADES_ONLY || diviOnly) {
      enableCaTypes = false;
    }
    if (cbIBKRFlexCaRS != null) cbIBKRFlexCaRS.setEnabled(enableCaTypes);
    if (cbIBKRFlexCaTC != null) cbIBKRFlexCaTC.setEnabled(enableCaTypes);
    if (cbIBKRFlexCaIC != null) cbIBKRFlexCaIC.setEnabled(enableCaTypes);
    if (cbIBKRFlexCaTO != null) cbIBKRFlexCaTO.setEnabled(enableCaTypes);

    // Hide/disable transformations include checkbox when it does not apply
    if (cbIBKRFlexIncludeCorporateActions != null) {
      cbIBKRFlexIncludeCorporateActions.setEnabled(!diviOnly);
    }
  }

  private void setIbkrFlexUiVisible(boolean visible) {
    if (pIBKRFlexButtons != null) pIBKRFlexButtons.setVisible(visible);
    if (lblIBKRFlexStatus != null) lblIBKRFlexStatus.setVisible(visible);
    if (lblIbkrFlexCsvInfo != null) lblIbkrFlexCsvInfo.setVisible(visible);
    if (lblIbkrFlexCsvSpacer != null) lblIbkrFlexCsvSpacer.setVisible(visible);
    if (bIbkrFlexCsvDetails != null) bIbkrFlexCsvDetails.setVisible(visible);
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
    // Avoid blocking EDT; if a preview load is already running, coalesce requests.
    if (previewLoadInProgress) {
      previewReloadRequested = true;
      return;
    }

    // Only run async for file-based formats (API formats manage their own async flows).
    int formatIdx = cbFormat != null ? cbFormat.getSelectedIndex() : 0;
    if (formatIdx != 8 && formatIdx != 9) {
      // Do not show busy overlay unless there is an actual file selection.
      if (formatIdx == 0) {
        // <vyberte formát>
        return;
      }

      boolean hasMultiFiles = (formatIdx == 3 && tradeLogMultiSelection && currentFiles != null && !currentFiles.isEmpty());
      if (!hasMultiFiles && currentFile == null) {
        updateSelectedFileLabel();
        return;
      }

      loadImportAsync();
      return;
    }

    // Legacy synchronous path below is kept for API formats only.

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

        if (formatIndex == 3 && tradeLogMultiSelection && currentFiles != null && !currentFiles.isEmpty()) {
          importIbTradeLogFilesIntoPreview(currentFiles, startD, endD, notImported);
        } else {
          System.out.println("[FORMAT:004] About to call importFile with formatIndex=" + formatIndex + " (programmatic: " + currentImportFormat + ", UI: " + cbFormat.getSelectedIndex() + ") for file: " + currentFile.getName());
          transactions.importFile(currentFile, startD, endD, formatIndex, notImported);
        }

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
          String header = "Col " + n;
          if (tradeLogMultiSelection && colCount > 0 && n == 0) {
            header = "Soubor";
          }
          niTable.getColumnModel().getColumn(n).setHeaderValue(header);
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
       UiDialogs.error(this, "Chyba čtení: " + e.getLocalizedMessage(), "Chyba", e);
       currentImportFormat = 0; // Clear programmatic override on error
     } catch (cz.datesoft.stockAccounting.imp.ImportException e) {
       System.out.println("[IMPORT:ERROR] ImportException during import: " + e.getMessage());
       System.out.println("[IMPORT:ERROR] UI state at time of error:");
       logUIComponentStates();
       UiDialogs.error(this, "Chyba při importu: " + e.getMessage(), "Chyba", e);
       currentImportFormat = 0; // Clear programmatic override on error
     }
  }

  private void loadImportAsync() {
    // Snapshot state so we can ignore outdated results
    final int formatIndex = (currentImportFormat > 0) ? currentImportFormat : (cbFormat != null ? cbFormat.getSelectedIndex() : 0);
    final java.io.File file = currentFile;
    final java.util.List<java.io.File> files = (tradeLogMultiSelection && currentFiles != null)
        ? new java.util.ArrayList<>(currentFiles)
        : null;
    final boolean multi = (formatIndex == 3 && tradeLogMultiSelection && files != null && !files.isEmpty());
    final boolean updateDups = cbUpdateDuplicates != null && cbUpdateDuplicates.isSelected();

    // Snapshot dates on EDT (Swing components are not thread-safe)
    final java.util.Date startD = normalizeStartDate(startDate != null ? startDate.getDate() : null);
    final java.util.Date endD = normalizeEndDate(endDate != null ? endDate.getDate() : null);

    String msg = "Načítám…";
    if (multi) {
      msg = "Načítám " + files.size() + " souborů…";
    } else if (file != null) {
      msg = "Načítám " + file.getName() + "…";
    }
    showBusy(msg);

    final javax.swing.SwingWorker<LoadResult, Void> worker = new javax.swing.SwingWorker<LoadResult, Void>() {
      @Override
      protected LoadResult doInBackground() throws Exception {
        return loadImportCompute(formatIndex, file, files, multi, updateDups, startD, endD);
      }

      @Override
      protected void done() {
        try {
          LoadResult r = get();
          applyLoadResult(r);
        } catch (Exception e) {
          UiDialogs.error(ImportWindow.this, "Chyba při importu: " + e.getMessage(), "Chyba", e);
        } finally {
          hideBusy();
          if (previewReloadRequested) {
            previewReloadRequested = false;
            loadImport();
          }
        }
      }
    };

    worker.execute();
  }

  private static java.util.Date normalizeStartDate(java.util.Date startD) {
    if (startD == null) return null;
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(startD);
    cal.set(GregorianCalendar.HOUR_OF_DAY, 0);
    cal.set(GregorianCalendar.MINUTE, 0);
    cal.set(GregorianCalendar.SECOND, 0);
    cal.set(GregorianCalendar.MILLISECOND, 0);
    return cal.getTime();
  }

  private static java.util.Date normalizeEndDate(java.util.Date endD) {
    if (endD == null) return null;
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(endD);
    cal.set(GregorianCalendar.HOUR_OF_DAY, 23);
    cal.set(GregorianCalendar.MINUTE, 59);
    cal.set(GregorianCalendar.SECOND, 59);
    cal.set(GregorianCalendar.MILLISECOND, 999999);
    return cal.getTime();
  }

  private static final class LoadResult {
    final java.util.Vector<Transaction> preview;
    final java.util.Vector<String[]> notImported;
    final java.util.Vector<Transaction> duplicatesToUpdate;
    final java.util.List<UpdatePair> updatePairs;
    final int duplicatesFiltered;
    final int formatIndex;

    LoadResult(java.util.Vector<Transaction> preview, java.util.Vector<String[]> notImported,
        java.util.Vector<Transaction> duplicatesToUpdate, java.util.List<UpdatePair> updatePairs,
        int duplicatesFiltered, int formatIndex) {
      this.preview = preview;
      this.notImported = notImported;
      this.duplicatesToUpdate = duplicatesToUpdate;
      this.updatePairs = updatePairs;
      this.duplicatesFiltered = duplicatesFiltered;
      this.formatIndex = formatIndex;
    }
  }

  private static final class UpdatePair {
    final Transaction existing;
    final Transaction incoming;
    final String match;

    UpdatePair(Transaction existing, Transaction incoming, String match) {
      this.existing = existing;
      this.incoming = incoming;
      this.match = match;
    }
  }

  private LoadResult loadImportCompute(int formatIndex, java.io.File file, java.util.List<java.io.File> files,
      boolean multi, boolean updateDups, java.util.Date startD, java.util.Date endD) throws Exception {
    // Mimic the synchronous loadImport logic, but without touching Swing components.
    if (formatIndex == 0) {
      return new LoadResult(new java.util.Vector<>(), new java.util.Vector<>(), new java.util.Vector<>(),
          new java.util.ArrayList<>(), 0, formatIndex);
    }
    if (formatIndex == 8 || formatIndex == 9) {
      // API formats: preview is handled by dedicated flows
      return new LoadResult(new java.util.Vector<>(), new java.util.Vector<>(), new java.util.Vector<>(),
          new java.util.ArrayList<>(), 0, formatIndex);
    }
    if (file == null) {
      return new LoadResult(new java.util.Vector<>(), new java.util.Vector<>(), new java.util.Vector<>(),
          new java.util.ArrayList<>(), 0, formatIndex);
    }

    java.util.Vector<String[]> notImported = new java.util.Vector<>();

    // Build candidate txs
    TransactionSet tmpPreview = new TransactionSet();
    if (formatIndex == 3 && multi && files != null && !files.isEmpty()) {
      // Multi-file TradeLog
      java.util.Vector<Transaction> all = new java.util.Vector<>();
      java.util.Vector<String[]> allNotImported = new java.util.Vector<>();
      cz.datesoft.stockAccounting.imp.ImportIBTradeLog importer = new cz.datesoft.stockAccounting.imp.ImportIBTradeLog();
      for (java.io.File f : files) {
        if (f == null) continue;
        java.util.Vector<String[]> per = new java.util.Vector<>();
        java.util.Vector<Transaction> txs = importer.doImport(f, startD, endD, per);
        all.addAll(txs);
        for (String[] row : per) {
          String[] withFile = new String[(row != null ? row.length : 0) + 1];
          withFile[0] = f.getName();
          if (row != null && row.length > 0) {
            System.arraycopy(row, 0, withFile, 1, row.length);
          }
          allNotImported.add(withFile);
        }
      }
      notImported.addAll(allNotImported);

      // internal dedupe (TxnID-based): avoid collapsing distinct executions that share the same timestamp
      java.util.Set<String> seenTxnIds = new java.util.HashSet<>();
      java.util.Vector<Transaction> unique = new java.util.Vector<>();
      for (Transaction candidate : all) {
        if (candidate == null) continue;
        String txn = candidate.getTxnId();
        if (txn != null) txn = txn.trim();
        if (txn == null || txn.isEmpty()) {
          // Fallback to business-key-based dedupe for rows without TxnID
          if (!tmpPreview.isDuplicate(candidate)) {
            tmpPreview.rows.add(candidate);
            unique.add(candidate);
          }
          continue;
        }
        if (seenTxnIds.add(txn)) {
          unique.add(candidate);
        }
      }
      tmpPreview.rows.clear();
      tmpPreview.rows.addAll(unique);
    } else {
      // Single-file import: reuse existing importer path
      tmpPreview.importFile(file, startD, endD, formatIndex, notImported);
    }

    // Filter duplicates vs main DB (same logic as current loadImport)
    java.util.Vector<Transaction> original = new java.util.Vector<>(tmpPreview.rows);
    TransactionSet mainDb = mainWindow.getTransactionDatabase();
    java.util.Vector<Transaction> filtered = mainDb.filterDuplicates(original);

    java.util.Vector<Transaction> dupsToUpdate = new java.util.Vector<>();
    if (updateDups) {
      for (Transaction candidate : original) {
        if (!filtered.contains(candidate)) {
          dupsToUpdate.add(candidate);
        }
      }

      // IB TradeLog: If we can deterministically pair legacy rows (missing TxnID) by group-count equality,
      // treat them as "duplicates to update" rather than "new" inserts in preview.
      if (formatIndex == 3 && !filtered.isEmpty()) {
        java.util.Set<Transaction> backfillable = mainDb.getIbTradeLogLegacyBackfillableCandidates(filtered);
        if (backfillable != null && !backfillable.isEmpty()) {
          java.util.Vector<Transaction> newFiltered = new java.util.Vector<>();
          for (Transaction t : filtered) {
            if (backfillable.contains(t)) {
              dupsToUpdate.add(t);
            } else {
              newFiltered.add(t);
            }
          }
          filtered = newFiltered;
        }
      }
    }

    // Build update pairs for debug preview (existing vs incoming)
    java.util.List<UpdatePair> pairs = new java.util.ArrayList<>();
    if (updateDups && !dupsToUpdate.isEmpty()) {
      for (Transaction incoming : dupsToUpdate) {
        if (incoming == null) continue;
        Transaction existing = mainDb.findDuplicateTransaction(incoming);
        String match = computeMatchKind(existing, incoming);
        pairs.add(new UpdatePair(existing, incoming, match));
      }
    }

    // duplicatesFiltered counts all non-new items (both "update" and "filtered")
    int duplicatesFiltered = original.size() - filtered.size();
    return new LoadResult(filtered, notImported, dupsToUpdate, pairs, duplicatesFiltered, formatIndex);
  }

  private static String computeMatchKind(Transaction existing, Transaction incoming) {
    if (existing == null || incoming == null) return "?";
    String txE = existing.getTxnId();
    String txI = incoming.getTxnId();
    if (txE != null && txI != null && !txE.trim().isEmpty() && txE.trim().equalsIgnoreCase(txI.trim())) {
      return "TxnID";
    }
    // TradeLog legacy group pairing / minute match ends up here as non-TxnID.
    return "Key";
  }

  private void applyLoadResult(LoadResult r) {
    if (r == null) return;

    // Apply preview transactions
    transactions.rows.clear();
    for (Transaction tx : r.preview) {
      if (tx == null) continue;
      tx.setSerial(transactions.serialCounter++);
      transactions.rows.add(tx);
    }
    transactions.sort();
    transactions.fireTableDataChanged();

    // Apply duplicates-to-update list
    duplicatesToUpdate.clear();
    duplicatesToUpdate.addAll(r.duplicatesToUpdate);

    previewUpdatePairs = (r.updatePairs != null) ? r.updatePairs : new java.util.ArrayList<>();

    // Update labels
    int n = transactions.getRowCount();
    String previewText = "Náhled (" + n + " " + getRecordsWord(n) + ")";
    if (r.duplicatesFiltered > 0) {
      if (cbUpdateDuplicates != null && cbUpdateDuplicates.isSelected()) {
        previewText += " - " + r.duplicatesFiltered + " duplikátů k aktualizaci";
      } else {
        previewText += " - " + r.duplicatesFiltered + " duplikátů vyfiltrováno";
      }
    }
    previewText += ":";
    lPreview.setText(previewText);

    if (lSummary != null) {
      int up = duplicatesToUpdate.size();
      int ni = r.notImported != null ? r.notImported.size() : 0;
      lSummary.setText("Nové: " + n + " | K aktualizaci: " + up + " | Neimportované: " + ni);
    }

    updateUpdatePreviewSection();

    int rowCount = r.notImported != null ? r.notImported.size() : 0;
    lUnimported.setText("Neimportované řádky (" + rowCount + " " + getRecordsWord(rowCount) + "):");

    // Fill not-imported table
    DefaultTableModel niTableModel = (DefaultTableModel) niTable.getModel();
    niTableModel.setRowCount(0);
    if (rowCount > 0) {
      // Determine max columns
      int colCount = 0;
      for (int i = 0; i < rowCount; i++) {
        int len = r.notImported.get(i) != null ? r.notImported.get(i).length : 0;
        if (len > colCount) colCount = len;
      }
      niTableModel.setRowCount(rowCount);
      niTableModel.setColumnCount(colCount);

      for (int c = 0; c < colCount; c++) {
        String header = "Col " + c;
        if (tradeLogMultiSelection && colCount > 0 && c == 0) {
          header = "Soubor";
        }
        niTable.getColumnModel().getColumn(c).setHeaderValue(header);
      }

      for (int i = 0; i < rowCount; i++) {
        String[] a = r.notImported.get(i);
        if (a == null) continue;
        for (int c = 0; c < a.length; c++) {
          niTableModel.setValueAt(a[c], i, c);
        }
      }
    }
  }

  private void updateUpdatePreviewSection() {
    if (lToUpdate == null || updateScrollPane == null || updateTable == null) return;

    int cnt = previewUpdatePairs != null ? previewUpdatePairs.size() : 0;
    boolean show = cnt > 0;
    lToUpdate.setVisible(show);
    updateScrollPane.setVisible(show);
    if (!show) {
      return;
    }

    lToUpdate.setText("K aktualizaci (" + cnt + " " + getRecordsWord(cnt) + "):");
    updateTable.setModel(new UpdatePreviewTableModel(previewUpdatePairs));
    updateTable.repaint();
  }

  private static final class UpdatePreviewTableModel extends javax.swing.table.AbstractTableModel {
    private final java.util.List<UpdatePair> pairs;

    private static final String[] COLS = {
        "Match",
        "Datum (DB)", "Datum (Import)",
        "Vypořádání (DB)", "Vypořádání (Import)",
        "Typ (DB)", "Typ (Import)",
        "Směr (DB)", "Směr (Import)",
        "Ticker (DB)", "Ticker (Import)",
        "Množství (DB)", "Množství (Import)",
        "Kurs (DB)", "Kurs (Import)",
        "Měna (DB)", "Měna (Import)",
        "Poplatky (DB)", "Poplatky (Import)",
        "Měna popl. (DB)", "Měna popl. (Import)",
        "Trh (DB)", "Trh (Import)",
        "Broker (DB)", "Broker (Import)",
        "ID účtu (DB)", "ID účtu (Import)",
        "ID transakce (DB)", "ID transakce (Import)",
        "Poznámka (DB)", "Poznámka (Import)"
    };

    UpdatePreviewTableModel(java.util.List<UpdatePair> pairs) {
      this.pairs = pairs == null ? java.util.Collections.emptyList() : pairs;
    }

    public int getRowCount() {
      return pairs.size();
    }

    public int getColumnCount() {
      return COLS.length;
    }

    public String getColumnName(int column) {
      return COLS[column];
    }

    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == 1 || columnIndex == 2 || columnIndex == 3 || columnIndex == 4) {
        return java.util.Date.class;
      }
      if (columnIndex == 11 || columnIndex == 12 || columnIndex == 13 || columnIndex == 14 || columnIndex == 17 || columnIndex == 18) {
        return java.lang.Double.class;
      }
      return java.lang.String.class;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      UpdatePair p = pairs.get(rowIndex);
      Transaction ex = p.existing;
      Transaction in = p.incoming;
      switch (columnIndex) {
        case 0: return p.match;
        case 1: return ex != null ? ex.getDate() : null;
        case 2: return in != null ? in.getDate() : null;
        case 3: return ex != null ? ex.getExecutionDate() : null;
        case 4: return in != null ? in.getExecutionDate() : null;
        case 5: return ex != null ? ex.getStringType() : null;
        case 6: return in != null ? in.getStringType() : null;
        case 7: return ex != null ? ex.getStringDirection() : null;
        case 8: return in != null ? in.getStringDirection() : null;
        case 9: return ex != null ? ex.getTicker() : null;
        case 10: return in != null ? in.getTicker() : null;
        case 11: return ex != null ? ex.getAmount() : null;
        case 12: return in != null ? in.getAmount() : null;
        case 13: return ex != null ? ex.getPrice() : null;
        case 14: return in != null ? in.getPrice() : null;
        case 15: return ex != null ? ex.getPriceCurrency() : null;
        case 16: return in != null ? in.getPriceCurrency() : null;
        case 17: return ex != null ? ex.getFee() : null;
        case 18: return in != null ? in.getFee() : null;
        case 19: return ex != null ? ex.getFeeCurrency() : null;
        case 20: return in != null ? in.getFeeCurrency() : null;
        case 21: return ex != null ? ex.getMarket() : null;
        case 22: return in != null ? in.getMarket() : null;
        case 23: return ex != null ? ex.getBroker() : null;
        case 24: return in != null ? in.getBroker() : null;
        case 25: return ex != null ? ex.getAccountId() : null;
        case 26: return in != null ? in.getAccountId() : null;
        case 27: return ex != null ? ex.getTxnId() : null;
        case 28: return in != null ? in.getTxnId() : null;
        case 29: return ex != null ? ex.getNote() : null;
        case 30: return in != null ? in.getNote() : null;
        default: return null;
      }
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
    lblIbkrFlexCsvInfo = new javax.swing.JLabel();
    lblIbkrFlexCsvSpacer = new javax.swing.JLabel();
    bIbkrFlexCsvDetails = new javax.swing.JButton();
    bSelectFile = new javax.swing.JButton();
    lSelectedFile = new javax.swing.JLabel();
    bRefresh = new javax.swing.JButton();
    bImport = new javax.swing.JButton();
    jPanel2 = new javax.swing.JPanel();
    bCancel = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    table = new javax.swing.JTable();
    lPreview = new javax.swing.JLabel();
    lSummary = new javax.swing.JLabel();
    lToUpdate = new javax.swing.JLabel();
    updateScrollPane = new javax.swing.JScrollPane();
    updateTable = new javax.swing.JTable();
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

    lblIbkrFlexCsvInfo.setText("");
    lblIbkrFlexCsvInfo.setToolTipText(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 0.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    getContentPane().add(lblIbkrFlexCsvInfo, gridBagConstraints);

    lblIbkrFlexCsvSpacer.setText("");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    getContentPane().add(lblIbkrFlexCsvSpacer, gridBagConstraints);

    bIbkrFlexCsvDetails.setText("Detaily...");
    bIbkrFlexCsvDetails.setToolTipText("Zobrazit detaily sekcí v načteném IBKR Flex CSV");
    bIbkrFlexCsvDetails.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        showIbkrFlexCsvDetailsDialog();
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    getContentPane().add(bIbkrFlexCsvDetails, gridBagConstraints);

    bSelectFile.setText("Vybrat soubor...");
    bSelectFile.setToolTipText("Vybrat lokální soubor pro import (dle zvoleného formátu)");
    bSelectFile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        selectLocalImportFile();
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(bSelectFile, gridBagConstraints);


    lSelectedFile.setText("(soubor nevybrán)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
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
    gridBagConstraints.gridy = 8;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 2.0;
    getContentPane().add(jScrollPane1, gridBagConstraints);

    lPreview.setText("Náhled (0 záznamů):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    getContentPane().add(lPreview, gridBagConstraints);

    lSummary.setText("Nové: 0 | K aktualizaci: 0 | Neimportované: 0");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 7;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(2, 5, 5, 5);
    getContentPane().add(lSummary, gridBagConstraints);

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
    gridBagConstraints.gridy = 8;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    getContentPane().add(cbUpdateDuplicates, gridBagConstraints);

    lToUpdate.setText("K aktualizaci (0 záznamů):");
    lToUpdate.setVisible(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 9;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    getContentPane().add(lToUpdate, gridBagConstraints);

    updateTable.setEnabled(false);
    updateScrollPane.setViewportView(updateTable);
    updateScrollPane.setVisible(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 10;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    getContentPane().add(updateScrollPane, gridBagConstraints);

    lUnimported.setText("Neimportované řádky (0 záznamů):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 11;
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
    gridBagConstraints.gridy = 12;
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
        "Vše (obchody + transformace + dividendy)",
        "Pouze obchody",
        "Pouze transformace",
        "Pouze dividendy"
      });
      cbIBKRFlexImportMode.setToolTipText("Určuje, které typy dat se mají importovat (dividendy se načítají ze sekce CTRN, pokud je v CSV přítomná)");
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
      bIBKRFlexRefreshPreview.setText("Obnovit náhled");
      bIBKRFlexRefreshPreview.setToolTipText("Znovu vytvořit náhled z načtených dat (bez stahování z API)");
      bIBKRFlexRefreshPreview.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          refreshIbkrPreviewFromCachedCsvAsync();
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
          markIbkrPreviewDirty("Režim importu změněn – náhled není aktuální");
          if (lastIbkrCsvContent != null && bIBKRFlexRefreshPreview != null && bIBKRFlexRefreshPreview.isEnabled()) {
            System.out.println("[IBKR] Auto-refresh preview due to mode change");
            refreshIbkrPreviewFromCachedCsvAsync();
          }
        }
      });
      
      // Build left-aligned button row grouped into compact sections
      javax.swing.JPanel pSource = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
      pSource.setBorder(javax.swing.BorderFactory.createTitledBorder("1) Zdroj"));
      pSource.add(bIBKRFlexFetch);
      pSource.add(bIBKRFlexFile);

      javax.swing.JPanel pPreview = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
      pPreview.setBorder(javax.swing.BorderFactory.createTitledBorder("2) Náhled"));
      pPreview.add(bIBKRFlexRefreshPreview);
      pPreview.add(bIBKRFlexClear);

      javax.swing.JPanel pOptions = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
      pOptions.setBorder(javax.swing.BorderFactory.createTitledBorder("3) Obsah"));
      pOptions.add(cbIBKRFlexImportMode);
      pOptions.add(cbIBKRFlexIncludeCorporateActions);
      pOptions.add(new javax.swing.JLabel("Typy:"));
      pOptions.add(cbIBKRFlexCaRS);
      pOptions.add(cbIBKRFlexCaTC);
      pOptions.add(cbIBKRFlexCaIC);
      pOptions.add(cbIBKRFlexCaTO);
      pOptions.add(bIBKRFlexAssetFilter);

      pIBKRFlexButtons.add(pSource);
      pIBKRFlexButtons.add(pPreview);
      pIBKRFlexButtons.add(pOptions);

      applyIbkrImportModeToUi();

      // Add to main layout above preview
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

    // Ensure status label remains visible
    if (lblIBKRFlexStatus != null) {
      lblIBKRFlexStatus.setVisible(true);
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
    
    boolean hasWork = !transactions.rows.isEmpty() || (duplicatesToUpdate != null && !duplicatesToUpdate.isEmpty());
    if (hasWork) {
      bIBKRFlexFetch.setText("Sloučit do databáze");
      bIBKRFlexFetch.setToolTipText("Sloučit načtené transakce do hlavní databáze");
    } else {
      bIBKRFlexFetch.setText("Načíst z IBKR");
      bIBKRFlexFetch.setToolTipText("Stáhnout data z IBKR Flex API pro aktuální rok");
    }
    
    // Enable/disable clear button
    if (bIBKRFlexClear != null) {
      bIBKRFlexClear.setEnabled(hasWork);
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
    
    boolean hasWork = !transactions.rows.isEmpty() || (duplicatesToUpdate != null && !duplicatesToUpdate.isEmpty());
    if (hasWork) {
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
    
    java.io.File selectedFile = chooseFileForOpen("Vybrat IBKR Flex CSV soubor", new String[] { ".csv" });
    if (selectedFile == null) {
      System.out.println("[IBKR:FILE:002] User cancelled file selection");
      return;
    }

    // Treat selecting a new file as starting a new preview session.
    // This avoids confusion where the old preview remains visible while the new file is being parsed.
    clearIbkrPreviewAndCache();

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
    lblIBKRFlexStatus.setText("Načítání souboru: " + selectedFile.getName() + "...");
    
     try {
       // Read file content (fast) and then refresh preview asynchronously (slow)
      String csvContent = readFileToString(selectedFile);

      lastIbkrCsvContent = csvContent;
      lastIbkrSourceLabel = selectedFile.getName();
      ibkrPreviewDirty = false;
       if (bIBKRFlexRefreshPreview != null) {
         bIBKRFlexRefreshPreview.setEnabled(false);
       }
       System.out.println("[IBKR:FILE:004] File read successfully, size: " + csvContent.length() + " chars");

      refreshIbkrPreviewFromCachedCsvAsync();
      return;
      
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
        
        TransactionSet mainDbForUndo = mainWindow.getTransactionDatabase();
        mainDbForUndo.beginImportUndoCapture();
        transactions.mergeTo(mainDbForUndo);
        
        // Update duplicates (IBKR Flex: always on)
        int updatedCount = 0;
        if (!duplicatesToUpdate.isEmpty()) {
          TransactionSet mainDb = mainWindow.getTransactionDatabase();
          
          System.out.println("[IBKR:UPDATE:001] Updating " + duplicatesToUpdate.size() + " duplicate transactions");
          
           // Start batch update to prevent double-updating same transaction
           mainDb.startBatchUpdate();
          
            updatedCount += mainDb.updateDuplicateTransactions(duplicatesToUpdate);
          
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
          mainWindow.jumpToFirstImportChangeInView();
        });
        
        // Non-blocking status bar summary (avoid modal dialog)
        mainWindow.setTransientStatusMessage(
            "IBKR Flex: přidáno " + transactionsAdded + ", aktualizováno " + updatedCount,
            10000L);

        AppLog.info("IBKR Flex: sloučení dokončeno (přidáno " + transactionsAdded + ", aktualizováno " + updatedCount + ")");
        
        // Clear preview for next import
        clearPreview();
        updateIBKRFlexButtonState();

        mainWindow.enableUndoImportIfAvailable();

        // For IBKR Flex, close the import window after a successful merge.
        clearIbkrCachedData();
        setVisible(false);
        
        System.out.println("[IBKR:MERGE:006] ✅ Merge completed successfully");
      } catch (Exception e) {
        System.out.println("[IBKR:MERGE:007] ❌ Merge failed: " + e.getMessage());
        e.printStackTrace();
        AppLog.error("IBKR Flex: sloučení selhalo: " + e.getMessage(), e);
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
      importer.setIncludeTrades(mode != IBKR_MODE_TRANS_ONLY && mode != IBKR_MODE_DIVI_ONLY);
      if (mode == IBKR_MODE_TRADES_ONLY) {
        importer.setIncludeCorporateActions(false);
      } else if (mode == IBKR_MODE_TRANS_ONLY) {
        importer.setIncludeCorporateActions(true);
      } else if (mode == IBKR_MODE_DIVI_ONLY) {
        importer.setIncludeCorporateActions(false);
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

      // Unify API and file parsing logic:
      // Use the same cached-CSV preview pipeline as "Načíst ze souboru" so that
      // parsing, collision normalization, duplicate handling and v2 section details
      // behave identically.
      if (lastIbkrCsvContent == null || lastIbkrCsvContent.trim().isEmpty()) {
        javax.swing.JOptionPane.showMessageDialog(this,
            "IBKR API nevrátilo CSV data pro náhled.",
            "IBKR Flex", javax.swing.JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (lblIBKRFlexStatus != null) {
        lblIBKRFlexStatus.setText("Staženo z IBKR API – připravuji náhled...");
      }
      refreshIbkrPreviewFromCachedCsvAsync();

      System.out.println("[IBKR:COMPLETE:001] API download finished - preview refresh scheduled");
      
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

            TransactionSet mainDbForUndo = mainWindow.getTransactionDatabase();
            mainDbForUndo.beginImportUndoCapture();
            transactions.mergeTo(mainDbForUndo);

            // Update duplicates if checkbox is checked
            int updatedCount = 0;
            if (cbUpdateDuplicates.isSelected() && !duplicatesToUpdate.isEmpty()) {
              TransactionSet mainDb = mainWindow.getTransactionDatabase();
              
              System.out.println("[UPDATE:001] Updating " + duplicatesToUpdate.size() + " duplicate transactions");
              
              // Start batch update to prevent double-updating same transaction
              mainDb.startBatchUpdate();
              
               updatedCount += mainDb.updateDuplicateTransactions(duplicatesToUpdate);
              
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
              mainWindow.jumpToFirstImportChangeInView();
            });

            mainWindow.enableUndoImportIfAvailable();

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
    updateIbkrFlexCsvInfoLabel();
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

    if (lblIbkrFlexCsvInfo != null) {
      lblIbkrFlexCsvInfo.setVisible(isIBKR);
    }

    boolean isLocalFile = isLocalFileFormat(formatIndex);
    if (bSelectFile != null) {
      bSelectFile.setVisible(isLocalFile);
    }
    if (lSelectedFile != null) {
      lSelectedFile.setVisible(isLocalFile);
      updateSelectedFileLabel();
    }

    // Show/hide IBKR Flex UI block
    if (formatIndex == 9) {
      setupIBKRFlexUI();
      setIbkrFlexUiVisible(true);
    } else {
      setIbkrFlexUiVisible(false);
    }

    // Clear multi-selection state when switching away from IB TradeLog.
    if (formatIndex != 3) {
      currentFiles = null;
      tradeLogMultiSelection = false;
    }

    // Update button text and state based on current state
    updateImportButtonText();
    updateImportButtonState(); // Check API credentials

    // Repack to adjust window size
    pack();

    // Update the IBKR Flex CSV info label (shown next to Format dropdown).
    updateIbkrFlexCsvInfoLabel();
  }

  private void showIbkrFlexCsvDetailsDialog() {
    if (!isIBKRFlexFormat()) {
      return;
    }
    if (lastIBKRParser == null || lastIBKRParser.getFlexCsvVersion() != IBKRFlexParser.FlexCsvVersion.V2_HEADERS_AND_TRAILERS) {
      javax.swing.JOptionPane.showMessageDialog(this,
          "Detaily jsou dostupné pouze pro IBKR Flex CSV (verze s hlavičkami a trailery).",
          "IBKR Flex - detaily CSV", javax.swing.JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    javax.swing.JTextArea textArea = new javax.swing.JTextArea(20, 120);
    textArea.setEditable(false);
    textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

    StringBuilder sb = new StringBuilder();
    sb.append("IBKR Flex CSV - detaily sekcí (v2)\n");
    if (lastIbkrSourceLabel != null && !lastIbkrSourceLabel.trim().isEmpty()) {
      sb.append("Soubor: ").append(lastIbkrSourceLabel.trim()).append("\n");
    }
    sb.append("\n");

    java.util.List<IBKRFlexParser.FlexAccountSections> accounts = lastIBKRParser.getFlexAccountSections();
    java.util.Map<String, java.util.List<String>> missingByAcc = lastIBKRParser.getMissingMandatoryV2SectionsByAccount();
    if (accounts == null || accounts.isEmpty()) {
      sb.append("Žádné sekce nebyly detekovány.\n");
    } else {
      for (IBKRFlexParser.FlexAccountSections a : accounts) {
        if (a == null) continue;
        String acc = a.accountId != null ? a.accountId.trim() : "";
        if (acc.isEmpty()) acc = "(unknown)";
        sb.append(acc).append("\n");

        java.util.LinkedHashSet<String> printed = new java.util.LinkedHashSet<>();
        if (a.sections != null) {
          for (IBKRFlexParser.FlexSection s : a.sections) {
            if (s == null) continue;
            String label = s.label != null ? s.label.trim() : "";
            String shortLabel = label;
            int semi = shortLabel.indexOf(';');
            if (semi >= 0) shortLabel = shortLabel.substring(0, semi).trim();
            if (shortLabel.isEmpty()) {
              shortLabel = s.code != null ? s.code.trim() : "";
            }
            if (shortLabel.isEmpty()) continue;

            StringBuilder line = new StringBuilder();
            line.append("  - ").append(shortLabel);
            if (s.code != null && !s.code.trim().isEmpty()) {
              line.append(" (").append(s.code.trim()).append(")");
            }
            if (s.rows != null) {
              line.append(" [rows=").append(s.rows).append("]");
            }

            String outLine = line.toString();
            if (printed.add(outLine)) {
              sb.append(outLine).append("\n");
            }
          }
        }

        java.util.List<String> missing = missingByAcc != null ? missingByAcc.get(a.accountId) : null;
        if (missing != null && !missing.isEmpty()) {
          sb.append("  WARNING: missing mandatory sections: ")
              .append(String.join(", ", missing))
              .append("\n");
        }

        sb.append("\n");
      }
    }

    textArea.setText(sb.toString());
    textArea.setCaretPosition(0);

    javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(textArea);
    javax.swing.JOptionPane pane = new javax.swing.JOptionPane(scrollPane, javax.swing.JOptionPane.PLAIN_MESSAGE);
    javax.swing.JDialog dialog = pane.createDialog(this, "IBKR Flex - detaily CSV");
    dialog.setResizable(true);
    dialog.setVisible(true);
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
        TransactionSet mainDbForUndo = mainWindow.getTransactionDatabase();
        mainDbForUndo.beginImportUndoCapture();
        transactions.mergeTo(mainDbForUndo);

        // Update duplicates if checkbox is checked
        if (cbUpdateDuplicates.isSelected() && !duplicatesToUpdate.isEmpty()) {
          int updatedCount = 0;
          TransactionSet mainDb = mainWindow.getTransactionDatabase();
          
          System.out.println("[UPDATE:001] Updating " + duplicatesToUpdate.size() + " duplicate transactions");
          
          // Start batch update to prevent double-updating same transaction
          mainDb.startBatchUpdate();
          
          updatedCount += mainDb.updateDuplicateTransactions(duplicatesToUpdate);
          
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

        mainWindow.enableUndoImportIfAvailable();

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
  private javax.swing.JLabel lSummary;
  private javax.swing.JLabel lToUpdate;
  private javax.swing.JScrollPane updateScrollPane;
  private javax.swing.JTable updateTable;
  private javax.swing.JLabel lUnimported;
  private javax.swing.JScrollPane niScrollPane;
  private javax.swing.JTable niTable;
  private javax.swing.JButton bClearPreview;
  private javax.swing.JLabel lCacheStatus;
  private javax.swing.JTable table;
  // End of variables declaration//GEN-END:variables

}
