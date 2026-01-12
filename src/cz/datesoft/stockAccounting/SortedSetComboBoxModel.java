/*
 * SortedSetComboBoxModel.java
 *
 * Created on 8. listopad 2006, 20:02
 *
 * Combo box model which implements a sorted set of items.
 */

package cz.datesoft.stockAccounting;

import javax.swing.*;
import java.util.Vector;

/**
 *
 * @author lemming2
 */
public class SortedSetComboBoxModel extends DefaultComboBoxModel<String> {
  /** Creates a new instance of SortedSetComboBoxModel */
  public SortedSetComboBoxModel() {
  }

  /**
   * Add item.
   *
   * @param item Item to be added
   *
   * @return Whether item was inserted.
   */
  public boolean putItem(String item) {
    for (int i = 0; i < getSize(); i++) {
      int r = ((String) getElementAt(i)).compareTo(item);

      if (r == 0)
        return false; // Equals

      if (r > 0) {
        // Insert new item here
        insertElementAt(item, i);

        return true;
      }
    }

    // Append
    addElement(item);

    return true;
  }
}
