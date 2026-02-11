package test;

import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;

public class TestRenderer extends DefaultTableCellRenderer {
    private static final Color TEST_COLOR = new Color(232, 244, 248);
    private static final Font TEST_FONT = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);

    @Override
    public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (isSelected) {
            return c;
        }

        setBackground(TEST_COLOR);
        setFont(TEST_FONT);
        setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 2, 0, new java.awt.Color(200, 200, 200)));

        return c;
    }
}