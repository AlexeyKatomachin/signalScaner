package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.BreakerBlock;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;

import java.util.List;

/**
 * Breaker block definition ported from strategy_breaker.py.
 */
public class BreakerUtils {
    private final OTEUtils oteUtils;

    public BreakerUtils() {
        this(new OTEUtils());
    }

    public BreakerUtils(OTEUtils oteUtils) {
        this.oteUtils = oteUtils;
    }

    public BreakerBlock detectBreakerBlock(List<Candle> candles, MarketBias currentBias) {
        return detectBreakerBlock(candles, currentBias, 150);
    }

    public BreakerBlock detectBreakerBlock(List<Candle> candles, MarketBias currentBias, int lookback) {
        MarketBias opposite = currentBias == MarketBias.BULLISH ? MarketBias.BEARISH :
                currentBias == MarketBias.BEARISH ? MarketBias.BULLISH : MarketBias.NEUTRAL;
        if (opposite == MarketBias.NEUTRAL) return null;
        OrderBlock ob = oteUtils.detectOrderBlock(candles, opposite, lookback, 2, 2);
        if (ob == null) return null;

        for (int j = ob.getBosIndex() + 1; j < candles.size(); j++) {
            if (currentBias == MarketBias.BULLISH && candles.get(j).getClose() > ob.getTop()) {
                return new BreakerBlock(ob.getBottom(), ob.getTop(), j, ob.getOrderBlockIndex());
            }
            if (currentBias == MarketBias.BEARISH && candles.get(j).getClose() < ob.getBottom()) {
                return new BreakerBlock(ob.getBottom(), ob.getTop(), j, ob.getOrderBlockIndex());
            }
        }
        return null;
    }
}
