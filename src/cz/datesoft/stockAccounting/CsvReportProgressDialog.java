/*
 * CsvReportProgressDialog.java
 *
 * Progress dialog for CSV report generation with countdown timer
 */

package cz.datesoft.stockAccounting;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import javax.swing.*;

/**
 * Progress dialog for CSV report generation with real-time status updates
 */
public class CsvReportProgressDialog extends JDialog {
    private final long reportId;
    private final long reportStartTime;
    private final Trading212CsvClient csvClient;
    private final Trading212CsvParser csvParser;
    private final Trading212ReportCache reportCache;
    private final javax.swing.SwingWorker importWorker; // Reference to worker for cancellation
    
    // Cache support (optional)
    private Trading212CsvCache csvCache = null;
    private String cacheAccountId = null;
    private int cacheYear = 0;

    private JLabel statusLabel;
    private JLabel countdownLabel;
    private JProgressBar progressBar;
    private JButton cancelButton;
    private Timer countdownTimer;
    private Timer statusTimer;

    // Results for blocking call
    private Vector<Transaction> resultTransactions = null;
    private Exception resultException = null;
    private boolean completed = false;

    public CsvReportProgressDialog(Frame parent, long reportId,
            Trading212CsvClient csvClient, Trading212CsvParser csvParser, Trading212ReportCache reportCache,
            javax.swing.SwingWorker importWorker) {
        super(parent, "Generating CSV Report", true); // Modal dialog
        this.reportId = reportId;
        this.reportStartTime = System.currentTimeMillis();
        this.csvClient = csvClient;
        this.csvParser = csvParser;
        this.reportCache = reportCache;
        this.importWorker = importWorker;

        initComponents();
        startCountdownTimer();
        startStatusMonitoring();
    }
    
    /**
     * Set cache parameters to enable CSV caching
     */
    public void setCacheParameters(Trading212CsvCache csvCache, String accountId, int year) {
        this.csvCache = csvCache;
        this.cacheAccountId = accountId;
        this.cacheYear = year;
    }

