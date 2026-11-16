package com.lavaksoft.bot.trade.data;

import com.lavaksoft.bot.trade.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Time-bucket aggregation for MT5 M5/M15 feed into H1/H4/D1/W1.
 */
public final class CandleAggregationUtils {
    private CandleAggregationUtils() {
    }

    public static List<Candle> aggregate(List<Candle> source, long bucketMillis) {
        List<Candle> result = new ArrayList<>();
        if (source == null || source.isEmpty()) return result;

        long currentBucket = Long.MIN_VALUE;
        double open = 0, high = 0, low = 0, close = 0, volume = 0;
        boolean initialized = false;

        for (Candle candle : source) {
            long bucket = Math.floorDiv(candle.getTime(), bucketMillis) * bucketMillis;
            if (!initialized || bucket != currentBucket) {
                if (initialized) result.add(new Candle(currentBucket, open, high, low, close, volume));
                currentBucket = bucket;
                open = candle.getOpen();
                high = candle.getHigh();
                low = candle.getLow();
                close = candle.getClose();
                volume = candle.getVolume();
                initialized = true;
            } else {
                high = Math.max(high, candle.getHigh());
                low = Math.min(low, candle.getLow());
                close = candle.getClose();
                volume += candle.getVolume();
            }
        }
        if (initialized) result.add(new Candle(currentBucket, open, high, low, close, volume));
        return result;
    }
}
