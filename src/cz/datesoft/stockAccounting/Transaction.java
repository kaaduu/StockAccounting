/*
 * Transaction.java
 *
 * Created on 26. únor 2008, 21:00
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package cz.datesoft.stockAccounting;

import java.util.Date;
import java.util.GregorianCalendar;
import java.text.DecimalFormat;
import java.io.BufferedReader;
import java.io.PrintWriter;

/**
 * Transaction (buy or sell)
 */
public class Transaction implements java.lang.Comparable, java.io.Serializable
{
  /** Direction constants */
  public static final int DIRECTION_SBUY = 1;
  public static final int DIRECTION_SSELL = -1;
  public static final int DIRECTION_TRANS_ADD = 2;
  public static final int DIRECTION_TRANS_SUB = -2;
  public static final int DIRECTION_DBUY = 3;
  public static final int DIRECTION_DSELL = -3;
  public static final int DIRECTION_CBUY = 4;
  public static final int DIRECTION_CSELL = -4;
  public static final int DIRECTION_DIVI_BRUTTO = 10;
  public static final int DIRECTION_DIVI_NETTO15 = 11;
  public static final int DIRECTION_DIVI_TAX = 12;
  public static final int DIRECTION_DIVI_UNKNOWN = 13;
  
  /** Serial number - used in sorting when dates equal */
  int serial;
  
  /** Date */
  Date date;
  
  /** Direction */
  int direction;
  
  /** Ticker */
  String ticker;
    
  /** Amount. Amount is double because due to splits etc. there can be non-integer amount */
  Double amount;
  
  /** Price */
  Double price;
  
  /** Price currency */
  String priceCurrency;
  
  /** Fee */
  Double fee;
  
  /** Fee currency */
  String feeCurrency;
  
  /** Local double number format */
  DecimalFormat ff;
  
  /** Market */
  String market;
  
  /** Execution date */
  Date executionDate;

    /** Note */
  String note;
  
  /**
   * Clear milliseconds in the date (keep seconds).
   */
  private static Date clearSMS(Date d)
  {
    GregorianCalendar cal = new GregorianCalendar();
    
    cal.setTime(d);
    cal.set(GregorianCalendar.MILLISECOND,0);
    
    return cal.getTime();
  }
    
  /**
   * Create "empty" transaction, just with a serial
   */
  protected Transaction(int serial)
  {
    ff = new DecimalFormat("#.#######");
    
    this.serial = serial;
    
    this.date = null;
    this.direction = 0;
    this.ticker = null;    
    this.amount = null;
    this.price = null;
    this.priceCurrency = null;
    this.fee = null;
    this.feeCurrency = null;
    this.market = null;
    this.note = null;
  }
  
