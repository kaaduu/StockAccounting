/*
 * CurrencyRateFetcher.java
 *
 * Fetches exchange rates from Czech National Bank (CNB) API
 * and calculates "jednotný kurz" (unified exchange rate) for tax purposes.
 */

package cz.datesoft.stockAccounting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Utility class for fetching currency exchange rates from CNB API
 * 
 * @author Antigravity AI
 */
public class CurrencyRateFetcher {
  /**
   * Result of a fetch operation
   */
  public static class FetchedRate {
    public double rate;
    public boolean wasModified;
    public String source;

    public FetchedRate(double rate, boolean wasModified, String source) {
      this.rate = rate;
      this.wasModified = wasModified;
      this.source = source;
    }
  }

  /**
   * Cache for fetched daily rates to minimize API calls
   * Key format: "CURRENCY|YYYY-MM-DD"
   */
  private static Map<String, Double> dailyRateCache = new HashMap<String, Double>();

  /**
   * CNB API endpoint for daily exchange rates
   */
  private static final String CNB_API_URL = "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/denni_kurz.txt";

  /**
   * Connection timeout in milliseconds
   */
  private static final int TIMEOUT_MS = 10000;

  /**
   * Fetch CNB exchange rate for a specific date
   * 
   * @param currency Currency code (e.g., "EUR", "USD")
   * @param year     Year
   * @param month    Month (1-12)
   * @param day      Day of month
   * @return Exchange rate (amount of CZK per 1 unit of foreign currency), or null
   *         if not found
   */
  private static Double fetchDailyRate(String currency, int year, int month, int day) throws Exception {
    currency = currency.toUpperCase();
    String dateStr = String.format("%02d.%02d.%d", day, month, year);
    String cacheKey = currency + "|" + String.format("%d-%02d-%02d", year, month, day);

    // Check cache first
    if (dailyRateCache.containsKey(cacheKey)) {
      return dailyRateCache.get(cacheKey);
    }

    // Fetch from CNB API
    String urlStr = CNB_API_URL + "?date=" + dateStr;
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    try {
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        return null; // Date not available (weekend/holiday)
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
      String line;

      // Skip first line (date header)
      reader.readLine();

      // Skip second line (column headers)
      reader.readLine();

      // Parse data lines
      // Format: země|měna|množství|kód|kurz
      // Example: EMU|euro|1|EUR|25,445
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\\|");
        if (parts.length >= 5) {
          String code = parts[3].trim();
          if (code.equalsIgnoreCase(currency)) {
            // Found the currency
            String amountStr = parts[2].trim();
            String rateStr = parts[4].trim().replace(',', '.');

            double amount = Double.parseDouble(amountStr);
            double rate = Double.parseDouble(rateStr);

            // Calculate rate per 1 unit of foreign currency
            double normalizedRate = rate / amount;

            // Cache the result
            dailyRateCache.put(cacheKey, normalizedRate);

            reader.close();
            return normalizedRate;
          }
        }
      }

      reader.close();
      return null; // Currency not found

    } finally {
      conn.disconnect();
    }
  }

  /**
   * Fetch CNB exchange rate for the last day of a specific month
   * Handles weekends/holidays by trying previous days
   * 
   * @param currency Currency code
   * @param year     Year
   * @param month    Month (1-12)
   * @return Exchange rate, or null if not found
   */
  public static Double fetchMonthEndRate(String currency, int year, int month) throws Exception {
    // Determine last day of month
    Calendar cal = new GregorianCalendar(year, month - 1, 1);
    int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

    // Try last day, then work backwards up to 5 days to handle weekends/holidays
    for (int dayOffset = 0; dayOffset < 5; dayOffset++) {
      int day = lastDay - dayOffset;
      if (day < 1)
        break;

      try {
        Double rate = fetchDailyRate(currency, year, month, day);
        if (rate != null) {
          return rate;
        }
      } catch (Exception e) {
        // Continue trying previous days
      }
    }

    return null;
  }

  /**
   * Calculate "jednotný kurz" (unified exchange rate) for a currency and year
   * This is the arithmetic mean of CNB rates from the last day of each month
   * 
   * @param currency Currency code
   * @param year     Year
   * @return Jednotný kurz, or null if insufficient data
   */
  public static Double fetchJednotnyKurz(String currency, int year) throws Exception {
    double sum = 0;
    int count = 0;

    // Fetch rates for last day of each month
    for (int month = 1; month <= 12; month++) {
      Double rate = fetchMonthEndRate(currency, year, month);
      if (rate != null) {
        sum += rate;
        count++;
      }
    }

    // Require at least 10 months of data (allow for some missing data)
    if (count < 10) {
      return null;
    }

    // Calculate arithmetic mean
    // Return arithmetic mean rounded to 2 decimal places
    double average = sum / count;
    return Math.round(average * 100.0) / 100.0;
  }

  /**
   * Fetch rates for all currencies and years in the table model
   * 
   * @param model          The table model containing currencies and years
   * @param existingRatios Existing currency ratios from Settings
   * @return Map of "CURRENCY|YEAR" -> FetchedRate
   */
  public static Map<String, FetchedRate> fetchRatesForTable(
      javax.swing.table.TableModel model,
      java.util.SortedSet<Settings.CurrencyRatio> existingRatios) throws Exception {
    Map<String, FetchedRate> results = new HashMap<String, FetchedRate>();

    // Build map of existing rates for comparison
    Map<String, Double> existingMap = new HashMap<String, Double>();
    for (Settings.CurrencyRatio ratio : existingRatios) {
      String key = ratio.getCurrency() + "|" + ratio.getYear();
      existingMap.put(key, ratio.getRatio());
    }

    // Get all currencies from model (skip first column which is "Rok")
    java.util.List<String> currencies = new java.util.ArrayList<String>();
    for (int col = 1; col < model.getColumnCount(); col++) {
      String currency = model.getColumnName(col);
      if (currency != null && !currency.isEmpty()) {
        currencies.add(currency);
      }
    }

    // Get all years from model
    for (int row = 0; row < model.getRowCount() - 1; row++) {
      Object yearObj = model.getValueAt(row, 0);
      if (yearObj == null)
        continue;

      int year = ((Integer) yearObj).intValue();

      // Fetch rate for each currency
      for (String currency : currencies) {
        if (currency.isEmpty())
          continue;
        // The instruction included an extra 'continue;' here, which would cause all
        // subsequent code in the loop to be skipped.
        // Assuming this was an unintended artifact of the instruction, it has been
        // omitted to maintain functional correctness.
        // If the intention was to skip all processing for non-empty currencies, please
        // clarify.

        try {
          Double fetchedRate = fetchJednotnyKurz(currency, year);
          if (fetchedRate != null) {
            String key = currency + "|" + year;
            Double existingRate = existingMap.get(key);

            boolean wasModified = (existingRate == null) ||
                (Math.abs(existingRate - fetchedRate) > 0.001);

            results.put(key, new FetchedRate(fetchedRate, wasModified, "CNB"));
          }
        } catch (Exception e) {
          // Skip this currency/year combination on error
          System.err.println("Error fetching " + currency + " for " + year + ": " + e.getMessage());
        }
      }
    }

    return results;
  }

  /**
   * Clear the daily rate cache
   */
  public static void clearCache() {
    dailyRateCache.clear();
  }

  /**
   * Fetch all daily rates for a specific year from CNB (bulk fetch)
   * URL format:
   * https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/rok.txt?rok=YYYY
   * 
   * @param year The year to fetch
   * @return Map of "CURRENCY|YYYY-MM-DD" -> Rate
   */
  public static Map<String, Double> fetchAnnualDailyRates(int year) throws Exception {
    Map<String, Double> results = new HashMap<String, Double>();
    String urlStr = "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/rok.txt?rok="
        + year;
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    try {
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        throw new Exception("CNB API returned response code " + responseCode + " for year " + year);
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
      String headerLine = reader.readLine();
      if (headerLine == null)
        throw new Exception("Empty response from CNB API");

      // Parse header to get currencies and multipliers
      // Datum|1 AUD|1 BGN|...|100 HUF|...
      String[] headers = headerLine.split("\\|");
      String[] curCodes = new String[headers.length];
      double[] multipliers = new double[headers.length];

      for (int i = 1; i < headers.length; i++) {
        String h = headers[i].trim();
        String[] hParts = h.split(" ");
        if (hParts.length == 2) {
          multipliers[i] = Double.parseDouble(hParts[0]);
          curCodes[i] = hParts[1].toUpperCase();
        }
      }

      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\\|");
        if (parts.length < 2)
          continue;

        // Datum: DD.MM.YYYY
        String dateParts[] = parts[0].split("\\.");
        if (dateParts.length != 3)
          continue;
        String isoDate = String.format("%s-%s-%s", dateParts[2], dateParts[1], dateParts[0]);

        for (int i = 1; i < parts.length && i < curCodes.length; i++) {
          if (curCodes[i] == null)
            continue;

          try {
            String rateStr = parts[i].trim().replace(',', '.');
            double rate = Double.parseDouble(rateStr);
            double normalizedRate = rate / multipliers[i];

            results.put(curCodes[i] + "|" + isoDate, normalizedRate);
          } catch (NumberFormatException e) {
            // Skip invalid rates
          }
        }
      }

      reader.close();
      return results;

    } finally {
      conn.disconnect();
    }
  }

  /**
   * Fetch daily rates for selected currencies for a specific year from CNB
   * @param year Year to fetch (e.g., 2024)
   * @param currencies List of currency codes to fetch (null means all currencies)
   * @return Map of "CURRENCY|YYYY-MM-DD" -> Rate
   */
  public static Map<String, Double> fetchSelectiveDailyRates(int year, java.util.List<String> currencies) throws Exception {
    Map<String, Double> results = new HashMap<String, Double>();
    String urlStr = "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/rok.txt?rok="
        + year;
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    try {
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        throw new Exception("CNB API returned response code " + responseCode + " for year " + year);
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
      String headerLine = reader.readLine();
      if (headerLine == null)
        throw new Exception("Empty response from CNB API");

      // Parse header to get currencies and multipliers
      // Datum|1 AUD|1 BGN|...|100 HUF|...
      String[] headers = headerLine.split("\\|");
      String[] curCodes = new String[headers.length];
      double[] multipliers = new double[headers.length];

      for (int i = 1; i < headers.length; i++) {
        String h = headers[i].trim();
        String[] hParts = h.split(" ");
        if (hParts.length == 2) {
          multipliers[i] = Double.parseDouble(hParts[0]);
          curCodes[i] = hParts[1].toUpperCase();
        }
      }

      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\\|");
        if (parts.length < 2)
          continue;

        // Datum: DD.MM.YYYY
        String dateParts[] = parts[0].split("\\.");
        if (dateParts.length != 3)
          continue;
        String isoDate = String.format("%s-%s-%s", dateParts[2], dateParts[1], dateParts[0]);

        for (int i = 1; i < parts.length && i < curCodes.length; i++) {
          if (curCodes[i] == null)
            continue;

          // Filter by requested currencies if specified
          if (currencies != null && !currencies.contains(curCodes[i])) {
            continue;
          }

          try {
            String rateStr = parts[i].trim().replace(',', '.');
            double rate = Double.parseDouble(rateStr);
            double normalizedRate = rate / multipliers[i];

            results.put(curCodes[i] + "|" + isoDate, normalizedRate);
          } catch (NumberFormatException e) {
            // Skip invalid rates
          }
        }
      }

      reader.close();
      return results;

    } finally {
      conn.disconnect();
    }
  }
}
