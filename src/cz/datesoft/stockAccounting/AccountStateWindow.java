/*
 * AccountStateWindow.java
 *
 * Created on 11. listopad 2006, 14:44
 */

package cz.datesoft.stockAccounting;

//import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.toedter.calendar.JDateChooser;
import java.awt.GridBagConstraints;
import java.awt.Dimension;
import java.awt.Component;
import java.util.GregorianCalendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;
import java.io.File;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JOptionPane;
import java.awt.FileDialog;
import javax.swing.JTable;
//import javax.swing.JLabel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.awt.Color;

/**
 *
 * @author lemming2
 */
public class AccountStateWindow extends javax.swing.JDialog {
  /**
   * Date renderer - render date in DD.MM.YYYY format and in green if older than 6
   * months
   */
  /// <editor-fold defaultstate="collapsed" desc="Class: CustomDateRenderer">
  private class CustomDateRenderer extends DefaultTableCellRenderer {
    /**
     * Formatter
     */
    private SimpleDateFormat _df;

    /**
     * Stocks we are rendering
     */
    private Stocks _stocks;

    /**
     * Constructor
     */
    public CustomDateRenderer(Stocks stocks) {
      _stocks = stocks;
      _df = new SimpleDateFormat("dd.MM.yyyy");
      setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    }

    /**
     *
     * @param table
     * @param value
     * @param isSelected
     * @param hasFocus
     * @param row
     * @param column
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
        int row, int column) {
      if (value instanceof Date) {
        // Check if this is a stock
        String symbol = (String) table.getValueAt(row, 0);
        Stocks.SecType type = _stocks.getSecurityType(symbol);

        // Check if date is over 6m
        if ((type == Stocks.SecType.STOCK) && Stocks.isOverTaxFreeDuration((Date) value, new Date())) {
          setBackground(Color.GREEN);
        } else {
          setBackground(Color.WHITE);
        }
        return super.getTableCellRendererComponent(table, _df.format((Date) value), isSelected, hasFocus, row, column);
      } else
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column); // Fallback

    }
  }
  /// </editor-fold>

  /**
   * Table model with security and amount
   */
  /// <editor-fold defaultstate="collapsed" desc="Class: StateTableModel">
  private class StateTableModel extends DefaultTableModel {

    /**
     * Build model from stocks information
     *
     * @param stocks Stocks data
     */
    public StateTableModel(Stocks stocks) {
      // Get ticker
      String[] tickers = _stocks.getStockTickers();
      java.util.Arrays.sort(tickers); // Sort tickers

      setRowCount(tickers.length);
      for (int i = 0; i < tickers.length; i++) {
        setValueAt(tickers[i], i, 0);
        setValueAt(formatAmount(_stocks.getStockAmount(tickers[i])), i, 1);
      }
    }

    private Object formatAmount(double v) {
      // Display cleanup only: hide floating point artifacts.
      if (Math.abs(v) < 0.000001) {
        return 0;
      }
      double r = Math.rint(v);
      if (Math.abs(v - r) < 0.000001) {
        return (long) r;
      }
      // Show up to 6 decimals for real fractions
      DecimalFormat nf = new DecimalFormat("0.######");
      return nf.format(v);
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public String getColumnName(int col) {
      switch (col) {
        case 0:
          return "Ticker";
        case 1:
          return "Množství";
        case 2:
          return "TWS";
        default:
          return "???";
      }
    }
  }
  /// </editor-fold>

  /// <editor-fold defaultstate="collapsed" desc="Class: CompareCellRenderer">
  private class CompareCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
        int row, int column) {
      Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (isSelected) {
        return c;
      }

      if (_twsPositions == null) {
        c.setBackground(Color.WHITE);
        return c;
      }

      try {
        double local = parseDouble(table.getValueAt(row, 1));
        double tws = parseDouble(table.getValueAt(row, 2));
        if (nearlyEqual(local, tws)) {
          c.setBackground(new Color(200, 255, 200));
        } else {
          c.setBackground(new Color(255, 200, 200));
        }
      } catch (Exception e) {
        c.setBackground(Color.WHITE);
      }
      return c;
    }
  }
  /// </editor-fold>

  private static boolean nearlyEqual(double a, double b) {
    return Math.abs(a - b) < 0.000001;
  }

