package cz.datesoft.stockAccounting;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Types;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EWrapper;
import com.ib.client.protobuf.MarketDepthExchangesProto;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Minimal TWS API client to fetch positions (stocks only).
 *
 * This project uses the IB API version with protobuf-enabled EWrapper, so we
 * provide stubs for all interface methods.
 */
public final class IbkrTwsPositionsClient implements EWrapper {
  private static final Logger logger = Logger.getLogger(IbkrTwsPositionsClient.class.getName());

  public static final class PositionsResult {
    public final Map<String, Map<String, Double>> positionsByAccount;
    public final Set<String> errors;

    PositionsResult(Map<String, Map<String, Double>> positionsByAccount, Set<String> errors) {
      this.positionsByAccount = positionsByAccount;
      this.errors = errors;
    }
  }

  private final EJavaSignal signal = new EJavaSignal();
  private final EClientSocket client = new EClientSocket(this, signal);

  private final Map<String, Map<String, Double>> positionsByAccount = new HashMap<>();
  private final Set<String> errors = new HashSet<>();

  private CountDownLatch positionsDone;

  public PositionsResult fetchPositions(String host, int port, int clientId, Duration timeout) throws Exception {
    positionsByAccount.clear();
    errors.clear();
    positionsDone = new CountDownLatch(1);

    logger.info("Connecting to TWS: " + host + ":" + port + " clientId=" + clientId);
    client.eConnect(host, port, clientId);
    if (!client.isConnected()) {
      throw new Exception("TWS: nepodařilo se připojit");
    }

    final EReader reader = new EReader(client, signal);
    reader.start();
    Thread readerThread = new Thread(() -> {
      try {
        while (client.isConnected()) {
          signal.waitForSignal();
          reader.processMsgs();
        }
      } catch (Exception e) {
        errors.add("Reader: " + e.getMessage());
      }
    }, "tws-api-reader");
    readerThread.setDaemon(true);
    readerThread.start();

    client.reqPositions();

    boolean ok = positionsDone.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    try {
      client.cancelPositions();
    } catch (Exception e) {
      // ignore
    }
    try {
      client.eDisconnect();
    } catch (Exception e) {
      // ignore
    }

    if (!ok) {
      throw new Exception("TWS: timeout při načítání pozic");
    }
    return new PositionsResult(copyPositions(), new HashSet<>(errors));
  }

  private Map<String, Map<String, Double>> copyPositions() {
    Map<String, Map<String, Double>> copy = new HashMap<>();
    for (Map.Entry<String, Map<String, Double>> e : positionsByAccount.entrySet()) {
      copy.put(e.getKey(), new HashMap<>(e.getValue()));
    }
    return copy;
  }

  @Override
  public void position(String account, Contract contract, Decimal pos, double avgCost) {
    if (contract == null) return;
    if (contract.secType() == null || contract.secType() != Types.SecType.STK) return;
    String symbol = contract.symbol();
    if (symbol == null || symbol.trim().isEmpty()) return;

    double p = pos == null ? 0.0 : pos.value().doubleValue();
    String acc = account == null ? "" : account;
    positionsByAccount.computeIfAbsent(acc, k -> new HashMap<>()).put(symbol.trim().toUpperCase(), p);
  }

  @Override
  public void positionEnd() {
    if (positionsDone != null) {
      positionsDone.countDown();
    }
  }

  @Override
  public void error(Exception e) {
    if (e != null) errors.add(e.getMessage());
  }

  @Override
  public void error(String str) {
    if (str != null) errors.add(str);
  }

  @Override
  public void error(int reqId, long time, int errorCode, String errorMsg, String advancedOrderRejectJson) {
    errors.add("TWS error " + errorCode + ": " + errorMsg);
  }

