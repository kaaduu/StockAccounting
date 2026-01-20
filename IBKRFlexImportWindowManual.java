/*
 * IBKRFlexImportWindowManual.java
 *
 * Standalone manual import window for testing IBKR Flex import
 * Can be integrated into MainWindow by adding menu item
 */

package cz.datesoft.stockaccounting;

import javax.swing.*;
import java.awt.*;

public class IBKRFlexImportWindowManual extends javax.swing.JDialog {

    private MainWindow mainWindow;
    private Vector<Transaction> transactions;
    private Vector<Integer> selectedYears;

    public IBKRFlexImportWindowManual(java.awt.Frame parent, MainWindow mainWindow) {
        super(parent, "Import z IBKR Flex - Manual", false);
        this.mainWindow = mainWindow;
        this.transactions = new Vector<>();

        initComponents();
        testSetup();
    }

    private void initComponents() {
        int currentYear = java.time.LocalDate.now().getYear();

        JPanel yearPanel = new JPanel(new java.awt.GridLayout(0, 5, 5, 5));
        yearPanel.setBorder(BorderFactory.createTitledBorder("Vyberte roky k importu"));

        for (int year = currentYear - 4; year <= currentYear; year++) {
            javax.swing.JCheckBox checkbox = new javax.swing.JCheckBox(String.valueOf(year));
            if (year == currentYear || year == currentYear - 1) {
                checkbox.setSelected(true);
            }
        }

        JPanel settingsPanel = new JPanel(new java.awt.GridLayout(2, 2, 5, 5));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("IBKR Flex nastavení"));

        javax.swing.JLabel queryLabel = new javax.swing.JLabel("Query ID:");
        javax.swing.JTextField queryField = new javax.swing.JTextField(40);

        javax.swing.JLabel tokenLabel = new javax.swing.JLabel("Flex Token:");
        javax.swing.JPasswordField tokenField = new javax.swing.JPasswordField(40);

        javax.swing.JButton testButton = new javax.swing.JButton("Testovat nastavení");
        testButton.addActionListener(e -> testSettings());

        javax.swing.JButton importButton = new javax.swing.JButton("Importovat");
        importButton.addActionListener(e -> startImport());

        javax.swing.JButton cancelButton = new javax.swing.JButton("Zrušit");
        cancelButton.addActionListener(e -> dispose());

        settingsPanel.add(queryLabel);
        settingsPanel.add(queryField);
        settingsPanel.add(tokenLabel);
        settingsPanel.add(tokenField);
        settingsPanel.add(testButton);
        settingsPanel.add(importButton);
        settingsPanel.add(cancelButton);

        setLayout(new java.awt.BorderLayout(10, 10));
        add(yearPanel, java.awt.BorderLayout.NORTH);
        add(settingsPanel, java.awt.BorderLayout.CENTER);

        setSize(600, 500);
        setLocationRelativeTo(getParent());
    }

    private void testSettings() {
        String queryId = queryField.getText().trim();
        String token = new String(tokenField.getPassword()).trim();

        if (queryId.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                        "Prosím zadejte Query ID",
                        "Chyba",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (token.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                        "Prosím zadejte Flex Token",
                        "Chyba",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        javax.swing.JOptionPane.showMessageDialog(this,
                    "Nastavení vypadá v pořádku!\\n\\nQuery ID: " + queryId + "\\nToken: " + token,
                    "Úspěch",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private void startImport() {
        selectedYears = new Vector<>();

        for (java.awt.Component comp : getContentPane().getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                if (panel.getComponentCount() >= 5) {
                    java.awt.Component[] components = panel.getComponents();
                    if (components[0] instanceof JCheckBox) {
                        javax.swing.JCheckBox checkbox = (javax.swing.JCheckBox) components[0];
                        selectedYears.add(Integer.parseInt(checkbox.getText()));
                    }
                    break;
                }
            }
        }

        if (selectedYears.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                        "Prosím vyberte alespoň jeden rok k importu",
                        "Chyba",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        dispose();

        System.out.println("=== IBKR Flex Import Test ===");
        System.out.println("Roky k importu: " + selectedYears);
        System.out.println("Počet transakcí: " + transactions.size());

        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = transactions.get(i);
            System.out.println(tx.getStringDate() + " | " + tx.getStringType() + " | " + 
                        tx.getTicker() + " | " + tx.getDirection() + " | " + tx.getAmount() + " | " + 
                        tx.getPrice() + " " + tx.getFee());
        }
    }

    public Vector<Transaction> getTransactions() {
        return transactions;
    }
}
