/*
 * ImportBjHTML.java
 *
 * Created on 17. únor 2008, 17:02
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting.imp;

import java.util.Vector;
import java.io.File;
import java.util.Date;

import cz.datesoft.stockAccounting.Transaction;
import java.nio.charset.Charset;

/**
 * Implements import from BrokerJet HTML transaction
 *
 * @author Michal Kara
 */
public class ImportBjHTML extends ImportBase
{
  /**
   * Identities of columns for this imported
   */
  private final int ID_DATE = 1;
  private final int ID_TIME = 2;
  private final int ID_DIRECTION = 3;
  private final int ID_AMOUNT = 4;
  private final int ID_CURRENCY = 5;
  private final int ID_PRICE = 6;
  private final int ID_FEE = 7;
  private final int ID_ISIN = 8;
  private final int ID_SYMBOL = 9;
  
  /**
   * Our data row with a bit more fields
   */
  public class BJDataRow extends DataRow
  {
    public String dateS;
    public String timeS;
    public String isin;
  }
  
  /** Creates a new instance of ImportBjHTML */
  public ImportBjHTML()
  {
    super();
  }
  
  public Vector<Transaction> doImport(File srcFile, Date startDate, Date endDate, Vector<String[]> notImported) throws ImportException, java.io.IOException
  {
    int mode = 0;
    java.io.BufferedReader ifl = new java.io.BufferedReader(new java.io.FileReader(srcFile));
    String s;
    int columnNo = 0;
    Vector<Transaction> res = new Vector<Transaction>();
    String[] irow = null;
    int columnCount = 0;
    
    // Establish column names
    registerColumnName("Datum", ID_DATE);
    registerColumnName("Čas", ID_TIME);
    registerColumnName("?as", ID_TIME);
    registerColumnName("Směr obchodu", ID_DIRECTION);
    registerColumnName("Sm?r obchodu", ID_DIRECTION);
    registerColumnName("Množství", ID_AMOUNT);
    registerColumnName("Mno?stv?", ID_AMOUNT);
    registerColumnName("Měna", ID_CURRENCY);
    registerColumnName("M?na", ID_CURRENCY);
    registerColumnName("Obchod&nbsp;cena<br>(vyjma&nbsp;poplatky)<br>Obchodování&nbsp;CCY", ID_PRICE);
    registerColumnName("Obchod&nbsp;cena<br>(vyjma&nbsp;poplatky)<br>Obchodov?n?&nbsp;CCY", ID_PRICE);
    registerColumnName("Poplatek", ID_FEE);
    registerColumnName("ISIN", ID_ISIN);
    registerColumnName("Symbol", ID_SYMBOL);    

    while((s = ifl.readLine()) != null) {
      if ((s.indexOf("<table cellspacing=\"1\" class=\"box\">") >= 0) && (mode == 0)) {
        // Start of the data table
        mode = 1;
        columnNo = 0;
      }
      
      if (mode == 1) {
        // Check for column header
        int i = s.indexOf("<th>");
        
        if (i >= 0) {
          int i2 = s.indexOf("</th>");
          
          String colName = s.substring(i+4, i2);
          
          setColumnIdentity(columnNo, colName);
          
          columnNo++;
        }
        else {
          if (s.indexOf("<tr class=") >= 0) {
            // First data row - check whether all columns are present
            checkAllColumnsPresent();
            
            // Create row array
            columnCount = columnNo+1;
            irow = new String[columnCount];
            
            mode = 2;
            
            columnNo = 0;
          }
        }
      }
      else if (mode == 2) {
        // Data row
        if (s.indexOf("</tr>") >= 0) {
          /* End of data row - store it */

          try {
            // Store data to data row
            BJDataRow drow = new BJDataRow();
            for(int i = 0; i < columnCount; i++) {
              // Process column contents based on column identity
              s = irow[i];

              switch(getColumnIdentity(i))
              {
                case ID_DATE:
                  drow.dateS = s;
                  break;
                case ID_TIME:
                  drow.timeS = s;
                  break;
                case ID_DIRECTION:
                  if (s.equalsIgnoreCase("N�kup") || s.equalsIgnoreCase("Nákup")) drow.direction = Transaction.DIRECTION_SBUY;
                  else if (s.equalsIgnoreCase("Prodej")) drow.direction = Transaction.DIRECTION_SSELL;
                  else if (s.equalsIgnoreCase("Bezplatn� dod�n�") || s.equalsIgnoreCase("Bezplatné dodání")) drow.direction = Transaction.DIRECTION_SSELL;
                  else throw new ImportException("Neznam� obsah sloupce 'Sm�r': '"+s+"' ");
                  break;
                case ID_AMOUNT:
                  drow.amount = (int)parseNumber(s);
                  break;
                case ID_CURRENCY:
                  drow.currency = s;
                  break;
                case ID_PRICE:
                  drow.price = parseNumber(s);
                  break;
                case ID_FEE:
                  drow.fee = parseNumber(s);
                  break;
                case ID_ISIN:
                  drow.isin = s;
                  break;
                case ID_SYMBOL:
                  drow.ticker = s;
                  break;
              }
            }
          
            // Calculate fields

            // TODO - Filter by dates - TODO
            
            drow.date = parseDate(drow.dateS + " " + drow.timeS, new Date());
            drow.executionDate = drow.date;
            drow.feeCurrency = drow.currency;
            drow.market = "";
            if (drow.ticker.length() == 0) drow.ticker = drow.isin; // Use ISIN when ticker (Symbol) not specified
          
            addRow(res, drow);
          }
          catch(Exception e) {
            // Row was not imported            
            e.printStackTrace();
            
            notImported.add(irow);
          }
          
          // Create a new row
          irow = new String[columnCount];
          columnNo = 0;
        }
        else {
          int i = s.indexOf("<td nowrap");
          
          if (i >= 0) {
            // Find end of start
            int i1 = s.indexOf(">", i);
            int i2 = s.indexOf("</td>");
                
            // Store contents
            irow[columnNo] = s.substring(i1+1, i2);
            
            // Increase column number
            columnNo++;
          }
        }
      }
    }    
    
    return res;
  }
  
}

