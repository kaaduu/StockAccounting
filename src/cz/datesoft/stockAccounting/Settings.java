/*
 * Settings.java
 *
 * Created on 22. říjen 2006, 14:03
 *
 * Governs miscellaneous settings.
 */
package cz.datesoft.stockAccounting;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.prefs.Preferences;
import java.util.GregorianCalendar;
import java.util.Date;
import java.util.Map;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.io.*;

/**
 *
 * @author lemming2
 */
public class Settings {
  /**
   * Currency ratio class
   */
  public static class CurrencyRatio implements java.lang.Comparable {
    /**
     * Currency this is for
     */
    private String currency;

    /**
     * Year this ratio is for
     */
    private int year;

    /**
     * Ratio
     */
    private double ratio;

    /**
     * Constructor
     */
    public CurrencyRatio(String currency, int year, double ratio) {
      this.currency = currency.toUpperCase();
      this.year = year;
      this.ratio = ratio;
    }

    /**
     * Ratio setter
     */
    public void setRatio(double ratio) {
      this.ratio = ratio;
    }

    /**
     * Getters
     */
    public String getCurrency() {
      return currency;
    }

    public int getYear() {
      return year;
    }

    public double getRatio() {
      return ratio;
    }

    /**
     * Comparator
     */
    public int compareTo(Object o) {
      CurrencyRatio r = (CurrencyRatio) o;

      if (r.getYear() < year)
        return -1;
      else if (r.getYear() > year)
        return 1;

      return currency.compareTo(r.getCurrency());
    }
  }

  /**
   * Ratios
   */
  private static SortedSet<CurrencyRatio> ratios;

  /**
   * Ratio cache valid
   */
  private static boolean ratioCacheValid;

  /**
   * Ratio cache (currency|year => ratio map)
   */
  private static HashMap<String, Double> ratioCache;

  /**
   * What is "half year"
   */
  public static final int HY_6M = 6;
  public static final int HY_183D = 183;
  private static int halfYear = HY_6M;

  /**
   * Year to compute
   */
  private static int computeYear;

  /**
   * Settings dialog
   */
  private static SettingsWindow dialog;

  /**
   * Markets
   */
  private static Markets markets;

  /**
   * Selected index in "over tax free duration" combo
   */
  private static int overTaxFreeDuration;

  /**
   * Selected index in "what to do with no income trades" combo
   */
  private static int noIncomeTrades;

  /**
   * Allow short trades over year's boundary
   */
  private static boolean allowShortOverYearBoundary;

  /**
   * Separate currency into a separate column in CSV export
   */
  private static boolean separateCurrencyInCSVExport;

  /**
   * File directory
   */
  private static String dataDirectory;

  /**
   * Import directory
   */
  private static String importDirectory;

  /**
   * Use daily exchange rates instead of unified rate
   */
  private static boolean useDailyRates;

  /**
   * Daily exchange rates map (currency|YYYY-MM-DD => ratio map)
   */
  private static HashMap<String, Double> dailyRates;

  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

  /**
   * Show settings dialog
   */
  public synchronized static void showDialog() {
    // Create dialog if it is not already created
    if (dialog == null)
      dialog = new SettingsWindow(Main.getMainWindow(), true);

    dialog.showDialog();
  }

  /**
   * "Half year" getter
   */
  public static int getHalfYear() {
    return halfYear;
  }

  /**
   * "Half year" setter
   */
  public static void setHalfYear(int iHalfYear) {
    switch (halfYear) {
      case HY_6M:
      case HY_183D:
        halfYear = iHalfYear;
        break;
    }
  }

  /**
   * Ratios getter
   */
  public static SortedSet<CurrencyRatio> getRatios() {
    return ratios;
  }

  /**
   * Ratios setter (used by dialog, so we set all currencies at once)
   */
  public static void setRatios(SortedSet<CurrencyRatio> iRatios) {
    ratios = iRatios;
    ratioCacheValid = false;
  }

  /**
   * Refres ratio cache
   */
  private static void refreshRatioCache() {
    ratioCache = new HashMap<String, Double>();
    CurrencyRatio r;

    for (Iterator i = ratios.iterator(); i.hasNext();) {
      r = (CurrencyRatio) i.next();

      ratioCache.put(r.getCurrency() + "|" + r.getYear(), Double.valueOf(r.getRatio()));
    }

    ratioCacheValid = true;
  }

  /**
   * Get ratio for given year & currency
   */
  public static double getRatio(String currency, int year) throws java.lang.IllegalArgumentException {
    Double d;

    if (currency.equalsIgnoreCase("CZK"))
      return 1;

    if (!ratioCacheValid)
      refreshRatioCache();

    d = ratioCache.get(currency.toUpperCase() + "|" + year);

    if (d == null)
      throw new java.lang.IllegalArgumentException("Kurs měny " + currency + " pro rok " + year + " není zadán!");

    return d.doubleValue();
  }