  /**
   * Create transaction from a file
   */
  protected Transaction(BufferedReader ifl)
  {
    ff = new DecimalFormat("#.#######");
    
    this.serial = 0;
    this.date = null;
    this.direction = 0;
    this.ticker = null;
    this.amount = null;
    this.price = null;
    this.priceCurrency = null;
    this.fee = null;
    this.feeCurrency = null;
    this.market = null;
    this.note = null;
    
    String a[];
    for(;;)
    {
      a = TransactionSet.readLine(ifl);
      
      if (a[0] == null) break; // Done - EOF
      if (a[0].equals(")")) break; // Done - end of row
      
      if (a[0].equals("serial")) this.serial = Integer.parseInt(a[1]);
      else if (a[0].equals("date")) this.date = TransactionSet.parseDate(a[1]);
      else if (a[0].equals("direction")) this.direction = Integer.parseInt(a[1]);
      else if (a[0].equals("ticker"))
      {
        if (a[1].length() > 0) this.ticker = a[1];
      }
      else if (a[0].equals("amount"))
      {
        if (a[1] != null)
          this.amount = Double.parseDouble(a[1]);
      }
      else if (a[0].equals("price"))
      {
        if (!a[1].equals("null")) this.price = Double.parseDouble(a[1]);
      }
      else if (a[0].equals("priceCurrency"))
      {
        if (!a[1].equals("null")) this.priceCurrency = a[1];
      }
      else if (a[0].equals("fee"))
      {
        if (!a[1].equals("null")) this.fee = Double.parseDouble(a[1]);
      }
      else if (a[0].equals("feeCurrency"))
      {
        if (!a[1].equals("null")) this.feeCurrency = a[1];
      }
      else if (a[0].equals("market"))
      {
        if (!a[1].equals("null")) this.market = a[1];
      }
      else if (a[0].equals("exDate"))
      {
        this.executionDate = TransactionSet.parseDate(a[1]);
      }
      else if (a[0].equals("note"))
      {
        if (!a[1].equals("null")) this.note = a[1];
      }
    }

    /*
    // Fix type
    if (this.market != null) {
      if (this.market.equalsIgnoreCase("--") ||
          this.market.equalsIgnoreCase("BOX") ||
          this.market.equalsIgnoreCase("ISE") ||
          this.market.equalsIgnoreCase("PSE") ||
          this.market.equalsIgnoreCase("PHLX") ||
          this.market.equalsIgnoreCase("NASDAQOM") ||
          this.market.equalsIgnoreCase("CBOE")) {
        if (this.direction == DIRECTION_SBUY) this.direction = DIRECTION_DBUY;
        else if (this.direction == DIRECTION_SSELL) this.direction = DIRECTION_DSELL;
      }
    }
     */
  }
  
  /**
   * Create new transaction with values
   */
  public Transaction(int serial, Date date, int direction, String ticker, double amount, double price, String priceCurrency, double fee, String feeCurrency, String market, Date executionDate,String note) throws Exception
  {
    ff = new DecimalFormat("#.#######");
    
    this.serial = serial;
    
    this.date = clearSMS(date);
    
    switch(direction)
    {
      case DIRECTION_SBUY:
      case DIRECTION_SSELL:
      case DIRECTION_TRANS_ADD:
      case DIRECTION_TRANS_SUB:
      case DIRECTION_DBUY:
      case DIRECTION_DSELL:
      case DIRECTION_CBUY:
      case DIRECTION_CSELL:
      case DIRECTION_DIVI_BRUTTO:
      case DIRECTION_DIVI_NETTO15:
      case DIRECTION_DIVI_TAX:
      case DIRECTION_DIVI_UNKNOWN:
        break;
      default:
        throw new Exception("Bad direction constant: "+direction);
    }
    this.direction = direction;
    
    this.ticker = ticker;
    this.amount = amount;
    this.price = price;
    this.priceCurrency = priceCurrency.toUpperCase();
    
    this.fee = fee;
    if (feeCurrency == null) this.feeCurrency = this.priceCurrency;
    else this.feeCurrency = feeCurrency.toUpperCase();
    
    this.market = market;
    this.executionDate = (executionDate == null) ? null : clearSMS(executionDate);
    this.note = note;
  }
  
  /**
   * Getters
   */
  public Date getDate()
  { return this.date; }
  public String getStringDate()
  {
    if (date == null) return null;
    java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
    cal.setTime(date);
    
    //return cal.get(GregorianCalendar.DAY_OF_MONTH)+"."+(cal.get(GregorianCalendar.MONTH)+1)+"."+cal.get(GregorianCalendar.YEAR)+" "+cal.get(GregorianCalendar.HOUR_OF_DAY)+":"+cal.get(GregorianCalendar.MINUTE);
    int sec = cal.get(GregorianCalendar.SECOND);
    if (sec != 0) {
      return cal.get(GregorianCalendar.DAY_OF_MONTH)+"."+(cal.get(GregorianCalendar.MONTH)+1)+"."+cal.get(GregorianCalendar.YEAR)+" "+cal.get(GregorianCalendar.HOUR_OF_DAY)+":"+String.format("%02d",cal.get(GregorianCalendar.MINUTE))+":"+String.format("%02d",sec);
    }
    return cal.get(GregorianCalendar.DAY_OF_MONTH)+"."+(cal.get(GregorianCalendar.MONTH)+1)+"."+cal.get(GregorianCalendar.YEAR)+" "+cal.get(GregorianCalendar.HOUR_OF_DAY)+":"+String.format("%02d",cal.get(GregorianCalendar.MINUTE));
    
  }

