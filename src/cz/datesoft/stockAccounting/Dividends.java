/*
 * Dividends.java
 *
 * Created on 3. leden 2007, 15:14
 *
 * Class to hold list of dividends and taxes paid
 */

package cz.datesoft.stockAccounting;

import java.util.Vector;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;

/**
 * Holds list of dividends
 *
 * @author Michal Kára
 */
public class Dividends
{
  /**
   * Class containing information about dividend. Used as a structure.
   */
  public class Dividend
  {
    /** When dividend/tax was paid */
    public Date date;
    
    /** Ticker this dividend is for */
    public String ticker;
    
    /** Brutto dividend */
    public double dividend;
    
    /** Currency dividend is in (may be null when this is just a tax record) */
    public String dividendCurrency;
    
    /** Tax paid */
    public double tax;
    
    /** Tax currency (may be null when this is just a dividend record */
    public String taxCurrency;
    
    public Dividend(Date date, String ticker, double dividend, String dividendCurrency, double tax, String taxCurrency)
    {
      this.date = date;
      this.ticker = ticker;
      this.dividend = dividend;
      this.dividendCurrency = dividendCurrency;
      this.tax = tax;
      this.taxCurrency = taxCurrency;
    }
  }
  
  /**
   * Stock trading exception - invalid dividend specs
   */
  public class DividendException extends Exception
  {
    public DividendException(String message) { super(message); }
  }  
  
  /**
   * List of dividends and / or taxes
   */
  private Vector<Dividend> divis;
  
  /** Creates a new instance of Dividends */
  public Dividends()
  {
    divis = new Vector<Dividend>();
  }
  
  /**
   * Applies transaction
   */
  public void applyTransaction(Transaction tx) throws DividendException
  {
    if (tx == null) return;
    if (tx.isDisabled()) return;
    if (tx.direction == tx.DIRECTION_DIVI_UNKNOWN) throw new DividendException("Zaznam o dividende z "+tx.date+" u tickeru "+tx.ticker+" je nastaven na typ 'D-Neznámá'. Aby mohly být zúčtovány dividendy, musíte jej ručně nastavit na správný typ!");

    switch(tx.direction) {
      case Transaction.DIRECTION_SBUY:
      case Transaction.DIRECTION_SSELL:
      case Transaction.DIRECTION_TRANS_ADD:
      case Transaction.DIRECTION_TRANS_SUB:
      case Transaction.DIRECTION_DBUY:
      case Transaction.DIRECTION_DSELL:
      case Transaction.DIRECTION_CBUY:
      case Transaction.DIRECTION_CSELL:
      case Transaction.DIRECTION_INT_BRUTTO:
      case Transaction.DIRECTION_INT_TAX:
      case Transaction.DIRECTION_INT_PAID:
      case Transaction.DIRECTION_INT_FEE:
        // Ignore these
        return;
    }
    
    // Get time at start of the day
    GregorianCalendar cal = new GregorianCalendar();
    // Some imports may not have executionDate set (older data / special cash rows).
    // Fall back to trade date in that case.
    cal.setTime(tx.executionDate != null ? tx.executionDate : tx.date);
    cal.set(cal.HOUR_OF_DAY,0);
    cal.set(cal.MINUTE,0);
    cal.set(cal.SECOND,0);
    cal.set(cal.MILLISECOND,0);
    
    Date roundedDate = cal.getTime();
    
    if (tx.direction == tx.DIRECTION_DIVI_NETTO15) {
      // Create & add new record
      divis.add(new Dividend(roundedDate,tx.ticker,tx.price*1.15,tx.priceCurrency,tx.price*1.15-tx.price,tx.priceCurrency));
    }
    else if (tx.direction == tx.DIRECTION_DIVI_BRUTTO) {
      // Try to find dividend record for same ticker & date with no dividend info
      for(Iterator<Dividend> i = divis.iterator();i.hasNext();) {
        Dividend d = i.next();
        
        if ((d.date.equals(roundedDate)) && (d.dividendCurrency == null) && (d.ticker.equalsIgnoreCase(tx.ticker))) {
          // Found it - fill in price
          d.dividend = tx.price;
          d.dividendCurrency = tx.priceCurrency;
          return;
        }
      }
      
      // Not found - add with no tax info
      divis.add(new Dividend(roundedDate,tx.ticker,tx.price,tx.priceCurrency,0,null));
    }
    else if (tx.direction == tx.DIRECTION_DIVI_TAX) {
      if (tx.price > 0) throw new DividendException("Daň z dividendy ze "+tx.date+" u tickeru "+tx.ticker+": Daň musí mít zápornou hodnotu (sloupec 'Cena')!");
      
      // Try to find dividend record for same ticker & date with no tax info
      for(Iterator<Dividend> i = divis.iterator();i.hasNext();) {
        Dividend d = i.next();
        
        if ((d.date.equals(roundedDate)) && (d.taxCurrency == null) && (d.ticker.equalsIgnoreCase(tx.ticker))) {
          // Found it - fill in tax
          d.tax = -tx.price;
          d.taxCurrency = tx.priceCurrency;
          return;
        }
      }
      
      // Not found - add with no tax info
      divis.add(new Dividend(roundedDate,tx.ticker,0,null,-tx.price,tx.priceCurrency));
    }
    else {
      throw new DividendException("Neznamy typ zaznamu: " + tx.direction);
    }
  }
  
  /**
   * Get dividends
   */
  public Dividend[] getDividends()
  {
    Dividend[] res = new Dividend[divis.size()];
    divis.toArray(res);
    
    return res;
  }
}
