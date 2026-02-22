package cz.datesoft.stockAccounting;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.util.GregorianCalendar;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * TransactionRepository handles persistence operations for transaction data.
 * This class separates persistence concerns from the TransactionSet data model,
 * following Single Responsibility Principle.
 */
public class TransactionRepository {

  private static final Logger logger = Logger.getLogger(TransactionRepository.class.getName());

  /**
   * Saves transaction data to a file.
   *
   * @param transactions Vector of transactions to save
   * @param serialCounter Current serial counter value
   * @param lastDateSet Last date that was set
   * @param dstFile Destination file
   * @throws java.io.FileNotFoundException if file cannot be created
   * @throws java.io.IOException if I/O error occurs
   */
  public static void save(Vector<Transaction> transactions, int serialCounter,
                       java.util.Date lastDateSet, File dstFile)
      throws java.io.FileNotFoundException, java.io.IOException {
    PrintWriter ofl = new PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(dstFile)));

    try {
      GregorianCalendar cal = new GregorianCalendar();

      ofl.println("version=1");
      ofl.println("serialCounter=" + serialCounter);
      if (lastDateSet == null)
        lastDateSet = getMaxDate(transactions);
      if (lastDateSet == null) {
        ofl.println("lastDateSet=null");
      } else {
        cal.setTime(lastDateSet);
        ofl.println("lastDateSet=" + cal.get(GregorianCalendar.DAY_OF_MONTH) + "."
            + (cal.get(GregorianCalendar.MONTH) + 1) + "." + cal.get(GregorianCalendar.YEAR) + " "
            + cal.get(GregorianCalendar.HOUR_OF_DAY) + ":" + cal.get(GregorianCalendar.MINUTE));
      }

      for (int i = 0; i < transactions.size(); i++) {
        Transaction tx = transactions.elementAt(i);

        if (tx.isFilledIn()) {
          ofl.println("row (");
          tx.save(ofl, "  ");
          ofl.println(")");
        }
      }
    } finally {
      try {
        ofl.close();
      } catch (Exception e) {
        logger.warning("Failed to close output file: " + e.getMessage());
      }
    }
  }

  /**
   * Loads transaction data from a file.
   *
   * @param srcFile Source file to load from
   * @param transformationCache Transformation cache for ticker relationships
   * @return LoadedData container with transactions, serialCounter, and lastDateSet
   * @throws java.io.FileNotFoundException if file not found
   * @throws java.io.IOException if I/O error occurs
   */
  public static LoadedData load(File srcFile, TransformationCache transformationCache)
      throws java.io.FileNotFoundException, java.io.IOException {
    try (BufferedReader ifl = new BufferedReader(new java.io.FileReader(srcFile))) {
      String a[];

      LoadedData data = new LoadedData();
      data.transactions = new Vector<Transaction>();
      data.serialCounter = 1;
      data.lastDateSet = null;

      a = readLine(ifl);

      if (a[0] == null) {
        return data;
      }

      if (!a[0].equals("version")) {
        return data;
      }

      if (!a[1].equals("1")) {
        return data;
      }

      for (;;) {
        a = readLine(ifl);
        if (a[0] == null)
          break;

        if (a[0].equals("row (")) {
          Transaction tx = new Transaction(ifl);
          data.transactions.add(tx);
        } else if (a[0].equals("serialCounter"))
          data.serialCounter = Integer.parseInt(a[1]);
        else if (a[0].equals("lastDateSet")) {
          if (a[1].equalsIgnoreCase("null"))
            data.lastDateSet = null;
          else
            data.lastDateSet = parseDate(a[1]);
        }
      }

      return data;
    }
  }

  /**
   * Loads transaction data from a file and merges with existing data.
   *
   * @param srcFile Source file to load from
   * @param transformationCache Transformation cache for ticker relationships
   * @return LoadedData container with transactions, serialCounter, and lastDateSet
   * @throws java.io.FileNotFoundException if file not found
   * @throws java.io.IOException if I/O error occurs
   */
  public static LoadedData loadAdd(File srcFile, TransformationCache transformationCache)
      throws java.io.FileNotFoundException, java.io.IOException {
    BufferedReader ifl = new BufferedReader(new java.io.FileReader(srcFile));
    String a[];

    LoadedData data = new LoadedData();
    data.transactions = new Vector<Transaction>();
    data.serialCounter = 1;
    data.lastDateSet = null;

    a = readLine(ifl);

    if (a[0] == null) {
      ifl.close();
      return data;
    }

    if (!a[0].equals("version")) {
      ifl.close();
      return data;
    }

    if (!a[1].equals("1")) {
      ifl.close();
      return data;
    }

    for (;;) {
      a = readLine(ifl);
      if (a[0] == null)
        break;

      if (a[0].equals("row (")) {
        Transaction tx = new Transaction(ifl);
        data.transactions.add(tx);
      } else if (a[0].equals("serialCounter"))
        data.serialCounter = Integer.parseInt(a[1]);
      else if (a[0].equals("lastDateSet")) {
        if (a[1].equalsIgnoreCase("null"))
          data.lastDateSet = null;
        else
          data.lastDateSet = parseDate(a[1]);
      }
    }

    ifl.close();
    return data;
  }

  private static String[] readLine(BufferedReader ifl) throws java.io.IOException {
    String line = ifl.readLine();
    if (line == null)
      return new String[] { null };

    String parts[] = line.split("=", 2);
    return parts;
  }

  private static java.util.Date parseDate(String dateStr) {
    String parts[] = dateStr.split("[. ]");
    int day = Integer.parseInt(parts[0]);
    int month = Integer.parseInt(parts[1]);
    int year = Integer.parseInt(parts[2]);
    String timeParts[] = parts[3].split(":");
    int hour = Integer.parseInt(timeParts[0]);
    int minute = Integer.parseInt(timeParts[1]);

    GregorianCalendar cal = new GregorianCalendar(year, month - 1, day, hour, minute);
    return cal.getTime();
  }

  private static java.util.Date getMaxDate(Vector<Transaction> transactions) {
    java.util.Date maxDate = null;
    for (Transaction tx : transactions) {
      if (tx.getDate() != null) {
        if (maxDate == null || tx.getDate().after(maxDate)) {
          maxDate = tx.getDate();
        }
      }
    }
    return maxDate;
  }

  /**
   * Container class for data loaded from file.
   */
  public static class LoadedData {
    public Vector<Transaction> transactions;
    public int serialCounter;
    public java.util.Date lastDateSet;
  }
}