  public String getStringExecutionDate()
  {
    if (executionDate == null) return null;
    java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
    cal.setTime(executionDate);
    
    int sec = cal.get(GregorianCalendar.SECOND);
    if (sec != 0) {
      return cal.get(GregorianCalendar.DAY_OF_MONTH)+"."+(cal.get(GregorianCalendar.MONTH)+1)+"."+cal.get(GregorianCalendar.YEAR)+" "+cal.get(GregorianCalendar.HOUR_OF_DAY)+":"+String.format("%02d",cal.get(GregorianCalendar.MINUTE))+":"+String.format("%02d",sec);
    }
    return cal.get(GregorianCalendar.DAY_OF_MONTH)+"."+(cal.get(GregorianCalendar.MONTH)+1)+"."+cal.get(GregorianCalendar.YEAR)+" "+cal.get(GregorianCalendar.HOUR_OF_DAY)+":"+String.format("%02d",cal.get(GregorianCalendar.MINUTE));
  }

  public int getSerial()
  { return this.serial; }
  public Integer getDirection()
  { return direction; }
  public String getStringDirection()
  {
    switch(this.direction)
    {
      case DIRECTION_SBUY:
      case DIRECTION_DBUY:
      case DIRECTION_CBUY:
        return "Nákup";
      case DIRECTION_SSELL:
      case DIRECTION_DSELL:
      case DIRECTION_CSELL:
        return "Prodej";
      case DIRECTION_TRANS_ADD:
        return "Přidání";
      case DIRECTION_TRANS_SUB:
        return "Odebrání";
      case DIRECTION_DIVI_BRUTTO:
        return "Hrubá";
      case DIRECTION_DIVI_NETTO15:
        return "Čistá 15%";
      case DIRECTION_DIVI_TAX:
        return "Daň";
      case DIRECTION_DIVI_UNKNOWN:
        return "Neznámá";
      default:
        return null;
    }
  }
  public String getStringType()
  {
    switch(this.direction)
    {
      case DIRECTION_SBUY:
      case DIRECTION_SSELL:
        return "CP";
      case DIRECTION_TRANS_ADD:
      case DIRECTION_TRANS_SUB:
        return "Transformace";
      case DIRECTION_DBUY:
      case DIRECTION_DSELL:
        return "Derivát";
      case DIRECTION_CBUY:
      case DIRECTION_CSELL:
        return "Cash";
      case DIRECTION_DIVI_BRUTTO:
      case DIRECTION_DIVI_NETTO15:
      case DIRECTION_DIVI_TAX:
      case DIRECTION_DIVI_UNKNOWN:
        return "Dividenda";
      default:
        return null;
    }
  }
  public String getTicker()
  { return ticker; } 
  public Double getAmount()
  { return amount; }
  public Double getFee()
  { return fee;  }
  public String getFeeCurrency()
  { return feeCurrency; }
  public Double getPrice()
  { return price; }
  public String getPriceCurrency()
  { return priceCurrency; }
  public String getMarket()
  { return market; }
  public Date getExecutionDate()
  { return executionDate; }
  public String getNote()
  { return note; }
  
  /**
   * Setters
   */
  protected void setDate(Date date)
  { this.date = clearSMS(date); }
  public void setDirection(int direction)
  { this.direction = direction; }

