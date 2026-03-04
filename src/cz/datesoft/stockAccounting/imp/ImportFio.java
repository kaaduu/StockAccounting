/*
 * ImportFio.java
 *
 * Created on 27. unor 2008, 9:46
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting.imp;

import java.util.Vector;
import java.io.File;
import java.util.Date;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import cz.datesoft.stockAccounting.Transaction;

/**
 * Import data from Fio CSV export
 *
 * @author Michal Kara
 */
public class ImportFio extends ImportBase
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
  private final int ID_VOLUME_CZK = 13;
  private final int ID_VOLUME_USD = 14;
  private final int ID_VOLUME_EUR = 15;
  
  /**
   * Our data row with a bit more fields
   */
  public class FioDataRow extends DataRow
  {
    String text;
    boolean disabled;
  }
  
  @Override
  protected void addRow(Vector<Transaction> set, DataRow row) throws Exception {
    super.addRow(set, row);
    if (row instanceof FioDataRow) {
      FioDataRow frow = (FioDataRow) row;
      if (frow.disabled) {
        // The transaction was already added, now set disabled flag
        set.get(set.size() - 1).setDisabled(true);
      }
    }
  }
  
  /** Creates a new instance of ImportFio */
  public ImportFio()
  {
    super();
  }
  
  public Vector<Transaction> doImport(File srcFile, Date startDate, Date endDate, Vector<String[]> notImported) throws ImportException, java.io.IOException
  {
    Vector<Transaction> res = new Vector<Transaction>();
    java.io.BufferedReader ifl = new java.io.BufferedReader(
        new java.io.InputStreamReader(new java.io.FileInputStream(srcFile), "Windows-1250")
    );
    String s;
    
    // Establish column names
    registerColumnName("Symbol", ID_TICKER);
    registerColumnName("Směr", ID_TYPE);
    registerColumnName("Sm?r", ID_TYPE);
    registerColumnName("Počet", ID_AMOUNT);
    registerColumnName("Po?et", ID_AMOUNT);
    registerColumnName("Cena", ID_PRICE);
    registerColumnName("Měna", ID_CURRENCY);
    registerColumnName("M?na", ID_CURRENCY);
    registerColumnName("Datum obchodu", ID_DATE);
    registerColumnName("Trh", ID_MARKET);
    registerColumnName("Množství", ID_AMOUNT);
    registerColumnName("Mno?stv?", ID_AMOUNT);
    registerColumnName("Datum vypořádání", ID_EXECUTION_DATE);
    registerColumnName("Datum vypo??d?n?", ID_EXECUTION_DATE);
    registerColumnName("Text FIO", ID_NOTE);
    registerColumnName("Poplatky v CZK", ID_FEE_CZK);
    registerColumnName("Poplatky v USD", ID_FEE_USD);
    registerColumnName("Poplatky v EUR", ID_FEE_EUR);
    registerColumnName("Objem v CZK", ID_VOLUME_CZK);
    registerColumnName("Objem v USD", ID_VOLUME_USD);
    registerColumnName("Objem v EUR", ID_VOLUME_EUR);

    // Read first line to extract account ID
    String firstLine = ifl.readLine();
    String accountId = null;
    if (firstLine != null) {
      java.util.regex.Pattern p = java.util.regex.Pattern.compile("portfolio\"([^\"]+)\"");
      java.util.regex.Matcher m = p.matcher(firstLine);
      if (m.find()) {
        String portfolioText = m.group(1);
        String[] parts = portfolioText.split(":\\s*");
        if (parts.length >= 2) {
          accountId = parts[parts.length - 1].trim();
        }
      }
    }

    // Find start
    int neededLen = 0;
    boolean startFound = false;
    while((s = ifl.readLine()) != null) {
      String[] a = s.split(";");
      if (a.length >= 6) {
        startFound = true;
        for(int i = 0; i < a.length; i++) {
          if (setColumnIdentity(i, a[i])) {
            neededLen = i+1;
          }
        }
        
        break;
      }
    }
    
    if (!startFound) throw new ImportException("FIO: Nemohu najít začátek dat - je soubor ve správném formátu?");
    
    // Check all columns are present...
    checkAllColumnsPresent();
    
    // Get indices
    int dirIdx = getColumnNo(ID_TYPE);
    int marketIdx = getColumnNo(ID_MARKET);
    int dateIdx = getColumnNo(ID_DATE);
    int amountIdx = getColumnNo(ID_AMOUNT);
    int currencyIdx = getColumnNo(ID_CURRENCY);
    int textIdx = getColumnNo(ID_NOTE);
    int executionIdx = getColumnNo(ID_EXECUTION_DATE);
    int tickerIdx = getColumnNo(ID_TICKER);
    int priceIdx = getColumnNo(ID_PRICE);
    int feeCZKIdx = getColumnNo(ID_FEE_CZK);
    int feeUSDIdx = getColumnNo(ID_FEE_USD);
    int feeEURIdx = getColumnNo(ID_FEE_EUR);
    int volumeCZKIdx = getColumnNo(ID_VOLUME_CZK);
    int volumeUSDIdx = getColumnNo(ID_VOLUME_USD);
    int volumeEURIdx = getColumnNo(ID_VOLUME_EUR);
    
    // Process data rows
    while((s = ifl.readLine()) != null) {
      boolean imported = false;
      
      String[] a = s.split(";", -1);
      
      if (a.length >= neededLen) {
        FioDataRow drow = new FioDataRow();

        drow.broker = "FIO";
        drow.accountId = accountId;

        String dirStr = a[dirIdx];

        drow.direction = 0;

        if (equalsIgoreCaseAndEncoding(dirStr, "Nákup", "N?kup")) drow.direction = Transaction.DIRECTION_SBUY;
        else if (dirStr.equalsIgnoreCase("Prodej")) drow.direction = Transaction.DIRECTION_SSELL;

        if (drow.direction == 0) {
          if (equalsIgoreCaseAndEncoding(a[marketIdx], "Výnos CP", "V?nos CP")) {
            /** Divi **/

            drow.date = parseDate(a[dateIdx], null);

            boolean outOfDateRange = false;

            if ((startDate != null) && (drow.date.compareTo(startDate) < 0)) outOfDateRange = true;
            if ((endDate != null) && (drow.date.compareTo(endDate) > 0)) outOfDateRange = true;

            if (!outOfDateRange) {
              try {
                // Determine price & currency
                drow.price = parseNumber(a[amountIdx]);
                drow.currency = a[currencyIdx].toUpperCase();

                // Require that currency by the dividend has exactly three chars. Fio reports share offering
                // rights the same way as dividends, so this is used as a distinguishing sign.
                if (drow.currency.length() == 3) {
                  drow.amount = 1;

                  // Determine direction (i.e. type)
                  drow.text = a[textIdx];
                  if (drow.text.equalsIgnoreCase("Dividenda - USA")) drow.direction = Transaction.DIRECTION_DIVI_BRUTTO;
                  else if (equalsIgoreCaseAndEncoding(drow.text, "Daň z divid. zaplacená v USA", "Da? z divid. zaplacen? v USA")) drow.direction = Transaction.DIRECTION_DIVI_TAX;
                  else {
                    if (drow.price < 0) drow.direction = Transaction.DIRECTION_DIVI_TAX;
                    else drow.direction = Transaction.DIRECTION_DIVI_UNKNOWN;
                  }

                  // Get other parameters
                  drow.executionDate = parseDate(a[executionIdx],drow.date);
                  drow.market = a[marketIdx].toUpperCase();
                  drow.ticker = a[tickerIdx];

                  addRow(res, drow);

                  imported = true;
                }
              }
              catch(Exception e) {
                e.printStackTrace();
              } // Ignore row on exception
            }  
          }
          else if (equalsIgoreCaseAndEncoding(a[marketIdx], "Pokladna", "Pokladna")) {
            try {
              drow.date = parseDate(a[dateIdx], null);

              boolean outOfDateRange = false;
              if ((startDate != null) && (drow.date.compareTo(startDate) < 0)) outOfDateRange = true;
              if ((endDate != null) && (drow.date.compareTo(endDate) > 0)) outOfDateRange = true;

              if (!outOfDateRange) {
                double volumeCZK = parseNumber(a[volumeCZKIdx]);
                double volumeUSD = parseNumber(a[volumeUSDIdx]);
                double volumeEUR = parseNumber(a[volumeEURIdx]);

                if (volumeCZK != 0) {
                  drow.ticker = "CZK";
                  drow.amount = Math.abs(volumeCZK);
                  drow.price = 1.0;
                  drow.currency = "CZK";
                } else if (volumeUSD != 0) {
                  drow.ticker = "USD";
                  drow.amount = Math.abs(volumeUSD);
                  drow.price = 1.0;
                  drow.currency = "USD";
                } else if (volumeEUR != 0) {
                  drow.ticker = "EUR";
                  drow.amount = Math.abs(volumeEUR);
                  drow.price = 1.0;
                  drow.currency = "EUR";
                }

                double volume = volumeCZK + volumeUSD + volumeEUR;
                drow.direction = volume > 0 ? Transaction.DIRECTION_CBUY : Transaction.DIRECTION_CSELL;

                drow.disabled = true;

                drow.note = a[textIdx];
                drow.executionDate = parseDate(a[executionIdx], drow.date);
                drow.market = "";

                addRow(res, drow);

                imported = true;
              }
            }
            catch(Exception e) {
              e.printStackTrace();
            }
          }
        }
        else {
          /** Buy, sell or transformation **/
          String ticker = a[tickerIdx];

          try {
            drow.amount = (int)parseNumber(a[amountIdx]);
            drow.price = parseNumber(a[priceIdx]);
            drow.currency = a[currencyIdx].toUpperCase();
            drow.market = a[marketIdx].toUpperCase();

            /* Get date */
            Date date = parseDate(a[dateIdx], null);

            boolean outOfDateRange = false;

            if ((startDate != null) && (date.compareTo(startDate) < 0)) outOfDateRange = true;
            if ((endDate != null) && (date.compareTo(endDate) > 0)) outOfDateRange = true;

            if (!outOfDateRange) {
              drow.date = date;
              drow.ticker = ticker;

              // Get fee
              drow.fee = parseNumber(a[feeEURIdx]);
              if (drow.fee > 0) {
                drow.feeCurrency = "EUR";
              }
              else {
                drow.fee = parseNumber(a[feeUSDIdx]);
                if (drow.fee > 0) {
                  drow.feeCurrency = "USD";                
                }
                else {
                  drow.fee = parseNumber(a[feeCZKIdx]);
                  if (drow.fee > 0) {
                    drow.feeCurrency = "CZK";
                  }                
                }
              }

              // Check for transformation
              if (drow.market.equalsIgnoreCase("Transformace")) {
                if (drow.direction == Transaction.DIRECTION_SBUY) drow.direction = Transaction.DIRECTION_TRANS_ADD;
                else if (drow.direction == Transaction.DIRECTION_SSELL) drow.direction = Transaction.DIRECTION_TRANS_SUB;
                drow.market = "";
              }

              drow.executionDate = parseDate(a[executionIdx],date);

              addRow(res, drow);

              imported = true;
            }
          }
          catch(Exception e) {
            e.printStackTrace();
          } // Ignore row on exceptio
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
