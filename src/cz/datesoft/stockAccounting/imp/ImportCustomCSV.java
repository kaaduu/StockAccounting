/*
 * ImportCustomCSV.java
 *
 * Created on 31. kvetna 2021
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting.imp;

//import static com.sun.xml.internal.ws.spi.db.BindingContextFactory.LOGGER;
import java.util.Vector;
import java.io.File;
import java.util.Date;

import cz.datesoft.stockAccounting.Transaction;
import static cz.datesoft.stockAccounting.imp.ImportBase.parseNumber;
import java.util.Arrays;

/**
 * Import data from Trading 212 CSV export
 *
 * @author Michal Kara
 */
public class ImportCustomCSV extends ImportBase
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
  private final int ID_FEE = 10;
  private final int ID_FEE_CURRENCY = 11;
  //private final int ID_FEE_EUR = 12;
  
  /**
   * Our data row with a bit more fields
   */
  public class CustomCSVDataRow extends ImportBase.DataRow
  {
    String text;
  }
  
  /** Creates a new instance of ImportCustomCSV */
  public ImportCustomCSV()
  {
    super();
  }
  
  public Vector<Transaction> doImport(File srcFile, Date startDate, Date endDate, Vector<String[]> notImported) throws ImportException, java.io.IOException
  {
    Vector<Transaction> res = new Vector<Transaction>();
    java.io.BufferedReader ifl = new java.io.BufferedReader(new java.io.FileReader(srcFile));
    String s;
    
    
    // Establish column names
    registerColumnName("Symbol", ID_TICKER);
    registerColumnName("Buy/Sell", ID_TYPE);    
    registerColumnName("Quantity", ID_AMOUNT);    
    registerColumnName("TradePrice", ID_PRICE);
    registerColumnName("CurrencyPrimary", ID_CURRENCY);    
    //format "20210128;093002"
    registerColumnName("DateTime", ID_DATE);    
    registerColumnName("Exchange", ID_MARKET);    
    // format: "20210201"
    registerColumnName("SettleDateTarget", ID_EXECUTION_DATE);
    registerColumnName("Description", ID_NOTE);    
    registerColumnName("IBCommission", ID_FEE);    
    registerColumnName("IBCommissionCurrency", ID_FEE_CURRENCY);        

    // local columns not propagated
    final int ID_MULTIPLIER = 13;
    registerColumnName("Multiplier", ID_MULTIPLIER);  
    final int ID_ASSETCLASS = 14;
    registerColumnName("AssetClass", ID_ASSETCLASS);  
    


    // Find start
    int neededLen = 0;
    boolean startFound = false;
    while((s = ifl.readLine()) != null) {
      String[] a = s.split(",");
      if (a.length >= 20) {
        //startFound = true;
        for(int i = 0; i < a.length; i++) {                  
          //remove "
          if (setColumnIdentity(i, a[i].replace("\"",""))) {
            neededLen = i+1;
          }
          // Check this is FlexQuery format (first line contains "ClientAccountID" i=0 first line
          if (i == 0 && a[i].replace("\"","").equals("ClientAccountID"))  startFound = true;                       
        }
        
        break;
      }
    }
    
    if (!startFound) throw new ImportException("IB FlexQuery Trades CSV: Nemohu najít začátek dat - je soubor ve správném formátu? Prvni string \"ClientAccountID\" chybi");
    
    // Check all columns are present...
    checkAllColumnsPresent();
    
    // Get indices
    int dirIdx = getColumnNo(ID_TYPE);
    int marketIdx = getColumnNo(ID_MARKET);
    //int marketIdx = getColumnNo(ID_MARKET);
    int dateIdx = getColumnNo(ID_DATE);
    int amountIdx = getColumnNo(ID_AMOUNT);
    int currencyIdx = getColumnNo(ID_CURRENCY);
    int noteIdx = getColumnNo(ID_NOTE);
    int executionIdx = getColumnNo(ID_EXECUTION_DATE);
    int tickerIdx = getColumnNo(ID_TICKER);
    int priceIdx = getColumnNo(ID_PRICE);
    int feeIdx = getColumnNo(ID_FEE);
    int feeCurrencyIdx = getColumnNo(ID_FEE_CURRENCY);    
    int multiplierIdx = getColumnNo(ID_MULTIPLIER);
    int typeIdx = getColumnNo(ID_ASSETCLASS);
    //int feeCZKIdx = getColumnNo(ID_FEE_CZK);
    //int feeUSDIdx = getColumnNo(ID_FEE_USD);
    //int feeEURIdx = getColumnNo(ID_FEE_EUR);
    
    // Process data rows
    while((s = ifl.readLine()) != null) {
      boolean imported = false;
      //remove all " from text
      String[] a = s.replace("\"","").split(",", -1);
      //System.out.print(Arrays.toString(a)+"\n");
      if (a.length >= neededLen) {
        CustomCSVDataRow drow = new CustomCSVDataRow();
        String dirStr = a[dirIdx];
        drow.direction = 0;
        
        // false false true :) mame vzdy jeden typ
        boolean cash = a[typeIdx].equals("CASH");
        boolean derivate = (a[typeIdx].equals("FUT") || a[typeIdx].equals("OPT") || a[typeIdx].equals("FOP") );
        boolean stock = ( a[typeIdx].equals("STK") || a[typeIdx].equals("WAR"));


        // detekce typu 
         if (!cash) {
            if (dirStr.equals("SELL")) drow.direction = derivate?Transaction.DIRECTION_DSELL:Transaction.DIRECTION_SSELL;            
            if (dirStr.equals("BUY"))  drow.direction = derivate?Transaction.DIRECTION_DBUY:Transaction.DIRECTION_SBUY;
         } else {
            if (dirStr.equals("SELL")) drow.direction = Transaction.DIRECTION_CSELL;
            if (dirStr.equals("BUY"))  drow.direction = Transaction.DIRECTION_CBUY;             
         }

            String ticker = a[tickerIdx];

          try {                        
            // We must turn negative amount in sell to positive amounts
            //a[19] should be Multiplier
            drow.amount = (int)Math.abs(Double.parseDouble(a[amountIdx]) * Double.parseDouble(a[multiplierIdx]));
            //drow.amount = (int)Math.abs(Double.parseDouble(a[amountIdx]));
            
            //drow.amount = (int)(Math.abs(Double.parseDouble(a[amountIdx]))*1000);
            drow.price = (Double.parseDouble(a[priceIdx]));
            //drow.price = parseNumber(a[priceIdx]);
            drow.currency = a[currencyIdx].toUpperCase();

            if (cash) drow.feeCurrency = "USD"; // For FX trades fees are always in USD
            else drow.feeCurrency = a[feeCurrencyIdx];
            drow.fee = -Double.parseDouble(a[feeIdx]);
            if (Math.abs(drow.fee) == 0) drow.fee = 0; // Avoid having "-0" as a fee

            

            drow.market = a[marketIdx].toUpperCase();
            //drow.market = "Unknown";

            /* Get date */
            //Date date = parseDate(a[dateIdx], null);
            String x = a[dateIdx];
            /* "20210128;093002" */                  
            drow.date = parseDate(x.substring(6,8)+"."+x.substring(4,6)+"."+x.substring(0,4)+" "+x.substring(9,11)+":"+x.substring(11,13)+":"+x.substring(13,15), null);
            //System.out.print(drow.date+"\n");
            
            // 20210201  - im adding same HHMMSS from trade but with settlement date
            if(a[executionIdx].equals("")) {  throw new SecurityException("Not settlement date - excluding not trade - probably corporate action"); }
            //; 
            String y = a[executionIdx];
            
            //System.out.print(x+"\n");
            //System.out.print(y+"\n");            
            drow.executionDate = parseDate(y.substring(6,8)+"."+y.substring(4,6)+"."+y.substring(0,4)+" "+x.substring(9,11)+":"+x.substring(11,13)+":"+x.substring(13,15), null);
            
            switch(a[typeIdx]) {
                case "WAR":
                       drow.note   = a[noteIdx]+"|Broker:IB|Type:Warrant";
                       break;
                default:
                       drow.note   = a[noteIdx]+"|Broker:IB";
                       break;                
            }
            //drow.note   = a[noteIdx];
            
            
            //boolean outOfDateRange = false;

            //if ((startDate != null) && (date.compareTo(startDate) < 0)) outOfDateRange = true;
            //if ((endDate != null) && (date.compareTo(endDate) > 0)) outOfDateRange = true;

            //if (!outOfDateRange) {
            //drow.date = date;
            drow.ticker = a[tickerIdx];
              
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