  public void setType(String newDirection)
  {
    if (newDirection == null) return;

    if (newDirection.equalsIgnoreCase("CP")) {
      switch(direction) {
        case DIRECTION_DBUY:
        case DIRECTION_CBUY:
        case DIRECTION_SBUY:
        case DIRECTION_TRANS_ADD:
          direction = DIRECTION_SBUY;
          break;
        case DIRECTION_DSELL:
        case DIRECTION_CSELL:
        case DIRECTION_SSELL:
        case DIRECTION_TRANS_SUB:
          direction = DIRECTION_SSELL;
          break;
        default:
          direction = DIRECTION_SBUY;
      }
    }
    else if (newDirection.equalsIgnoreCase("Derivát")) {
      switch(direction) {
        case DIRECTION_DBUY:
        case DIRECTION_CBUY:
        case DIRECTION_SBUY:
        case DIRECTION_TRANS_ADD:
          direction = DIRECTION_DBUY;
          break;
        case DIRECTION_DSELL:
        case DIRECTION_CSELL:
        case DIRECTION_SSELL:
        case DIRECTION_TRANS_SUB:
          direction = DIRECTION_DSELL;
          break;
        default:
          direction = DIRECTION_DBUY;
      }
    }
    else if (newDirection.equalsIgnoreCase("Transformace")) {
      switch(direction) {
        case DIRECTION_DBUY:
        case DIRECTION_CBUY:
        case DIRECTION_SBUY:
        case DIRECTION_TRANS_ADD:
          direction = DIRECTION_TRANS_ADD;
          break;
        case DIRECTION_DSELL:
        case DIRECTION_CSELL:
        case DIRECTION_SSELL:
        case DIRECTION_TRANS_SUB:
          direction = DIRECTION_TRANS_SUB;
          break;
        default:
          direction = DIRECTION_TRANS_ADD;
      }
    }
    else if (newDirection.equalsIgnoreCase("Cash")) {
      switch(direction) {
        case DIRECTION_DBUY:
        case DIRECTION_CBUY:
        case DIRECTION_SBUY:
        case DIRECTION_TRANS_ADD:
          direction = DIRECTION_CBUY;
          break;
        case DIRECTION_DSELL:
        case DIRECTION_CSELL:
        case DIRECTION_SSELL:
        case DIRECTION_TRANS_SUB:
          direction = DIRECTION_CSELL;
          break;
        default:
          direction = DIRECTION_CBUY;
      }
    }
    else if (newDirection.equalsIgnoreCase("Dividenda")) {
      switch(direction) {
        case DIRECTION_DIVI_BRUTTO:
        case DIRECTION_DIVI_NETTO15:
        case DIRECTION_DIVI_TAX:
        case DIRECTION_DIVI_UNKNOWN:
          break;
        default:
          direction = DIRECTION_DIVI_UNKNOWN;
      }
    }
  }

  /**
   * Get possible directions
   */
  public String[] getPossibleDirections()
  {
    switch(this.direction) {
      case DIRECTION_SBUY:
      case DIRECTION_SSELL:
      case DIRECTION_DBUY:
      case DIRECTION_DSELL:
      case DIRECTION_CBUY:
      case DIRECTION_CSELL:
        String[] a = { "Nákup", "Prodej" };
        return a;
      case DIRECTION_TRANS_ADD:
      case DIRECTION_TRANS_SUB:
        String[] b = { "Přidání", "Odebrání" };
        return b;
      case DIRECTION_DIVI_BRUTTO:
      case DIRECTION_DIVI_NETTO15:
      case DIRECTION_DIVI_TAX:
      case DIRECTION_DIVI_UNKNOWN:
        String[] c = { "Hrubá", "Čistá 15%", "Daň", "Neznámá" };
        return c;
      default:
        String[] z = { };
        return z;
    }
  }

