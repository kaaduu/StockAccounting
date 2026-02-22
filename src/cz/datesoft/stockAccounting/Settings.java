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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
  private static String importDirectory;

  // Trading 212 API settings
  private static String trading212ApiKey;
  private static String trading212ApiSecret;
  private static boolean trading212UseDemo;

  /**
   * Use daily exchange rates instead of unified rate
   */
  private static boolean useDailyRates;

  /**
   * Last selected import format index
   */
  private static int lastImportFormat = 0;

  /**
   * Update duplicates on import checkbox state
   */
  private static boolean updateDuplicatesOnImport = false;

  // File chooser preference
  public static final int FILE_CHOOSER_NATIVE = 0;
  public static final int FILE_CHOOSER_SWING = 1;
  private static int fileChooserMode = FILE_CHOOSER_NATIVE;

  /**
   * Show "O aplikaci" window on startup
   */
  private static boolean showAboutOnStartup = true;

  /**
   * Show metadata columns (Broker, AccountID, TxnID, Effect) visibility
   */
  private static boolean showMetadataColumns = true;
  private static boolean showSecondsInDateColumns = false;

  // Import highlight settings
  private static boolean highlightInsertedEnabled = true;
  private static boolean highlightUpdatedEnabled = true;
  private static java.awt.Color highlightInsertedColor = null;
  private static java.awt.Color highlightUpdatedColor = null;

  /**
   * Daily exchange rates map (currency|YYYY-MM-DD => ratio map)
   */
  private static boolean autoMaximized = false;

  // UI theme (Look&Feel)
  public static final int THEME_SYSTEM = 0;
  public static final int THEME_FLAT_LIGHT = 1;
  public static final int THEME_FLAT_DARK = 2;
  public static final int THEME_FLAT_INTELLIJ = 3;
  public static final int THEME_FLAT_DARCULA = 4;
  private static int uiTheme = THEME_SYSTEM;

  // UI fonts
  // Empty/zero means "use Look&Feel default".
  private static String uiFontFamily = "";
  private static int uiFontSize = 0;
  private static String monospaceFontFamily = "Monospaced";
  private static int monospaceFontSize = 12;

  public static int getUiTheme() {
    return uiTheme;
  }

  public static void setUiTheme(int value) {
    uiTheme = value;
  }

  public static String getUiFontFamily() {
    return uiFontFamily;
  }

  public static void setUiFontFamily(String family) {
    uiFontFamily = (family == null) ? "" : family.trim();
  }

  public static int getUiFontSize() {
    return uiFontSize;
  }

  public static void setUiFontSize(int size) {
    uiFontSize = size;
  }

  public static String getMonospaceFontFamily() {
    return monospaceFontFamily;
  }

  public static void setMonospaceFontFamily(String family) {
    String f = (family == null) ? "" : family.trim();
    monospaceFontFamily = f.isEmpty() ? "Monospaced" : f;
  }

  public static int getMonospaceFontSize() {
    return monospaceFontSize;
  }

  public static void setMonospaceFontSize(int size) {
    monospaceFontSize = size;
  }

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
   * Get last opened file path
   */
  public static String getLastOpenedFile() {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    return p.get("lastOpenedFile", null);
  }

  /**
   * Set last opened file path
   */
  public static void setLastOpenedFile(String path) {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    if (path == null) {
      p.remove("lastOpenedFile");
    } else {
      p.put("lastOpenedFile", path);
    }
  }

  /**
   * Get auto-load last file setting
   */
  public static boolean getAutoLoadLastFile() {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    return p.getBoolean("autoLoadLastFile", false);
  }

  /**
   * Set auto-load last file setting
   */
  public static void setAutoLoadLastFile(boolean autoLoad) {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("autoLoadLastFile", autoLoad);
  }

  private static final String PREF_KEY_RECENT_FILES = "recentFiles";
  private static final int MAX_RECENT_FILES = 10;

  public static List<String> getRecentFiles() {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    String files = p.get(PREF_KEY_RECENT_FILES, "");
    if (files.isEmpty()) return new ArrayList<>();
    return new ArrayList<>(Arrays.asList(files.split("\\|")));
  }

  public static void addRecentFile(String path) {
    if (path == null || path.isEmpty()) return;
    List<String> files = getRecentFiles();
    files.remove(path);
    files.add(0, path);
    if (files.size() > MAX_RECENT_FILES) {
      files = files.subList(0, MAX_RECENT_FILES);
    }
    saveRecentFiles(files);
  }

  public static void removeRecentFile(String path) {
    List<String> files = getRecentFiles();
    files.remove(path);
    saveRecentFiles(files);
  }

  private static void saveRecentFiles(List<String> files) {
    Preferences p = Preferences.userNodeForPackage(Settings.class);
    String joined = String.join("|", files);
    if (joined.isEmpty()) {
      p.remove(PREF_KEY_RECENT_FILES);
    } else {
      p.put(PREF_KEY_RECENT_FILES, joined);
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

  // Trading 212 API settings getters
  public static String getTrading212ApiKey() {
    return trading212ApiKey;
  }

  public static String getTrading212ApiSecret() {
    return trading212ApiSecret;
  }

  public static boolean getTrading212UseDemo() {
    return trading212UseDemo;
  }

  // Trading 212 import state
  private static String trading212ImportState = null;

  public static String getTrading212ImportState() {
    return trading212ImportState;
  }

  public static void setTrading212ImportState(String state) {
    trading212ImportState = state;
  }

  // IBKR Flex settings
  private static String ibkrFlexQueryId = null;
  private static String ibkrFlexToken = null;

  // Unified cache base directory
  private static String cacheBaseDir = null;

  // IBKR TWS API settings
  private static String twsHost = null;
  private static Integer twsPort = null;
  private static Integer twsClientId = null;
  private static Integer twsTimeoutSeconds = null;
  private static String twsDefaultAccount = null;

  private static boolean cloudSyncEnabled = false;
  private static boolean cloudSyncAutoOnStartup = false;
  private static boolean cloudSyncAutoOnSave = false;
  private static boolean cloudSyncAutoOnImport = false;
  private static long lastCloudSyncTimestamp = 0;

  private static String defaultCacheBaseDir() {
    return System.getProperty("user.home") + "/.stockaccounting/cache";
  }

  public static String getCacheBaseDir() {
    if (cacheBaseDir == null || cacheBaseDir.trim().isEmpty()) {
      // Prefer persisted preferences key if present; otherwise default.
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      cacheBaseDir = p.get("cacheBaseDir", defaultCacheBaseDir());
    }
    return cacheBaseDir;
  }

  public static void setCacheBaseDir(String value) {
    cacheBaseDir = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    if (value != null && !value.trim().isEmpty()) {
      p.put("cacheBaseDir", value.trim());
    } else {
      p.remove("cacheBaseDir");
    }
  }

  public static String getTwsHost() {
    if (twsHost == null || twsHost.trim().isEmpty()) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      twsHost = p.get("twsHost", "127.0.0.1");
    }
    return twsHost;
  }

  public static void setTwsHost(String value) {
    twsHost = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    if (value != null && !value.trim().isEmpty()) {
      p.put("twsHost", value.trim());
    } else {
      p.remove("twsHost");
    }
  }

  public static int getTwsPort() {
    if (twsPort == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      twsPort = p.getInt("twsPort", 7496);
    }
    return twsPort;
  }

  public static void setTwsPort(int value) {
    twsPort = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putInt("twsPort", value);
  }

  public static int getTwsClientId() {
    if (twsClientId == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      twsClientId = p.getInt("twsClientId", 101);
    }
    return twsClientId;
  }

  public static void setTwsClientId(int value) {
    twsClientId = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putInt("twsClientId", value);
  }

  public static int getTwsTimeoutSeconds() {
    if (twsTimeoutSeconds == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      twsTimeoutSeconds = p.getInt("twsTimeoutSeconds", 15);
      if (twsTimeoutSeconds < 3) {
        twsTimeoutSeconds = 3;
      }
      if (twsTimeoutSeconds > 120) {
        twsTimeoutSeconds = 120;
      }
    }
    return twsTimeoutSeconds;
  }

  public static void setTwsTimeoutSeconds(int value) {
    if (value < 3)
      value = 3;
    if (value > 120)
      value = 120;
    twsTimeoutSeconds = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putInt("twsTimeoutSeconds", value);
  }

  public static String getTwsDefaultAccount() {
    if (twsDefaultAccount == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      twsDefaultAccount = p.get("twsDefaultAccount", "");
    }
    return twsDefaultAccount;
  }

  public static void setTwsDefaultAccount(String value) {
    twsDefaultAccount = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    if (value != null && !value.trim().isEmpty()) {
      p.put("twsDefaultAccount", value.trim());
    } else {
      p.remove("twsDefaultAccount");
    }
  }

  public static boolean getCloudSyncEnabled() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    cloudSyncEnabled = p.getBoolean("cloudSyncEnabled", false);
    return cloudSyncEnabled;
  }

  public static void setCloudSyncEnabled(boolean value) {
    cloudSyncEnabled = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("cloudSyncEnabled", value);
  }

  public static boolean getCloudSyncAutoOnStartup() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    cloudSyncAutoOnStartup = p.getBoolean("cloudSyncAutoOnStartup", false);
    return cloudSyncAutoOnStartup;
  }

  public static void setCloudSyncAutoOnStartup(boolean value) {
    cloudSyncAutoOnStartup = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("cloudSyncAutoOnStartup", value);
  }

  public static boolean getCloudSyncAutoOnSave() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    cloudSyncAutoOnSave = p.getBoolean("cloudSyncAutoOnSave", false);
    return cloudSyncAutoOnSave;
  }

  public static void setCloudSyncAutoOnSave(boolean value) {
    cloudSyncAutoOnSave = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("cloudSyncAutoOnSave", value);
  }

  public static boolean getCloudSyncAutoOnImport() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    cloudSyncAutoOnImport = p.getBoolean("cloudSyncAutoOnImport", false);
    return cloudSyncAutoOnImport;
  }

  public static void setCloudSyncAutoOnImport(boolean value) {
    cloudSyncAutoOnImport = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("cloudSyncAutoOnImport", value);
  }

  public static long getLastCloudSyncTimestamp() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    lastCloudSyncTimestamp = p.getLong("lastCloudSyncTimestamp", 0);
    return lastCloudSyncTimestamp;
  }

  public static void setLastCloudSyncTimestamp(long value) {
    lastCloudSyncTimestamp = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putLong("lastCloudSyncTimestamp", value);
  }

  public static String getIbkrFlexQueryId() {
    if (ibkrFlexQueryId == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      ibkrFlexQueryId = p.get("IBKR_FLEX_QUERY_ID", "");
    }
    return ibkrFlexQueryId;
  }

  public static String getIbkrFlexToken() {
    if (ibkrFlexToken == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      ibkrFlexToken = p.get("IBKR_FLEX_TOKEN", "");
    }
    return ibkrFlexToken;
  }

  public static void setIbkrFlexQueryId(String queryId) {
    ibkrFlexQueryId = queryId;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    if (queryId != null && !queryId.isEmpty()) {
      p.put("IBKR_FLEX_QUERY_ID", queryId);
    } else {
      p.remove("IBKR_FLEX_QUERY_ID");
    }
  }

  public static void setIbkrFlexToken(String token) {
    ibkrFlexToken = token;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    if (token != null && !token.isEmpty()) {
      p.put("IBKR_FLEX_TOKEN", token);
    } else {
      p.remove("IBKR_FLEX_TOKEN");
    }
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

  public static int getFileChooserMode() {
    return fileChooserMode;
  }

  public static void setFileChooserMode(int value) {
    if (value != FILE_CHOOSER_NATIVE && value != FILE_CHOOSER_SWING) {
      value = FILE_CHOOSER_NATIVE;
    }
    fileChooserMode = value;
  }

  public static boolean getShowAboutOnStartup() {
    return showAboutOnStartup;
  }

  public static void setShowAboutOnStartup(boolean value) {
    showAboutOnStartup = value;
  }

  // Trading 212 API settings setters
  public static void setTrading212ApiKey(String value) {
    trading212ApiKey = value;
  }

  public static void setTrading212ApiSecret(String value) {
    trading212ApiSecret = value;
  }

  public static void setTrading212UseDemo(boolean value) {
    trading212UseDemo = value;
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
   * Last import format getter/setter
   */
  public static int getLastImportFormat() {
    return lastImportFormat;
  }

  public static void setLastImportFormat(int value) {
    lastImportFormat = value;
  }

  /**
   * Get show metadata columns setting
   */
  public static boolean getShowMetadataColumns() {
    return showMetadataColumns;
  }

  /**
   * Set show metadata columns setting
   */
  public static void setShowMetadataColumns(boolean value) {
    showMetadataColumns = value;
  }

  public static boolean getShowSecondsInDateColumns() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    showSecondsInDateColumns = p.getBoolean("showSecondsInDateColumns", false);
    return showSecondsInDateColumns;
  }

  public static void setShowSecondsInDateColumns(boolean value) {
    showSecondsInDateColumns = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("showSecondsInDateColumns", value);
  }

  public static boolean getAutoMaximized() {
    return autoMaximized;
  }

  public static void setAutoMaximized(boolean value) {
    autoMaximized = value;
  }

  public static boolean getHighlightInsertedEnabled() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    highlightInsertedEnabled = p.getBoolean("highlightInsertedEnabled", true);
    return highlightInsertedEnabled;
  }

  public static void setHighlightInsertedEnabled(boolean value) {
    highlightInsertedEnabled = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("highlightInsertedEnabled", value);
  }

  public static boolean getHighlightUpdatedEnabled() {
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    highlightUpdatedEnabled = p.getBoolean("highlightUpdatedEnabled", true);
    return highlightUpdatedEnabled;
  }

  public static void setHighlightUpdatedEnabled(boolean value) {
    highlightUpdatedEnabled = value;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.putBoolean("highlightUpdatedEnabled", value);
  }

  public static java.awt.Color getHighlightInsertedColor() {
    if (highlightInsertedColor == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      String hex = p.get("highlightInsertedColor", "#C8FFC8"); // light green
      highlightInsertedColor = parseColorHex(hex, new java.awt.Color(200, 255, 200));
    }
    return highlightInsertedColor;
  }

  public static void setHighlightInsertedColor(java.awt.Color c) {
    if (c == null)
      return;
    highlightInsertedColor = c;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.put("highlightInsertedColor", colorToHex(c));
  }

  public static java.awt.Color getHighlightUpdatedColor() {
    if (highlightUpdatedColor == null) {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      String hex = p.get("highlightUpdatedColor", "#FFFFC8"); // light yellow
      highlightUpdatedColor = parseColorHex(hex, new java.awt.Color(255, 255, 200));
    }
    return highlightUpdatedColor;
  }

  public static void setHighlightUpdatedColor(java.awt.Color c) {
    if (c == null)
      return;
    highlightUpdatedColor = c;
    java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
    p.put("highlightUpdatedColor", colorToHex(c));
  }

  private static java.awt.Color parseColorHex(String hex, java.awt.Color fallback) {
    try {
      if (hex == null)
        return fallback;
      String s = hex.trim();
      if (s.startsWith("#"))
        s = s.substring(1);
      if (s.length() != 6)
        return fallback;
      int rgb = Integer.parseInt(s, 16);
      return new java.awt.Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    } catch (Exception e) {
      return fallback;
    }
  }

  private static String colorToHex(java.awt.Color c) {
    String r = String.format("%02X", c.getRed());
    String g = String.format("%02X", c.getGreen());
    String b = String.format("%02X", c.getBlue());
    return "#" + r + g + b;
  }

  /**
   * Get update duplicates on import checkbox state
   */
  public static boolean getUpdateDuplicatesOnImport() {
    return updateDuplicatesOnImport;
  }

  /**
   * Set update duplicates on import checkbox state
   */
  public static void setUpdateDuplicatesOnImport(boolean value) {
    updateDuplicatesOnImport = value;
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

    try {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      String[] keys = p.keys();

      for (String key : keys) {
        if (key.startsWith("dailyRate.")) {
          // Extract the rate key (remove "dailyRate." prefix)
          String rateKey = key.substring("dailyRate.".length());
          String rateValue = p.get(key, "");

          if (!rateValue.isEmpty()) {
            try {
              dailyRates.put(rateKey, Double.valueOf(Double.parseDouble(rateValue)));
            } catch (NumberFormatException e) {
              // Skip invalid entries
              System.err.println("Invalid daily rate value for key: " + key);
            }
          }
        }
      }
    } catch (Exception e) {
      System.err.println("Error loading daily rates from Preferences: " + e.getMessage());
    }
  }

  /**
   * Save daily rates to data directory
   */
  public static void saveDailyRates() {
    if (dailyRates == null || dailyRates.isEmpty())
      return;

    try {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
      for (Map.Entry<String, Double> entry : dailyRates.entrySet()) {
        p.put("dailyRate." + entry.getKey(), entry.getValue().toString());
      }
    } catch (Exception e) {
      System.err.println("Error saving daily rates: " + e.getMessage());
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

    /* Show metadata columns */
    showMetadataColumns = p.getBoolean("showMetadataColumns", true);

    /* Trading 212 API settings */
    trading212ApiKey = p.get("trading212ApiKey", null);
    trading212ApiSecret = p.get("trading212ApiSecret", null);
    trading212UseDemo = p.getBoolean("trading212UseDemo", true);

    /* Trading 212 import state */
    trading212ImportState = p.get("trading212ImportState", null);

    /* Last import format */
    s = p.get("lastImportFormat", "0");
    try {
      lastImportFormat = Integer.parseInt(s);
    } catch (NumberFormatException e) {
      lastImportFormat = 0; // Default to "select format"
    }

    /* Update duplicates on import */
    updateDuplicatesOnImport = p.getBoolean("updateDuplicatesOnImport", false);

    /* File chooser preference */
    fileChooserMode = p.getInt("fileChooserMode", FILE_CHOOSER_NATIVE);
    if (fileChooserMode != FILE_CHOOSER_NATIVE && fileChooserMode != FILE_CHOOSER_SWING) {
      fileChooserMode = FILE_CHOOSER_NATIVE;
    }

    /* About on startup */
    showAboutOnStartup = p.getBoolean("showAboutOnStartup", true);

    /* Auto maximized */
    autoMaximized = p.getBoolean("autoMaximized", false);

    /* UI theme */
    uiTheme = p.getInt("uiTheme", THEME_SYSTEM);
    if (uiTheme < THEME_SYSTEM || uiTheme > THEME_FLAT_DARCULA) {
      uiTheme = THEME_SYSTEM;
    }

    /* UI fonts */
    uiFontFamily = p.get("uiFontFamily", "");
    uiFontSize = p.getInt("uiFontSize", 0);
    if (uiFontSize < 0 || uiFontSize > 96) {
      uiFontSize = 0;
    }

    monospaceFontFamily = p.get("monospaceFontFamily", "Monospaced");
    if (monospaceFontFamily == null || monospaceFontFamily.trim().isEmpty()) {
      monospaceFontFamily = "Monospaced";
    }

    monospaceFontSize = p.getInt("monospaceFontSize", 12);
    if (monospaceFontSize < 6 || monospaceFontSize > 96) {
      monospaceFontSize = 12;
    }
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

    // Show metadata columns
    p.putBoolean("showMetadataColumns", showMetadataColumns);

    // Trading 212 API settings
    if (trading212ApiKey != null) {
      p.put("trading212ApiKey", trading212ApiKey);
    }
    if (trading212ApiSecret != null) {
      p.put("trading212ApiSecret", trading212ApiSecret);
    }
    p.putBoolean("trading212UseDemo", trading212UseDemo);

    // Trading 212 import state
    if (trading212ImportState != null) {
      p.put("trading212ImportState", trading212ImportState);
    }

    // Last import format
    p.put("lastImportFormat", Integer.toString(lastImportFormat));

    // Update duplicates on import
    p.putBoolean("updateDuplicatesOnImport", updateDuplicatesOnImport);

    // File chooser preference
    p.putInt("fileChooserMode", fileChooserMode);

    // About on startup
    p.putBoolean("showAboutOnStartup", showAboutOnStartup);

    // Auto maximized
    p.putBoolean("autoMaximized", autoMaximized);

    // UI theme
    p.putInt("uiTheme", uiTheme);

    // UI fonts
    p.put("uiFontFamily", (uiFontFamily == null) ? "" : uiFontFamily);
    p.putInt("uiFontSize", uiFontSize);
    p.put("monospaceFontFamily", (monospaceFontFamily == null) ? "Monospaced" : monospaceFontFamily);
    p.putInt("monospaceFontSize", monospaceFontSize);
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

  /**
   * Get currencies that are actually used in transactions
   */
  public static java.util.Set<String> getUsedCurrencies(TransactionSet transactions) {
    java.util.Set<String> usedCurrencies = new java.util.HashSet<String>();

    if (transactions != null) {
      for (java.util.Iterator<Transaction> i = transactions.iterator(); i.hasNext();) {
        Transaction tx = i.next();
        String priceCurrency = tx.getPriceCurrency();
        String feeCurrency = tx.getFeeCurrency();

        if (priceCurrency != null && !priceCurrency.trim().isEmpty() && !"CZK".equalsIgnoreCase(priceCurrency)) {
          usedCurrencies.add(priceCurrency.trim().toUpperCase());
        }

        if (feeCurrency != null && !feeCurrency.trim().isEmpty() && !"CZK".equalsIgnoreCase(feeCurrency)) {
          usedCurrencies.add(feeCurrency.trim().toUpperCase());
        }
      }
    }

    return usedCurrencies;
  }

  /**
   * Get list of currencies to fetch (prioritizing used currencies, falling back
   * to defaults)
   */
  public static java.util.List<String> getCurrenciesToFetch(TransactionSet transactions) {
    java.util.Set<String> usedCurrencies = getUsedCurrencies(transactions);
    java.util.List<String> currenciesToFetch = new java.util.ArrayList<String>();

    if (!usedCurrencies.isEmpty()) {
      currenciesToFetch.addAll(usedCurrencies);
    } else {
      // Fallback to common currencies if no transactions found
      currenciesToFetch.addAll(java.util.Arrays.asList("USD", "EUR", "GBP"));
    }

    return currenciesToFetch;
  }

  /**
   * Statistics about daily rates for UI display
   */
  public static class RateStats {
    public final int totalRates;
    public final java.util.Map<String, java.util.Map<Integer, Integer>> currencyYearCounts;

    public RateStats(int totalRates, java.util.Map<String, java.util.Map<Integer, Integer>> currencyYearCounts) {
      this.totalRates = totalRates;
      this.currencyYearCounts = currencyYearCounts;
    }
  }

  /**
   * Result of import operation
   */
  public static class ImportResult {
    public final int imported;
    public final int overwritten;
    public final int skipped;
    public final java.util.List<String> errors;

    public ImportResult(int imported, int overwritten, int skipped, java.util.List<String> errors) {
      this.imported = imported;
      this.overwritten = overwritten;
      this.skipped = skipped;
      this.errors = errors;
    }
  }

  /**
   * Get statistics about current daily rates
   */
  public static RateStats getRateStats() {
    if (dailyRates == null || dailyRates.isEmpty()) {
      return new RateStats(0, java.util.Collections.emptyMap());
    }

    java.util.Map<String, java.util.Map<Integer, Integer>> currencyYearCounts = new java.util.TreeMap<>();

    for (String key : dailyRates.keySet()) {
      String[] parts = key.split("\\|");
      if (parts.length == 2) {
        String currency = parts[0];
        try {
          int year = Integer.parseInt(parts[1].substring(0, 4)); // Extract year from YYYY-MM-DD

          currencyYearCounts.computeIfAbsent(currency, k -> new java.util.TreeMap<>())
              .merge(year, 1, Integer::sum);
        } catch (Exception e) {
          // Skip invalid entries
        }
      }
    }

    return new RateStats(dailyRates.size(), currencyYearCounts);
  }

  /**
   * Delete daily rates by currency and year selection
   */
  public static int deleteRates(java.util.Set<String> currencies, java.util.Set<Integer> years) {
    if (dailyRates == null || dailyRates.isEmpty()) {
      return 0;
    }

    // Create auto-backup before deletion
    try {
      java.io.File backupDir = new java.io.File(dataDirectory == null ? "." : dataDirectory);
      String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
      java.io.File backupFile = new java.io.File(backupDir, "daily_rates_backup_" + timestamp + ".csv");
      exportRates(backupFile, null, null); // Export all rates as backup
    } catch (Exception e) {
      System.err.println("Warning: Could not create backup before deletion: " + e.getMessage());
    }

    int deletedCount = 0;
    java.util.Map<String, Double> deletedRatesBackup = new java.util.HashMap<>();
    java.util.List<String> keysToRemove = new java.util.ArrayList<>();

    try {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);

      for (String key : dailyRates.keySet()) {
        String[] parts = key.split("\\|");
        if (parts.length == 2) {
          String currency = parts[0];
          try {
            int year = Integer.parseInt(parts[1].substring(0, 4));

            if (currencies.contains(currency) && years.contains(year)) {
              keysToRemove.add(key);
              deletedRatesBackup.put(key, dailyRates.get(key));
              p.remove("dailyRate." + key);
              deletedCount++;
            }
          } catch (Exception e) {
            // Skip invalid entries
          }
        }
      }

      // Remove from in-memory map
      for (String key : keysToRemove) {
        dailyRates.remove(key);
      }

      // Store for potential undo
      if (!deletedRatesBackup.isEmpty()) {
        lastDeletedRates = deletedRatesBackup;
      }

    } catch (Exception e) {
      System.err.println("Error during selective deletion: " + e.getMessage());
    }

    return deletedCount;
  }

  /**
   * Export daily rates to CSV file
   */
  public static void exportRates(java.io.File file, java.util.Set<String> currencies, java.util.Set<Integer> years)
      throws Exception {
    if (dailyRates == null || dailyRates.isEmpty()) {
      throw new Exception("Žádné denní kurzy k exportu");
    }

    java.io.PrintWriter writer = null;
    try {
      writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8"));

      // Write header
      writer.println("CURRENCY,DATE,RATE");

      // Filter and write data
      java.util.List<String> sortedKeys = new java.util.ArrayList<>(dailyRates.keySet());
      java.util.Collections.sort(sortedKeys);

      for (String key : sortedKeys) {
        String[] parts = key.split("\\|");
        if (parts.length == 2) {
          String currency = parts[0];
          String date = parts[1];
          Double rate = dailyRates.get(key);

          try {
            int year = Integer.parseInt(date.substring(0, 4));

            // Apply filters if specified
            if (currencies != null && !currencies.contains(currency))
              continue;
            if (years != null && !years.contains(year))
              continue;

            writer.printf("%s,%s,%.4f%n", currency, date, rate);
          } catch (Exception e) {
            // Skip invalid entries
          }
        }
      }

    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  /**
   * Export unified exchange rates ("jednotny kurz") to JSON file.
   * This exports Settings.ratios (currency|year => ratio).
   */
  public static void exportUnifiedRatesJson(java.io.File file) throws Exception {
    if (ratios == null || ratios.isEmpty()) {
      throw new Exception("Žádné jednotné kurzy k exportu");
    }

    java.io.PrintWriter writer = null;
    try {
      writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8"));

      java.util.List<CurrencyRatio> list = new java.util.ArrayList<CurrencyRatio>(ratios);
      // CurrencyRatio.compareTo sorts by year desc, then currency. For export, use year asc, then currency.
      java.util.Collections.sort(list, new java.util.Comparator<CurrencyRatio>() {
        public int compare(CurrencyRatio a, CurrencyRatio b) {
          if (a == null && b == null) return 0;
          if (a == null) return -1;
          if (b == null) return 1;
          if (a.getYear() < b.getYear()) return -1;
          if (a.getYear() > b.getYear()) return 1;
          return a.getCurrency().compareTo(b.getCurrency());
        }
      });

      writer.println("{");
      writer.println("  \"type\": \"unifiedRates\",");
      writer.println("  \"rates\": [");

      for (int i = 0; i < list.size(); i++) {
        CurrencyRatio r = list.get(i);
        if (r == null) continue;
        String comma = (i == list.size() - 1) ? "" : ",";
        // currency is [A-Z]+; safe for JSON
        writer.println("    {\"currency\": \"" + r.getCurrency() + "\", \"year\": " + r.getYear() + ", \"rate\": "
            + java.lang.Double.toString(r.getRatio()) + "}" + comma);
      }

      writer.println("  ]");
      writer.println("}");
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  /**
   * Import daily rates from CSV file
   */
  public static ImportResult importRates(java.io.File file, ConflictResolutionStrategy strategy) throws Exception {
    if (!file.exists()) {
      throw new Exception("Soubor neexistuje: " + file.getName());
    }

    java.util.List<String> errors = new java.util.ArrayList<>();
    int imported = 0;
    int overwritten = 0;
    int skipped = 0;

    java.io.BufferedReader reader = null;
    try {
      reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));

      String line;
      boolean headerSkipped = false;

      while ((line = reader.readLine()) != null) {
        if (!headerSkipped) {
          headerSkipped = true;
          continue; // Skip header
        }

        if (line.trim().isEmpty())
          continue;

        String[] parts = line.split(",");
        if (parts.length != 3) {
          errors.add("Neplatný formát řádku: " + line);
          skipped++;
          continue;
        }

        String currency = parts[0].trim().toUpperCase();
        String date = parts[1].trim();
        String rateStr = parts[2].trim();

        // Validate data
        if (!isValidCurrency(currency)) {
          errors.add("Neplatná měna: " + currency);
          skipped++;
          continue;
        }

        if (!isValidDate(date)) {
          errors.add("Neplatné datum: " + date);
          skipped++;
          continue;
        }

        Double rate;
        try {
          rate = Double.parseDouble(rateStr);
          if (rate <= 0) {
            errors.add("Neplatný kurz (musí být kladný): " + rateStr);
            skipped++;
            continue;
          }
        } catch (NumberFormatException e) {
          errors.add("Neplatný formát kurzu: " + rateStr);
          skipped++;
          continue;
        }

        // Check for conflicts and apply strategy
        String key = currency + "|" + date;
        boolean exists = dailyRates.containsKey(key);

        if (exists && strategy == ConflictResolutionStrategy.SKIP) {
          skipped++;
          continue;
        }

        if (exists && strategy == ConflictResolutionStrategy.ASK_USER) {
          // This will be handled by the UI layer
          // For now, we'll assume overwrite for programmatic imports
        }

        // Store the rate
        try {
          java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
          p.put("dailyRate." + key, rate.toString());

          dailyRates.put(key, rate);

          if (exists) {
            overwritten++;
          } else {
            imported++;
          }
        } catch (Exception e) {
          errors.add("Chyba při ukládání kurzu " + key + ": " + e.getMessage());
          skipped++;
        }
      }

    } finally {
      if (reader != null) {
        reader.close();
      }
    }

    return new ImportResult(imported, overwritten, skipped, errors);
  }

  /**
   * Simple undo for last deletion operation
   */
  private static java.util.Map<String, Double> lastDeletedRates = null;

  public static boolean undoLastDeletion() {
    if (lastDeletedRates == null || lastDeletedRates.isEmpty()) {
      return false;
    }

    try {
      java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);

      for (java.util.Map.Entry<String, Double> entry : lastDeletedRates.entrySet()) {
        p.put("dailyRate." + entry.getKey(), entry.getValue().toString());
        dailyRates.put(entry.getKey(), entry.getValue());
      }

      lastDeletedRates.clear();
      return true;
    } catch (Exception e) {
      System.err.println("Error during undo: " + e.getMessage());
      return false;
    }
  }

  // Utility methods for validation
  private static boolean isValidCurrency(String currency) {
    return currency.length() == 3 && currency.matches("[A-Z]{3}");
  }

  private static boolean isValidDate(String date) {
    try {
      java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
      sdf.setLenient(false);
      java.util.Date parsed = sdf.parse(date);
      // Don't allow dates too far in the future (more than 1 year)
      return parsed.before(new java.util.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Conflict resolution strategies for import
   */
  public enum ConflictResolutionStrategy {
    OVERWRITE, SKIP, ASK_USER
  }

  /**
   * Show enhanced management dialog for daily rates
   */
  public static void showDeleteDailyRatesDialog(java.awt.Component parent) {
    // This will be replaced by the new RateManagementDialog
    // For now, keep the old implementation as fallback
    int result = javax.swing.JOptionPane.showConfirmDialog(
        parent,
        "Opravdu chcete smazat všechny uložené denní kurzy?",
        "Smazat denní kurzy",
        javax.swing.JOptionPane.YES_NO_OPTION,
        javax.swing.JOptionPane.WARNING_MESSAGE);

    if (result == javax.swing.JOptionPane.YES_OPTION) {
      try {
        java.util.prefs.Preferences p = java.util.prefs.Preferences.userNodeForPackage(Settings.class);
        String[] keys = p.keys();
        int deletedCount = 0;

        for (String key : keys) {
          if (key.startsWith("dailyRate.")) {
            p.remove(key);
            deletedCount++;
          }
        }

        if (dailyRates != null) {
          dailyRates.clear();
        }

        javax.swing.JOptionPane.showMessageDialog(
            parent,
            "Smazáno " + deletedCount + " denních kurzů.",
            "Hotovo",
            javax.swing.JOptionPane.INFORMATION_MESSAGE);
      } catch (Exception e) {
        javax.swing.JOptionPane.showMessageDialog(
            parent,
            "Chyba při mazání denních kurzů: " + e.getMessage(),
            "Chyba",
            javax.swing.JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static java.util.Map<String, Object> exportAllSettings() {
    java.util.Map<String, Object> settings = new java.util.HashMap<String, Object>();
    
    settings.put("version", System.currentTimeMillis());
    settings.put("halfYear", halfYear);
    settings.put("ratios", ratiosToString());
    settings.put("computeYear", computeYear);
    settings.put("overTaxFreeDuration", overTaxFreeDuration);
    settings.put("noIncomeTrades", noIncomeTrades);
    settings.put("allowShortOverYearBoundary", allowShortOverYearBoundary);
    settings.put("separateCurrencyInCSVExport", separateCurrencyInCSVExport);
    settings.put("uiTheme", uiTheme);
    settings.put("uiFontFamily", uiFontFamily);
    settings.put("uiFontSize", uiFontSize);
    settings.put("monospaceFontFamily", monospaceFontFamily);
    settings.put("monospaceFontSize", monospaceFontSize);
    settings.put("highlightInsertedEnabled", highlightInsertedEnabled);
    settings.put("highlightUpdatedEnabled", highlightUpdatedEnabled);
    settings.put("lastImportFormat", lastImportFormat);
    settings.put("updateDuplicatesOnImport", updateDuplicatesOnImport);
    settings.put("showMetadataColumns", showMetadataColumns);
    settings.put("showSecondsInDateColumns", showSecondsInDateColumns);
    settings.put("useDailyRates", useDailyRates);
    settings.put("dailyRates", exportDailyRatesMap());
    settings.put("markets", markets != null ? markets.saveString() : "");
    settings.put("dataDirectory", dataDirectory);
    settings.put("importDirectory", importDirectory);
    settings.put("cacheBaseDir", cacheBaseDir);
    settings.put("fileChooserMode", fileChooserMode);
    settings.put("showAboutOnStartup", showAboutOnStartup);
    settings.put("autoMaximized", autoMaximized);

    settings.put("trading212ApiKey", trading212ApiKey);
    settings.put("trading212ApiSecret", trading212ApiSecret);
    settings.put("trading212UseDemo", trading212UseDemo);
    settings.put("trading212ImportState", trading212ImportState);
    
    settings.put("ibkrFlexQueryId", ibkrFlexQueryId);
    settings.put("ibkrFlexToken", ibkrFlexToken);
    
    settings.put("twsHost", twsHost);
    settings.put("twsPort", twsPort);
    settings.put("twsClientId", twsClientId);
    settings.put("twsTimeoutSeconds", twsTimeoutSeconds);
    settings.put("twsDefaultAccount", twsDefaultAccount);

    settings.put("cloudSyncEnabled", cloudSyncEnabled);
    settings.put("cloudSyncAutoOnStartup", cloudSyncAutoOnStartup);
    settings.put("cloudSyncAutoOnSave", cloudSyncAutoOnSave);
    settings.put("cloudSyncAutoOnImport", cloudSyncAutoOnImport);
    settings.put("lastCloudSyncTimestamp", lastCloudSyncTimestamp);

    return settings;
  }

  public static void importAllSettings(java.util.Map<String, Object> settings) {
    if (settings.containsKey("halfYear")) {
      halfYear = (Integer) settings.get("halfYear");
    }
    if (settings.containsKey("ratios")) {
      parseRatiosString((String) settings.get("ratios"));
    }
    if (settings.containsKey("computeYear")) {
      computeYear = (Integer) settings.get("computeYear");
    }
    if (settings.containsKey("overTaxFreeDuration")) {
      overTaxFreeDuration = (Integer) settings.get("overTaxFreeDuration");
    }
    if (settings.containsKey("noIncomeTrades")) {
      noIncomeTrades = (Integer) settings.get("noIncomeTrades");
    }
    if (settings.containsKey("allowShortOverYearBoundary")) {
      allowShortOverYearBoundary = (Boolean) settings.get("allowShortOverYearBoundary");
    }
    if (settings.containsKey("separateCurrencyInCSVExport")) {
      separateCurrencyInCSVExport = (Boolean) settings.get("separateCurrencyInCSVExport");
    }
    if (settings.containsKey("uiTheme")) {
      uiTheme = (Integer) settings.get("uiTheme");
    }
    if (settings.containsKey("uiFontFamily")) {
      uiFontFamily = (String) settings.get("uiFontFamily");
    }
    if (settings.containsKey("uiFontSize")) {
      uiFontSize = (Integer) settings.get("uiFontSize");
    }
    if (settings.containsKey("monospaceFontFamily")) {
      monospaceFontFamily = (String) settings.get("monospaceFontFamily");
    }
    if (settings.containsKey("monospaceFontSize")) {
      monospaceFontSize = (Integer) settings.get("monospaceFontSize");
    }
    if (settings.containsKey("highlightInsertedEnabled")) {
      highlightInsertedEnabled = (Boolean) settings.get("highlightInsertedEnabled");
    }
    if (settings.containsKey("highlightUpdatedEnabled")) {
      highlightUpdatedEnabled = (Boolean) settings.get("highlightUpdatedEnabled");
    }
    if (settings.containsKey("lastImportFormat")) {
      lastImportFormat = (Integer) settings.get("lastImportFormat");
    }
    if (settings.containsKey("updateDuplicatesOnImport")) {
      updateDuplicatesOnImport = (Boolean) settings.get("updateDuplicatesOnImport");
    }
    if (settings.containsKey("showMetadataColumns")) {
      showMetadataColumns = (Boolean) settings.get("showMetadataColumns");
    }
    if (settings.containsKey("showSecondsInDateColumns")) {
      showSecondsInDateColumns = (Boolean) settings.get("showSecondsInDateColumns");
    }
    if (settings.containsKey("useDailyRates")) {
      useDailyRates = (Boolean) settings.get("useDailyRates");
    }
    if (settings.containsKey("dailyRates")) {
      importDailyRatesMap((java.util.Map<String, Double>) settings.get("dailyRates"));
    }
    if (settings.containsKey("markets")) {
      String marketsStr = (String) settings.get("markets");
      if (marketsStr != null && !marketsStr.isEmpty()) {
        markets = new Markets(marketsStr);
      }
    }
    if (settings.containsKey("dataDirectory")) {
      dataDirectory = (String) settings.get("dataDirectory");
    }
    if (settings.containsKey("importDirectory")) {
      importDirectory = (String) settings.get("importDirectory");
    }
    if (settings.containsKey("cacheBaseDir")) {
      cacheBaseDir = (String) settings.get("cacheBaseDir");
    }
    if (settings.containsKey("fileChooserMode")) {
      fileChooserMode = (Integer) settings.get("fileChooserMode");
    }
    if (settings.containsKey("showAboutOnStartup")) {
      showAboutOnStartup = (Boolean) settings.get("showAboutOnStartup");
    }
    if (settings.containsKey("autoMaximized")) {
      autoMaximized = (Boolean) settings.get("autoMaximized");
    }

    if (settings.containsKey("trading212ApiKey")) {
      trading212ApiKey = (String) settings.get("trading212ApiKey");
    }
    if (settings.containsKey("trading212ApiSecret")) {
      trading212ApiSecret = (String) settings.get("trading212ApiSecret");
    }
    if (settings.containsKey("trading212UseDemo")) {
      trading212UseDemo = (Boolean) settings.get("trading212UseDemo");
    }
    if (settings.containsKey("trading212ImportState")) {
      trading212ImportState = (String) settings.get("trading212ImportState");
    }
    
    if (settings.containsKey("ibkrFlexQueryId")) {
      ibkrFlexQueryId = (String) settings.get("ibkrFlexQueryId");
    }
    if (settings.containsKey("ibkrFlexToken")) {
      ibkrFlexToken = (String) settings.get("ibkrFlexToken");
    }
    
    if (settings.containsKey("twsHost")) {
      twsHost = (String) settings.get("twsHost");
    }
    if (settings.containsKey("twsPort")) {
      twsPort = (Integer) settings.get("twsPort");
    }
    if (settings.containsKey("twsClientId")) {
      twsClientId = (Integer) settings.get("twsClientId");
    }
    if (settings.containsKey("twsTimeoutSeconds")) {
      twsTimeoutSeconds = (Integer) settings.get("twsTimeoutSeconds");
    }
    if (settings.containsKey("twsDefaultAccount")) {
      twsDefaultAccount = (String) settings.get("twsDefaultAccount");
    }

    if (settings.containsKey("cloudSyncEnabled")) {
      cloudSyncEnabled = (Boolean) settings.get("cloudSyncEnabled");
    }
    if (settings.containsKey("cloudSyncAutoOnStartup")) {
      cloudSyncAutoOnStartup = (Boolean) settings.get("cloudSyncAutoOnStartup");
    }
    if (settings.containsKey("cloudSyncAutoOnSave")) {
      cloudSyncAutoOnSave = (Boolean) settings.get("cloudSyncAutoOnSave");
    }
    if (settings.containsKey("cloudSyncAutoOnImport")) {
      cloudSyncAutoOnImport = (Boolean) settings.get("cloudSyncAutoOnImport");
    }
    if (settings.containsKey("lastCloudSyncTimestamp")) {
      lastCloudSyncTimestamp = (Long) settings.get("lastCloudSyncTimestamp");
    }

    save();
  }

  public static void exportToEncryptedBackup(java.io.OutputStream output, char[] password) throws Exception {
    java.util.Map<String, Object> settings = exportAllSettings();
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
    oos.writeObject(settings);
    oos.close();
    
    byte[] data = baos.toByteArray();
    byte[] encrypted = EncryptionUtils.encryptData(data, password);
    output.write(encrypted);
  }

  public static void importFromEncryptedBackup(java.io.InputStream input, char[] password) throws Exception {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int bytesRead;
    while ((bytesRead = input.read(buffer)) != -1) {
      baos.write(buffer, 0, bytesRead);
    }
    
    byte[] encrypted = baos.toByteArray();
    byte[] decrypted = EncryptionUtils.decryptData(encrypted, password);
    
    java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(decrypted);
    java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> settings = (java.util.Map<String, Object>) ois.readObject();
    ois.close();
    
    importAllSettings(settings);
  }

  private static String ratiosToString() {
    StringBuilder sb = new StringBuilder();
    for (CurrencyRatio ratio : ratios) {
      if (sb.length() > 0) {
        sb.append("\n");
      }
      sb.append(ratio.getCurrency()).append("|").append(ratio.getYear()).append("|").append(ratio.getRatio());
    }
    return sb.toString();
  }

  private static void parseRatiosString(String str) {
    ratios = new java.util.TreeSet<CurrencyRatio>();
    if (str == null || str.isEmpty()) {
      return;
    }
    String[] lines = str.split("\n");
    for (String line : lines) {
      String[] parts = line.split("\\|");
      if (parts.length == 3) {
        try {
          String currency = parts[0].trim();
          int year = Integer.parseInt(parts[1].trim());
          double ratio = Double.parseDouble(parts[2].trim());
          ratios.add(new CurrencyRatio(currency, year, ratio));
        } catch (NumberFormatException e) {
          AppLog.warn("Invalid ratio format: " + line);
        }
      }
    }
  }

  private static java.util.Map<String, Double> exportDailyRatesMap() {
    java.util.Map<String, Double> map = new java.util.HashMap<String, Double>();
    if (dailyRates != null) {
      for (java.util.Map.Entry<String, Double> entry : dailyRates.entrySet()) {
        map.put(entry.getKey(), entry.getValue());
      }
    }
    return map;
  }

  private static void importDailyRatesMap(java.util.Map<String, Double> map) {
    if (dailyRates == null) {
      dailyRates = new java.util.HashMap<String, Double>();
    }
    dailyRates.clear();
    if (map != null) {
      dailyRates.putAll(map);
    }
  }
}
