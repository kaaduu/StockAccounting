package cz.datesoft.stockAccounting;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Vector;

/**
 * Holds list of interests and related costs/taxes.
 * Mirrors Dividends logic but for Transaction.DIRECTION_INT_*.
 */
public class Interests {
  public class Interest {
    /** When interest/tax was paid */
    public Date date;
    /** Ticker this interest is for */
    public String ticker;
    /** Brutto interest */
    public double interest;
    /** Currency interest is in (may be null when this is just a tax/fee record) */
    public String interestCurrency;
    /** Tax amount (positive) */
    public double tax;
    /** Tax currency */
    public String taxCurrency;
    /** Paid interest (debit) amount (positive) */
    public double paid;
    /** Paid currency */
    public String paidCurrency;
    /** Fees amount (positive) */
    public double fee;
    /** Fee currency */
    public String feeCurrency;

    public Interest(Date date, String ticker, double interest, String interestCurrency, double tax, String taxCurrency,
        double paid, String paidCurrency, double fee, String feeCurrency) {
      this.date = date;
      this.ticker = ticker;
      this.interest = interest;
      this.interestCurrency = interestCurrency;
      this.tax = tax;
      this.taxCurrency = taxCurrency;
      this.paid = paid;
      this.paidCurrency = paidCurrency;
      this.fee = fee;
      this.feeCurrency = feeCurrency;
    }
  }

  public class InterestException extends Exception {
    public InterestException(String message) {
      super(message);
    }
  }

  private Vector<Interest> interests;

  public Interests() {
    interests = new Vector<Interest>();
  }

  public void applyTransaction(Transaction tx) throws InterestException {
    if (tx == null)
      return;
    if (tx.isDisabled())
      return;

    // Ignore everything except interest directions.
    switch (tx.direction) {
      case Transaction.DIRECTION_INT_BRUTTO:
      case Transaction.DIRECTION_INT_TAX:
      case Transaction.DIRECTION_INT_PAID:
      case Transaction.DIRECTION_INT_FEE:
        break;
      default:
        return;
    }

    // Get time at start of the day
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(tx.executionDate != null ? tx.executionDate : tx.date);
    cal.set(cal.HOUR_OF_DAY, 0);
    cal.set(cal.MINUTE, 0);
    cal.set(cal.SECOND, 0);
    cal.set(cal.MILLISECOND, 0);
    Date roundedDate = cal.getTime();

    if (tx.direction == Transaction.DIRECTION_INT_BRUTTO) {
      // Try to find record for same ticker & date with no interest info
      for (Iterator<Interest> it = interests.iterator(); it.hasNext();) {
        Interest r = it.next();
        if (r.date.equals(roundedDate) && r.interestCurrency == null && r.ticker.equalsIgnoreCase(tx.ticker)) {
          r.interest = tx.price;
          r.interestCurrency = tx.priceCurrency;
          return;
        }
      }
      interests.add(new Interest(roundedDate, tx.ticker, tx.price, tx.priceCurrency, 0, null, 0, null, 0, null));
      return;
    }

    if (tx.direction == Transaction.DIRECTION_INT_TAX) {
      if (tx.price > 0)
        throw new InterestException(
            "Daň z úroku ze " + tx.date + " u tickeru " + tx.ticker + ": Daň musí mít zápornou hodnotu (sloupec 'Cena')!");
      for (Iterator<Interest> it = interests.iterator(); it.hasNext();) {
        Interest r = it.next();
        if (r.date.equals(roundedDate) && r.taxCurrency == null && r.ticker.equalsIgnoreCase(tx.ticker)) {
          r.tax = -tx.price;
          r.taxCurrency = tx.priceCurrency;
          return;
        }
      }
      interests.add(new Interest(roundedDate, tx.ticker, 0, null, -tx.price, tx.priceCurrency, 0, null, 0, null));
      return;
    }

    if (tx.direction == Transaction.DIRECTION_INT_PAID) {
      if (tx.price > 0)
        throw new InterestException(
            "Zaplacený úrok ze " + tx.date + " u tickeru " + tx.ticker + ": musí mít zápornou hodnotu (sloupec 'Cena')!");
      for (Iterator<Interest> it = interests.iterator(); it.hasNext();) {
        Interest r = it.next();
        if (r.date.equals(roundedDate) && r.paidCurrency == null && r.ticker.equalsIgnoreCase(tx.ticker)) {
          r.paid = -tx.price;
          r.paidCurrency = tx.priceCurrency;
          return;
        }
      }
      interests.add(new Interest(roundedDate, tx.ticker, 0, null, 0, null, -tx.price, tx.priceCurrency, 0, null));
      return;
    }

    if (tx.direction == Transaction.DIRECTION_INT_FEE) {
      if (tx.price > 0)
        throw new InterestException(
            "Poplatek k úroku ze " + tx.date + " u tickeru " + tx.ticker + ": musí mít zápornou hodnotu (sloupec 'Cena')!");
      for (Iterator<Interest> it = interests.iterator(); it.hasNext();) {
        Interest r = it.next();
        if (r.date.equals(roundedDate) && r.feeCurrency == null && r.ticker.equalsIgnoreCase(tx.ticker)) {
          r.fee = -tx.price;
          r.feeCurrency = tx.priceCurrency;
          return;
        }
      }
      interests.add(new Interest(roundedDate, tx.ticker, 0, null, 0, null, 0, null, -tx.price, tx.priceCurrency));
      return;
    }

    throw new InterestException("Neznámý typ záznamu: " + tx.direction);
  }

  public Interest[] getInterests() {
    Interest[] res = new Interest[interests.size()];
    interests.toArray(res);
    return res;
  }
}
