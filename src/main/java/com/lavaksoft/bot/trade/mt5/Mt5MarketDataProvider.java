package com.lavaksoft.bot.trade.mt5;

import com.lavaksoft.bot.trade.data.CandleAggregationUtils;
import com.lavaksoft.bot.trade.data.MarketDataProvider;
import com.lavaksoft.bot.trade.model.Candle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MarketDataProvider backed by the file feed emitted by the MT5 EA.
 */
public class Mt5MarketDataProvider implements MarketDataProvider {
    private final Mt5FeedReader reader;
    private final Map<String, String> feedKeyBySymbol;

    public Mt5MarketDataProvider(Mt5FeedReader reader, Map<String, String> feedKeyBySymbol) {
        this.reader = reader;
        this.feedKeyBySymbol = new HashMap<>(feedKeyBySymbol);
    }

    @Override
    public List<Candle> getKlines(String symbol, String interval, int limit) {
        List<Candle> all = load(symbol, interval, true);
        if (all.size() <= limit) return all;
        return new ArrayList<>(all.subList(all.size() - limit, all.size()));
    }

    @Override
    public List<Candle> getKlines(String symbol, String interval, long startTime, long endTime) {
        return load(symbol, interval, true).stream()
                .filter(c -> c.getTime() >= startTime && c.getTime() <= endTime)
                .collect(Collectors.toList());
    }

    @Override
    public List<Candle> getKlinesClosed(String symbol, String interval, long startTime, long endTime) {
        return load(symbol, interval, false).stream()
                .filter(c -> c.getTime() >= startTime && c.getTime() <= endTime)
                .collect(Collectors.toList());
    }

    private List<Candle> load(String symbol, String interval, boolean includeFormingCandle) {
        String key = feedKeyBySymbol.get(symbol);
        if (key == null) return Collections.emptyList();
        switch (interval.toLowerCase()) {
            case "5m":
                return reader.readFeed(key, "M5", includeFormingCandle);
            case "15m":
                return reader.readFeed(key, "M15", includeFormingCandle);
            case "1h":
                return CandleAggregationUtils.aggregate(reader.readFeed(key, "M15", includeFormingCandle), 60L * 60_000L);
            case "4h":
                return CandleAggregationUtils.aggregate(reader.readFeed(key, "M15", includeFormingCandle), 4L * 60L * 60_000L);
            case "1d":
                return CandleAggregationUtils.aggregate(reader.readFeed(key, "M15", includeFormingCandle), 24L * 60L * 60_000L);
            case "1w":
                return CandleAggregationUtils.aggregate(reader.readFeed(key, "M15", includeFormingCandle), 7L * 24L * 60L * 60_000L);
            default:
                throw new IllegalArgumentException("Unsupported MT5 interval: " + interval);
        }
    }
}