  // ----- stubs (unused) -----
  @Override public void tickPrice(int tickerId, int field, double price, com.ib.client.TickAttrib attribs) {}
  @Override public void tickSize(int tickerId, int field, Decimal size) {}
  @Override public void tickOptionComputation(int tickerId, int field, int tickAttrib, double impliedVol, double delta,
      double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {}
  @Override public void tickGeneric(int tickerId, int tickType, double value) {}
  @Override public void tickString(int tickerId, int tickType, String value) {}
  @Override public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture,
      int holdDays, String futureLastTradeDate, double dividendImpact, double dividendsToLastTradeDate) {}
  @Override public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining, double avgFillPrice, long permId,
      int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {}
  @Override public void openOrder(int orderId, Contract contract, com.ib.client.Order order, com.ib.client.OrderState orderState) {}
  @Override public void openOrderEnd() {}
  @Override public void updateAccountValue(String key, String value, String currency, String accountName) {}
  @Override public void updatePortfolio(Contract contract, Decimal position, double marketPrice, double marketValue, double averageCost,
      double unrealizedPNL, double realizedPNL, String accountName) {}
  @Override public void updateAccountTime(String timeStamp) {}
  @Override public void accountDownloadEnd(String accountName) {}
  @Override public void nextValidId(int orderId) {}
  @Override public void contractDetails(int reqId, com.ib.client.ContractDetails contractDetails) {}
  @Override public void bondContractDetails(int reqId, com.ib.client.ContractDetails contractDetails) {}
  @Override public void contractDetailsEnd(int reqId) {}
  @Override public void execDetails(int reqId, Contract contract, com.ib.client.Execution execution) {}
  @Override public void execDetailsEnd(int reqId) {}
  @Override public void updateMktDepth(int tickerId, int position, int operation, int side, double price, Decimal size) {}
  @Override public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, Decimal size,
      boolean isSmartDepth) {}
  @Override public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {}
  @Override public void managedAccounts(String accountsList) {}
  @Override public void receiveFA(int faDataType, String xml) {}
  @Override public void historicalData(int reqId, com.ib.client.Bar bar) {}
  @Override public void scannerParameters(String xml) {}
  @Override public void scannerData(int reqId, int rank, com.ib.client.ContractDetails contractDetails, String distance, String benchmark,
      String projection, String legsStr) {}
  @Override public void scannerDataEnd(int reqId) {}
  @Override public void realtimeBar(int reqId, long time, double open, double high, double low, double close, Decimal volume, Decimal wap,
      int count) {}
  @Override public void currentTime(long time) {}
  @Override public void fundamentalData(int reqId, String data) {}
  @Override public void deltaNeutralValidation(int reqId, com.ib.client.DeltaNeutralContract deltaNeutralContract) {}
  @Override public void tickSnapshotEnd(int reqId) {}
  @Override public void marketDataType(int reqId, int marketDataType) {}
  @Override public void commissionAndFeesReport(com.ib.client.CommissionAndFeesReport commissionAndFeesReport) {}
  @Override public void accountSummary(int reqId, String account, String tag, String value, String currency) {}
  @Override public void accountSummaryEnd(int reqId) {}
  @Override public void verifyMessageAPI(String apiData) {}
  @Override public void verifyCompleted(boolean isSuccessful, String errorText) {}
  @Override public void verifyAndAuthMessageAPI(String apiData, String xyzChallange) {}
  @Override public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {}
  @Override public void displayGroupList(int reqId, String groups) {}
  @Override public void displayGroupUpdated(int reqId, String contractInfo) {}
  @Override public void connectionClosed() {}
  @Override public void connectAck() {}
  @Override public void positionMulti(int reqId, String account, String modelCode, Contract contract, Decimal pos, double avgCost) {}
  @Override public void positionMultiEnd(int reqId) {}
  @Override public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value, String currency) {}
  @Override public void accountUpdateMultiEnd(int reqId) {}
  @Override public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId, String tradingClass,
      String multiplier, Set<String> expirations, Set<Double> strikes) {}
  @Override public void securityDefinitionOptionalParameterEnd(int reqId) {}
  @Override public void softDollarTiers(int reqId, com.ib.client.SoftDollarTier[] tiers) {}
  @Override public void familyCodes(com.ib.client.FamilyCode[] familyCodes) {}
  @Override public void symbolSamples(int reqId, com.ib.client.ContractDescription[] contractDescriptions) {}
  @Override public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {}
  @Override public void mktDepthExchanges(com.ib.client.DepthMktDataDescription[] depthMktDataDescriptions) {}
  @Override public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline, String extraData) {}
  @Override public void smartComponents(int reqId, Map<Integer, Map.Entry<String, Character>> theMap) {}
  @Override public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {}
  @Override public void newsProviders(com.ib.client.NewsProvider[] newsProviders) {}
  @Override public void newsArticle(int requestId, int articleType, String articleText) {}
  @Override public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {}
  @Override public void historicalNewsEnd(int requestId, boolean hasMore) {}
  @Override public void headTimestamp(int reqId, String headTimestamp) {}
  @Override public void histogramData(int reqId, java.util.List<com.ib.client.HistogramEntry> items) {}
  @Override public void historicalDataUpdate(int reqId, com.ib.client.Bar bar) {}
  @Override public void rerouteMktDataReq(int reqId, int conid, String exchange) {}
  @Override public void rerouteMktDepthReq(int reqId, int conid, String exchange) {}
  @Override public void marketRule(int marketRuleId, com.ib.client.PriceIncrement[] priceIncrements) {}
  @Override public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {}
  @Override public void pnlSingle(int reqId, Decimal pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {}
  @Override public void historicalTicks(int reqId, java.util.List<com.ib.client.HistoricalTick> ticks, boolean done) {}
  @Override public void historicalTicksBidAsk(int reqId, java.util.List<com.ib.client.HistoricalTickBidAsk> ticks, boolean done) {}
  @Override public void historicalTicksLast(int reqId, java.util.List<com.ib.client.HistoricalTickLast> ticks, boolean done) {}
  @Override public void tickByTickAllLast(int reqId, int tickType, long time, double price, Decimal size, com.ib.client.TickAttribLast tickAttribLast,
      String exchange, String specialConditions) {}
  @Override public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, Decimal bidSize, Decimal askSize,
      com.ib.client.TickAttribBidAsk tickAttribBidAsk) {}
  @Override public void tickByTickMidPoint(int reqId, long time, double midPoint) {}
  @Override public void orderBound(long orderId, int apiClientId, int apiOrderId) {}
  @Override public void completedOrder(Contract contract, com.ib.client.Order order, com.ib.client.OrderState orderState) {}
  @Override public void completedOrdersEnd() {}
  @Override public void replaceFAEnd(int reqId, String text) {}
  @Override public void wshMetaData(int reqId, String dataJson) {}
  @Override public void wshEventData(int reqId, String dataJson) {}
  @Override public void historicalSchedule(int reqId, String startDateTime, String endDateTime, String timeZone, java.util.List<com.ib.client.HistoricalSession> sessions) {}
  @Override public void userInfo(int reqId, String whiteBrandingId) {}
  @Override public void currentTimeInMillis(long time) {}

  // ProtoBuf callbacks: ignore
  @Override public void orderStatusProtoBuf(com.ib.client.protobuf.OrderStatusProto.OrderStatus orderStatus) {}
  @Override public void openOrderProtoBuf(com.ib.client.protobuf.OpenOrderProto.OpenOrder openOrder) {}
  @Override public void openOrdersEndProtoBuf(com.ib.client.protobuf.OpenOrdersEndProto.OpenOrdersEnd openOrdersEnd) {}
  @Override public void errorProtoBuf(com.ib.client.protobuf.ErrorMessageProto.ErrorMessage errorMessage) {}
  @Override public void execDetailsProtoBuf(com.ib.client.protobuf.ExecutionDetailsProto.ExecutionDetails executionDetails) {}
  @Override public void execDetailsEndProtoBuf(com.ib.client.protobuf.ExecutionDetailsEndProto.ExecutionDetailsEnd executionDetailsEnd) {}
  @Override public void completedOrderProtoBuf(com.ib.client.protobuf.CompletedOrderProto.CompletedOrder completedOrder) {}
  @Override public void completedOrdersEndProtoBuf(com.ib.client.protobuf.CompletedOrdersEndProto.CompletedOrdersEnd completedOrdersEnd) {}
  @Override public void orderBoundProtoBuf(com.ib.client.protobuf.OrderBoundProto.OrderBound orderBound) {}
  @Override public void contractDataProtoBuf(com.ib.client.protobuf.ContractDataProto.ContractData contractData) {}
  @Override public void bondContractDataProtoBuf(com.ib.client.protobuf.ContractDataProto.ContractData contractData) {}
  @Override public void contractDataEndProtoBuf(com.ib.client.protobuf.ContractDataEndProto.ContractDataEnd contractDataEnd) {}
  @Override public void tickPriceProtoBuf(com.ib.client.protobuf.TickPriceProto.TickPrice tickPrice) {}
  @Override public void tickSizeProtoBuf(com.ib.client.protobuf.TickSizeProto.TickSize tickSize) {}
  @Override public void tickOptionComputationProtoBuf(com.ib.client.protobuf.TickOptionComputationProto.TickOptionComputation tickOptionComputation) {}
  @Override public void tickGenericProtoBuf(com.ib.client.protobuf.TickGenericProto.TickGeneric tickGeneric) {}
  @Override public void tickStringProtoBuf(com.ib.client.protobuf.TickStringProto.TickString tickString) {}
  @Override public void tickSnapshotEndProtoBuf(com.ib.client.protobuf.TickSnapshotEndProto.TickSnapshotEnd tickSnapshotEnd) {}
  @Override public void updateMarketDepthProtoBuf(com.ib.client.protobuf.MarketDepthProto.MarketDepth marketDepth) {}
  @Override public void updateMarketDepthL2ProtoBuf(com.ib.client.protobuf.MarketDepthL2Proto.MarketDepthL2 marketDepthL2) {}
  @Override public void marketDataTypeProtoBuf(com.ib.client.protobuf.MarketDataTypeProto.MarketDataType marketDataType) {}
  @Override public void tickReqParamsProtoBuf(com.ib.client.protobuf.TickReqParamsProto.TickReqParams tickReqParams) {}
  @Override public void updateAccountValueProtoBuf(com.ib.client.protobuf.AccountValueProto.AccountValue accountValue) {}
  @Override public void updatePortfolioProtoBuf(com.ib.client.protobuf.PortfolioValueProto.PortfolioValue portfolioValue) {}
  @Override public void updateAccountTimeProtoBuf(com.ib.client.protobuf.AccountUpdateTimeProto.AccountUpdateTime accountUpdateTime) {}
  @Override public void accountDataEndProtoBuf(com.ib.client.protobuf.AccountDataEndProto.AccountDataEnd accountDataEnd) {}
  @Override public void managedAccountsProtoBuf(com.ib.client.protobuf.ManagedAccountsProto.ManagedAccounts managedAccounts) {}
  @Override public void positionProtoBuf(com.ib.client.protobuf.PositionProto.Position position) {}
  @Override public void positionEndProtoBuf(com.ib.client.protobuf.PositionEndProto.PositionEnd positionEnd) {}
  @Override public void accountSummaryProtoBuf(com.ib.client.protobuf.AccountSummaryProto.AccountSummary accountSummary) {}
  @Override public void accountSummaryEndProtoBuf(com.ib.client.protobuf.AccountSummaryEndProto.AccountSummaryEnd accountSummaryEnd) {}
  @Override public void positionMultiProtoBuf(com.ib.client.protobuf.PositionMultiProto.PositionMulti positionMulti) {}
  @Override public void positionMultiEndProtoBuf(com.ib.client.protobuf.PositionMultiEndProto.PositionMultiEnd positionMultiEnd) {}
  @Override public void accountUpdateMultiProtoBuf(com.ib.client.protobuf.AccountUpdateMultiProto.AccountUpdateMulti accountUpdateMulti) {}
  @Override public void accountUpdateMultiEndProtoBuf(com.ib.client.protobuf.AccountUpdateMultiEndProto.AccountUpdateMultiEnd accountUpdateMultiEnd) {}
  @Override public void historicalDataProtoBuf(com.ib.client.protobuf.HistoricalDataProto.HistoricalData historicalData) {}
  @Override public void historicalDataUpdateProtoBuf(com.ib.client.protobuf.HistoricalDataUpdateProto.HistoricalDataUpdate historicalDataUpdate) {}
  @Override public void historicalDataEndProtoBuf(com.ib.client.protobuf.HistoricalDataEndProto.HistoricalDataEnd historicalDataEnd) {}
  @Override public void realTimeBarTickProtoBuf(com.ib.client.protobuf.RealTimeBarTickProto.RealTimeBarTick realTimeBarTick) {}
  @Override public void headTimestampProtoBuf(com.ib.client.protobuf.HeadTimestampProto.HeadTimestamp headTimestamp) {}
  @Override public void histogramDataProtoBuf(com.ib.client.protobuf.HistogramDataProto.HistogramData histogramData) {}
  @Override public void historicalTicksProtoBuf(com.ib.client.protobuf.HistoricalTicksProto.HistoricalTicks historicalTicks) {}
  @Override public void historicalTicksBidAskProtoBuf(com.ib.client.protobuf.HistoricalTicksBidAskProto.HistoricalTicksBidAsk historicalTicksBidAsk) {}
  @Override public void historicalTicksLastProtoBuf(com.ib.client.protobuf.HistoricalTicksLastProto.HistoricalTicksLast historicalTicksLast) {}
  @Override public void tickByTickDataProtoBuf(com.ib.client.protobuf.TickByTickDataProto.TickByTickData tickByTickData) {}
  @Override public void updateNewsBulletinProtoBuf(com.ib.client.protobuf.NewsBulletinProto.NewsBulletin newsBulletin) {}
  @Override public void newsArticleProtoBuf(com.ib.client.protobuf.NewsArticleProto.NewsArticle newsArticle) {}
  @Override public void newsProvidersProtoBuf(com.ib.client.protobuf.NewsProvidersProto.NewsProviders newsProviders) {}
  @Override public void historicalNewsProtoBuf(com.ib.client.protobuf.HistoricalNewsProto.HistoricalNews historicalNews) {}
  @Override public void historicalNewsEndProtoBuf(com.ib.client.protobuf.HistoricalNewsEndProto.HistoricalNewsEnd historicalNewsEnd) {}
  @Override public void wshMetaDataProtoBuf(com.ib.client.protobuf.WshMetaDataProto.WshMetaData wshMetaData) {}
  @Override public void wshEventDataProtoBuf(com.ib.client.protobuf.WshEventDataProto.WshEventData wshEventData) {}
  @Override public void tickNewsProtoBuf(com.ib.client.protobuf.TickNewsProto.TickNews tickNews) {}
  @Override public void scannerParametersProtoBuf(com.ib.client.protobuf.ScannerParametersProto.ScannerParameters scannerParameters) {}
  @Override public void scannerDataProtoBuf(com.ib.client.protobuf.ScannerDataProto.ScannerData scannerData) {}
  @Override public void fundamentalsDataProtoBuf(com.ib.client.protobuf.FundamentalsDataProto.FundamentalsData fundamentalsData) {}
  @Override public void pnlProtoBuf(com.ib.client.protobuf.PnLProto.PnL pnl) {}
  @Override public void pnlSingleProtoBuf(com.ib.client.protobuf.PnLSingleProto.PnLSingle pnlSingle) {}
  @Override public void receiveFAProtoBuf(com.ib.client.protobuf.ReceiveFAProto.ReceiveFA receiveFA) {}
  @Override public void replaceFAEndProtoBuf(com.ib.client.protobuf.ReplaceFAEndProto.ReplaceFAEnd replaceFAEnd) {}
  @Override public void commissionAndFeesReportProtoBuf(com.ib.client.protobuf.CommissionAndFeesReportProto.CommissionAndFeesReport commissionAndFeesReport) {}
  @Override public void historicalScheduleProtoBuf(com.ib.client.protobuf.HistoricalScheduleProto.HistoricalSchedule historicalSchedule) {}
  @Override public void rerouteMarketDataRequestProtoBuf(com.ib.client.protobuf.RerouteMarketDataRequestProto.RerouteMarketDataRequest rerouteMarketDataRequest) {}
  @Override public void rerouteMarketDepthRequestProtoBuf(com.ib.client.protobuf.RerouteMarketDepthRequestProto.RerouteMarketDepthRequest rerouteMarketDepthRequest) {}
  @Override public void secDefOptParameterProtoBuf(com.ib.client.protobuf.SecDefOptParameterProto.SecDefOptParameter secDefOptParameter) {}
  @Override public void secDefOptParameterEndProtoBuf(com.ib.client.protobuf.SecDefOptParameterEndProto.SecDefOptParameterEnd secDefOptParameterEnd) {}
  @Override public void softDollarTiersProtoBuf(com.ib.client.protobuf.SoftDollarTiersProto.SoftDollarTiers softDollarTiers) {}
  @Override public void familyCodesProtoBuf(com.ib.client.protobuf.FamilyCodesProto.FamilyCodes familyCodes) {}
  @Override public void symbolSamplesProtoBuf(com.ib.client.protobuf.SymbolSamplesProto.SymbolSamples symbolSamples) {}
  @Override public void smartComponentsProtoBuf(com.ib.client.protobuf.SmartComponentsProto.SmartComponents smartComponents) {}
  @Override public void marketRuleProtoBuf(com.ib.client.protobuf.MarketRuleProto.MarketRule marketRule) {}
  @Override public void userInfoProtoBuf(com.ib.client.protobuf.UserInfoProto.UserInfo userInfo) {}
  @Override public void nextValidIdProtoBuf(com.ib.client.protobuf.NextValidIdProto.NextValidId nextValidId) {}
  @Override public void currentTimeProtoBuf(com.ib.client.protobuf.CurrentTimeProto.CurrentTime currentTime) {}
  @Override public void currentTimeInMillisProtoBuf(com.ib.client.protobuf.CurrentTimeInMillisProto.CurrentTimeInMillis currentTimeInMillis) {}
  @Override public void verifyMessageApiProtoBuf(com.ib.client.protobuf.VerifyMessageApiProto.VerifyMessageApi verifyMessageApi) {}
  @Override public void verifyCompletedProtoBuf(com.ib.client.protobuf.VerifyCompletedProto.VerifyCompleted verifyCompleted) {}
  @Override public void displayGroupListProtoBuf(com.ib.client.protobuf.DisplayGroupListProto.DisplayGroupList displayGroupList) {}
  @Override public void displayGroupUpdatedProtoBuf(com.ib.client.protobuf.DisplayGroupUpdatedProto.DisplayGroupUpdated displayGroupUpdated) {}
  @Override public void marketDepthExchangesProtoBuf(MarketDepthExchangesProto.MarketDepthExchanges marketDepthExchanges) {}
}
