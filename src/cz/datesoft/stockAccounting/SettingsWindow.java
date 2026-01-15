/*
 * SettingsDialog.java
 *
 * Created on 6. říjen 2006, 14:03
 */

package cz.datesoft.stockAccounting;

import java.util.TreeSet;
import java.util.SortedSet;
import java.util.Vector;
import java.util.Iterator;
import java.util.HashMap;
import javax.swing.table.TableColumn;
import java.text.DecimalFormat;
import javax.swing.table.TableColumnModel;
import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.DefaultListModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 *
 * @author lemming2
 */
public class SettingsWindow extends javax.swing.JDialog {
  private MainWindow mainWindow;

  /**
   * Column names
   */
  private class RTableModel extends javax.swing.table.AbstractTableModel {
    /**
     * Table column names
     */
    Vector<String> columnNames;

    /**
     * Column name => column index mapping
     */
    HashMap<String, Integer> colName2Idx;

    /**
     * Year => row mapping
     */
    HashMap<Integer, Vector<Object>> year2Vector;

    /**
     * Table data
     */
    Vector<Vector<Object>> data;

    /**
     * Constructor - initialize model from a set of currency ratios
     */
    public RTableModel(SortedSet<Settings.CurrencyRatio> initialData) {
      Iterator<Settings.CurrencyRatio> i1;
      Iterator<String> i2;
      SortedSet<String> names = new TreeSet<String>();

      // Create collections
      columnNames = new Vector<String>();
      data = new Vector<Vector<Object>>();
      colName2Idx = new HashMap<String, Integer>();
      year2Vector = new HashMap<Integer, Vector<Object>>();

      /* Pass 1 - determine column names => which currencies we have */
      for (i1 = initialData.iterator(); i1.hasNext();) {
        Settings.CurrencyRatio r = i1.next();
        names.add(r.getCurrency());
      }

      // Put column names into a vector & make translation table
      columnNames.add("Rok");
      for (i2 = names.iterator(); i2.hasNext();) {
        String name = i2.next();

        colName2Idx.put(name, Integer.valueOf(columnNames.size()));
        columnNames.add(name);
      }

      /* Pass2 - put data into grid */
      for (i1 = initialData.iterator(); i1.hasNext();) {
        Settings.CurrencyRatio r = i1.next();
        Vector<Object> v = year2Vector.get(r.getYear());
        int i;

        if (v == null) {
          // New year
          v = new Vector<Object>();
          Integer y = Integer.valueOf(r.getYear());
          year2Vector.put(y, v);

          v.add(y); // Initialize first column with year

          data.add(v); // Add row to data vector
        }

        i = colName2Idx.get(r.getCurrency()).intValue();
        while (v.size() <= i)
          v.add(null); // Enlarge vector

        v.set(i, Double.valueOf(r.getRatio()));
      }
    }

    public Class getColumnClass(int c) {
      if (c == 0)
        return Integer.class;
      else
        return Double.class;
    }

    public Object getValueAt(int row, int col) {
      Vector<Object> v;

      try {
        v = data.get(row);

        return v.get(col);
      } catch (java.lang.ArrayIndexOutOfBoundsException e) {
        return null;
      }
    }

    public void setValueAt(Object value, int row, int col) {
      Vector<Object> v;

      try {
        v = data.get(row);
      } catch (java.lang.ArrayIndexOutOfBoundsException e) {
        if (value == null)
          return; // Don't add row when user entered nothing

        v = new Vector<Object>();

        data.add(row, v);
      }

      if (col == 0) {
        if (value == null)
          return; // Don't accept null in the year column

        // Year was set - change year2Vector map
        Integer year = (Integer) value;
        year2Vector.remove(year);
        year2Vector.put(year, v);
      }

      while (v.size() <= col)
        v.add(null);

      v.set(col, value);
    }

    public int getColumnCount() {
      return columnNames.size();
    }

    public int getRowCount() {
      return data.size() + 1;
    }

    public String getColumnName(int col) {
      return columnNames.get(col);
    }

    public boolean isCellEditable(int row, int column) {
      return true;
    }

    /**
     * Add currency to the model
     *
     * @return Model index
     */
    public int addCurrency(String currency) {
      int idx;

      currency = currency.toUpperCase();

      if (colName2Idx.get(currency) != null)
        throw new java.lang.IllegalArgumentException("Currency already present"); // Already present

      idx = columnNames.size();
      colName2Idx.put(currency, Integer.valueOf(idx));
      columnNames.add(currency);

      return idx;
    }

    /**
     * Remove currency from the model.
     *
     * @return Model index of the currency.
     */
    public int removeCurrency(String currency) {
      currency = currency.toUpperCase();

      Integer idxI = colName2Idx.get(currency);

      if (idxI == null)
        throw new java.lang.IllegalArgumentException("Currency does not exist"); // Not present

      int idx = idxI.intValue();

      // Remove from hash
      colName2Idx.remove(currency);

      // Clear in column names
      columnNames.set(idx, "");

      /*
       * We do not need to remove from data since data model indexes do not change. We
       * might want to clear positions to free up memory, but we have very small DB
       * anyway.
       */

      return idx;
    }

    /**
     * Return data in grid as a dataset of CurrencyRatio
     */
    public SortedSet<Settings.CurrencyRatio> constructDataSet() {
      int i, n, year;

      SortedSet<Settings.CurrencyRatio> set = new TreeSet<Settings.CurrencyRatio>();

      for (i = 0; i < data.size(); i++) {
        Vector<Object> v = data.get(i);

        if (v.get(0) != null) {
          year = ((Integer) v.get(0)).intValue();

          for (n = 1; n < v.size(); n++) {
            Double ratio = (Double) v.get(n);

            if (ratio != null) {
              String s = columnNames.get(n);
              if (s.length() > 0)
                set.add(new Settings.CurrencyRatio(s, year, ratio.doubleValue()));
            }
          }
        }
      }

      return set;
    }

    /**
     * Return currencies list
     */
    public Object[] getCurrenciesList() {
      return columnNames.subList(1, columnNames.size()).toArray();
    }

