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

  // Trading 212 specific components
  javax.swing.JComboBox<String> cbTrading212Year;

  // Trading 212 import state
  private Trading212ImportState importState;

  /** Creates new form ImportWindow */
  public ImportWindow(java.awt.Frame parent, boolean modal) {
    super("Import souboru");
    initComponents();

    mainWindow = (MainWindow) parent;

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

    startDate.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
      public void propertyChange(java.beans.PropertyChangeEvent evt) {
        loadImport();
      }
    });

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
    table.setModel(transactions);

    table.getColumnModel().getColumn(0).setPreferredWidth(200);
    table.getColumnModel().getColumn(0).setCellRenderer(new CZDateRenderer());

    table.getColumnModel().getColumn(10).setCellRenderer(new CZDateRenderer());

    // niTable.setTableHeader(new JTableHeader());
    updateWindowTitle(); // Set initial window title
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
    // Clear not imported rows
    DefaultTableModel model = (DefaultTableModel) niTable.getModel();
    model.setNumRows(0);

    try {
      Vector<String[]> notImported = new Vector<String[]>();

      if (cbFormat.getSelectedIndex() == 0)
        return; // Bad format

      if (isTrading212Format()) {
        // For API imports, don't auto-fetch on date changes
        // User must explicitly click Import button after selecting year
        return;
      }

      // File-based import logic
      if (currentFile == null)
        return; // No file for file-based import

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

        int formatIndex = cbFormat.getSelectedIndex();
        System.out.println("[FORMAT:004] About to call importFile with formatIndex=" + formatIndex + " for file: " + currentFile.getName());
        transactions.importFile(currentFile, startD, endD, formatIndex, notImported);

       // Filter duplicate transactions that already exist in main database
       System.out.println("[DUPLICATE:001] Checking for duplicates in file import against main database");
       Vector<Transaction> originalTransactions = new Vector<>(transactions.rows); // Copy before filtering
       Vector<Transaction> filteredTransactions = mainWindow.getTransactionDatabase().filterDuplicates(originalTransactions);
       int duplicatesFiltered = originalTransactions.size() - filteredTransactions.size();

       // Replace preview table with filtered transactions
       if (duplicatesFiltered > 0) {
         transactions.rows.clear();
         transactions.rows.addAll(filteredTransactions);
         transactions.fireTableDataChanged();
         System.out.println("[DUPLICATE:002] Filtered " + duplicatesFiltered + " duplicates from file import");
       }

       // Set labels
       int n = transactions.getRowCount();
       String previewText = "Náhled (" + n + " " + getRecordsWord(n) + ")";
       if (duplicatesFiltered > 0) {
         previewText += " - " + duplicatesFiltered + " duplikátů vyfiltrováno";
       }
       previewText += ":";
       lPreview.setText(previewText);
      int rowCount = notImported.size();
      lUnimported.setText("Neimportované řádky (" + rowCount + " " + getRecordsWord(rowCount) + "):");

      /* Fill in data model for not imported rows */

      // Get number of columns
      int colCount = 0;
      for (int i = 0; i < rowCount; i++) {
        n = notImported.get(i).length;
        if (n > colCount)
          colCount = n;
      }

      if (rowCount > 0) {
        model.setRowCount(rowCount);
        model.setColumnCount(colCount);

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
            model.setValueAt(null, i, n);
          }
        }
      } else {
        model.setRowCount(0);
      }
    } catch (java.io.FileNotFoundException e) {
      JOptionPane.showMessageDialog(this, "Soubor nenalezen!");
    } catch (java.io.IOException e) {
      JOptionPane.showMessageDialog(this, "Chyba čtení: " + e.getLocalizedMessage());
    } catch (cz.datesoft.stockAccounting.imp.ImportException e) {
      JOptionPane.showMessageDialog(this, "Chyba při importu: " + e.getMessage());
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
    currentFile = file;

    System.out.println("[FORMAT:001] startImport called with preselectedFormat=" + preselectedFormat + ", file=" + (file != null ? file.getName() : "null"));

    // Restore last selected format from settings (unless preselected format is specified)
    if (preselectedFormat == 0) {
      int savedFormat = cz.datesoft.stockAccounting.Settings.getLastImportFormat();
      if (savedFormat > 0 && savedFormat < cbFormat.getModel().getSize()) {
        cbFormat.setSelectedIndex(savedFormat);
        updateUiForFormat(savedFormat);
      }
    }

    // Set dates for file-based imports
    if (startDateValue != null) {
      startDate.setDate(startDateValue);
    }
    endDate.setDate(null);

    // FORCE reset format selection to ensure UI state matches import request
    if (preselectedFormat > 0) {
      System.out.println("[FORMAT:002] Force setting cbFormat to index " + preselectedFormat);
      cbFormat.setSelectedIndex(preselectedFormat);
      updateUiForFormat(preselectedFormat);
      updateWindowTitle();
      System.out.println("[FORMAT:003] UI state reset complete, cbFormat.getSelectedIndex()=" + cbFormat.getSelectedIndex());
    }

    if (cbFormat.getSelectedIndex() != 0 && currentFile != null) {
      loadImport(); // Only load if we have a file for file-based imports
    }

    setVisible(true);
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
        "BrokerJet - HTML export (legacy)", "IB - TradeLog", "IB - FlexQuery Trades only CSV",
        "T212 Invest  - csv  mena: USD", "T212 Invest  - csv  mena: CZK", "Revolut - csv", "Trading 212 API" }));
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
    gridBagConstraints.gridy = 3;
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
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(lPreview, gridBagConstraints);

    lUnimported.setText("Neimportované řádky (0 záznamů):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 4;
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
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    getContentPane().add(niScrollPane, gridBagConstraints);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private boolean isTrading212Format() {
    return cbFormat.getSelectedIndex() == 8; // Trading 212 API is index 8
  }

  private void setupTrading212YearSelection() {
    if (cbTrading212Year == null) {
      cbTrading212Year = new javax.swing.JComboBox<>();
      bClearPreview = new javax.swing.JButton("Vymazat náhled");
      lCacheStatus = new javax.swing.JLabel("Cache: None");

      populateTrading212YearDropdown();

      // Add selection listener to update cache status
      cbTrading212Year.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          updateCacheStatus();
        }
      });

      // Add action listeners for buttons
      bClearPreview.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          clearPreview();
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

      // Add status label on next row
      gbc.gridx = 0;
      gbc.gridy = 13;
      gbc.gridwidth = 3;
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
    if (lCacheStatus != null) {
      lCacheStatus.setVisible(false);
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
    System.out.println("[VALIDATE:001] performTrading212Import called with mergeMode=" + mergeMode);

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

            // Invalidate transformation cache after import (new transactions may have transformations)
            System.out.println("DEBUG: Invalidating transformation cache after API import");
            mainWindow.getTransactionDatabase().invalidateTransformationCache();

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
            javax.swing.JOptionPane.showMessageDialog(mainWindow,
                "Úspěšně importováno " + transactionsAdded + " transakcí!\n\n" +
                    "Metoda importu: CSV Report (komplexní data včetně objednávek, dividend a úroků)\n\n" +
                    "Nyní můžete importovat další rok nebo zavřít toto okno.",
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

             System.out.println("[DUPLICATE:002] Filtered " + duplicatesFiltered + " duplicates, adding " + filteredTransactions.size() + " new transactions to preview table");
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
               previewText += " - " + duplicatesFiltered + " duplikátů vyfiltrováno";
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
    updateClearButtonState();
  }

  /**
   * Clear preview data manually
   */
  private void clearPreview() {
    System.out.println("[CLEAR:001] Clearing preview data manually");
    transactions.clear();
    lPreview.setText("Náhled (0 záznamů):");
    lUnimported.setText("Neimportované řádky (0 záznamů):");
    updateImportButtonText();
    updateClearButtonState();
    System.out.println("[CLEAR:002] Preview cleared successfully");
  }

  /**
   * Update UI components visibility based on selected format
   */
   private void updateUiForFormat(int formatIndex) {
    boolean isApiFormat = (formatIndex == 8); // Trading 212 API

    // Clear preview when switching to API format to prevent data contamination from previous imports
    if (isApiFormat && transactions.getRowCount() > 0) {
      System.out.println("[FORMAT:001] Clearing preview when switching to API format");
      clearPreview();
    }

    // Hide date selection UI for API formats (we use year dropdown instead)
    jLabel1.setVisible(!isApiFormat); // "Importovat od:"
    jLabel2.setVisible(!isApiFormat); // "do:"
    startDate.setVisible(!isApiFormat);
    endDate.setVisible(!isApiFormat);

    // Keep preview tables visible for API formats - they show fetched data
    lPreview.setVisible(true);
    jScrollPane1.setVisible(true);
    lUnimported.setVisible(true);
    niScrollPane.setVisible(true);

    // Hide refresh button for API (not needed - user clicks Import to fetch)
    bRefresh.setVisible(!isApiFormat);

    // Show/hide API-specific UI
    if (isApiFormat) {
      setupTrading212YearSelection();
    } else {
      hideTrading212YearSelection();
    }

    // Update button text based on current state
    updateImportButtonText();

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
    } else {
      setTitle("Import souboru");
    }
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
    if (importState.isYearFullyImported(year)) {
      return "(Imported)";
    } else if (importState.getLastImportDate(year) != null) {
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
      if (isTrading212Format()) {
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

        // Invalidate transformation cache after import
        System.out.println("DEBUG: Invalidating transformation cache after file import");
        mainWindow.getTransactionDatabase().invalidateTransformationCache();

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
    loadImport();
  }// GEN-LAST:event_bRefreshActionPerformed

  private void bCancelActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bCancelActionPerformed
  {// GEN-HEADEREND:event_bCancelActionPerformed
    dispose();
  }// GEN-LAST:event_bCancelActionPerformed

  private void cbFormatActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_cbFormatActionPerformed

    if (cbFormat.getSelectedIndex() != 0) {
      updateUiForFormat(cbFormat.getSelectedIndex());
      updateWindowTitle(); // Update window title based on format

      if (isTrading212Format()) {
        // For API format, just show the year selection UI
        // Don't auto-fetch - wait for user to select year and click Import button
       } else {
         loadImport(); // For file-based formats
       }
     }

     // Save the selected format for persistence across app restarts
     cz.datesoft.stockAccounting.Settings.setLastImportFormat(cbFormat.getSelectedIndex());
     cz.datesoft.stockAccounting.Settings.save();

  }// GEN-LAST:event_cbFormatActionPerformed

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
