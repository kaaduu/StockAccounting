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

/**
 * Class holding list of stocks we bought and when
 *
 * @author lemming
 */
public class Stocks
{
  /**
   * Security type
   */
  public static enum SecType { STOCK, DERIVATE, CASH };

  public static Date TAX_FREE_DURATION_BOUNDARY = new GregorianCalendar(2014, 0, 1).getTime();
  
  /**
   * Stock split or reverse split.
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockSplit">
  public class StockSplit
  {
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
    public StockSplit(Date date, double ratio)
    {
      this.date = date;
      this.ratio = ratio;
    }
    
    /**
     * Date getter
     */
    public Date getDate() { return date; }
    
    /**
     * Ratio getter
     */
    public double getRatio() { return ratio; }
    
    /**
     * Return type ("split or reverse-split")
     */
    public String getType() { return (ratio<1)?"reverse split":"split"; }
    
    /**
     * Return string ratio
     */
    public String getSRatio()
    {
      if (ratio > 1) return ratio+":1";
      else return "1:"+Math.round(1/ratio);
    }
  }
  // </editor-fold>
  
  /**
   * Stock rename
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockSplit">
  public class StockRename
  {
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
    public StockRename(Date date, String oldName, String newName)
    {
      this.date = date;
      this.oldName = oldName;
      this.newName = newName;
    }
    
    /**
     * Old name getter
     */
    public String getOldName() { return oldName; }
    
    /**
     * New name getter
     */
    public String getNewName() { return newName; }
    
    /**
     * Date getter
     */
    public Date getDate() { return date; }
  }
  // </editor-fold>
  
  /**
   * Stock fragment - bought at one price at one point at a time
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockFragment">
  public class StockFragment
  {
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
     * Amount we hold (is double since it can be fractional due to reverse splits). It can be also negative.
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
     * Ratio to original amount (i.e. to convert from current amount to original amount)
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
    public StockFragment(String ticker, Date tradeDate, Date executionDate, double amount, double price, double fee, String priceCurrency, String feeCurrency, String market)
    {
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
    public void applyStockRename(StockRename rename)
    {
      // Just record it - we do not track current ticker
      renames.add(rename);
    }
    
    /**
     * Apply split / reverse split
     */
    public void applyStockSplit(StockSplit split)
    {
      // Change amount
      amount *= split.getRatio();
      oamRatio /= split.getRatio();
      
      // Check if we can round amount - to avoid fractioning errors
      double a = Math.round(amount);      
      if (Math.abs(a-amount) < 0.001) amount = a;
      
      // Record split
      splits.add(split);
    }

    /**
     * Original amount getter
     */
    public String getOriginalTicker() { return originalTicker; }
    
    /**
     * Opened getter
     */
    public Date getOpened() { return opened; }
    
    /**
     * Amount getter
     */
    public double getAmount() { return amount; }
    
    /**
     * Price getter
     */
    public double getPrice() { return price; }
    
    /**
     * Fee getter
     */
    public double getFee() { return fee; }
    
    /**
     * Price currency getter
     */
    public String getPriceCurrency() { return priceCurrency; }
    
    /**
     * Fee currency getter
     */
    public String getFeeCurrency() { return feeCurrency; }
    
    /**
     * Get original amount ratio
     */
    public double getOAMRatio() { return oamRatio; }
    
    /**
     * Get splits
     */
    public StockSplit[] getSplits()
    {
      StockSplit[] res = new StockSplit[splits.size()];
      splits.toArray(res);
      
      return res;
    }
    
    /**
     * Get renames
     */
    public StockRename[] getRenames()
    {
      StockRename[] res = new StockRename[renames.size()];
      renames.toArray(res);
      
      return res;
    }
    
    /**
     * Decrease amount
     *
     * @return Fee for this part of amount
     */
    public double decreaseAmount(double damount) throws TradingException
    {
      if (Math.signum(damount) == Math.signum(amount)) throw new TradingException("Interní chyba: Pokus odebrat z fragmentu pozici se stejným znaménkem!");
      if (Math.abs(damount) > Math.abs(amount)) throw new TradingException("Interní chyba: Pokus odebrat z fragmentu více akcií než obsahuje!");
      
      if (Math.abs(amount+damount) < 0.0001) {
        // Decreased all
        double res = fee;        
        amount = 0;
        fee = 0;
        return res;
      }
      else {
        double res = ((double)Math.round(fee * Math.abs(damount / amount) * 100))/100;
        
        amount += damount;
        fee -= res;
        
        if (fee < 0) fee = 0; // To be sure
      
        return res;
      }
    }
    
    /**
     * Return whether this fragment is empty (i.e., holds no stock)
     */
    public boolean isEmpty()
    {
      return (Math.abs(amount) < 0.0001);
    }
  }
  // </editor-fold>
  
  /**
   * Stock trading exception - invalid trade
   */
  public static class TradingException extends Exception
  {
    public TradingException(String message) { super(message); }
  }
  
  /**
   * Structure holding one half of the trade
   */
  // <editor-fold defaultstate="collapsed" desc="Class: HalfTrade">
  public static class HalfTrade
  {
    /**
     * Date
     */
    public Date date;
      
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
      
    public HalfTrade(Date date, String ticker, double amount, double price, double fee, String priceCurrency, String feeCurrency)
    {
      this.date = date;
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
   * Stock trade - buy & sell info. We use this as a structure rather than as a class.
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockTrade">
  public static class StockTrade
  {
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

    public StockTrade(SecType secType, HalfTrade open, HalfTrade close, StockSplit[] splits, StockRename[] renames) throws TradingException
    {
      this.secType = secType;
      this.open = open;
      this.close = close;
      this.splits = splits;
      this.renames = renames;
      
      /** Compute profit **/
      
      /* Check if all currencies are equal and are != CZK */
      /*
      boolean foreignProfitOK = true;
      boolean tradeInCZK = false;
      String[] cs = new String[4];
      
      cs[0] = buy.priceCurrency;
      cs[1] = buy.feeCurrency;
      cs[2] = sell.priceCurrency;
      cs[3] = sell.feeCurrency;
      
      String lastC = null;
      for(int i=0;i<4;i++) {
        if (cs[i] != null) {
          if (lastC == null) lastC = cs[i];
          else {
            if (!lastC.equalsIgnoreCase(cs[i])) {
              // Currencies not equal
              foreignProfitOK = false;
              break;
            }
          }
        }
      }
      
      if (foreignProfitOK) {
        if (lastC == null) foreignProfitOK = false;
        else if (lastC.equalsIgnoreCase("CZK")) {
          tradeInCZK = true;
          foreignProfitOK = false;
        }
      }
      */

      /* Compute sums */
      try {
        // Buy sum
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(open.date);
        int year = cal.get(GregorianCalendar.YEAR);
        openDebitCZK = Stocks.roundToHellers(open.fee * Settings.getRatio(open.feeCurrency,year));
        if (open.amount > 0) openDebitCZK += Stocks.roundToHellers(open.price * open.amount * Settings.getRatio(open.priceCurrency,year));
        else openCreditCZK = Stocks.roundToHellers(open.price * -open.amount * Settings.getRatio(open.priceCurrency,year));
        openSumCZK = openCreditCZK-openDebitCZK;

        // Sell sum
        cal.setTime(close.date);
        year = cal.get(GregorianCalendar.YEAR);
        closeDebitCZK = Stocks.roundToHellers(close.fee * Settings.getRatio(close.feeCurrency,year));
        if (close.amount > 0) closeCreditCZK += Stocks.roundToHellers(close.price * close.amount * Settings.getRatio(close.priceCurrency,year));
        else closeDebitCZK += Stocks.roundToHellers(close.price * -close.amount * Settings.getRatio(close.priceCurrency,year));
        closeSumCZK = closeCreditCZK-closeDebitCZK;
        
        // Profit
        profitCZK = openSumCZK + closeSumCZK;
        
      }
      catch(java.lang.IllegalArgumentException ex) {
        // Rethrow as trading exception
        throw new TradingException(ex.getMessage());
      }
    }

    /**
     * Get date of the infome
     *
     * @return Execution date of the income trade
     */
    public Date getIncomeDate()
    {
      if (open.amount < 0) return open.date;
      else return close.date;
    }

    /**
     * Return whether there was any income, on open or close
     */
    public boolean doesIncome()
    {
      return (openCreditCZK > 0) || (closeCreditCZK > 0);
    }
  }
  // </editor-fold>
  
  /**
   * Information about stock
   */
  // <editor-fold defaultstate="collapsed" desc="Class: StockInfo">
  private class StockInfo
  {
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
    public StockInfo(String ticker, SecType secType)
    {
      this.ticker = ticker;
      this.secType = secType;
      
      fragments = new Vector<StockFragment>();
    }
    
    /**
     * Get ticker
     */
    public String getTicker() { return ticker; }
    
    /**
     * Get amount as an double
     */
    private double getAmountD()
    {
      double amount = 0;
      
      for(Iterator<StockFragment> i=fragments.iterator();i.hasNext();) {
        StockFragment f = i.next();
        
        amount += f.getAmount();
      }
      
      return amount;
    }
    
    /**
     * Get amount as an int
     */
    public int getAmountInt()
    { 
      // Round double amount
      return (int)Math.round(getAmountD());
    }
    
    /**
     * Add stock (buy or just transform-add)
     */
    /*
    public void add(Date date,int amount, double price, double fee, String priceCurrency, String feeCurrency)
    {
      fragments.add(new StockFragment(ticker, date, amount, price, fee, priceCurrency, feeCurrency));
    }
     */
    
    /**
     * Remove stock 
     *
     * @return Stock trades that have been cause by this removal.
     */
    public StockTrade[] applyTrade(Date clearingDate, Date tradeDate, SecType secType, double amount, double price, double fee, String priceCurrency, String feeCurrency, String market) throws TradingException
    {
//      if (secType != this.secType) throw new TradingException("Ticker: "+ticker+", datum: "+date+": Nesouhlasný typ tickeru (akcie vs derivát) v následném obchodu!");

      Vector<StockTrade> trades = new Vector<StockTrade>();
      double am = amount; // Make amount as double - to be able to remove partially
      
      // Try if we can pair some of the fragments against this trade
      Vector<StockFragment> deletedFragments = new Vector<StockFragment>();
      for(StockFragment f : fragments) {
        if (Math.abs(am) < 0.0001) break; // Paired all of the trade
        
        double sa = f.getAmount();
        if (Math.signum(sa) == Math.signum(am)) break; // Fragments are of the same type (long/short) as the trade; no point in trying to pair
        
        if (Math.abs(sa) > Math.abs(am)) {
          /* We can use part of this fragment */
          
          // Get what we bought
          double openfee = f.decreaseAmount(am);
          Stocks.HalfTrade open = new Stocks.HalfTrade(f.getOpened(), f.getOriginalTicker(), -am*f.getOAMRatio(),  f.getPrice(), openfee, f.getPriceCurrency(), f.getFeeCurrency());
          if (f.isEmpty()) {
            // We can remove this fragment
            deletedFragments.add(f);
          }
          
          // And what we sold
          Stocks.HalfTrade close = new Stocks.HalfTrade(clearingDate, ticker, -am, price, fee, priceCurrency, feeCurrency);
          
          // Add trade
          trades.add(new StockTrade(secType, open, close, f.getSplits(), f.getRenames()));
          
          // Finished
          am = 0;
        }
        else {
          /* We need more fragments - use this one wholy */
          
          // Create buy halftrade
          Stocks.HalfTrade open = new Stocks.HalfTrade(f.getOpened(), f.getOriginalTicker(), f.getAmount()*f.getOAMRatio(),  f.getPrice(), f.getFee(), f.getPriceCurrency(), f.getFeeCurrency());

          // And what we sold
          double closeFee = ((double)Math.round(fee * Math.abs(((double)f.getAmount()) / am) * 100))/100;
          Stocks.HalfTrade close = new Stocks.HalfTrade(clearingDate, ticker, f.getAmount(), price, closeFee, priceCurrency, feeCurrency);

          // Add trade
          trades.add(new StockTrade(secType, open, close, f.getSplits(), f.getRenames()));
          
          // Make this fragment to "to delete" list
          deletedFragments.add(f);
          
          // Decrease amount of stock we need & fee
          am += f.getAmount();
          fee -= closeFee;
          if (fee < 0) fee = 0;
        }
      }

      // Delete fragments marked for deletion
      for(StockFragment f : deletedFragments) fragments.remove(f);
      
      if (Math.abs(am) > 0.0001) {
        // We need to create fragment for the (rest of) the trade
        fragments.add(new StockFragment(ticker, clearingDate, tradeDate, am, price, fee, priceCurrency, feeCurrency, market));
      }
      
      StockTrade[] res = new StockTrade[trades.size()];
      trades.toArray(res);
      
      return res;
    }
    
    /**
     * Apply stock rename (on all fragments)
     */
    public void applyStockRename(Stocks.StockRename rename)
    {
      this.ticker = rename.getNewName();

      for(Iterator<StockFragment> i = fragments.iterator();i.hasNext();) {
        i.next().applyStockRename(rename);
      }
    }
    
    /** 
     * Apply stock split (on all fragments)
     */
    public void applyStockSplit(Stocks.StockSplit split) throws TradingException
    {
      double origAmount = getAmountD();
      
      for(Iterator<StockFragment> i = fragments.iterator();i.hasNext();) {
        i.next().applyStockSplit(split);
      }
      
      // Check if we got integer number of stock (in all fragments together - one fragment CAN contain a fragment of a stock)
      /* It is actually legal - fraction can will be then sold
      double am = getAmountD();
      
      if (Math.abs(am - Math.round(am)) > 0.001) throw new TradingException("Ticker: "+ticker+", čas: "+split.getDate()+" reverzní split by vedl k necelému počtu akcií! Původní počet: "+origAmount+", poměr: "+split.getSRatio()+".");
      */
    }
  }
  // </editor-fold>
  
  /**
   * Info about stocks
   */
  HashMap<String,StockInfo> infos;
  
  /**
   * Transformations not yet applied
   */
  Vector<Transaction> trans;
  
  /** Creates a new instance of Stocks */
  public Stocks()
  {
    infos = new HashMap<String,StockInfo>();
    trans = new Vector<Transaction>();
  }

  /**
   * Get stock info - eventually create an empty one
   */
  private Stocks.StockInfo getInfo(String ticker, SecType secType)
  {
    ticker = ticker.toUpperCase();
    
    StockInfo res = infos.get(ticker);
    
    if (res != null) return res;
    
    res = new StockInfo(ticker, secType);
    
    infos.put(ticker,res);
    
    return res;
  }
  
  /**
   * Apply transaction
   *
   * @return Trades that were finished during the transaction. May be also NULL or empty array for no trades, so beware!
   */
  public StockTrade[] applyTransaction(Transaction tx, boolean useExecutionDate) throws TradingException
  {
    // Do not count dividends
    if ((tx.direction == Transaction.DIRECTION_DIVI_BRUTTO) || (tx.direction == Transaction.DIRECTION_DIVI_NETTO15) || (tx.direction == Transaction.DIRECTION_DIVI_TAX) || (tx.direction == Transaction.DIRECTION_DIVI_UNKNOWN)) return null;
    
    // Sanity check - does transaction have ticker & amount?
    if (tx.getDate() == null) throw new TradingException("Ticker: "+tx.getTicker()+": transakce nemá vyplněné datum!");
    if (tx.getTicker() == null) throw new TradingException("Datum: "+tx.getDate()+": transakce nemá vyplněný ticker!");
    if (tx.getAmount() == null) throw new TradingException("Ticker: "+tx.getTicker()+": datum: "+tx.getDate()+", transakce nemá vyplněný počet!");
    
    if (trans.size() > 0) {
      // Check if we should apply transactions
      Transaction tx1 = trans.get(0);
      
      if ((tx.getDirection() != Transaction.DIRECTION_TRANS_ADD) && (tx.getDirection() != Transaction.DIRECTION_TRANS_SUB)) finishTransformations(); // Not a transformation - finish
      if (tx.getDate().compareTo(tx1.getDate()) != 0) finishTransformations(); // Another date - finish
      else {
        if (tx.getDirection() == tx1.getDirection()) {
          throw new TradingException("Ticker: "+tx.getTicker()+", datum: "+tx.getDate()+", dvě transformace ve stejný čas jsou stejného typu!");
        }
        
        if (trans.size() == 2) {
          throw new TradingException("Ticker: "+tx.getTicker()+", datum: "+tx.getDate()+", více než dvě transformace ve stejný čas!");
        }
        
        // Add transformation
        trans.add(tx);
        
        return null;
      }
    }
    
    if ((tx.getDirection() == Transaction.DIRECTION_TRANS_ADD) || (tx.getDirection() == Transaction.DIRECTION_TRANS_SUB)) {
      // Transformation - add
      trans.add(tx);
      return null;
    }
    
    /* Definitely not a transformation - execute */
    
    // Determine execution date
    Date txDate = tx.getDate();
    if (useExecutionDate) {
      txDate = tx.getExecutionDate();
    }
    
    double amount = 0;
    if ((tx.getDirection() == Transaction.DIRECTION_SBUY) || (tx.getDirection() == Transaction.DIRECTION_DBUY) || (tx.getDirection() == Transaction.DIRECTION_CBUY)) {
      Double a = tx.getAmount();
      if (a != null) amount = a.doubleValue();
    }
    else if ((tx.getDirection() == Transaction.DIRECTION_SSELL) || (tx.getDirection() == Transaction.DIRECTION_DSELL) || (tx.getDirection() == Transaction.DIRECTION_CSELL)) {
      Double a = tx.getAmount();
      if (a != null) amount = -a.doubleValue();
    }
    else {
      throw new TradingException("Ticker: "+tx.getTicker()+", datum: "+tx.getDate()+" neznámý typ transakce; interní chyba?");
    }

    // Determine type
    SecType type = SecType.STOCK;

    switch(tx.getDirection()) {
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
    if (d != null) price = d.doubleValue();
      
    d = tx.getFee();
    if (d != null) fee = d.doubleValue();
      
    StockTrade[] res = info.applyTrade(txDate, tx.getDate(), type, amount, price, fee, tx.getPriceCurrency(), tx.getFeeCurrency(), tx.market);
      
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
  public void finishTransformations() throws TradingException
  {
    if (trans.size() == 0) return; // Nothing to finish
    
    if (trans.size() != 2) {
      Transaction tx = trans.get(0);
      throw new TradingException("Ticker: "+tx.getTicker()+", datum: "+tx.getDate()+", pouze jedna transformace ve stejný čas!");
    }
    
    Transaction tx1 = trans.get(0);
    Transaction tx2 = trans.get(1);
    
    // Make TX1 to be always SUB
    if (tx1.getDirection() != Transaction.DIRECTION_TRANS_SUB) {
      Transaction tx = tx2;
      tx2 = tx1;
      tx1 = tx;
    }

    // Sanity check - do all transactions have ticker & amount?
    if ((tx1.getTicker() == null) || (tx2.getTicker() == null) || (tx1.getAmount() == null) || (tx2.getAmount() == null))
      throw new TradingException("Interní chyba, uložena transformace bez dostatečných argumentů!");
    
    // Get stock info
    StockInfo info = infos.get(tx1.getTicker());
    if (info == null) throw new TradingException("Ticker: "+tx1.getTicker()+", datum: "+tx1.getDate()+": Pokus o odebrání tickeru, který není známý!");
   
    if (!tx1.getTicker().equalsIgnoreCase(tx2.getTicker())) {
      // Ticker changed- create & apply rename operation
      Stocks.StockRename rename = new Stocks.StockRename(tx1.getDate(),tx1.getTicker(),tx2.getTicker());
      
      info.applyStockRename(rename);
      
      // Move info to another slot
      infos.put(rename.getNewName(),info);
      infos.remove(rename.getOldName());
    }
    
    double am1 = tx1.getAmount().doubleValue();
    double am2 = tx2.getAmount().doubleValue();
    if (am1 != am2) {
      // Split or reverse split
      Stocks.StockSplit split = new Stocks.StockSplit(tx1.getDate(),am2 / am1);
      
      info.applyStockSplit(split);
    }
    
    trans.clear();
  }
  
  /**
   * Get list of stock currently on the acount
   */
  public String[] getStockTickers()
  {
    String[] res = new String[infos.size()];
    infos.keySet().toArray(res);
    
    return res;
  }
  
  /**
   * Get amount of stock we have
   */
  //public int getStockAmount(String stock)
  public double getStockAmount(String stock)
  {
    StockInfo info = infos.get(stock.toUpperCase());
    
    if (info == null) return 0;
    //else return info.getAmountInt();    
    else return info.getAmountD();
  }

  /**
   * Get fragments of the stock
   *
   * @param symbol Security symbol
   *
   * @return
   */
  public final Vector<StockFragment> getSecurityFragments(String symbol)
  {
    StockInfo info = infos.get(symbol.toUpperCase());

    if (info == null) return null;
    else return info.fragments;
  }

  /**
   * Get type of the secutiry
   *
   * @param symbol Security symbol
   *
   * @return
   */
  public SecType getSecurityType(String symbol)
  {
    StockInfo info = infos.get(symbol.toUpperCase());

    if (info == null) throw new IllegalArgumentException("Security does not exist");

    return info.secType;
  }

  /**
   * Round price to hallers
   *
   * @param price
   *
   * @return Rounded price
   */
  public static double roundToHellers(double price)
  {
    return ((double)Math.round(price*100))/100;
  }

  /**
   * Create transactions that led to this state. I.e. all transactions (or their parts) that were
   * not yet completely closed.
   *
   * @param stock Stock name
   *
   * @return Set of transactions that creates this state
   */
  public TransactionSet buildStateTransactions() throws Exception
  {
    TransactionSet res = new TransactionSet();

    // Pass tickers
    for(String ticker : infos.keySet()) {
      StockInfo i = infos.get(ticker);

      // Pass fragments
      for(StockFragment f : i.fragments) {
        int direction = 0;

        switch(i.secType) {
          case STOCK:
            if (f.amount < 0) direction = Transaction.DIRECTION_SSELL;
            else direction = Transaction.DIRECTION_SBUY;
            break;
          case DERIVATE:
            if (f.amount < 0) direction = Transaction.DIRECTION_DSELL;
            else direction = Transaction.DIRECTION_DBUY;
            break;
          case CASH:
            if (f.amount < 0) direction = Transaction.DIRECTION_DSELL;
            else direction = Transaction.DIRECTION_DBUY;
            break;
        }

        res.addTransaction(f.opened, direction, ticker, Math.abs(f.amount), f.price, f.priceCurrency, f.fee, f.feeCurrency, f.market, f.opened,f.note);
      }
    }

    return res;
  }

  /**
   * Check for any open short positions. If position was opened in a year given as a parameter,
   * generate pseudo-close half-trade on December 31st with price 0. This is to make income
   * appear in the year position was opened though it is not closed yet.
   *
   * Note: The "closed" stock fragments are not removed, only "trades" are generated.
   *
   * @param year Year position must be opened in
   *
   * @return Array of trades, may be null
   */
  public StockTrade[] autocloseShortTransactions(int year) throws TradingException
  {
    Vector<StockTrade> trades = new Vector<StockTrade>();

    // Create date of Decemeber 31st
    GregorianCalendar cal = new GregorianCalendar(year, 12-1, 31);
    Date endOfYear = cal.getTime();

    // Pass tickers
    for(String ticker : infos.keySet()) {
      StockInfo i = infos.get(ticker);

      // Pass fragments
      for(StockFragment f : i.fragments) {
        if (f.amount < 0) {
          // Short-position fragment
          cal.setTime(f.opened);
          if (cal.get(GregorianCalendar.YEAR) == year) {
            /* In the correct year - generate pseudo - trade */
            HalfTrade open = new HalfTrade(f.opened, ticker, f.amount, f.price, f.fee, f.priceCurrency, f.feeCurrency);
            HalfTrade close = new HalfTrade(endOfYear, ticker, -f.amount, 0, 0, f.priceCurrency, f.feeCurrency);

            // Generate complete "trade"
            StockTrade st = new StockTrade(i.secType, open, close, f.getSplits(), f.getRenames());
            trades.add(st);
          }
        }
      }
    }

    // Make vector to an array
    if (trades.size() == 0) return null;

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
  public static boolean isOverTaxFreeDuration(Date d1, Date d2)
  {
    GregorianCalendar cal1 = new GregorianCalendar();
    GregorianCalendar cal2 = new GregorianCalendar();

    cal1.setTime(d1);
    cal2.setTime(d2);

    int mdiff = (cal2.get(GregorianCalendar.YEAR) - cal1.get(GregorianCalendar.YEAR))*12 + (cal2.get(GregorianCalendar.MONTH) - cal1.get(GregorianCalendar.MONTH));
    
    if (d1.before(TAX_FREE_DURATION_BOUNDARY)) {
        if (mdiff < 6) return false;
        if (mdiff > 6) return true;
    }
    else {
        if (mdiff < 3*12) return false;
        if (mdiff > 3*12) return true;        
    }

    return (cal2.get(GregorianCalendar.DAY_OF_MONTH) > cal1.get(GregorianCalendar.DAY_OF_MONTH));
  }

}
