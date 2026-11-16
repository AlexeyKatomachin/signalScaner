package com.lavaksoft.bot.trade.data;

import com.lavaksoft.bot.trade.model.Candle;

import java.util.List;

public interface MarketDataProvider {
    List<Candle> getKlines(String symbol, String interval, int limit);

    // Новый метод: свечи за период от startTime до endTime (в миллисекундах)
    List<Candle> getKlines(String symbol, String interval, long startTime, long endTime);

    List<Candle> getKlinesClosed(String symbol, String interval, long startTime, long endTime);
}
