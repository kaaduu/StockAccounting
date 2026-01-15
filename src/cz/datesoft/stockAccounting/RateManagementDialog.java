package cz.datesoft.stockAccounting;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Enhanced dialog for managing daily rates with granular control
 */
public class RateManagementDialog extends JDialog {

    private JTree selectionTree;
    private DefaultTreeModel treeModel;
    private JButton deleteButton;
    private JButton exportButton;
    private JButton importButton;
    private JButton undoButton;
    private JLabel statusLabel;

    private Map<String, Set<Integer>> selectedItems = new HashMap<>();

    public RateManagementDialog(java.awt.Window parent) {
        super(parent instanceof Frame ? (Frame) parent : null, "Správa denních kurzů", true);
        initComponents();
        loadRateStats();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(500, 600));

        // Tree panel
        JPanel treePanel = createTreePanel();
        add(treePanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        // Status label
        statusLabel = new JLabel("Načítám statistiky...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(statusLabel, BorderLayout.NORTH);
    }

    private JPanel createTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Create root node
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Denní kurzy");

        // Create tree
        treeModel = new DefaultTreeModel(root);
        selectionTree = new JTree(treeModel);
        selectionTree.setRootVisible(false);
        selectionTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        selectionTree.setCellRenderer(new RateTreeCellRenderer());

        // Add check box behavior
        selectionTree.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                int row = selectionTree.getRowForLocation(e.getX(), e.getY());
                if (row != -1) {
                    TreePath path = selectionTree.getPathForRow(row);
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

                    if (node.getUserObject() instanceof RateTreeNode) {
                        RateTreeNode rateNode = (RateTreeNode) node.getUserObject();
                        rateNode.selected = !rateNode.selected;
                        updateSelection(rateNode);
                        selectionTree.repaint();
                        updateStatus();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(selectionTree);
        scrollPane.setPreferredSize(new Dimension(450, 400));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Control buttons for tree
        JPanel treeControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllButton = new JButton("Vybrat vše");
        JButton selectNoneButton = new JButton("Zrušit výběr");
        JButton expandAllButton = new JButton("Rozbalit");

        selectAllButton.addActionListener(e -> selectAll());
        selectNoneButton.addActionListener(e -> selectNone());
        expandAllButton.addActionListener(e -> expandAll());

        treeControls.add(selectAllButton);
        treeControls.add(selectNoneButton);
        treeControls.add(expandAllButton);
        panel.add(treeControls, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        deleteButton = new JButton("Smazat vybrané");
        exportButton = new JButton("Exportovat");
        importButton = new JButton("Importovat");
        undoButton = new JButton("Zpět");

        deleteButton.addActionListener(this::deleteSelected);
        exportButton.addActionListener(this::exportSelected);
        importButton.addActionListener(this::importRates);
        undoButton.addActionListener(this::undoLast);

        undoButton.setEnabled(false); // Initially disabled

        panel.add(deleteButton);
        panel.add(exportButton);
        panel.add(importButton);
        panel.add(undoButton);

        JButton closeButton = new JButton("Zavřít");
        closeButton.addActionListener(e -> dispose());
        panel.add(closeButton);

        return panel;
    }

    private void loadRateStats() {
        Settings.RateStats stats = Settings.getRateStats();

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        for (Map.Entry<String, Map<Integer, Integer>> currencyEntry : stats.currencyYearCounts.entrySet()) {
            String currency = currencyEntry.getKey();
            Map<Integer, Integer> yearCounts = currencyEntry.getValue();

            // Calculate total for currency
            int currencyTotal = yearCounts.values().stream().mapToInt(Integer::intValue).sum();

            DefaultMutableTreeNode currencyNode = new DefaultMutableTreeNode(
                new RateTreeNode(currency, currencyTotal, false, true));
            root.add(currencyNode);

            // Add year nodes
            for (Map.Entry<Integer, Integer> yearEntry : yearCounts.entrySet()) {
                int year = yearEntry.getKey();
                int count = yearEntry.getValue();

                DefaultMutableTreeNode yearNode = new DefaultMutableTreeNode(
                    new RateTreeNode(year + " (" + count + ")", count, false, false, currency, year));
                currencyNode.add(yearNode);
            }
        }

        treeModel.reload();
        updateStatus();

        // Expand all currencies by default
        for (int i = 0; i < root.getChildCount(); i++) {
            TreePath path = new TreePath(new Object[]{root, root.getChildAt(i)});
            selectionTree.expandPath(path);
        }
    }

    private void selectAll() {
        selectAll((DefaultMutableTreeNode) treeModel.getRoot());
        treeModel.reload();
        updateStatus();
    }

    private void selectAll(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof RateTreeNode) {
            RateTreeNode rateNode = (RateTreeNode) node.getUserObject();
            rateNode.selected = true;
            updateSelection(rateNode);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            selectAll((DefaultMutableTreeNode) node.getChildAt(i));
        }
    }

    private void selectNone() {
        selectNone((DefaultMutableTreeNode) treeModel.getRoot());
        treeModel.reload();
        updateStatus();
    }

    private void selectNone(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof RateTreeNode) {
            RateTreeNode rateNode = (RateTreeNode) node.getUserObject();
            rateNode.selected = false;
            updateSelection(rateNode);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            selectNone((DefaultMutableTreeNode) node.getChildAt(i));
        }
    }

    private void expandAll() {
        for (int i = 0; i < selectionTree.getRowCount(); i++) {
            selectionTree.expandRow(i);
        }
    }

    private void updateSelection(RateTreeNode node) {
        if (node.isCurrency) {
            // Currency selection affects all its years
            Set<Integer> years = selectedItems.computeIfAbsent(node.text, k -> new HashSet<>());
            if (node.selected) {
                // Add all years for this currency
                Settings.RateStats stats = Settings.getRateStats();
                Map<Integer, Integer> yearCounts = stats.currencyYearCounts.get(node.text);
                if (yearCounts != null) {
                    years.addAll(yearCounts.keySet());
                }
            } else {
                years.clear();
            }
        } else {
            // Year selection
            Set<Integer> years = selectedItems.computeIfAbsent(node.currency, k -> new HashSet<>());
            if (node.selected) {
                years.add(node.year);
            } else {
                years.remove(node.year);
            }
        }
    }

    private void updateStatus() {
        int selectedGroups = 0;
        int totalRates = 0;

        for (Set<Integer> years : selectedItems.values()) {
            if (!years.isEmpty()) {
                selectedGroups += years.size();
                // Estimate rates count (this could be more accurate)
                totalRates += years.size() * 250; // Rough estimate
            }
        }

        Settings.RateStats stats = Settings.getRateStats();
        statusLabel.setText(String.format("Celkem: %d kurzů  Vybráno: %d skupin",
            stats.totalRates, selectedGroups));
    }

    private void deleteSelected(ActionEvent e) {
        if (selectedItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nevybrali jste žádné kurzy ke smazání.",
                "Žádný výběr", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Set<String> currencies = selectedItems.keySet();
        Set<Integer> allYears = new HashSet<>();
        for (Set<Integer> years : selectedItems.values()) {
            allYears.addAll(years);
        }

        int estimatedCount = currencies.size() * allYears.size() * 250; // Rough estimate

        int result = JOptionPane.showConfirmDialog(this,
            String.format("Opravdu chcete smazat vybrané kurzy?\n" +
                "Měny: %s\nRoky: %s\nOdhadovaný počet: ~%d kurzů\n\nBude vytvořena záloha.",
                String.join(", ", currencies), allYears.toString(), estimatedCount),
            "Potvrzení smazání", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            int deletedCount = Settings.deleteRates(currencies, allYears);

            JOptionPane.showMessageDialog(this,
                String.format("Smazáno %d denních kurzů.\nZáloha byla vytvořena.", deletedCount),
                "Hotovo", JOptionPane.INFORMATION_MESSAGE);

            undoButton.setEnabled(true);
            loadRateStats(); // Refresh the tree
        }
    }

    private void exportSelected(ActionEvent e) {
        if (selectedItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nevybrali jste žádné kurzy k exportu.",
                "Žádný výběr", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(generateExportFilename());
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            try {
                Set<String> currencies = selectedItems.keySet();
                Set<Integer> allYears = new HashSet<>();
                for (Set<Integer> years : selectedItems.values()) {
                    allYears.addAll(years);
                }

                Settings.exportRates(file, currencies, allYears);

                JOptionPane.showMessageDialog(this,
                    "Kurzy byly úspěšně exportovány do souboru:\n" + file.getName(),
                    "Export dokončen", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Chyba při exportu: " + ex.getMessage(),
                    "Chyba", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importRates(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV soubory", "csv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            try {
                // Show preview
                Settings.RateStats currentStats = Settings.getRateStats();
                JOptionPane.showMessageDialog(this,
                    String.format("Import ze souboru: %s\n\nAktuální stav: %d kurzů",
                        file.getName(), currentStats.totalRates),
                    "Náhled importu", JOptionPane.INFORMATION_MESSAGE);

                // For now, use OVERWRITE strategy. Interactive conflict resolution would need more UI work
                Settings.ImportResult result = Settings.importRates(file, Settings.ConflictResolutionStrategy.OVERWRITE);

                String message = String.format(
                    "Import dokončen:\n" +
                    "✓ Importováno: %d kurzů\n" +
                    "⚠ Přepsáno: %d kurzů\n" +
                    "✗ Přeskočeno: %d kurzů",
                    result.imported, result.overwritten, result.skipped);

                if (!result.errors.isEmpty()) {
                    message += "\n\nChyby:\n" + String.join("\n", result.errors.subList(0, Math.min(5, result.errors.size())));
                }

                JOptionPane.showMessageDialog(this, message,
                    "Import dokončen", JOptionPane.INFORMATION_MESSAGE);

                loadRateStats(); // Refresh the tree

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Chyba při importu: " + ex.getMessage(),
                    "Chyba", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void undoLast(ActionEvent e) {
        if (Settings.undoLastDeletion()) {
            JOptionPane.showMessageDialog(this, "Poslední smazání bylo obnoveno.",
                "Zpět", JOptionPane.INFORMATION_MESSAGE);
            undoButton.setEnabled(false);
            loadRateStats(); // Refresh the tree
        } else {
            JOptionPane.showMessageDialog(this, "Nelze obnovit - žádná operace k vrácení.",
                "Chyba", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File generateExportFilename() {
        Set<String> currencies = selectedItems.keySet();
        Set<Integer> allYears = new HashSet<>();
        for (Set<Integer> years : selectedItems.values()) {
            allYears.addAll(years);
        }

        String currenciesStr = currencies.isEmpty() ? "all" :
            String.join("_", currencies).substring(0, Math.min(20, String.join("_", currencies).length()));
        String yearsStr = allYears.isEmpty() ? "all" :
            allYears.stream().map(String::valueOf).reduce((a,b) -> a + "_" + b).orElse("all");

        return new File(String.format("daily_rates_%s_%s.csv", currenciesStr, yearsStr));
    }

    // Tree node data structure
    private static class RateTreeNode {
        String text;
        int count;
        boolean selected;
        boolean isCurrency;
        String currency;
        int year;

        RateTreeNode(String text, int count, boolean selected, boolean isCurrency) {
            this.text = text;
            this.count = count;
            this.selected = selected;
            this.isCurrency = isCurrency;
        }

        RateTreeNode(String text, int count, boolean selected, boolean isCurrency, String currency, int year) {
            this(text, count, selected, isCurrency);
            this.currency = currency;
            this.year = year;
        }

        @Override
        public String toString() {
            return String.format("%s %s (%d)", selected ? "☑" : "☐", text, count);
        }
    }

    // Custom tree cell renderer with checkboxes
    private static class RateTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (node.getUserObject() instanceof RateTreeNode) {
                    RateTreeNode rateNode = (RateTreeNode) node.getUserObject();
                    setText(rateNode.toString());
                }
            }

            return this;
        }
    }
}