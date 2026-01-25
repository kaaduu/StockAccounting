/*
 * MainWindow.java
 *
 * Created on 5. říjen 2006, 23:32
 */

package cz.datesoft.stockAccounting;

import java.awt.Component;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import com.toedter.calendar.JDateChooser;
import java.util.Date;
import java.util.Set;
import java.io.File;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.awt.FileDialog;
import javax.swing.JFileChooser;
import java.awt.event.KeyEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.swing.Action;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author lemming2
 */
public class MainWindow extends javax.swing.JFrame {

  // <editor-fold defaultstate="collapsed" desc="Class: DateChooserCellEditor">
  /**
   * Custom cell renderer that highlights recently updated rows with light yellow
   * background
   */
  private class HighlightedCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
    @Override
    public java.awt.Component getTableCellRendererComponent(
        javax.swing.JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
      java.awt.Component c = super.getTableCellRendererComponent(
          table, value, isSelected, hasFocus, row, column);

      // Reset font to base (renderer instances are reused)
      c.setFont(table.getFont());

      // Check if this row was recently inserted/updated (import highlighting)
      if (!isSelected) {
        try {
          if (Settings.getHighlightInsertedEnabled() && transactions.isRecentlyInserted(row)) {
            c.setBackground(Settings.getHighlightInsertedColor());
          } else if (Settings.getHighlightUpdatedEnabled() && transactions.isRecentlyUpdated(row)) {
            c.setBackground(Settings.getHighlightUpdatedColor());

            int modelCol = table.convertColumnIndexToModel(column);
            if (transactions.isRecentlyUpdatedColumn(row, modelCol)) {
              c.setFont(table.getFont().deriveFont(java.awt.Font.BOLD));
            }
          } else {
            c.setBackground(java.awt.Color.WHITE);
          }
        } catch (Exception e) {
          // Safety: if checking fails, just use white background
          c.setBackground(java.awt.Color.WHITE);
        }
      }

      return c;
    }
  }

  /**
   * Date renderer with highlighting support
   */
  private class HighlightedDateRenderer extends CZDateRenderer {
    @Override
    public java.awt.Component getTableCellRendererComponent(
        javax.swing.JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
      java.awt.Component c = super.getTableCellRendererComponent(
          table, value, isSelected, hasFocus, row, column);

      // Reset font to base (renderer instances are reused)
      c.setFont(table.getFont());

      // Check if this row was recently inserted/updated (import highlighting)
      if (!isSelected) {
        try {
          if (Settings.getHighlightInsertedEnabled() && transactions.isRecentlyInserted(row)) {
            c.setBackground(Settings.getHighlightInsertedColor());
          } else if (Settings.getHighlightUpdatedEnabled() && transactions.isRecentlyUpdated(row)) {
            c.setBackground(Settings.getHighlightUpdatedColor());

            int modelCol = table.convertColumnIndexToModel(column);
            if (transactions.isRecentlyUpdatedColumn(row, modelCol)) {
              c.setFont(table.getFont().deriveFont(java.awt.Font.BOLD));
            }
          } else {
            c.setBackground(java.awt.Color.WHITE);
          }
        } catch (Exception e) {
          // Safety: if checking fails, just use white background
          c.setBackground(java.awt.Color.WHITE);
        }
      }

      return c;
    }
  }

  /**
   * Date cell editor using JCalendar. We do not use JCalendar builtin date
   * editor,
   * since it is not configurable. And we need some "specialities" (like
   * remembering last date) anyway.
   */
  private class DateChooserCellEditor extends javax.swing.AbstractCellEditor
      implements javax.swing.table.TableCellEditor {
    private JDateChooser dateChooser;

    public DateChooserCellEditor() {
      dateChooser = new JDateChooser();
    }

    public Component getTableCellEditorComponent(javax.swing.JTable table, Object value, boolean isSelected, int row,
        int column) {
      Date date = null;

      if (value == null) {
        // Try to get last date set
        date = Main.getMainWindow().getTransactionDatabase().getLastDateSet();
      } else if (value instanceof Date) {
        date = (Date) value;
      }

      dateChooser.setDate(date);
      dateChooser.setFont(table.getFont()); // Ensure chooser uses same font as the table
      // Display seconds only when present (renderer does the same).
      dateChooser.setDateFormatString("dd. MM. yyyy HH:mm:ss");

      return dateChooser;
    }

    public Object getCellEditorValue() {
      Date date = dateChooser.getDate();
      if (date == null)
        return null;

      GregorianCalendar res = new GregorianCalendar();

      res.setTime(date);

      // Clear milliseconds; keep seconds.
      res.set(GregorianCalendar.MILLISECOND, 0);

      return res.getTime();
    }
  }
  // </editor-fold>

  // Transaction database
  private TransactionSet transactions;

  // Import window
  private ImportWindow importWindow;

  // Current file for .t212state tracking
  private File currentFile;

  // Current Trading 212 import state for this file
  private Trading212ImportState currentFileImportState;

  // Compute window
  private ComputeWindow computeWindow;

  // Account state window
  private AccountStateWindow accountStateWindow;

  // About window
  private AboutWindow aboutWindow;

  // Dollar icon image
  private javax.swing.ImageIcon dollarIcon;

  /** Creates new form MainWindow */
  public MainWindow() {
    initComponents();

    System.out.println("DEBUG: miNewActionPerformed - creating new TransactionSet");
    transactions = new TransactionSet();
    System.out.println("DEBUG: New TransactionSet rows.size() = " + transactions.rows.size());

    // Re-add the TableModelListener for automatic status bar updates
    transactions.addTableModelListener(new javax.swing.event.TableModelListener() {
      @Override
      public void tableChanged(javax.swing.event.TableModelEvent e) {
        System.out.println("DEBUG: TableModelEvent received, type: " + e.getType());
        updateStatusBar();
      }
    });

    table.setModel(transactions);
    System.out.println("DEBUG: Table model set, calling initTableColumns");
    // Call mainwindow table initialization - helpers, column setting
    initTableColumns();

    // Force immediate status bar update for initial empty state
    System.out.println("DEBUG: Forcing status bar update after Nový");
    updateStatusBar();

    // Undo import is disabled by default
    enableUndoImportIfAvailable();

    // Create dialogs
    importWindow = new ImportWindow(this, true);
    computeWindow = new ComputeWindow(this, false);
    accountStateWindow = new AccountStateWindow(this, false);
    aboutWindow = new AboutWindow(this, true);

    java.net.URL iconURL = getClass().getResource("images/dolarm.png");
    dollarIcon = new javax.swing.ImageIcon(iconURL);
    setIconImage(getDollarImage());
    updateTitle();

    // Auto-maximize if enabled
    if (Settings.getAutoMaximized()) {
      setExtendedState(javax.swing.JFrame.MAXIMIZED_BOTH);
    }

    // Check for last opened file
    checkLastOpenedFile();
  }

  private void checkLastOpenedFile() {
    String lastPath = Settings.getLastOpenedFile();
    File lastFile = null;
    if (lastPath != null) {
      File f = new File(lastPath);
      if (f.exists() && f.isFile())
        lastFile = f;
    }

    File[] recentFiles = getRecentDatFiles();

    if (Settings.getAutoLoadLastFile()) {
      // Prefer the last explicitly opened file (user expectation).
      File toOpen = lastFile;
      if (toOpen == null && recentFiles != null && recentFiles.length > 0)
        toOpen = recentFiles[0];

      if (toOpen != null) {
        try {
          openFile(toOpen);
        } catch (Exception e) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(this, "Chyba při automatickém načítání souboru:\n" + e.getMessage());
        }
      }
      return;
    }

    // Auto-Load disabled: show selection dialog if we have recent files.
    if (recentFiles != null && recentFiles.length > 0) {
      File defaultSelection = recentFiles[0];
      if (lastFile != null) {
        for (File rf : recentFiles) {
          if (rf != null && rf.equals(lastFile)) {
            defaultSelection = lastFile;
            break;
          }
        }
      }

      Object selected = JOptionPane.showInputDialog(
          this,
          "Vyberte datový soubor k načtení:",
          "Otevřít soubor",
          JOptionPane.QUESTION_MESSAGE,
          null,
          recentFiles,
          defaultSelection);

      if (selected != null) {
        try {
          openFile((File) selected);
        } catch (Exception e) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(this, "Chyba při načítání souboru:\n" + e.getMessage());
        }
      }

      return;
    }

    // No recent files from scan: ask to open last opened file if known.
    if (lastFile != null) {
      if (JOptionPane.showConfirmDialog(this,
          "Chcete načíst poslední otevřený soubor?\n" + lastFile.getAbsolutePath(),
          "Načíst poslední soubor",
          JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
        try {
          openFile(lastFile);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Scan data directory for recent .dat files, sorted by date desc
   */
  private File[] getRecentDatFiles() {
    String dataDir = Settings.getDataDirectory();
    if (dataDir == null)
      return null;

    File dir = new File(dataDir);
    if (!dir.exists() || !dir.isDirectory())
      return null;

    File[] files = dir.listFiles(new java.io.FilenameFilter() {
      public boolean accept(File d, String name) {
        return name.toLowerCase().endsWith(".dat");
      }
    });

    if (files == null || files.length == 0)
      return null;

    // Sort by last modified descending
    java.util.Arrays.sort(files, new java.util.Comparator<File>() {
      public int compare(File f1, File f2) {
        return Long.compare(f2.lastModified(), f1.lastModified());
      }
    });

    // Limit to top 5
    if (files.length > 5) {
      File[] top = new File[5];
      System.arraycopy(files, 0, top, 0, 5);
      return top;
    }

    return files;
  }

  /**
   * Return image for (window) dollar icon
   */
  public java.awt.Image getDollarImage() {
    return dollarIcon.getImage();
  }

  /**
   * Check whether transaction set is modify and offer to save if so.
   *
   * @return True when action can proceed, false when cancel was selected.
   */
  private boolean checkModified() {
    if (transactions.isModified()) {
      int res = JOptionPane.showConfirmDialog(this, "Datový soubor byl modifikován. Přejete si jej uložit?", "Otázka",
          JOptionPane.YES_NO_CANCEL_OPTION);

      if (res == JOptionPane.CANCEL_OPTION)
        return false;

      if (res == JOptionPane.YES_OPTION) {
        return saveAction();
      }
    }

    return true;
  }

  /**
   * Called to handle exit request
   */
  private void exitRequested() {
    if (checkModified()) {
      System.exit(0);
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

    cbCurrencies = new javax.swing.JComboBox();
    cbDirection = new javax.swing.JComboBox();
    cbTickers = new javax.swing.JComboBox();
    cbMarkets = new javax.swing.JComboBox();
    cbType = new javax.swing.JComboBox();
    jPanel2 = new javax.swing.JPanel();
    bApplyFilter = new javax.swing.JButton();
    bClearFilter = new javax.swing.JButton();
    jLabel2 = new javax.swing.JLabel();
    dcFrom = new com.toedter.calendar.JDateChooser();
    jLabel3 = new javax.swing.JLabel();
    dcTo = new com.toedter.calendar.JDateChooser();
    jLabel4 = new javax.swing.JLabel();
    tfTicker = new javax.swing.JTextField();
    jLabel5 = new javax.swing.JLabel();
    tfMarket = new javax.swing.JTextField();
    jLabel6 = new javax.swing.JLabel();
    jSeparator3 = new javax.swing.JSeparator();
    bDelete = new javax.swing.JButton();
    bSort = new javax.swing.JButton();
    jLabel7 = new javax.swing.JLabel();
    tfNote = new javax.swing.JTextField();
    jLabel8 = new javax.swing.JLabel();
    cbBrokerFilter = new javax.swing.JComboBox();
    jLabel9 = new javax.swing.JLabel();
    cbAccountIdFilter = new javax.swing.JComboBox();
    jLabel10 = new javax.swing.JLabel();
    cbEffectFilter = new javax.swing.JComboBox();
    cbShowMetadata = new javax.swing.JCheckBox();
    jScrollPane1 = new javax.swing.JScrollPane();
    table = new javax.swing.JTable();
    jPanel1 = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    jMenuBar1 = new javax.swing.JMenuBar();
    jMenu1 = new javax.swing.JMenu();
    miNew = new javax.swing.JMenuItem();
    miOpen = new javax.swing.JMenuItem();
    miOpenAdd = new javax.swing.JMenuItem();
    miSave = new javax.swing.JMenuItem();
    miSaveAs = new javax.swing.JMenuItem();
    miSaveFiltered = new javax.swing.JMenuItem();
    miImport = new javax.swing.JMenuItem();
    miExport = new javax.swing.JMenuItem();
    miExportFIO = new javax.swing.JMenuItem();
    jSeparator2 = new javax.swing.JSeparator();
    miExit = new javax.swing.JMenuItem();
    miShowLogs = new javax.swing.JMenuItem();
    jMenu2 = new javax.swing.JMenu();
    miAccountState = new javax.swing.JMenuItem();
    miReport = new javax.swing.JMenuItem();
    jSeparator1 = new javax.swing.JSeparator();
    miSettings = new javax.swing.JMenuItem();
    jMenu3 = new javax.swing.JMenu();
    miAbout = new javax.swing.JMenuItem();

    cbCurrencies
        .setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

    cbDirection.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "A-nákup", "E-nákup", "T-přidání",
        "T-odebrání", "D-hrubá", "D-daň", "D-neznámá", "A-prodej", "E-prodej", " " }));

    cbTickers.setEditable(true);
    cbTickers.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

    cbMarkets.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

    cbType.setModel(
        new javax.swing.DefaultComboBoxModel(new String[] { "CP", "Derivát", "Transformace", "Dividenda", "Cash" }));

    cbTypeFilter = new javax.swing.JComboBox();
    cbTypeFilter.setModel(
        new javax.swing.DefaultComboBoxModel(
            new String[] { "", "CP", "Derivát", "Transformace", "Dividenda", "Cash" }));

    cbEffectFilter = new javax.swing.JComboBox();
    cbEffectFilter.setModel(
        new javax.swing.DefaultComboBoxModel(new String[] { "", "Assignment", "Exercise", "Expired" }));

    // Set sizes and handlers for filter combo boxes
    cbBrokerFilter.setMinimumSize(new java.awt.Dimension(80, 20));
    cbBrokerFilter.setPreferredSize(new java.awt.Dimension(80, 20));
    cbBrokerFilter.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        applyFilter(); // Apply filter on selection change
      }
    });

    cbAccountIdFilter.setMinimumSize(new java.awt.Dimension(100, 20));
    cbAccountIdFilter.setPreferredSize(new java.awt.Dimension(100, 20));
    cbAccountIdFilter.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        applyFilter(); // Apply filter on selection change
      }
    });

    cbEffectFilter.setMinimumSize(new java.awt.Dimension(100, 20));
    cbEffectFilter.setPreferredSize(new java.awt.Dimension(100, 20));
    cbEffectFilter.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        applyFilter(); // Apply filter on selection change
      }
    });

    // Column visibility checkbox
    cbShowMetadata.setText("Show Metadata");
    cbShowMetadata.setSelected(Settings.getShowMetadataColumns());
    cbShowMetadata.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        Settings.setShowMetadataColumns(cbShowMetadata.isSelected());
        updateColumnVisibility();
      }
    });

    cbShowSeconds = new javax.swing.JCheckBox();
    cbShowSeconds.setText("Sekundy");
    cbShowSeconds.setToolTipText("Zobrazit/skryt sekundy ve sloupcích Datum a Vypořádání");
    cbShowSeconds.setSelected(Settings.getShowSecondsInDateColumns());
    cbShowSeconds.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        Settings.setShowSecondsInDateColumns(cbShowSeconds.isSelected());
        initTableColumns();
        // Ensure widths are recalculated and applied immediately.
        table.doLayout();
        table.revalidate();
        table.repaint();
      }
    });

    bCopy = new javax.swing.JButton();
    bCopy.setText("Kopírovat");
    bCopy.setToolTipText("Zkopíruje vybrané řádky do schránky jako CSV (včetně hlavičky, uvozovky, oddělovač ',')");
    bCopy.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bCopyActionPerformed(evt);
      }
    });

    bClearColors = new javax.swing.JButton();
    bClearColors.setText("Vyčistit barvy");
    bClearColors.setToolTipText("Zruší zvýraznění nových/aktualizovaných řádků");
    bClearColors.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bClearColorsActionPerformed(evt);
      }
    });

    // Set labels for filter components
    jLabel8.setText("Broker:");
    jLabel9.setText("Account ID:");
    jLabel10.setText("Effect:");

    setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
    setTitle("Akciové účetnictví");
    addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowClosing(java.awt.event.WindowEvent evt) {
        formWindowClosing(evt);
      }

      public void windowOpened(java.awt.event.WindowEvent evt) {
        formWindowOpened(evt);
      }
    });
    getContentPane().setLayout(new java.awt.BorderLayout());

    jPanel2.setLayout(new java.awt.GridBagLayout());

    bApplyFilter.setText("Filtrovat");
    bApplyFilter.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bApplyFilterActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(bApplyFilter, gridBagConstraints);

    bClearFilter.setText("Zrušit");
    bClearFilter.setEnabled(false);
    bClearFilter.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bClearFilterActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(bClearFilter, gridBagConstraints);

    jLabel2.setText("Od:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel2, gridBagConstraints);

    dcFrom.setMinimumSize(new java.awt.Dimension(90, 20));
    dcFrom.setPreferredSize(new java.awt.Dimension(90, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(dcFrom, gridBagConstraints);

    jLabel3.setText("Do:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel3, gridBagConstraints);

    dcTo.setMinimumSize(new java.awt.Dimension(120, 20));
    dcTo.setPreferredSize(new java.awt.Dimension(120, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(dcTo, gridBagConstraints);

    jLabel4.setText("Ticker:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel4, gridBagConstraints);

    tfTicker.setMinimumSize(new java.awt.Dimension(60, 20));
    tfTicker.setPreferredSize(new java.awt.Dimension(60, 20));
    tfTicker.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        tfTickerActionPerformed(evt);
      }
    });
    tfTicker.addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyPressed(java.awt.event.KeyEvent evt) {
        tfTickerKeyPressed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(tfTicker, gridBagConstraints);

    jLabel5.setText("Trh:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel5, gridBagConstraints);

    tfMarket.setMinimumSize(new java.awt.Dimension(60, 20));
    tfMarket.setPreferredSize(new java.awt.Dimension(60, 20));
    tfMarket.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        tfMarketActionPerformed(evt);
      }
    });
    tfMarket.addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyPressed(java.awt.event.KeyEvent evt) {
        tfMarketKeyPressed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 9;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(tfMarket, gridBagConstraints);

    jLabel6.setText("Typ:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 10;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel6, gridBagConstraints);

    cbTypeFilter.setMinimumSize(new java.awt.Dimension(80, 20));
    cbTypeFilter.setPreferredSize(new java.awt.Dimension(80, 20));
    cbTypeFilter.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        applyFilter(); // Apply filter on selection change
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 11;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(cbTypeFilter, gridBagConstraints);

    // Separator spanning both rows
    jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 12;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridheight = 2; // Span both rows
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jSeparator3, gridBagConstraints);

    // Delete button spanning both rows
    bDelete.setText("Smazat řádek");
    bDelete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bDeleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 13;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridheight = 2; // Span both rows
    gridBagConstraints.anchor = java.awt.GridBagConstraints.CENTER;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(bDelete, gridBagConstraints);

    // Undo delete button spanning both rows
    bUndoDelete = new javax.swing.JButton();
    bUndoDelete.setText("Zpět");
    bUndoDelete.setEnabled(false);
    bUndoDelete.setToolTipText("Vrátí poslední smazání řádků (zruší všechny filtry)");
    bUndoDelete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bUndoDeleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2; // Moved to row 2 to avoid overlap with Note filter
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(bUndoDelete, gridBagConstraints);

    // Undo import button spanning both rows
    bUndoImport = new javax.swing.JButton();
    bUndoImport.setText("Zpět import");
    bUndoImport.setEnabled(false);
    bUndoImport.setToolTipText("Vrátí poslední import (vložené řádky i aktualizace duplikátů)");
    bUndoImport.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bUndoImportActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2; // Moved to row 2 to avoid overlap
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(bUndoImport, gridBagConstraints);

    // Clear TxnID/AccountID button spanning both rows
    bClearTxnId = new javax.swing.JButton();
    bClearTxnId.setText("Smazat TxnID");
    bClearTxnId.setToolTipText("Odstraní Broker/ID účtu/ID transakce z vybraných řádků (včetně Note)");
    bClearTxnId.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bClearTxnIdActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 16;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridheight = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.CENTER;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(bClearTxnId, gridBagConstraints);

    // Sort button spanning both rows
    bSort.setText("Seřadit");
    bSort.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSortActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 17;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridheight = 2; // Span both rows
    gridBagConstraints.anchor = java.awt.GridBagConstraints.CENTER;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    jPanel2.add(bSort, gridBagConstraints);

    // ===== ROW 1: Metadata Filters (with left indentation for visual hierarchy)
    // =====

    // Row 1: Note filter
    jLabel7.setText("Note:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 20, 5, 0); // 20px left indent
    jPanel2.add(jLabel7, gridBagConstraints);

    tfNote.setMinimumSize(new java.awt.Dimension(150, 20));
    tfNote.setPreferredSize(new java.awt.Dimension(150, 20));
    tfNote.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        tfNoteActionPerformed(evt);
      }
    });
    tfNote.addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyPressed(java.awt.event.KeyEvent evt) {
        tfNoteKeyPressed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2; // Span 2 columns for wider note field
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(tfNote, gridBagConstraints);

    // Row 1: Broker filter
    jLabel8.setText("Broker:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel8, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(cbBrokerFilter, gridBagConstraints);

    // Row 1: Account ID filter
    jLabel9.setText("Account ID:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel9, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(cbAccountIdFilter, gridBagConstraints);

    // Row 1: Effect filter
    jLabel10.setText("Effect:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 7;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel10, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 8;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(cbEffectFilter, gridBagConstraints);

    // Add column visibility checkbox
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 9;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 15, 5, 0); // Extra left padding
    jPanel2.add(cbShowMetadata, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 10;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 0);
    jPanel2.add(cbShowSeconds, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 11;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 0);
    jPanel2.add(bCopy, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 12;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 0);
    jPanel2.add(bClearColors, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    getContentPane().add(jPanel2, java.awt.BorderLayout.NORTH);

    table.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null }
        },
        new String[] {
            "Datum", "Směr", "Ticker", "Množství", "Kurs", "Měna kursu", "Poplatky", "Měna poplatků", "Note"
        }) {
      Class[] types = new Class[] {
          java.lang.Object.class, java.lang.Object.class, java.lang.String.class, java.lang.Integer.class,
          java.lang.Double.class, java.lang.Object.class, java.lang.Double.class, java.lang.Object.class,
          java.lang.String.class
      };

      public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
      }
    });
    table.setSurrendersFocusOnKeystroke(true);
    jScrollPane1.setViewportView(table);

    getContentPane().add(jScrollPane1, java.awt.BorderLayout.CENTER);

    // Create status bar layout
    jLabel1.setText("Záznamů: 0");
    jLabel1.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));

    jPanel1.setLayout(new java.awt.BorderLayout());
    jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    jPanel1.add(jLabel1, java.awt.BorderLayout.WEST);

    getContentPane().add(jPanel1, java.awt.BorderLayout.SOUTH);

    jMenu1.setText("Soubor");

    miNew.setText("Nový");
    miNew.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miNewActionPerformed(evt);
      }
    });
    jMenu1.add(miNew);

    miOpen.setAccelerator(
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.ALT_DOWN_MASK));
    miOpen.setText("Otevřít - nový");
    miOpen.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miOpenActionPerformed(evt);
      }
    });
    jMenu1.add(miOpen);

    miOpenAdd.setText("Otevřít - přidat");
    miOpenAdd.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miOpenAddActionPerformed(evt);
      }
    });
    jMenu1.add(miOpenAdd);

    miSave.setText("Uložit");
    miSave.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miSaveActionPerformed(evt);
      }
    });
    jMenu1.add(miSave);

    miSaveAs.setText("Uložit jako");
    miSaveAs.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miSaveAsActionPerformed(evt);
      }
    });
    jMenu1.add(miSaveAs);

    miSaveFiltered.setText("Uložit vyfiltrované");
    miSaveFiltered.setEnabled(false);
    miSaveFiltered.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miSaveFilteredActionPerformed(evt);
      }
    });
    jMenu1.add(miSaveFiltered);

    miImport.setAccelerator(
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, java.awt.event.InputEvent.CTRL_DOWN_MASK));
    miImport.setText("Import od brokera");
    miImport.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miImportActionPerformed(evt);
      }
    });
    jMenu1.add(miImport);

    miExport.setText("Export do interni csv");
    miExport.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miExportActionPerformed(evt);
      }
    });
    jMenu1.add(miExport);

    miExportFIO.setText("Export do FIO formatu");
    miExportFIO.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miExportFIOActionPerformed(evt);
      }
    });
    jMenu1.add(miExportFIO);
    jMenu1.add(jSeparator2);

    miExit.setAccelerator(
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_DOWN_MASK));
    miExit.setText("Konec");
    miExit.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miExitActionPerformed(evt);
      }
    });
    jMenu1.add(miExit);

    jMenuBar1.add(jMenu1);

    jMenu2.setText("Nástroje");

    miAccountState.setText("Stav účtu");
    miAccountState.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miAccountStateActionPerformed(evt);
      }
    });
    jMenu2.add(miAccountState);

    miReport.setText("Podklad pro DP");
    miReport.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miReportActionPerformed(evt);
      }
    });
    jMenu2.add(miReport);
    jMenu2.add(jSeparator1);

    miSettings.setText("Nastavení");
    miSettings.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miSettingsActionPerformed(evt);
      }
    });
    jMenu2.add(miSettings);

    jMenuBar1.add(jMenu2);

    jMenu3.setText("Nápověda");

    miAbout.setText("O aplikaci");
    miAbout.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miAboutActionPerformed(evt);
      }
    });
    jMenu3.add(miAbout);

    miShowLogs.setText("Logy");
    miShowLogs.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miShowLogsActionPerformed(evt);
      }
    });
    jMenu3.add(miShowLogs);

    jMenuBar1.add(jMenu3);

    setJMenuBar(jMenuBar1);

    pack();

    // Set reasonable minimum size for two-row filter layout
    setMinimumSize(new java.awt.Dimension(1200, 600));
  }// </editor-fold>//GEN-END:initComponents

  private void formWindowClosing(java.awt.event.WindowEvent evt)// GEN-FIRST:event_formWindowClosing
  {// GEN-HEADEREND:event_formWindowClosing
    exitRequested();

  }// GEN-LAST:event_formWindowClosing

  private void miExitActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miExitActionPerformed
  {// GEN-HEADEREND:event_miExitActionPerformed
    exitRequested();
  }// GEN-LAST:event_miExitActionPerformed

  public void initTableColumns() {
    // get list of tickers from current transactions (if new = empty)
    cbTickers.setModel(transactions.getTickersModel());

    // Populate metadata filter combo boxes
    cbBrokerFilter.setModel(transactions.getBrokersModel());
    cbAccountIdFilter.setModel(transactions.getAccountIdsModel());

    // Enable manual column resizing
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

    // Create renderers for highlighting
    HighlightedDateRenderer dateRenderer = new HighlightedDateRenderer();
    HighlightedCellRenderer highlightRenderer = new HighlightedCellRenderer();

    // Compute packed width for date columns based on a worst-case sample string.
    int dateColWidth = computePackedDateColumnWidth();

    // Datum obchodu
    table.getColumnModel().getColumn(0).setPreferredWidth(dateColWidth);
    table.getColumnModel().getColumn(0).setMaxWidth(dateColWidth);
    table.getColumnModel().getColumn(0).setCellRenderer(dateRenderer);
    table.getColumnModel().getColumn(0).setCellEditor(new DateChooserCellEditor());
    // Typ
    table.getColumnModel().getColumn(1).setPreferredWidth(100);
    table.getColumnModel().getColumn(1).setMaxWidth(100);
    table.getColumnModel().getColumn(1).setCellEditor(new javax.swing.DefaultCellEditor(cbType));
    // Smer
    table.getColumnModel().getColumn(2).setPreferredWidth(80);
    table.getColumnModel().getColumn(2).setMaxWidth(80);
    table.getColumnModel().getColumn(2).setCellEditor(new TransactionDirectionCellEditor(cbDirection));
    // Ticker
    table.getColumnModel().getColumn(3).setPreferredWidth(150);
    table.getColumnModel().getColumn(3).setMaxWidth(300);
    table.getColumnModel().getColumn(3).setCellEditor(new javax.swing.DefaultCellEditor(cbTickers));
    table.getColumnModel().getColumn(6).setCellEditor(new javax.swing.DefaultCellEditor(cbCurrencies));
    table.getColumnModel().getColumn(4).setPreferredWidth(80);
    table.getColumnModel().getColumn(4).setMaxWidth(80);
    table.getColumnModel().getColumn(5).setPreferredWidth(80);
    table.getColumnModel().getColumn(5).setMaxWidth(80);
    table.getColumnModel().getColumn(6).setPreferredWidth(50);
    table.getColumnModel().getColumn(6).setMaxWidth(50);
    // fee
    table.getColumnModel().getColumn(7).setPreferredWidth(30);
    table.getColumnModel().getColumn(7).setMaxWidth(50);
    // feeCurrency
    table.getColumnModel().getColumn(8).setPreferredWidth(30);
    table.getColumnModel().getColumn(8).setMaxWidth(50);
    table.getColumnModel().getColumn(8).setCellEditor(new javax.swing.DefaultCellEditor(cbCurrencies));
    // Trh
    table.getColumnModel().getColumn(9).setPreferredWidth(80);
    table.getColumnModel().getColumn(9).setMaxWidth(80);
    // Datum vyporadani
    table.getColumnModel().getColumn(10).setPreferredWidth(dateColWidth);
    table.getColumnModel().getColumn(10).setMaxWidth(dateColWidth);
    table.getColumnModel().getColumn(10).setCellRenderer(dateRenderer);
    table.getColumnModel().getColumn(10).setCellEditor(new DateChooserCellEditor());
    // Broker
    table.getColumnModel().getColumn(11).setPreferredWidth(80);
    table.getColumnModel().getColumn(11).setMaxWidth(150);
    // ID účtu (Account ID)
    table.getColumnModel().getColumn(12).setPreferredWidth(100);
    table.getColumnModel().getColumn(12).setMaxWidth(150);
    // ID transakce (Txn ID)
    table.getColumnModel().getColumn(13).setPreferredWidth(120);
    table.getColumnModel().getColumn(13).setMaxWidth(200);
    // Efekt (Effect)
    table.getColumnModel().getColumn(14).setPreferredWidth(120);
    table.getColumnModel().getColumn(14).setMaxWidth(200);
    // Poznamka (Note)
    table.getColumnModel().getColumn(15).setPreferredWidth(200);
    table.getColumnModel().getColumn(15).setMaxWidth(500);

    // Apply highlighted cell renderer to all non-date columns
    for (int i = 1; i <= 15; i++) {
      if (i != 0 && i != 10) { // Skip date columns
        table.getColumnModel().getColumn(i).setCellRenderer(highlightRenderer);
      }
    }

    // Apply column visibility settings
    updateColumnVisibility();
  }

  private int computePackedDateColumnWidth() {
    try {
      boolean showSeconds = Settings.getShowSecondsInDateColumns();
      String sample = showSeconds ? "28.12.2026 23:59:59" : "28.12.2026 23:59";

      javax.swing.table.TableColumn col0 = table.getColumnModel().getColumn(0);
      javax.swing.table.TableColumn col10 = table.getColumnModel().getColumn(10);

      int w0 = computeColumnWidthForSample(0, col0.getHeaderValue(), sample);
      int w10 = computeColumnWidthForSample(10, col10.getHeaderValue(), sample);

      return Math.max(w0, w10);
    } catch (Exception e) {
      // Fallback to previous fixed widths
      return Settings.getShowSecondsInDateColumns() ? 150 : 100;
    }
  }

  private int computeColumnWidthForSample(int viewColumnIndex, Object headerValue, String sample) {
    int padding = 18;

    int headerWidth = 0;
    try {
      javax.swing.table.JTableHeader th = table.getTableHeader();
      if (th != null) {
        javax.swing.table.TableCellRenderer hr = th.getDefaultRenderer();
        java.awt.Component c = hr.getTableCellRendererComponent(table, headerValue, false, false, -1, viewColumnIndex);
        if (c != null) {
          headerWidth = c.getPreferredSize().width;
        }
      }
    } catch (Exception e) {
      headerWidth = 0;
    }

    int sampleWidth = 0;
    try {
      java.awt.FontMetrics fm = table.getFontMetrics(table.getFont());
      sampleWidth = fm.stringWidth(sample);
    } catch (Exception e) {
      sampleWidth = 0;
    }

    return Math.max(headerWidth, sampleWidth) + padding;
  }

  private void bCopyActionPerformed(java.awt.event.ActionEvent evt) {
    if (table == null)
      return;

    int[] selected = table.getSelectedRows();
    if (selected == null || selected.length == 0) {
      JOptionPane.showMessageDialog(this, "Nejsou vybrané žádné řádky.", "Kopírovat", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    boolean showSeconds = Settings.getShowSecondsInDateColumns();
    java.text.SimpleDateFormat df2 = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
    java.text.SimpleDateFormat df3 = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    java.util.function.Function<String, String> csvQuote = (String s) -> {
      if (s == null)
        s = "";
      // Keep CSV one-row-per-line.
      s = s.replace('\n', ' ').replace('\r', ' ');
      // Escape quotes per RFC4180.
      s = s.replace("\"", "\"\"");
      return "\"" + s + "\"";
    };

    java.util.function.Function<Object, String> toCellString = (Object v) -> {
      if (v == null)
        return "";
      if (v instanceof java.util.Date) {
        java.util.Date d = (java.util.Date) v;
        if (showSeconds) {
          java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
          cal.setTime(d);
          if (cal.get(java.util.GregorianCalendar.SECOND) != 0) {
            return df3.format(d);
          }
        }
        return df2.format(d);
      }
      return String.valueOf(v);
    };

    int modelColCount = table.getModel() != null ? table.getModel().getColumnCount() : 0;
    java.util.List<Integer> modelCols = new java.util.ArrayList<>();
    for (int mc = 0; mc < modelColCount; mc++) {
      modelCols.add(mc);
    }

    StringBuilder sb = new StringBuilder();

    // Header row (always include all columns)
    for (int c = 0; c < modelCols.size(); c++) {
      int modelCol = modelCols.get(c);
      String header = table.getModel().getColumnName(modelCol);
      if (c > 0)
        sb.append(',');
      sb.append(csvQuote.apply(header));
    }
    sb.append('\n');

    for (int r = 0; r < selected.length; r++) {
      int viewRow = selected[r];
      int modelRow = table.convertRowIndexToModel(viewRow);

      for (int c = 0; c < modelCols.size(); c++) {
        int modelCol = modelCols.get(c);
        Object v = table.getModel().getValueAt(modelRow, modelCol);
        String cell = toCellString.apply(v);
        if (c > 0)
          sb.append(',');
        sb.append(csvQuote.apply(cell));
      }
      if (r < selected.length - 1)
        sb.append('\n');
    }

    try {
      java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(sb.toString());
      java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Nepodařilo se zkopírovat do schránky: " + e.getMessage(),
          "Kopírovat", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void bClearColorsActionPerformed(java.awt.event.ActionEvent evt) {
    try {
      transactions.clearHighlights();
      lastStatusMessage = " | Barvy vyčištěny";
      updateStatusBar();
      table.repaint();
    } catch (Exception e) {
      // ignore
    }
  }

  /**
   * Refresh metadata filter combo boxes with current transaction data
   */
  public void refreshMetadataFilters() {
    cbBrokerFilter.setModel(transactions.getBrokersModel());
    cbAccountIdFilter.setModel(transactions.getAccountIdsModel());

    // Reset selections to empty (no filter)
    cbBrokerFilter.setSelectedIndex(0);
    cbAccountIdFilter.setSelectedIndex(0);
  }

  /**
   * Update visibility of metadata columns based on settings
   */
  public void updateColumnVisibility() {
    boolean showColumns = Settings.getShowMetadataColumns();
    TableColumnModel columnModel = table.getColumnModel();

    // Metadata columns are 11-14: Broker, AccountID, TxnID, Effect
    for (int i = 11; i <= 14; i++) {
      TableColumn column = columnModel.getColumn(i);
      column.setMinWidth(showColumns ? 1 : 0);
      column.setMaxWidth(showColumns ? Integer.MAX_VALUE : 0);
      column.setPreferredWidth(showColumns ? (i == 11 ? 80 : i == 12 ? 100 : i == 13 ? 120 : 120) : 0);
      column.setWidth(showColumns ? column.getPreferredWidth() : 0);
    }

    // Force table layout update
    table.revalidate();
    table.repaint();
  }

  private void miNewActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miNewActionPerformed
  {// GEN-HEADEREND:event_miNewActionPerformed
   // There is bug no calendar choser working and others.. so disabling this menu
   // activity
   // if (JOptionPane.showConfirmDialog(rootPane, "Pozor!\n Aktualne nefunguje,
   // ukonci a spust aplikaci znovu :)\nAle pokud rozumis jave a chtel bys toto
   // opravit budu jen rad\n", "Upozorneni", JOptionPane.OK_CANCEL_OPTION,
   // JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;

    System.out.println("DEBUG: miNewActionPerformed - creating new TransactionSet");
    transactions = new TransactionSet();
    System.out.println("DEBUG: New TransactionSet rows.size() = " + transactions.rows.size());

    // Re-add the TableModelListener for automatic status bar updates
    transactions.addTableModelListener(new javax.swing.event.TableModelListener() {
      @Override
      public void tableChanged(javax.swing.event.TableModelEvent e) {
        System.out.println("DEBUG: TableModelEvent received, type: " + e.getType());
        updateStatusBar();
      }
    });

    table.setModel(transactions);
    System.out.println("DEBUG: Table model set, calling initTableColumns");
    // Call mainwindow table initialization - helpers, column setting
    initTableColumns();

    // Force immediate status bar update for initial empty state
    System.out.println("DEBUG: Forcing status bar update after Nový");
    updateStatusBar();

    // Clear results of computing
    computeWindow.clearComputeResults();

    // Clear highlights explicitly (fresh dataset)
    transactions.clearHighlights();
    table.repaint();

    // Undo import state cleared
    enableUndoImportIfAvailable();
    // Clear filter
    clearFilter();

    currentFile = null;
    currentFileImportState = null;
    updateTitle();

    // if (JOptionPane.showConfirmDialog(rootPane, "Pozor!\n Aktualne nefunguje,
    // ukonci a spust aplikaci znovu :)\nAle pokud rozumis jave a chtel bys toto
    // opravit budu jen rad\n", "Upozorneni", JOptionPane.OK_CANCEL_OPTION,
    // JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;

  }// GEN-LAST:event_miNewActionPerformed

  private void miExportActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_miExportActionPerformed
    FileDialog dialog = new FileDialog(this, "Exportovat do CSV", FileDialog.SAVE);
    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName != null) {
      try {
        // Add .csv to file if it has no extension
        File file = new File(dialog.getDirectory(), fileName);
        if (file.getName().indexOf('.') < 0)
          file = new File(file.getParent(), file.getName() + ".csv");

        if (file.exists()) {
          // Ask whether to overwrite
          if (JOptionPane.showConfirmDialog(this, "Soubor " + file.toString() + " existuje. Chcete jej přepsat?",
              "Soubor existuje", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;
        }

        transactions.export(file);
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Při ukládání nastala chyba:" + e);
      }
    }
  }// GEN-LAST:event_miExportActionPerformed

  private void miAboutActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miAboutActionPerformed
  {// GEN-HEADEREND:event_miAboutActionPerformed
   // Ahow about dialog
    aboutWindow.setVisible(true);
  }// GEN-LAST:event_miAboutActionPerformed

  private void formWindowOpened(java.awt.event.WindowEvent evt)// GEN-FIRST:event_formWindowOpened
  {// GEN-HEADEREND:event_formWindowOpened
   // Show about dialog
    if (cz.datesoft.stockAccounting.Settings.getShowAboutOnStartup()) {
      aboutWindow.setVisible(true);
    }
  }// GEN-LAST:event_formWindowOpened

  private void miReportActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miReportActionPerformed
  {// GEN-HEADEREND:event_miReportActionPerformed
    computeWindow.setVisible(true);
  }// GEN-LAST:event_miReportActionPerformed

  private void miAccountStateActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miAccountStateActionPerformed
  {// GEN-HEADEREND:event_miAccountStateActionPerformed
   // Show account state window
    accountStateWindow.showDialog();
  }// GEN-LAST:event_miAccountStateActionPerformed

  private void miImportActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_miImportActionPerformed
    // Open ImportWindow directly; it is the single source of truth
    // for format selection and file selection.

    Date startDate = transactions.getMaxDate();
    if (startDate != null) {
      // Add a day to start importing next day we have
      GregorianCalendar cal = new GregorianCalendar();
      cal.setTime(startDate);
      cal.add(GregorianCalendar.DAY_OF_MONTH, 1);
      startDate = cal.getTime();
    }

    int savedFormatIndex = cz.datesoft.stockAccounting.Settings.getLastImportFormat();
    importWindow.startImport(null, startDate, savedFormatIndex);
  }// GEN-LAST:event_miImportActionPerformed

  private void bDeleteActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bDeleteActionPerformed
  {// GEN-HEADEREND:event_bDeleteActionPerformed
    int[] selectedRows = table.getSelectedRows();
    if (selectedRows == null || selectedRows.length == 0) {
      JOptionPane.showMessageDialog(this, "Nejsou vybrané žádné řádky.", "Smazat", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    int deleted = transactions.deleteRows(selectedRows);

    // Reset selection/editing
    table.clearSelection();

    if (deleted > 0) {
      bUndoDelete.setEnabled(true);
      lastStatusMessage = " | Smazáno: " + deleted;
      updateStatusBar();
    }
  }// GEN-LAST:event_bDeleteActionPerformed

  private void bUndoDeleteActionPerformed(java.awt.event.ActionEvent evt) {
    int restored = transactions.undoLastDelete();
    if (restored <= 0) {
      bUndoDelete.setEnabled(false);
      JOptionPane.showMessageDialog(this, "Není co vrátit.", "Zpět", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    clearAllFiltersAndInputs();
    bUndoDelete.setEnabled(false);
    lastStatusMessage = " | Obnoveno: " + restored;
    updateStatusBar();
  }

  private void bUndoImportActionPerformed(java.awt.event.ActionEvent evt) {
    if (transactions == null || !transactions.hasUndoImport()) {
      bUndoImport.setEnabled(false);
      JOptionPane.showMessageDialog(this, "Není co vrátit.", "Zpět import", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    if (JOptionPane.showConfirmDialog(this,
        "Opravdu chcete vrátit poslední import?\n\nVrátí vložené řádky i aktualizace duplikátů.",
        "Zpět import", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
      return;
    }

    int changed = transactions.undoLastImport();
    if (changed <= 0) {
      bUndoImport.setEnabled(false);
      JOptionPane.showMessageDialog(this, "Není co vrátit.", "Zpět import", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    clearAllFiltersAndInputs();
    bUndoImport.setEnabled(false);
    lastStatusMessage = " | Import vrácen: " + changed;
    updateStatusBar();
  }

  private void bClearTxnIdActionPerformed(java.awt.event.ActionEvent evt) {
    int[] selected = table.getSelectedRows();
    if (selected == null || selected.length == 0) {
      JOptionPane.showMessageDialog(this, "Nejsou vybrané žádné řádky.", "Smazat TxnID",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    if (JOptionPane.showConfirmDialog(this,
        "Opravdu chcete odstranit Broker/ID účtu/ID transakce z vybraných řádků?\n\n" +
            "Odstraní se i z poznámky (Note). To ovlivní párování při re-importu.",
        "Smazat TxnID", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
      return;
    }

    int changed = 0;
    try {
      for (int viewRow : selected) {
        if (viewRow < 0)
          continue;
        Transaction tx = transactions.getRowAt(viewRow);
        if (tx == null)
          continue;
        tx.clearBrokerAccountTxnMetadata();
        changed++;
      }
      if (changed > 0) {
        transactions.modified = true;
        refreshMetadataFilters();
        table.revalidate();
        table.repaint();
        lastStatusMessage = " | Smazáno TxnID: " + changed;
        updateStatusBar();
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Chyba: " + e.getMessage(), "Smazat TxnID", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void clearAllFiltersAndInputs() {
    // Clear model filter + UI controls
    transactions.clearFilter();

    if (tfTicker != null)
      tfTicker.setText("");
    if (tfMarket != null)
      tfMarket.setText("");
    if (tfNote != null)
      tfNote.setText("");
    if (dcFrom != null)
      dcFrom.setDate(null);
    if (dcTo != null)
      dcTo.setDate(null);
    if (cbTypeFilter != null)
      cbTypeFilter.setSelectedIndex(0);
    if (cbBrokerFilter != null)
      cbBrokerFilter.setSelectedIndex(0);
    if (cbAccountIdFilter != null)
      cbAccountIdFilter.setSelectedIndex(0);
    if (cbEffectFilter != null)
      cbEffectFilter.setSelectedIndex(0);

    // Disable filter-related actions
    if (bClearFilter != null)
      bClearFilter.setEnabled(false);
    if (miSaveFiltered != null)
      miSaveFiltered.setEnabled(false);

    // Track current ticker filter for status bar
    currentTickerFilter = null;

    table.revalidate();
    table.repaint();
  }

  private void bSortActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bSortActionPerformed
  {// GEN-HEADEREND:event_bSortActionPerformed
   // Sort rows
    transactions.sort();
  }// GEN-LAST:event_bSortActionPerformed

  /**
   * Save transactions to this file, smart-handle errors
   *
   * @return Whether action was sucessful.
   */
  private boolean saveTransactions(File fl) {
    try {
      transactions.save(fl);

      // Save Trading 212 import state to sidecar file if exists
      if (currentFileImportState != null && currentFile != null) {
        try {
          currentFileImportState.saveToFile(currentFile);
          System.out.println("Saved Trading 212 import state to .t212state file: " + currentFile.getAbsolutePath());
        } catch (Exception e) {
          System.err.println("Failed to save Trading 212 import state: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this, "Při ukládání souboru nastala chyba: " + e);

      return false;
    }

    return true;
  }

  /**
   * Save transactions to different file.
   *
   * @return Whether action was successful
   */
  private boolean saveAsTransactions() {
    FileDialog dialog = new FileDialog(this, "Uložit soubor", FileDialog.SAVE);

    String loc = Settings.getDataDirectory();
    if (loc != null)
      dialog.setDirectory(loc);

    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName != null) {
      File file = new File(dialog.getDirectory(), fileName);

      // If saving to a different file, reset currentFile and import state
      if (!file.equals(currentFile)) {
        currentFile = file;
        currentFileImportState = null;
        System.out.println("Saved to different file, reset currentFileImportState");
      }

      if (file.exists()) {
        if (JOptionPane.showConfirmDialog(this,
            "Soubor " + file.getAbsolutePath() + " již existuje, chcete jej přepsat?", "Přepsat soubor?",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
          return false;
      }

      Settings.setDataDirectory(dialog.getDirectory());
      Settings.save();

      boolean success = saveTransactions(file);
      if (success) {
        updateTitle();
        Settings.setLastOpenedFile(file.getAbsolutePath());
      }
      return success;
    }

    return false;
  }

  public boolean saveFiltered() {
    FileDialog dialog = new FileDialog(this, "Uložit vyfiltrované transakce", FileDialog.SAVE);

    String loc = Settings.getDataDirectory();
    if (loc != null)
      dialog.setDirectory(loc);

    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName != null) {
      File file = new File(dialog.getDirectory(), fileName);
      if (file.exists()) {
        if (JOptionPane.showConfirmDialog(this,
            "Soubor " + file.getAbsolutePath() + " již existuje, chcete jej přepsat?", "Přepsat soubor?",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
          return false;
      }

      Settings.setDataDirectory(dialog.getDirectory());
      Settings.save();

      try {
        transactions.saveFiltered(file);
      } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this, "Při ukládání souboru nastala chyba: " + e);

        return false;
      }

      return true;
    }

    return false;

  }

  /**
   * Save transactions, do saveAs when file name was not yet established.
   *
   * @return Whether action was successful
   */
  public boolean saveAction() {
    File fl = transactions.getFile();

    if (fl == null)
      return saveAsTransactions();
    else
      return saveTransactions(fl);
  }

  /**
   * Apply filter to the transaction set
   */
  private void applyFilter() {
    // Get ticker and market. Set them to NULL when they are not set.
    String ticker = tfTicker.getText();
    String market = tfMarket.getText();
    String type = (String) cbTypeFilter.getSelectedItem();
    String note = tfNote.getText();
    String broker = (String) cbBrokerFilter.getSelectedItem();
    String accountId = (String) cbAccountIdFilter.getSelectedItem();
    String effect = (String) cbEffectFilter.getSelectedItem();

    if (ticker.length() == 0)
      ticker = null;
    if (market.length() == 0)
      market = null;
    if (type != null && type.length() == 0)
      type = null;
    if (note.length() == 0)
      note = null;
    if (broker != null && broker.length() == 0)
      broker = null;
    if (accountId != null && accountId.length() == 0)
      accountId = null;
    if (effect != null && effect.length() == 0)
      effect = null;

    // Track current ticker filter for status bar display
    currentTickerFilter = ticker;

    // Apply filter
    transactions.applyFilter(dcFrom.getDate(), dcTo.getDate(), ticker, market, type, note, broker, accountId, effect);

    // Enable clear filter button and save filtered
    bClearFilter.setEnabled(true);
    miSaveFiltered.setEnabled(true);
  }

  public void clearFilter() {
    transactions.clearFilter();
    bClearFilter.setEnabled(false);
    miSaveFiltered.setEnabled(false);
    cbTypeFilter.setSelectedIndex(0); // Reset to empty selection
    cbBrokerFilter.setSelectedIndex(0); // Reset to empty option
    cbAccountIdFilter.setSelectedIndex(0); // Reset to empty option
    cbEffectFilter.setSelectedIndex(0);
  }

  private void miSaveAsActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miSaveAsActionPerformed
  {// GEN-HEADEREND:event_miSaveAsActionPerformed
    saveAsTransactions();
  }// GEN-LAST:event_miSaveAsActionPerformed

  private void miSaveActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miSaveActionPerformed
  {// GEN-HEADEREND:event_miSaveActionPerformed
    saveAction();
  }// GEN-LAST:event_miSaveActionPerformed

  private void miSettingsActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miSettingsActionPerformed
  {// GEN-HEADEREND:event_miSettingsActionPerformed
    Settings.showDialog();
  }// GEN-LAST:event_miSettingsActionPerformed

  private void bApplyFilterActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bApplyFilterActionPerformed
    applyFilter();
  }// GEN-LAST:event_bApplyFilterActionPerformed

  private void bClearFilterActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bClearFilterActionPerformed
    clearFilter();
  }// GEN-LAST:event_bClearFilterActionPerformed

  private void tfTickerKeyPressed(java.awt.event.KeyEvent evt) {// GEN-FIRST:event_tfTickerKeyPressed
    if (evt.getKeyCode() == KeyEvent.VK_ENTER)
      applyFilter();
  }// GEN-LAST:event_tfTickerKeyPressed

  private void tfMarketKeyPressed(java.awt.event.KeyEvent evt) {// GEN-FIRST:event_tfMarketKeyPressed
    if (evt.getKeyCode() == KeyEvent.VK_ENTER)
      applyFilter();
  }// GEN-LAST:event_tfMarketKeyPressed

  private void miSaveFilteredActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_miSaveFilteredActionPerformed
    saveFiltered();
  }// GEN-LAST:event_miSaveFilteredActionPerformed

  private void miOpenActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_miOpenActionPerformed
    // Show open dialog
    FileDialog dialog = new FileDialog(this, "Otevřít soubor", FileDialog.LOAD);

    String loc = Settings.getDataDirectory();
    if (loc != null)
      dialog.setDirectory(loc);

    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName != null) {
      File selectedFile = new File(dialog.getDirectory(), fileName);
      Settings.setDataDirectory(dialog.getDirectory());
      Settings.save();

      try {
        openFile(selectedFile);
      } catch (Exception e) {
        // e.printStackTrace(); // Handled in openFile or caught here?
        // Previous impl printed stack trace here.
        e.printStackTrace();
        JOptionPane.showMessageDialog(this, "Při načítání souboru nastala chyba: " + e);
      }
    }
  }

  public void openFile(File selectedFile) throws Exception {
    // Load file
    transactions.load(selectedFile);

    // Load Trading 212 import state from sidecar file if exists
    currentFile = selectedFile;
    currentFileImportState = Trading212ImportState.loadFromFile(selectedFile);
    if (currentFileImportState != null) {
      System.out
          .println("Loaded Trading 212 import state from .t212state file for: " + selectedFile.getAbsolutePath());
    }

    // Initialize date range to show all loaded data (1900-01-01 to today)
    java.util.GregorianCalendar startCal = new java.util.GregorianCalendar(1900, 0, 1); // 1900-01-01
    dcFrom.setDate(startCal.getTime());
    dcTo.setDate(new java.util.Date()); // Today

    // Invalidate transformation cache for loaded data
    System.out.println("DEBUG: Invalidating transformation cache after loading .dat file");
    transactions.invalidateTransformationCache();

    // Refresh metadata filter dropdowns with loaded data
    refreshMetadataFilters();

    // Clear results of computing to avoid confusion
    computeWindow.clearComputeResults();

    // Clear inserted/updated highlights from previous session
    transactions.clearHighlights();
    table.repaint();

    if (transactions.wereSerialsRepaired()) {
      setTransientStatusMessage(
          "Oprava: přegenerovány interní serialy (duplicit: " + transactions.getSerialDuplicatesFound() + ")",
          12000L);
    }

    // Clear filter
    clearFilter();

    updateTitle();

    // Save as last opened file
    Settings.setLastOpenedFile(selectedFile.getAbsolutePath());
  }

  private void tfTickerActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tfTickerActionPerformed
    // TODO add your handling code here:
  }// GEN-LAST:event_tfTickerActionPerformed

  private void tfNoteKeyPressed(java.awt.event.KeyEvent evt) {// GEN-FIRST:event_tfNoteKeyPressed
    if (evt.getKeyCode() == KeyEvent.VK_ENTER)
      applyFilter();
  }// GEN-LAST:event_tfNoteKeyPressed

  private void tfMarketActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tfMarketActionPerformed
    // TODO add your handling code here:
  }// GEN-LAST:event_tfMarketActionPerformed

  private void miExportFIOActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_miExportFIOActionPerformed
    if (JOptionPane.showConfirmDialog(rootPane,
        "Pozor!\n Exportuji se pouze obchody typu Cenny Papir a veskere transformace split,reverse split zatim filtrovane\n\nPo exportu muzete porovnat s vystupy na http://kacka.baldsoft.com/\nBohuzel frakcni akcie FIO format nepodporuje",
        "Limitovany export", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
      return;

    FileDialog dialog = new FileDialog(this, "Exportovat do FIO CSV formatu - lze pouzit na kacka.baldsoft.com",
        FileDialog.SAVE);
    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName != null) {
      try {
        // Add .csv to file if it has no extension
        File file = new File(dialog.getDirectory(), fileName);
        if (file.getName().indexOf('.') < 0)
          file = new File(file.getParent(), file.getName() + ".csv");

        if (file.exists()) {
          // Ask whether to overwrite
          if (JOptionPane.showConfirmDialog(this, "Soubor " + file.toString() + " existuje. Chcete jej přepsat?",
              "Soubor existuje", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;
        }

        transactions.exportFIO(file);

        // Convert just created exportFIO from utf-8 to Windows-1250
        FileInputStream input = new FileInputStream(file);
        InputStreamReader reader = new InputStreamReader(input, "utf-8");
        // create temporary file
        File destinationFile = File.createTempFile("temp", ".csv");
        // System.out.println(destinationFile.getAbsolutePath());

        FileOutputStream output = new FileOutputStream(destinationFile);
        OutputStreamWriter writer = new OutputStreamWriter(output, "Windows-1250");

        int read = reader.read();
        while (read != -1) {
          writer.write(read);
          read = reader.read();
        }
        reader.close();
        writer.close();
        // Move temporary converted file as original
        Files.move(Paths.get(destinationFile.toString()), Paths.get(file.toString()),
            StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Při ukládání nastala chyba:" + e);
      }
    }
  }// GEN-LAST:event_miExportFIOActionPerformed

  private void tfNoteActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tfNoteActionPerformed
    // TODO add your handling code here:
  }// GEN-LAST:event_tfNoteActionPerformed

  private void miOpenAddActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_miOpenAddActionPerformed

    // Show open dialog
    FileDialog dialog = new FileDialog(this, "Otevřít soubor", FileDialog.LOAD);

    String loc = Settings.getDataDirectory();
    if (loc != null)
      dialog.setDirectory(loc);

    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName != null) {
      File selectedFile = new File(dialog.getDirectory(), fileName);
      Settings.setDataDirectory(dialog.getDirectory());
      Settings.save();

      try {
        // Load file
        transactions.loadAdd(selectedFile);

        // Refresh metadata filter dropdowns with loaded data
        refreshMetadataFilters();

        // Clear results of computing to avoid confusion
        computeWindow.clearComputeResults();

        // Clear inserted/updated highlights from previous session
        transactions.clearHighlights();
        table.repaint();

        if (transactions.wereSerialsRepaired()) {
          setTransientStatusMessage(
              "Oprava: přegenerovány interní serialy (duplicit: " + transactions.getSerialDuplicatesFound() + ")",
              12000L);
        }

        // Clear filter
        clearFilter();

        updateTitle();
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Při načítání souboru nastala chyba: " + e);
      }
    }
  }// GEN-LAST:event_miOpenAddActionPerformed

  /**
   * Refresh currencies combo
   */
  public void refreshCurrenciesCombo() {
    String[] currs = Settings.getCurrencies();
    DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) cbCurrencies.getModel();

    model.removeAllElements();
    java.util.Arrays.sort(currs); // Sort currencies
    boolean czkEntered = false;
    for (int i = 0; i < currs.length; i++) {
      String curr = currs[i];
      if ((!czkEntered) && (curr.compareTo("CZK") >= 0)) {
        model.addElement("CZK");
        czkEntered = true;
      }

      model.addElement(curr);
    }
    if (!czkEntered)
      model.addElement("CZK");

  }

  /**
   * Return transaction database
   */
  public TransactionSet getTransactionDatabase() {
    return transactions;
  }

  /**
   * Get current file for .t212state tracking
   */
  public File getCurrentFile() {
    return currentFile;
  }

  /**
   * Get current Trading 212 import state for this file
   */
  public Trading212ImportState getCurrentFileImportState() {
    return currentFileImportState;
  }

  /**
   * Set current Trading 212 import state for this file
   */
  public void setCurrentFileImportState(Trading212ImportState state) {
    this.currentFileImportState = state;
  }

  /**
   * Update status bar with current statistics
   */
  private void updateStatusBar() {
    TransactionSet db = getTransactionDatabase();
    int totalRecords = db.rows.size(); // Actual transaction count (excludes empty row)

    // For filtered count, check if filtering is active and exclude empty row
    int visibleRecords;
    if (db.filteredRows != null) {
      visibleRecords = db.filteredRows.size(); // Actual filtered transactions
    } else {
      visibleRecords = db.rows.size(); // All transactions when no filter
    }

    String status = "Záznamů: " + totalRecords;
    if (visibleRecords != totalRecords) {
      status += " | Filtr: " + visibleRecords;

      // Show related tickers if smart filtering was used
      if (currentTickerFilter != null && !currentTickerFilter.isEmpty()) {
        try {
          Set<String> relatedTickers = db.getRelatedTickers(currentTickerFilter);
          if (relatedTickers.size() > 1) {
            // Sort for consistent display
            java.util.List<String> sortedRelated = new java.util.ArrayList<>(relatedTickers);
            java.util.Collections.sort(sortedRelated);
            status += " | Zahrnuje: " + String.join(", ", sortedRelated);
          }
        } catch (Exception e) {
          // Ignore errors in status bar display
          System.err.println("Error getting related tickers for status bar: " + e.getMessage());
        }
      }
    }

    if (lastStatusMessage != null && !lastStatusMessage.isEmpty()) {
      status += lastStatusMessage;
      logEvent(lastStatusMessage.replaceFirst("^ \\| ", "")); // Remove separator if present
      lastStatusMessage = null;
    }

    long now = System.currentTimeMillis();
    if (transientStatusMessage != null && !transientStatusMessage.isEmpty() && now <= transientStatusUntilMs) {
      // Put transient message first so it isn't hidden by label truncation.
      status = transientStatusMessage + " | " + status;
      jLabel1.setToolTipText(transientStatusMessage);
    } else {
      transientStatusMessage = null;
      transientStatusUntilMs = 0L;
      jLabel1.setToolTipText(null);
    }

    if (dpStatusWarning != null && !dpStatusWarning.isEmpty()) {
      status += dpStatusWarning;
      jLabel1.setForeground(statusColorWarning);
    } else {
      jLabel1.setForeground(statusColorDefault);
    }

    jLabel1.setText(status);
  }

  /**
   * List of event logs
   */
  private final java.util.List<String> eventLogs = new java.util.ArrayList<>();

  /**
   * Log an event with timestamp
   */
  private void logEvent(String message) {
    if (message == null || message.trim().isEmpty())
      return;
    String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    eventLogs.add("[" + timestamp + "] " + message);
  }

  /**
   * Show logs dialog
   */
  private void miShowLogsActionPerformed(java.awt.event.ActionEvent evt) {
    // 2x wider (was 60, now 120)
    javax.swing.JTextArea textArea = new javax.swing.JTextArea(20, 120);
    textArea.setEditable(false);
    textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

    StringBuilder sb = new StringBuilder();
    synchronized (eventLogs) {
      for (String log : eventLogs) {
        sb.append(log).append("\n");
      }
    }

    if (sb.length() == 0) {
      sb.append("Žádné logy k zobrazení.");
    }

    textArea.setText(sb.toString());
    textArea.setCaretPosition(textArea.getDocument().getLength());

    javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(textArea);

    // Create resizable dialog instead of simple message dialog
    javax.swing.JOptionPane pane = new javax.swing.JOptionPane(scrollPane, javax.swing.JOptionPane.PLAIN_MESSAGE);
    javax.swing.JDialog dialog = pane.createDialog(this, "Logy událostí");
    dialog.setResizable(true);
    dialog.setVisible(true);
  }

  /**
   * Update window title based on current file
   */
  private void updateTitle() {
    File fl = transactions.getFile();
    if (fl != null) {
      setTitle("Akciové účetnictví - " + fl.getName());
    } else {
      setTitle("Akciové účetnictví");
    }
  }

  public void setTransientStatusMessage(String msg, long ttlMs) {
    if (msg == null)
      return;
    logEvent(msg);
    transientStatusMessage = msg;
    transientStatusUntilMs = System.currentTimeMillis() + Math.max(0L, ttlMs);
    System.out.println("INFO: " + msg);
    updateStatusBar();
  }

  /**
   * Refresh the main table display
   */
  public void refreshTable() {
    table.revalidate();
    table.repaint();
  }

  public void jumpToFirstImportChangeInView() {
    if (transactions == null)
      return;

    int row = transactions.findFirstUpdatedVisibleRowIndex();
    final boolean updated;
    if (row < 0) {
      row = transactions.findFirstInsertedVisibleRowIndex();
      updated = false;
    } else {
      updated = true;
    }
    if (row < 0) {
      return;
    }

    // If filter is active but row isn't visible, skip and notify.
    // (Our find*VisibleRowIndex() already scans visible rows only.)
    if (transactions.filteredRows != null && row >= transactions.filteredRows.size()) {
      setTransientStatusMessage("Skok přeskočen (řádek není ve filtru vidět)", 8000L);
      return;
    }

    final int viewRow = row;
    javax.swing.SwingUtilities.invokeLater(() -> {
      try {
        if (viewRow < 0 || viewRow >= table.getRowCount()) {
          setTransientStatusMessage("Skok přeskočen (řádek není ve filtru vidět)", 8000L);
          return;
        }

        table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        java.awt.Rectangle rect = table.getCellRect(viewRow, 0, true);
        // Center the row in the visible viewport when possible.
        try {
          java.awt.Container parent = table.getParent();
          if (parent instanceof javax.swing.JViewport) {
            javax.swing.JViewport vp = (javax.swing.JViewport) parent;
            int viewHeight = vp.getExtentSize().height;
            java.awt.Rectangle target = new java.awt.Rectangle(rect);
            target.y = Math.max(0, rect.y - (viewHeight / 2) + (rect.height / 2));
            target.height = viewHeight;
            table.scrollRectToVisible(target);
          } else {
            table.scrollRectToVisible(rect);
          }
        } catch (Exception e) {
          table.scrollRectToVisible(rect);
        }

        flashSelectionRow(viewRow);

        setTransientStatusMessage(updated ? "Skok na aktualizovaný řádek" : "Skok na nový řádek", 4000L);
      } catch (Exception e) {
        // ignore
      }
    });
  }

  private void flashSelectionRow(int viewRow) {
    if (flashTimer != null) {
      try {
        flashTimer.stop();
      } catch (Exception e) {
      }
      flashTimer = null;
    }

    flashRowIndex = viewRow;
    final int[] ticks = new int[] { 0 };
    flashTimer = new javax.swing.Timer(180, ev -> {
      ticks[0]++;
      if (ticks[0] % 2 == 0) {
        table.clearSelection();
      } else {
        if (flashRowIndex != null && flashRowIndex >= 0 && flashRowIndex < table.getRowCount()) {
          table.getSelectionModel().setSelectionInterval(flashRowIndex, flashRowIndex);
        }
      }
      table.repaint();
      if (ticks[0] >= 7) {
        ((javax.swing.Timer) ev.getSource()).stop();
        flashTimer = null;
        // Leave row selected at the end
        if (flashRowIndex != null && flashRowIndex >= 0 && flashRowIndex < table.getRowCount()) {
          table.getSelectionModel().setSelectionInterval(flashRowIndex, flashRowIndex);
        }
        flashRowIndex = null;
      }
    });
    flashTimer.setRepeats(true);
    flashTimer.start();
  }

  /** Current ticker filter for status bar display */
  private String currentTickerFilter = null;

  /** One-shot status bar message appended once */
  private String lastStatusMessage = null;

  // Transient status bar message (kept for a short time)
  private String transientStatusMessage = null;
  private long transientStatusUntilMs = 0L;

  // Flash updated/inserted row after import
  private Integer flashRowIndex = null;
  private javax.swing.Timer flashTimer = null;

  // Persistent DP warning shown in status bar
  private String dpStatusWarning = null;
  private int dpStatusWarningYear = 0;
  private int dpStatusWarningCount = 0;
  private final java.awt.Color statusColorDefault = java.awt.Color.BLACK;
  private final java.awt.Color statusColorWarning = new java.awt.Color(180, 0, 0);

  public void setDpYearEndSettlementWarning(int year, int count) {
    dpStatusWarningYear = year;
    dpStatusWarningCount = count;
    if (count > 0) {
      dpStatusWarning = " | DP " + year + ": vypořádání=datum (" + count + ")";
    } else {
      dpStatusWarning = null;
    }
    updateStatusBar();
  }

  public void enableUndoImportIfAvailable() {
    try {
      if (bUndoImport != null && transactions != null) {
        bUndoImport.setEnabled(transactions.hasUndoImport());
      }
    } catch (Exception e) {
      // ignore
    }
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bApplyFilter;
  private javax.swing.JButton bClearFilter;
  private javax.swing.JButton bDelete;
  private javax.swing.JButton bUndoDelete;
  private javax.swing.JButton bUndoImport;
  private javax.swing.JButton bClearTxnId;
  private javax.swing.JButton bSort;
  private javax.swing.JComboBox cbCurrencies;
  private javax.swing.JComboBox cbDirection;
  private javax.swing.JComboBox cbMarkets;
  private javax.swing.JComboBox cbTickers;
  private javax.swing.JComboBox cbType;
  private javax.swing.JComboBox cbTypeFilter;
  private com.toedter.calendar.JDateChooser dcFrom;
  private com.toedter.calendar.JDateChooser dcTo;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel4;
  private javax.swing.JLabel jLabel5;
  private javax.swing.JLabel jLabel6;
  private javax.swing.JLabel jLabel7;
  private javax.swing.JLabel jLabel8;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JLabel jLabel10;
  private javax.swing.JMenu jMenu1;
  private javax.swing.JMenu jMenu2;
  private javax.swing.JMenu jMenu3;
  private javax.swing.JMenuBar jMenuBar1;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JSeparator jSeparator3;
  private javax.swing.JMenuItem miAbout;
  private javax.swing.JMenuItem miShowLogs;
  private javax.swing.JMenuItem miAccountState;
  private javax.swing.JMenuItem miExit;
  private javax.swing.JMenuItem miExport;
  private javax.swing.JMenuItem miExportFIO;
  private javax.swing.JMenuItem miImport;
  private javax.swing.JMenuItem miNew;
  private javax.swing.JMenuItem miOpen;
  private javax.swing.JMenuItem miOpenAdd;
  private javax.swing.JMenuItem miReport;
  private javax.swing.JMenuItem miSave;
  private javax.swing.JMenuItem miSaveAs;
  private javax.swing.JMenuItem miSaveFiltered;
  private javax.swing.JMenuItem miSettings;
  private javax.swing.JTable table;
  private javax.swing.JTextField tfMarket;
  private javax.swing.JTextField tfNote;
  private javax.swing.JTextField tfTicker;
  private javax.swing.JComboBox cbBrokerFilter;
  private javax.swing.JComboBox cbAccountIdFilter;
  private javax.swing.JComboBox cbEffectFilter;
  private javax.swing.JCheckBox cbShowMetadata;
  private javax.swing.JCheckBox cbShowSeconds;
  private javax.swing.JButton bCopy;
  private javax.swing.JButton bClearColors;
  // End of variables declaration//GEN-END:variables

}
