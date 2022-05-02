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
public class ImportRevolutCSV extends ImportBase
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
  //private final int ID_FEE_CZK = 10;
  //private final int ID_FEE_USD = 11;
  //private final int ID_FEE_EUR = 12;
  
  /**
   * Our data row with a bit more fields
   */
  public class RevolutDataRow extends ImportBase.DataRow
  {
    String text;
  }
  
  /** Creates a new instance of ImportT212 */
  public ImportRevolutCSV()
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
    registerColumnName("Type", ID_TYPE);    
    registerColumnName("Quantity", ID_AMOUNT);    
    registerColumnName("Price per share", ID_PRICE);
    registerColumnName("Currency", ID_CURRENCY);    
    registerColumnName("Date", ID_DATE);
    //registerColumnName("Name", ID_NOTE);
    //registerColumnName("Trh", ID_MARKET);
    //registerColumnName("Množství", ID_AMOUNT);    
    //final int ID_FEE1 = 13;
    //registerColumnName("Transaction fee (USD)", ID_FEE1);
    //final int ID_FEE2 = 14;
    //registerColumnName("Finra fee (USD)", ID_FEE2);
    //final int ID_CASH = 15;
    //registerColumnName("Charge amount (USD)", ID_CASH);
    //final int ID_CASHFEE = 16;
    //registerColumnName("Deposit fee (USD)", ID_CASHFEE);
    

    // Find start
    int neededLen = 0;
    boolean startFound = false;
    while((s = ifl.readLine()) != null) {
      String[] a = s.split(";");
      if (a.length == 8) {
        startFound = true;
        for(int i = 0; i < a.length; i++) {
          if (setColumnIdentity(i, a[i])) {
            neededLen = i+1;
          }
        }
        
        break;
      }
    }
    
    if (!startFound) throw new ImportException("Revolut: Nemohu najít začátek dat - je soubor ve správném formátu?");
    
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
    //int feeTransactionIdx = getColumnNo(ID_FEE1);
    //int feeFinfraIdx = getColumnNo(ID_FEE2);
    //int feeEURIdx = getColumnNo(ID_FEE_EUR);
    int noteIdx = getColumnNo(ID_NOTE);
    //int depositIdx = getColumnNo(ID_CASH);
    //int depositfeeIdx = getColumnNo(ID_CASHFEE);
    
    // Process data rows
    while((s = ifl.readLine()) != null) {
      boolean imported = false;
      
      String[] a = s.split(";", -1);
      
      if (a.length >= neededLen) {
        RevolutDataRow drow = new RevolutDataRow();

        String dirStr = a[dirIdx];
        drow.direction = 0;

        
        
        // false false true :) mame vzdy jeden typ
        //boolean cash = dirStr.equals("Deposit");
        //boolean derivate = (a[typeIdx].equals("FUT") || a[typeIdx].equals("OPT") || a[typeIdx].equals("FOP") );
        //boolean stock = ( a[typeIdx].equals("STK") || a[typeIdx].equals("WAR"));


        // detekce typu 
         //if (!cash) {
            if (dirStr.matches(".*SELL.*")) drow.direction = Transaction.DIRECTION_SSELL;          
            if (dirStr.matches(".*BUY.*")) drow.direction = Transaction.DIRECTION_SBUY;
         //} else {
         //   if (dirStr.equals("Deposit")) drow.direction = Transaction.DIRECTION_CBUY;
            //Don't have real data so just guessing format :)
         //   if (dirStr.equals("Withdrawal"))  drow.direction = Transaction.DIRECTION_CSELL;             
         //}
        
        
        //if (equalsIgoreCaseAndEncoding(dirStr, "Limit buy", "Market buy")) drow.direction = Transaction.DIRECTION_SBUY;
        // "Limit buy", "Market buy", "Stop buy"
        //if (dirStr.matches(".*buy.*")) drow.direction = Transaction.DIRECTION_SBUY;
        //else if (dirStr.equalsIgnoreCase("Prodej")) drow.direction = Transaction.DIRECTION_SSELL;
        // "Limit sell", "Market sell", "Stop sell"
        //else if (dirStr.matches(".*sell.*")) drow.direction = Transaction.DIRECTION_SSELL;
        //if (drow.direction == 0) {            }
        //else {
          /** Buy, sell or transformation **/
          
          String ticker = a[tickerIdx]; 
          String currency =a[currencyIdx].toUpperCase();
          
          try {
            drow.ticker = ticker;
            //We dont report GBX only GBP so we need convert it
            //if (currency.equals("GBX")) {
            //    drow.currency = "GBP";
            //    drow.price = Double.parseDouble(a[priceIdx])/100;                                
            //} else {
            drow.currency = currency;
            drow.price = Double.parseDouble(a[priceIdx]);                                
            //}
            
            drow.amount = Double.parseDouble(a[amountIdx]);                                    
            
            //drow.market = a[marketIdx].toUpperCase();
            drow.market = "";
            //drow.note = a[noteIdx].replace("\"","")+"|Broker:Revolut";
            drow.note = "Broker:Revolut";
            /* Get date */   /* "2021-05-28 13:34:53" */      
            /* Get date /*   /* 09/08/2019 15:57:48   */
            String x = a[dateIdx];           
            drow.date = parseDate(x.substring(0,2)+"."+x.substring(3,5)+"."+x.substring(6,10)+" "+x.substring(11), null);
            drow.executionDate = drow.date;
            
            // Get fee - works of base currency USD?
            // Temporary fee = 0 sometimes those fields are not present
            //drow.fee = parseNumber(a[feeTransactionIdx])+parseNumber(a[feeFinfraIdx]);
            drow.fee = 0;
            
              
            //TODO
            // I need add 2 rows per one dividend record Dividena-Hruba a Dividenda-Dan
            // Determine direction (i.e. type)
            //       drow.text = a[textIdx];
            //      if (drow.text.equalsIgnoreCase("Dividenda - USA")) drow.direction = Transaction.DIRECTION_DIVI_BRUTTO;
            //      else if (equalsIgoreCaseAndEncoding(drow.text, "Daň z divid. zaplacená v USA", "Da? z divid. zaplacen? v USA")) drow.direction = Transaction.DIRECTION_DIVI_TAX;
            //      else {
            //        if (drow.price < 0) drow.direction = Transaction.DIRECTION_DIVI_TAX;
            //        else drow.direction = Transaction.DIRECTION_DIVI_UNKNOWN;
            //      }            
              addRow(res, drow);
              imported = true;              
          } catch(Exception e) {
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