  public void setDirection(String direction)
  {
    if (direction == null) return;

    switch(this.direction) {
      case DIRECTION_SBUY:
      case DIRECTION_SSELL:
        if (direction.equalsIgnoreCase("Nákup")) this.direction = DIRECTION_SBUY;
        else if (direction.equalsIgnoreCase("Prodej")) this.direction = DIRECTION_SSELL;
        break;
      case DIRECTION_DBUY:
      case DIRECTION_DSELL:
        if (direction.equalsIgnoreCase("Nákup")) this.direction = DIRECTION_DBUY;
        else if (direction.equalsIgnoreCase("Prodej")) this.direction = DIRECTION_DSELL;
        break;
      case DIRECTION_CBUY:
      case DIRECTION_CSELL:
        if (direction.equalsIgnoreCase("Nákup")) this.direction = DIRECTION_CBUY;
        else if (direction.equalsIgnoreCase("Prodej")) this.direction = DIRECTION_CSELL;
        break;
      case DIRECTION_TRANS_ADD:
      case DIRECTION_TRANS_SUB:
        if (direction.equalsIgnoreCase("Přidání")) this.direction = DIRECTION_TRANS_ADD;
        else if (direction.equalsIgnoreCase("Odebrání")) this.direction = DIRECTION_TRANS_SUB;
        break;
      case DIRECTION_DIVI_BRUTTO:
      case DIRECTION_DIVI_NETTO15:
      case DIRECTION_DIVI_TAX:
      case DIRECTION_DIVI_UNKNOWN:
        if (direction.equalsIgnoreCase("Hrubá")) this.direction = DIRECTION_DIVI_BRUTTO;
        else if (direction.equalsIgnoreCase("Čistá 15%")) this.direction = DIRECTION_DIVI_NETTO15;
        else if (direction.equalsIgnoreCase("Daň")) this.direction = DIRECTION_DIVI_TAX;
        else if (direction.equalsIgnoreCase("Neznámá")) this.direction = DIRECTION_DIVI_UNKNOWN;
        break;
    }
  }
  public void setSerial(int serial)
  { this.serial = serial; }
  public void setTicker(String ticker)
  { this.ticker = ticker; }
  public void setAmount(Double amount)
  { this.amount = amount; }
  public void setFee(Double fee)
  { this.fee = fee; }
  public void setFeeCurrency(String feeCurrency)
  { this.feeCurrency = feeCurrency; }
  public void setPrice(Double price)
  { this.price = price; }
  public void setPriceCurrency(String priceCurrency)
  { this.priceCurrency = priceCurrency; }
  public void setMarket(String market)
  { this.market = market; }
  public void setExecutionDate(Date executionDate)
  { this.executionDate = (executionDate == null) ? null : clearSMS(executionDate); }
  public void setNote(String note)
  { this.note = note; }
  
   /**
    * Update fields from another transaction (used for re-import updates)
    * Updates: Note, Fee, FeeCurrency, ExecutionDate
    * Does NOT update business key fields: Date, Direction, Ticker, Amount, Price, PriceCurrency, Market
    */
   public void updateFromTransaction(Transaction source) {
      this.setNote(source.getNote());
      this.setFee(source.getFee());
      this.setFeeCurrency(source.getFeeCurrency());
      this.setExecutionDate(source.getExecutionDate());
   }

   /**
    * Update fields from another transaction (used for re-import updates) when TxnID match is used.
    *
    * In this mode we can safely update the timestamp to include missing seconds,
    * because identity is anchored by TxnID.
    */
   public void updateFromTransactionWithTxnIdMatch(Transaction source) {
     // Keep existing behavior
     updateFromTransaction(source);

     // Also update trade timestamp
     this.setDate(source.getDate());
   }