  /**
   * Get compute year
   */
  public static int getComputeYear() {
    return computeYear;
  }

  /**
   * Set compute year
   */
  public static void setComputeYear(int value) {
    computeYear = value;
  }

  /**
   * Get markets
   */
  public static Markets getMarkets() {
    return markets;
  }

  /**
   * Set markets
   */
  public static void setMarkets(Markets newMarkets) {
    markets = newMarkets;
  }

  /* Getters */
  public static int getOverTaxFreeDuration() {
    return overTaxFreeDuration;
  }

  public static int getNoIncomeTrades() {
    return noIncomeTrades;
  }

  public static boolean getAllowShortOverYearBoundary() {
    return allowShortOverYearBoundary;
  }

  public static boolean getSeparateCurrencyInCSVExport() {
    return separateCurrencyInCSVExport;
  }

  public static String getDataDirectory() {
    return dataDirectory;
  }

  public static String getImportDirectory() {
    return importDirectory;
  }

  /* Setters */
  public static void setOverTaxFreeDuration(int value) {
    overTaxFreeDuration = value;
  }

  public static void setNoIncomeTrades(int value) {
    noIncomeTrades = value;
  }

  public static void setAllowShortOverYearBoundary(boolean value) {
    allowShortOverYearBoundary = value;
  }

  public static void setSeparateCurrencyInCSVExport(boolean value) {
    separateCurrencyInCSVExport = value;
  }

  public static void setDataDirectory(String value) {
    dataDirectory = value;
  }

  public static void setImportDirectory(String value) {
    importDirectory = value;
  }

  /**
   * Use daily exchange rates getter/setter
   */
  public static boolean getUseDailyRates() {
    return useDailyRates;
  }

  public static void setUseDailyRates(boolean value) {
    useDailyRates = value;
  }

  /**
   * Get daily rates map
   */
  public static HashMap<String, Double> getDailyRates() {
    return dailyRates;
  }

  /**
   * Set daily rates map
   */
  public static void setDailyRates(HashMap<String, Double> iDailyRates) {
    dailyRates = iDailyRates;
  }

  /**
   * Get exchange rate for given currency and date.
   * If useDailyRates is enabled, tries to find daily rate, otherwise uses unified
   * rate.
   */
  public static double getExchangeRate(String currency, Date date) {
    if (currency == null || currency.equalsIgnoreCase("CZK"))
      return 1.0;

    if (useDailyRates && dailyRates != null) {
      // Try current date, then look back up to 7 days for weekends/holidays
      GregorianCalendar lookbackCal = new GregorianCalendar();
      lookbackCal.setTime(date);

      for (int i = 0; i < 7; i++) {
        String key = currency.toUpperCase() + "|" + dateFormat.format(lookbackCal.getTime());
        Double rate = dailyRates.get(key);
        if (rate != null)
          return rate.doubleValue();

        // Not found, try previous day
        lookbackCal.add(Calendar.DAY_OF_MONTH, -1);
      }

      // If we're here, no daily rate found in last 7 days
      // System.err.println("Warning: Daily rate for " + currency + " at " +
      // dateFormat.format(date) + " or previous 7 days missing, falling back to
      // unified rate.");
    }

    // Default to unified rate (jednotný kurz)
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(date);
    try {
      return getRatio(currency, cal.get(Calendar.YEAR));
    } catch (Exception e) {
      // If unified rate is also missing, we can't do much
      throw new java.lang.IllegalArgumentException(
          "Kurs měny " + currency + " pro datum " + dateFormat.format(date) + " není zadán (ani denní, ani jednotný)!");
    }
  }

