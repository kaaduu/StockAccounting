package cz.datesoft.stockAccounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.datesoft.stockAccounting.imp.ImportFirefish;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Vector;
import org.junit.jupiter.api.Test;

class ImportFirefishTest {

  @Test
  void importsClosedAndLiquidatedLoansAsInterestTransactions() throws Exception {
    ImportFirefish importer = new ImportFirefish();
    File src = new File("data/firefish/investments-statement-2026-04-22_aaa.csv");

    Vector<String[]> notImported = new Vector<String[]>();
    Vector<Transaction> txs = importer.doImport(src, null, null, notImported);

    assertEquals(6, txs.size(), "Fixture should produce one interest transaction per closed/liquidated loan");
    assertEquals(0, notImported.size(), "Fixture contains only importable statuses");

    Transaction first = txs.get(0);
    assertEquals("Kreditni.Urok", first.getTicker());
    assertEquals("Úrok", first.getStringType());
    assertEquals("Hrubá", first.getStringDirection());
    assertEquals(Double.valueOf(1.0), first.getAmount());
    assertEquals(Double.valueOf(3600.0), first.getPrice());
    assertEquals("CZK", first.getPriceCurrency());
    assertEquals("Firefish", first.getBroker());
    assertEquals("251168350/0600", first.getAccountId());
    assertEquals("1e2d1770", first.getTxnId());
    assertNotNull(first.getExecutionDate());
    assertEquals("24.2.2026 0:00", first.getStringDate());
    assertTrue(first.getNote().contains("TxnID:1e2d1770"));
    assertTrue(first.getNote().contains("InvestmentAmount:40000"));
    assertTrue(first.getNote().contains("AmountDue:43600"));
    assertTrue(first.getNote().contains("ClosedAt:02-24-26"));

    Transaction liquidated = txs.get(4);
    assertEquals("a5d88418", liquidated.getTxnId());
    assertEquals(Double.valueOf(450.0), liquidated.getPrice());
    assertTrue(liquidated.getNote().contains("Status:LIQUIDATED"));
  }

  @Test
  void acceptsSlashDatesWithFourDigitYear() throws Exception {
    ImportFirefish importer = new ImportFirefish();
    Path tempFile = Files.createTempFile("firefish-slash-dates-", ".csv");
    String csv = "Investment id,Start date (mm/dd/yyyy),Maturity date (mm/dd/yyyy),Interest rate (% p.a.),Currency,Investment amount,Amount due,Status,Closed at (mm/dd/yyyy),My account number,Collateral sum (BTC),Liquidation price,Investor id,Borrower id,Note,Loan type\n"
        + "abc123,1/15/2026,4/15/2026,7.00,CZK,35000,35604,CLOSED,2/11/2026,251168350/0600,0.05450200,687641,1800000.0000072,574ec3d8,,Standard\n";
    Files.writeString(tempFile, csv, StandardCharsets.UTF_8);

    try {
      Vector<Transaction> txs = importer.doImport(tempFile.toFile(), null, null, new Vector<String[]>());

      assertEquals(1, txs.size());
      Transaction tx = txs.get(0);
      assertEquals("abc123", tx.getTxnId());
      assertEquals("11.2.2026 0:00", tx.getStringDate());
      assertEquals(Double.valueOf(604.0), tx.getPrice());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void reportsIgnoredStatusesAndErrorsAsNotImportedWithReason() throws Exception {
    ImportFirefish importer = new ImportFirefish();
    Path tempFile = Files.createTempFile("firefish-not-imported-", ".csv");
    String csv = "Investment id,Start date (mm/dd/yyyy),Maturity date (mm/dd/yyyy),Interest rate (% p.a.),Currency,Investment amount,Amount due,Status,Closed at (mm/dd/yyyy),My account number,Collateral sum (BTC),Liquidation price,Investor id,Borrower id,Note,Loan type\n"
        + "ok1,1/15/2026,4/15/2026,7.00,CZK,35000,35604,CLOSED,2/11/2026,251168350/0600,0.1,687641,i1,b1,,Standard\n"
        + "skip1,1/15/2026,4/15/2026,7.00,CZK,35000,35604,ACTIVE,2/11/2026,251168350/0600,0.1,687641,i1,b1,,Standard\n"
        + "bad1,1/15/2026,4/15/2026,7.00,CZK,35000,35604,CLOSED,not-a-date,251168350/0600,0.1,687641,i1,b1,,Standard\n";
    Files.writeString(tempFile, csv, StandardCharsets.UTF_8);

    try {
      Vector<String[]> notImported = new Vector<String[]>();
      Vector<Transaction> txs = importer.doImport(tempFile.toFile(), null, null, notImported);

      assertEquals(1, txs.size());
      assertEquals(2, notImported.size());
      assertEquals("IGNORED_STATUS:ACTIVE", notImported.get(0)[0]);
      assertTrue(notImported.get(1)[0].startsWith("ERROR:"));
      assertEquals("skip1", notImported.get(0)[1]);
      assertEquals("bad1", notImported.get(1)[1]);
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void ignoresProvidedDateFilterAndStillImportsClosedLoans() throws Exception {
    ImportFirefish importer = new ImportFirefish();
    Path tempFile = Files.createTempFile("firefish-ignore-date-filter-", ".csv");
    String csv = "Investment id,Start date (mm/dd/yyyy),Maturity date (mm/dd/yyyy),Interest rate (% p.a.),Currency,Investment amount,Amount due,Status,Closed at (mm/dd/yyyy),My account number,Collateral sum (BTC),Liquidation price,Investor id,Borrower id,Note,Loan type\n"
        + "ok1,1/15/2026,4/15/2026,7.00,CZK,35000,35604,CLOSED,2/11/2026,251168350/0600,0.1,687641,i1,b1,,Standard\n";
    Files.writeString(tempFile, csv, StandardCharsets.UTF_8);

    try {
      java.util.Date restrictiveStart = java.util.Date
          .from(LocalDate.of(2026, 12, 31).atStartOfDay(ZoneId.systemDefault()).toInstant());
      Vector<Transaction> txs = importer.doImport(tempFile.toFile(), restrictiveStart, restrictiveStart,
          new Vector<String[]>());

      assertEquals(1, txs.size(), "Firefish local CSV should ignore Importovat od/do filters");
      assertEquals("ok1", txs.get(0).getTxnId());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }
}