  private static double parseDouble(Object o) {
    if (o == null) return 0.0;
    if (o instanceof Number) return ((Number) o).doubleValue();
    try {
      String s = o.toString().trim().replace(',', '.');
      if (s.isEmpty()) return 0.0;
      return Double.parseDouble(s);
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Table model with security, amount and date opened
   */
  /// <editor-fold defaultstate="collapsed" desc="Class: StateOpenTableModel">
  private class StateOpenTableModel extends DefaultTableModel {

    /**
     * Build model from stocks information
     *
     * @param stocks Stocks data
     */
    public StateOpenTableModel(Stocks stocks) {
      // Get ticker
      String[] tickers = _stocks.getStockTickers();
      java.util.Arrays.sort(tickers); // Sort tickers

      DecimalFormat nf = new DecimalFormat("0");
      SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy");

      for (int i = 0; i < tickers.length; i++) {
        Vector<Stocks.StockFragment> fragments = _stocks.getSecurityFragments(tickers[i]);

        if (fragments != null) {
          for (Stocks.StockFragment f : fragments) {
            Object[] row = { tickers[i], f.getAmount(), f.getOpened() };
            addRow(row);
          }
        }
      }
    }

    @Override
    public Class getColumnClass(int col) {
      switch (col) {
        case 0:
          return String.class;
        case 1:
          return Double.class;
        case 2:
          return Date.class;
        default:
          return Object.class;
      }
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public String getColumnName(int col) {
      switch (col) {
        case 0:
          return "Ticker";
        case 1:
          return "Množství";
        case 2:
          return "Otevřeno";
        default:
          return "???";
      }
    }
  }
  /// </editor-fold>

  /** End date */
  private JDateChooser _endDate;

  /** Main window **/
  private MainWindow _mainWindow;

  /** Last used stocks object **/
  private Stocks _stocks;

  // TWS comparison data
  private java.util.Map<String, Double> _twsPositions = null;
  private java.util.Map<String, java.util.Map<String, Double>> _twsPositionsByAccount = null;
  private String _twsSelectedAccount = null;
  private TwsCompareStats _twsLastStats = null;

  /** Creates new form AccountStateWindow */
  public AccountStateWindow(java.awt.Frame parent, boolean modal) {
    super(parent, modal);
    initComponents();

    this.setSize(new java.awt.Dimension(300, 600));
    this.setLocationByPlatform(true);

    _mainWindow = (MainWindow) parent;

    GridBagConstraints gbc;

    _endDate = new JDateChooser();
    _endDate.setPreferredSize(new Dimension(100, 20));

    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new java.awt.Insets(5, 5, 5, 0);
    getContentPane().add(_endDate, gbc);

    _endDate.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
      public void propertyChange(java.beans.PropertyChangeEvent evt) {
        recompute(_endDate.getDate());
      }
    });

    getContentPane().doLayout();
  }

  /**
   * Recompute state
   */
  private void recompute(Date endDate) {
    boolean useExecutionDate = (cbStateType.getSelectedIndex() == 1);

    // Make date 0:0:0
    GregorianCalendar cal = new GregorianCalendar();

    cal.setTime(endDate);
    cal.set(GregorianCalendar.HOUR_OF_DAY, 0);
    cal.set(GregorianCalendar.MINUTE, 0);
    cal.set(GregorianCalendar.SECOND, 0);
    cal.set(GregorianCalendar.MILLISECOND, 0);
    endDate = cal.getTime();

    _stocks = new Stocks();
    try {
      // Get transaction set
      TransactionSet transactions = _mainWindow.getTransactionDatabase();

      // Sort transactions before we proceed
      transactions.sort();

      // Do transactions
      for (Iterator<Transaction> i = transactions.iterator(); i.hasNext();) {
        Transaction tx = i.next();

        if (useExecutionDate) {
          if (tx.getExecutionDate().compareTo(endDate) >= 0)
            continue; // Ignore this transaction. We can't just break, since execution dates may not
                      // appear in-order
        } else {
          if (tx.getDate().compareTo(endDate) >= 0)
            break; // Reached end date
        }

        // Apply the transaction
        _stocks.applyTransaction(tx, useExecutionDate);
      }

      // Finish transformations we have
      _stocks.finishTransformations();
    } catch (Stocks.TradingException ex) {
      JOptionPane.showMessageDialog(this, "Při výpočtu stavu účtu nastala chyba:\n\n" + ex.getMessage());
      return;
    }

    // Create and set model
    if (cbOpenDetails.isSelected()) {
      table.setModel(new StateOpenTableModel(_stocks));
      table.getColumnModel().getColumn(2).setCellRenderer(new CustomDateRenderer(_stocks));
    } else {
      table.setModel(new StateTableModel(_stocks));
      // Apply renderer for comparison coloring
      CompareCellRenderer r = new CompareCellRenderer();
      for (int i = 0; i < table.getColumnCount(); i++) {
        table.getColumnModel().getColumn(i).setCellRenderer(r);
      }

      // If we already loaded TWS, re-apply to the new model.
      if (_twsPositionsByAccount != null) {
        applyTwsToTable();
      }
    }
  }

  /**
   * Set date, recompute & show dialog
   */
  public void showDialog() {
    // Set end date to now if not set yet & recompute
    if (_endDate.getDate() == null)
      _endDate.setDate(new Date()); // Will call recompute because we changed property
    else
      recompute(_endDate.getDate()); // Call recompute ourselves

    setVisible(true);
  }

  /**
   * Save opening transactions for the state
   */
  private void saveTransactions() {
    if (cbStateType.getSelectedIndex() != 0) {
      // Show warning message
      if (JOptionPane.showConfirmDialog(rootPane,
          "Pozor! Aktuální výsledky jsou spočítané podle data vypořádání, ne podle data obchodu. Ale výpisy\nod brokera jsou většinou sestavovány právě podle data obchodu. V uložených datech by mohly chybět\nobchody, které v následně importovaných výpisech nebudou! Doporučuji vybrat z roletky\n\"Podle času obchodu\" a spustit export znovu. Chcete export provést,\ni když výsledky mohou být chybné?",
          "Špatný typ výpočtu", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
        return;
    }

    // Run save dialog
    FileDialog dialog = new FileDialog(this, "Export", FileDialog.SAVE);
    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName == null)
      return; // Canceled

    // Check whether file exists
    File f = new File(dialog.getDirectory(), fileName);
    if (f.exists()) {
      // Ask for overwrite
      if (JOptionPane.showConfirmDialog(rootPane, "Vybraný soubor již existuje. Chcete jej přepsat?", "Soubor existuje",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
        return;
    }

    try {
      // Do export - build transaction set and save it
      _stocks.buildStateTransactions().save(f);
    } catch (Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(rootPane, "Chyba při zápisu souboru: " + e.toString(), "Chyba",
          JOptionPane.ERROR_MESSAGE);
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

    cbOpenDetails = new javax.swing.JCheckBox();
    jLabel1 = new javax.swing.JLabel();
    jTextField1 = new javax.swing.JTextField();
    cbStateType = new javax.swing.JComboBox();
    bSaveTx = new javax.swing.JButton();
    bLoadTws = new javax.swing.JButton();
    lTwsAccount = new javax.swing.JLabel();
    cbTwsAccount = new javax.swing.JComboBox();
    lTwsStatus = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    table = new javax.swing.JTable();
    cbOpenDetails = new javax.swing.JCheckBox();

    setTitle("Výpočet stavu účtu");
    getContentPane().setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Stav k:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(jLabel1, gridBagConstraints);

    jTextField1.setEditable(false);
    jTextField1.setText("00:00");
    jTextField1.setMinimumSize(new java.awt.Dimension(34, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    getContentPane().add(jTextField1, gridBagConstraints);

    cbStateType
        .setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Podle času obchodu", "Podle času vypořádání" }));
    cbStateType.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cbStateTypeActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    getContentPane().add(cbStateType, gridBagConstraints);

    bSaveTx.setText("Exportovat jako obchody");
    bSaveTx.setToolTipText(
        "Uloží otevírací obchody, které utevřely zobrazené pozice. Toto je možné použít pro vedení každého roku ve zvláštním souboru.");
    bSaveTx.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveTxActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    getContentPane().add(bSaveTx, gridBagConstraints);

    bLoadTws.setText("Načíst z TWS");
    bLoadTws.setToolTipText("Načte pozice z lokálně běžícího TWS (API) a porovná s tabulkou");
    bLoadTws.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bLoadTwsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(bLoadTws, gridBagConstraints);

    lTwsAccount.setText("Účet:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridx = 0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    getContentPane().add(lTwsAccount, gridBagConstraints);

    cbTwsAccount.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Součet všech" }));
    cbTwsAccount.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cbTwsAccountActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
    getContentPane().add(cbTwsAccount, gridBagConstraints);

    lTwsStatus.setText(" ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 7;
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    getContentPane().add(lTwsStatus, gridBagConstraints);

    table.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null, null },
            { null, null },
            { null, null },
            { null, null }
        },
        new String[] {
            "Ticker", "Množství"
        }) {
      Class[] types = new Class[] {
          java.lang.String.class, java.lang.Float.class
      };
      boolean[] canEdit = new boolean[] {
          false, false
      };

      public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
      }
    });
    jScrollPane1.setViewportView(table);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    getContentPane().add(jScrollPane1, gridBagConstraints);

    cbOpenDetails.setText("Zobrazit detaily otevření");
    cbOpenDetails.setMaximumSize(new java.awt.Dimension(250, 23));
    cbOpenDetails.setMinimumSize(new java.awt.Dimension(250, 23));
    cbOpenDetails.setPreferredSize(new java.awt.Dimension(250, 23));
    cbOpenDetails.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cbOpenDetailsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    getContentPane().add(cbOpenDetails, gridBagConstraints);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void cbStateTypeActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_cbStateTypeActionPerformed
  {// GEN-HEADEREND:event_cbStateTypeActionPerformed
    recompute(_endDate.getDate()); // Call recompute
  }// GEN-LAST:event_cbStateTypeActionPerformed

  private void bSaveTxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveTxActionPerformed
    saveTransactions();
  }// GEN-LAST:event_bSaveTxActionPerformed

  private void cbOpenDetailsActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_cbOpenDetailsActionPerformed
    recompute(_endDate.getDate()); // Call recompute
  }// GEN-LAST:event_cbOpenDetailsActionPerformed

  private void bLoadTwsActionPerformed(java.awt.event.ActionEvent evt) {
    loadFromTws();
  }

  private void cbTwsAccountActionPerformed(java.awt.event.ActionEvent evt) {
    updateTwsSelectedAccount();
    // Persist selection for next run (if not 'Součet všech')
    if (_twsSelectedAccount == null) {
      Settings.setTwsDefaultAccount("");
    } else {
      Settings.setTwsDefaultAccount(_twsSelectedAccount);
    }
    applyTwsToTable();
    updateTwsStatusSummary(null);
  }

  private void updateTwsSelectedAccount() {
    if (cbTwsAccount == null) return;
    Object sel = cbTwsAccount.getSelectedItem();
    if (sel == null) {
      _twsSelectedAccount = null;
      return;
    }
    String s = sel.toString();
    if ("Součet všech".equals(s)) {
      _twsSelectedAccount = null;
    } else {
      _twsSelectedAccount = s;
    }
  }

  private void loadFromTws() {
    // Do not run in EDT
    bLoadTws.setEnabled(false);
    lTwsStatus.setText("Načítám pozice z TWS...");
    
    javax.swing.SwingWorker<IbkrTwsPositionsClient.PositionsResult, Void> w = new javax.swing.SwingWorker<>() {
      @Override
      protected IbkrTwsPositionsClient.PositionsResult doInBackground() throws Exception {
        IbkrTwsPositionsClient c = new IbkrTwsPositionsClient();
        return c.fetchPositions(Settings.getTwsHost(), Settings.getTwsPort(), Settings.getTwsClientId(),
            java.time.Duration.ofSeconds(Settings.getTwsTimeoutSeconds()));
      }

      @Override
      protected void done() {
        try {
          IbkrTwsPositionsClient.PositionsResult r = get();
          _twsPositionsByAccount = r.positionsByAccount;

          // Fill account selector
          java.util.Set<String> accounts = new java.util.TreeSet<>(_twsPositionsByAccount.keySet());
          cbTwsAccount.removeAllItems();
          cbTwsAccount.addItem("Součet všech");
          for (String acc : accounts) {
            if (acc != null && !acc.isBlank()) {
              cbTwsAccount.addItem(acc);
            }
          }

          // Preselect default account (if present)
          String preferred = Settings.getTwsDefaultAccount();
          if (preferred != null && !preferred.isBlank()) {
            for (int i = 0; i < cbTwsAccount.getItemCount(); i++) {
              Object it = cbTwsAccount.getItemAt(i);
              if (it != null && preferred.trim().equals(it.toString().trim())) {
                cbTwsAccount.setSelectedIndex(i);
                break;
              }
            }
          }

          updateTwsSelectedAccount();
          applyTwsToTable();

          updateTwsStatusSummary(r.errors);
        } catch (Exception e) {
          lTwsStatus.setText("Chyba: " + e.getMessage());
          _twsPositionsByAccount = null;
          _twsPositions = null;
          _twsLastStats = null;
        } finally {
          bLoadTws.setEnabled(true);
          // Force repaint to apply renderer colors
          table.repaint();
        }
      }
    };
    w.execute();
  }

  private void applyTwsToTable() {
    if (_stocks == null || cbOpenDetails.isSelected()) {
      // Compare only supported in summary table
      return;
    }

    // Build ticker->pos map
    java.util.Map<String, Double> merged = new java.util.HashMap<>();
    if (_twsPositionsByAccount != null) {
      if (_twsSelectedAccount == null) {
        // Sum all
        for (java.util.Map<String, Double> m : _twsPositionsByAccount.values()) {
          if (m == null) continue;
          for (java.util.Map.Entry<String, Double> e : m.entrySet()) {
            merged.put(e.getKey(), merged.getOrDefault(e.getKey(), 0.0) + (e.getValue() == null ? 0.0 : e.getValue()));
          }
        }
      } else {
        java.util.Map<String, Double> m = _twsPositionsByAccount.get(_twsSelectedAccount);
        if (m != null) {
          merged.putAll(m);
        }
      }
    }
    _twsPositions = merged;

    DefaultTableModel m = (DefaultTableModel) table.getModel();
    int mismatch = 0;
    int filled = 0;
    int localCount = 0;

    java.util.Set<String> seen = new java.util.HashSet<>();
    for (int i = 0; i < m.getRowCount(); i++) {
      String ticker = (String) m.getValueAt(i, 0);
      String localKey = IbkrTwsPositionsClient.normalizeTicker(ticker);
      if (localKey == null) continue;

      localCount++;

      Double tws = null;
      for (String k : IbkrTwsPositionsClient.buildAlternateTickers(localKey)) {
        tws = _twsPositions.get(k);
        if (tws != null) {
          break;
        }
      }
      m.setValueAt(tws == null ? 0.0 : tws, i, 2);

      // Compare only for local rows (before adding extras)
      double local = parseDouble(m.getValueAt(i, 1));
      double tv = tws == null ? 0.0 : tws;
      if (!nearlyEqual(local, tv)) {
        mismatch++;
      }
      filled++;

      // Mark all alternates as seen so extras don't re-add.
      seen.addAll(IbkrTwsPositionsClient.buildAlternateTickers(localKey));
    }

    // Add extra tickers from TWS not present in local
    java.util.List<String> extra = new java.util.ArrayList<>();
    for (String ticker : _twsPositions.keySet()) {
      if (!seen.contains(ticker)) {
        extra.add(ticker);
      }
    }
    java.util.Collections.sort(extra);
    for (String ticker : extra) {
      Object[] row = { ticker, 0.0, _twsPositions.get(ticker) };
      m.addRow(row);
    }

    _twsLastStats = new TwsCompareStats(localCount, _twsPositions.size(), mismatch, extra.size(), filled);
  }

  private static final class TwsCompareStats {
    final int localTickers;
    final int twsTickers;
    final int mismatch;
    final int onlyInTws;
    final int compared;

    TwsCompareStats(int localTickers, int twsTickers, int mismatch, int onlyInTws, int compared) {
      this.localTickers = localTickers;
      this.twsTickers = twsTickers;
      this.mismatch = mismatch;
      this.onlyInTws = onlyInTws;
      this.compared = compared;
    }
  }

  private void updateTwsStatusSummary(java.util.Set<String> errors) {
    if (lTwsStatus == null) return;
    if (_twsLastStats == null) return;

    String base = "TWS: " + _twsLastStats.twsTickers + " tickerů";
    String cmp = ", nesedí: " + _twsLastStats.mismatch + "/" + _twsLastStats.compared;
    String extra = _twsLastStats.onlyInTws > 0 ? ", jen v TWS: " + _twsLastStats.onlyInTws : "";
    String msg = base + cmp + extra;

    if (errors != null && !errors.isEmpty()) {
      msg += " (varování: " + errors.iterator().next() + ")";
    }
    lTwsStatus.setText(msg);
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bLoadTws;
  private javax.swing.JButton bSaveTx;
  private javax.swing.JCheckBox cbOpenDetails;
  private javax.swing.JComboBox cbStateType;
  private javax.swing.JComboBox cbTwsAccount;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel lTwsAccount;
  private javax.swing.JLabel lTwsStatus;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTextField jTextField1;
  private javax.swing.JTable table;
  // End of variables declaration//GEN-END:variables

}
