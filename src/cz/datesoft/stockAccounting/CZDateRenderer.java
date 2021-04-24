/*
 * CZDateRenderer.java
 *
 * Created on 8. listopad 2006, 22:05
 *
 * Render date as a DD. MM. YYYY string
 */

package cz.datesoft.stockAccounting;

import java.text.SimpleDateFormat;

/**
 * Renders date using DD.MM.YYYY HH:MM format
 *
 * @author Michal Kara
 */
public class CZDateRenderer extends javax.swing.table.DefaultTableCellRenderer
{
  /**
   * Formatter
   */
  private SimpleDateFormat _d2;

  public CZDateRenderer()
  {
    // Initialize formatter
    _d2 = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
  }

  @Override
  protected void setValue(Object value)
  {
    
    if (value == null) super.setValue(null);
    else if (value.getClass().equals(java.util.Date.class)) {
      super.setValue(_d2.format((java.util.Date)value));
    }
    else super.setValue(value);
  }  
}
