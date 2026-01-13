/*
 * ComputeDialog.java
 *
 * Created on 6. říjen 2006, 17:01
 */

package cz.datesoft.stockAccounting;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import javax.swing.table.DefaultTableModel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import java.text.DecimalFormat;
import java.util.Vector;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.io.File;
import java.awt.FileDialog;

/**
 *
 * @author Michal Kára
 */
public class ComputeWindow extends javax.swing.JDialog {
  private static enum IncludeOverTaxFreeDuration {
    SHOW_ONLY, INCLUDE, LEAVE_OUT
  }

  private static enum NoIncomeTrades {
    SHOW_ONLY, INCLUDE, LEAVE_OUT
  }

  /**
   * Formats we can save in
   */
  private static enum SaveFormat {
    HTML("HTML"),
    CSV("CSV");

    private final String _name;

    SaveFormat(String name) {
      _name = name;
    }

    @Override
    public String toString() {
      return _name;
    }
  }

  /**
   * Main window
   */
  private MainWindow mainWindow;

  /**
   * Year we computed with
   */
  private int yearComputed;

  /**
   * Include over tax free duration
   */
  private boolean includeOverTaxFreeDurarionComputed;

  /** Creates new form ComputeDialog */
  public ComputeWindow(java.awt.Frame parent, boolean modal) {
    super(parent, modal);
    mainWindow = (MainWindow) parent;
    initComponents();

    // works better on multiple monitors environment
    GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    int width = gd.getDisplayMode().getWidth();
    int height = gd.getDisplayMode().getHeight();
    // Maximize window
    setBounds(0, 0, width, height);
    // setLocation(0, 0);
    // setSize(java.awt.Toolkit.getDefaultToolkit().getScreenSize()); #worked well
    // on single monitor only
    // setSize(width, height);

    // Set right-aligning renderer for columns that need it
    DefaultTableCellRenderer rarenderer = new DefaultTableCellRenderer();
    rarenderer.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);

    yearComputed = 0;

    // Initialize conversion method label
    lConvMethod = new javax.swing.JLabel();
    lConvMethod.setFont(new java.awt.Font("Tahoma", 1, 11));
    lConvMethod.setText("Metoda přepočtu: " + (Settings.getUseDailyRates() ? "Denní kurz" : "Jednotný kurz"));

    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    gbc.gridy = 1;
    gbc.gridx = 6; // After cbSeparateCurrencyCSV
    gbc.gridwidth = 3;
    gbc.anchor = java.awt.GridBagConstraints.WEST;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    jPanel1.add(lConvMethod, gbc);

    // Update table columns and renderers
    TableColumnModel[] tcms = { tableCP.getColumnModel(), tableDer.getColumnModel(), tableCash.getColumnModel() };
    for (TableColumnModel tcm : tcms) {
      // Indices: 0:Date, 2:Počet, 3:Kurz, 4:J.Cena, 5:Poplatky, 6:Otevření, 9:Počet,
      // 10:Kurz, 11:J.Cena, 12:Poplatky, 13:Zavření, 14:Výsledek
      int[] rightAligned = { 0, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14 };
      for (int idx : rightAligned) {
        tcm.getColumn(idx).setCellRenderer(rarenderer);
      }
    }

