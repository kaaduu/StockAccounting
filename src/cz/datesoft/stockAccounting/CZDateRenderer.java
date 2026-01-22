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
   * Formatters
   */
  private SimpleDateFormat _d2;
  private SimpleDateFormat _d3;

  public CZDateRenderer()
  {
    // Initialize formatter
    _d2 = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    _d3 = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
  }

  @Override
  protected void setValue(Object value)
  {
    
    if (value == null) super.setValue(null);
    else if (value.getClass().equals(java.util.Date.class)) {
      java.util.Date d = (java.util.Date) value;
      java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
      cal.setTime(d);
      if (Settings.getShowSecondsInDateColumns() && cal.get(java.util.GregorianCalendar.SECOND) != 0) {
        super.setValue(_d3.format(d));
      } else {
        super.setValue(_d2.format(d));
      }
    }
    else super.setValue(value);
  }  
}
