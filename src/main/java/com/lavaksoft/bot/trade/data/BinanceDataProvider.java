package com.lavaksoft.bot.trade.data;

import com.binance.connector.client.impl.SpotClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavaksoft.bot.trade.model.Candle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class BinanceDataProvider implements MarketDataProvider {
    private static final Logger LOG = Logger.getLogger(BinanceDataProvider.class.getName());
    private final SpotClientImpl client;
    private final ObjectMapper mapper = new ObjectMapper();

    public BinanceDataProvider(String apiKey, String secretKey) {
        this.client = new SpotClientImpl(apiKey, secretKey);
    }

    @Override
    public List<Candle> getKlines(String symbol, String interval, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("interval", interval);
        params.put("limit", limit);
        String json = client.createMarket().klines(params);
        return getCandles(symbol, interval, json);
    }

    @Override
    public List<Candle> getKlines(String symbol, String interval, long startTime, long endTime) {
        Map<String, Object> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("interval", interval);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        params.put("limit", 1000); // максимум за раз, можно сделать несколько итераций, если надо больше
        String json = client.createMarket().klines(params);
        // парсинг – такой же, как в getKlines(symbol, interval, limit)
        return getCandles(symbol, interval, json);
    }

    @Override
    public List<Candle> getKlinesClosed(String symbol, String interval, long startTime, long endTime) {
        List<Candle> klines = getKlines(symbol, interval, startTime, endTime);
        if (klines.size() < 2) return new ArrayList<>();
        return klines.subList(0, klines.size() - 2);
    }

    @NotNull
    private List<Candle> getCandles(String symbol, String interval, String json) {
        try {
            JsonNode arr = mapper.readTree(json);
            List<Candle> candles = new ArrayList<>();
            for (JsonNode item : arr) {
                long time = item.get(0).asLong();
                double open = Double.parseDouble(item.get(1).asText());
                double high = Double.parseDouble(item.get(2).asText());
                double low = Double.parseDouble(item.get(3).asText());
                double close = Double.parseDouble(item.get(4).asText());
                double volume = Double.parseDouble(item.get(5).asText());
                candles.add(new Candle(time, open, high, low, close, volume));
            }
            return candles;
        } catch (Exception e) {
            LOG.severe("Failed to fetch klines for " + symbol + "/" + interval + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