    TableColumnModel tcmDiv = diviTable.getColumnModel();
    tcmDiv.getColumn(0).setCellRenderer(rarenderer);
    tcmDiv.getColumn(2).setCellRenderer(rarenderer);
    tcmDiv.getColumn(3).setCellRenderer(rarenderer);
    tcmDiv.getColumn(4).setCellRenderer(rarenderer);
    tcmDiv.getColumn(5).setCellRenderer(rarenderer);
  }

  /**
   * Save HTML header
   */
  private void saveHTMLHeader(java.io.PrintWriter ofl, String title, javax.swing.JTable tbl) {
    ofl.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
        +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"cz\" lang=\"cz\"><head><title>" + title + "</title>");
    ofl.println("<style type=\"text/css\">");
    ofl.println("body { text-align: center; background-color: white; }");
    ofl.println("table { border: 1px solid black; border-spacing: 0px; margin-left: auto; margin-right: auto; }");
    ofl.println("td { border: 1px solid black; padding: 2px; text-align: right; }");
    ofl.println("th { border: 1px solid black; padding: 2px; background-color: #dddddd; }");
    ofl.println(".left { text-align: left; }");
    ofl.println(".finalRow { font-weight: bold; }");
    ofl.println("</style></head>");
    ofl.println("<body>");
    ofl.println("<h1>" + title + "</h1>");
    ofl.println("<table>");

    // Save table header line
    TableColumnModel cm = tbl.getColumnModel();
    ofl.println("<tr>");
    for (int i = 0; i < tbl.getColumnCount(); i++) {
      ofl.write("<th>" + (String) cm.getColumn(i).getHeaderValue() + "</th>");
    }
    ofl.println("</tr>");
  }

  /**
   * Save computed as HTML
   */
  private void saveHTML(String title, File file, JTable table) throws Exception {
    java.io.PrintWriter ofl = new java.io.PrintWriter(new java.io.FileWriter(file));

    saveHTMLHeader(ofl, title, table);

    // Save all other lines
    DefaultTableModel model = (DefaultTableModel) table.getModel();
    int emptyRow = -1;
    int finalRow = model.getRowCount() - 1;
    for (int i = 0; i < model.getRowCount(); i++) {
      if (i != emptyRow) {
        ofl.write("<tr" + ((i == finalRow) ? " class=\"finalRow\"" : "") + ">");
        for (int n = 0; n < model.getColumnCount(); n++) {
          if ((n == 1) || (n == 7) || (n == 8) || (n == 15))
            ofl.write("<td class=\"left\">");
          else
            ofl.write("<td>");
          String s = (String) model.getValueAt(i, n);
          if (n != 15)
            s = spaces2nbsp(s);
          ofl.write(s + "</td>");
        }
        ofl.println("</tr>");
      }
    }

    ofl.println("</body></html>");

    ofl.close();

  }

  /**
   * Save dividend summary as HTML
   */
  private void saveDiviHTML(String title, File file) throws java.io.IOException {
    java.io.PrintWriter ofl = new java.io.PrintWriter(new java.io.FileWriter(file));

    saveHTMLHeader(ofl, title, diviTable);

    // Save all other lines
    DefaultTableModel model = (DefaultTableModel) diviTable.getModel();
    int emptyRow = model.getRowCount() - 2;
    int finalRow = model.getRowCount() - 1;
    for (int i = 0; i < model.getRowCount(); i++) {
      if (i != emptyRow) {
        ofl.write("<tr" + ((i == finalRow) ? " class=\"finalRow\"" : "") + ">");
        for (int n = 0; n < model.getColumnCount(); n++) {
          if (n == 1)
            ofl.write("<td class=\"left\">");
          else
            ofl.write("<td>");
          String s = (String) model.getValueAt(i, n);
          s = spaces2nbsp(s);
          ofl.write(s + "</td>");
        }
        ofl.println("</tr>");
      }
    }

    ofl.println("</body></html>");

    ofl.close();
  }

  /**
   * Save computed as CSV
   *
   * @param title              Title of the table
   * @param file               File to save to
   * @param table              Table to save
   * @param splitColumnHeaders Names (header) of columns which will be saved as
   *                           two columns, split by the first space
   * @param splitSecHeader     What to append to the header (column name) of the
   *                           second column in the two split columns
   */
  private void saveCSV(String title, File file, JTable table, String[] splitColumnHeaders, String splitSecHeader)
      throws Exception {
    java.io.BufferedWriter ofl = new java.io.BufferedWriter(new java.io.FileWriter(file));

    // Save file header
    ofl.write('"' + title + "\";\n;\n");

    // Save table header line
    TableColumnModel cm = table.getColumnModel();
    boolean[] splitColumn = new boolean[table.getColumnCount()];
    for (int i = 0; i < table.getColumnCount(); i++) {
      if (i > 0)
        ofl.write(';');
      String header = (String) cm.getColumn(i).getHeaderValue();
      ofl.write('"' + header + '"');

      for (String h : splitColumnHeaders) {
        if (h.equalsIgnoreCase(header)) {
          // Set to split this column
          splitColumn[i] = true;

          // Write column again with added header
          ofl.write(';');
          ofl.write('"' + header + splitSecHeader + '"');

          // Do it only once
          break;
        }
      }
    }
    ofl.write('\n');

    // Save all other lines
    DefaultTableModel model = (DefaultTableModel) table.getModel();
    for (int i = 0; i < model.getRowCount(); i++) {
      for (int n = 0; n < model.getColumnCount(); n++) {
        if (n > 0)
          ofl.write(';');
        String value = (String) model.getValueAt(i, n);

        if (splitColumn[n]) {
          // Write split
          String[] a = value.split(" ", 2);
          ofl.write('"' + a[0] + '"');
          if (a.length == 2) {
            ofl.write(";\"" + a[1] + '"');
          }
        } else {
          // Write normally
          ofl.write('"' + value + '"');
        }
      }
      ofl.write('\n');
    }

    // Close
    ofl.close();
  }

  /**
   * Saves computed results. Format can be either HTML or CSV
   *
   * @param format Format to use
   * @param table  Table to save
   */
  private void save(SaveFormat format, JTable table) {
    if (yearComputed == 0) {
      // Nothing computed
      JOptionPane.showMessageDialog(this, "Výsledky nebyly (ještě) dopočítány.");
      return;
    }

    FileDialog dialog = new FileDialog(this, "Uložit jako " + format, FileDialog.SAVE);
    dialog.setVisible(true);

    String fileName = dialog.getFile();
    if (fileName == null)
      return; // Canceled

    File file = new File(dialog.getDirectory(), fileName);

    // Add .format if not present
    String name = file.getName();
    if (name.lastIndexOf('.') < 0)
      file = new File(file.getParent(), name + "." + format);

    // Check whether file exists
    if (file.exists()) {
      if (JOptionPane.showConfirmDialog(this, "Soubor " + file.getAbsolutePath() + " již existuje, chcete jej přepsat?",
          "Přepsat soubor?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
        return;
    }

    String[] SPLIT_COLUMNS = { "J. cena", "Poplatky", "Dividenda" };

    try {
      if (table == diviTable) {
        if (format == SaveFormat.HTML)
          saveDiviHTML("Shrnutí dividend za rok " + yearComputed, file);
        else
          saveCSV("Shrnutí dividend za rok " + yearComputed, file, diviTable, SPLIT_COLUMNS, " měna");
      } else {
        String instrumentName;
        if (table == tableCP)
          instrumentName = "cenných papírů";
        else if (table == tableCash)
          instrumentName = "kurzových zisků";
        else
          instrumentName = "derivátů";
        String title = includeOverTaxFreeDurarionComputed
            ? ("Výsledky obchodování " + instrumentName + " za rok " + yearComputed)
            : ("Podklad pro daňové přiznání " + instrumentName + " za rok " + yearComputed);
        if (format == SaveFormat.HTML)
          saveHTML(title, file, table);
        else
          saveCSV(title, file, table, SPLIT_COLUMNS, " měna");
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Chyba při ukládání: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Utility function to format date + time
   */
  private String formatDateTime(Date date) {
    GregorianCalendar cal = new GregorianCalendar();
    DecimalFormat d2 = new DecimalFormat("00");
    cal.setTime(date);

    return d2.format(cal.get(GregorianCalendar.DAY_OF_MONTH)) + "." + d2.format(cal.get(GregorianCalendar.MONTH) + 1)
        + "." + cal.get(GregorianCalendar.YEAR) + " " + d2.format(cal.get(GregorianCalendar.HOUR_OF_DAY)) + ":"
        + d2.format(cal.get(GregorianCalendar.MINUTE));
  }

  /**
   * Utility function to format just date
   */
  private String formatDate(Date date) {
    GregorianCalendar cal = new GregorianCalendar();
    DecimalFormat d2 = new DecimalFormat("00");
    cal.setTime(date);

    return d2.format(cal.get(GregorianCalendar.DAY_OF_MONTH)) + "." + d2.format(cal.get(GregorianCalendar.MONTH) + 1)
        + "." + cal.get(GregorianCalendar.YEAR);
  }

  /**
   * Convert spaces to &nbsp; entities
   */
  private String spaces2nbsp(String s) {
    return s.replaceAll(" ", "&nbsp;");
  }

  /**
   * Clear results of tax computing
   */
  public void clearComputeResults() {
    ((DefaultTableModel) tableCP.getModel()).setNumRows(0);
    ((DefaultTableModel) tableDer.getModel()).setNumRows(0);
    ((DefaultTableModel) tableCash.getModel()).setNumRows(0);

  }

  /**
   * Compute dividends
   */
  private void computeDividends(int year) {
    TransactionSet transactions = mainWindow.getTransactionDatabase();
    GregorianCalendar cal = new GregorianCalendar();
    // int year = Integer.parseInt(eYear.getText());

    DecimalFormat d2 = new DecimalFormat("00");
    DecimalFormat dn = new DecimalFormat("#");
    DecimalFormat fn = new DecimalFormat("0.00#####");
    fn.setGroupingUsed(true);
    fn.setGroupingSize(3);
    DecimalFormat f2 = new DecimalFormat("0.00");
    f2.setGroupingUsed(true);
    f2.setGroupingSize(3);

    // Sort transactions before we proceed
    transactions.sort();

    // Clear model
    DefaultTableModel model = (DefaultTableModel) diviTable.getModel();
    model.setNumRows(0);

    // Dividend holder
    Dividends divis = new Dividends();

    try {
      for (Iterator<Transaction> i = transactions.iterator(); i.hasNext();) {
        Transaction tx = i.next();

        // Check if we are not over the year
        cal.setTime(tx.getExecutionDate());
        int ty = cal.get(GregorianCalendar.YEAR);
        if (ty != year)
          continue; // Ignore this transaction - we can't just break, since execution dates may not
                    // be in order

        // Apply transaction
        divis.applyTransaction(tx);
      }
    } catch (Exception ex) {
      // Clear model
      model.setNumRows(0);

      // Show error message
      JOptionPane.showMessageDialog(this,
          "V průběhu výpočtu došlo k chybě:\n\n" + ex.getMessage() + "\n\nVýpočet byl přerušen.", "Chyba",
          JOptionPane.ERROR_MESSAGE);

      return;
    }

    // Put dividends into model & compute summaries
    double sumDivi = 0;
    double sumTaxes = 0;

    Dividends.Dividend[] ds = divis.getDividends();
    for (int i = 0; i < ds.length; i++) {
      Vector<String> row = new Vector<String>();
      Dividends.Dividend d = ds[i];

      row.add(formatDate(d.date));
      row.add(d.ticker);

      // Add dividend
      if (d.dividendCurrency != null) {
        row.add(f2.format(d.dividend) + " " + d.dividendCurrency);

        double czk = Stocks.roundToHellers(d.dividend * Settings.getExchangeRate(d.dividendCurrency, d.date));
        row.add(f2.format(czk));

        sumDivi += czk;
      } else {
        row.add("");
        row.add("");
      }

      // Add tax
      if (d.taxCurrency != null) {
        row.add(f2.format(d.tax) + " " + d.taxCurrency);

        double czk = Stocks.roundToHellers(d.tax * Settings.getExchangeRate(d.taxCurrency, d.date));
        row.add(f2.format(czk));

        sumTaxes += czk;
      } else {
        row.add("");
        row.add("");
      }

      model.addRow(row);
    }

    // Add summary
    String row[] = { "", "", "", "-----------", "", "-----------" };
    model.addRow(row);
    String row2[] = { "", "", "", f2.format(sumDivi), "", f2.format(sumTaxes) };
    model.addRow(row2);
  }

  private void saveSettings() {
    // Store settings with which we computes
    Settings.setComputeYear(yearComputed);
    Settings.setAllowShortOverYearBoundary(cbAllowShortOverYearBoundary.isSelected());
    Settings.setNoIncomeTrades(cbNoIncome.getSelectedIndex());
    Settings.setOverTaxFreeDuration(cbOverTaxFreeDuration.getSelectedIndex());
    Settings.setSeparateCurrencyInCSVExport(cbSeparateCurrencyCSV.isSelected());
    Settings.save();
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

    jPanel1 = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    eYear = new javax.swing.JTextField();
    jLabel3 = new javax.swing.JLabel();
    cbOverTaxFreeDuration = new javax.swing.JComboBox();
    jLabel4 = new javax.swing.JLabel();
    cbNoIncome = new javax.swing.JComboBox();
    cbAllowShortOverYearBoundary = new javax.swing.JCheckBox();
    cbSeparateCurrencyCSV = new javax.swing.JCheckBox();
    bCompute = new javax.swing.JButton();
    jSeparator1 = new javax.swing.JSeparator();
    bClose = new javax.swing.JButton();
    jTabbedPane1 = new javax.swing.JTabbedPane();
    pCP = new javax.swing.JPanel();
    bSaveCSVCP = new javax.swing.JButton();
    bSaveHTMLCP = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableCP = new javax.swing.JTable();
    pDerivates = new javax.swing.JPanel();
    bSaveCSVDer = new javax.swing.JButton();
    bSaveHTMLDer = new javax.swing.JButton();
    jScrollPane3 = new javax.swing.JScrollPane();
    tableDer = new javax.swing.JTable();
    pCash = new javax.swing.JPanel();
    bSaveCSVCash = new javax.swing.JButton();
    bSaveHTMLCash = new javax.swing.JButton();
    jScrollPane4 = new javax.swing.JScrollPane();
    tableCash = new javax.swing.JTable();
    jPanel2 = new javax.swing.JPanel();
    jLabel2 = new javax.swing.JLabel();
    cbComputeDivi = new javax.swing.JCheckBox();
    bSaveCSVDivi = new javax.swing.JButton();
    bSaveHTMLDivi = new javax.swing.JButton();
    jScrollPane2 = new javax.swing.JScrollPane();
    diviTable = new javax.swing.JTable();

    setTitle("Výpočet základu pro DP nebo výsledku obchodování");
    setMaximumSize(new java.awt.Dimension(1069, 232));
    addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowOpened(java.awt.event.WindowEvent evt) {
        formWindowOpened(evt);
      }
    });
    getContentPane().setLayout(new java.awt.GridBagLayout());

    jPanel1.setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Rok:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(jLabel1, gridBagConstraints);

    eYear.setColumns(4);
    eYear.setText("2006");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(eYear, gridBagConstraints);

    jLabel3.setText("Obchody s CP mimo DP:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(jLabel3, gridBagConstraints);
    jLabel3.getAccessibleContext().setAccessibleDescription("");

    cbOverTaxFreeDuration
        .setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Zobrazit ve výstupu, nepočítat do základu",
            "Zobrazit ve výstupu, počítat do základu", "Nezobrazit ani nepočítat" }));
    cbOverTaxFreeDuration
        .setToolTipText("Jak naložit s obchody s cennými papíry, které splňují daňový test (6m do 2013, 3r od 2014)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(cbOverTaxFreeDuration, gridBagConstraints);

    jLabel4.setText("Obchody bez příjmu:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(jLabel4, gridBagConstraints);

    cbNoIncome.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Zobrazit ve výstupu, nepočítat do základu",
        "Zobrazit ve výstupu, počítat do základu", "Nezobrazit ani nepočítat" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(cbNoIncome, gridBagConstraints);

    cbAllowShortOverYearBoundary.setText("Povolit obchody nakrátko přes přelom roku");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel1.add(cbAllowShortOverYearBoundary, gridBagConstraints);

    cbSeparateCurrencyCSV.setText("Oddělit měnu v CSV exportu");
    cbSeparateCurrencyCSV.setToolTipText(
        "Pokud je zaškrtnuto. sloupce J. cena, Poplatky a Dividenda budou v CSV exportu rozdělené na číslo a měnu - nad tímto exportem se lépe dělají výpočty");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
    jPanel1.add(cbSeparateCurrencyCSV, gridBagConstraints);

    bCompute.setText("Spočítat");
    bCompute.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bComputeActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    jPanel1.add(bCompute, gridBagConstraints);

    jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
    jPanel1.add(jSeparator1, gridBagConstraints);

    bClose.setText("Zavřít");
    bClose.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bCloseActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    jPanel1.add(bClose, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    getContentPane().add(jPanel1, gridBagConstraints);

    pCP.setLayout(new java.awt.GridBagLayout());

    bSaveCSVCP.setText("Uložit CSV");
    bSaveCSVCP.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveCSVCPActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 0);
    pCP.add(bSaveCSVCP, gridBagConstraints);

    bSaveHTMLCP.setText("Uložit HTML");
    bSaveHTMLCP.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveHTMLCPActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
    pCP.add(bSaveHTMLCP, gridBagConstraints);

    tableCP.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null }
        },
        new String[] {
            "Otevřeno", "Ticker", "Počet", "Kurz", "J. cena", "Poplatky", "Otevření CZK", "Zavřeno", "Ticker", "Počet",
            "Kurz", "J. cena", "Poplatky", "Zavření CZK", "Výsledek CZK", "Poznámka"
        }) {
      Class[] types = new Class[] {
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
      };
      boolean[] canEdit = new boolean[] {
          false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false
      };

      public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
      }
    });
    jScrollPane1.setViewportView(tableCP);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 2.0;
    pCP.add(jScrollPane1, gridBagConstraints);

    jTabbedPane1.addTab("Cenné papíry", pCP);

    pDerivates.setLayout(new java.awt.GridBagLayout());

    bSaveCSVDer.setText("Uložit CSV");
    bSaveCSVDer.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveCSVDerActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 0);
    pDerivates.add(bSaveCSVDer, gridBagConstraints);

    bSaveHTMLDer.setText("Uložit HTML");
    bSaveHTMLDer.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveHTMLDerActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
    pDerivates.add(bSaveHTMLDer, gridBagConstraints);

    tableDer.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null }
        },
        new String[] {
            "Otevřeno", "Ticker", "Počet", "Kurz", "J. cena", "Poplatky", "Otevření CZK", "Zavřeno", "Ticker", "Počet",
            "Kurz", "J. cena", "Poplatky", "Zavření CZK", "Výsledek CZK", "Poznámka"
        }) {
      Class[] types = new Class[] {
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
      };
      boolean[] canEdit = new boolean[] {
          false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false
      };

      public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
      }
    });
    jScrollPane3.setViewportView(tableDer);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 2.0;
    pDerivates.add(jScrollPane3, gridBagConstraints);

    jTabbedPane1.addTab("Deriváty", pDerivates);

    pCash.setLayout(new java.awt.GridBagLayout());

    bSaveCSVCash.setText("Uložit CSV");
    bSaveCSVCash.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveCSVCashActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 0);
    pCash.add(bSaveCSVCash, gridBagConstraints);

    bSaveHTMLCash.setText("Uložit HTML");
    bSaveHTMLCash.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveHTMLCashActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
    pCash.add(bSaveHTMLCash, gridBagConstraints);

    tableCash.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null },
            { null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null }
        },
        new String[] {
            "Otevřeno", "Ticker", "Počet", "Kurz", "J. cena", "Poplatky", "Otevření CZK", "Zavřeno", "Ticker", "Počet",
            "Kurz", "J. cena", "Poplatky", "Zavření CZK", "Výsledek CZK", "Poznámka"
        }) {
      Class[] types = new Class[] {
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
      };
      boolean[] canEdit = new boolean[] {
          false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false
      };

      public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
      }
    });
    jScrollPane4.setViewportView(tableCash);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 2.0;
    pCash.add(jScrollPane4, gridBagConstraints);

    jTabbedPane1.addTab("Cash", pCash);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 2.0;
    getContentPane().add(jTabbedPane1, gridBagConstraints);

    jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
    jLabel2.setText("Dividendy:     ");
    jPanel2.add(jLabel2);

    cbComputeDivi.setSelected(true);
    cbComputeDivi.setText("Počítat");
    cbComputeDivi.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    cbComputeDivi.setMargin(new java.awt.Insets(0, 0, 0, 0));
    jPanel2.add(cbComputeDivi);

    bSaveCSVDivi.setText("Uložit CSV");
    bSaveCSVDivi.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveCSVDiviActionPerformed(evt);
      }
    });
    jPanel2.add(bSaveCSVDivi);

    bSaveHTMLDivi.setText("Uložit HTML");
    bSaveHTMLDivi.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveHTMLDiviActionPerformed(evt);
      }
    });
    jPanel2.add(bSaveHTMLDivi);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    getContentPane().add(jPanel2, gridBagConstraints);

    diviTable.setModel(new javax.swing.table.DefaultTableModel(
        new Object[][] {

        },
        new String[] {
            "Datum", "Ticker", "Dividenda", "Dividenda CZK", "Zaplacená daň", "Zaplacená daň CZK"
        }) {
      Class[] types = new Class[] {
          java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class,
          java.lang.String.class, java.lang.String.class
      };
      boolean[] canEdit = new boolean[] {
          false, false, false, false, false, false
      };

      public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
      }
    });
    jScrollPane2.setViewportView(diviTable);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 2.0;
    getContentPane().add(jScrollPane2, gridBagConstraints);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void bSaveHTMLDiviActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bSaveHTMLDiviActionPerformed
  {// GEN-HEADEREND:event_bSaveHTMLDiviActionPerformed
    save(SaveFormat.HTML, diviTable);
  }// GEN-LAST:event_bSaveHTMLDiviActionPerformed

  private void bSaveCSVDiviActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bSaveCSVDiviActionPerformed
  {// GEN-HEADEREND:event_bSaveCSVDiviActionPerformed
    save(SaveFormat.CSV, diviTable);
  }// GEN-LAST:event_bSaveCSVDiviActionPerformed

  private void bCloseActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bCloseActionPerformed
  {// GEN-HEADEREND:event_bCloseActionPerformed
    setVisible(false);
  }// GEN-LAST:event_bCloseActionPerformed

  private void bComputeActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bComputeActionPerformed
  {// GEN-HEADEREND:event_bComputeActionPerformed
    /* Run computation */
    TransactionSet transactions = mainWindow.getTransactionDatabase();
    IncludeOverTaxFreeDuration overTaxFreeDuration;
    NoIncomeTrades noIncomeTrades;
    GregorianCalendar cal = new GregorianCalendar();
    int year = Integer.parseInt(eYear.getText());

    boolean allowShortsOverYearBorder = cbAllowShortOverYearBoundary.isSelected();

    switch (cbOverTaxFreeDuration.getSelectedIndex()) {
      case 1:
        overTaxFreeDuration = IncludeOverTaxFreeDuration.INCLUDE;
        break;
      case 2:
        overTaxFreeDuration = IncludeOverTaxFreeDuration.LEAVE_OUT;
        break;
      default:
        overTaxFreeDuration = IncludeOverTaxFreeDuration.SHOW_ONLY;
    }

    switch (cbNoIncome.getSelectedIndex()) {
      case 1:
        noIncomeTrades = NoIncomeTrades.INCLUDE;
        break;
      case 2:
        noIncomeTrades = NoIncomeTrades.LEAVE_OUT;
        break;
      default:
        noIncomeTrades = NoIncomeTrades.SHOW_ONLY;
    }

    yearComputed = 0;
    includeOverTaxFreeDurarionComputed = (overTaxFreeDuration == IncludeOverTaxFreeDuration.INCLUDE);

    DefaultTableModel modelCP = (DefaultTableModel) tableCP.getModel();
    DefaultTableModel modelDer = (DefaultTableModel) tableDer.getModel();
    DefaultTableModel modelCash = (DefaultTableModel) tableCash.getModel();

    DecimalFormat d2 = new DecimalFormat("00");
    DecimalFormat dn = new DecimalFormat("#");
    DecimalFormat fn = new DecimalFormat("0.00#####");
    fn.setGroupingUsed(true);
    fn.setGroupingSize(3);
    DecimalFormat f2 = new DecimalFormat("0.00");
    f2.setGroupingUsed(true);
    f2.setGroupingSize(3);
    DecimalFormat fRate = new DecimalFormat("0.0000");

    // Sort transactions before we proceed
    transactions.sort();

    // Clear models
    clearComputeResults();

    // Run computation
    Stocks stocks = new Stocks();
    double cp_sumCZK = 0;
    double cp_openCreditSumCZK = 0;
    double cp_openDebitSumCZK = 0;
    double cp_closeCreditSumCZK = 0;
    double cp_closeDebitSumCZK = 0;
    double der_sumCZK = 0;
    double der_openCreditSumCZK = 0;
    double der_openDebitSumCZK = 0;
    double der_closeCreditSumCZK = 0;
    double der_closeDebitSumCZK = 0;
    double cash_sumCZK = 0;
    double cash_openCreditSumCZK = 0;
    double cash_openDebitSumCZK = 0;
    double cash_closeCreditSumCZK = 0;
    double cash_closeDebitSumCZK = 0;

    try {
      for (Iterator<Transaction> i = transactions.iterator();;) {
        Stocks.StockTrade[] ts = null;
        boolean autoCloseShorts = false;

        if (i.hasNext()) {
          // We have a transaction to process
          Transaction tx = i.next();
          ts = stocks.applyTransaction(tx, true);
        } else {
          // End of transactions - find short trades opened in year we compute for that
          // are not yet closed and add them
          ts = stocks.autocloseShortTransactions(year);
          autoCloseShorts = true;
        }

        if (ts != null) {
          // Print out only transactions that happen in the year that interests us
          for (int n = 0; n < ts.length; n++) {
            Stocks.StockTrade t = ts[n];

            // Check year of the income
            cal.setTime(t.getIncomeDate());
            if (cal.get(GregorianCalendar.YEAR) == year) {
              // Determine row flags
              boolean cp = (t.secType == Stocks.SecType.STOCK);
              boolean cash = (t.secType == Stocks.SecType.CASH);
              boolean cpOverTaxFreeDuration = cp && Stocks.isOverTaxFreeDuration(t.open.date, t.close.date);
              boolean include = (overTaxFreeDuration == IncludeOverTaxFreeDuration.INCLUDE) || (!cpOverTaxFreeDuration);
              boolean show = (overTaxFreeDuration != IncludeOverTaxFreeDuration.LEAVE_OUT) || (!cpOverTaxFreeDuration);

              /* Note */
              StringBuffer msg = new StringBuffer();

              if (show && (!t.doesIncome())) {
                switch (noIncomeTrades) {
                  case INCLUDE:
                    // Keep
                    break;
                  case LEAVE_OUT:
                    include = false;
                    show = false;
                    break;
                  case SHOW_ONLY:
                    include = false;
                    msg.append("Z obchodu není příjem; výdej nezapočítán.");
                    break;
                }
              }

              if (show) {
                Vector<String> rowData = new Vector<String>();

                /* Open */

                // Date
                rowData.add(formatDateTime(t.open.date));

                // Ticker
                rowData.add(t.open.ticker);

                // Amount
                rowData.add(f2.format(t.open.amount));

                // Kurz
                rowData.add(fRate.format(t.openRate));

                // Price
                if (t.open.priceCurrency != null)
                  rowData.add(fn.format(t.open.price) + " " + t.open.priceCurrency);
                else
                  rowData.add("0");

                // Fee
                if (t.open.feeCurrency != null)
                  rowData.add(f2.format(t.open.fee) + " " + t.open.feeCurrency);
                else
                  rowData.add("-");

                // Open sum
                if (include)
                  rowData.add(f2.format(t.openSumCZK));
                else
                  rowData.add("-");

                if (include) {
                  if (cp) {
                    cp_openCreditSumCZK += t.openCreditCZK;
                    cp_openDebitSumCZK += t.openDebitCZK;
                  } else if (cash) {
                    cash_openCreditSumCZK += t.openCreditCZK;
                    cash_openDebitSumCZK += t.openDebitCZK;
                  } else {
                    der_openCreditSumCZK += t.openCreditCZK;
                    der_openDebitSumCZK += t.openDebitCZK;
                  }
                }

                /* Close */

                if (!autoCloseShorts) {
                  /* Go normal */
                  // Date
                  rowData.add(formatDateTime(t.close.date));

                  // Ticker
                  rowData.add(t.close.ticker);

                  // Amount
                  rowData.add(f2.format(t.close.amount));

                  // Kurz Close
                  rowData.add(fRate.format(t.closeRate));

                  // Price
                  if (t.close.priceCurrency != null)
                    rowData.add(fn.format(t.close.price) + " " + t.close.priceCurrency);
                  else
                    rowData.add("0");

                  // Fee
                  if (t.close.feeCurrency != null)
                    rowData.add(f2.format(t.close.fee) + " " + t.close.feeCurrency);
                  else
                    rowData.add("-");

                  // Close sum
                  if (include) {
                    String v = f2.format(t.closeSumCZK); // Prepare content

                    // Check if this is a short over year's border
                    if ((!allowShortsOverYearBorder) && (t.open.amount < 0)) {
                      // Short trade && we do not allow them over year's border
                      cal.setTime(t.close.date);
                      if (cal.get(GregorianCalendar.YEAR) != year) {
                        // Closed in another year - do not count expenses for closing
                        t.closeSumCZK = 0;
                        t.profitCZK = t.openSumCZK;
                        msg.append("Obchod nakrátko přes přelom roku; náklad na zavření nezapočítán. ");
                        rowData.add("-");
                      } else {
                        rowData.add(v);
                      }
                    } else {
                      rowData.add(v);
                    }
                  } else
                    rowData.add("-");
                } else {
                  // Automatically closed short position - do not fill in close
                  rowData.add("-"); // Date
                  rowData.add("-"); // Ticker
                  rowData.add("-"); // Amount
                  rowData.add("-"); // Kurz
                  rowData.add("-"); // Price
                  rowData.add("-"); // Fee
                  rowData.add("-"); // Sum

                  msg.append("Neuzavřený obchod nakrátko");
                }

                if (include) {
                  if (cp) {
                    cp_closeCreditSumCZK += t.closeCreditCZK;
                    cp_closeDebitSumCZK += t.closeDebitCZK;
                  } else if (cash) {
                    cash_closeCreditSumCZK += t.closeCreditCZK;
                    cash_closeDebitSumCZK += t.closeDebitCZK;
                  } else {
                    der_closeCreditSumCZK += t.closeCreditCZK;
                    der_closeDebitSumCZK += t.closeDebitCZK;
                  }
                }

                /* Results */

                // Result in CZK
                if (include) {
                  rowData.add(f2.format(t.profitCZK));

                  if (cp)
                    cp_sumCZK += t.profitCZK;
                  else if (cash)
                    cash_sumCZK += t.profitCZK;
                  else
                    der_sumCZK += t.profitCZK;
                } else
                  rowData.add("-");

                if ((overTaxFreeDuration != IncludeOverTaxFreeDuration.INCLUDE) && (cpOverTaxFreeDuration)) {
                  if (t.open.date.before(Stocks.TAX_FREE_DURATION_BOUNDARY))
                    msg.append("Nad 6m. ");
                  else
                    msg.append("Nad 3r. ");
                }

                for (int j = 0; j < t.renames.length; j++) {
                  if (msg.length() > 0)
                    msg.append(", ");
                  Stocks.StockRename rename = t.renames[j];
                  msg.append(formatDateTime(rename.getDate()) + " ticker " + rename.getOldName() + " přejmenován na "
                      + rename.getNewName());
                }

                for (int j = 0; j < t.splits.length; j++) {
                  if (msg.length() > 0)
                    msg.append(", ");
                  Stocks.StockSplit split = t.splits[j];
                  msg.append(formatDateTime(split.getDate()) + " proveden " + split.getType() + " v poměru "
                      + split.getSRatio());
                }

                // Notes
                rowData.add(msg.toString());

                // Adding
                if (cp)
                  modelCP.addRow(rowData);
                else if (cash)
                  modelCash.addRow(rowData);
                else
                  modelDer.addRow(rowData);
              }
            }
          }
        } else {
          // ts == null -> end of transactions
          break;
        }
      }
    } catch (Stocks.TradingException ex) {
      // Clear model
      modelCP.setNumRows(0);
      modelDer.setNumRows(0);
      modelCash.setNumRows(0);

      // Show error message
      JOptionPane.showMessageDialog(this,
          "V průběhu výpočtu došlo k chybě:\n\n" + ex.getMessage() + "\n\nVýpočet byl přerušen.", "Chyba",
          JOptionPane.ERROR_MESSAGE);

      return;
    }

    // Add summary
    String row[] = { "", "", "", "", "", "", "-----------", "", "", "", "", "", "", "-----------", "-----------", "" };
    modelCP.addRow(row);
    modelDer.addRow(row);
    modelCash.addRow(row);

    String row3a[] = { "", "", "", "", "Příjem:", "", f2.format(cp_openCreditSumCZK), "", "", "", "", "", "",
        f2.format(cp_closeCreditSumCZK), f2.format(cp_openCreditSumCZK + cp_closeCreditSumCZK), "" };
    modelCP.addRow(row3a);
    String row3b[] = { "", "", "", "", "Příjem:", "", f2.format(der_openCreditSumCZK), "", "", "", "", "", "",
        f2.format(der_closeCreditSumCZK), f2.format(der_openCreditSumCZK + der_closeCreditSumCZK), "" };
    modelDer.addRow(row3b);
    String row3c[] = { "", "", "", "", "Příjem:", "", f2.format(cash_openCreditSumCZK), "", "", "", "", "", "",
        f2.format(cash_closeCreditSumCZK), f2.format(cash_openCreditSumCZK + cash_closeCreditSumCZK), "" };
    modelCash.addRow(row3c);

    String row4a[] = { "", "", "", "", "Výdej:", "", f2.format(cp_openDebitSumCZK), "", "", "", "", "", "",
        f2.format(cp_closeDebitSumCZK), f2.format(cp_openDebitSumCZK + cp_closeDebitSumCZK), "" };
    modelCP.addRow(row4a);
    String row4b[] = { "", "", "", "", "Výdej:", "", f2.format(der_openDebitSumCZK), "", "", "", "", "", "",
        f2.format(der_closeDebitSumCZK), f2.format(der_openDebitSumCZK + der_closeDebitSumCZK), "" };
    modelDer.addRow(row4b);
    String row4c[] = { "", "", "", "", "Výdej:", "", f2.format(cash_openDebitSumCZK), "", "", "", "", "", "",
        f2.format(cash_closeDebitSumCZK), f2.format(cash_openDebitSumCZK + cash_closeDebitSumCZK), "" };
    modelCash.addRow(row4c);

    String row2a[] = { "", "", "", "", "Zisk:", "", "", "", "", "", "", "", "", "", f2.format(cp_sumCZK), "" };
    modelCP.addRow(row2a);
    String row2b[] = { "", "", "", "", "Zisk:", "", "", "", "", "", "", "", "", "", f2.format(der_sumCZK), "" };
    modelDer.addRow(row2b);
    String row2c[] = { "", "", "", "", "Zisk:", "", "", "", "", "", "", "", "", "", f2.format(cash_sumCZK), "" };
    modelCash.addRow(row2c);

    yearComputed = year;

    if (cbComputeDivi.isSelected())
      computeDividends(year); // Compute dividends
    else
      ((DefaultTableModel) (diviTable.getModel())).setNumRows(0); // Clear dividend table

    saveSettings();
  }// GEN-LAST:event_bComputeActionPerformed

  private void formWindowOpened(java.awt.event.WindowEvent evt)// GEN-FIRST:event_formWindowOpened
  {// GEN-HEADEREND:event_formWindowOpened

    /* Load settings */
    eYear.setText(Integer.toString(Settings.getComputeYear()));
    cbAllowShortOverYearBoundary.setSelected(Settings.getAllowShortOverYearBoundary());
    cbNoIncome.setSelectedIndex(Settings.getNoIncomeTrades());
    cbOverTaxFreeDuration.setSelectedIndex(Settings.getOverTaxFreeDuration());
    cbSeparateCurrencyCSV.setSelected(Settings.getSeparateCurrencyInCSVExport());

  }// GEN-LAST:event_formWindowOpened

  private void bSaveCSVCPActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveCSVCPActionPerformed
    save(SaveFormat.CSV, tableCP);
  }// GEN-LAST:event_bSaveCSVCPActionPerformed

  private void bSaveHTMLCPActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveHTMLCPActionPerformed
    save(SaveFormat.HTML, tableCP);
  }// GEN-LAST:event_bSaveHTMLCPActionPerformed

  private void bSaveCSVDerActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveCSVDerActionPerformed
    save(SaveFormat.CSV, tableDer);
  }// GEN-LAST:event_bSaveCSVDerActionPerformed

  private void bSaveHTMLDerActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveHTMLDerActionPerformed
    save(SaveFormat.HTML, tableDer);
  }// GEN-LAST:event_bSaveHTMLDerActionPerformed

  private void bSaveCSVCashActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bSaveCSVCashActionPerformed
  {// GEN-HEADEREND:event_bSaveCSVCashActionPerformed
    save(SaveFormat.CSV, tableCash);
  }// GEN-LAST:event_bSaveCSVCashActionPerformed

  private void bSaveHTMLCashActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bSaveHTMLCashActionPerformed
  {// GEN-HEADEREND:event_bSaveHTMLCashActionPerformed
    save(SaveFormat.HTML, tableCash);
  }// GEN-LAST:event_bSaveHTMLCashActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bClose;
  private javax.swing.JButton bCompute;
  private javax.swing.JButton bSaveCSVCP;
  private javax.swing.JButton bSaveCSVCash;
  private javax.swing.JButton bSaveCSVDer;
  private javax.swing.JButton bSaveCSVDivi;
  private javax.swing.JButton bSaveHTMLCP;
  private javax.swing.JButton bSaveHTMLCash;
  private javax.swing.JButton bSaveHTMLDer;
  private javax.swing.JButton bSaveHTMLDivi;
  private javax.swing.JCheckBox cbAllowShortOverYearBoundary;
  private javax.swing.JCheckBox cbComputeDivi;
  private javax.swing.JComboBox cbNoIncome;
  private javax.swing.JComboBox cbOverTaxFreeDuration;
  private javax.swing.JCheckBox cbSeparateCurrencyCSV;
  private javax.swing.JTable diviTable;
  private javax.swing.JTextField eYear;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel4;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JScrollPane jScrollPane3;
  private javax.swing.JScrollPane jScrollPane4;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JTabbedPane jTabbedPane1;
  private javax.swing.JPanel pCP;
  private javax.swing.JPanel pCash;
  private javax.swing.JPanel pDerivates;
  private javax.swing.JTable tableCP;
  private javax.swing.JTable tableCash;
  private javax.swing.JTable tableDer;
  private javax.swing.JLabel lConvMethod;
  // End of variables declaration//GEN-END:variables

}
