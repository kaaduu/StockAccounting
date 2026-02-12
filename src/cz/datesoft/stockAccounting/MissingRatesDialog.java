/*
 * MissingRatesDialog.java
 *
 * Dialog for displaying missing FX rates and providing options to fetch them
 */

package cz.datesoft.stockAccounting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dialog showing missing FX exchange rates after import
 * 
 * @author Antigravity AI
 */
public class MissingRatesDialog extends JDialog {

  private FXRateChecker.RatesCheckResult result;
  private MainWindow mainWindow;
  private JPanel detailsPanel;
  private boolean detailsVisible = false;
  private JButton btnFetch;
  private JButton btnSettings;
  private JButton btnIgnore;
  private JButton btnDetails;

  public MissingRatesDialog(Frame parent, FXRateChecker.RatesCheckResult result, MainWindow mainWindow) {
    super(parent, "Chybějící kurzy měn", true);
    this.result = result;
    this.mainWindow = mainWindow;
    
    System.out.println("[FXRATES:DIALOG:001] Missing rates dialog displayed");
    System.out.println("[FXRATES:DIALOG:002]   - Missing daily rates: " + result.getMissingDailyCount());
    System.out.println("[FXRATES:DIALOG:003]   - Missing unified rates: " + result.getMissingUnifiedCount());
    AppLog.info(String.format("FX Rate Check Dialog: Zobrazen uživateli - chybějící denní: %d, jednotné: %d",
        result.getMissingDailyCount(), result.getMissingUnifiedCount()));
    
    initComponents();
    pack();
    setLocationRelativeTo(parent);
  }

  private void initComponents() {
    setLayout(new BorderLayout(10, 10));
    setPreferredSize(new Dimension(450, detailsVisible ? 400 : 200));

    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    JLabel titleLabel = new JLabel("⚠ Chybějící kurzy měn");
    titleLabel.setFont(new Font("Dialog", Font.BOLD, 16));
    mainPanel.add(titleLabel, BorderLayout.NORTH);

    JTextArea messageArea = new JTextArea();
    messageArea.setEditable(false);
    messageArea.setOpaque(false);
    messageArea.setLineWrap(true);
    messageArea.setWrapStyleWord(true);
    
    String message = "Po importu byla detekována chybějící data o měnových kurzech.\n" +
        "Pro správný výpočet je nutné tyto kurzy mít k dispozici.\n\n";

    if (result.hasMissingDailyRates()) {
      message += String.format("Chybějící denní kurzy: %d (měny: %d, roky: %d)\n",
          result.getMissingDailyCount(), result.currencies.size(), result.years.size());
    }
    if (result.hasMissingUnifiedRates()) {
      message += String.format("Chybějící jednotné kurzy: %d (měny: %d, roky: %d)",
          result.getMissingUnifiedCount(), result.missingUnifiedRates.size(), result.years.size());
    }

    messageArea.setText(message);
    mainPanel.add(messageArea, BorderLayout.CENTER);

    add(mainPanel, BorderLayout.NORTH);

    detailsPanel = createDetailsPanel();
    detailsPanel.setVisible(detailsVisible);
    add(detailsPanel, BorderLayout.CENTER);

    JPanel buttonPanel = createButtonPanel();
    add(buttonPanel, BorderLayout.SOUTH);
  }

  private JPanel createDetailsPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Detaily"));

    JTabbedPane tabbedPane = new JTabbedPane();

    if (result.hasMissingDailyRates()) {
      JTextArea dailyRatesArea = new JTextArea();
      dailyRatesArea.setEditable(false);
      dailyRatesArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
      
      StringBuilder sb = new StringBuilder();
      for (String rateKey : result.missingDailyRates) {
        sb.append(rateKey).append("\n");
      }
      if (sb.length() > 500) {
        sb.setLength(500);
        sb.append("\n... (a další)");
      }
      dailyRatesArea.setText(sb.toString());
      
      JScrollPane dailyScrollPane = new JScrollPane(dailyRatesArea);
      tabbedPane.addTab("Denní kurzy", dailyScrollPane);
    }

