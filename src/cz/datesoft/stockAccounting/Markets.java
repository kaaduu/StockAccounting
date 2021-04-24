/*
 * Markets.java
 *
 * Created on 23. prosinec 2006, 9:27
 *
 * Classes with information about markets.
 */

package cz.datesoft.stockAccounting;

import java.util.TreeSet;
import java.util.TreeMap;
import java.lang.StringBuilder;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Class with information abotu markets
 * 
 */
public class Markets extends javax.swing.DefaultListModel
{
  
  /**
   * Info about one market
   */
  public class Market extends javax.swing.DefaultListModel implements Comparable
  {
    /**
     * Info about holiday
     */
    public class Holiday implements Comparable
    {
      /** Day of month when holiday takes place */
      public int day;
      
      /** Month when holiday takes place */
      public int month;
      
      /** Year when holiday started (or 0) */
      public int yearFrom;
      
      /** Year when holiday stopped (or 0) */
      public int yearTo;
      
      /**
       * Return day ID
       */
      public int dayID()
      {
        return month*100+day;
      }
      
      /**
       * Make string representation
       */
      public String toString()
      {
        StringBuilder s = new StringBuilder();
        DecimalFormat fmt = new DecimalFormat("00");
        
        s.append(fmt.format(day));
        s.append(". ");
        s.append(fmt.format(month));
        s.append(".");
        
        if (yearFrom > 0) {
          if (yearTo > 0) {
            s.append(' ');
            s.append(fmt.format(yearFrom));
            if (yearFrom != yearTo) {
              s.append('-');
              s.append(fmt.format(yearTo));
            }
          }
          else {
            s.append(" (od ");
            s.append(fmt.format(yearFrom));
            s.append(')');
          }
        }
        else if (yearTo > 0) {
            s.append(" (do ");
            s.append(fmt.format(yearTo));
            s.append(')');          
        }
        
        return s.toString();
      }

      public int compareTo(Object o)
      {
        Holiday h = (Holiday)o;
        
        return dayID() - h.dayID();
      }
      
      /**
       * Constructor from values
       */
      public Holiday(int day, int month, int yearFrom, int yearTo)
      {
        this.day = day;
        this.month = month;
        this.yearFrom = yearFrom;
        this.yearTo = yearTo;
      }
      
      /**
       * Constructor from saved settings
       */
      public Holiday(int version, String settings)
      {
        String[] a = settings.split("\t");
        
        day = Integer.parseInt(a[0]);
        month = Integer.parseInt(a[1]);
        yearFrom = Integer.parseInt(a[2]);
        yearTo = Integer.parseInt(a[3]);
      }
      
      /**
       * Generate string for saving
       */
      public String saveString()
      {
        return day+"\t"+month+"\t"+yearFrom+"\t"+yearTo;
      }
    }
    
    /**
     * Market name
     */
    private String _name;
    
    /**
     * Holidays we have
     */
    private TreeSet<Holiday> _holidays;
    
    /**
     * Number of days to the time when the trade is really executed
     */
    private int _tradeDelay;
    
    /**
     * Constuctor
     */
    protected Market(String name, int tradeDelay)
    {
      _name = name;
      _tradeDelay = tradeDelay;
      _holidays = new TreeSet<Holiday>();
      
    }
    
    /**
     * Constructs market data from settings
     */
    protected Market(int version, String settings)
    {
      _holidays = new TreeSet<Holiday>();
      
      String a[] = settings.split("\r");
      _name = a[0];
      _tradeDelay = Integer.parseInt(a[1]);
      for(int i=2;i<a.length;i++) {
        _holidays.add(new Holiday(version,a[i]));
      }
    }
    
    public Object getElementAt(int index)
    {
      return _holidays.toArray()[index];
    }
    
    public int getSize()
    {
      return _holidays.size();
    }

    /**
     * Determine position
     */
    public int getHolidayPosition(Holiday h)
    {
      int idx = 0;
      
      for(Iterator<Holiday> i=_holidays.iterator();i.hasNext();) {
        if (i.next() == h) break;
        idx++;
      }
      
      return idx;
    }
    
    /**
     * Add new holiday
     */
    public void addHoliday(int day, int month, int yearFrom, int yearTo)
    {
      Holiday h = new Holiday(day, month, yearFrom, yearTo);
      _holidays.add(h);
      
      int idx = getHolidayPosition(h);
      
      fireIntervalAdded(this, idx, idx);
    }
    
    /**
     * Remove holiday
     */
    public void remove(Holiday holiday)
    {
      int idx = getHolidayPosition(holiday);
      
      _holidays.remove(holiday);
      
      fireIntervalRemoved(this, idx, idx);
    }
    
    public void holidayUpdated(Holiday holiday)
    {
      int idx = getHolidayPosition(holiday);
      
      fireContentsChanged(this, idx, idx);
    }
    
    /**
     * Return name
     */
    public String getName()
    {
      return _name;
    }
    
    /**
     * Set name (protected - only Markets can do it)
     */
    protected void setName(String name)
    {
      _name = name;
    }
    
