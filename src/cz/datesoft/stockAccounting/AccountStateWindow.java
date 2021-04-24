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
import javax.swing.JFileChooser;
import javax.swing.JTable;
//import javax.swing.JLabel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.awt.Color;

/**
 *
 * @author  lemming2
 */
public class AccountStateWindow extends javax.swing.JDialog
{
  /**
   * Date renderer - render date in DD.MM.YYYY format and in green if older than 6 months
   */
  /// <editor-fold defaultstate="collapsed" desc="Class: CustomDateRenderer">
  private class CustomDateRenderer extends DefaultTableCellRenderer
  {
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
    public CustomDateRenderer(Stocks stocks)
    {
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
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
    {
      if (value instanceof Date) {
        // Check if this is a stock
        String symbol = (String)table.getValueAt(row, 0);
        Stocks.SecType type = _stocks.getSecurityType(symbol);

        // Check if date is over 6m
        if ((type == Stocks.SecType.STOCK) && Stocks.isOverTaxFreeDuration((Date)value, new Date())) {
          setBackground(Color.GREEN);
        }
        else {
          setBackground(Color.WHITE);
        }
        return super.getTableCellRendererComponent(table, _df.format((Date)value), isSelected, hasFocus, row, column);
      }
      else return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column); // Fallback

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
        setValueAt(_stocks.getStockAmount(tickers[i]), i, 1);
      }
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int col) {
      switch (col) {
        case 0:
          return "Ticker";
        case 1:
          return "Množství";
        default:
          return "???";
      }
    }
  }
  /// </editor-fold>

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
            Object[] row = {tickers[i], f.getAmount(), f.getOpened()};
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

  /** Creates new form AccountStateWindow */
  public AccountStateWindow(java.awt.Frame parent, boolean modal)
  {
    super(parent, modal);
    initComponents();

    this.setSize(new java.awt.Dimension(300,600));
    this.setLocationByPlatform(true);
        
    _mainWindow = (MainWindow)parent;
    
    GridBagConstraints gbc;
    
    _endDate = new JDateChooser();
    _endDate.setPreferredSize(new Dimension(100,20));
    
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new java.awt.Insets(5, 5, 5, 0);
    getContentPane().add(_endDate,gbc);

    _endDate.addPropertyChangeListener(new java.beans.PropertyChangeListener()
    {
      public void propertyChange(java.beans.PropertyChangeEvent evt) { 
        recompute(_endDate.getDate());
      }
    });    
    
    getContentPane().doLayout();    
  }

  /**
   * Recompute state
   */
  private void recompute(Date endDate)
  {
    boolean useExecutionDate = (cbStateType.getSelectedIndex() == 1);
    
    // Make date 0:0:0
    GregorianCalendar cal = new GregorianCalendar();
    
    cal.setTime(endDate);
    cal.set(GregorianCalendar.HOUR_OF_DAY,0);
    cal.set(GregorianCalendar.MINUTE,0);
    cal.set(GregorianCalendar.SECOND,0);
    cal.set(GregorianCalendar.MILLISECOND,0);
    endDate = cal.getTime();
    
    _stocks = new Stocks();
    try {
      // Get transaction set
      TransactionSet transactions = _mainWindow.getTransactionDatabase();
      
      // Sort transactions before we proceed
      transactions.sort();

      // Do transactions
      for(Iterator<Transaction> i = transactions.iterator();i.hasNext();) {
        Transaction tx = i.next();
      
        if (useExecutionDate) {
          if (tx.getExecutionDate().compareTo(endDate) >= 0) continue; // Ignore this transaction. We can't just break, since execution dates may not appear in-order
        }
        else {
          if (tx.getDate().compareTo(endDate) >= 0) break; // Reached end date          
        }
        
        // Apply the transaction
        _stocks.applyTransaction(tx,useExecutionDate);
      }
    
      // Finish transformations we have
      _stocks.finishTransformations();
    }
    catch(Stocks.TradingException ex) {
      JOptionPane.showMessageDialog(this,"Při výpočtu stavu účtu nastala chyba:\n\n"+ex.getMessage());
      return;
    }
    
    // Create and set model
    if (cbOpenDetails.isSelected()) {
      table.setModel(new StateOpenTableModel(_stocks));
      table.getColumnModel().getColumn(2).setCellRenderer(new CustomDateRenderer(_stocks));
    }
    else {
      table.setModel(new StateTableModel(_stocks));
    }
  }
  
  /**
   * Set date, recompute & show dialog 
   */
  public void showDialog()
  {
    // Set end date to now if not set yet & recompute
    if (_endDate.getDate() == null) _endDate.setDate(new Date()); // Will call recompute because we changed property
    else recompute(_endDate.getDate()); // Call recompute ourselves
    
    setVisible(true);
  }

  /**
   * Save opening transactions for the state
   */
  private void saveTransactions()
  {
    if (cbStateType.getSelectedIndex() != 0) {
      // Show warning message
      if (JOptionPane.showConfirmDialog(rootPane, "Pozor! Aktuální výsledky jsou spočítané podle data vypořádání, ne podle data obchodu. Ale výpisy\nod brokera jsou většinou sestavovány právě podle data obchodu. V uložených datech by mohly chybět\nobchody, které v následně importovaných výpisech nebudou! Doporučuji vybrat z roletky\n\"Podle času obchodu\" a spustit export znovu. Chcete export provést,\ni když výsledky mohou být chybné?", "Špatný typ výpočtu", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
    }

    // Run save dialog
    if (chFileChooser.showDialog(rootPane, "Export") != JFileChooser.APPROVE_OPTION) return; // Canceled

    // Check whether file exists
    File f = chFileChooser.getSelectedFile();
    if (f.exists()) {
      // Ask for overwrite
      if (JOptionPane.showConfirmDialog(rootPane, "Vybraný soubor již existuje. Chcete jej přepsat?", "Soubor existuje", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
    }

    try {
      // Do export - build transaction set and save it
      _stocks.buildStateTransactions().save(f);
    }
    catch(Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(rootPane, "Chyba při zápisu souboru: "+e.toString(), "Chyba", JOptionPane.ERROR_MESSAGE);
    }
  }
  
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    chFileChooser = new javax.swing.JFileChooser();
    jLabel1 = new javax.swing.JLabel();
    jTextField1 = new javax.swing.JTextField();
    cbStateType = new javax.swing.JComboBox();
    bSaveTx = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    table = new javax.swing.JTable();
    cbOpenDetails = new javax.swing.JCheckBox();

    chFileChooser.setDialogTitle("Exportovat stav jako obchody");
    chFileChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);

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

    cbStateType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Podle času obchodu", "Podle času vypořádání" }));
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
    bSaveTx.setToolTipText("Uloží otevírací obchody, které utevřely zobrazené pozice. Toto je možné použít pro vedení každého roku ve zvláštním souboru.");
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

    table.setModel(new javax.swing.table.DefaultTableModel(
      new Object [][] {
        {null, null},
        {null, null},
        {null, null},
        {null, null}
      },
      new String [] {
        "Ticker", "Množství"
      }
    ) {
      Class[] types = new Class [] {
        java.lang.String.class, java.lang.Integer.class
      };
      boolean[] canEdit = new boolean [] {
        false, false
      };

      public Class getColumnClass(int columnIndex) {
        return types [columnIndex];
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
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

  private void cbStateTypeActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cbStateTypeActionPerformed
  {//GEN-HEADEREND:event_cbStateTypeActionPerformed
    recompute(_endDate.getDate()); // Call recompute
  }//GEN-LAST:event_cbStateTypeActionPerformed

  private void bSaveTxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bSaveTxActionPerformed
    saveTransactions();
  }//GEN-LAST:event_bSaveTxActionPerformed

  private void cbOpenDetailsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbOpenDetailsActionPerformed
    recompute(_endDate.getDate()); // Call recompute
  }//GEN-LAST:event_cbOpenDetailsActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bSaveTx;
  private javax.swing.JCheckBox cbOpenDetails;
  private javax.swing.JComboBox cbStateType;
  private javax.swing.JFileChooser chFileChooser;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTextField jTextField1;
  private javax.swing.JTable table;
  // End of variables declaration//GEN-END:variables
  
}

