package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference swing/bias implementation ported from the Python S&P500 project.
 * It intentionally lives next to the existing FractalUtils instead of replacing it,
 * so both definitions can be tested and compared on the same market data.
 */
public class MarketStructureUtils {

    public SwingPoints findSwingPoints(List<Candle> candles) {
        return findSwingPoints(candles, 2, 2);
    }

    public SwingPoints findSwingPoints(List<Candle> candles, int left, int right) {
        List<Integer> highs = new ArrayList<>();
        List<Integer> lows = new ArrayList<>();
        for (int i = left; i < candles.size() - right; i++) {
            if (isFirstMaximumInWindow(candles, i, left, right)) {
                highs.add(i);
            }
            if (isFirstMinimumInWindow(candles, i, left, right)) {
                lows.add(i);
            }
        }
        return new SwingPoints(highs, lows);
    }

    public MarketBias marketBias(List<Candle> candles) {
        return marketBias(candles, 2, 2);
    }

    public MarketBias marketBias(List<Candle> candles, int left, int right) {
        SwingPoints points = findSwingPoints(candles, left, right);
        if (points.getHighs().size() < 2 || points.getLows().size() < 2) {
            return MarketBias.NEUTRAL;
        }

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

    private boolean isFirstMaximumInWindow(List<Candle> candles, int index, int left, int right) {
        double value = candles.get(index).getHigh();
        int from = index - left;
        int to = index + right;
        for (int i = from; i <= to; i++) {
            double other = candles.get(i).getHigh();
            if (other > value) return false;
            // numpy.argmax(window) == left: an equal maximum to the LEFT disqualifies the center.
            if (i < index && Double.compare(other, value) == 0) return false;
        }
        return true;
    }

    private boolean isFirstMinimumInWindow(List<Candle> candles, int index, int left, int right) {
        double value = candles.get(index).getLow();
        int from = index - left;
        int to = index + right;
        for (int i = from; i <= to; i++) {
            double other = candles.get(i).getLow();
            if (other < value) return false;
            if (i < index && Double.compare(other, value) == 0) return false;
        }
        return true;
    }
}
