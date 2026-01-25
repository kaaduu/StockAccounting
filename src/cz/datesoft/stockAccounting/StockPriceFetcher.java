/*
 * StockPriceFetcher.java
 *
 * Fetches stock prices from Yahoo Finance API.
 */
package cz.datesoft.stockAccounting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.json.JSONObject;

/**
 * Utility class to fetch stock prices from Yahoo Finance.
 */
public class StockPriceFetcher {

    private static final String YAHOO_API_URL_TEMPLATE = "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d";
    private static final int TIMEOUT_MS = 10000;

    /**
     * Fetch current prices for a list of tickers.
     * 
     * @param tickers List of ticker symbols (e.g. "AAPL", "MSFT", "CEZ.PR")
     * @return Map of Ticker -> Price (in ticker's currency)
     */
    public Map<String, Double> fetchPrices(List<String> tickers) {
        Map<String, Double> results = new HashMap<>();

        for (String ticker : tickers) {
            if (ticker == null || ticker.trim().isEmpty())
                continue;

            try {
                Double price = fetchPrice(ticker);
                if (price != null) {
                    results.put(ticker, price);
                }
            } catch (Exception e) {
                System.err.println("Error fetching price for " + ticker + ": " + e.getMessage());
            }
        }

        return results;
    }

    private Double fetchPrice(String ticker) throws Exception {
        String urlStr = String.format(YAHOO_API_URL_TEMPLATE, ticker.trim());
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            // Yahoo finance requires a User-Agent often
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("HTTP " + responseCode);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(response.toString());
            if (json.has("chart")) {
                JSONObject chart = json.getJSONObject("chart");
                if (chart.has("result") && !chart.isNull("result")) {
                    JSONObject result = chart.getJSONArray("result").getJSONObject(0);
                    if (result.has("meta")) {
                        JSONObject meta = result.getJSONObject("meta");
                        if (meta.has("regularMarketPrice")) {
                            return meta.getDouble("regularMarketPrice");
                        }
                    }
                }
            }

            return null;
        } finally {
            conn.disconnect();
        }
    }
}
