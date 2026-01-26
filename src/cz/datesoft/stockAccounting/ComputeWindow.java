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
import java.awt.Desktop;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

  private static final DateTimeFormatter CZ_DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private static boolean isEndOfYearTradeDateForYear(java.util.Date d, int year) {
    if (d == null)
      return false;
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(d);
    int y = cal.get(GregorianCalendar.YEAR);
    int m = cal.get(GregorianCalendar.MONTH) + 1;
    int day = cal.get(GregorianCalendar.DAY_OF_MONTH);
    return y == year && m == 12 && (day == 29 || day == 30 || day == 31);
  }

  private int countSameSettlementOnYearEnd(TransactionSet transactions, int year) {
    if (transactions == null)
      return 0;
    int anySame = 0;
    for (Iterator<Transaction> it = transactions.iterator(); it.hasNext();) {
      Transaction tx = it.next();
      if (tx == null)
        continue;
      java.util.Date dTrade = tx.getDate();
      if (!isEndOfYearTradeDateForYear(dTrade, year))
        continue;
      java.util.Date dSettle = tx.getExecutionDate();
      if (dSettle != null && dSettle.equals(dTrade)) {
        anySame++;
      }
    }
    return anySame;
  }

  private void warnAboutSettlementDatesForYearIfNeeded(TransactionSet transactions, int year) {
    int anySame = countSameSettlementOnYearEnd(transactions, year);
    if (anySame <= 0)
      return;

    StringBuilder msg = new StringBuilder();
    msg.append("Pozor na datum vypořádání u obchodů na konci roku ").append(year).append(".\n\n");
    msg.append("Celkem: ").append(anySame)
        .append(" transakcí (29.12–31.12.").append(year)
        .append(") má 'Datum vypořádání' shodné s 'Datum'.\n\n");
    msg.append(
        "Doporučení: ověřte skutečné datum vypořádání u brokera (zejména u obchodů 31.12) a případně jej ručně upravte ve sloupci 'Datum vypořádání'.");

    JOptionPane.showMessageDialog(this, msg.toString(), "Upozornění: datum vypořádání", JOptionPane.WARNING_MESSAGE);
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

  private static class CpTaxExportTrade {
    String currency;
    String ticker;
    Date openTradeDate;
    Date openExecutionDate;
    Date closeTradeDate;
    Date closeExecutionDate;
    Date incomeTradeDate;
    Date incomeExecutionDate;
    double taxIncomeCZK;
    double taxExpenseCZK;
    double profitCZK;
  }

  private final java.util.List<CpTaxExportTrade> lastComputedCpTaxTrades = new java.util.ArrayList<>();

  private void saveWindowBounds() {
    try {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      java.awt.Point pt = getLocationOnScreen();
      java.awt.Dimension d = getSize();
      p.putInt("computeWindow.x", pt.x);
      p.putInt("computeWindow.y", pt.y);
      p.putInt("computeWindow.w", d.width);
      p.putInt("computeWindow.h", d.height);
    } catch (Exception e) {
      // ignore
    }
  }

  /** Creates new form ComputeDialog */
  public ComputeWindow(java.awt.Frame parent, boolean modal) {
    super(parent, modal);
    mainWindow = (MainWindow) parent;
    initComponents();

    // Respect user window management (no forced fullscreen).
    setLocationByPlatform(true);
    try {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      int w = p.getInt("computeWindow.w", 1100);
      int h = p.getInt("computeWindow.h", 760);
      int x = p.getInt("computeWindow.x", Integer.MIN_VALUE);
      int y = p.getInt("computeWindow.y", Integer.MIN_VALUE);
      setSize(new java.awt.Dimension(Math.max(900, w), Math.max(650, h)));
      if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
        setLocation(x, y);
      }
    } catch (Exception e) {
      setSize(new java.awt.Dimension(1100, 760));
    }

    addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override
      public void componentMoved(java.awt.event.ComponentEvent e) {
        saveWindowBounds();
      }

      @Override
      public void componentResized(java.awt.event.ComponentEvent e) {
        saveWindowBounds();
      }
    });
    // setLocation(0, 0);
    // setSize(java.awt.Toolkit.getDefaultToolkit().getScreenSize()); #worked well
    // on single monitor only
    // setSize(width, height);

    // Set right-aligning renderer for columns that need it
    DefaultTableCellRenderer rarenderer = new DefaultTableCellRenderer();
    rarenderer.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);

    yearComputed = 0;

    // Initialize conversion method toggle
    cbUseDailyRatesCompute = new javax.swing.JCheckBox();
    cbUseDailyRatesCompute.setText("Používat denní kurzy");
    cbUseDailyRatesCompute.setSelected(Settings.getUseDailyRates());
    cbUseDailyRatesCompute.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        Settings.setUseDailyRates(cbUseDailyRatesCompute.isSelected());
        Settings.save();
      }
    });

    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    gbc.gridy = 1;
    gbc.gridx = 6; // After cbSeparateCurrencyCSV
    gbc.gridwidth = 3;
    gbc.anchor = java.awt.GridBagConstraints.WEST;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    jPanel1.add(cbUseDailyRatesCompute, gbc);

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

  private void saveHTMLHeaderNewTrades(java.io.PrintWriter ofl, String title, javax.swing.JTable tbl) {
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

    // Header line (18 columns): insert Poplatky CZK after open+close Poplatky
    TableColumnModel cm = tbl.getColumnModel();
    ofl.println("<tr>");
    for (int i = 0; i < tbl.getColumnCount(); i++) {
      ofl.write("<th>" + (String) cm.getColumn(i).getHeaderValue() + "</th>");
      if (i == 5) {
        ofl.write("<th>Poplatky CZK (otev.)</th>");
      }
      if (i == 12) {
        ofl.write("<th>Poplatky CZK (zav.)</th>");
      }
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

  private void saveHtmlNewTrades(JTable table) {
    if (yearComputed == 0) {
      JOptionPane.showMessageDialog(this, "Výsledky nebyly (ještě) dopočítány.");
      return;
    }

    FileDialog dialog = new FileDialog(this, "Uložit jako HTML new", FileDialog.SAVE);
    dialog.setVisible(true);
    String fileName = dialog.getFile();
    if (fileName == null)
      return;

    File file = new File(dialog.getDirectory(), fileName);
    if (file.getName().lastIndexOf('.') < 0) {
      file = new File(file.getParent(), file.getName() + ".html");
    }

    if (file.exists()) {
      if (JOptionPane.showConfirmDialog(this,
          "Soubor " + file.getAbsolutePath() + " již existuje, chcete jej přepsat?",
          "Přepsat soubor?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
        return;
      }
    }

    String instrumentName;
    if (table == tableCP) {
      instrumentName = "cenných papírů";
    } else if (table == tableCash) {
      instrumentName = "kurzových zisků";
    } else {
      instrumentName = "derivátů";
    }
    String title = includeOverTaxFreeDurarionComputed
        ? ("Výsledky obchodování " + instrumentName + " za rok " + yearComputed)
        : ("Podklad pro daňové přiznání " + instrumentName + " za rok " + yearComputed);

    try {
      saveHTMLNewTrades(title, file, table);
      promptOpenSavedHtml(file);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Chyba při ukládání: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void promptOpenSavedHtml(File file) {
    if (file == null)
      return;
    int r = JOptionPane.showConfirmDialog(this,
        "Chcete otevřít uložený HTML soubor v prohlížeči?\n\n" + file.getAbsolutePath(),
        "Otevřít HTML", JOptionPane.YES_NO_OPTION);
    if (r != JOptionPane.YES_OPTION)
      return;

    try {
      if (!Desktop.isDesktopSupported()) {
        JOptionPane.showMessageDialog(this,
            "Otevření v prohlížeči není podporováno na této platformě.\n" + file.getAbsolutePath());
        return;
      }
      Desktop d = Desktop.getDesktop();
      if (d.isSupported(Desktop.Action.BROWSE)) {
        d.browse(file.toURI());
      } else if (d.isSupported(Desktop.Action.OPEN)) {
        d.open(file);
      } else {
        JOptionPane.showMessageDialog(this, "Otevření v prohlížeči není podporováno.\n" + file.getAbsolutePath());
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Nelze otevřít soubor: " + e.getMessage() + "\n" + file.getAbsolutePath());
    }
  }

  private void saveHTMLNewTrades(String title, File file, JTable table) throws Exception {
    try (java.io.PrintWriter ofl = new java.io.PrintWriter(new java.io.FileWriter(file))) {
      // CSS and Header
      ofl.println("<!DOCTYPE html>");
      ofl.println("<html><head><title>" + title + "</title>");
      ofl.println("<meta charset=\"utf-8\">");
      ofl.println("<style>");
      ofl.println(
          "body { font-family: 'Roboto Condensed', sans-serif; color: #777; font-size: 14px; background-color: #f9f9f9; padding: 20px; }");
      ofl.println("h1, h2 { color: #333; }");
      ofl.println(
          "table { width: 100%; border-collapse: separate; border-spacing: 0; margin-bottom: 20px; background-color: white; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }");
      ofl.println(
          "th { background-color: #028af4; color: white; padding: 8px 5px; text-align: left; font-weight: bold; border-left: 1px solid rgba(255,255,255,0.2); }");
      ofl.println("th:first-child { border-left: none; }");
      ofl.println("td { padding: 5px; border-bottom: 1px solid #eee; color: #555; }");
      ofl.println("tr:hover td { background-color: #f1f8ff; }");
      ofl.println(".align-right { text-align: right; }");
      ofl.println(".center { text-align: center; }");
      ofl.println(".bold { font-weight: bold; }");
      ofl.println(".red { color: red; }");
      ofl.println(".green { color: green; }");
      ofl.println(".border-top td { border-top: 1px solid #aaa; background-color: #fafafa; font-weight: bold; }");
      ofl.println(".section-nav { margin: 10px 0 22px 0; }");
      ofl.println(
          ".section-nav a { display: inline-block; margin-right: 10px; padding: 6px 10px; background: #fff; border: 1px solid #e6e6e6; border-radius: 8px; text-decoration: none; color: #028af4; }");
      ofl.println(".section-nav a:hover { background: #f1f8ff; }");
      ofl.println("</style></head><body>");

      ofl.println("<h1>" + title + "</h1>");

      boolean isCpTable = (table == tableCP);
      boolean unifiedRates = !Settings.getUseDailyRates();

      if (isCpTable && unifiedRates) {
        writeKackaLikeCpSections(ofl);
      } else if (isCpTable && !unifiedRates) {
        ofl.println(
            "<p><b>Poznámka:</b> Používáte denní kurzy (ČNB). Souhrny ve stylu Kačky (Souhrn/Výstupy podle roků/akcií) se generují pouze pro jednotný kurz.</p>");
      }

      DefaultTableModel model = (DefaultTableModel) table.getModel();

      // Group rows by currency
      java.util.Map<String, java.util.List<Integer>> rowsByCurrency = new java.util.TreeMap<>();
      java.util.List<Integer> summaryRows = new java.util.ArrayList<>();

      int rowCount = model.getRowCount();
      for (int i = 0; i < rowCount; i++) {
        String col0 = (String) model.getValueAt(i, 0);
        if (col0 == null || col0.trim().isEmpty()) {
          // Likely a summary row or empty separator
          // If it has content in other columns, it is summary
          String colProfit = (String) model.getValueAt(i, 14);
          if (colProfit != null && !colProfit.trim().isEmpty()) {
            summaryRows.add(i);
          }
          continue;
        }

        // Determine currency from Column 4 (J. cena) -> "100.00 USD"
        String priceStr = (String) model.getValueAt(i, 4);
        String cur = "Ostatní";
        if (priceStr != null && priceStr.contains(" ")) {
          cur = priceStr.substring(priceStr.lastIndexOf(" ") + 1).trim();
        }

        if (!rowsByCurrency.containsKey(cur)) {
          rowsByCurrency.put(cur, new java.util.ArrayList<>());
        }
        rowsByCurrency.get(cur).add(i);
      }

      // Process each currency group
      for (String currency : rowsByCurrency.keySet()) {
        ofl.println("<h2>Měna: " + currency + "</h2>");
        ofl.println("<table>");

        // Header
        ofl.println("<thead><tr>");
        ofl.println(
            "<th class=\"center\">Otevřeno</th><th>Ticker</th><th class=\"align-right\">Počet</th><th class=\"align-right\">Kurz (O)</th><th class=\"align-right\">Cena (O)</th><th class=\"align-right\">Popl. (O)</th><th class=\"align-right\">Popl. CZK</th><th class=\"align-right\">Výdaj CZK</th>");
        ofl.println(
            "<th class=\"center\">Zavřeno</th><th class=\"align-right\">Kurz (Z)</th><th class=\"align-right\">Cena (Z)</th><th class=\"align-right\">Popl. (Z)</th><th class=\"align-right\">Popl. CZK</th><th class=\"align-right\">Příjem CZK</th>");
        ofl.println("<th class=\"align-right\">Zisk CZK</th><th>Pozn.</th>");
        ofl.println("</tr></thead>");

        ofl.println("<tbody>");

        double sumProfit = 0;
        double sumExpense = 0;
        double sumIncome = 0;

        for (Integer r : rowsByCurrency.get(currency)) {
          ofl.println("<tr>");

          // 0: Otevřeno
          ofl.println("<td class=\"center\">" + val(model, r, 0) + "</td>"); // Date
          // 1: Ticker
          ofl.println("<td><b>" + val(model, r, 1) + "</b></td>");
          // 2: Počet
          ofl.println("<td class=\"align-right\">" + val(model, r, 2) + "</td>");
          // 3: Kurz (Open Rate)
          ofl.println("<td class=\"align-right\">" + val(model, r, 3) + "</td>");
          // 4: Cena (Open Price) -> Strip currency
          ofl.println("<td class=\"align-right\">" + stripCur(val(model, r, 4)) + "</td>");
          // 5: Popl (Open)
          ofl.println("<td class=\"align-right\">" + stripCur(val(model, r, 5)) + "</td>");
          // Computed: Popl CZK (Open)
          String openFeeCzk = computeFeeCzkCell(model, r, 0, 5);
          ofl.println("<td class=\"align-right text-muted\">" + openFeeCzk + "</td>");
          // 6: Otevření CZK (Expense)
          String expStr = val(model, r, 6);
          ofl.println("<td class=\"align-right\">" + expStr + "</td>");
          sumExpense += parseDouble(expStr);

          // 7: Zavřeno
          ofl.println("<td class=\"center\">" + val(model, r, 7) + "</td>");
          // 10: Kurz (Close Rate)
          ofl.println("<td class=\"align-right\">" + val(model, r, 10) + "</td>");
          // 11: Cena (Close Price)
          ofl.println("<td class=\"align-right\">" + stripCur(val(model, r, 11)) + "</td>");
          // 12: Popl (Close)
          ofl.println("<td class=\"align-right\">" + stripCur(val(model, r, 12)) + "</td>");
          // Computed: Popl CZK (Close)
          String closeFeeCzk = computeFeeCzkCell(model, r, 7, 12);
          ofl.println("<td class=\"align-right text-muted\">" + closeFeeCzk + "</td>");
          // 13: Zavření CZK (Income)
          String incStr = val(model, r, 13);
          ofl.println("<td class=\"align-right\">" + incStr + "</td>");
          sumIncome += parseDouble(incStr);

          // 14: Výsledek CZK (Profit)
          String profitStr = val(model, r, 14);
          double profit = parseDouble(profitStr);
          String colorClass = profit < 0 ? "red" : "green";
          ofl.println("<td class=\"align-right bold " + colorClass + "\">" + profitStr + "</td>");
          sumProfit += profit;

          // 15: Pozn
          ofl.println("<td>" + val(model, r, 15) + "</td>");

          ofl.println("</tr>");
        }

        // Footer / Totals for Currency
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");
        ofl.println("<tr class=\"border-top\">");
        ofl.println("<td colspan=\"7\">Celkem " + currency + "</td>");
        ofl.println("<td class=\"align-right\">" + df.format(sumExpense) + "</td>");
        ofl.println("<td colspan=\"5\"></td>");
        ofl.println("<td class=\"align-right\">" + df.format(sumIncome) + "</td>");
        String sumColor = sumProfit < 0 ? "red" : "green";
        ofl.println("<td class=\"align-right " + sumColor + "\">" + df.format(sumProfit) + "</td>");
        ofl.println("<td></td>");
        ofl.println("</tr>");

        ofl.println("</tbody></table>");
      }

      // Global Summary from original table rows (if found) or computed
      if (!summaryRows.isEmpty()) {
        ofl.println("<h2>Celkové součty (všechny měny v CZK)</h2>");
        ofl.println("<table><thead><tr><th>Popis</th><th class=\"align-right\">Hodnota</th></tr></thead><tbody>");
        for (Integer r : summaryRows) {
          String label = val(model, r, 5); // "Příjem:", "Výdej:", "Zisk:" are usually in col 5
          if (label.isEmpty())
            label = "Součet";
          // Value is scattered.
          // Income/Expense sums are in col 14 or scattered.
          // Let's rely on what's visible. Column 14 has totals often.
          String val14 = val(model, r, 14);
          if (!val14.isEmpty()) {
            ofl.println("<tr><td>" + label + "</td><td class=\"align-right bold\">" + val14 + "</td></tr>");
          }
        }
        ofl.println("</tbody></table>");
      }

      ofl.println("</body></html>");
    }
  }

  private String val(DefaultTableModel m, int r, int c) {
    if (m.getValueAt(r, c) == null)
      return "";
    return ((String) m.getValueAt(r, c)).trim();
  }

  private String stripCur(String s) {
    if (s == null)
      return "";
    int idx = s.lastIndexOf(" ");
    if (idx > 0)
      return s.substring(0, idx).trim(); // Remove currency suffix
    return s;
  }

  private static String htmlEscape(String s) {
    if (s == null)
      return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private void writeKackaLikeCpSections(java.io.PrintWriter ofl) {
    if (yearComputed == 0)
      return;

    // Work on a copy, sorted by income date.
    java.util.List<CpTaxExportTrade> trades = new java.util.ArrayList<>(lastComputedCpTaxTrades);
    trades.removeIf(t -> t == null || t.incomeExecutionDate == null);
    trades.sort((a, b) -> a.incomeExecutionDate.compareTo(b.incomeExecutionDate));

    java.util.Map<String, java.util.List<CpTaxExportTrade>> byCur = new java.util.TreeMap<>();
    java.util.Map<String, java.util.List<CpTaxExportTrade>> byTicker = new java.util.TreeMap<>();
    for (CpTaxExportTrade t : trades) {
      String cur = t.currency != null ? t.currency : "CZK";
      byCur.computeIfAbsent(cur, k -> new java.util.ArrayList<>()).add(t);

      String ticker = t.ticker != null ? t.ticker : "(bez tickeru)";
      byTicker.computeIfAbsent(ticker, k -> new java.util.ArrayList<>()).add(t);
    }

    java.text.DecimalFormat f2 = new java.text.DecimalFormat("0.00");
    f2.setGroupingUsed(true);
    f2.setGroupingSize(3);
    java.text.DecimalFormat fr = new java.text.DecimalFormat("0.00");
    fr.setGroupingUsed(true);
    fr.setGroupingSize(3);

    ofl.println("<div class=\"section-nav\">" +
        "<a href=\"#kacka-souhrn\">Souhrn</a>" +
        "<a href=\"#kacka-roky\">Výstupy podle jednotlivých roků</a>" +
        "<a href=\"#kacka-akcie\">Výstupy podle jednotlivých akcií</a>" +
        "</div>");

    // Souhrn
    ofl.println("<h2 id=\"kacka-souhrn\">Souhrn</h2>");
    ofl.println("<table>");
    ofl.println("<thead><tr>");
    ofl.println("<th colspan=\"5\">Souhrn pro rok " + yearComputed + "</th>");
    ofl.println("</tr>");
    ofl.println("<tr>");
    ofl.println("<th>Měna</th>");
    ofl.println("<th class=\"align-right\">Kurs</th>");
    ofl.println("<th class=\"align-right\">Daňové příjmy (CZK)</th>");
    ofl.println("<th class=\"align-right\">Daňové výdaje (CZK)</th>");
    ofl.println("<th class=\"align-right\">Zisk (CZK)</th>");
    ofl.println("</tr></thead>");
    ofl.println("<tbody>");

    for (String cur : byCur.keySet()) {
      double income = 0;
      double expense = 0;
      double profit = 0;
      for (CpTaxExportTrade t : byCur.get(cur)) {
        income += t.taxIncomeCZK;
        expense += t.taxExpenseCZK;
        profit += t.profitCZK;
      }

      double rate = 1.0;
      try {
        rate = Settings.getRatio(cur, yearComputed);
      } catch (Exception e) {
        rate = 1.0;
      }

      String id = "id" + yearComputed + htmlEscape(cur);
      ofl.println("<tr>");
      ofl.println("<td><a href=\"#" + id + "\">Obchody v " + htmlEscape(cur) + "</a></td>");
      ofl.println("<td class=\"align-right\">" + fr.format(rate) + "</td>");
      ofl.println("<td class=\"align-right\">" + f2.format(income) + "</td>");
      ofl.println("<td class=\"align-right\">" + f2.format(expense) + "</td>");
      String pClass = profit < 0 ? "red" : "green";
      ofl.println("<td class=\"align-right bold " + pClass + "\">" + f2.format(profit) + "</td>");
      ofl.println("</tr>");
    }
    ofl.println("</tbody></table>");

    // Výstupy podle jednotlivých roků (for selected year)
    ofl.println("<h2 id=\"kacka-roky\">Výstupy podle jednotlivých roků</h2>");
    ofl.println("<div class=\"section-nav\">" +
        "<a href=\"#id" + yearComputed + "\">" + yearComputed + "</a>" +
        "</div>");

    for (String cur : byCur.keySet()) {
      String id = "id" + yearComputed + htmlEscape(cur);
      ofl.println("<table id=\"" + id + "\">");
      ofl.println("<thead><tr>");
      ofl.println("<th colspan=\"6\">Rok " + yearComputed + " Měna: " + htmlEscape(cur) + "</th>");
      ofl.println("</tr><tr>");
      ofl.println("<th>Datum obchodu</th>");
      ofl.println("<th>Vypořádání</th>");
      ofl.println("<th>CP</th>");
      ofl.println("<th class=\"align-right\">Zisk (CZK)</th>");
      ofl.println("<th class=\"align-right\">Daňové příjmy (CZK)</th>");
      ofl.println("<th class=\"align-right\">Daňové výdaje (CZK)</th>");
      ofl.println("</tr></thead><tbody>");

      for (CpTaxExportTrade t : byCur.get(cur)) {
        String ticker = t.ticker != null ? t.ticker : "";
        String tickerAnchor = "id" + htmlEscape(ticker);
        String tradeDate = t.incomeTradeDate != null ? formatDateTime(t.incomeTradeDate) : "";
        String execDate = t.incomeExecutionDate != null ? formatDateTime(t.incomeExecutionDate) : "";
        String pClass = t.profitCZK < 0 ? "red" : "green";
        ofl.println("<tr>");
        ofl.println("<td>" + htmlEscape(tradeDate) + "</td>");
        ofl.println("<td>" + htmlEscape(execDate) + "</td>");
        ofl.println("<td><a href=\"#" + tickerAnchor + "\">" + htmlEscape(ticker) + "</a></td>");
        ofl.println("<td class=\"align-right bold " + pClass + "\">" + f2.format(t.profitCZK) + "</td>");
        ofl.println("<td class=\"align-right\">" + f2.format(t.taxIncomeCZK) + "</td>");
        ofl.println("<td class=\"align-right\">" + f2.format(t.taxExpenseCZK) + "</td>");
        ofl.println("</tr>");
      }
      ofl.println("</tbody></table>");
    }

    // Výstupy podle jednotlivých akcií (Kačka-like timeline)
    ofl.println("<h2 id=\"kacka-akcie\">Výstupy podle jednotlivých akcií</h2>");

    java.util.Set<String> tickersOfInterest = new java.util.TreeSet<>(byTicker.keySet());
    writeKackaLikeCpTickerTimeline(ofl, tickersOfInterest);
  }

  private static class SellAgg {
    String currency;
    double profitCur;
    double costBaseCur;
    double taxIncomeCZK;
    double taxExpenseCZK;
  }

  private void writeKackaLikeCpTickerTimeline(java.io.PrintWriter ofl, java.util.Set<String> tickersOfInterest) {
    if (tickersOfInterest == null || tickersOfInterest.isEmpty())
      return;

    // Small Kačka-like icons
    ofl.println("<style>");
    ofl.println(
        ".kicon { display:inline-block; width:16px; height:16px; border-radius:50%; line-height:16px; text-align:center; font-weight:bold; font-size:11px; color:#fff; }");
    ofl.println(".kicon.buy { background:#2e8b57; }");
    ofl.println(".kicon.sell { background:#c0392b; }");
    ofl.println(".krow-ca td { background:#fcfcfc; color:#666; }");
    ofl.println("</style>");

    // Build per-transaction sell aggregates and per-component timelines using a GLOBAL
    // FIFO replay. Corporate actions (TRANS_SUB/TRANS_ADD) often span multiple tickers
    // (e.g. ATNF -> ETHZ, CS -> UBS). Replaying per ticker breaks transformation pairing
    // and triggers "pouze jedna transformace" errors.
    java.util.Map<Integer, SellAgg> sellAggBySerial = new java.util.HashMap<>();

    TransactionSet txs = mainWindow.getTransactionDatabase();
    txs.sort();

    // End of year (inclusive)
    GregorianCalendar cal = new GregorianCalendar(yearComputed, 12 - 1, 31, 23, 59, 59);
    cal.set(GregorianCalendar.MILLISECOND, 999);
    java.util.Date endOfYear = cal.getTime();

    // Helper: relevant directions
    java.util.function.Predicate<Transaction> isRelevant = (tx) -> {
      if (tx == null)
        return false;
      int d = tx.getDirection();
      return d == Transaction.DIRECTION_SBUY || d == Transaction.DIRECTION_SSELL || d == Transaction.DIRECTION_TRANS_ADD
          || d == Transaction.DIRECTION_TRANS_SUB;
    };

    // 1) Collect candidate transactions up to end-of-year
    java.util.List<Transaction> candidates = new java.util.ArrayList<>();
    for (Iterator<Transaction> it = txs.iterator(); it.hasNext();) {
      Transaction tx = it.next();
      if (!isRelevant.test(tx))
        continue;
      java.util.Date eff = tx.getExecutionDate() != null ? tx.getExecutionDate() : tx.getDate();
      if (eff == null)
        continue;
      if (eff.after(endOfYear))
        continue;
      if (tx.getTicker() == null)
        continue;
      candidates.add(tx);
    }

    // 2) Build union-find components from corporate actions (TRANS_SUB/TRANS_ADD pairs)
    class UF {
      private final java.util.Map<String, String> parent = new java.util.HashMap<>();

      String norm(String t) {
        return t == null ? null : t.trim().toUpperCase();
      }

      String find(String t) {
        t = norm(t);
        if (t == null)
          return null;
        String p = parent.get(t);
        if (p == null) {
          parent.put(t, t);
          return t;
        }
        if (!p.equals(t))
          parent.put(t, find(p));
        return parent.get(t);
      }

      void union(String a, String b) {
        a = find(a);
        b = find(b);
        if (a == null || b == null)
          return;
        if (!a.equals(b))
          parent.put(a, b);
      }

      java.util.Set<String> all() {
        return new java.util.HashSet<>(parent.keySet());
      }
    }

    UF uf = new UF();
    java.util.Map<Long, java.util.List<Transaction>> transByTime = new java.util.HashMap<>();
    for (Transaction tx : candidates) {
      int d = tx.getDirection();
      if (d != Transaction.DIRECTION_TRANS_ADD && d != Transaction.DIRECTION_TRANS_SUB)
        continue;
      if (tx.getDate() == null)
        continue;
      long key = tx.getDate().getTime();
      transByTime.computeIfAbsent(Long.valueOf(key), k -> new java.util.ArrayList<>()).add(tx);
      uf.find(tx.getTicker());
    }

    for (java.util.List<Transaction> group : transByTime.values()) {
      if (group == null || group.size() < 2)
        continue;
      Transaction sub = null;
      Transaction add = null;
      for (Transaction tx : group) {
        if (tx.getDirection() == Transaction.DIRECTION_TRANS_SUB)
          sub = tx;
        else if (tx.getDirection() == Transaction.DIRECTION_TRANS_ADD)
          add = tx;
      }
      if (sub != null && add != null) {
        uf.union(sub.getTicker(), add.getTicker());
      }
    }

    // 3) Determine which components we want to print:
    // start from tickersOfInterest (taxable trades in selected year) and include all
    // tickers connected via corporate actions.
    java.util.Set<String> interest = new java.util.HashSet<>();
    for (String t : tickersOfInterest) {
      if (t == null)
        continue;
      interest.add(t.trim().toUpperCase());
    }

    java.util.Set<String> expandedTickers = new java.util.HashSet<>();
    // Ensure the UF knows about interest tickers too
    for (String t : interest)
      uf.find(t);

    java.util.Map<String, java.util.Set<String>> compMembers = new java.util.HashMap<>();
    for (String t : uf.all()) {
      String r = uf.find(t);
      compMembers.computeIfAbsent(r, k -> new java.util.HashSet<>()).add(t);
    }

    java.util.Set<String> compRootsToPrint = new java.util.HashSet<>();
    for (String t : interest) {
      String r = uf.find(t);
      if (r == null)
        continue;
      compRootsToPrint.add(r);
      java.util.Set<String> mem = compMembers.get(r);
      if (mem != null)
        expandedTickers.addAll(mem);
      else
        expandedTickers.add(t);
    }

    // Also include tickers that have no transformations but are in interest
    for (String t : interest) {
      if (!expandedTickers.contains(t))
        expandedTickers.add(t);
    }

    // 4) Global FIFO replay for all expanded tickers
    java.util.List<Transaction> global = new java.util.ArrayList<>();
    for (Transaction tx : candidates) {
      String t = tx.getTicker() != null ? tx.getTicker().trim().toUpperCase() : null;
      if (t == null)
        continue;
      if (!expandedTickers.contains(t))
        continue;
      global.add(tx);
    }
    global.sort((a, b) -> {
      int c1 = a.getDate().compareTo(b.getDate());
      if (c1 != 0)
        return c1;
      return Integer.compare(a.getSerial(), b.getSerial());
    });

    // Map (ticker|execMillis) to transaction serial for short autoclose mapping.
    java.util.Map<String, Integer> serialByTickerAndExec = new java.util.HashMap<>();
    for (Transaction tx : global) {
      if (tx.getTicker() == null)
        continue;
      if (tx.getDirection() != Transaction.DIRECTION_SSELL)
        continue;
      java.util.Date eff = tx.getExecutionDate() != null ? tx.getExecutionDate() : tx.getDate();
      if (eff == null)
        continue;
      String key = tx.getTicker().trim().toUpperCase() + "|" + eff.getTime();
      serialByTickerAndExec.put(key, Integer.valueOf(tx.getSerial()));
    }

    Stocks stocks = new Stocks();
    for (Transaction tx : global) {
      try {
        boolean useExec = false;
        int d = tx.getDirection();
        if ((d == Transaction.DIRECTION_SBUY || d == Transaction.DIRECTION_SSELL) && tx.getExecutionDate() != null)
          useExec = true;
        Stocks.StockTrade[] trades = stocks.applyTransaction(tx, useExec);
        if (trades == null)
          continue;

        for (Stocks.StockTrade st : trades) {
          int ownerSerial = tx.getSerial();

          String cur = null;
          if (st.close != null && st.close.priceCurrency != null)
            cur = st.close.priceCurrency;
          else if (st.open != null && st.open.priceCurrency != null)
            cur = st.open.priceCurrency;
          cur = cur != null ? cur.trim().toUpperCase() : "CZK";

          double openCash = -st.open.amount * st.open.price - st.open.fee;
          double closeCash = st.close.amount * st.close.price - st.close.fee;
          double profitCur = openCash + closeCash;

          double base = 0.0;
          if (openCash < 0)
            base = -openCash;
          else if (closeCash < 0)
            base = -closeCash;

          SellAgg agg = sellAggBySerial.get(ownerSerial);
          if (agg == null) {
            agg = new SellAgg();
            agg.currency = cur;
            sellAggBySerial.put(ownerSerial, agg);
          }
          agg.currency = cur;
          agg.profitCur += profitCur;
          agg.costBaseCur += base;

          GregorianCalendar yc = new GregorianCalendar();
          yc.setTime(st.getIncomeDate());
          int incomeYear = yc.get(GregorianCalendar.YEAR);
          if (incomeYear == yearComputed) {
            boolean overTest = Stocks.isOverTaxFreeDuration(st.open.executionDate, st.close.executionDate);
            if (!overTest) {
              agg.taxIncomeCZK += (st.openCreditCZK + st.closeCreditCZK);
              agg.taxExpenseCZK += -(st.openDebitCZK + st.closeDebitCZK);
            }
          }
        }
      } catch (Exception e) {
        // Keep export resilient
        System.err.println("Failed to compute Kačka-like FIFO data: " + e.getMessage());
      }
    }

    // Also include synthetic autoclose of open shorts in the selected year.
    try {
      Stocks.StockTrade[] shorts = stocks.autocloseShortTransactions(yearComputed);
      if (shorts != null) {
        for (Stocks.StockTrade st : shorts) {
          GregorianCalendar yc = new GregorianCalendar();
          yc.setTime(st.getIncomeDate());
          if (yc.get(GregorianCalendar.YEAR) != yearComputed)
            continue;

          if (st.open == null || st.open.executionDate == null || st.open.ticker == null)
            continue;

          String key = st.open.ticker.trim().toUpperCase() + "|" + st.open.executionDate.getTime();
          Integer ownerSerialObj = serialByTickerAndExec.get(key);
          if (ownerSerialObj == null)
            continue;
          int ownerSerial = ownerSerialObj.intValue();

          String cur = null;
          if (st.open != null && st.open.priceCurrency != null)
            cur = st.open.priceCurrency;
          else if (st.close != null && st.close.priceCurrency != null)
            cur = st.close.priceCurrency;
          cur = cur != null ? cur.trim().toUpperCase() : "CZK";

          double openCash = -st.open.amount * st.open.price - st.open.fee;
          double closeCash = st.close.amount * st.close.price - st.close.fee;
          double profitCur = openCash + closeCash;

          SellAgg agg = sellAggBySerial.get(ownerSerial);
          if (agg == null) {
            agg = new SellAgg();
            agg.currency = cur;
            sellAggBySerial.put(ownerSerial, agg);
          }
          agg.currency = cur;
          agg.profitCur += profitCur;
          if (openCash < 0)
            agg.costBaseCur += -openCash;
          agg.taxIncomeCZK += (st.openCreditCZK + st.closeCreditCZK);
          agg.taxExpenseCZK += -(st.openDebitCZK + st.closeDebitCZK);
        }
      }
    } catch (Exception e) {
      // ignore
    }

    // 5) Build per-component timelines
    java.util.Map<String, java.util.List<Transaction>> txByComponent = new java.util.HashMap<>();
    for (Transaction tx : global) {
      String t = tx.getTicker() != null ? tx.getTicker().trim().toUpperCase() : null;
      if (t == null)
        continue;
      String r = uf.find(t);
      if (r == null)
        r = t;
      if (!compRootsToPrint.contains(r) && !interest.contains(t))
        continue;
      txByComponent.computeIfAbsent(r, k -> new java.util.ArrayList<>()).add(tx);
    }

    // Determine display name for each component.
    java.util.Map<String, String> displayNameByRoot = new java.util.HashMap<>();
    for (String root : txByComponent.keySet()) {
      java.util.List<Transaction> list = txByComponent.get(root);
      list.sort((a, b) -> {
        int c1 = a.getDate().compareTo(b.getDate());
        if (c1 != 0)
          return c1;
        return Integer.compare(a.getSerial(), b.getSerial());
      });

      String display = null;
      for (Transaction tx : list) {
        if (tx.getDirection() == Transaction.DIRECTION_TRANS_ADD && tx.getTicker() != null) {
          display = tx.getTicker().trim().toUpperCase();
        }
      }
      if (display == null) {
        for (Transaction tx : list) {
          if (tx.getTicker() != null) {
            display = tx.getTicker().trim().toUpperCase();
          }
        }
      }
      if (display == null)
        display = root;
      displayNameByRoot.put(root, display);
    }

    // Nav links (sorted by display name)
    java.util.List<String> rootsSorted = new java.util.ArrayList<>(txByComponent.keySet());
    rootsSorted.sort((a, b) -> displayNameByRoot.get(a).compareTo(displayNameByRoot.get(b)));

    ofl.println("<div class=\"section-nav\">");
    for (String root : rootsSorted) {
      String disp = displayNameByRoot.get(root);
      String anchor = "id" + htmlEscape(disp);
      ofl.println("<a href=\"#" + anchor + "\">" + htmlEscape(disp) + "</a>");
    }
    ofl.println("</div>");

    // Nav links
    ofl.println("<div class=\"section-nav\">");
    java.text.DecimalFormat fQty = new java.text.DecimalFormat("0.####");
    fQty.setGroupingUsed(true);
    fQty.setGroupingSize(3);
    java.text.DecimalFormat fMoney = new java.text.DecimalFormat("0.00");
    fMoney.setGroupingUsed(true);
    fMoney.setGroupingSize(3);
    java.text.DecimalFormat fPct = new java.text.DecimalFormat("0.00");

    for (String root : rootsSorted) {
      java.util.List<Transaction> list = txByComponent.get(root);
      if (list == null || list.isEmpty())
        continue;

      list.sort((a, b) -> {
        int c1 = a.getDate().compareTo(b.getDate());
        if (c1 != 0)
          return c1;
        return Integer.compare(a.getSerial(), b.getSerial());
      });

      String display = displayNameByRoot.get(root);
      String headerCur = "CZK";
      for (Transaction tx : list) {
        if (tx != null && tx.getPriceCurrency() != null) {
          headerCur = tx.getPriceCurrency().trim().toUpperCase();
          break;
        }
      }

      String tickerAnchor = "id" + htmlEscape(display);
      ofl.println("<table id=\"" + tickerAnchor + "\">");
      ofl.println("<thead>");
      ofl.println("<tr><th class=\"bold\" colspan=\"12\">" + htmlEscape(display) + " ( " + htmlEscape(headerCur)
          + " )</th></tr>");
      ofl.println("<tr>");
      ofl.println("<th></th>");
      ofl.println("<th>Datum obchodu</th>");
      ofl.println("<th>Vypořádání</th>");
      ofl.println("<th class=\"align-right\">Počet</th>");
      ofl.println("<th class=\"align-right\">Cena</th>");
      ofl.println("<th class=\"align-right\">Poplatky</th>");
      ofl.println("<th class=\"align-right\">Objem</th>");
      ofl.println("<th class=\"align-right\">Zisk</th>");
      ofl.println("<th class=\"align-right\">%</th>");
      ofl.println("<th class=\"align-right\">Daňové příjmy (CZK)</th>");
      ofl.println("<th class=\"align-right\">Daňové výdaje (CZK)</th>");
      ofl.println("<th class=\"align-right\">Stav CP</th>");
      ofl.println("</tr>");
      ofl.println("</thead><tbody>");

      double pos = 0.0;
      double sumFeesCur = 0.0;
      double sumVolumeCur = 0.0;
      double sumProfitCur = 0.0;
      double sumIncomeCZK = 0.0;
      double sumExpenseCZK = 0.0;

      for (Transaction tx : list) {
        boolean isBuy = tx.getDirection() == Transaction.DIRECTION_SBUY;
        boolean isSell = tx.getDirection() == Transaction.DIRECTION_SSELL;
        boolean isTransSub = tx.getDirection() == Transaction.DIRECTION_TRANS_SUB;
        boolean isTransAdd = tx.getDirection() == Transaction.DIRECTION_TRANS_ADD;

        double qty = tx.getAmount() != null ? tx.getAmount().doubleValue() : 0.0;
        double price = tx.getPrice() != null ? tx.getPrice().doubleValue() : 0.0;
        double fee = tx.getFee() != null ? tx.getFee().doubleValue() : 0.0;

        String priceCur = tx.getPriceCurrency() != null ? tx.getPriceCurrency().trim().toUpperCase() : headerCur;
        String feeCur = tx.getFeeCurrency() != null ? tx.getFeeCurrency().trim().toUpperCase() : priceCur;

        // Convert fee into price currency for Objem / totals.
        double feeInPriceCur = fee;
        if (fee > 0 && feeCur != null && priceCur != null && !feeCur.equalsIgnoreCase(priceCur)
            && tx.getExecutionDate() != null) {
          try {
            double feeCzk = fee * Settings.getExchangeRate(feeCur, tx.getExecutionDate());
            double priceRate = Settings.getExchangeRate(priceCur, tx.getExecutionDate());
            if (priceRate != 0)
              feeInPriceCur = feeCzk / priceRate;
          } catch (Exception e) {
            // fallback: keep as-is
          }
        }

        // Objem in trade currency (Kačka includes fees)
        Double volume = null;
        if (isBuy) {
          volume = -(qty * price + feeInPriceCur);
        } else if (isSell) {
          volume = (qty * price - feeInPriceCur);
        }

        if (volume != null) {
          sumFeesCur += feeInPriceCur;
          sumVolumeCur += volume.doubleValue();
        }

        // Running position
        if (isBuy)
          pos += qty;
        else if (isSell)
          pos -= qty;
        else if (isTransSub)
          pos -= qty;
        else if (isTransAdd)
          pos += qty;
        // Component-level position

        ofl.println("<tr>");
        if (isBuy)
          ofl.println("<td class=\"center\"><span class=\"kicon buy\">B</span></td>");
        else if (isSell)
          ofl.println("<td class=\"center\"><span class=\"kicon sell\">S</span></td>");
        else if (isTransSub)
          ofl.println("<td class=\"center\"><span class=\"kicon\" style=\"background:#6c757d\" title=\"Corporate action: TRANS_SUB\">CA</span></td>");
        else if (isTransAdd)
          ofl.println("<td class=\"center\"><span class=\"kicon\" style=\"background:#6c757d\" title=\"Corporate action: TRANS_ADD\">CA</span></td>");
        else
          ofl.println("<td></td>");

        java.util.Date effDate = tx.getExecutionDate() != null ? tx.getExecutionDate() : tx.getDate();
        ofl.println("<td>" + htmlEscape(formatDateTime(tx.getDate())) + "</td>");
        ofl.println("<td>" + htmlEscape(formatDateTime(effDate)) + "</td>");
        ofl.println("<td class=\"align-right\">" + htmlEscape(fQty.format(qty)) + "</td>");

        if (isTransSub || isTransAdd) {
          ofl.println("<td class=\"align-right\">" + htmlEscape(isTransSub ? "Transformace SUB" : "Transformace ADD")
              + "</td>");
          ofl.println("<td class=\"align-right\"></td>");
          ofl.println("<td class=\"align-right\"></td>");
        } else {
          ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(price)) + "</td>");
          ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(feeInPriceCur)) + "</td>");
          ofl.println(
              "<td class=\"align-right\">" + (volume == null ? "" : htmlEscape(fMoney.format(volume.doubleValue())))
                  + "</td>");
        }

        // Zisk/% and tax columns: show only when we have a computed aggregate for this transaction.
        SellAgg agg = sellAggBySerial.get(tx.getSerial());
        if (agg != null) {
          String pClass = agg.profitCur < 0 ? "red" : "green";
          double pct = 0.0;
          if (agg.costBaseCur > 0)
            pct = (agg.profitCur / agg.costBaseCur) * 100.0;
          ofl.println("<td class=\"align-right bold " + pClass + "\">" + htmlEscape(fMoney.format(agg.profitCur))
              + "</td>");
          ofl.println("<td class=\"align-right\">" + htmlEscape(fPct.format(pct)) + "%</td>");
          ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(agg.taxIncomeCZK)) + "</td>");
          ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(agg.taxExpenseCZK)) + "</td>");
          sumProfitCur += agg.profitCur;
          sumIncomeCZK += agg.taxIncomeCZK;
          sumExpenseCZK += agg.taxExpenseCZK;
        } else {
          ofl.println("<td class=\"align-right\"></td>");
          ofl.println("<td class=\"align-right\"></td>");
          ofl.println("<td class=\"align-right\"></td>");
          ofl.println("<td class=\"align-right\"></td>");
        }

        ofl.println("<td class=\"align-right\">" + htmlEscape(fQty.format(pos)) + "</td>");
        ofl.println("</tr>");
      }

      // Totals row
      ofl.println("<tr class=\"border-top\">");
      ofl.println("<td colspan=\"5\">Celkem " + htmlEscape(display) + "</td>");
      ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(sumFeesCur)) + "</td>");
      ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(sumVolumeCur)) + "</td>");
      String pClass = sumProfitCur < 0 ? "red" : "green";
      ofl.println("<td class=\"align-right bold " + pClass + "\">" + htmlEscape(fMoney.format(sumProfitCur))
          + "</td>");
      ofl.println("<td></td>");
      ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(sumIncomeCZK)) + "</td>");
      ofl.println("<td class=\"align-right\">" + htmlEscape(fMoney.format(sumExpenseCZK)) + "</td>");
      ofl.println("<td></td>");
      ofl.println("</tr>");

      ofl.println("</tbody></table>");
    }
  }

  private double parseDouble(String s) {
    if (s == null)
      return 0.0;
    s = s.trim();
    if (s.isEmpty() || s.equals("-"))
      return 0.0;

    try {
      boolean negative = false;

      // Normalize minus signs (Unicode variants to standard ASCII minus)
      // \u2212 (Minus Sign), \u2013 (En Dash), \u2014 (Em Dash)
      s = s.replace('\u2212', '-').replace('\u2013', '-').replace('\u2014', '-');

      // Normalize spaces (remove standard spaces, NBSP \u00A0, Narrow NBSP \u202F)
      s = s.replace(" ", "").replace("\u00A0", "").replace("\u202F", "");

      // Negative values can be encoded as (123.45)
      if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
        negative = true;
        s = s.substring(1, s.length() - 1);
      }

      if (s.startsWith("-")) {
        negative = true;
        s = s.substring(1);
      } else if (s.startsWith("+")) {
        s = s.substring(1);
      }

      // Normalize thousands/decimal separators.
      // Supports:
      // - Czech: 123 456,78  -> after space removal: 123456,78
      // - US:    123,456.78
      // - EU:    123.456,78
      int lastComma = s.lastIndexOf(',');
      int lastDot = s.lastIndexOf('.');
      if (lastComma >= 0 && lastDot >= 0) {
        if (lastDot > lastComma) {
          // Decimal '.' and ',' are thousands
          s = s.replace(",", "");
        } else {
          // Decimal ',' and '.' are thousands
          s = s.replace(".", "");
          s = s.replace(',', '.');
        }
      } else if (lastComma >= 0) {
        // Only comma -> decimal comma
        s = s.replace(',', '.');
      }

      double v = Double.parseDouble(s);
      return negative ? -v : v;
    } catch (NumberFormatException e) {
      // If parsing fails, return 0.0 which maps to green (neutral/gain)
      // Ideally we should log this or output visual warning, but 0.0 is safe fallback
      System.err.println("Failed to parse double: '" + s + "'");
      return 0.0;
    }
  }

  private String computeFeeCzkCell(DefaultTableModel model, int rowIndex, int dateCol, int feeCol) {
    try {
      String dateStr = (String) model.getValueAt(rowIndex, dateCol);
      String feeStr = (String) model.getValueAt(rowIndex, feeCol);
      if (dateStr == null || feeStr == null)
        return "-";
      dateStr = dateStr.trim();
      feeStr = feeStr.trim();
      if (dateStr.isEmpty() || dateStr.equals("-"))
        return "-";
      if (feeStr.isEmpty() || feeStr.equals("-"))
        return "-";

      String[] parts = feeStr.split(" ", 2);
      if (parts.length != 2)
        return "-";
      double fee = Double.parseDouble(parts[0].replace(',', '.').replace(" ", "")); // Handle NBSP in parsing
      String cur = parts[1].trim();
      if (cur.isEmpty())
        return "-";

      LocalDateTime ldt = LocalDateTime.parse(dateStr, CZ_DATE_TIME_FMT);
      java.util.Date d = java.util.Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());

      double rate = Settings.getExchangeRate(cur, d);
      double czk = Stocks.roundToHellers(fee * rate);
      java.text.DecimalFormat f2 = new java.text.DecimalFormat("0.00");
      f2.setGroupingUsed(true);
      f2.setGroupingSize(3);
      return spaces2nbsp(f2.format(czk));
    } catch (Exception e) {
      return "-";
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

    lastComputedCpTaxTrades.clear();

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
        if (tx.isDisabled()) {
          continue;
        }

        java.util.Date exDate = tx.getExecutionDate() != null ? tx.getExecutionDate() : tx.getDate();
        if (exDate == null) {
          continue;
        }

        cal.setTime(exDate);
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
    bSaveHTMLCPNew = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableCP = new javax.swing.JTable();
    pDerivates = new javax.swing.JPanel();
    bSaveCSVDer = new javax.swing.JButton();
    bSaveHTMLDer = new javax.swing.JButton();
    bSaveHTMLDerNew = new javax.swing.JButton();
    jScrollPane3 = new javax.swing.JScrollPane();
    tableDer = new javax.swing.JTable();
    pCash = new javax.swing.JPanel();
    bSaveCSVCash = new javax.swing.JButton();
    bSaveHTMLCash = new javax.swing.JButton();
    bSaveHTMLCashNew = new javax.swing.JButton();
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

    bSaveHTMLCPNew.setText("Uložit HTML new");
    bSaveHTMLCPNew.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveHTMLCPNewActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
    pCP.add(bSaveHTMLCPNew, gridBagConstraints);

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

    bSaveHTMLDerNew.setText("Uložit HTML new");
    bSaveHTMLDerNew.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveHTMLDerNewActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
    pDerivates.add(bSaveHTMLDerNew, gridBagConstraints);

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

    bSaveHTMLCashNew.setText("Uložit HTML new");
    bSaveHTMLCashNew.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        bSaveHTMLCashNewActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
    pCash.add(bSaveHTMLCashNew, gridBagConstraints);

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

    jLabel2.setFont(jLabel2.getFont().deriveFont(java.awt.Font.BOLD));
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

    // Year-scoped warning + status bar warning (only 29.-31.12 of selected year).
    int sameSettlementCount = countSameSettlementOnYearEnd(transactions, year);
    warnAboutSettlementDatesForYearIfNeeded(transactions, year);
    if (mainWindow != null) {
      mainWindow.setDpYearEndSettlementWarning(year, sameSettlementCount);
    }

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

    RowStatsHelper rowStatsHelper = new RowStatsHelper();

    try {

      for (Iterator<Transaction> i = transactions.iterator(); i.hasNext();) {
        Transaction tx = i.next();
        Stocks.StockTrade[] ts = stocks.applyTransaction(tx, true);

        if (ts != null) {
          // Print out only transactions that happen in the year that interests us
          for (int n = 0; n < ts.length; n++) {
            Stocks.StockTrade t = ts[n];
            processTradeRow(t, modelCP, modelDer, modelCash, year, overTaxFreeDuration, noIncomeTrades,
                rowStatsHelper, allowShortsOverYearBorder);
          }
        }
      }

      // Finalize - find short trades opened in year we compute for that
      // are not yet closed and add them
      Stocks.StockTrade[] ts = stocks.autocloseShortTransactions(year);
      if (ts != null) {
        for (int n = 0; n < ts.length; n++) {
          Stocks.StockTrade t = ts[n];
          processTradeRow(t, modelCP, modelDer, modelCash, year, overTaxFreeDuration, noIncomeTrades,
              rowStatsHelper, allowShortsOverYearBorder);
        }
      }

      // Add summary
      String row[] = { "", "", "", "", "", "", "-----------", "", "", "", "", "", "", "-----------", "-----------",
          "" };
      modelCP.addRow(row);
      modelDer.addRow(row);
      modelCash.addRow(row);

      String row3a[] = { "", "", "", "", "", "Příjem:", f2.format(rowStatsHelper.cp_openCreditSumCZK), "", "", "", "",
          "",
          "",
          f2.format(rowStatsHelper.cp_closeCreditSumCZK),
          f2.format(rowStatsHelper.cp_openCreditSumCZK + rowStatsHelper.cp_closeCreditSumCZK), "" };
      modelCP.addRow(row3a);
      String row3b[] = { "", "", "", "", "", "Příjem:", f2.format(rowStatsHelper.der_openCreditSumCZK), "", "", "", "",
          "",
          "",
          f2.format(rowStatsHelper.der_closeCreditSumCZK),
          f2.format(rowStatsHelper.der_openCreditSumCZK + rowStatsHelper.der_closeCreditSumCZK), "" };
      modelDer.addRow(row3b);
      String row3c[] = { "", "", "", "", "", "Příjem:", f2.format(rowStatsHelper.cash_openCreditSumCZK), "", "", "", "",
          "",
          "",
          f2.format(rowStatsHelper.cash_closeCreditSumCZK),
          f2.format(rowStatsHelper.cash_openCreditSumCZK + rowStatsHelper.cash_closeCreditSumCZK), "" };
      modelCash.addRow(row3c);

      String row4a[] = { "", "", "", "", "", "Výdej:", f2.format(rowStatsHelper.cp_openDebitSumCZK), "", "", "", "", "",
          "",
          f2.format(rowStatsHelper.cp_closeDebitSumCZK),
          f2.format(rowStatsHelper.cp_openDebitSumCZK + rowStatsHelper.cp_closeDebitSumCZK), "" };
      modelCP.addRow(row4a);
      String row4b[] = { "", "", "", "", "", "Výdej:", f2.format(rowStatsHelper.der_openDebitSumCZK), "", "", "", "",
          "", "",
          f2.format(rowStatsHelper.der_closeDebitSumCZK),
          f2.format(rowStatsHelper.der_openDebitSumCZK + rowStatsHelper.der_closeDebitSumCZK), "" };
      modelDer.addRow(row4b);
      String row4c[] = { "", "", "", "", "", "Výdej:", f2.format(rowStatsHelper.cash_openDebitSumCZK), "", "", "", "",
          "", "",
          f2.format(rowStatsHelper.cash_closeDebitSumCZK),
          f2.format(rowStatsHelper.cash_openDebitSumCZK + rowStatsHelper.cash_closeDebitSumCZK), "" };
      modelCash.addRow(row4c);

      String row2a[] = { "", "", "", "", "", "Zisk:", "", "", "", "", "", "", "", "",
          f2.format(rowStatsHelper.cp_sumCZK),
          "" };
      modelCP.addRow(row2a);
      String row2b[] = { "", "", "", "", "", "Zisk:", "", "", "", "", "", "", "", "",
          f2.format(rowStatsHelper.der_sumCZK),
          "" };
      modelDer.addRow(row2b);
      String row2c[] = { "", "", "", "", "", "Zisk:", "", "", "", "", "", "", "", "",
          f2.format(rowStatsHelper.cash_sumCZK),
          "" };
      modelCash.addRow(row2c);

      yearComputed = year;

      if (cbComputeDivi.isSelected())
        computeDividends(year); // Compute dividends
      else
        ((DefaultTableModel) (diviTable.getModel())).setNumRows(0); // Clear dividend table

      saveSettings();
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
  }// GEN-LAST:event_bComputeActionPerformed

  private void formWindowOpened(java.awt.event.WindowEvent evt)// GEN-FIRST:event_formWindowOpened
  {// GEN-HEADEREND:event_formWindowOpened

    /* Load settings */
    eYear.setText(Integer.toString(Settings.getComputeYear()));
    cbAllowShortOverYearBoundary.setSelected(Settings.getAllowShortOverYearBoundary());
    cbNoIncome.setSelectedIndex(Settings.getNoIncomeTrades());
    cbOverTaxFreeDuration.setSelectedIndex(Settings.getOverTaxFreeDuration());
    cbSeparateCurrencyCSV.setSelected(Settings.getSeparateCurrencyInCSVExport());

    /* Refresh conversion method toggle */
    if (cbUseDailyRatesCompute != null) {
      cbUseDailyRatesCompute.setSelected(Settings.getUseDailyRates());
    }

  }// GEN-LAST:event_formWindowOpened

  private void bSaveCSVCPActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveCSVCPActionPerformed
    save(SaveFormat.CSV, tableCP);
  }// GEN-LAST:event_bSaveCSVCPActionPerformed

  private void bSaveHTMLCPActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveHTMLCPActionPerformed
    save(SaveFormat.HTML, tableCP);
  }// GEN-LAST:event_bSaveHTMLCPActionPerformed

  private void bSaveHTMLCPNewActionPerformed(java.awt.event.ActionEvent evt) {
    saveHtmlNewTrades(tableCP);
  }

  private void bSaveCSVDerActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveCSVDerActionPerformed
    save(SaveFormat.CSV, tableDer);
  }// GEN-LAST:event_bSaveCSVDerActionPerformed

  private void bSaveHTMLDerActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_bSaveHTMLDerActionPerformed
    save(SaveFormat.HTML, tableDer);
  }// GEN-LAST:event_bSaveHTMLDerActionPerformed

  private void bSaveHTMLDerNewActionPerformed(java.awt.event.ActionEvent evt) {
    saveHtmlNewTrades(tableDer);
  }

  private void bSaveCSVCashActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bSaveCSVCashActionPerformed
  {// GEN-HEADEREND:event_bSaveCSVCashActionPerformed
    save(SaveFormat.CSV, tableCash);
  }// GEN-LAST:event_bSaveCSVCashActionPerformed

  private void bSaveHTMLCashActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_bSaveHTMLCashActionPerformed
  {// GEN-HEADEREND:event_bSaveHTMLCashActionPerformed
    save(SaveFormat.HTML, tableCash);
  }// GEN-LAST:event_bSaveHTMLCashActionPerformed

  private void bSaveHTMLCashNewActionPerformed(java.awt.event.ActionEvent evt) {
    saveHtmlNewTrades(tableCash);
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton bClose;
  private javax.swing.JButton bCompute;
  private javax.swing.JButton bSaveCSVCP;
  private javax.swing.JButton bSaveCSVCash;
  private javax.swing.JButton bSaveCSVDer;
  private javax.swing.JButton bSaveCSVDivi;
  private javax.swing.JButton bSaveHTMLCP;
  private javax.swing.JButton bSaveHTMLCPNew;
  private javax.swing.JButton bSaveHTMLCash;
  private javax.swing.JButton bSaveHTMLCashNew;
  private javax.swing.JButton bSaveHTMLDer;
  private javax.swing.JButton bSaveHTMLDerNew;
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
  private javax.swing.JCheckBox cbUseDailyRatesCompute;
  // End of variables declaration//GEN-END:variables

  private class RowStatsHelper {
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
  }

  private void processTradeRow(Stocks.StockTrade t, DefaultTableModel modelCP, DefaultTableModel modelDer,
      DefaultTableModel modelCash, int year, IncludeOverTaxFreeDuration overTaxFreeDuration,
      NoIncomeTrades noIncomeTrades, RowStatsHelper stats, boolean allowShortsOverYearBorder) {
    GregorianCalendar cal = new GregorianCalendar();
    DecimalFormat fn = new DecimalFormat("0.00#####");
    fn.setGroupingUsed(true);
    fn.setGroupingSize(3);
    DecimalFormat f2 = new DecimalFormat("0.00");
    f2.setGroupingUsed(true);
    f2.setGroupingSize(3);
    DecimalFormat fRate = new DecimalFormat("0.0000");

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
            stats.cp_openCreditSumCZK += t.openCreditCZK;
            stats.cp_openDebitSumCZK += t.openDebitCZK;
          } else if (cash) {
            stats.cash_openCreditSumCZK += t.openCreditCZK;
            stats.cash_openDebitSumCZK += t.openDebitCZK;
          } else {
            stats.der_openCreditSumCZK += t.openCreditCZK;
            stats.der_openDebitSumCZK += t.openDebitCZK;
          }
        }

        /* Close */

        if (t.close != null) {
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
            stats.cp_closeCreditSumCZK += t.closeCreditCZK;
            stats.cp_closeDebitSumCZK += t.closeDebitCZK;
          } else if (cash) {
            stats.cash_closeCreditSumCZK += t.closeCreditCZK;
            stats.cash_closeDebitSumCZK += t.closeDebitCZK;
          } else {
            stats.der_closeCreditSumCZK += t.closeCreditCZK;
            stats.der_closeDebitSumCZK += t.closeDebitCZK;
          }
        }

        /* Results */

        // Result in CZK
        if (include) {
          rowData.add(f2.format(t.profitCZK));

          if (cp)
            stats.cp_sumCZK += t.profitCZK;
          else if (cash)
            stats.cash_sumCZK += t.profitCZK;
          else
            stats.der_sumCZK += t.profitCZK;

          // Persist CP tax-trade export data (for Kačka-like summaries in HTML new).
          if (cp) {
            CpTaxExportTrade te = new CpTaxExportTrade();
            te.ticker = t.close != null && t.close.ticker != null ? t.close.ticker : t.open.ticker;
            te.openTradeDate = t.open.tradeDate;
            te.openExecutionDate = t.open.executionDate;
            te.closeTradeDate = t.close != null ? t.close.tradeDate : null;
            te.closeExecutionDate = t.close != null ? t.close.executionDate : null;

            // Income year is based on executionDate (t.getIncomeDate uses open.date/close.date).
            te.incomeExecutionDate = t.getIncomeDate();
            te.incomeTradeDate = t.open.amount < 0 ? t.open.tradeDate : (t.close != null ? t.close.tradeDate : null);

            // Currency for per-currency reporting.
            String cur = null;
            if (t.open != null && t.open.priceCurrency != null)
              cur = t.open.priceCurrency;
            else if (t.close != null && t.close.priceCurrency != null)
              cur = t.close.priceCurrency;
            te.currency = cur != null ? cur.trim().toUpperCase() : "CZK";

            // Tax numbers in CZK.
            te.taxIncomeCZK = t.openCreditCZK + t.closeCreditCZK;
            te.taxExpenseCZK = -(t.openDebitCZK + t.closeDebitCZK);
            te.profitCZK = t.profitCZK;
            lastComputedCpTaxTrades.add(te);
          }
        } else {
          rowData.add("-");
        }

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

}
