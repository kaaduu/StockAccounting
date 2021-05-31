/*
 * ImportT212.java
 *
 * Created on 31. kvetna 2021
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting.imp;

import java.util.Vector;
import java.io.File;
import java.util.Date;

import cz.datesoft.stockAccounting.Transaction;
import static cz.datesoft.stockAccounting.imp.ImportBase.parseNumber;

/**
 * Import data from Trading 212 CSV export
 *
 * @author Michal Kara
 */
public class ImportT212 extends ImportBase
{
  /**
   * Identities of columns for this imported
   */
  private final int ID_TICKER = 1;
  private final int ID_TYPE = 2;
  private final int ID_AMOUNT = 3;
  private final int ID_PRICE = 4;
  private final int ID_CURRENCY = 5;
  private final int ID_DATE = 6;
  private final int ID_MARKET = 7;
  private final int ID_EXECUTION_DATE = 8;
  private final int ID_NOTE = 9;
  private final int ID_FEE_CZK = 10;
  private final int ID_FEE_USD = 11;
  private final int ID_FEE_EUR = 12;
  
  /**
   * Our data row with a bit more fields
   */
  public class T212DataRow extends ImportBase.DataRow
  {
    String text;
  }
  
  /** Creates a new instance of ImportT212 */
  public ImportT212()
  {
    super();
  }
  
  public Vector<Transaction> doImport(File srcFile, Date startDate, Date endDate, Vector<String[]> notImported) throws ImportException, java.io.IOException
  {
    Vector<Transaction> res = new Vector<Transaction>();
    java.io.BufferedReader ifl = new java.io.BufferedReader(new java.io.FileReader(srcFile));
    String s;
    
    // Establish column names
    registerColumnName("Ticker", ID_TICKER);
    registerColumnName("Action", ID_TYPE);    
    registerColumnName("No. of shares", ID_AMOUNT);    
    registerColumnName("Price / share", ID_PRICE);
    registerColumnName("Currency (Price / share)", ID_CURRENCY);    
    registerColumnName("Time", ID_DATE);
    //registerColumnName("Trh", ID_MARKET);
    //registerColumnName("Množství", ID_AMOUNT);    


    // Find start
    int neededLen = 0;
    boolean startFound = false;
    while((s = ifl.readLine()) != null) {
      String[] a = s.split(",");
      if (a.length >= 10) {
        startFound = true;
        for(int i = 0; i < a.length; i++) {
          if (setColumnIdentity(i, a[i])) {
            neededLen = i+1;
          }
        }
        
        break;
      }
    }
    
    if (!startFound) throw new ImportException("T212: Nemohu najít začátek dat - je soubor ve správném formátu?");
    
    // Check all columns are present...
    checkAllColumnsPresent();
    
    // Get indices
    int dirIdx = getColumnNo(ID_TYPE);
    //int marketIdx = getColumnNo(ID_MARKET);
    //int marketIdx = getColumnNo(ID_MARKET);
    int dateIdx = getColumnNo(ID_DATE);
    int amountIdx = getColumnNo(ID_AMOUNT);
    int currencyIdx = getColumnNo(ID_CURRENCY);
    //int textIdx = getColumnNo(ID_NOTE);
    //int executionIdx = getColumnNo(ID_EXECUTION_DATE);
    int tickerIdx = getColumnNo(ID_TICKER);
    int priceIdx = getColumnNo(ID_PRICE);
    //int feeCZKIdx = getColumnNo(ID_FEE_CZK);
    //int feeUSDIdx = getColumnNo(ID_FEE_USD);
    //int feeEURIdx = getColumnNo(ID_FEE_EUR);
    
    // Process data rows
    while((s = ifl.readLine()) != null) {
      boolean imported = false;
      
      String[] a = s.split(",", -1);
      
      if (a.length >= neededLen) {
        T212DataRow drow = new T212DataRow();

        String dirStr = a[dirIdx];

        drow.direction = 0;

        //if (equalsIgoreCaseAndEncoding(dirStr, "Limit buy", "Market buy")) drow.direction = Transaction.DIRECTION_SBUY;
        // "Limit buy", "Market buy", "Stop buy"
        if (dirStr.matches(".*buy.*")) drow.direction = Transaction.DIRECTION_SBUY;
        //else if (dirStr.equalsIgnoreCase("Prodej")) drow.direction = Transaction.DIRECTION_SSELL;
        // "Limit sell", "Market sell", "Stop sell"
        else if (dirStr.matches(".*sell.*")) drow.direction = Transaction.DIRECTION_SSELL;

        //if (drow.direction == 0) {            }
        //else {
          /** Buy, sell or transformation **/
          String ticker = a[tickerIdx];

          try {
            //LOGGER.debug("This is a debug");
            
            //drow.amount = (int)parseNumber(a[amountIdx]);
            drow.amount = (int)Math.abs(Double.parseDouble(a[amountIdx]) );
            drow.price = Double.parseDouble(a[priceIdx]);
            //drow.price = parseNumber(a[priceIdx]);
            drow.currency = a[currencyIdx].toUpperCase();
            //drow.market = a[marketIdx].toUpperCase();
            drow.market = "UnknownT212";

            /* Get date */
            //Date date = parseDate(a[dateIdx], null);
            String x = a[dateIdx];
            /* "2021-05-28 13:34:53" */      
            
            drow.date = parseDate(x.substring(8,10)+"."+x.substring(5,7)+"."+x.substring(0,4)+" "+x.substring(11), null);
            drow.executionDate = drow.date;
          
            
            
            //boolean outOfDateRange = false;

            //if ((startDate != null) && (date.compareTo(startDate) < 0)) outOfDateRange = true;
            //if ((endDate != null) && (date.compareTo(endDate) > 0)) outOfDateRange = true;

            //if (!outOfDateRange) {
            //drow.date = date;
            drow.ticker = ticker;
              
            //drow.executionDate = parseDate(a[executionIdx],date);
            //drow.executionDate = drow.date;

              addRow(res, drow);
              imported = true;              
            //}
          }
          catch(Exception e) {
            e.printStackTrace();
          } // Ignore row on exceptio
      //  } 
      }
      if ((!imported) && (notImported != null) && (a.length > 0)) {
        // Add not imported row to not imported
        notImported.add(a);
      }
    }
    
    return res;
  }
}