    if (result.hasMissingUnifiedRates()) {
      JTextArea unifiedRatesArea = new JTextArea();
      unifiedRatesArea.setEditable(false);
      unifiedRatesArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
      
      StringBuilder sb = new StringBuilder();
      List<String> sortedCurrencies = new ArrayList<>(result.missingUnifiedRates.keySet());
      java.util.Collections.sort(sortedCurrencies);
      for (String currency : sortedCurrencies) {
        Set<Integer> years = result.missingUnifiedRates.get(currency);
        List<Integer> sortedYears = new ArrayList<>(years);
        java.util.Collections.sort(sortedYears);
        sb.append(currency).append(": ").append(sortedYears).append("\n");
      }
      unifiedRatesArea.setText(sb.toString());
      
      JScrollPane unifiedScrollPane = new JScrollPane(unifiedRatesArea);
      tabbedPane.addTab("Jednotné kurzy", unifiedScrollPane);
    }

    panel.add(tabbedPane, BorderLayout.CENTER);
    return panel;
  }

  private JPanel createButtonPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

    btnDetails = new JButton("Zobrazit detaily");
    btnDetails.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        toggleDetails();
      }
    });
    panel.add(btnDetails);

    btnFetch = new JButton("Načíst chybějící kurzy");
    btnFetch.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fetchMissingRates();
      }
    });
    panel.add(btnFetch);

    btnSettings = new JButton("Otevřít nastavení");
    btnSettings.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        openSettings();
      }
    });
    panel.add(btnSettings);

    btnIgnore = new JButton("Ignorovat");
    btnIgnore.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        AppLog.info("FX Rate Check Dialog: Uživatel ignoroval chybějící kurzy");
        System.out.println("[FXRATES:DIALOG:004] User dismissed dialog without fetching");
        dispose();
      }
    });
    panel.add(btnIgnore);

    return panel;
  }

  private void toggleDetails() {
    detailsVisible = !detailsVisible;
    detailsPanel.setVisible(detailsVisible);
    btnDetails.setText(detailsVisible ? "Skrýt detaily" : "Zobrazit detaily");
    pack();
  }

  private void fetchMissingRates() {
    AppLog.info("FX Rate Fetch: Uživatel spustil načítání chybějících kurzů");
    System.out.println("[FXRATES:FETCH:001] Fetch started by user");
    System.out.println("[FXRATES:FETCH:002]   - Years to fetch: " + result.years);
    System.out.println("[FXRATES:FETCH:003]   - Currencies to fetch: " + result.currencies);
    System.out.println("[FXRATES:FETCH:004]   - Missing unified rate pairs: " + result.missingUnifiedRates);
    
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    btnFetch.setEnabled(false);
    btnSettings.setEnabled(false);
    btnIgnore.setEnabled(false);

    SwingWorker<FetchResult, Void> worker = new SwingWorker<FetchResult, Void>() {
      @Override
      protected FetchResult doInBackground() throws Exception {
        int dailyFetched = 0;
        int unifiedFetched = 0;
        int failed = 0;

        if (result.hasMissingDailyRates()) {
          for (Integer year : result.years) {
            try {
              List<String> currencies = new ArrayList<>(result.currencies);
              Map<String, Double> yearRates = CurrencyRateFetcher.fetchSelectiveDailyRates(year, currencies);
              if (yearRates != null && !yearRates.isEmpty()) {
                Map<String, Double> existingRates = Settings.getDailyRates();
                if (existingRates != null) {
                  yearRates.putAll(existingRates);
                }
                Settings.setDailyRates(new java.util.HashMap<>(yearRates));
                dailyFetched += yearRates.size();
              }
            } catch (Exception e) {
              AppLog.error("FX Rate Fetch: Chyba při načítání denních kurzů pro rok " + year + ": " + e.getMessage(), e);
              System.err.println("[FXRATES:ERROR:001] Error fetching daily rates for year " + year + ": " + e.getMessage());
              failed++;
            }
          }
        }

        if (result.hasMissingUnifiedRates()) {
          for (Map.Entry<String, Set<Integer>> entry : result.missingUnifiedRates.entrySet()) {
            String currency = entry.getKey();
            for (Integer year : entry.getValue()) {
              try {
                Double rate = CurrencyRateFetcher.fetchJednotnyKurz(currency, year);
                if (rate != null) {
                  Settings.addOrUpdateRatio(currency, year, rate);
                  unifiedFetched++;
                } else {
                  failed++;
                }
              } catch (Exception e) {
                AppLog.error("FX Rate Fetch: Chyba při načítání jednotného kurzu " + currency + " " + year + ": " + e.getMessage(), e);
                System.err.println("[FXRATES:ERROR:002] Error fetching unified rate for " + currency + " " + year + ": " + e.getMessage());
                failed++;
              }
            }
          }
        }

        Settings.save();
        return new FetchResult(dailyFetched, unifiedFetched, failed);
      }

      @Override
      protected void done() {
        setCursor(Cursor.getDefaultCursor());
        btnFetch.setEnabled(true);
        btnSettings.setEnabled(true);
        btnIgnore.setEnabled(true);

        try {
          FetchResult fetchResult = get();
          String message = String.format("Načítání dokončeno.\n" +
              "Načteno denních kurzů: %d\n" +
              "Načteno jednotných kurzů: %d\n",
              fetchResult.dailyFetched, fetchResult.unifiedFetched);
          
          if (fetchResult.failed > 0) {
            message += String.format("Chyby: %d\n", fetchResult.failed);
          }
          
          System.out.println("[FXRATES:FETCH:005] Fetch completed - daily: " + fetchResult.dailyFetched + 
              ", unified: " + fetchResult.unifiedFetched + ", failed: " + fetchResult.failed);
          
          if (fetchResult.failed > 0) {
            AppLog.warn(String.format("FX Rate Fetch: Dokončeno s chybami - denní: %d, jednotné: %d, chyby: %d",
                fetchResult.dailyFetched, fetchResult.unifiedFetched, fetchResult.failed));
          } else {
            AppLog.info(String.format("FX Rate Fetch: Dokončeno úspěšně - denní: %d, jednotné: %d",
                fetchResult.dailyFetched, fetchResult.unifiedFetched));
          }
          
          JOptionPane.showMessageDialog(MissingRatesDialog.this,
              message,
              "Hotovo", JOptionPane.INFORMATION_MESSAGE);
          
          dispose();
        } catch (Exception e) {
          AppLog.error("FX Rate Fetch: Chyba při načítání: " + e.getMessage(), e);
          System.err.println("[FXRATES:ERROR:003] Exception during fetch: " + e.getMessage());
          JOptionPane.showMessageDialog(MissingRatesDialog.this,
              "Chyba při načítání: " + e.getMessage(),
              "Chyba", JOptionPane.ERROR_MESSAGE);
        }
      }
    };

    worker.execute();
  }

  private void openSettings() {
    AppLog.info("FX Rate Check Dialog: Uživatel otevřel nastavení kurzů");
    System.out.println("[FXRATES:DIALOG:005] Opening settings with missing rates highlighted");
    
    SettingsWindow settingsWindow = new SettingsWindow(mainWindow, true);
    settingsWindow.showDialog();
    settingsWindow.highlightMissingRates(result);
    dispose();
  }

  private static class FetchResult {
    int dailyFetched;
    int unifiedFetched;
    int failed;

    FetchResult(int dailyFetched, int unifiedFetched, int failed) {
      this.dailyFetched = dailyFetched;
      this.unifiedFetched = unifiedFetched;
      this.failed = failed;
    }
  }
}