    /**
     * Get trade delay
     */
    public int getTradeDelay()
    {
      return _tradeDelay;
    }
    
    /**
     * Set trade delay
     */
    public void setTradeDelay(int tradeDelay)
    {
      _tradeDelay = tradeDelay;
    }
    
    /**
     * Convert to string - return name
     */
    public String toString()
    {
      return _name;
    }
    
    public int compareTo(Object o)
    {
      Market m = (Market)o;
      
      return _name.compareToIgnoreCase(m.getName());
    }
    
    /**
     * Generate string for saving
     */
    public String saveString()
    {
      StringBuilder b = new StringBuilder();
      
      b.append(_name);
      b.append("\r");
      b.append(_tradeDelay);
      for(Iterator<Holiday> i=_holidays.iterator();i.hasNext();) {
        b.append("\r");
        b.append(i.next().saveString());
      }
      
      return b.toString();
    }
    
    /**
     * Determine execution date for transaction that took place on some date
     */
    public Date executionDate(Date tradeDate)
    {
      if (_tradeDelay == 0) return tradeDate; // No delay
      
      GregorianCalendar cal = new GregorianCalendar();
      
      cal.setTime(tradeDate);

      int daysToGo = _tradeDelay;
      while(daysToGo > 0) {
        cal.add(cal.DAY_OF_MONTH,1);
        
        int wday = cal.get(cal.DAY_OF_WEEK);
        if ((wday != cal.SATURDAY) && (wday != cal.SUNDAY)) {
            // Check if this is holiday
            int day = cal.get(cal.DAY_OF_MONTH);
            int month = cal.get(cal.MONTH)+1;
            int year = cal.get(cal.YEAR);
            
            for(Iterator<Holiday> i=_holidays.iterator();i.hasNext();) {
              Holiday h = i.next();
              if ((day == h.day) && (month == h.month)) {
                if (((h.yearFrom == 0) || (year >= h.yearFrom)) &&
                    ((h.yearTo == 0) || (year <= h.yearTo))) {
                  // We got holiday - continue to next day
                  continue;
                }
              }
            }
        }
        
        // OK, no weekend, no holiday
        daysToGo--;
      }
      
      return cal.getTime();
    }
  }
  
  TreeMap<String,Market> _markets;
  
  /** Creates a new instance of Markets */
  public Markets()
  {
    _markets = new TreeMap<String,Market>();
  }
  
  /**
   * Creates new instance from saved settings
   */
  public Markets(String settings)
  {
    _markets = new TreeMap<String,Market>();
    
    if (settings.length() == 0) return; // No settings to load
    
    String[] a = settings.split("\n");
    
    int version = Integer.parseInt(a[0]);
    if (version != 1) {
      throw new IllegalArgumentException("Špatná verze nastavení!");
    }
    
    for(int i=1;i<a.length;i++) {
      Market m = new Market(version,a[i]);
      _markets.put(m.getName(),m);
    }
  }
  
  /**
   * Determine at which position is market with this name
   */
  private int getMarketPosition(String marketName)
  {
    int idx = 0;
    
    for(Iterator<String> i=_markets.keySet().iterator();i.hasNext();) {
      if (i.next().equals(marketName)) break;
      idx++;
    }
    
    return idx;
  }
  
  /**
   * Add market
   */
  public void addMarket(String marketName, int marketDelay)
  {
    if (_markets.get(marketName) != null) {
      throw new java.lang.IllegalArgumentException("Duplicitní jméno trhu!");
    }
    
    Market m = new Market(marketName, marketDelay);
    
    _markets.put(marketName.toUpperCase(),m);
    
    // Determine at which position the market was inserted
    int idx = getMarketPosition(marketName);
    fireIntervalAdded(this,idx,idx);
  }
    
  /**
   * Remove market
   */
  public void removeMarket(Market market)
  {
    int idx = getMarketPosition(market.getName());
    _markets.remove(market.getName());
    
    fireIntervalRemoved(this,idx,idx);
  }
  
  /**
   * Change market name
   */
  public void setMarketName(Market market, String newName)
  {
    removeMarket(market);
    
    market.setName(newName);
    
    _markets.put(newName.toUpperCase(),market);
    
    // Determine at which position the market was inserted
    int idx = getMarketPosition(newName);
    fireIntervalAdded(this,idx,idx);
  }
  
  /**
   * Get Market by name
   */
  public Market get(String name)
  {
    return _markets.get(name.toUpperCase());
  }

  public int getSize()
  {
    return _markets.size();
  }
  
  public Object getElementAt(int index)
  {
    return _markets.values().toArray()[index];
  }
  
  /**
   * Return data as string
   */
  public String saveString()
  {
    StringBuilder b = new StringBuilder();
    
    b.append("1");
    
    for(Iterator<Market> i=_markets.values().iterator();i.hasNext();) {
      b.append("\n");
      b.append(i.next().saveString());
    }
    
    return b.toString();
  }
}
