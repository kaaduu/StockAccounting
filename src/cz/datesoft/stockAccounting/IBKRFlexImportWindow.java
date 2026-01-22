/*
 * IBKRFlexImportWindow.java
 *
 * UI window for IBKR Flex Query import with year checkboxes
 * 
 * @deprecated As of 2026-01-20, replaced by unified ImportWindow with "IBKR Flex API" format.
 * 
 * This standalone window is NO LONGER ACCESSIBLE from application menus.
 * It is kept in the codebase for reference and potential future multi-year import feature.
 * 
 * RECOMMENDED WORKFLOW:
 * - Current year import: Use "Soubor" → "Import od brokera" → "IBKR Flex API"
 * - Historical year import: Temporarily change Query ID in Settings for each year
 * 
 * TECHNICAL DETAILS:
 * - Multi-year import logic from this window could be integrated into ImportWindow later
 * - Year checkbox UI pattern may be useful for future enhancements
 * - IBKRFlexImporter backend still supports multi-year imports via importYears() method
 * 
 * @see ImportWindow for current IBKR import implementation
 * @see IBKRFlexImporter for backend multi-year import support
 */

package cz.datesoft.stockAccounting;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Vector;

@Deprecated
public class IBKRFlexImportWindow extends javax.swing.JDialog {

    private MainWindow mainWindow;
    private boolean importCompleted = false;
    private Vector<Integer> selectedYears;

    private java.util.Map<Integer, javax.swing.JCheckBox> yearCheckboxes = new java.util.HashMap<>();

    public IBKRFlexImportWindow(java.awt.Frame parent, MainWindow mainWindow) {
        super(parent, "Import z IBKR Flex", true);
        this.mainWindow = mainWindow;

        initComponents();
        layoutComponents();
    }

    public Vector<Integer> getSelectedYears() {
        return selectedYears;
    }

    public boolean isImportCompleted() {
        return importCompleted;
    }

    private void initComponents() {
        int currentYear = LocalDate.now().getYear();

        JPanel yearPanel = new JPanel(new java.awt.GridLayout(0, 5, 5, 5));
        yearPanel.setBorder(BorderFactory.createTitledBorder("Vyberte roky k importu"));

        for (int year = currentYear - 4; year <= currentYear; year++) {
            javax.swing.JCheckBox checkbox = new javax.swing.JCheckBox(String.valueOf(year));
            yearCheckboxes.put(year, checkbox);
            yearPanel.add(checkbox);

            if (year == currentYear || year == currentYear - 1) {
                checkbox.setSelected(true);
            }
        }

        JPanel settingsPanel = new JPanel(new java.awt.GridLayout(2, 2, 5, 5));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("IBKR Flex nastavení"));

        javax.swing.JLabel queryLabel = new javax.swing.JLabel("Query ID:");
        javax.swing.JTextField queryField = new javax.swing.JTextField(40);
        queryField.setText(Settings.getIbkrFlexQueryId());

        javax.swing.JLabel tokenLabel = new javax.swing.JLabel("Flex Token:");
        javax.swing.JPasswordField tokenField = new javax.swing.JPasswordField(40);
        String savedToken = Settings.getIbkrFlexToken();
        if (savedToken != null && !savedToken.isEmpty()) {
            tokenField.setText(savedToken);
        }

        settingsPanel.add(queryLabel);
        settingsPanel.add(queryField);
        settingsPanel.add(tokenLabel);
        settingsPanel.add(tokenField);

        javax.swing.JButton importButton = new javax.swing.JButton("Importovat");
        importButton.addActionListener(e -> startImport(queryField.getText(), new String(tokenField.getPassword())));

        javax.swing.JButton cancelButton = new javax.swing.JButton("Zrušit");
        cancelButton.addActionListener(e -> dispose());

        setLayout(new java.awt.BorderLayout(10, 10));
        add(yearPanel, java.awt.BorderLayout.NORTH);
        add(settingsPanel, java.awt.BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(importButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, java.awt.BorderLayout.SOUTH);

        setSize(600, 400);
        setLocationRelativeTo(getParent());
    }

    private void layoutComponents() {

    }

    private void startImport(String queryId, String token) {
        Settings.setIbkrFlexQueryId(queryId.trim());
        Settings.setIbkrFlexToken(token.trim());

        selectedYears = new Vector<>();
        for (java.util.Map.Entry<Integer, javax.swing.JCheckBox> entry : yearCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedYears.add(entry.getKey());
            }
        }

        if (selectedYears.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Vyberte alespoň jeden rok k importu",
                    "Chyba", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        IBKRFlexImporter importer = new IBKRFlexImporter(token, queryId);
        importer.setParentFrame(mainWindow);

        String validationError = importer.getValidationError();
        if (validationError != null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    validationError,
                    "Chybějící nastavení", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        setVisible(false);

        try {
            // Create progress dialog (modal)
            IBKRFlexProgressDialog progressDialog = new IBKRFlexProgressDialog(
                    mainWindow, importer, selectedYears);

            // Start import first (runs in background)
            progressDialog.startImport();

            // Then show dialog and wait for completion
            IBKRFlexImporter.ImportResult result = progressDialog.waitForCompletion();

            if (result.success) {
                // Show preview table
                showPreviewTable(result);
                importCompleted = true;
            } else {
                javax.swing.JOptionPane.showMessageDialog(mainWindow,
                        "Import selhal: " + result.message,
                        "Chyba", javax.swing.JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(mainWindow,
                    "Chyba při importu: " + e.getMessage(),
                    "Chyba", javax.swing.JOptionPane.ERROR_MESSAGE);
        } finally {
            // Always show import window again
            setVisible(true);
        }
    }

    private void showPreviewTable(IBKRFlexImporter.ImportResult result) {
        // Collect all transactions from all years
        Vector<Transaction> allTransactions = new Vector<>();
        for (IBKRFlexImporter.ImportYearResult yearResult : result.yearsImported) {
            allTransactions.addAll(yearResult.transactions);
        }

        // Show preview table
        IBKRFlexPreviewTable previewTable = new IBKRFlexPreviewTable(
                (java.awt.Frame) getParent(), mainWindow, allTransactions);
        previewTable.setVisible(true);

        // Mark as completed
        importCompleted = true;
        dispose();
    }
}
