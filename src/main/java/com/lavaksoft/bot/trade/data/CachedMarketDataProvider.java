package com.lavaksoft.bot.trade.data;

import com.lavaksoft.bot.trade.model.Candle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CachedMarketDataProvider implements MarketDataProvider {
    private final MarketDataProvider delegate;
    private final ConcurrentHashMap<String, CachedData> cache = new ConcurrentHashMap<>();
    private static final long TTL_MILLIS = TimeUnit.MINUTES.toMillis(1);

    public CachedMarketDataProvider(MarketDataProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Candle> getKlines(String symbol, String interval, int limit) {
        String key = symbol + ":" + interval + ":" + limit;
        CachedData cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < TTL_MILLIS) {
            return cached.candles;
        }
        List<Candle> fresh = delegate.getKlines(symbol, interval, limit);
        cache.put(key, new CachedData(fresh, System.currentTimeMillis()));
        return fresh;
    }

    @Override
    public List<Candle> getKlines(String symbol, String interval, long startTime, long endTime) {
        String key = symbol + ":" + interval + ":" + startTime + ":" + endTime;
        CachedData cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < TTL_MILLIS) {
            return cached.candles;
        }
        List<Candle> fresh = delegate.getKlines(symbol, interval, startTime, endTime);
        cache.put(key, new CachedData(fresh, System.currentTimeMillis()));
        return fresh;
    }

    @Override
    public List<Candle> getKlinesClosed(String symbol, String interval, long startTime, long endTime) {
        List<Candle> klines = getKlines(symbol, interval, startTime, endTime);
        if (klines.size() < 2) return new ArrayList<>();
        return klines.subList(0, klines.size() - 2);
    }

    private static class CachedData {
        final List<Candle> candles;
        final long timestamp;
        CachedData(List<Candle> candles, long timestamp) {
            this.candles = candles;
            this.timestamp = timestamp;
        }
    }
}