   /**
    * Parse structured note field and extract metadata
    * Format: Description|Broker:VALUE|AccountID:VALUE|TxnID:VALUE|Code:VALUE
    * Returns map with keys: broker, accountId, txnId, code
    */
   public static java.util.Map<String, String> parseNoteMetadata(String note) {
     java.util.Map<String, String> metadata = new java.util.HashMap<>();

     if (note == null || note.isEmpty()) {
       return metadata;
     }

     // Split by pipe delimiter
     String[] parts = note.split("\\|");

     for (String part : parts) {
       if (part.contains(":")) {
         String[] keyValue = part.split(":", 2);
         String key = keyValue[0].trim().toLowerCase();
         String value = keyValue.length > 1 ? keyValue[1].trim() : "";

         switch (key) {
           case "broker":
             metadata.put("broker", value);
             break;
           case "accountid":
             metadata.put("accountId", value);
             break;
           case "txnid":
             metadata.put("txnId", value);
             break;
           case "code":
             metadata.put("code", value);
             break;
         }
       }
     }

     return metadata;
   }

   /**
    * Get broker from note field
    */
   public String getBroker() {
     return parseNoteMetadata(note).getOrDefault("broker", "");
   }

   /**
    * Get account ID from note field
    */
   public String getAccountId() {
     return parseNoteMetadata(note).getOrDefault("accountId", "");
   }

   /**
    * Get transaction ID from note field
    */
   public String getTxnId() {
     return parseNoteMetadata(note).getOrDefault("txnId", "");
   }

    /**
     * Get effect description for derivatives based on code
     * A = Assignment, Ex = Exercise, Ep = Expired
     * Supports multiple codes separated by commas
     * Only applies to derivative transactions (DIRECTION_DBUY, DIRECTION_DSELL)
     */
    public String getEffect() {
      // Only show effect for derivatives
      if (direction != DIRECTION_DBUY && direction != DIRECTION_DSELL) {
        return "";
      }

      String codeString = parseNoteMetadata(note).getOrDefault("code", "");
      if (codeString.isEmpty()) {
        return "";
      }

      // Split by semicolon and process each code (format: Code:A;C or Code:C;Ep)
      String[] codes = codeString.split(";");
      java.util.List<String> effects = new java.util.ArrayList<>();

      for (String code : codes) {
        String trimmedCode = code.trim();
        String effect = "";
        switch (trimmedCode) {
          case "A":
            effect = "Assignment";
            break;
          case "Ex":
            effect = "Exercise";
            break;
          case "Ep":
            effect = "Expired";
            break;
          default:
            // Ignore unknown codes
            continue;
        }
        if (!effect.isEmpty()) {
          effects.add(effect);
        }
      }

      // Return comma-separated effects or empty string
      return String.join(", ", effects);
    }

   /**
    * Compare function
   */
  public int compareTo(Object o)
  {
    int res;
    
    Transaction tx = (Transaction)o;
    
    // Compare dates
    res = date.compareTo(tx.getDate());
    if (res != 0) return res;
    
    // Compare serials
    if (serial < tx.getSerial()) return -1;
    else return 1;
  }
  
  /**
   * Return whether transaction is completely filled in
   */
  public boolean isFilledIn()
  {
    switch(direction) {
      case DIRECTION_TRANS_ADD:
      case DIRECTION_TRANS_SUB:
        // We need not price & price currency for transformations
        return ((serial != 0) && (date != null) && (direction != 0) && (ticker != null) &&
         (amount != null));
      default:
        return ((serial != 0) && (date != null) && (direction != 0) && (ticker != null) &&
         (amount != null) && (price != null) && (priceCurrency != null));
    }
  }
  
  public void save(PrintWriter ofl, String prefix) throws java.io.IOException
  {
    GregorianCalendar cal = new GregorianCalendar();
    
    cal.setTime(date);
    
    ofl.println(prefix+"serial="+serial);
    ofl.println(prefix+"date="+getStringDate());
    ofl.println(prefix+"direction="+direction);
    ofl.println(prefix+"ticker="+ticker);
    ofl.println(prefix+"amount="+amount);
    ofl.println(prefix+"price="+price);
    ofl.println(prefix+"priceCurrency="+priceCurrency);
    ofl.println(prefix+"fee="+fee);
    ofl.println(prefix+"feeCurrency="+feeCurrency);
    ofl.println(prefix+"market="+market);
    ofl.println(prefix+"exDate="+getStringExecutionDate());
    ofl.println(prefix+"note="+note);
  }
  
