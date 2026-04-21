package cz.datesoft.stockAccounting;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

/**
 * Small Swing helpers for actions that must not discard an in-progress table
 * edit.
 */
final class TableEditSupport {
  private static final String LAST_EDIT_ROW_KEY = TableEditSupport.class.getName() + ".lastEditRow";
  private static final String LAST_EDIT_COLUMN_KEY = TableEditSupport.class.getName() + ".lastEditColumn";

  private TableEditSupport() {
  }

  static void rememberCurrentEditingCell(JTable table) {
    if (table == null || !table.isEditing()) {
      return;
    }

    table.putClientProperty(LAST_EDIT_ROW_KEY, Integer.valueOf(table.getEditingRow()));
    table.putClientProperty(LAST_EDIT_COLUMN_KEY, Integer.valueOf(table.getEditingColumn()));
  }

  static int getLastEditedRow(JTable table) {
    if (table == null) {
      return -1;
    }

    Object value = table.getClientProperty(LAST_EDIT_ROW_KEY);
    return value instanceof Integer ? ((Integer) value).intValue() : -1;
  }

  static int getLastEditedColumn(JTable table) {
    if (table == null) {
      return -1;
    }

    Object value = table.getClientProperty(LAST_EDIT_COLUMN_KEY);
    return value instanceof Integer ? ((Integer) value).intValue() : -1;
  }

  static boolean commitActiveDataEditor(JTable table, int actionColumn) {
    if (table == null) {
      return true;
    }

    rememberCurrentEditingCell(table);

    if (!table.isEditing()) {
      return true;
    }
    if (table.getEditingColumn() == actionColumn) {
      return true;
    }

    return commitActiveEditor(table);
  }

  static boolean commitActiveEditor(JTable table) {
    if (table == null || !table.isEditing()) {
      return true;
    }

    TableCellEditor editor = table.getCellEditor();
    if (editor == null) {
      return !table.isEditing();
    }

    if (!editor.stopCellEditing()) {
      return false;
    }

    return !table.isEditing();
  }

  static boolean activateCellEditor(JTable table, int row, int column) {
    if (table == null || row < 0 || column < 0) {
      return false;
    }
    if (row >= table.getRowCount() || column >= table.getColumnCount()) {
      return false;
    }

    table.changeSelection(row, column, false, false);
    table.requestFocusInWindow();

    table.putClientProperty(LAST_EDIT_ROW_KEY, Integer.valueOf(row));
    table.putClientProperty(LAST_EDIT_COLUMN_KEY, Integer.valueOf(column));

    if (!table.editCellAt(row, column)) {
      return false;
    }

    Component editorComponent = table.getEditorComponent();
    if (editorComponent != null) {
      editorComponent.requestFocusInWindow();
    }

    return table.isEditing();
  }
}
