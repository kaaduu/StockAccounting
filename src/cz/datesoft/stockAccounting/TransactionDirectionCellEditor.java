/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting;

import javax.swing.JTable;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import java.awt.Component;

/**
 * Combo box cell editor which changes model according to the transaction type
 *
 * @author Michal Kara
 */
public class TransactionDirectionCellEditor extends DefaultCellEditor
{
  private JComboBox _component;

  public TransactionDirectionCellEditor(JComboBox component)
  {
    super(component);
    _component = component;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
  {
    /* Set combo box model according to type */
    DefaultComboBoxModel m = (DefaultComboBoxModel)_component.getModel();

    // Add elements
    m.removeAllElements();
    for(String e : ((TransactionSet)table.getModel()).getRowAt(row).getPossibleDirections()) {
      m.addElement(e);
    }

    // Set selected
    _component.setSelectedItem(value);
    
    // Return component
    return _component;
  }
}
