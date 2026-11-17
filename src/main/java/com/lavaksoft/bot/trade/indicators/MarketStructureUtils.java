package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.StructureBreak;
import com.lavaksoft.bot.trade.model.StructureBreakType;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic swing/market-structure calculations.
 *
 * All pivots are confirmed only after the configured number of right-side bars exists.
 * This is important for replay/backtest code because it prevents look-ahead use of a pivot.
 */
public class MarketStructureUtils {

    public SwingPoints findSwingPoints(List<Candle> candles) {
        return findSwingPoints(candles, 2, 2);
    }

    public SwingPoints findSwingPoints(List<Candle> candles, int left, int right) {
        List<Integer> highs = new ArrayList<>();
        List<Integer> lows = new ArrayList<>();
        if (candles == null || candles.size() < left + right + 1) {
            return new SwingPoints(highs, lows);
        }
        for (int i = left; i < candles.size() - right; i++) {
            if (isFirstMaximumInWindow(candles, i, left, right)) highs.add(i);
            if (isFirstMinimumInWindow(candles, i, left, right)) lows.add(i);
        }
        return new SwingPoints(highs, lows);
    }

    public MarketBias marketBias(List<Candle> candles) {
        return marketBias(candles, 2, 2);
    }

    public MarketBias marketBias(List<Candle> candles, int left, int right) {
        SwingPoints points = findSwingPoints(candles, left, right);
        if (points.getHighs().size() < 2 || points.getLows().size() < 2) return MarketBias.NEUTRAL;

        int previousHighIndex = points.getHighs().get(points.getHighs().size() - 2);
        int lastHighIndex = points.getHighs().get(points.getHighs().size() - 1);
        int previousLowIndex = points.getLows().get(points.getLows().size() - 2);
        int lastLowIndex = points.getLows().get(points.getLows().size() - 1);

        double h1 = candles.get(previousHighIndex).getHigh();
        double h2 = candles.get(lastHighIndex).getHigh();
        double l1 = candles.get(previousLowIndex).getLow();
        double l2 = candles.get(lastLowIndex).getLow();

        if (h2 > h1 && l2 > l1) return MarketBias.BULLISH;
        if (h2 < h1 && l2 < l1) return MarketBias.BEARISH;
        return MarketBias.NEUTRAL;
    }

    /**
     * Finds actual structure breaks, not flags attached to old pivots.
     * A pivot becomes available only at pivotIndex + right. The first close through the
     * active pivot is emitted once. A break against the current structural trend is CHOCH;
     * a break continuing it is BOS.
     */
    public List<StructureBreak> findStructureBreaks(List<Candle> candles) {
        return findStructureBreaks(candles, 2, 2);
    }

    public List<StructureBreak> findStructureBreaks(List<Candle> candles, int left, int right) {
        List<StructureBreak> result = new ArrayList<>();
        if (candles == null || candles.size() < left + right + 1) return result;

        SwingPoints points = findSwingPoints(candles, left, right);
        Set<Integer> highIndexes = new HashSet<>(points.getHighs());
        Set<Integer> lowIndexes = new HashSet<>(points.getLows());

        Integer activeHigh = null;
        Integer activeLow = null;
        boolean highCrossed = false;
        boolean lowCrossed = false;
        MarketBias trend = MarketBias.NEUTRAL;

        for (int bar = 0; bar < candles.size(); bar++) {
            int newlyConfirmedPivot = bar - right;
            if (newlyConfirmedPivot >= 0) {
                if (highIndexes.contains(newlyConfirmedPivot)) {
                    activeHigh = newlyConfirmedPivot;
                    highCrossed = false;
                }
                if (lowIndexes.contains(newlyConfirmedPivot)) {
                    activeLow = newlyConfirmedPivot;
                    lowCrossed = false;
                }
            }

            double close = candles.get(bar).getClose();
            if (activeHigh != null && !highCrossed && bar > activeHigh) {
                double level = candles.get(activeHigh).getHigh();
                if (close > level) {
                    StructureBreakType type = trend == MarketBias.BEARISH ? StructureBreakType.CHOCH : StructureBreakType.BOS;
                    result.add(new StructureBreak(type, ActionType.LONG, activeHigh, bar, level));
                    highCrossed = true;
                    trend = MarketBias.BULLISH;
                }
            }

            if (activeLow != null && !lowCrossed && bar > activeLow) {
                double level = candles.get(activeLow).getLow();
                if (close < level) {
                    StructureBreakType type = trend == MarketBias.BULLISH ? StructureBreakType.CHOCH : StructureBreakType.BOS;
                    result.add(new StructureBreak(type, ActionType.SHORT, activeLow, bar, level));
                    lowCrossed = true;
                    trend = MarketBias.BEARISH;
                }
            }
        }
        return result;
    }

    private boolean isFirstMaximumInWindow(List<Candle> candles, int index, int left, int right) {
        double value = candles.get(index).getHigh();
        for (int i = index - left; i <= index + right; i++) {
            double other = candles.get(i).getHigh();
            if (other > value) return false;
            if (i < index && Double.compare(other, value) == 0) return false;
        }
        return true;
    }

    private boolean isFirstMinimumInWindow(List<Candle> candles, int index, int left, int right) {
        double value = candles.get(index).getLow();
        for (int i = index - left; i <= index + right; i++) {
            double other = candles.get(i).getLow();
            if (other < value) return false;
            if (i < index && Double.compare(other, value) == 0) return false;
        }
        return true;
    }
}