  /**
   * Load daily rates from data directory
   */
  private static void loadDailyRates() {
    dailyRates = new HashMap<String, Double>();
    File file = new File(dataDirectory == null ? "." : dataDirectory, "daily_rates.dat");
    if (!file.exists())
      return;

    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\\|");
        if (parts.length == 2) {
          try {
            dailyRates.put(parts[0], Double.valueOf(Double.parseDouble(parts[1])));
          } catch (NumberFormatException e) {
            // Skip invalid numbers
          }
        }
      }
    } catch (Exception e) {
      System.err.println("Error loading daily rates: " + e.getMessage());
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
        }
      }
    }
  }

   /**
    * Save daily rates to data directory
    */
   public static void saveDailyRates() {
     if (dailyRates == null)
       return;
     File file = new File(dataDirectory == null ? "." : dataDirectory, "daily_rates.dat");

     PrintWriter writer = null;
     try {
       writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
       for (Map.Entry<String, Double> entry : dailyRates.entrySet()) {
         writer.println(entry.getKey() + "|" + entry.getValue());
       }
     } catch (Exception e) {
       System.err.println("Error saving daily rates: " + e.getMessage());
     } finally {
       if (writer != null) {
         writer.close();
       }
     }
   }

   /**
    * Save only daily rates immediately (like unified rates auto-save)
    */
   public static void saveDailyRatesImmediately() {
     if (dailyRates != null && !dailyRates.isEmpty()) {
       saveDailyRates(); // Call existing save method
     }
   }



  /**
   * Load settings
   */
  public static void load() {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    String s, a[], b[];
    int i, n;

    // Load half year
    s = p.get("halfYear", "6M");
    if (s.equalsIgnoreCase("183D"))
      halfYear = HY_183D;

    // Load ratios
    ratios = new TreeSet<CurrencyRatio>();
    s = p.get("ratios",
        "EUR|2020|26.50\nUSD|2020|23.14\nCAD|2020|17.23\nGBP|2020|29.800\n" +
            "EUR|2019|25.66\nUSD|2019|22.93\nGBP|2019|29.310\n" +
            "EUR|2018|25.68\nUSD|2018|21.78\nGBP|2018|28.980\n");

    // System.out.println(s);
    a = s.split("\n");
    for (i = 0; i < a.length; i++) {
      b = a[i].split("\\|");
      try {
        CurrencyRatio r = new CurrencyRatio(b[0], Integer.parseInt(b[1]), Double.parseDouble(b[2]));
        ratios.add(r);
      } catch (Exception e) {
      } // Just catch badly formatted data
    }

    // Load year to compute
    s = p.get("computeYear", null);
    if (s == null) {
      // Default compute year to year before 3 months
      GregorianCalendar cal = new GregorianCalendar();
      cal.add(GregorianCalendar.MONTH, -3);

      computeYear = cal.get(GregorianCalendar.YEAR);
    } else
      computeYear = Integer.parseInt(s);

    markets = new Markets(p.get("markets", ""));

    /* overtaxFreeDuration settings */
    s = p.get("overTaxFreeDuration", "0");
    try {
      overTaxFreeDuration = Integer.parseInt(s);
    } catch (Exception e) {
      overTaxFreeDuration = 0;
    }

    /* no income trades settings */
    s = p.get("noIncomeTrades", "0");
    try {
      noIncomeTrades = Integer.parseInt(s);
    } catch (Exception e) {
      noIncomeTrades = 0;
    }

    /* shorts over year boundary */
    s = p.get("allowShortOverYearBoundary", "false");
    try {
      allowShortOverYearBoundary = Boolean.parseBoolean(s);
    } catch (Exception e) {
      allowShortOverYearBoundary = false;
    }

    /* separate currency in CSV export */
    s = p.get("separateCurrencyInCSVExport", "false");
    try {
      separateCurrencyInCSVExport = Boolean.parseBoolean(s);
    } catch (Exception e) {
      separateCurrencyInCSVExport = false;
    }

    /* Data directory */
    dataDirectory = p.get("dataDirectory", "");
    if (dataDirectory.length() == 0)
      dataDirectory = null;

    /* Import directory */
    importDirectory = p.get("importDirectory", "");
    if (importDirectory.length() == 0)
      importDirectory = null;

    /* Use daily rates */
    useDailyRates = p.getBoolean("useDailyRates", false);

    /* Load daily rates */
    loadDailyRates();
  }

  /**
   * Save settings
   */
  public static void save() {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    Iterator<CurrencyRatio> i;
    StringBuffer s = new StringBuffer();

    // Save half year
    p.put("halfYear", (halfYear == HY_6M) ? "6M" : "183D");

    // Save ratios
    for (i = ratios.iterator(); i.hasNext();) {
      if (s.length() > 0)
        s.append("\n");
      CurrencyRatio r = i.next();
      s.append(r.getCurrency() + "|" + r.getYear() + "|" + r.getRatio());
    }
    p.put("ratios", s.toString());

    // Save compute year
    p.put("computeYear", Integer.toString(computeYear));

    p.put("markets", markets.saveString());

    // Over tax free duration settings
    p.put("overTaxFreeDuration", Integer.toString(overTaxFreeDuration));

    // No income trades
    p.put("noIncomeTrades", Integer.toString(noIncomeTrades));

    // Allow shorts over year's boundary
    p.put("allowShortOverYearBoundary", Boolean.toString(allowShortOverYearBoundary));

    // Separate currency in CSV export
    p.put("separateCurrencyInCSVExport", Boolean.toString(separateCurrencyInCSVExport));

    // Data directory
    p.put("dataDirectory", (dataDirectory == null) ? "" : dataDirectory);

    // Import directory
    p.put("importDirectory", (importDirectory == null) ? "" : importDirectory);

    // Use daily rates
    p.putBoolean("useDailyRates", useDailyRates);

    // Save daily rates
    saveDailyRates();
  }

  /**
   * Get known currencies (that have defined ratios)
   */
  public static String[] getCurrencies() {
    HashSet<String> cset = new HashSet<String>();

    for (Iterator<CurrencyRatio> i = ratios.iterator(); i.hasNext();)
      cset.add(i.next().getCurrency());

    String res[] = new String[cset.size()];
    cset.toArray(res);

    return res;
  }
}
