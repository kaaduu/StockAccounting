/*
 * ImportIBTradeLog.java
 *
 * Created on 28. brezen 2008, 21:06
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting.imp;

import java.util.Vector;
import java.io.File;
import java.util.Date;

import cz.datesoft.stockAccounting.Transaction;

/**
 * Import data from IB "Tradelog" format
 *
 * @author Michal Kara
 */
public class ImportIBTradeLog extends ImportBase
{ 
  /** Creates a new instance of ImportIBTradeLog */
  public ImportIBTradeLog()
  {
    super();
  }
  
  public Vector<Transaction> doImport(File srcFile, Date startDate, Date endDate, Vector<String[]> notImported) throws ImportException, java.io.IOException
  {
    Vector<Transaction> res = new Vector<Transaction>();
    java.io.BufferedReader ifl = new java.io.BufferedReader(new java.io.FileReader(srcFile));
    String s;


    // Check tradelog format - basic test
    int neededLen = 0;
    boolean startFound = false;
    s = ifl.readLine();
    if (s.equals("ACCOUNT_INFORMATION"))  startFound = true;

    if (!startFound) throw new ImportException("IB FlexQuery Trades CSV: Nemohu najít začátek dat - je soubor ve správném formátu? Prvni radka ACCOUNT_INFORAMTION chybi");

    // Extract Account ID from ACT_INF line
    String accountId = "UNKNOWN";
    s = ifl.readLine();
    if (s != null && s.startsWith("ACT_INF|")) {
        String[] actFields = s.split("\\|");
        if (actFields.length > 1) {
            accountId = actFields[1].trim();
        }
    }

    // Process data rows
    while((s = ifl.readLine()) != null) {
      boolean imported = false;
      
      String[] a = s.split("\\|");
      
      if (a.length >= 15) {
        boolean cash = a[0].equals("CASH_TRD");
        boolean derivate = (a[0].equals("FUT_TRD") || a[0].equals("OPT_TRD") ||  a[0].equals("FOP_TRD"));
        boolean stock = a[0].equals("STK_TRD");
        if (derivate || stock || cash) {
           try {
             // Known trades
             DataRow drow = new DataRow();

             // Extract field values for clarity
             String tradeType = a[0];           // STK_TRD, FUT_TRD, etc.
             String transactionId = a[1];       // IB transaction identifier
             String ticker = a[2];              // Stock symbol
             String description = a[3];         // Company description
             String exchange = a[4];            // Trading venue
             String action = a[5];              // BUYTOOPEN, SELLTOCLOSE, etc.
             String statusCode = a[6];          // O, C, Ca, etc.

             // IB have cancelled codes as "Ca" those needs to be filtered out too
             drow.ticker = ticker;
             drow.market = exchange;

             // Enhanced note format with Account ID, Transaction ID, and status code
             drow.note = ticker + "|Broker:IB|AccountID:" + accountId +
                        "|TxnID:" + transactionId + "|Code:" + statusCode;
            
          
            drow.direction = 0;
            //detect types
            if(!cash) {
                if (a[5].length() >= 4) {
                    if (a[5].substring(0,4).equals("SELL")) drow.direction = derivate?Transaction.DIRECTION_DSELL:Transaction.DIRECTION_SSELL;
                }
                if (a[5].substring(0,3).equals("BUY")) drow.direction = derivate?Transaction.DIRECTION_DBUY:Transaction.DIRECTION_SBUY;
            } else {
                if (a[5].substring(0,4).equals("SELL")) drow.direction = Transaction.DIRECTION_CSELL;
                if (a[5].substring(0,3).equals("BUY")) drow.direction = Transaction.DIRECTION_CBUY;
            } //!cash             
                            
            
            
            
            String date = a[7];
            drow.date = parseDate(date.substring(6)+"."+date.substring(4,6)+"."+date.substring(0,4)+" "+a[8], null);
            drow.executionDate = drow.date;
          
            drow.currency = a[9];
            if (cash) drow.feeCurrency = "USD"; // For FX trades fees are always in USD
            else drow.feeCurrency = drow.currency;
          
            // We must turn negative amounts in sell to positive amounts
            //beware of Corporate Actions splits number of shares between 0-1 negative value - we want to exclude it
            double x = (Double)Math.abs(Double.parseDouble(a[10]));
            if ( x>0 && x<1 )   throw new ImportException("Obchod obsahuje 0 akcii - pravdepodobne corporate akce - FractShare?");          
            drow.amount = (Double)Math.abs(Double.parseDouble(a[10]) * Double.parseDouble(a[11]));
          
            drow.price = Double.parseDouble(a[12]);
            
            drow.fee = -Double.parseDouble(a[14]);
            if (Math.abs(drow.fee) == 0) drow.fee = 0; // Avoid having "-0" as a fee
          
            
            // We don't want import status codes Ca = Cancelled  
            if (! statusCode.equals("Ca")) { imported = true; addRow(res, drow); }
            
          }
          catch(Exception e) {
            e.printStackTrace();
          } // Ignore row on exception
        }
      }
          
      if ((!imported) && (notImported != null) && (a.length > 0)) {
        // Add not imported row to not imported
        notImported.add(a);
      }
    }
    
    return res;
  }
}
