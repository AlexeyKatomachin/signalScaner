package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.BprZone;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.LiquiditySweep;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** BPR and liquidity-sweep calculations. */
public class BPRUtils {

    public List<BprZone> findBprZones(List<Candle> candles, List<FVG> fvgs, int maxGapBars) {
        List<BprZone> zones = new ArrayList<>();
        for (int i = 0; i < fvgs.size(); i++) {
            FVG a = fvgs.get(i);
            int aIndex = candles.indexOf(a.getCandle());
            if (aIndex < 0) continue;
            for (int j = i + 1; j < fvgs.size(); j++) {
                FVG b = fvgs.get(j);
                int bIndex = candles.indexOf(b.getCandle());
                if (bIndex < 0 || bIndex <= aIndex) continue;
                if (bIndex - aIndex > maxGapBars) break;
                if (a.getType() == b.getType()) continue;
                double low = Math.max(a.getLow(), b.getLow());
                double high = Math.min(a.getHigh(), b.getHigh());
                if (low < high) zones.add(new BprZone(low, high, bIndex, b.getCandle().getTime(), a, b));
            }
        }
        zones.sort(Comparator.comparingInt(BprZone::getFormedIndex));
        return zones;
    }

    /** Most recent zones for human-readable visual validation. */
    public List<BprZone> findRecentBprZones(List<Candle> candles, List<FVG> fvgs, int maxGapBars, int maxResults) {
        List<BprZone> all = findBprZones(candles, fvgs, maxGapBars);
        if (all.size() <= maxResults) return all;
        return new ArrayList<>(all.subList(all.size() - maxResults, all.size()));
    }

    public LiquiditySweep detectLiquiditySweep(List<Candle> candles, SwingPoints points, MarketBias bias, int lookback) {
        int n = candles.size();
        int recentStart = Math.max(0, n - lookback);

        if (bias == MarketBias.BULLISH) {
            Integer levelIndex = lastAtOrAfter(points.getLows(), recentStart);
            if (levelIndex == null) return null;
            return findLowSweep(candles, levelIndex);
        } else if (bias == MarketBias.BEARISH) {
            Integer levelIndex = lastAtOrAfter(points.getHighs(), recentStart);
            if (levelIndex == null) return null;
            return findHighSweep(candles, levelIndex);
        }
        return null;
    }

    /** Finds historical wick sweeps of confirmed main-structure swings. */
    public List<LiquiditySweep> detectLiquiditySweeps(List<Candle> candles, SwingPoints points, int maxResults) {
        List<LiquiditySweep> sweeps = new ArrayList<>();
        for (Integer index : points.getLows()) {
            LiquiditySweep sweep = findLowSweep(candles, index);
            if (sweep != null) sweeps.add(sweep);
        }
        for (Integer index : points.getHighs()) {
            LiquiditySweep sweep = findHighSweep(candles, index);
            if (sweep != null) sweeps.add(sweep);
        }
        sweeps.sort(Comparator.comparingInt(LiquiditySweep::getSweepIndex));
        if (sweeps.size() <= maxResults) return sweeps;
        return new ArrayList<>(sweeps.subList(sweeps.size() - maxResults, sweeps.size()));
    }

    public ActionType direction(MarketBias bias) {
        return bias == MarketBias.BULLISH ? ActionType.LONG : bias == MarketBias.BEARISH ? ActionType.SHORT : null;
    }

    private static LiquiditySweep findLowSweep(List<Candle> candles, int levelIndex) {
        double level = candles.get(levelIndex).getLow();
        for (int j = levelIndex + 3; j < candles.size(); j++) {
            Candle candle = candles.get(j);
            if (candle.getLow() < level && candle.getClose() > level) {
                return new LiquiditySweep(levelIndex, j, level, candle.getLow());
            }
        }
        return null;
    }

    private static LiquiditySweep findHighSweep(List<Candle> candles, int levelIndex) {
        double level = candles.get(levelIndex).getHigh();
        for (int j = levelIndex + 3; j < candles.size(); j++) {
            Candle candle = candles.get(j);
            if (candle.getHigh() > level && candle.getClose() < level) {
                return new LiquiditySweep(levelIndex, j, level, candle.getHigh());
            }
        }
        return null;
    }

    private static Integer lastAtOrAfter(List<Integer> indexes, int threshold) {
        for (int i = indexes.size() - 1; i >= 0; i--) if (indexes.get(i) >= threshold) return indexes.get(i);
        return null;
    }
}
