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

/**
 *
 * @author lemming2
 */
public class MainWindow extends javax.swing.JFrame {

  // <editor-fold defaultstate="collapsed" desc="Class: DateChooserCellEditor">
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
      dateChooser.setDateFormatString("dd. MM. yyyy HH:mm");

      return dateChooser;
    }

    public Object getCellEditorValue() {
      Date date = dateChooser.getDate();
      if (date == null)
        return null;

      GregorianCalendar res = new GregorianCalendar();

      res.setTime(date);

      // Clear seconds & miliseconds
      res.set(GregorianCalendar.SECOND, 0);
      res.set(GregorianCalendar.MILLISECOND, 0);

      return res.getTime();
    }
  }
  // </editor-fold>

  // Transaction database
  private TransactionSet transactions;

  // Import window
  private ImportWindow importWindow;

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

    transactions = new TransactionSet();

    cbTickers.setModel(transactions.getTickersModel());

    this.setSize(1000, 550);
    this.setLocationByPlatform(true);

    dcFrom.setDateFormatString("dd.MM.yyyy");
    try {
      dcFrom.setDate(new java.text.SimpleDateFormat("yyyy-MM-dd").parse("1900-01-01"));
    } catch (Exception e) {
    }
    dcFrom.getDateEditor().getUiComponent().addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyPressed(java.awt.event.KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_ENTER)
          applyFilter();
      }
    });

    dcTo.setDateFormatString("dd.MM.yyyy");
    dcTo.setDate(new java.util.Date());
    dcTo.getDateEditor().getUiComponent().addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyPressed(java.awt.event.KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_ENTER)
          applyFilter();
      }
    });

    table.setModel(transactions);

    // Call mainwindow table initialization - helpers, column setting
    initTableColumns();

    // Create dialogs
    importWindow = new ImportWindow(this, true);
    computeWindow = new ComputeWindow(this, false);
    accountStateWindow = new AccountStateWindow(this, false);
    aboutWindow = new AboutWindow(this, true);

    java.net.URL iconURL = getClass().getResource("images/dolarm.png");
    dollarIcon = new javax.swing.ImageIcon(iconURL);
    setIconImage(getDollarImage());
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
        new javax.swing.DefaultComboBoxModel(new String[] { "", "CP", "Derivát", "Transformace", "Dividenda", "Cash" }));

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
    getContentPane().setLayout(new java.awt.GridBagLayout());

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
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 11;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(cbTypeFilter, gridBagConstraints);

    jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 15;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jSeparator3, gridBagConstraints);

    bDelete.setText("Smazat řádek");
    bDelete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bDeleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 16;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(bDelete, gridBagConstraints);

    bSort.setText("Seřadit");
    bSort.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSortActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 17;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    jPanel2.add(bSort, gridBagConstraints);

    jLabel7.setText("Note:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 10;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(jLabel7, gridBagConstraints);

    tfNote.setMinimumSize(new java.awt.Dimension(60, 20));
    tfNote.setPreferredSize(new java.awt.Dimension(60, 20));
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
    gridBagConstraints.gridx = 11;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel2.add(tfNote, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    getContentPane().add(jPanel2, gridBagConstraints);

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

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    getContentPane().add(jScrollPane1, gridBagConstraints);

    jLabel1.setText("   ");

    org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
    jPanel1.setLayout(jPanel1Layout);
    jPanel1Layout.setHorizontalGroup(
        jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel1));
    jPanel1Layout.setVerticalGroup(
        jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel1));

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    getContentPane().add(jPanel1, gridBagConstraints);

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

    jMenuBar1.add(jMenu3);

    setJMenuBar(jMenuBar1);

    pack();
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

    // Enable manual column resizing
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

    // Datum obchodu
    table.getColumnModel().getColumn(0).setPreferredWidth(100);
    table.getColumnModel().getColumn(0).setMaxWidth(100);
    table.getColumnModel().getColumn(0).setCellRenderer(new CZDateRenderer());
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
    table.getColumnModel().getColumn(10).setPreferredWidth(100);
    table.getColumnModel().getColumn(10).setMaxWidth(100);
    table.getColumnModel().getColumn(10).setCellRenderer(new CZDateRenderer());
    table.getColumnModel().getColumn(10).setCellEditor(new DateChooserCellEditor());
    // Poznamka (Note)
    table.getColumnModel().getColumn(11).setPreferredWidth(200);
    table.getColumnModel().getColumn(11).setMaxWidth(500);

  }

  private void miNewActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_miNewActionPerformed
  {// GEN-HEADEREND:event_miNewActionPerformed
   // There is bug no calendar choser working and others.. so disabling this menu
   // activity
   // if (JOptionPane.showConfirmDialog(rootPane, "Pozor!\n Aktualne nefunguje,
   // ukonci a spust aplikaci znovu :)\nAle pokud rozumis jave a chtel bys toto
   // opravit budu jen rad\n", "Upozorneni", JOptionPane.OK_CANCEL_OPTION,
   // JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;

    transactions = new TransactionSet();
    table.setModel(transactions);
    // Call mainwindow table initialization - helpers, column setting
    initTableColumns();

    // Clear results of computing
    computeWindow.clearComputeResults();
    // Clear filter
    clearFilter();

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
    aboutWindow.setVisible(true);
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
    // Show format selection dialog first
    String[] formats = {"<vyberte formát>", "Fio - obchody export", "BrokerJet - HTML export (legacy)",
                       "IB - TradeLog", "IB - FlexQuery Trades only CSV", "T212 Invest  - csv  mena: USD",
                       "T212 Invest  - csv  mena: CZK", "Revolut - csv", "Trading 212 API"};

    String selectedFormat = (String) javax.swing.JOptionPane.showInputDialog(
        this, "Vyberte formát importu:", "Formát importu",
        javax.swing.JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]);

    if (selectedFormat == null || selectedFormat.equals("<vyberte formát>")) {
      return; // User cancelled or didn't select format
    }

    int formatIndex = java.util.Arrays.asList(formats).indexOf(selectedFormat);
    boolean isApiFormat = (formatIndex == 8); // Trading 212 API

    File selectedFile = null;
    Date startDate = null;

    if (!isApiFormat) {
      // Show file dialog only for file-based formats
      FileDialog dialog = new FileDialog(this, "Importovat soubor", FileDialog.LOAD);

      String loc = Settings.getImportDirectory();
      if (loc != null)
        dialog.setDirectory(loc);

      dialog.setVisible(true);

      String fileName = dialog.getFile();
      if (fileName != null) {
        selectedFile = new File(dialog.getDirectory(), fileName);
        Settings.setImportDirectory(dialog.getDirectory());
        Settings.save();

        // Get start date for file-based imports
        startDate = transactions.getMaxDate();
        if (startDate != null) {
          // Add a day to start importing next day we have
          GregorianCalendar cal = new GregorianCalendar();
          cal.setTime(startDate);
          cal.add(GregorianCalendar.DAY_OF_MONTH, 1);
          startDate = cal.getTime();
        }
      } else {
        return; // User cancelled file selection
      }
    }

    // Open ImportWindow with the selected format and optional file
    importWindow.startImport(selectedFile, startDate, formatIndex);
  }// GEN-LAST:event_miImportActionPerformed

  private void bDeleteActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bDeleteActionPerformed
  {// GEN-HEADEREND:event_bDeleteActionPerformed
   // Store selected row
    int selectedRow = table.getSelectedRow();

    // Reset editting, if active
    table.clearSelection();

    // Delete current row
    transactions.deleteRow(selectedRow);
  }// GEN-LAST:event_bDeleteActionPerformed

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
      if (file.exists()) {
        if (JOptionPane.showConfirmDialog(this,
            "Soubor " + file.getAbsolutePath() + " již existuje, chcete jej přepsat?", "Přepsat soubor?",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
          return false;
      }

      Settings.setDataDirectory(dialog.getDirectory());
      Settings.save();

      return saveTransactions(file);
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

    if (ticker.length() == 0)
      ticker = null;
    if (market.length() == 0)
      market = null;
    if (type != null && type.length() == 0)
      type = null;
    if (note.length() == 0)
      note = null;

    // Apply filter
    transactions.applyFilter(dcFrom.getDate(), dcTo.getDate(), ticker, market, type, note);

    // Enable clear filter button and save filtered
    bClearFilter.setEnabled(true);
    miSaveFiltered.setEnabled(true);
  }

  public void clearFilter() {
    transactions.clearFilter();
    bClearFilter.setEnabled(false);
    miSaveFiltered.setEnabled(false);
    cbTypeFilter.setSelectedIndex(0); // Reset to empty selection
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
        // Load file
        transactions.load(selectedFile);

        // Clear results of computing to avoid confusion
        computeWindow.clearComputeResults();

        // Clear filter
        clearFilter();
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Při načítání souboru nastala chyba: " + e);
      }
    }
  }// GEN-LAST:event_miOpenActionPerformed

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

        // Clear results of computing to avoid confusion
        computeWindow.clearComputeResults();

        // Clear filter
        clearFilter();
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
   * Refresh the main table display
   */
  public void refreshTable() {
    table.revalidate();
    table.repaint();
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bApplyFilter;
  private javax.swing.JButton bClearFilter;
  private javax.swing.JButton bDelete;
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
  // End of variables declaration//GEN-END:variables

}