    /**
     * Remove row
     */
    public void removeRow(int idx) {
      // Determine year & remove from year2Vector table
      try {
        Vector<Object> v = data.get(idx);

        Object year = v.get(0);

        if (year != null) {
          // Year is filled in
          year2Vector.remove(year);
        }

        // Remove row
        data.remove(idx);

        fireTableDataChanged();
      } catch (java.lang.ArrayIndexOutOfBoundsException e) {
        // Do nothing
      }
    }
  }

  /**
   * Renderer for arbitrary number of decimal digits
   */
  private class DoubleRenderer extends javax.swing.table.DefaultTableCellRenderer {
    // Formatter
    private DecimalFormat f;

    public DoubleRenderer() {
      // Initialize formatter
      f = new DecimalFormat("0.00#####");
    }

    protected void setValue(Object value) {

      if (value == null)
        super.setValue(null);
      else if (value.getClass().equals(java.lang.Double.class)) {
        this.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        super.setValue(f.format(value));
      } else
        super.setValue(value);
    }
  }

  /**
   * Renderer for highlighted cells (fetched/modified values)
   */
  private class HighlightedDoubleRenderer extends DoubleRenderer {
    @Override
    public java.awt.Component getTableCellRendererComponent(
        javax.swing.JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
      java.awt.Component c = super.getTableCellRendererComponent(
          table, value, isSelected, hasFocus, row, column);

      // Check if this cell was fetched/modified
      String cellKey = row + "," + column;
      if (fetchedCells.contains(cellKey)) {
        if (!isSelected) {
          c.setBackground(new java.awt.Color(255, 255, 200)); // Light yellow
        }
      } else {
        if (!isSelected) {
          c.setBackground(java.awt.Color.WHITE);
        }
      }

      return c;
    }
  }

  /**
   * Model we use
   */
  RTableModel model;

  /**
   * Renderer we use
   */
  DoubleRenderer doubleRenderer;

  /**
   * Highlighted renderer for fetched cells
   */
  HighlightedDoubleRenderer highlightedRenderer;

  /**
   * Set of fetched/modified cells (format: "row,col")
   */
  java.util.Set<String> fetchedCells;

  /**
   * Markets
   */
  Markets markets;

  /** Creates new form SettingsDialog */
  public SettingsWindow(java.awt.Frame parent, boolean modal) {
    super(parent, modal);
    this.mainWindow = (MainWindow) parent;
    initComponents();

    this.setLocationByPlatform(true);
    doubleRenderer = new DoubleRenderer();
    highlightedRenderer = new HighlightedDoubleRenderer();
    fetchedCells = new java.util.HashSet<String>();
    initDailyRatesTab();
  }

  /**
   * Enables / disables buttons + refreshes edit boxes
   */
  private void holidaySelectionChanged() {
    int idx = lHolidays.getSelectedIndex();

    if (idx == -1) {
      // Clear boxes
      eDay.setText("");
      eMonth.setText("");
      eYearFrom.setText("");
      eYearTo.setText("");

      // Disable buttons
      bModifyHoliday.setEnabled(false);
      bDeleteHoliday.setEnabled(false);
    } else {
      // Set texts
      Markets.Market.Holiday holiday = (Markets.Market.Holiday) lHolidays.getSelectedValue();

      eDay.setText(Integer.toString(holiday.day));
      eMonth.setText(Integer.toString(holiday.month));
      eYearFrom.setText(Integer.toString(holiday.yearFrom));
      eYearTo.setText(Integer.toString(holiday.yearTo));

      // Enable buttons
      bModifyHoliday.setEnabled(true);
      bDeleteHoliday.setEnabled(true);
    }
  }

  /**
   * Enables / disables market change/delete + refreshes holidays
   */
  private void marketSelectionChanged() {
    int idx = lMarkets.getSelectedIndex();
    if (idx == -1) {
      // Clear & disable holidays
      lHolidays.setModel(new DefaultListModel());
      holidaySelectionChanged();
      bAddHoliday.setEnabled(false);

      // Clear values
      eMarketName.setText("");
      eDelay.setText("");

      // Disable delete / modify buttons
      bModifyMarket.setEnabled(false);
      bDeleteMarket.setEnabled(false);
    } else {
      Markets.Market m = (Markets.Market) lMarkets.getSelectedValue();

      // Set holidays
      lHolidays.setModel(m);
      holidaySelectionChanged();
      bAddHoliday.setEnabled(true);

      // Set texts
      eMarketName.setText(m.getName());
      eDelay.setText(Integer.toString(m.getTradeDelay()));

      // Enable delete / modify buttons
      bModifyMarket.setEnabled(true);
      bDeleteMarket.setEnabled(true);
    }
  }

  /**
   * Checks whether entered data for market are valid
   */
  private boolean checkMarketData() {
    if (eMarketName.getText().length() == 0) {
      JOptionPane.showMessageDialog(this, "Název trhu nesmí být prázdný", "Chyba", JOptionPane.ERROR_MESSAGE);
      return false;
    }

    int d = -1;
    try {
      d = Integer.parseInt(eDelay.getText());
    } catch (Exception e) {
    }

    if (d < 0) {
      JOptionPane.showMessageDialog(this, "Vypořádání musí být celé nezáporné číslo!", "Chyba",
          JOptionPane.ERROR_MESSAGE);
      return false;
    }

    return true;
  }

