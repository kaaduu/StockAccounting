/*
 * Stocks.java
 *
 * Created on 10. listopad 2006, 22:02
 *
 * Class holding list of stocks we bought and when and subsequent classes. These classes incorporate
 * complete logic handling stock buys, sells and transformations (rename, split, reverse split).
 *
 * On input, you have stock transactions (TransactionSet.Transaction, as entered in other parts of this programs).
 * On output side, you get completed trades (matched buys / sells) and info about current state of the account.
 */

package cz.datesoft.stockAccounting;

import java.util.Date;
import java.util.Vector;
import java.util.Iterator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Calendar;
import java.text.SimpleDateFormat;

/**
 * Class holding list of stocks we bought and when
 *
 * @author lemming
 */
public class Stocks {
  // Floating point normalization for holdings
  private static final double SNAP_TO_INTEGER_EPS = 1e-6;
  // Snap very small residual holdings to zero (floating noise).
  // Keep this well below real fractional-share precision.
  private static final double SNAP_TO_ZERO_EPS = 1e-8;

  // Corporate actions and fractional shares are typically reported to 4 decimals.
  private static final int AMOUNT_SCALE_DEFAULT = 4;

  private static double snapAmount(double v) {
    if (Math.abs(v) < SNAP_TO_ZERO_EPS) {
      return 0.0;
    }
    double r = Math.rint(v); // nearest integer (ties to even)
    if (Math.abs(v - r) < SNAP_TO_INTEGER_EPS) {
      return r;
    }
    return v;
  }

  private static double roundAmount(double v, int scale) {
    return java.math.BigDecimal.valueOf(v)
        .setScale(scale, java.math.RoundingMode.HALF_UP)
        .doubleValue();
  }

  private static void quantizeFragmentsToScale(StockInfo info, int scale) {
    if (info == null || info.fragments == null || info.fragments.isEmpty()) return;

    for (StockFragment f : info.fragments) {
      double oldAmount = f.amount;
      double newAmount = snapAmount(roundAmount(oldAmount, scale));

      if (newAmount != 0.0 && oldAmount != 0.0) {
        // Keep the original amount mapping stable: original = current * oamRatio
        f.oamRatio *= oldAmount / newAmount;
      }

      f.amount = newAmount;
    }
  }

  private static boolean isIbkrLfTransaction(Transaction tx) {
    if (tx == null) return false;
    String note = tx.getNote();
    if (note == null) return false;
    // IBKR Flex/TradeLog uses Code:LF for cash-in-lieu fractional disposal.
    return note.contains("Code:LF") || note.contains("|Code:LF") || note.contains("Code=LF");
  }

  // Cash-in-lieu notes are informational; final post-action amount is given by TRANS_ADD.

  private static void reconcileHoldingsToTarget(StockInfo info, double targetAmount) {
    if (info == null) return;
    // Normalize to IBKR-like precision when reconciling transformation totals.
    double target = roundAmount(targetAmount, AMOUNT_SCALE_DEFAULT);
    double actual = roundAmount(info.getAmountD(), AMOUNT_SCALE_DEFAULT);
    double delta = roundAmount(target - actual, AMOUNT_SCALE_DEFAULT);

    if (info.fragments == null || info.fragments.isEmpty()) {
      return;
    }

    if (Math.abs(delta) < SNAP_TO_ZERO_EPS) {
      return;
    }

    // Adjust fragment amounts to match declared post-action amount.
    // Keep FIFO distortion minimal: apply correction to the last (newest) fragment.
    StockFragment last = info.fragments.lastElement();
    double a = last.amount;
    double newAmount = snapAmount(roundAmount(a + delta, AMOUNT_SCALE_DEFAULT));
    if (newAmount != 0.0 && a != 0.0) {
      last.oamRatio *= a / newAmount;
    }
    last.amount = newAmount;

    // Final snap to stabilize near-zero / near-integer
    for (StockFragment f : info.fragments) {
      f.amount = snapAmount(roundAmount(f.amount, AMOUNT_SCALE_DEFAULT));
    }
  }

  // Note: we intentionally do not derive split ratio from note.
  // For correctness, StockAccounting treats TRANS_ADD amount as source of truth.
  /**
   * Security type
   */
  public static enum SecType {
    STOCK, DERIVATE, CASH
  };

  public static Date TAX_FREE_DURATION_BOUNDARY = new GregorianCalendar(2014, 0, 1).getTime();

  /**
   * Stock split or reverse split.
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockSplit">
  public class StockSplit {
    /**
     * Date when split happend
     */
    private Date date;

    /**
     * Ratio. Splits have ratio >1 , reverse splits <1.
     */
    private double ratio;

    /**
     * Constructor
     */
    public StockSplit(Date date, double ratio) {
      this.date = date;
      this.ratio = ratio;
    }

    /**
     * Date getter
     */
    public Date getDate() {
      return date;
    }

    /**
     * Ratio getter
     */
    public double getRatio() {
      return ratio;
    }

    /**
     * Return type ("split or reverse-split")
     */
    public String getType() {
      return (ratio < 1) ? "reverse split" : "split";
    }

