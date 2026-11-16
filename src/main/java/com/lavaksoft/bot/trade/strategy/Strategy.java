package com.lavaksoft.bot.trade.strategy;

import com.lavaksoft.bot.trade.model.Candle;

import java.util.List;

public interface Strategy {
    void init(List<Candle> htf1WCandles, List<Candle> htf1DCandles, List<Candle> mtf4hCandles, List<Candle> mtf1hCandles,
              List<Candle> ltf15hCandles, List<Candle> ltf5hCandles);

    void iteration();
}