  /**
   * Gets data form market, returns array of day, month, yearFrom, yearTo
   */
  private int[] getHolidayData() throws Exception {
    int[] res = new int[4];

    try {
      res[0] = Integer.parseInt(eDay.getText());
    } catch (Exception e) {
      throw new Exception("Den musí být celé číslo!");
    }

    try {
      res[1] = Integer.parseInt(eMonth.getText());
    } catch (Exception e) {
      throw new Exception("Měsíc musí být celé číslo!");
    }

    try {
      res[2] = Integer.parseInt(eYearFrom.getText());
    } catch (Exception e) {
      res[2] = 0;
    }

    try {
      res[3] = Integer.parseInt(eYearTo.getText());
    } catch (Exception e) {
      res[3] = 0;
    }

    return res;
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc=" Generated Code
  // ">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    jPanel1 = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    cbHalfYear = new javax.swing.JComboBox();
    jLabel2 = new javax.swing.JLabel();
    jComboBox2 = new javax.swing.JComboBox();
    jPanel4 = new javax.swing.JPanel();
    jPanel6 = new javax.swing.JPanel();
    jPanel7 = new javax.swing.JPanel();
    jLabel3 = new javax.swing.JLabel();
    jScrollPane2 = new javax.swing.JScrollPane();
    lMarkets = new javax.swing.JList();
    jPanel9 = new javax.swing.JPanel();
    bAddMarket = new javax.swing.JButton();
    jLabel4 = new javax.swing.JLabel();
    eMarketName = new javax.swing.JTextField();
    bModifyMarket = new javax.swing.JButton();
    bDeleteMarket = new javax.swing.JButton();
    jLabel10 = new javax.swing.JLabel();
    jLabel11 = new javax.swing.JLabel();
    eDelay = new javax.swing.JTextField();
    pHolidays = new javax.swing.JPanel();
    jLabel5 = new javax.swing.JLabel();
    jScrollPane3 = new javax.swing.JScrollPane();
    lHolidays = new javax.swing.JList();
    jPanel10 = new javax.swing.JPanel();
    jLabel6 = new javax.swing.JLabel();
    eDay = new javax.swing.JTextField();
    jLabel7 = new javax.swing.JLabel();
    eMonth = new javax.swing.JTextField();
    jLabel8 = new javax.swing.JLabel();
    eYearFrom = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    eYearTo = new javax.swing.JTextField();
    jPanel11 = new javax.swing.JPanel();
    bAddHoliday = new javax.swing.JButton();
    bModifyHoliday = new javax.swing.JButton();
    bDeleteHoliday = new javax.swing.JButton();
    jTabbedPane1 = new javax.swing.JTabbedPane();
    jPanel2 = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    table = new javax.swing.JTable();
    jPanel5 = new javax.swing.JPanel();
    tfCurrency = new javax.swing.JTextField();
    bAddCurrency = new javax.swing.JButton();
    jSeparator1 = new javax.swing.JSeparator();
    cbRemoveCurrency = new javax.swing.JComboBox();
    bRemoveCurrency = new javax.swing.JButton();
    jSeparator2 = new javax.swing.JSeparator();
    bRemoveYear = new javax.swing.JButton();
    jPanel3 = new javax.swing.JPanel();
    bCancel = new javax.swing.JButton();
    bOK = new javax.swing.JButton();

    jPanel1.setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("P\u016fl roku je:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(jLabel1, gridBagConstraints);

    cbHalfYear
        .setModel(new javax.swing.DefaultComboBoxModel(new String[] { "6 m\u011bs\u00edc\u016f", "183 dn\u00ed" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(cbHalfYear, gridBagConstraints);

    jLabel2.setText("Metoda p\u00e1rov\u00e1n\u00ed n\u00e1kup/prodej:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(jLabel2, gridBagConstraints);

    jComboBox2.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "FIFO" }));
    jComboBox2.setEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(jComboBox2, gridBagConstraints);

    org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
    jPanel4.setLayout(jPanel4Layout);
    jPanel4Layout.setHorizontalGroup(
        jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 100, Short.MAX_VALUE));
    jPanel4Layout.setVerticalGroup(
        jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 100, Short.MAX_VALUE));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.weighty = 1.0;
    jPanel1.add(jPanel4, gridBagConstraints);

    jPanel6.setLayout(new java.awt.GridBagLayout());

    jPanel7.setLayout(new java.awt.GridBagLayout());

    jLabel3.setText("Trhy:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    jPanel7.add(jLabel3, gridBagConstraints);

    lMarkets.setModel(new javax.swing.AbstractListModel() {
      String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };

      public int getSize() {
        return strings.length;
      }

      public Object getElementAt(int i) {
        return strings[i];
      }
    });
    lMarkets.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
      public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
        lMarketsValueChanged(evt);
      }
    });

    jScrollPane2.setViewportView(lMarkets);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    jPanel7.add(jScrollPane2, gridBagConstraints);

    jPanel9.setLayout(new java.awt.GridBagLayout());

    bAddMarket.setText("P\u0159idat");
    bAddMarket.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bAddMarketActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
    jPanel9.add(bAddMarket, gridBagConstraints);

    jLabel4.setText("N\u00e1zev:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 5);
    jPanel9.add(jLabel4, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
    jPanel9.add(eMarketName, gridBagConstraints);

    bModifyMarket.setText("Upravit");
    bModifyMarket.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bModifyMarketActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
    jPanel9.add(bModifyMarket, gridBagConstraints);

    bDeleteMarket.setText("Vymazat");
    bDeleteMarket.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bDeleteMarketActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    jPanel9.add(bDeleteMarket, gridBagConstraints);

    jLabel10.setText("Vypo\u0159\u00e1d\u00e1n\u00ed:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
    jPanel9.add(jLabel10, gridBagConstraints);

    jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    jLabel11.setText("T+");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
    jPanel9.add(jLabel11, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
    jPanel9.add(eDelay, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
    jPanel7.add(jPanel9, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel6.add(jPanel7, gridBagConstraints);

    pHolidays.setLayout(new java.awt.GridBagLayout());

    jLabel5.setText("Sv\u00e1tky na trhu:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    pHolidays.add(jLabel5, gridBagConstraints);

    lHolidays.setModel(new javax.swing.AbstractListModel() {
      String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };

      public int getSize() {
        return strings.length;
      }

      public Object getElementAt(int i) {
        return strings[i];
      }
    });
    lHolidays.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
      public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
        lHolidaysValueChanged(evt);
      }
    });

    jScrollPane3.setViewportView(lHolidays);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    pHolidays.add(jScrollPane3, gridBagConstraints);

    jPanel10.setLayout(new java.awt.GridBagLayout());

    jLabel6.setText("Den:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    jPanel10.add(jLabel6, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
    jPanel10.add(eDay, gridBagConstraints);

    jLabel7.setText("M\u011bs\u00edc:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
    jPanel10.add(jLabel7, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
    jPanel10.add(eMonth, gridBagConstraints);

    jLabel8.setText("Od roku:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel10.add(jLabel8, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel10.add(eYearFrom, gridBagConstraints);

    jLabel9.setText("Do roku:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel10.add(jLabel9, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel10.add(eYearTo, gridBagConstraints);

    jPanel11.setLayout(new java.awt.GridBagLayout());

    bAddHoliday.setText("P\u0159idat");
    bAddHoliday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bAddHolidayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
    jPanel11.add(bAddHoliday, gridBagConstraints);

    bModifyHoliday.setText("Upravit");
    bModifyHoliday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bModifyHolidayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
    jPanel11.add(bModifyHoliday, gridBagConstraints);

    bDeleteHoliday.setText("Vymazat");
    bDeleteHoliday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bDeleteHolidayActionPerformed(evt);
      }
    });

    jPanel11.add(bDeleteHoliday, new java.awt.GridBagConstraints());

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel10.add(jPanel11, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    pHolidays.add(jPanel10, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel6.add(pHolidays, gridBagConstraints);

    getContentPane().setLayout(new java.awt.GridBagLayout());

    setTitle("Nastaven\u00ed");
    setModal(true);
    setName("settingsDialog");
    jPanel2.setLayout(new java.awt.GridBagLayout());

    table.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null, null, null },
            { null, null, null },
            { null, null, null },
            { null, null, null }
        },
        new String[] {
            "Rok", "EUR", "USD"
        }) {
      Class[] types = new Class[] {
          java.lang.Integer.class, java.lang.Object.class, java.lang.Object.class
      };

      public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
      }
    });
    table.setCellSelectionEnabled(true);
    jScrollPane1.setViewportView(table);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel2.add(jScrollPane1, gridBagConstraints);

    jPanel5.setLayout(new java.awt.GridBagLayout());

    tfCurrency.setColumns(5);
    tfCurrency.setText("PZL");
    tfCurrency.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bAddCurrencyActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    jPanel5.add(tfCurrency, gridBagConstraints);

    bAddCurrency.setText("P\u0159idat m\u011bnu");
    bAddCurrency.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bAddCurrencyActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    jPanel5.add(bAddCurrency, gridBagConstraints);

    jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
    jPanel5.add(jSeparator1, gridBagConstraints);

    cbRemoveCurrency.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "EUR", "USD" }));
    jPanel5.add(cbRemoveCurrency, new java.awt.GridBagConstraints());

    bRemoveCurrency.setText("Odstranit m\u011bnu");
    bRemoveCurrency.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bRemoveCurrencyActionPerformed(evt);
      }
    });

    jPanel5.add(bRemoveCurrency, new java.awt.GridBagConstraints());

    jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
    jPanel5.add(jSeparator2, gridBagConstraints);

    bRemoveYear.setText("Odstranit rok");
    bRemoveYear.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bRemoveYearActionPerformed(evt);
      }
    });

    jPanel5.add(bRemoveYear, new java.awt.GridBagConstraints());

    jSeparator3 = new javax.swing.JSeparator();
    jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
    jPanel5.add(jSeparator3, gridBagConstraints);

    bFetchRates = new javax.swing.JButton();
    bFetchRates.setText("Načíst kurzy");
    bFetchRates.setToolTipText("Načíst jednotné kurzy z ČNB");
    bFetchRates.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bFetchRatesActionPerformed(evt);
      }
    });
    jPanel5.add(bFetchRates, new java.awt.GridBagConstraints());

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    jPanel2.add(jPanel5, gridBagConstraints);

    // Add daily rates controls to currency tab
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);

    javax.swing.JPanel dailyRatesPanel = new javax.swing.JPanel();
    dailyRatesPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
    dailyRatesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Denní kurzy měn"));

    cbUseDailyRates = new javax.swing.JCheckBox("Používat denní kurzy");
    cbUseDailyRates.setToolTipText("Při zaškrtnutí budou pro přepočet měn používány přesné denní kurzy ČNB");
    dailyRatesPanel.add(cbUseDailyRates);

    javax.swing.JButton bSmartFetch = new javax.swing.JButton("Chytré stažení");
    bSmartFetch.setToolTipText("Automaticky zjistí roky s obchody a stáhne pro ně chybějící denní kurzy z ČNB");
    bSmartFetch.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bFetchDailyRatesActionPerformed(evt);
      }
    });
    dailyRatesPanel.add(bSmartFetch);

    javax.swing.JButton bManageRates = new javax.swing.JButton("Správa kurzů");
    bManageRates.setToolTipText("Správa uložených denních kurzů - mazání podle roku nebo vše");
    bManageRates.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        showRateManagementDialog();
      }
    });
    dailyRatesPanel.add(bManageRates);

    jPanel2.add(dailyRatesPanel, gridBagConstraints);

    jTabbedPane1.addTab("Kurzy m\u011bn", jPanel2);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    getContentPane().add(jTabbedPane1, gridBagConstraints);

    bCancel.setText("Storno");
    bCancel.setMaximumSize(new java.awt.Dimension(100, 23));
    bCancel.setMinimumSize(new java.awt.Dimension(100, 23));
    bCancel.setPreferredSize(new java.awt.Dimension(70, 23));
    bCancel.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bCancelActionPerformed(evt);
      }
    });

    bOK.setText("OK");
    bOK.setMaximumSize(new java.awt.Dimension(100, 23));
    bOK.setMinimumSize(new java.awt.Dimension(100, 23));
    bOK.setPreferredSize(new java.awt.Dimension(70, 23));
    bOK.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bOKActionPerformed(evt);
      }
    });

    org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
    jPanel3.setLayout(jPanel3Layout);
    jPanel3Layout.setHorizontalGroup(
        jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(bOK, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                    org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 444, Short.MAX_VALUE)
                .add(bCancel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                    org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)));
    jPanel3Layout.setVerticalGroup(
        jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(bOK, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                    org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(bCancel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                    org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(jPanel3, gridBagConstraints);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void lHolidaysValueChanged(javax.swing.event.ListSelectionEvent evt)// GEN-FIRST:event_lHolidaysValueChanged
  {// GEN-HEADEREND:event_lHolidaysValueChanged
    holidaySelectionChanged();
  }// GEN-LAST:event_lHolidaysValueChanged

  private void bDeleteHolidayActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bDeleteHolidayActionPerformed
  {// GEN-HEADEREND:event_bDeleteHolidayActionPerformed
    if (JOptionPane.showConfirmDialog(this, "Opravdu si přejete smazat svátek?", "Potvrzení",
        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {

    }

    Markets.Market.Holiday h = (Markets.Market.Holiday) lHolidays.getSelectedValue();
    Markets.Market m = (Markets.Market) lMarkets.getSelectedValue();
    m.remove(h);
  }// GEN-LAST:event_bDeleteHolidayActionPerformed

  private void bModifyHolidayActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bModifyHolidayActionPerformed
  {// GEN-HEADEREND:event_bModifyHolidayActionPerformed
    int data[] = null;

    try {
      data = getHolidayData();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage(), "Chyba", JOptionPane.ERROR_MESSAGE);
    }

    Markets.Market.Holiday h = (Markets.Market.Holiday) lHolidays.getSelectedValue();

    h.day = data[0];
    h.month = data[1];
    h.yearFrom = data[2];
    h.yearTo = data[3];

    Markets.Market m = (Markets.Market) lMarkets.getSelectedValue();

    m.holidayUpdated(h);

    lHolidays.clearSelection();
  }// GEN-LAST:event_bModifyHolidayActionPerformed

  private void bAddHolidayActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bAddHolidayActionPerformed
  {// GEN-HEADEREND:event_bAddHolidayActionPerformed
    int data[] = null;

    try {
      data = getHolidayData();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage(), "Chyba", JOptionPane.ERROR_MESSAGE);
    }

    Markets.Market m = (Markets.Market) lMarkets.getSelectedValue();

    m.addHoliday(data[0], data[1], data[2], data[3]);

    lHolidays.clearSelection();
    holidaySelectionChanged();
  }// GEN-LAST:event_bAddHolidayActionPerformed

  private void bDeleteMarketActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bDeleteMarketActionPerformed
  {// GEN-HEADEREND:event_bDeleteMarketActionPerformed
    if (JOptionPane.showConfirmDialog(this, "Opravdu smazat trh?", "Potvrzení",
        JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
      Markets.Market m = (Markets.Market) lMarkets.getSelectedValue();

      markets.removeMarket(m);
    }

    marketSelectionChanged();
  }// GEN-LAST:event_bDeleteMarketActionPerformed

  private void bModifyMarketActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bModifyMarketActionPerformed
  {// GEN-HEADEREND:event_bModifyMarketActionPerformed
    if (!checkMarketData())
      return;

    try {
      Markets.Market m = (Markets.Market) lMarkets.getSelectedValue();
      String s = eDelay.getText(); // Changing name makes changes in listbox which in turn clears delay box, so we
                                   // store it here

      markets.setMarketName(m, eMarketName.getText());

      m.setTradeDelay(Integer.parseInt(s));
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage(), "Chyba", JOptionPane.ERROR_MESSAGE);
    }
    marketSelectionChanged();
  }// GEN-LAST:event_bModifyMarketActionPerformed

  private void lMarketsValueChanged(javax.swing.event.ListSelectionEvent evt)// GEN-FIRST:event_lMarketsValueChanged
  {// GEN-HEADEREND:event_lMarketsValueChanged
    marketSelectionChanged();
  }// GEN-LAST:event_lMarketsValueChanged

  private void bAddMarketActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bAddMarketActionPerformed
  {// GEN-HEADEREND:event_bAddMarketActionPerformed
    if (!checkMarketData())
      return;

    try {
      markets.addMarket(eMarketName.getText(), Integer.parseInt(eDelay.getText()));
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage(), "Chyba", JOptionPane.ERROR_MESSAGE);
    }

    lMarkets.clearSelection();
    marketSelectionChanged();
  }// GEN-LAST:event_bAddMarketActionPerformed

  private void bRemoveYearActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bRemoveYearActionPerformed
  {// GEN-HEADEREND:event_bRemoveYearActionPerformed
    int row = table.getSelectedRow();

    // Remove selected row
    model.removeRow(row);
  }// GEN-LAST:event_bRemoveYearActionPerformed

  private void bRemoveCurrencyActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bRemoveCurrencyActionPerformed
  {// GEN-HEADEREND:event_bRemoveCurrencyActionPerformed
   // Remove selected currency
    int modelIndex = model.removeCurrency((String) cbRemoveCurrency.getSelectedItem());

    table.removeColumn(table.getColumnModel().getColumn(modelIndex));

    // Refresh currencies combo
    refreshCurrenciesCombo();
  }// GEN-LAST:event_bRemoveCurrencyActionPerformed

  private void bAddCurrencyActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bAddCurrencyActionPerformed
  {// GEN-HEADEREND:event_bAddCurrencyActionPerformed
   // Add currency
    String currency = tfCurrency.getText();

    if (currency.length() == 0)
      return; // Don't add empty curency

    // Add to model
    int modelIndex = model.addCurrency(currency);

    // Add to table
    TableColumn column = new TableColumn(modelIndex);
    column.setCellRenderer(doubleRenderer);
    column.setIdentifier(currency);
    table.addColumn(column);

    // Clear text field
    tfCurrency.setText("");

    // Refresh currencies combo
    refreshCurrenciesCombo();
  }// GEN-LAST:event_bAddCurrencyActionPerformed

  private void bOKActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bOKActionPerformed
  {// GEN-HEADEREND:event_bOKActionPerformed
   // Store settings
    Settings.setHalfYear((cbHalfYear.getSelectedIndex() == 0) ? Settings.HY_6M : Settings.HY_183D);
    Settings.setRatios(model.constructDataSet());
    Settings.setMarkets(markets);

    // And save them
    Settings.setUseDailyRates(cbUseDailyRates.isSelected());
    Settings.save();

    // Close
    setVisible(false);
  }// GEN-LAST:event_bOKActionPerformed

  private void bCancelActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bCancelActionPerformed
  {// GEN-HEADEREND:event_bCancelActionPerformed
    setVisible(false);
  }// GEN-LAST:event_bCancelActionPerformed

  // Enhanced bFetchRatesActionPerformed method
  // Replace lines 1210-1334 in SettingsWindow.java with this code

  private void bFetchRatesActionPerformed(java.awt.event.ActionEvent evt) {
    // Determine target year
    int selectedYear = -1;
    int selectedRow = table.getSelectedRow();
    if (selectedRow != -1 && selectedRow < model.getRowCount() - 1) {
      Object yearObj = model.getValueAt(selectedRow, 0);
      if (yearObj != null) {
        int year = ((Integer) yearObj).intValue();
        Object[] options = { "Jen rok " + year, "Všechny roky", "Zrušit" };
        int n = JOptionPane.showOptionDialog(this,
            "Chcete načíst jednotné kurzy pouze pro rok " + year + " nebo pro všechny roky v tabulce?",
            "Výběr rozsahu načítání",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);

        if (n == JOptionPane.YES_OPTION) {
          selectedYear = year;
        } else if (n == JOptionPane.CANCEL_OPTION || n == JOptionPane.CLOSED_OPTION) {
          return;
        }
        // If NO_OPTION, selectedYear remains -1 (all years)
      }
    }

    final int targetYear = selectedYear;

    // Create progress dialog with progress bar
    final javax.swing.JDialog progressDialog = new javax.swing.JDialog(this, "Načítání kurzů", true);
    progressDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);

    javax.swing.JPanel progressPanel = new javax.swing.JPanel(new java.awt.BorderLayout(10, 10));
    progressPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20));

    final javax.swing.JLabel progressLabel = new javax.swing.JLabel("Načítám kurzy z ČNB...");
    final javax.swing.JProgressBar progressBar = new javax.swing.JProgressBar();
    progressBar.setStringPainted(true);
    progressBar.setString("0%");

    progressPanel.add(progressLabel, java.awt.BorderLayout.NORTH);
    progressPanel.add(progressBar, java.awt.BorderLayout.CENTER);

    progressDialog.add(progressPanel);
    progressDialog.pack();
    progressDialog.setLocationRelativeTo(this);

    // Count total items to fetch
    int totalItems = 0;
    for (int col = 1; col < model.getColumnCount(); col++) {
      String currency = model.getColumnName(col);
      if (currency != null && !currency.isEmpty()) {
        for (int row = 0; row < model.getRowCount() - 1; row++) {
          Object yearObj = model.getValueAt(row, 0);
          if (yearObj != null) {
            int year = ((Integer) yearObj).intValue();
            if (targetYear == -1 || year == targetYear) {
              totalItems++;
            }
          }
        }
      }
    }

    final int totalCount = totalItems;
    final int[] currentCount = { 0 };

    // Fetch rates in background thread
    final java.util.Map<String, CurrencyRateFetcher.FetchedRate>[] resultHolder = new java.util.Map[1];
    final Exception[] errorHolder = new Exception[1];

    Thread fetchThread = new Thread(new Runnable() {
      public void run() {
        try {
          // Fetch rates with progress updates
          java.util.Map<String, CurrencyRateFetcher.FetchedRate> results = new java.util.HashMap<String, CurrencyRateFetcher.FetchedRate>();

          // Build map of existing rates
          java.util.Map<String, Double> existingMap = new java.util.HashMap<String, Double>();
          for (Settings.CurrencyRatio ratio : Settings.getRatios()) {
            String key = ratio.getCurrency() + "|" + ratio.getYear();
            existingMap.put(key, ratio.getRatio());
          }

          // Get currencies
          java.util.List<String> currencies = new java.util.ArrayList<String>();
          for (int col = 1; col < model.getColumnCount(); col++) {
            String currency = model.getColumnName(col);
            if (currency != null && !currency.isEmpty()) {
              currencies.add(currency);
            }
          }

          // Fetch for each year and currency
          for (int row = 0; row < model.getRowCount() - 1; row++) {
            Object yearObj = model.getValueAt(row, 0);
            if (yearObj == null)
              continue;

            int year = ((Integer) yearObj).intValue();
            if (targetYear != -1 && year != targetYear)
              continue;

            for (String currency : currencies) {
              try {
                Double fetchedRate = CurrencyRateFetcher.fetchJednotnyKurz(currency, year);
                if (fetchedRate != null) {
                  String key = currency + "|" + year;
                  Double existingRate = existingMap.get(key);

                  boolean wasModified = (existingRate == null) ||
                      (Math.abs(existingRate - fetchedRate) > 0.001);

                  results.put(key, new CurrencyRateFetcher.FetchedRate(fetchedRate, wasModified, "CNB"));
                }
              } catch (Exception e) {
                System.err.println("Error fetching " + currency + " for " + year + ": " + e.getMessage());
              }

              // Update progress
              currentCount[0]++;
              final int progress = (int) ((currentCount[0] * 100.0) / totalCount);
              javax.swing.SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  progressBar.setValue(progress);
                  progressBar.setString(progress + "%");
                  progressLabel
                      .setText("Načítám kurzy z ČNB... (" + currentCount[0] + "/" + totalCount + ")");
                }
              });
            }
          }

          resultHolder[0] = results;
        } catch (Exception e) {
          errorHolder[0] = e;
        } finally {
          javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              progressDialog.setVisible(false);
            }
          });
        }
      }
    });

    fetchThread.start();
    progressDialog.setVisible(true); // Blocks until thread closes dialog

    // Check for errors
    if (errorHolder[0] != null) {
      JOptionPane.showMessageDialog(this,
          "Chyba při načítání kurzů: " + errorHolder[0].getMessage(),
          "Chyba", JOptionPane.ERROR_MESSAGE);
      return;
    }

    java.util.Map<String, CurrencyRateFetcher.FetchedRate> fetchedRates = resultHolder[0];

    if (fetchedRates == null || fetchedRates.isEmpty()) {
      JOptionPane.showMessageDialog(this,
          "Nepodařilo se načíst žádné kurzy. Zkontrolujte připojení k internetu.",
          "Informace", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    // Save old values for potential rollback
    java.util.Map<String, Double> oldValues = new java.util.HashMap<String, Double>();

    // Apply fetched rates to table and highlight changed cells (PREVIEW)
    fetchedCells.clear();

    for (java.util.Map.Entry<String, CurrencyRateFetcher.FetchedRate> entry : fetchedRates.entrySet()) {
      String key = entry.getKey();
      CurrencyRateFetcher.FetchedRate fetchedRate = entry.getValue();

      // Parse key: "CURRENCY|YEAR"
      String[] parts = key.split("\\|");
      if (parts.length != 2)
        continue;

      String currency = parts[0];
      int year = Integer.parseInt(parts[1]);

      // Find row and column in table
      for (int row = 0; row < model.getRowCount() - 1; row++) {
        Object yearObj = model.getValueAt(row, 0);
        if (yearObj != null && ((Integer) yearObj).intValue() == year) {
          // Find currency column
          for (int col = 1; col < model.getColumnCount(); col++) {
            if (model.getColumnName(col).equals(currency)) {
              // Save old value
              Object oldVal = model.getValueAt(row, col);
              if (oldVal != null) {
                oldValues.put(row + "," + col, ((Double) oldVal).doubleValue());
              }

              // Set new value (PREVIEW)
              model.setValueAt(Double.valueOf(fetchedRate.rate), row, col);

              // Highlight if modified
              if (fetchedRate.wasModified) {
                fetchedCells.add(row + "," + col);
              }
              break;
            }
          }
          break;
        }
      }
    }

    // Switch to highlighted renderer
    javax.swing.table.TableColumnModel cm = table.getColumnModel();
    for (int i = 1; i < cm.getColumnCount(); i++) {
      cm.getColumn(i).setCellRenderer(highlightedRenderer);
    }

    // Refresh table to show preview
    table.repaint();

    // Count modified values
    int modifiedCount = 0;
    for (CurrencyRateFetcher.FetchedRate rate : fetchedRates.values()) {
      if (rate.wasModified)
        modifiedCount++;
    }

    // Build confirmation message
    StringBuilder message = new StringBuilder();
    message.append("Načteno ").append(fetchedRates.size()).append(" kurzů z ČNB.\n");
    if (targetYear != -1) {
      message.append("Rozsah: Pouze rok ").append(targetYear).append(".\n");
    }
    if (modifiedCount > 0) {
      message.append(modifiedCount).append(" hodnot se liší od aktuálních.\n\n");
      message.append("Změněné hodnoty jsou zvýrazněny žlutě.\n");
      message.append("Chcete tyto kurzy uložit?");
    } else {
      message.append("Všechny hodnoty odpovídají aktuálním kurzům.\n");
      message.append("Chcete je přesto uložit?");
    }

    int result = JOptionPane.showConfirmDialog(this, message.toString(),
        "Potvrzení", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

    if (result != JOptionPane.YES_OPTION) {
      // User cancelled - restore old values
      for (java.util.Map.Entry<String, Double> entry : oldValues.entrySet()) {
        String[] pos = entry.getKey().split(",");
        int row = Integer.parseInt(pos[0]);
        int col = Integer.parseInt(pos[1]);
        model.setValueAt(Double.valueOf(entry.getValue()), row, col);
      }

      // Clear highlighting
      fetchedCells.clear();

      // Switch back to normal renderer
      for (int i = 1; i < cm.getColumnCount(); i++) {
        cm.getColumn(i).setCellRenderer(doubleRenderer);
      }

      table.repaint();
      return;
    }

    // User accepted - keep the changes
    JOptionPane.showMessageDialog(this,
        "Kurzy byly úspěšně načteny a uloženy.\nZměněné hodnoty jsou zvýrazněny žlutě.",
        "Hotovo", JOptionPane.INFORMATION_MESSAGE);
  }

  /**
   * Refresh "remove curency" combo box & button state
   */
  /* Daily Rates Tab Implementation */
  private javax.swing.JPanel pDailyRates;
  private javax.swing.JTable dailyRatesTable;
  private javax.swing.JCheckBox cbUseDailyRates;
  private javax.swing.JButton bFetchDailyRates;
  private javax.swing.table.DefaultTableModel dailyRatesModel;

  private void initDailyRatesTab() {
    pDailyRates = new javax.swing.JPanel();
    pDailyRates.setLayout(new java.awt.BorderLayout());

    // Top panel for options
    javax.swing.JPanel pTop = new javax.swing.JPanel();
    pTop.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    cbUseDailyRates = new javax.swing.JCheckBox("Používat denní kurzy");
    cbUseDailyRates.setSelected(Settings.getUseDailyRates());
    cbUseDailyRates.setToolTipText(
        "Při zaškrtnutí budou pro přepočet měn používány přesné denní kurzy ČNB namísto jednotného kurzu");
    pTop.add(cbUseDailyRates);

    bFetchDailyRates = new javax.swing.JButton("Chytré stažení");
    bFetchDailyRates.setToolTipText("Automaticky zjistí roky s obchody a stáhne pro ně chybějící denní kurzy z ČNB");
    bFetchDailyRates.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bFetchDailyRatesActionPerformed(evt);
      }
    });
    pTop.add(bFetchDailyRates);

    pDailyRates.add(pTop, java.awt.BorderLayout.NORTH);

    // Table for daily rates
    dailyRatesModel = new javax.swing.table.DefaultTableModel(
        new Object[][] {},
        new String[] { "Měna", "Datum", "Kurz" }) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 2)
          return Double.class;
        return String.class;
      }
    };

    dailyRatesTable = new javax.swing.JTable(dailyRatesModel);
    dailyRatesTable.setAutoCreateRowSorter(true);

    // Use the same DoubleRenderer for formatting
    dailyRatesTable.setDefaultRenderer(Double.class, new DoubleRenderer());

    javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(dailyRatesTable);
    pDailyRates.add(scrollPane, java.awt.BorderLayout.CENTER);

    jTabbedPane1.addTab("Denní kurzy", pDailyRates);

    // Populate table
    refreshDailyRatesTable();
  }

  private void refreshDailyRatesTable() {
    dailyRatesModel.setRowCount(0);
    java.util.HashMap<String, Double> rates = Settings.getDailyRates();
    if (rates == null)
      return;

    // Use a strategy to show many rates efficiently
    // Sort keys to have some order (optional but nice)
    java.util.List<String> sortedKeys = new java.util.ArrayList<String>(rates.keySet());
    java.util.Collections.sort(sortedKeys);

    for (String key : sortedKeys) {
      String[] parts = key.split("\\|");
      if (parts.length == 2) {
        dailyRatesModel.addRow(new Object[] { parts[0], parts[1], rates.get(key) });
      }
    }
  }

  private void bFetchDailyRatesActionPerformed(java.awt.event.ActionEvent evt) {
    // 1. Identify years with trades
    final java.util.Set<Integer> years = new java.util.HashSet<Integer>();
    TransactionSet ts = mainWindow.getTransactionDatabase();
    if (ts != null) {
      java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
      for (Iterator<Transaction> i = ts.iterator(); i.hasNext();) {
        Transaction tx = i.next();
        cal.setTime(tx.getExecutionDate());
        years.add(cal.get(java.util.GregorianCalendar.YEAR));
      }
    }

    if (years.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Nebyly nalezeny žádné roky s obchody k načtení.");
      return;
    }

    // 2. Determine currencies to fetch
    final java.util.List<String> currenciesToFetch = Settings.getCurrenciesToFetch(ts);
    if (currenciesToFetch.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Nebyly určeny žádné měny k načtení.");
      return;
    }

    // 3. Fetch in background with progress bar
    final JDialog progressDialog = new JDialog(this, "Načítání denních kurzů", true);
    final JProgressBar progressBar = new JProgressBar(0, years.size());
    progressBar.setStringPainted(true);
    progressDialog.add(progressBar, java.awt.BorderLayout.CENTER);
    progressDialog.setSize(300, 75);
    progressDialog.setLocationRelativeTo(this);

    new Thread(new Runnable() {
      public void run() {
        int loadedCount = 0;
        int failedCount = 0;
        final java.util.HashMap<String, Double> allNewRates = new java.util.HashMap<String, Double>();
        java.util.HashMap<String, Double> existingRates = Settings.getDailyRates();
        if (existingRates != null)
          allNewRates.putAll(existingRates);

        for (final int year : years) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              progressBar.setValue(progressBar.getValue() + 1);
              progressBar.setString("Načítám rok " + year + "...");
            }
          });

          try {
            // Use selective fetching - only fetch currencies that are needed
            java.util.Map<String, Double> yearRates = CurrencyRateFetcher.fetchSelectiveDailyRates(year, currenciesToFetch);
            allNewRates.putAll(yearRates);
            loadedCount++;
          } catch (Exception e) {
            System.err.println("Chyba při načítání roku " + year + ": " + e.getMessage());
            failedCount++;
          }
        }

        final int fLoaded = loadedCount;
        final int fFailed = failedCount;

        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            progressDialog.dispose();
            Settings.setDailyRates(allNewRates);
            refreshDailyRatesTable();
            JOptionPane.showMessageDialog(SettingsWindow.this,
                "Načítání dokončeno.\nÚspěšně načteno roků: " + fLoaded + "\nChyb: " + fFailed);
          }
        });
      }
    }).start();

    progressDialog.setVisible(true);
  }

  private void showRateManagementDialog() {
    RateManagementDialog dialog = new RateManagementDialog(this);
    dialog.setVisible(true);
    // Refresh the table after dialog closes (rates may have changed)
    refreshDailyRatesTable();
  }

  private void refreshCurrenciesCombo() {
    // Get list of currencies from data mode
    Object[] currencies = model.getCurrenciesList();

    // Re-fill combo box
    javax.swing.DefaultComboBoxModel cbm = (javax.swing.DefaultComboBoxModel) cbRemoveCurrency.getModel();
    cbm.removeAllElements();
    for (int i = 0; i < currencies.length; i++)
      cbm.addElement(currencies[i]);

    // Enable / disable controls
    cbRemoveCurrency.setEnabled(currencies.length > 0);
    bRemoveCurrency.setEnabled(currencies.length > 0);
  }

  /**
   * Show dialog - get data from settings and show
   */
  public void showDialog() {
    /** Refresh data from settings **/

    /* Refresh "half year" */
    int hy = Settings.getHalfYear();
    if (hy == Settings.HY_6M)
      cbHalfYear.setSelectedIndex(0);
    else
      cbHalfYear.setSelectedIndex(1);

    /* Refresh ratios tab */

    // Refresh table
    model = new RTableModel(Settings.getRatios());
    table.setModel(model);

    // Set renderer on columns
    TableColumnModel cm = table.getColumnModel();
    for (int i = 1; i < cm.getColumnCount(); i++)
      cm.getColumn(i).setCellRenderer(doubleRenderer);

    // Clear add currency text field
    tfCurrency.setText("");

    // Refresh currencies combo
    refreshCurrenciesCombo();

    /** Get our own copy of markets **/
    markets = new Markets(Settings.getMarkets().saveString());

    // Setup models
    lMarkets.setModel(markets);
    marketSelectionChanged();

    // Refresh daily rates tab
    if (cbUseDailyRates != null) {
      cbUseDailyRates.setSelected(Settings.getUseDailyRates());
      refreshDailyRatesTable();
    }

    setVisible(true);
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bAddCurrency;
  private javax.swing.JButton bAddHoliday;
  private javax.swing.JButton bAddMarket;
  private javax.swing.JButton bCancel;
  private javax.swing.JButton bDeleteHoliday;
  private javax.swing.JButton bDeleteMarket;
  private javax.swing.JButton bModifyHoliday;
  private javax.swing.JButton bModifyMarket;
  private javax.swing.JButton bOK;
  private javax.swing.JButton bRemoveCurrency;
  private javax.swing.JButton bRemoveYear;
  private javax.swing.JComboBox cbHalfYear;
  private javax.swing.JComboBox cbRemoveCurrency;
  private javax.swing.JTextField eDay;
  private javax.swing.JTextField eDelay;
  private javax.swing.JTextField eMarketName;
  private javax.swing.JTextField eMonth;
  private javax.swing.JTextField eYearFrom;
  private javax.swing.JTextField eYearTo;
  private javax.swing.JComboBox jComboBox2;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel10;
  private javax.swing.JLabel jLabel11;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel4;
  private javax.swing.JLabel jLabel5;
  private javax.swing.JLabel jLabel6;
  private javax.swing.JLabel jLabel7;
  private javax.swing.JLabel jLabel8;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel jPanel10;
  private javax.swing.JPanel jPanel11;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JPanel jPanel4;
  private javax.swing.JPanel jPanel5;
  private javax.swing.JPanel jPanel6;
  private javax.swing.JPanel jPanel7;
  private javax.swing.JPanel jPanel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JScrollPane jScrollPane3;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JSeparator jSeparator3;
  private javax.swing.JButton bFetchRates;
  private javax.swing.JTabbedPane jTabbedPane1;
  private javax.swing.JList lHolidays;
  private javax.swing.JList lMarkets;
  private javax.swing.JPanel pHolidays;
  private javax.swing.JTable table;
  private javax.swing.JTextField tfCurrency;
  // End of variables declaration//GEN-END:variables

}
