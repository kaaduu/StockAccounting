/*
 * ImportBase.java
 *
 * Created on 17. unor 2008, 17:10
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting.imp;

import java.util.Vector;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.GregorianCalendar;
import cz.datesoft.stockAccounting.Transaction;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * Common base class for importers - defines utility functions
 *
 * @author Michal Kara
 */
public abstract class ImportBase
{
  /**
   * Data row - struct. In its base here it contains data needed for transaction
   */
  public class DataRow
  {
    public Date date;
    public int direction;
    public String ticker;
    //changed to double from int to support fractional shares
    public double amount;
    public double price;
    public double fee;
    public String currency;
    public String feeCurrency;
    public String market;
    public Date executionDate;
    public String note;
  }
  
  /**
   * Identity - NONE
   */
  public final int ID_NONE = 0;
  
  /**
   * List telling "what which column contains"
   */
  private Vector<Integer> _fields;
  
  /**
   * Hash mapping column name => column id
   */
  private HashMap<String, Integer> _columnNames;
  
  private final CharsetDecoder DECODER_WIN1250 = Charset.forName("Windows-1250").newDecoder();
  private final CharsetDecoder DECODER_ISO88592 = Charset.forName("ISO-8859-2").newDecoder();
  
  /**
   * Set identity of a column by a number
   */
  protected void setColumnIdentity(int columnNo, int identity)
  {
    if (_fields.size() > columnNo) _fields.set(columnNo, Integer.valueOf(identity));
    
    while(_fields.size() < columnNo) {
      _fields.add(Integer.valueOf(ID_NONE));
    }
    
    _fields.add(Integer.valueOf(identity));
  }
  
  /**
   * Set identity of a column by a string
   *
   * @return Whether column was a known column
   */
  protected boolean setColumnIdentity(int columnNo, String name)
  {
    Integer i = _columnNames.get(name);
    
    if (i == null) {
        // Try without encoding
        try {
            String name2 = new String(name.getBytes("ISO-8859-1"));
            i = _columnNames.get(name2);
        }
        catch(Exception e) {}
    }
    
    setColumnIdentity(columnNo, (i == null)?ID_NONE:i.intValue());

    return (i != null);
  }  
  
  /**
   * Check that strings equal ignoring case
   * 
   * @param tested String to test, will be tried in different encodings
   * @param utfEncoding String to test, in UTF encoding
   * @param isoEncoding String to test, in ISO encofing
   * 
   * @return Whether strings equal
   */
  protected boolean equalsIgoreCaseAndEncoding(String anyEncoding, String utfEncoding, String isoEncoding) {
      if (anyEncoding == null) {
          return (utfEncoding == null);
      }
      
      if (utfEncoding == null) {
          return false;
      }
      
      if (utfEncoding.equals(anyEncoding)) {
          return true;
      }
      
      try {
        if (isoEncoding.equals(new String(anyEncoding.getBytes("ISO-8859-1")))) {
            return true;
        }
      }
      catch(Exception e) {}
      
      return false;
  }

  /**
   * Get identity of an column
   */
  protected int getColumnIdentity(int columnNo)
  {
    if (columnNo >= _fields.size()) return ID_NONE;
    return _fields.get(columnNo).intValue();
  }

  /**
   * Get column no based on identity
   *
   * @return Column no, -1 when not found
   */
  protected int getColumnNo(int identity)
  {
    // XXX Not so quick, but it should not matter much XXX
    
    for(int i = 0; i < _fields.size(); i++) {
      if (_fields.get(i).intValue() == identity) return i;
    }
    
    return -1;
  }
  
  
  /**
   * Parses number out of the field
   */
  protected static double parseNumber(String value)
  {
    // Remove all non-numeric and non-comma characters
    String s = value.replaceAll("[^0-9,.]+", "");
    
    if (s.length() == 0) return 0; // Empty string is 0
    
    // Convert comma to dot and return
    return Double.parseDouble(s.replace(',','.'));
  }
  
  /**
   * Convert date from string to text
   */
  public Date parseDate(String dateStr, Date timeDate)
  {
     GregorianCalendar cal = new GregorianCalendar();
     String[] a = dateStr.split("[\\. :]");
     
     if ((a.length == 3) || (a.length == 5) || (a.length == 6)) {
       cal.set(cal.DAY_OF_MONTH,Integer.parseInt(a[0]));
       cal.set(cal.MONTH,Integer.parseInt(a[1])-1);
       cal.set(cal.YEAR,Integer.parseInt(a[2]));
            
       if (a.length >= 5) {
         // Set time from string
         cal.set(cal.HOUR_OF_DAY,Integer.parseInt(a[3]));
         cal.set(cal.MINUTE,Integer.parseInt(a[4]));
         if (a.length == 6) {
           cal.set(cal.SECOND,Integer.parseInt(a[5]));
         }
       }
       else {
         if (timeDate != null) {
           // Set time from timeDate
           GregorianCalendar time = new GregorianCalendar ();
           time.setTime(timeDate);
           
           cal.set(cal.HOUR_OF_DAY,time.get(time.HOUR_OF_DAY));
           cal.set(cal.MINUTE,time.get(time.MINUTE));
         }
         else {
           // Zero time
           cal.set(cal.HOUR_OF_DAY,0);
           cal.set(cal.MINUTE,0);
         }
         
       }
       
       cal.set(cal.SECOND,0);
       cal.set(cal.MILLISECOND,0);
     }    
     
     return cal.getTime();
  }  
  
  /**
   * Register column name with this identity
   */
  protected void registerColumnName(String name, int identity)
  {
    _columnNames.put(name, Integer.valueOf(identity));
  }
    
  /**
   * Check that all known columns were found. Throws an exception when not.
   */
  protected void checkAllColumnsPresent() throws ImportException
  {
    // Pass all names
    for(String name : _columnNames.keySet()) {
      int identity = _columnNames.get(name).intValue();
      
      // Check if some column has this identity
      boolean found = false;
      for(Integer id : _fields) {
        if (id == identity) {
          found = true;
          break;
        }
      }
      
      if (!found) {
        // Column not found - throw an exception
        throw new ImportException("Required column '" + name + "' not found in the input data!");
      }
    }
  }
  
  /**
   * Transform data row into transaction and add it to a vector
   */
  protected void addRow(Vector<Transaction> set, DataRow row) throws Exception
  {
    Transaction tx = new Transaction(0, row.date, row.direction, row.ticker, row.amount, row.price, row.currency, row.fee, row.feeCurrency, row.market, row.executionDate, row.note);
    
    set.add(tx);
  }
  
  /**
   * Creates a new instance of ImportBase
   */
  public ImportBase()
  {
    _fields = new Vector<Integer>();
    _columnNames = new HashMap<String, Integer>();
  }
  
  public abstract Vector<Transaction> doImport(File srcFile, Date startDate, Date endDate, Vector<String[]> notImported) throws ImportException, java.io.IOException;
}