  public void export(PrintWriter ofl) throws java.io.IOException
  {
    //ofl.println(getStringDate()+";"+getStringType()+";"+getStringDirection()+";"+ticker+";"+amount+";"+ff.format(price)+";"+priceCurrency+";"+ff.format(fee)+";"+feeCurrency+";"+market+";"+getStringExecutionDate()+";"+note);
      ofl.println(getStringDate()+";"+getStringType()+";"+getStringDirection()+";"+ticker+";"+amount+";"+price+";"+priceCurrency+";"+fee+";"+feeCurrency+";"+market+";"+getStringExecutionDate()+";"+getBroker()+";"+getAccountId()+";"+getTxnId()+";"+getEffect()+";"+note);
  }
  
    public void exportFIO(PrintWriter ofl) throws java.io.IOException
  {
      // Export je pro kacka.baldsoft.com a ta nepodporuje derivaty ani transformace takze pouze exportuje Cenne papiry
      if (getStringType().equals("CP")) {
          // price 10.22 but FIO use czech locale we need 10,22 how  silly
          String price_s = String.valueOf(price).replace(".", ",");
          //if (fee>0) { String fee_s=String.valueOf(fee).replace(".",",");} else { String fee_s="";}
          // getStringDirection() Nakup (-amount) vs Prodej(+amount)
          int sign = 0;
          if (getStringDirection().equals("Nákup")) {
              sign = -1;
          } else {
              sign = 1;
          }

          // Get fee
          String ObjemUSD = "";
          String ObjemCZK = "";
          String ObjemEUR = "";
          if (priceCurrency.equals("USD")) {
              ObjemUSD = String.valueOf(ff.format(price * amount * sign)).replace(".", ",");
          }
          if (priceCurrency.equals("EUR")) {
              ObjemEUR = String.valueOf(ff.format(price * amount * sign)).replace(".", ",");
          }
          if (priceCurrency.equals("CZK")) {
              ObjemCZK = String.valueOf(ff.format(price * amount * sign)).replace(".", ",");
          }

          String PoplatkyUSD = "";
          String PoplatkyCZK = "";
          String PoplatkyEUR = "";
          switch (feeCurrency) {
              case "USD":
                  PoplatkyUSD = String.valueOf(fee).replace(".", ",");
                  break;
              case "EUR":
                  PoplatkyEUR = String.valueOf(fee).replace(".", ",");
                  break;
              case "CZK":
                  PoplatkyCZK = String.valueOf(fee).replace(".", ",");
                  break;
              default:
        ;
          }
          String ExeDate[] = getStringExecutionDate().split(" ", 0);
          String amount_s = String.valueOf(Math.abs(amount)).replace(".",",");
          String Text = getStringDate() + ";" + getStringDirection() + ";" + ticker + ";" + price_s + ";" + amount_s + ";" + priceCurrency + ";" + ObjemCZK + ";" + PoplatkyCZK + ";" + ObjemUSD + ";" + PoplatkyUSD + ";" + ObjemEUR + ";" + PoplatkyEUR + ";" + note + ";" + ExeDate[0];
          //String CP1250 = new String(Text.getBytes("Windows-1250"), "UTF-8");

          //ofl.println(getStringDate()+";"+getStringDirection()+";"+ticker+";"+price_s+";"+(int)Math.abs(amount)+";"+priceCurrency+";"+ObjemCZK+";"+PoplatkyCZK+";"+ObjemUSD+";"+PoplatkyUSD+";"+ObjemEUR+";"+PoplatkyEUR+";"+note+";"+ExeDate[0]);
          ofl.println(Text);
      } //is CP only
  } 

}