    /**
     * Block and wait for dialog completion, return results
     */
    public Vector<Transaction> waitForCompletion() throws Exception {
        // Show dialog on EDT
        javax.swing.SwingUtilities.invokeLater(() -> setVisible(true));

        // Wait for completion in a separate thread (not blocking EDT)
        while (!completed) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Exception("Import interrupted", e);
            }
        }

        if (resultException != null) {
            throw resultException;
        }

        return resultTransactions;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // Status display panel
        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        statusLabel = new JLabel("Inicializace generování reportu...");
        countdownLabel = new JLabel("Další kontrola stavu: 65 sekund");

        statusPanel.add(statusLabel);
        statusPanel.add(countdownLabel);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(statusPanel, BorderLayout.NORTH);

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Preparing...");
        progressBar.setStringPainted(true);
        progressBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(progressBar, BorderLayout.CENTER);

        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        cancelButton = new JButton("Zrušit");

        cancelButton.addActionListener(e -> cancelReport());

        buttonPanel.add(cancelButton);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(450, 180);
        setLocationRelativeTo(getParent());
    }

    private void startCountdownTimer() {
        countdownTimer = new Timer(1000, e -> updateCountdown());
        countdownTimer.start();
    }

    private void updateCountdown() {
        long elapsed = System.currentTimeMillis() - reportStartTime;
        long nextCheckIn = 65000 - (elapsed % 65000);

        int seconds = (int) (nextCheckIn / 1000);
        countdownLabel.setText("Další kontrola stavu: " + seconds + " sekund");

        // Check for 30-minute timeout
        long elapsedMinutes = elapsed / (1000 * 60);
        if (elapsedMinutes >= 30) {
            showTimeoutDialog();
        }
    }

    private void startStatusMonitoring() {
        statusTimer = new Timer(65000, e -> checkReportStatus());
        statusTimer.start();
    }

    private void checkReportStatus() {
        try {
            // Use cache to avoid repeated API calls
            Trading212CsvClient.CsvReportStatus status = reportCache.getReportStatus(reportId, csvClient);

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Status: " + status.status.getDisplayText());

                if (status.status == Trading212CsvClient.CsvReportStatus.ReportStatus.FINISHED) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Report ready!");
                    startCsvDownload(status.downloadUrl);
                } else if (status.status == Trading212CsvClient.CsvReportStatus.ReportStatus.FAILED) {
                    showError("Report generation failed on Trading 212 servers.\n" +
                            "This may be due to invalid date range or service issues.\n\n" +
                            "Suggestions:\n" +
                            "• Try a smaller date range\n" +
                            "• Check your internet connection\n" +
                            "• Try again in a few minutes");
                }
            });

        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Chyba při kontrole stavu: " + ex.getMessage());
                progressBar.setString("Connection issue...");
            });
        }
    }

    private void showTimeoutDialog() {
        if (countdownTimer != null)
            countdownTimer.stop();
        if (statusTimer != null)
            statusTimer.stop();

        int result = JOptionPane.showConfirmDialog(this,
                "The CSV report generation has been running for over 30 minutes.\n\n" +
                        "This is longer than usual. The report may be very large or there may be server delays.\n\n" +
                        "Would you like to:\n" +
                        "• Continue waiting\n" +
                        "• Cancel and try again later",
                "Report Generation Timeout",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        switch (result) {
            case JOptionPane.YES_OPTION: // Continue
                startCountdownTimer();
                startStatusMonitoring();
                break;
            case JOptionPane.NO_OPTION: // Cancel
                // Signal cancellation to waitForCompletion()
                this.resultException = new Exception("Import zrušen kvůli timeout");
                this.completed = true;
                dispose();
                break;
        }
    }

    private void startCsvDownload(String downloadUrl) {
        statusLabel.setText("Stahování CSV reportu...");
        progressBar.setString("Downloading...");
        cancelButton.setEnabled(false);

        // Download in background thread
        SwingWorker<Vector<Transaction>, Void> worker = new SwingWorker<Vector<Transaction>, Void>() {
            @Override
            protected Vector<Transaction> doInBackground() throws Exception {
                String csvContent = csvClient.downloadCsvReport(downloadUrl);
                
                // Save CSV to cache if parameters are set
                if (csvCache != null && cacheAccountId != null && cacheYear > 0) {
                    try {
                        csvCache.saveCsv(cacheAccountId, cacheYear, csvContent);
                        System.out.println("✓ Saved CSV to cache (account: " + cacheAccountId + ", year: " + cacheYear + ")");
                    } catch (Exception cacheError) {
                        System.err.println("Failed to save CSV to cache: " + cacheError.getMessage());
                    }
                }
                
                return csvParser.parseCsvReport(csvContent);
            }

            @Override
            protected void done() {
                try {
                    Vector<Transaction> transactions = get();
                    completeImport(transactions);
                    JOptionPane.showMessageDialog(CsvReportProgressDialog.this,
                            "Úspěšně importováno " + transactions.size() + " transakcí!",
                            "Import dokončen", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    showError("Import selhal: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void completeImport(Vector<Transaction> transactions) {
        // Stop timers to prevent background notifications and GUI freeze
        if (statusTimer != null)
            statusTimer.stop();
        if (countdownTimer != null)
            countdownTimer.stop();

        // Set results for waitForCompletion() to return
        this.resultTransactions = transactions;
        this.completed = true;

        // This would integrate with the main application to add transactions
        // For now, we'll just log the success
        System.out.println("Successfully imported " + transactions.size() + " transactions from CSV");
    }

    private void cancelReport() {
        if (statusTimer != null)
            statusTimer.stop();
        if (countdownTimer != null)
            countdownTimer.stop();

        // Cancel the actual import worker to stop the API request
        if (importWorker != null) {
            System.out.println("[CANCEL:001] Canceling background import worker");
            importWorker.cancel(true); // Interrupt the background import
        }

        // Signal cancellation to waitForCompletion()
        this.resultException = new Exception("Import zrušen uživatelem");
        this.completed = true;

        dispose();
        // In a full implementation, this would attempt to cancel the report on Trading
        // 212
    }

    private void showError(String message) {
        if (statusTimer != null)
            statusTimer.stop();
        if (countdownTimer != null)
            countdownTimer.stop();

        // Signal error to waitForCompletion()
        this.resultException = new Exception(message);
        this.completed = true;

        JOptionPane.showMessageDialog(this, message,
                "Import Error", JOptionPane.ERROR_MESSAGE);
        dispose();
    }
}