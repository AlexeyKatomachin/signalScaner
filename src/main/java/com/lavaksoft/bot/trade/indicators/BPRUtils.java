package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.BprZone;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.LiquiditySweep;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.ArrayList;
import java.util.List;

/**
 * BPR and liquidity-sweep logic ported from strategy_bpr.py.
 */
public class BPRUtils {

    public List<BprZone> findBprZones(List<Candle> candles, List<FVG> fvgs, int maxGapBars) {
        List<BprZone> zones = new ArrayList<>();
        for (int i = 0; i < fvgs.size(); i++) {
            FVG a = fvgs.get(i);
            int aIndex = candles.indexOf(a.getCandle());
            for (int j = i + 1; j < fvgs.size(); j++) {
                FVG b = fvgs.get(j);
                int bIndex = candles.indexOf(b.getCandle());
                if (bIndex - aIndex > maxGapBars) continue;
                if (a.getType() == b.getType()) continue;
                double low = Math.max(a.getLow(), b.getLow());
                double high = Math.min(a.getHigh(), b.getHigh());
                if (low < high) {
                    zones.add(new BprZone(low, high, bIndex, b.getCandle().getTime(), a, b));
                }
            }
        }
        return zones;
    }

    public LiquiditySweep detectLiquiditySweep(List<Candle> candles, SwingPoints points, MarketBias bias, int lookback) {
        int n = candles.size();
        int recentStart = Math.max(0, n - lookback);

        if (bias == MarketBias.BULLISH) {
            Integer levelIndex = lastAtOrAfter(points.getLows(), recentStart);
            if (levelIndex == null) return null;
            double level = candles.get(levelIndex).getLow();
            for (int j = levelIndex + 1; j < n; j++) {
                Candle candle = candles.get(j);
                if (candle.getLow() < level && candle.getClose() > level) {
                    return new LiquiditySweep(levelIndex, j, level, candle.getLow());
                }
            }
        } else if (bias == MarketBias.BEARISH) {
            Integer levelIndex = lastAtOrAfter(points.getHighs(), recentStart);
            if (levelIndex == null) return null;
            double level = candles.get(levelIndex).getHigh();
            for (int j = levelIndex + 1; j < n; j++) {
                Candle candle = candles.get(j);
                if (candle.getHigh() > level && candle.getClose() < level) {
                    return new LiquiditySweep(levelIndex, j, level, candle.getHigh());
                }
            }
        }
        return null;
    }

    public ActionType direction(MarketBias bias) {
        return bias == MarketBias.BULLISH ? ActionType.LONG : bias == MarketBias.BEARISH ? ActionType.SHORT : null;
    }

    private static Integer lastAtOrAfter(List<Integer> indexes, int threshold) {
        for (int i = indexes.size() - 1; i >= 0; i--) {
            if (indexes.get(i) >= threshold) return indexes.get(i);
        }
        return null;
    }
}
