package cz.datesoft.stockAccounting.export;

import cz.datesoft.stockAccounting.Transaction;
import java.io.PrintWriter;
import java.io.IOException;
import java.text.DecimalFormat;

/**
 * TransactionExporter provides export functionality for transactions.
 * This class separates export concerns from the Transaction entity,
 * following the Single Responsibility Principle.
 */
public class TransactionExporter {

  private static final DecimalFormat FF = new DecimalFormat("#.#######");

  /**
   * Exports a transaction to standard CSV format.
   *
   * @param transaction the transaction to export
   * @param ofl the PrintWriter to write to
   * @throws IOException if an I/O error occurs
   */
  public static void exportTransaction(Transaction transaction, PrintWriter ofl) throws IOException {
    ofl.println(transaction.getStringDate() + ";" + transaction.getStringType() + ";"
        + transaction.getStringDirection() + ";" + transaction.getTicker() + ";" + transaction.getAmount() + ";"
        + transaction.getPrice() + ";" + transaction.getPriceCurrency() + ";" + transaction.getFee() + ";"
        + transaction.getFeeCurrency() + ";" + transaction.getMarket() + ";" + transaction.getStringExecutionDate()
        + ";" + transaction.getBroker() + ";" + transaction.getAccountId() + ";" + transaction.getTxnId() + ";"
        + transaction.getIsin() + ";" + transaction.getIssuerCountry() + ";" + transaction.getEffect() + ";"
        + transaction.getNote());
  }

  /**
   * Exports a transaction to FIO bank format.
   * FIO format is specific for import to kacka.baldsoft.com and
   * only supports securities (Cenné papíry - "CP").
   *
   * @param transaction the transaction to export
   * @param ofl the PrintWriter to write to
   * @throws IOException if an I/O error occurs
   */
  public static void exportToFIO(Transaction transaction, PrintWriter ofl) throws IOException {
    // Export je pro kacka.baldsoft.com a ta nepodporuje derivaty ani transformace
    // takze pouze exportuje Cenne papiry
    if (transaction.getStringType().equals("CP")) {
      // price 10.22 but FIO use czech locale we need 10,22 how silly
      String price_s = String.valueOf(transaction.getPrice()).replace(".", ",");

      // getStringDirection() Nakup (-amount) vs Prodej(+amount)
      int sign = 0;
      if (transaction.getStringDirection().equals("Nákup")) {
        sign = -1;
      } else {
        sign = 1;
      }

      // Get fee
      String ObjemUSD = "";
      String ObjemCZK = "";
      String ObjemEUR = "";
      if (transaction.getPriceCurrency().equals("USD")) {
        ObjemUSD = String.valueOf(FF.format(transaction.getPrice() * transaction.getAmount() * sign)).replace(".", ",");
      }
      if (transaction.getPriceCurrency().equals("EUR")) {
        ObjemEUR = String.valueOf(FF.format(transaction.getPrice() * transaction.getAmount() * sign)).replace(".", ",");
      }
      if (transaction.getPriceCurrency().equals("CZK")) {
        ObjemCZK = String.valueOf(FF.format(transaction.getPrice() * transaction.getAmount() * sign)).replace(".", ",");
      }

      String PoplatkyUSD = "";
      String PoplatkyCZK = "";
      String PoplatkyEUR = "";
      switch (transaction.getFeeCurrency()) {
        case "USD":
          PoplatkyUSD = String.valueOf(transaction.getFee()).replace(".", ",");
          break;
        case "EUR":
          PoplatkyEUR = String.valueOf(transaction.getFee()).replace(".", ",");
          break;
        case "CZK":
          PoplatkyCZK = String.valueOf(transaction.getFee()).replace(".", ",");
          break;
        default:
          ;
      }
      String ExeDate[] = transaction.getStringExecutionDate().split(" ", 0);
      String amount_s = String.valueOf(Math.abs(transaction.getAmount())).replace(".", ",");
      String Text = transaction.getStringDate() + ";" + transaction.getStringDirection() + ";" + transaction.getTicker()
          + ";" + price_s + ";" + amount_s + ";" + transaction.getPriceCurrency() + ";" + ObjemCZK + ";"
          + PoplatkyCZK + ";" + ObjemUSD + ";" + PoplatkyUSD + ";" + ObjemEUR + ";" + PoplatkyEUR + ";"
          + transaction.getNote() + ";" + ExeDate[0];

      ofl.println(Text);
    } // is CP only
  }
}
