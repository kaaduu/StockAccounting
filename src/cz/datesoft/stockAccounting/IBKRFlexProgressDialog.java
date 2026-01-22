/*
 * IBKRFlexProgressDialog.java
 *
 * Modal progress dialog for IBKR Flex import (similar to CsvReportProgressDialog)
 */

package cz.datesoft.stockAccounting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * Modal progress dialog for IBKR Flex import with real-time status updates
 * Blocks until completion and returns results
 */
public class IBKRFlexProgressDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(IBKRFlexProgressDialog.class.getName());

    private final IBKRFlexImporter importer;
    private final Vector<Integer> years;
    private javax.swing.SwingWorker importWorker;

    private IBKRFlexImporter.ImportResult result = null;
    private Exception resultException = null;
    private boolean completed = false;

    private JLabel statusLabel;
    private JLabel countdownLabel;
    private JProgressBar progressBar;
    private JButton cancelButton;

    private CountDownLatch doneLatch;

    public IBKRFlexProgressDialog(Frame parent, IBKRFlexImporter importer, Vector<Integer> years) {
        super(parent, "IBKR Flex Import Progress", true);
        this.importer = importer;
        this.years = years;

        logger.fine("Initializing progress dialog");
        initComponents();
        logger.fine("Progress dialog components initialized");
    }

    public IBKRFlexImporter.ImportResult waitForCompletion() throws Exception {
        logger.fine("Showing progress dialog and waiting for completion");

        doneLatch = new CountDownLatch(1);

        setVisible(true);

        logger.fine("Dialog closed: completed=" + completed + ", hasException=" + (resultException != null));

        if (resultException != null) {
            throw resultException;
        }

        return result;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        statusLabel = new JLabel("Inicializace IBKR Flex importu...");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        countdownLabel = new JLabel("Čekání na zahájení...");

        statusPanel.add(statusLabel);
        statusPanel.add(countdownLabel);
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Importování...");
        progressBar.setStringPainted(true);
        mainPanel.add(progressBar, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cancelButton = new JButton("Zrušit");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (importWorker != null) {
                    importWorker.cancel(true);
                    statusLabel.setText("Rušení importu...");
                    cancelButton.setEnabled(false);
                }
            }
        });
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        setSize(400, 180);
        setLocationRelativeTo(getParent());
    }

    public void startImport() {
        logger.fine("Starting import worker");

        importWorker = new javax.swing.SwingWorker<IBKRFlexImporter.ImportResult, String>() {

            @Override
            protected IBKRFlexImporter.ImportResult doInBackground() throws Exception {
                logger.fine("Import worker background task starting");
                try {
                    publish("Spouštění importu...");
                    return importer.importYears(years, this);
                } catch (Exception e) {
                    logger.warning("Import worker exception: " + e.getMessage());
                    publish("Chyba: " + e.getMessage());
                    throw e;
                }
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    String latest = chunks.get(chunks.size() - 1);
                    statusLabel.setText(latest);
                    logger.fine("Status update: " + latest);
                }
            }

            @Override
            protected void done() {
                logger.fine("Import worker done callback");
                completed = true;
                try {
                    result = get();
                    logger.info("Import successful: " + (result != null ? result.message : "null"));
                    statusLabel.setText("Import dokončen!");
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    countdownLabel.setText("Hotovo");
                    cancelButton.setText("Zavřít");
                    cancelButton.removeActionListener(cancelButton.getActionListeners()[0]);
                    cancelButton.addActionListener(e -> {
                        setVisible(false);
                    });

                    // Auto-close: this is a modal progress dialog and should return control immediately.
                    setVisible(false);
                } catch (CancellationException e) {
                    logger.info("Import cancelled by user");
                    resultException = new Exception("Import cancelled by user");
                    statusLabel.setText("Import zrušen uživatelem");
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    countdownLabel.setText("Zrušeno");
                    cancelButton.setText("Zavřít");
                    cancelButton.removeActionListener(cancelButton.getActionListeners()[0]);
                    cancelButton.addActionListener(ev -> {
                        setVisible(false);
                    });

                    // Auto-close on cancel
                    setVisible(false);
                } catch (Exception e) {
                    logger.warning("Import failed: " + e.getMessage());
                    resultException = e;
                    statusLabel.setText("Import selhal!");
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    countdownLabel.setText("Zkontrolujte nastavení");
                    cancelButton.setText("Zavřít");
                    cancelButton.removeActionListener(cancelButton.getActionListeners()[0]);
                    cancelButton.addActionListener(ev -> {
                        setVisible(false);
                    });

                    // Auto-close on error
                    setVisible(false);
                }
                doneLatch.countDown();
            }
        };

        logger.fine("Executing import worker");
        importWorker.execute();
    }
}