    /**
     * Return string ratio
     */
    public String getSRatio() {
      if (ratio > 1)
        return ratio + ":1";
      else
        return "1:" + Math.round(1 / ratio);
    }
  }
  // </editor-fold>

  /**
   * Stock rename
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockSplit">
  public class StockRename {
    /**
     * Old (original) name
     */
    private String oldName;

    /**
     * New name
     */
    private String newName;

    /**
     * Date when rename was applied
     */
    private Date date;

    /**
     * Constructor
     */
    public StockRename(Date date, String oldName, String newName) {
      this.date = date;
      this.oldName = oldName;
      this.newName = newName;
    }

    /**
     * Old name getter
     */
    public String getOldName() {
      return oldName;
    }

    /**
     * New name getter
     */
    public String getNewName() {
      return newName;
    }

    /**
     * Date getter
     */
    public Date getDate() {
      return date;
    }
  }
  // </editor-fold>

  /**
   * Stock fragment - bought at one price at one point at a time
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockFragment">
  public class StockFragment {
    /**
     * Original ticker as the stock was bought
     */
    private String originalTicker;

    /**
     * Date / time opened (transaction cleared)
     */
    private Date opened;

    /**
     * Date when trade was executed
     */
    private Date tradeDate;

    /**
     * Amount we hold (is double since it can be fractional due to reverse splits).
     * It can be also negative.
     */
    private double amount;

    /**
     * Original amount
     */
    private double originalAmount;

    /**
     * Price opened at
     */
    private double price;

    /**
     * Fee for the transaction
     */
    private double fee;

    /**
     * Currency for the price
     */
    private String priceCurrency;

    /**
     * Currency for the fee
     */
    private String feeCurrency;

    /**
     * Market where opening trade was executed
     */
    private String market;

    /**
     * Market where opening trade was executed
     */
    private String note;

    /**
     * Ratio to original amount (i.e. to convert from current amount to original
     * amount)
     */
    double oamRatio;

    /**
     * Vector of stock splits & reverse splits
     */
    private Vector<StockSplit> splits;

    /**
     * Stock renames
     */
    private Vector<StockRename> renames;

    /**
     * Constructor
     */
    public StockFragment(String ticker, Date tradeDate, Date executionDate, double amount, double price, double fee,
        String priceCurrency, String feeCurrency, String market) {
      this.originalTicker = ticker;
      this.tradeDate = tradeDate;
      this.opened = executionDate;
      this.amount = amount;
      this.originalAmount = amount;
      this.price = price;
      this.fee = fee;
      this.market = market;
      this.oamRatio = 1;
      this.priceCurrency = priceCurrency;
      this.feeCurrency = feeCurrency;
      this.note = note;

      splits = new Vector<StockSplit>();
      renames = new Vector<StockRename>();
    }

    /**
     * Apply ticker change
     */
    public void applyStockRename(StockRename rename) {
      // Just record it - we do not track current ticker
      renames.add(rename);
    }

    /**
     * Apply split / reverse split
     */
    public void applyStockSplit(StockSplit split) {
      // Change amount
      amount = snapAmount(amount * split.getRatio());
      oamRatio /= split.getRatio();

      // Check if we can round amount - to avoid fractioning errors
      double a = Math.round(amount);
      if (Math.abs(a - amount) < 0.001)
        amount = snapAmount(a);

      // Record split
      splits.add(split);
    }

    /**
     * Original amount getter
     */
    public String getOriginalTicker() {
      return originalTicker;
    }

    /**
     * Opened getter
     */
    public Date getOpened() {
      return opened;
    }

    /**
     * Amount getter
     */
    public double getAmount() {
      return amount;
    }

    /**
     * Price getter
     */
    public double getPrice() {
      return price;
    }

    /**
     * Fee getter
     */
    public double getFee() {
      return fee;
    }

    /**
     * Price currency getter
     */
    public String getPriceCurrency() {
      return priceCurrency;
    }

    /**
     * Fee currency getter
     */
    public String getFeeCurrency() {
      return feeCurrency;
    }

    /**
     * Get original amount ratio
     */
    public double getOAMRatio() {
      return oamRatio;
    }

    /**
     * Get splits
     */
    public StockSplit[] getSplits() {
      StockSplit[] res = new StockSplit[splits.size()];
      splits.toArray(res);

      return res;
    }

    /**
     * Get renames
     */
    public StockRename[] getRenames() {
      StockRename[] res = new StockRename[renames.size()];
      renames.toArray(res);

      return res;
    }

    /**
     * Decrease amount
     *
     * @return Fee for this part of amount
     */
    public double decreaseAmount(double damount) throws TradingException {
      if (Math.signum(damount) == Math.signum(amount))
        throw new TradingException("Interní chyba: Pokus odebrat z fragmentu pozici se stejným znaménkem!");
      if (Math.abs(damount) > Math.abs(amount))
        throw new TradingException("Interní chyba: Pokus odebrat z fragmentu více akcií než obsahuje!");

      if (Math.abs(amount + damount) < 0.0001) {
        // Decreased all
        double res = fee;
        amount = 0;
        fee = 0;
        return res;
      } else {
        double res = ((double) Math.round(fee * Math.abs(damount / amount) * 100)) / 100;

        amount = snapAmount(amount + damount);
        fee -= res;

        if (fee < 0)
          fee = 0; // To be sure

        return res;
      }
    }

    /**
     * Return whether this fragment is empty (i.e., holds no stock)
     */
    public boolean isEmpty() {
      return (Math.abs(amount) < 0.0001);
    }
  }
  // </editor-fold>

  /**
   * Stock trading exception - invalid trade
   */
  public static class TradingException extends Exception {
    public TradingException(String message) {
      super(message);
    }
  }

  /**
   * Structure holding one half of the trade
   */
  // <editor-fold defaultstate="collapsed" desc="Class: HalfTrade">
  public static class HalfTrade {
    /**
     * Date
     */
    public Date date;

    /**
     * Trade date/time (when the trade was executed).
     *
     * Note: For some generated half-trades (e.g. synthetic autoclose), this may be
     * equal to {@link #date}.
     */
    public Date tradeDate;

    /**
     * Execution/settlement date/time.
     *
     * Historically, {@link #date} stored this value; we keep it for
     * backward-compatibility.
     */
    public Date executionDate;

    /**
     * Ticker
     */
    public String ticker;

    /**
     * Amount
     */
    double amount;

    /**
     * Price
     */
    double price;

    /**
     * Fee
     */
    double fee;

    /**
     * Price currency
     */
    String priceCurrency;

    /**
     * Fee currency
     */
    String feeCurrency;

    public HalfTrade(Date date, String ticker, double amount, double price, double fee, String priceCurrency,
        String feeCurrency) {
      this.date = date;
      this.tradeDate = date;
      this.executionDate = date;
      this.ticker = ticker;
      this.amount = amount;
      this.price = price;
      this.fee = fee;
      this.priceCurrency = priceCurrency;
      this.feeCurrency = feeCurrency;
    }

    public HalfTrade(Date executionDate, Date tradeDate, String ticker, double amount, double price, double fee,
        String priceCurrency, String feeCurrency) {
      this.date = executionDate;
      this.executionDate = executionDate;
      this.tradeDate = tradeDate != null ? tradeDate : executionDate;
      this.ticker = ticker;
      this.amount = amount;
      this.price = price;
      this.fee = fee;
      this.priceCurrency = priceCurrency;
      this.feeCurrency = feeCurrency;
    }
  }
  // </editor-fold>

  /**
   * Stock trade - buy & sell info. We use this as a structure rather than as a
   * class.
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockTrade">
  public static class StockTrade {
    /**
     * Type of the security
     */
    SecType secType;

    /**
     * Open part
     */
    HalfTrade open;

    /**
     * Close part
     */
    HalfTrade close;

    /**
     * Stock splits
     */
    StockSplit[] splits;

    /**
     * Stock renames
     */
    StockRename[] renames;

    /**
     * Cash credited on open
     */
    double openCreditCZK;

    /**
     * Cash Debited on open
     */
    double openDebitCZK;

    /**
     * Cash credited on close
     */
    double closeCreditCZK;

    /**
     * Cash Debited on close
     */
    double closeDebitCZK;

    /**
     * How much money we made opening position
     */
    double openSumCZK;

    /**
     * How money we made closing position
     */
    double closeSumCZK;

    /**
     * Profit in CZK
     */
    double profitCZK;

    /**
     * Rate used for open side
     */
    double openRate;

    /**
     * Rate used for close side
     */
    double closeRate;

    public StockTrade(SecType secType, HalfTrade open, HalfTrade close, StockSplit[] splits, StockRename[] renames)
        throws TradingException {
      this.secType = secType;
      this.open = open;
      this.close = close;
      this.splits = splits;
      this.renames = renames;

      /** Compute profit **/

      /* Check if all currencies are equal and are != CZK */
      /*
       * boolean foreignProfitOK = true;
       * boolean tradeInCZK = false;
       * String[] cs = new String[4];
       * 
       * cs[0] = buy.priceCurrency;
       * cs[1] = buy.feeCurrency;
       * cs[2] = sell.priceCurrency;
       * cs[3] = sell.feeCurrency;
       * 
       * String lastC = null;
       * for(int i=0;i<4;i++) {
       * if (cs[i] != null) {
       * if (lastC == null) lastC = cs[i];
       * else {
       * if (!lastC.equalsIgnoreCase(cs[i])) {
       * // Currencies not equal
       * foreignProfitOK = false;
       * break;
       * }
       * }
       * }
       * }
       * 
       * if (foreignProfitOK) {
       * if (lastC == null) foreignProfitOK = false;
       * else if (lastC.equalsIgnoreCase("CZK")) {
       * tradeInCZK = true;
       * foreignProfitOK = false;
       * }
       * }
       */

      /* Compute sums */
      try {
        // Open sum
        openRate = Settings.getExchangeRate(open.feeCurrency, open.date); // Use fee currency for general check, ideally
                                                                          // we'd store per-currency if mixed, but
                                                                          // usually they match
        // Actually, we should probably just store the rates used in the calc below
        double feeRate = Settings.getExchangeRate(open.feeCurrency, open.date);
        openDebitCZK = Stocks.roundToHellers(open.fee * feeRate);
        if (open.amount > 0) {
          double priceRate = Settings.getExchangeRate(open.priceCurrency, open.date);
          openRate = priceRate; // Primary rate is the price one
          openDebitCZK += Stocks.roundToHellers(open.price * open.amount * priceRate);
        } else {
          double priceRate = Settings.getExchangeRate(open.priceCurrency, open.date);
          openRate = priceRate;
          openCreditCZK = Stocks.roundToHellers(open.price * -open.amount * priceRate);
        }
        openSumCZK = openCreditCZK - openDebitCZK;

        // Sell sum
        double closeFeeRate = Settings.getExchangeRate(close.feeCurrency, close.date);
        closeDebitCZK = Stocks.roundToHellers(close.fee * closeFeeRate);
        if (close.amount > 0) {
          double closePriceRate = Settings.getExchangeRate(close.priceCurrency, close.date);
          closeRate = closePriceRate;
          closeCreditCZK += Stocks.roundToHellers(close.price * close.amount * closePriceRate);
        } else {
          double closePriceRate = Settings.getExchangeRate(close.priceCurrency, close.date);
          closeRate = closePriceRate;
          closeDebitCZK += Stocks.roundToHellers(close.price * -close.amount * closePriceRate);
        }
        closeSumCZK = closeCreditCZK - closeDebitCZK;

        // Profit
        profitCZK = openSumCZK + closeSumCZK;

      } catch (java.lang.IllegalArgumentException ex) {
        // Rethrow as trading exception
        throw new TradingException(ex.getMessage());
      }
    }

    /**
     * Get date of the infome
     *
     * @return Execution date of the income trade
     */
    public Date getIncomeDate() {
      if (open.amount < 0)
        return open.date;
      else
        return close.date;
    }

    /**
     * Return whether there was any income, on open or close
     */
    public boolean doesIncome() {
      return (openCreditCZK > 0) || (closeCreditCZK > 0);
    }
  }
  // </editor-fold>

  /**
   * Information about stock
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockInfo">
  private class StockInfo {
    /**
     * Security type
     */
    private SecType secType;

    /**
     * Stock ticker
     */
    private String ticker;

    /**
     * Stock fragments
     */
    private Vector<StockFragment> fragments;

    /**
     * Constructor
     */
    public StockInfo(String ticker, SecType secType) {
      this.ticker = ticker;
      this.secType = secType;

      fragments = new Vector<StockFragment>();
    }

    /**
     * Get ticker
     */
    public String getTicker() {
      return ticker;
    }

    /**
     * Get amount as an double
     */
    private double getAmountD() {
      double amount = 0;

      for (Iterator<StockFragment> i = fragments.iterator(); i.hasNext();) {
        StockFragment f = i.next();

        amount += f.getAmount();
      }

      return snapAmount(amount);
    }

    /**
     * Get amount as an int
     */
    public int getAmountInt() {
      // Round double amount
      return (int) Math.round(getAmountD());
    }

    /**
     * Add stock (buy or just transform-add)
     */
    /*
     * public void add(Date date,int amount, double price, double fee, String
     * priceCurrency, String feeCurrency)
     * {
     * fragments.add(new StockFragment(ticker, date, amount, price, fee,
     * priceCurrency, feeCurrency));
     * }
     */

    /**
     * Remove stock
     *
     * @return Stock trades that have been cause by this removal.
     */
    public StockTrade[] applyTrade(Date clearingDate, Date tradeDate, SecType secType, double amount, double price,
        double fee, String priceCurrency, String feeCurrency, String market) throws TradingException {
      // if (secType != this.secType) throw new TradingException("Ticker: "+ticker+",
      // datum: "+date+": Nesouhlasný typ tickeru (akcie vs derivát) v následném
      // obchodu!");

      Vector<StockTrade> trades = new Vector<StockTrade>();
      double am = amount; // Make amount as double - to be able to remove partially

      // Try if we can pair some of the fragments against this trade
      Vector<StockFragment> deletedFragments = new Vector<StockFragment>();
      for (StockFragment f : fragments) {
        if (Math.abs(am) < 0.0001)
          break; // Paired all of the trade

        double sa = f.getAmount();
        if (Math.signum(sa) == Math.signum(am))
          break; // Fragments are of the same type (long/short) as the trade; no point in trying
                 // to pair

        if (Math.abs(sa) > Math.abs(am)) {
          /* We can use part of this fragment */

          // Get what we bought
          double openfee = f.decreaseAmount(am);
          Stocks.HalfTrade open = new Stocks.HalfTrade(f.getOpened(), f.tradeDate, f.getOriginalTicker(),
              -am * f.getOAMRatio(), f.getPrice(), openfee, f.getPriceCurrency(), f.getFeeCurrency());
          if (f.isEmpty()) {
            // We can remove this fragment
            deletedFragments.add(f);
          }

          // And what we sold
          Stocks.HalfTrade close = new Stocks.HalfTrade(clearingDate, tradeDate, ticker, -am, price, fee,
              priceCurrency, feeCurrency);

          // Add trade
          trades.add(new StockTrade(secType, open, close, f.getSplits(), f.getRenames()));

          // Finished
          am = 0;
        } else {
          /* We need more fragments - use this one wholy */

          // Create buy halftrade
          Stocks.HalfTrade open = new Stocks.HalfTrade(f.getOpened(), f.tradeDate, f.getOriginalTicker(),
              f.getAmount() * f.getOAMRatio(), f.getPrice(), f.getFee(), f.getPriceCurrency(), f.getFeeCurrency());

          // And what we sold
          double closeFee = ((double) Math.round(fee * Math.abs(((double) f.getAmount()) / am) * 100)) / 100;
          Stocks.HalfTrade close = new Stocks.HalfTrade(clearingDate, tradeDate, ticker, f.getAmount(), price,
              closeFee, priceCurrency, feeCurrency);

          // Add trade
          trades.add(new StockTrade(secType, open, close, f.getSplits(), f.getRenames()));

          // Make this fragment to "to delete" list
          deletedFragments.add(f);

          // Decrease amount of stock we need & fee
          am += f.getAmount();
          fee -= closeFee;
          if (fee < 0)
            fee = 0;
        }
      }

      // Delete fragments marked for deletion
      for (StockFragment f : deletedFragments)
        fragments.remove(f);

      if (Math.abs(am) > 0.0001) {
        // We need to create fragment for the (rest of) the trade
        fragments.add(
            new StockFragment(ticker, clearingDate, tradeDate, am, price, fee, priceCurrency, feeCurrency, market));
      }

      StockTrade[] res = new StockTrade[trades.size()];
      trades.toArray(res);

      return res;
    }

    /**
     * Apply stock rename (on all fragments)
     */
    public void applyStockRename(Stocks.StockRename rename) {
      this.ticker = rename.getNewName();

      for (Iterator<StockFragment> i = fragments.iterator(); i.hasNext();) {
        i.next().applyStockRename(rename);
      }
    }

    /**
     * Apply stock split (on all fragments)
     */
    public void applyStockSplit(Stocks.StockSplit split) throws TradingException {
      double origAmount = getAmountD();

      for (Iterator<StockFragment> i = fragments.iterator(); i.hasNext();) {
        i.next().applyStockSplit(split);
      }

      // Normalize floating point noise after split
      for (StockFragment f : fragments) {
        f.amount = snapAmount(f.amount);
        f.originalAmount = snapAmount(f.originalAmount);
      }

      // Check if we got integer number of stock (in all fragments together - one
      // fragment CAN contain a fragment of a stock)
      /*
       * It is actually legal - fraction can will be then sold
       * double am = getAmountD();
       * 
       * if (Math.abs(am - Math.round(am)) > 0.001) throw new
       * TradingException("Ticker: "+ticker+", čas: "+split.getDate()
       * +" reverzní split by vedl k necelému počtu akcií! Původní počet: "
       * +origAmount+", poměr: "+split.getSRatio()+".");
       */
    }
  }
  // </editor-fold>

  /**
   * Info about stocks
   */
  HashMap<String, StockInfo> infos;

  /**
   * Transformations not yet applied
   * Key = yyyy-MM-dd HH:mm|broker|accountId (minute-level)
   */
  Map<String, List<Transaction>> pendingTransformations;

  /** Creates a new instance of Stocks */
  public Stocks() {
    infos = new HashMap<String, StockInfo>();
    pendingTransformations = new HashMap<String, List<Transaction>>();
  }

  /**
   * Get stock info - eventually create an empty one
   */
  private Stocks.StockInfo getInfo(String ticker, SecType secType) {
    ticker = ticker.toUpperCase();

    StockInfo res = infos.get(ticker);

    if (res != null)
      return res;

    res = new StockInfo(ticker, secType);

    infos.put(ticker, res);

    return res;
  }

  /**
   * Generate a key for grouping transformations
   * Format: yyyy-MM-dd HH:mm|broker|accountId
   */
  private String getTransformationKey(Transaction tx) {
    Calendar cal = GregorianCalendar.getInstance();
    cal.setTime(tx.getDate());
    cal.set(GregorianCalendar.SECOND, 0);
    cal.set(GregorianCalendar.MILLISECOND, 0);

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    String timeKey = df.format(cal.getTime());

    // Add broker and accountId for disambiguation
    String broker = tx.getBroker() != null ? tx.getBroker() : "";
    String accountId = tx.getAccountId() != null ? tx.getAccountId() : "";

    return timeKey + "|" + broker + "|" + accountId;
  }

  /**
   * Process a bucket of transformations (all transactions in the same minute)
   * @param bucket List of transformations to process
   * @throws TradingException if the bucket is invalid
   */
  private void processTransformationBucket(List<Transaction> bucket) throws TradingException {
    // Split by TxnID
    Map<String, List<Transaction>> byTxnId = new HashMap<String, List<Transaction>>();
    List<Transaction> noTxnId = new ArrayList<Transaction>();

    for (Transaction tx : bucket) {
      String txnId = tx.getTxnId();
      if (txnId != null && !txnId.isEmpty()) {
        byTxnId.computeIfAbsent(txnId, k -> new ArrayList<Transaction>()).add(tx);
      } else {
        noTxnId.add(tx);
      }
    }

    // Process transformations with TxnID
    for (List<Transaction> group : byTxnId.values()) {
      if (group.size() != 2) {
        throw new TradingException(
          "Transformace s TxnID=" + group.get(0).getTxnId() +
          " má " + group.size() + " transakcí (očekáváno 2: SUB + ADD). Každá korporátní akce musí mít PŘESNĚ 2 transakce (SUB + ADD)."
        );
      }
      applyTransformationPair(group.get(0), group.get(1));
    }

    // Process transformations without TxnID (backward compatibility)
    if (noTxnId.size() == 2) {
      applyTransformationPair(noTxnId.get(0), noTxnId.get(1));
    } else if (noTxnId.size() > 2) {
      throw new TradingException(
        "Nalezeno " + noTxnId.size() + " transformací ve stejné minutě bez ID transakce.\n\n" +
        "Pro vyřešení:\n" +
        "  1) Pokud importujete z IBKR/Trading 212, zkontrolujte nastavení exportu.\n" +
        "  2) Přidejte ID transakce (TxnID) do obou transakcí.\n" +
        "  3) Nebo rozdělte transformace do různých minut."
      );
    }
  }

  /**
   * Flush all pending transformations
   * @throws TradingException if any transformation is invalid
   */
  private void flushPendingTransformation() throws TradingException {
    for (List<Transaction> bucket : pendingTransformations.values()) {
      processTransformationBucket(bucket);
    }
    pendingTransformations.clear();
  }

  /**
   * Apply transaction
   *
   * @return Trades that were finished during the transaction. May be also NULL or
   *         empty array for no trades, so beware!
   */
  public StockTrade[] applyTransaction(Transaction tx, boolean useExecutionDate) throws TradingException {
    if (tx != null && tx.isDisabled()) {
      return null;
    }
    // Do not count dividends / interests in trading PnL engine
    if ((tx.direction == Transaction.DIRECTION_DIVI_BRUTTO) || (tx.direction == Transaction.DIRECTION_DIVI_NETTO15)
        || (tx.direction == Transaction.DIRECTION_DIVI_TAX) || (tx.direction == Transaction.DIRECTION_DIVI_UNKNOWN)
        || (tx.direction == Transaction.DIRECTION_INT_BRUTTO) || (tx.direction == Transaction.DIRECTION_INT_TAX)
        || (tx.direction == Transaction.DIRECTION_INT_PAID) || (tx.direction == Transaction.DIRECTION_INT_FEE))
      return null;

    // Sanity check - does transaction have ticker & amount?
    if (tx.getDate() == null)
      throw new TradingException("Ticker: " + tx.getTicker() + ": transakce nemá vyplněné datum!");
    if (tx.getTicker() == null)
      throw new TradingException("Datum: " + tx.getDate() + ": transakce nemá vyplněný ticker!");
    if (tx.getAmount() == null)
      throw new TradingException(
          "Ticker: " + tx.getTicker() + ": datum: " + tx.getDate() + ", transakce nemá vyplněný počet!");

    if (pendingTransformations.size() > 0) {
      // Get key of the first pending transformation
      String firstKey = pendingTransformations.keySet().iterator().next();

      // Check if current transaction is a transformation
      boolean isTransformation = (tx.getDirection() == Transaction.DIRECTION_TRANS_ADD)
          || (tx.getDirection() == Transaction.DIRECTION_TRANS_SUB);

      if (!isTransformation) {
        // Not a transformation - flush pending transformations
        flushPendingTransformation();
      } else {
        // Check if the minute key has changed
        String currentKey = getTransformationKey(tx);
        if (!currentKey.equals(firstKey)) {
          // Different minute - flush pending transformations first
          flushPendingTransformation();

          // Add this transformation to new bucket
          pendingTransformations.computeIfAbsent(currentKey, k -> new ArrayList<Transaction>()).add(tx);
        } else {
          // Same minute - add to current bucket
          pendingTransformations.get(currentKey).add(tx);
        }
      }
    }

    if ((tx.getDirection() == Transaction.DIRECTION_TRANS_ADD)
        || (tx.getDirection() == Transaction.DIRECTION_TRANS_SUB)) {
      // Transformation - add to bucket
      String key = getTransformationKey(tx);
      pendingTransformations.computeIfAbsent(key, k -> new ArrayList<Transaction>()).add(tx);
      return null;
    }

    /* Definitely not a transformation - execute */

    // Determine execution date
    Date txDate = tx.getDate();
    if (useExecutionDate) {
      txDate = tx.getExecutionDate();
    }

    double amount = 0;
    if ((tx.getDirection() == Transaction.DIRECTION_SBUY) || (tx.getDirection() == Transaction.DIRECTION_DBUY)
        || (tx.getDirection() == Transaction.DIRECTION_CBUY)) {
      Double a = tx.getAmount();
      if (a != null)
        amount = a.doubleValue();
    } else if ((tx.getDirection() == Transaction.DIRECTION_SSELL) || (tx.getDirection() == Transaction.DIRECTION_DSELL)
        || (tx.getDirection() == Transaction.DIRECTION_CSELL)) {
      Double a = tx.getAmount();
      if (a != null)
        amount = -a.doubleValue();
    } else {
      throw new TradingException(
          "Ticker: " + tx.getTicker() + ", datum: " + tx.getDate() + " neznámý typ transakce; interní chyba?");
    }

    // IBKR fractional disposal: keep share amounts aligned with 4-decimal reporting.
    if (isIbkrLfTransaction(tx)) {
      amount = roundAmount(amount, AMOUNT_SCALE_DEFAULT);
    }

    // Determine type
    SecType type = SecType.STOCK;

    switch (tx.getDirection()) {
      case Transaction.DIRECTION_SBUY:
      case Transaction.DIRECTION_SSELL:
        type = SecType.STOCK;
        break;
      case Transaction.DIRECTION_DBUY:
      case Transaction.DIRECTION_DSELL:
        type = SecType.DERIVATE;
        break;
      case Transaction.DIRECTION_CBUY:
      case Transaction.DIRECTION_CSELL:
        type = SecType.CASH;
        break;
      default:
        type = SecType.STOCK;
    }

    // Process transaction
    StockInfo info = getInfo(tx.getTicker(), type);

    double price = 0, fee = 0;

    Double d = tx.getPrice();
    if (d != null)
      price = d.doubleValue();

    d = tx.getFee();
    if (d != null)
      fee = d.doubleValue();

    StockTrade[] res = info.applyTrade(txDate, tx.getDate(), type, amount, price, fee, tx.getPriceCurrency(),
        tx.getFeeCurrency(), tx.market);

    // For IBKR LF (cash-in-lieu fractional disposal), keep holdings stable to the same
    // 4-decimal domain as the reported transaction.
    if (isIbkrLfTransaction(tx)) {
      quantizeFragmentsToScale(info, AMOUNT_SCALE_DEFAULT);
      // Align total to 4-decimal domain to remove binary float residue.
      reconcileHoldingsToTarget(info, roundAmount(info.getAmountD(), AMOUNT_SCALE_DEFAULT));
    }

    // Check if we are out of this stock
    if (info.getAmountD() == 0) {
      // Remove stock info from account
      infos.remove(info.getTicker());
    }

    return res;
  }

  /**
   * Finish transformations that may be yet incomplete
   */
  /**
   * Apply a single transformation pair (SUB + ADD)
   * @param tx1 First transaction (will be adjusted to SUB if needed)
   * @param tx2 Second transaction
   * @throws TradingException if validation fails
   */
  private void applyTransformationPair(Transaction tx1, Transaction tx2) throws TradingException {
    // Make TX1 to be always SUB
    if (tx1.getDirection() != Transaction.DIRECTION_TRANS_SUB) {
      Transaction tx = tx2;
      tx2 = tx1;
      tx1 = tx;
    }

    // Sanity check - do all transactions have ticker & amount?
    if ((tx1.getTicker() == null) || (tx2.getTicker() == null) || (tx1.getAmount() == null)
        || (tx2.getAmount() == null))
      throw new TradingException("Interní chyba, uložena transformace bez dostatečných argumentů!");

    // Get stock info
    StockInfo info = infos.get(tx1.getTicker());
    if (info == null)
      throw new TradingException(
          "Ticker: " + tx1.getTicker() + ", datum: " + tx1.getDate() + ": Pokus o odebrání tickeru, který není známý!");

    if (!tx1.getTicker().equalsIgnoreCase(tx2.getTicker())) {
      // Ticker changed- create & apply rename operation
      Stocks.StockRename rename = new Stocks.StockRename(tx1.getDate(), tx1.getTicker(), tx2.getTicker());

      info.applyStockRename(rename);

      // Move info to another slot
      infos.put(rename.getNewName(), info);
      infos.remove(rename.getOldName());
    }

    double am1 = tx1.getAmount().doubleValue();
    double am2 = tx2.getAmount().doubleValue();
    if (am1 != am2) {
      // Split or reverse split
      // Use the declared before/after amounts as the authoritative source of truth.
      // Applying a derived ratio (from note or double division) can introduce drift.
      double ratio = am2 / am1;
      Stocks.StockSplit split = new Stocks.StockSplit(tx1.getDate(), ratio);

      info.applyStockSplit(split);

      // IBKR and similar sources represent post-corporate-action quantities in limited
      // precision (typically 4 decimals). Quantize fragments first, then reconcile the
      // total to the declared TRANS_ADD amount.
      quantizeFragmentsToScale(info, AMOUNT_SCALE_DEFAULT);

      // Force holdings to exactly match the declared TRANS_ADD amount.
      // This eliminates recurring fractional residues (1/9, 2/3, 8/9, etc.)
      // and keeps later LF/cash actions consistent.
      reconcileHoldingsToTarget(info, am2);
    }

    // If holdings are now zero, remove the ticker entry so it doesn't show in Stav uctu.
    if (info.getAmountD() == 0) {
      infos.remove(info.getTicker());
    }
  }

  /**
   * Apply a single transformation pair (SUB + ADD)
   * @param tx1 First transaction (will be adjusted to SUB if needed)
   * @param tx2 Second transaction
   * @throws TradingException if validation fails
   */
  public void finishTransformations() throws TradingException {
    flushPendingTransformation();
  }

  public String[] getStockTickers() {
    String[] res = new String[infos.size()];
    infos.keySet().toArray(res);

    return res;
  }

  /**
   * Get amount of stock we have
   */
  // public int getStockAmount(String stock)
  public double getStockAmount(String stock) {
    StockInfo info = infos.get(stock.toUpperCase());

    if (info == null)
      return 0;
    // else return info.getAmountInt();
    else
      return info.getAmountD();
  }

  /**
   * Get fragments of the stock
   *
   * @param symbol Security symbol
   *
   * @return
   */
  public final Vector<StockFragment> getSecurityFragments(String symbol) {
    StockInfo info = infos.get(symbol.toUpperCase());

    if (info == null)
      return null;
    else
      return info.fragments;
  }

  /**
   * Get type of the secutiry
   *
   * @param symbol Security symbol
   *
   * @return
   */
  public SecType getSecurityType(String symbol) {
    StockInfo info = infos.get(symbol.toUpperCase());

    if (info == null)
      throw new IllegalArgumentException("Security does not exist");

    return info.secType;
  }

  /**
   * Round price to hallers
   *
   * @param price
   *
   * @return Rounded price
   */
  public static double roundToHellers(double price) {
    return ((double) Math.round(price * 100)) / 100;
  }

  /**
   * Create transactions that led to this state. I.e. all transactions (or their
   * parts) that were
   * not yet completely closed.
   *
   * @param stock Stock name
   *
   * @return Set of transactions that creates this state
   */
  public TransactionSet buildStateTransactions() throws Exception {
    TransactionSet res = new TransactionSet();

    // Pass tickers
    for (String ticker : infos.keySet()) {
      StockInfo i = infos.get(ticker);

      // Pass fragments
      for (StockFragment f : i.fragments) {
        int direction = 0;

        switch (i.secType) {
          case STOCK:
            if (f.amount < 0)
              direction = Transaction.DIRECTION_SSELL;
            else
              direction = Transaction.DIRECTION_SBUY;
            break;
          case DERIVATE:
            if (f.amount < 0)
              direction = Transaction.DIRECTION_DSELL;
            else
              direction = Transaction.DIRECTION_DBUY;
            break;
          case CASH:
            if (f.amount < 0)
              direction = Transaction.DIRECTION_DSELL;
            else
              direction = Transaction.DIRECTION_DBUY;
            break;
        }

        res.addTransaction(f.opened, direction, ticker, Math.abs(f.amount), f.price, f.priceCurrency, f.fee,
            f.feeCurrency, f.market, f.opened, f.note);
      }
    }

    return res;
  }

  /**
   * Check for any open short positions. If position was opened in a year given as
   * a parameter,
   * generate pseudo-close half-trade on December 31st with price 0. This is to
   * make income
   * appear in the year position was opened though it is not closed yet.
   *
   * Note: The "closed" stock fragments are not removed, only "trades" are
   * generated.
   *
   * @param year Year position must be opened in
   *
   * @return Array of trades, may be null
   */
  public StockTrade[] autocloseShortTransactions(int year) throws TradingException {
    Vector<StockTrade> trades = new Vector<StockTrade>();

    // Create date of Decemeber 31st
    GregorianCalendar cal = new GregorianCalendar(year, 12 - 1, 31);
    Date endOfYear = cal.getTime();

    // Pass tickers
    for (String ticker : infos.keySet()) {
      StockInfo i = infos.get(ticker);

      // Pass fragments
      for (StockFragment f : i.fragments) {
        if (f.amount < 0) {
          // Short-position fragment
          cal.setTime(f.opened);
          if (cal.get(GregorianCalendar.YEAR) == year) {
            /* In the correct year - generate pseudo - trade */
            HalfTrade open = new HalfTrade(f.opened, f.tradeDate, ticker, f.amount, f.price, f.fee, f.priceCurrency,
                f.feeCurrency);
            HalfTrade close = new HalfTrade(endOfYear, endOfYear, ticker, -f.amount, 0, 0, f.priceCurrency,
                f.feeCurrency);

            // Generate complete "trade"
            StockTrade st = new StockTrade(i.secType, open, close, f.getSplits(), f.getRenames());
            trades.add(st);
          }
        }
      }
    }

    // Make vector to an array
    if (trades.size() == 0)
      return null;

    StockTrade[] res = new StockTrade[trades.size()];
    trades.toArray(res);
    return res;
  }

  /**
   * @param d1 Date1 (open)
   * @param d2 Date2 (close)
   * 
   * @return Whether difference between dates is >6 months
   */
  public static boolean isOverTaxFreeDuration(Date d1, Date d2) {
    GregorianCalendar cal1 = new GregorianCalendar();
    GregorianCalendar cal2 = new GregorianCalendar();

    cal1.setTime(d1);
    cal2.setTime(d2);

    int mdiff = (cal2.get(GregorianCalendar.YEAR) - cal1.get(GregorianCalendar.YEAR)) * 12
        + (cal2.get(GregorianCalendar.MONTH) - cal1.get(GregorianCalendar.MONTH));

    if (d1.before(TAX_FREE_DURATION_BOUNDARY)) {
      if (mdiff < 6)
        return false;
      if (mdiff > 6)
        return true;
    } else {
      if (mdiff < 3 * 12)
        return false;
      if (mdiff > 3 * 12)
        return true;
    }

    return (cal2.get(GregorianCalendar.DAY_OF_MONTH) > cal1.get(GregorianCalendar.DAY_OF_MONTH));
  }

}
