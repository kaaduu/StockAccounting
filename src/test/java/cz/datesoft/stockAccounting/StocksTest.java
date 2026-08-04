package cz.datesoft.stockAccounting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StocksTest {

  @Test
  void stockTradeAtSameTimestampAfterTransformationPairIsProcessedNormally() throws Exception {
    Date purchaseDate = TransactionSet.parseDate("2.10.2024 10:00");
    Date transformationDate = TransactionSet.parseDate("3.10.2024 20:25");
    Stocks stocks = new Stocks();

    Transaction purchase = transaction(1, purchaseDate, Transaction.DIRECTION_SBUY, "LSB", 400.0, 1.0);
    Transaction transformationSub = transaction(2, transformationDate, Transaction.DIRECTION_TRANS_SUB,
        "LSB", 400.0, 0.0);
    Transaction transformationAdd = transaction(3, transformationDate, Transaction.DIRECTION_TRANS_ADD,
        "LSBCF", 40.0, 0.0);
    Transaction sale = transaction(4, transformationDate, Transaction.DIRECTION_SSELL, "LSBCF", 40.0, 0.066);

    assertDoesNotThrow(() -> {
      stocks.applyTransaction(purchase, false);
      stocks.applyTransaction(transformationSub, false);
      stocks.applyTransaction(transformationAdd, false);
      stocks.applyTransaction(sale, false);
      stocks.finishTransformations();
    });
    assertEquals(0, stocks.getStockTickers().length,
        "The stock sale must be processed normally and close the transformed LSBCF position");
  }

  @Test
  void transformationPairAtDifferentTimestampStartsAfterPreviousPairIsFinished() throws Exception {
    Date purchaseDate = TransactionSet.parseDate("1.10.2024 10:00");
    Date firstTransformationDate = TransactionSet.parseDate("2.10.2024 10:00");
    Date secondTransformationDate = TransactionSet.parseDate("3.10.2024 10:00");
    Stocks stocks = new Stocks();

    assertDoesNotThrow(() -> {
      stocks.applyTransaction(transaction(1, purchaseDate, Transaction.DIRECTION_SBUY, "AAA", 100.0, 1.0), false);
      stocks.applyTransaction(
          transaction(2, firstTransformationDate, Transaction.DIRECTION_TRANS_SUB, "AAA", 100.0, 0.0), false);
      stocks.applyTransaction(
          transaction(3, firstTransformationDate, Transaction.DIRECTION_TRANS_ADD, "BBB", 10.0, 0.0), false);
      stocks.applyTransaction(
          transaction(4, secondTransformationDate, Transaction.DIRECTION_TRANS_SUB, "BBB", 10.0, 0.0), false);
      stocks.applyTransaction(
          transaction(5, secondTransformationDate, Transaction.DIRECTION_TRANS_ADD, "CCC", 5.0, 0.0), false);
      stocks.finishTransformations();
    });
    assertEquals(Set.of("CCC"), tickerSet(stocks));
  }

  @Test
  void multipleTransformationPairsAtSameTimestampRemainSupported() throws Exception {
    Date purchaseDate = TransactionSet.parseDate("1.10.2024 10:00");
    Date transformationDate = TransactionSet.parseDate("2.10.2024 10:00");
    Stocks stocks = new Stocks();

    assertDoesNotThrow(() -> {
      stocks.applyTransaction(transaction(1, purchaseDate, Transaction.DIRECTION_SBUY, "AAA", 100.0, 1.0), false);
      stocks.applyTransaction(transaction(2, purchaseDate, Transaction.DIRECTION_SBUY, "CCC", 50.0, 1.0), false);
      stocks.applyTransaction(
          transaction(3, transformationDate, Transaction.DIRECTION_TRANS_SUB, "AAA", 100.0, 0.0), false);
      stocks.applyTransaction(
          transaction(4, transformationDate, Transaction.DIRECTION_TRANS_ADD, "BBB", 10.0, 0.0), false);
      stocks.applyTransaction(
          transaction(5, transformationDate, Transaction.DIRECTION_TRANS_SUB, "CCC", 50.0, 0.0), false);
      stocks.applyTransaction(
          transaction(6, transformationDate, Transaction.DIRECTION_TRANS_ADD, "DDD", 5.0, 0.0), false);
      stocks.finishTransformations();
    });
    assertEquals(Set.of("BBB", "DDD"), tickerSet(stocks));
  }

  @Test
  void transformationsWithSameDirectionAreStillRejected() throws Exception {
    Date purchaseDate = TransactionSet.parseDate("1.10.2024 10:00");
    Date transformationDate = TransactionSet.parseDate("2.10.2024 10:00");
    Stocks stocks = new Stocks();

    stocks.applyTransaction(transaction(1, purchaseDate, Transaction.DIRECTION_SBUY, "AAA", 100.0, 1.0), false);
    stocks.applyTransaction(
        transaction(2, transformationDate, Transaction.DIRECTION_TRANS_SUB, "AAA", 100.0, 0.0), false);

    Stocks.TradingException exception = assertThrows(Stocks.TradingException.class,
        () -> stocks.applyTransaction(
            transaction(3, transformationDate, Transaction.DIRECTION_TRANS_SUB, "BBB", 10.0, 0.0), false));
    assertTrue(exception.getMessage().contains("stejného typu"));
  }

  private static Transaction transaction(int serial, Date date, int direction, String ticker, double amount,
      double price) throws Exception {
    return new Transaction(serial, date, direction, ticker, amount, price, "CZK", 0.0, "CZK", "", date, null);
  }

  private static Set<String> tickerSet(Stocks stocks) {
    return new HashSet<>(Arrays.asList(stocks.getStockTickers()));
  }
}
