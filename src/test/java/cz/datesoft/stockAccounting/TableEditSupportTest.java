package cz.datesoft.stockAccounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Iterator;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TableEditSupportTest {

  @Test
  void commitActiveEditorPersistsValuesIntoLastEmptyTransactionRow() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      TransactionSet transactions = new TransactionSet();
      JTable table = new JTable(transactions);

      assertEquals(1, transactions.getRowCount(), "Empty dataset should expose only the extra input row");
      assertTrue(table.editCellAt(0, 3), "Ticker cell in the extra input row should be editable");

      JTextField editor = (JTextField) table.getEditorComponent();
      editor.setText("ETHZ");

      assertTrue(TableEditSupport.commitActiveEditor(table), "Active edit should be committed");
      assertFalse(table.isEditing(), "Table should leave edit mode after successful commit");
      assertEquals(2, transactions.getRowCount(), "Committed edit should create a real row plus the extra input row");
      assertEquals("ETHZ", transactions.getValueAt(0, 3), "Ticker value should be stored in the model");
    });
  }

  @Test
  void activateCellEditorStartsEditingRequestedCell() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      TransactionSet transactions = new TransactionSet();
      JTable table = new JTable(transactions);

      assertTrue(TableEditSupport.activateCellEditor(table, 0, 0),
          "Helper should start editing on the requested input cell");
      assertTrue(table.isEditing(), "Table should enter edit mode");
      assertEquals(0, table.getEditingRow(), "Editing should start on the requested row");
      assertEquals(0, table.getEditingColumn(), "Editing should start on the requested column");
    });
  }

  @Test
  void reactivatingLastInputRowKeepsCommittedRowAndMovesToNextPlaceholder() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      TransactionSet transactions = new TransactionSet();
      JTable table = new JTable(transactions);

      assertTrue(TableEditSupport.activateCellEditor(table, 0, 3));
      JTextField firstEditor = (JTextField) table.getEditorComponent();
      firstEditor.setText("ETHZ");
      assertTrue(TableEditSupport.commitActiveEditor(table));

      assertEquals(2, table.getRowCount(), "After committing the first row there should be a new placeholder row");
      assertEquals("ETHZ", transactions.getValueAt(0, 3), "Committed row should stay intact");

      int nextPlaceholderRow = table.getRowCount() - 1;
      assertTrue(TableEditSupport.activateCellEditor(table, nextPlaceholderRow, 0),
          "Second activation should target the new placeholder row");
      assertTrue(table.isEditing());
      assertEquals(1, table.getEditingRow(), "Editing should move to the next placeholder row");
      assertEquals("ETHZ", transactions.getValueAt(0, 3), "Existing row must not be replaced");
    });
  }

  @Test
  void actionColumnAppearsOnCurrentManualRowAndNotOnPlaceholder() {
    TransactionSet transactions = new TransactionSet();

    assertEquals("Uložit", transactions.getValueAt(0, TransactionSet.COL_ACTION),
        "Empty table should offer save action on the initial input row");
    assertTrue(transactions.isCellEditable(0, TransactionSet.COL_ACTION),
        "Initial input row should allow clicking the save action");

    transactions.setValueAt("ETHZ", 0, 3);

    assertEquals(2, transactions.getRowCount(), "Typing into the input row should create a placeholder row");
    assertEquals("Uložit", transactions.getValueAt(0, TransactionSet.COL_ACTION),
        "The newest real row should expose the save action");
    assertEquals(null, transactions.getValueAt(1, TransactionSet.COL_ACTION),
        "The placeholder row should not expose another save action");
    assertTrue(transactions.isCellEditable(0, TransactionSet.COL_ACTION),
        "Current manual row should allow the save action");
    assertFalse(transactions.isCellEditable(1, TransactionSet.COL_ACTION),
        "Placeholder row should not be clickable in the action column");
  }

  @Test
  void actionColumnDoesNotCreateRowOnItsOwn() {
    TransactionSet transactions = new TransactionSet();

    transactions.setValueAt("Uložit", 0, TransactionSet.COL_ACTION);

    assertEquals(1, transactions.getRowCount(), "Clicking the action column alone must not create a new row");
  }

  @Test
  void commitActiveDataEditorCompletesTransformationRowBeforeSaveAction() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      TransactionSet transactions = new TransactionSet();
      JTable table = new JTable(transactions);

      JComboBox<String> typeCombo = new JComboBox<>(
          new String[] { "CP", "Derivát", "Transformace", "Dividenda", "Úrok", "Cash" });
      table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));

      Date txDate = TransactionSet.parseDate("21.04.2026 10:15");
      transactions.setValueAt(txDate, 0, 0);
      transactions.setValueAt("AAPL", 0, 3);
      transactions.setValueAt(Double.valueOf(12.0), 0, 4);

      Transaction tx = transactions.getRowAt(0);
      assertFalse(tx.isFilledIn(), "Row should stay incomplete before the pending type selection is committed");

      assertTrue(table.editCellAt(0, 1), "Type cell should enter edit mode");
      @SuppressWarnings("unchecked")
      JComboBox<String> editor = (JComboBox<String>) table.getEditorComponent();
      editor.setSelectedItem("Transformace");

      assertTrue(TableEditSupport.commitActiveDataEditor(table, TransactionSet.COL_ACTION),
          "Pending type editor should be committed before save action runs");
      assertEquals("Transformace", tx.getStringType(), "Committed type should reach the model");
      assertTrue(tx.isFilledIn(), "Transformation row should be complete after the pending type edit is committed");
    });
  }

  @Test
  void commitActiveEditorCompletesTransformationRowBeforeDataAction() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      TransactionSet transactions = new TransactionSet();
      JTable table = new JTable(transactions);

      JComboBox<String> typeCombo = new JComboBox<>(
          new String[] { "CP", "Derivát", "Transformace", "Dividenda", "Úrok", "Cash" });
      table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));

      Date txDate = TransactionSet.parseDate("21.04.2026 10:15");
      transactions.setValueAt(txDate, 0, 0);
      transactions.setValueAt("AAPL", 0, 3);
      transactions.setValueAt(Double.valueOf(12.0), 0, 4);

      Transaction tx = transactions.getRowAt(0);
      assertFalse(tx.isFilledIn(), "Row should stay incomplete before the generic data action commits the editor");

      assertTrue(table.editCellAt(0, 1), "Type cell should enter edit mode");
      @SuppressWarnings("unchecked")
      JComboBox<String> editor = (JComboBox<String>) table.getEditorComponent();
      editor.setSelectedItem("Transformace");

      assertTrue(TableEditSupport.commitActiveEditor(table),
          "Generic data actions should commit the active cell editor before using the model");
      assertEquals("Transformace", tx.getStringType(), "Committed type should reach the model");
      assertTrue(tx.isFilledIn(), "Transformation row should be complete after the pending type edit is committed");
    });
  }

  @Test
  void savedTransformationRowSurvivesPersistenceRoundTrip() throws Exception {
    TransactionSet transactions = new TransactionSet();

    Date txDate = TransactionSet.parseDate("21.04.2026 10:15");
    transactions.setValueAt(txDate, 0, 0);
    transactions.setValueAt("Transformace", 0, 1);
    transactions.setValueAt("AAPL", 0, 3);
    transactions.setValueAt(Double.valueOf(12.0), 0, 4);

    Transaction tx = transactions.getRowAt(0);
    assertTrue(tx.isFilledIn(), "Prepared transformation row should be persisted");

    Path tempFile = Files.createTempFile("stock-accounting-transformace-", ".dat");
    try {
      transactions.save(tempFile.toFile());

      TransactionSet loaded = new TransactionSet();
      loaded.load(tempFile.toFile());

      assertEquals(2, loaded.getRowCount(), "Loaded dataset should contain the saved row and one placeholder row");
      Transaction loadedTx = loaded.getRowAt(0);
      assertEquals("Transformace", loadedTx.getStringType(), "Loaded row should keep transformation type");
      assertEquals("AAPL", loadedTx.getTicker(), "Loaded row should keep ticker");
      assertEquals(Double.valueOf(12.0), loadedTx.getAmount(), "Loaded row should keep amount");
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void savedTransformationRowAppearsInTransformationFilter() {
    TransactionSet transactions = new TransactionSet();

    Date txDate = TransactionSet.parseDate("21.04.2026 10:15");
    transactions.setValueAt(txDate, 0, 0);
    transactions.setValueAt("Transformace", 0, 1);
    transactions.setValueAt("AAPL", 0, 3);
    transactions.setValueAt(Double.valueOf(12.0), 0, 4);

    Transaction tx = transactions.getRowAt(0);
    assertTrue(tx.isFilledIn(), "Prepared transformation row should be visible in filters");

    transactions.applyFilter(txDate, txDate, null, null, "Transformace", null, null, null, null);

    assertEquals(2, transactions.getRowCount(), "Filtered view should show the transformation row plus placeholder row");
    assertEquals("Transformace", transactions.getValueAt(0, 1),
        "Transformation row should remain visible when filtering by type");
  }

  @Test
  void newRowCreatedUnderActiveFilterSurvivesClearFilter() {
    TransactionSet transactions = new TransactionSet();

    Date txDate = TransactionSet.parseDate("21.04.2026 10:15");
    transactions.applyFilter(txDate, txDate, null, null, "Transformace", null, null, null, null);

    transactions.setValueAt(txDate, 0, 0);
    transactions.setValueAt("Transformace", 0, 1);
    transactions.setValueAt("AAPL", 0, 3);
    transactions.setValueAt(Double.valueOf(12.0), 0, 4);

    assertEquals(2, transactions.getRowCount(),
        "Filtered view should still show the created transformation row plus placeholder row");

    transactions.clearFilter();

    assertEquals(2, transactions.getRowCount(),
        "Clearing the filter should keep the newly created row in the main dataset");
    assertEquals("Transformace", transactions.getValueAt(0, 1), "Created row should remain in the base model");
    assertEquals("AAPL", transactions.getValueAt(0, 3), "Ticker should survive after clearing the filter");
  }

  @Test
  void newRowCreatedUnderActiveFilterIsVisibleToIterator() {
    TransactionSet transactions = new TransactionSet();

    Date txDate = TransactionSet.parseDate("21.04.2026 10:15");
    transactions.applyFilter(txDate, txDate, null, null, "Transformace", null, null, null, null);

    transactions.setValueAt(txDate, 0, 0);
    transactions.setValueAt("Transformace", 0, 1);
    transactions.setValueAt("AAPL", 0, 3);
    transactions.setValueAt(Double.valueOf(12.0), 0, 4);

    Iterator<Transaction> it = transactions.iterator();
    assertTrue(it.hasNext(), "Iterator should expose rows created while a filter is active");

    Transaction tx = it.next();
    assertEquals("Transformace", tx.getStringType(), "Iterator should see the transformation row");
    assertEquals("AAPL", tx.getTicker(), "Iterator should see the persisted ticker");
    assertEquals(Double.valueOf(12.0), tx.getAmount(), "Iterator should see the persisted amount");
    assertFalse(it.hasNext(), "Only one created row should be present in the base dataset");
  }
}
