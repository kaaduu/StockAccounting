/*
 * IBKRFlexPreviewTable.java
 *
 * Preview table for IBKR Flex transactions before import
 */

package cz.datesoft.stockAccounting;

import javax.swing.*;
import java.util.Vector;
import cz.datesoft.stockAccounting.*;

public class IBKRFlexPreviewTable extends JDialog {

    private MainWindow mainWindow;
    private Vector<Transaction> transactions;
    private TransactionSet previewData;
    private JTable table;

    public IBKRFlexPreviewTable(java.awt.Frame parent, MainWindow mainWindow, Vector<Transaction> transactions) {
        super(parent, "Náhled importovaných transakcí", false);
        this.mainWindow = mainWindow;
        this.transactions = transactions;
        this.previewData = new TransactionSet();

        initComponents();
        loadData();
    }

    private void initComponents() {
        setSize(900, 500);
        setLocationRelativeTo(getParent());
        setLayout(new java.awt.BorderLayout());

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setPreferredSize(new java.awt.Dimension(880, 400));

        table = new JTable(previewData);
        table.setRowHeight(25);
        table.setDefaultRenderer(Object.class, new PreviewCellRenderer());

        add(scrollPane, java.awt.BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton importButton = new JButton("Importovat vybrané");
        importButton.addActionListener(e -> {
            importSelected();
        });

        JButton importAllButton = new JButton("Importovat vše");
        importAllButton.addActionListener(e -> {
            importAll();
        });

        JButton cancelButton = new JButton("Zrušit");
        cancelButton.addActionListener(e -> {
            dispose();
        });

        buttonPanel.add(importButton);
        buttonPanel.add(importAllButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, java.awt.BorderLayout.SOUTH);
    }

    private void loadData() {
        for (Transaction tx : transactions) {
            try {
                previewData.addTransaction(tx.getDate(), tx.getDirection().intValue(), tx.getTicker(),
                        tx.getAmount().doubleValue(), tx.getPrice().doubleValue(), tx.getPriceCurrency(),
                        tx.getFee().doubleValue(), tx.getFeeCurrency(), tx.getMarket(),
                        tx.getExecutionDate(), tx.getNote());
            } catch (Exception e) {
                // Skip invalid transactions
            }
        }
        previewData.fireTableDataChanged();
    }

    private void importSelected() {
        for (int i = 0; i < previewData.getRowCount(); i++) {
            if (table.isRowSelected(i)) {
                Transaction tx = previewData.getRowAt(i);
                try {
                    mainWindow.getTransactionDatabase().addTransaction(tx.getDate(), tx.getDirection().intValue(), tx.getTicker(),
                            tx.getAmount().doubleValue(), tx.getPrice().doubleValue(), tx.getPriceCurrency(),
                            tx.getFee().doubleValue(), tx.getFeeCurrency(), tx.getMarket(),
                            tx.getExecutionDate(), tx.getNote());
                } catch (Exception e) {
                    // Skip invalid transactions
                }
            }
        }
        mainWindow.refreshTable();
        dispose();
    }

    private void importAll() {
        for (int i = 0; i < previewData.getRowCount(); i++) {
            Transaction tx = previewData.getRowAt(i);
            try {
                mainWindow.getTransactionDatabase().addTransaction(tx.getDate(), tx.getDirection().intValue(), tx.getTicker(),
                        tx.getAmount().doubleValue(), tx.getPrice().doubleValue(), tx.getPriceCurrency(),
                        tx.getFee().doubleValue(), tx.getFeeCurrency(), tx.getMarket(),
                        tx.getExecutionDate(), tx.getNote());
            } catch (Exception e) {
                // Skip invalid transactions
            }
        }
        mainWindow.refreshTable();
        dispose();
    }

    private static class PreviewCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
            javax.swing.JLabel label = new javax.swing.JLabel(value != null ? value.toString() : "");
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(new java.awt.Color(200, 220, 255));
            } else {
                label.setBackground(java.awt.Color.WHITE);
            }
            return label;
        }
    }
}
