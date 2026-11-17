package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.BreakerBlock;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;

import java.util.ArrayList;
import java.util.List;

/** Breaker block = an opposite-side order block that was later invalidated by a close. */
public class BreakerUtils {
    private final OTEUtils oteUtils;

    public BreakerUtils() {
        this(new OTEUtils());
    }

    public BreakerUtils(OTEUtils oteUtils) {
        this.oteUtils = oteUtils;
    }

    public BreakerBlock detectBreakerBlock(List<Candle> candles, MarketBias currentBias) {
        List<BreakerBlock> blocks = detectBreakerBlocks(candles, currentBias, 220, 1);
        return blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
    }

    public BreakerBlock detectBreakerBlock(List<Candle> candles, MarketBias currentBias, int lookback) {
        List<BreakerBlock> blocks = detectBreakerBlocks(candles, currentBias, lookback, 1);
        return blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
    }

    public List<BreakerBlock> detectBreakerBlocks(List<Candle> candles, MarketBias currentBias, int lookback, int maxResults) {
        List<BreakerBlock> result = new ArrayList<>();
        MarketBias opposite = currentBias == MarketBias.BULLISH ? MarketBias.BEARISH
                : currentBias == MarketBias.BEARISH ? MarketBias.BULLISH : MarketBias.NEUTRAL;
        if (opposite == MarketBias.NEUTRAL) return result;

        List<OrderBlock> oppositeBlocks = oteUtils.detectOrderBlocks(candles, opposite, lookback, 2, 2, 30);
        for (OrderBlock ob : oppositeBlocks) {
            for (int j = ob.getBosIndex() + 1; j < candles.size(); j++) {
                if (currentBias == MarketBias.BULLISH && candles.get(j).getClose() > ob.getTop()) {
                    result.add(new BreakerBlock(ob.getBottom(), ob.getTop(), j, ob.getOrderBlockIndex()));
                    break;
                }
                if (currentBias == MarketBias.BEARISH && candles.get(j).getClose() < ob.getBottom()) {
                    result.add(new BreakerBlock(ob.getBottom(), ob.getTop(), j, ob.getOrderBlockIndex()));
                    break;
                }
            }
        }
        if (result.size() <= maxResults) return result;
        return new ArrayList<>(result.subList(result.size() - maxResults, result.size()));
    }
}
