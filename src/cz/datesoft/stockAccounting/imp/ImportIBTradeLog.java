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
            // IB have cancelled codes as "Ca" those needs to be filtered out too
            String statusCode = a[6];
            
            drow.ticker = a[2];
            drow.market = a[4];

            //note will contain close code if interesting
            // filtering O,C (Open,Close)
            switch(statusCode) {
                case "O":
                case "C":
                       drow.note   = a[3]+"|Broker:IB";
                       break;
                default:
                       drow.note   = a[3]+"|Broker:IB|Code:"+a[6];
                       break;                
            }
            
          
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
            drow.amount = (int)Math.abs(Double.parseDouble(a[10]) * Double.parseDouble(a[11]));
          
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
