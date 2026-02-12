/*
 * FXRateChecker.java
 *
 * Checks for missing FX exchange rates after import
 */

package cz.datesoft.stockAccounting;

import java.util.*;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Utility class for checking missing FX exchange rates
 * 
 * @author Antigravity AI
 */
public class FXRateChecker {

  /**
   * Result of FX rate check
   */
  public static class RatesCheckResult {
    public final Set<String> currencies;
    public final Set<Integer> years;
    public final Set<String> missingDailyRates;
    public final Map<String, Set<Integer>> missingUnifiedRates;
    public final boolean hasMissingRates;

    public RatesCheckResult(Set<String> currencies, Set<Integer> years,
        Set<String> missingDailyRates, Map<String, Set<Integer>> missingUnifiedRates) {
      this.currencies = currencies;
      this.years = years;
      this.missingDailyRates = missingDailyRates;
      this.missingUnifiedRates = missingUnifiedRates;
      this.hasMissingRates = !missingDailyRates.isEmpty() || !missingUnifiedRates.isEmpty();
    }

    public boolean hasMissingDailyRates() {
      return !missingDailyRates.isEmpty();
    }

    public boolean hasMissingUnifiedRates() {
      return !missingUnifiedRates.isEmpty();
    }

    public int getMissingDailyCount() {
      return missingDailyRates.size();
    }

    public int getMissingUnifiedCount() {
      int count = 0;
      for (Set<Integer> years : missingUnifiedRates.values()) {
        count += years.size();
      }
      return count;
    }
  }

  /**
   * Check for missing FX rates in given transaction set
   * 
   * @param transactions Transaction set to check
   * @return RatesCheckResult containing information about missing rates
   */
  public static RatesCheckResult checkForMissingRates(TransactionSet transactions) {
    AppLog.info("FX Rate Check: Spouštím kontrolu pro " + 
        (transactions != null ? transactions.getRowCount() : 0) + " transakcí");
    System.out.println("[FXRATES:CHECK:001] FX rate check started - transactions: " + 
        (transactions != null ? transactions.getRowCount() : 0));

    Set<String> currencies = Settings.getUsedCurrencies(transactions);
    Set<Integer> years = getUsedYears(transactions);
    
    System.out.println("[FXRATES:CHECK:002]   - Currencies found: " + currencies);
    System.out.println("[FXRATES:CHECK:003]   - Years found: " + years);
    
    Set<String> missingDailyRates = new HashSet<>();
    Map<String, Set<Integer>> missingUnifiedRates = new HashMap<>();

    if (transactions == null || transactions.getRowCount() == 0) {
      return new RatesCheckResult(currencies, years, missingDailyRates, missingUnifiedRates);
    }

    boolean useDailyRates = Settings.getUseDailyRates();

    GregorianCalendar cal = new GregorianCalendar();
    for (Iterator<Transaction> i = transactions.iterator(); i.hasNext();) {
      Transaction tx = i.next();
      Date executionDate = tx.getExecutionDate();
      cal.setTime(executionDate);
      int year = cal.get(Calendar.YEAR);

      String priceCurrency = tx.getPriceCurrency();
      String feeCurrency = tx.getFeeCurrency();

      if (useDailyRates) {
        if (priceCurrency != null && !priceCurrency.equalsIgnoreCase("CZK")) {
          if (!hasDailyRate(priceCurrency, executionDate)) {
            String dateStr = formatDate(executionDate);
            missingDailyRates.add(priceCurrency.toUpperCase() + "|" + dateStr);
          }
        }

        if (feeCurrency != null && !feeCurrency.equalsIgnoreCase("CZK")) {
          if (!hasDailyRate(feeCurrency, executionDate)) {
            String dateStr = formatDate(executionDate);
            missingDailyRates.add(feeCurrency.toUpperCase() + "|" + dateStr);
          }
        }
      }

      for (String currency : currencies) {
        if (!currency.equalsIgnoreCase("CZK")) {
          if (!hasUnifiedRate(currency, year)) {
            missingUnifiedRates.computeIfAbsent(currency, k -> new HashSet<>()).add(year);
          }
        }
      }
    }

    if (missingDailyRates.isEmpty() && missingUnifiedRates.isEmpty()) {
      AppLog.info("FX Rate Check: Žádné chybějící kurzy nenalezeny");
      System.out.println("[FXRATES:CHECK:004] ✅ FX rate check completed - NO missing rates");
    } else {
      AppLog.warn(String.format("FX Rate Check: Nalezeny chybějící kurzy - denní: %d, jednotné: %d, měny: %s, roky: %s",
          missingDailyRates.size(), missingUnifiedRates.size(), currencies, years));
      System.out.println("[FXRATES:CHECK:005] ⚠ FX rate check completed - FOUND missing rates:");
      System.out.println("[FXRATES:CHECK:006]   - Missing daily rates: " + missingDailyRates.size());
      System.out.println("[FXRATES:CHECK:007]   - Missing unified rates: " + 
          (missingUnifiedRates.values().stream().mapToInt(Set::size).sum()));
      if (!currencies.isEmpty()) {
        System.out.println("[FXRATES:CHECK:008]   - Currencies with missing rates: " + currencies);
      }
      if (!years.isEmpty()) {
        System.out.println("[FXRATES:CHECK:009]   - Years with missing rates: " + years);
      }
    }

    return new RatesCheckResult(currencies, years, missingDailyRates, missingUnifiedRates);
  }

  /**
   * Get years used in the transaction set
   */
  private static Set<Integer> getUsedYears(TransactionSet transactions) {
    Set<Integer> years = new HashSet<>();
    if (transactions != null) {
      GregorianCalendar cal = new GregorianCalendar();
      for (Iterator<Transaction> i = transactions.iterator(); i.hasNext();) {
        Transaction tx = i.next();
        cal.setTime(tx.getExecutionDate());
        years.add(cal.get(Calendar.YEAR));
      }
    }
    return years;
  }

  /**
   * Check if daily rate exists for given currency and date
   */
  private static boolean hasDailyRate(String currency, Date date) {
    if (currency == null || currency.equalsIgnoreCase("CZK")) {
      return true;
    }

    Map<String, Double> dailyRates = Settings.getDailyRates();
    if (dailyRates == null) {
      return false;
    }

    currency = currency.toUpperCase();

    try {
      for (int i = 0; i < 7; i++) {
        String key = currency + "|" + formatDate(date);
        if (dailyRates.containsKey(key)) {
          return true;
        }
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        date = cal.getTime();
      }
    } catch (Exception e) {
      return false;
    }

    return false;
  }

  /**
   * Check if unified rate exists for given currency and year
   */
  private static boolean hasUnifiedRate(String currency, int year) {
    if (currency == null || currency.equalsIgnoreCase("CZK")) {
      return true;
    }

    try {
      Settings.getRatio(currency, year);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Format date to YYYY-MM-DD string
   */
  private static String formatDate(Date date) {
    GregorianCalendar cal = new GregorianCalendar();
    cal.setTime(date);
    return String.format("%d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH));
  }
}
