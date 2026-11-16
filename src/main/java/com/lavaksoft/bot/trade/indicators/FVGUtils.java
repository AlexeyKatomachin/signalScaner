package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * FVG utilities.
 * <p>
 * Two algorithms are intentionally kept:
 * 1) findFVG(...) is the original signalScaner implementation.
 * 2) findReferenceFVG(...) is the exact three-candle definition used by the supplied Python project.
 * <p>
 * Keeping both lets visual tests compare definitions without silently changing the existing strategy.
 */
public class FVGUtils {

    public static boolean checkBelongFVG(FVG fvg, Candle candle) {
        return (fvg.getHigh() < candle.getHigh() && fvg.getHigh() > candle.getLow()) ||
                (fvg.getLow() < candle.getHigh() && fvg.getLow() > candle.getLow());
    }

    public List<FVG> findFVG(List<Candle> candles) {
        List<FVG> fvgs = new ArrayList<>();
        TreeMap<Double, FVG> fvgLong = new TreeMap<>();
        TreeMap<Double, FVG> fvgShort = new TreeMap<>();
        findFvg(candles, fvgs, fvgLong, fvgShort, 1);
        fvgShort.putAll(fvgLong);
        return new ArrayList<>(fvgShort.values());
    }

    /**
     * Python project find_fvgs(): c3.low > c1.high => bullish; c3.high < c1.low => bearish.
     */
    public List<FVG> findReferenceFVG(List<Candle> candles) {
        return findReferenceFVG(candles, 0);
    }

    /**
     * Same as Python find_fvgs(df.iloc[fromIndex:]).
     * The first possible returned FVG therefore ends at fromIndex + 2.
     */
    public List<FVG> findReferenceFVG(List<Candle> candles, int fromIndex) {
        List<FVG> result = new ArrayList<>();
        int start = Math.max(0, fromIndex);
        for (int i = start + 2; i < candles.size(); i++) {
            Candle c1 = candles.get(i - 2);
            Candle c3 = candles.get(i);
            if (c3.getLow() > c1.getHigh()) {
                result.add(new FVG(c3, c3.getLow(), c1.getHigh(), ActionType.LONG));
            } else if (c3.getHigh() < c1.getLow()) {
                result.add(new FVG(c3, c1.getLow(), c3.getHigh(), ActionType.SHORT));
            }
        }
        return result;
    }

    private void findFvg(List<Candle> candles, List<FVG> fvgs, TreeMap<Double, FVG> fvgLong,
                         TreeMap<Double, FVG> fvgShort, int indexFrom) {
        for (int i = indexFrom; i < candles.size() - 1; i++) {
            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);
            Candle next = candles.get(i + 1);
            checkTestOrFulfilled(fvgLong, fvgShort, next);
            addFVG(fvgs, fvgLong, fvgShort, current, previous, next);
        }
    }

    private static void addFVG(List<FVG> fvgs, TreeMap<Double, FVG> fvgLong, TreeMap<Double, FVG> fvgShort,
                               Candle current, Candle previous, Candle next) {
        if (current.getOpen() - current.getClose() > 0) {
            if (previous.getLow() - next.getHigh() > 0) {
                FVG fvg = new FVG(current, previous.getLow(), next.getHigh(), ActionType.SHORT);
                fvgs.add(fvg);
                fvgShort.put(fvg.getLow(), fvg);
            }
        } else {
            if (next.getLow() - previous.getHigh() > 0) {
                FVG fvg = new FVG(current, next.getLow(), previous.getHigh(), ActionType.LONG);
                fvgs.add(fvg);
                fvgLong.put(fvg.getHigh(), fvg);
            }
        }
    }

    private static void checkTestOrFulfilled(TreeMap<Double, FVG> fvgLong, TreeMap<Double, FVG> fvgShort, Candle next) {
        if (fvgLong.ceilingEntry(next.getLow()) != null) {
            FVG fvg = fvgLong.ceilingEntry(next.getLow()).getValue();
            if (fvg.getLow() >= next.getLow() && next.getHigh() > fvg.getLow()) {
                fvg.setFulfilled(true);
                fvg.setCandleFulfilled(next);
                fvgLong.remove(fvg.getHigh(), fvg);
            }
            if (next.getLow() < fvg.getHigh() && !fvg.isTested()) fvg.setTested(true);
        }

        if (fvgShort.floorEntry(next.getHigh()) != null) {
            FVG fvg = fvgShort.floorEntry(next.getHigh()).getValue();
            if (fvg.getHigh() > next.getLow() && fvg.getHigh() <= next.getHigh()) {
                fvg.setFulfilled(true);
                fvg.setCandleFulfilled(next);
                fvgShort.remove(fvg.getHigh(), fvg);
            }
            if (next.getHigh() > fvg.getLow() && !fvg.isTested()) fvg.setTested(true);
        }
    }

    public void updateFVG(List<Candle> candles) {
        List<FVG> fvgs = new ArrayList<>();
        TreeMap<Double, FVG> fvgLong = new TreeMap<>();
        TreeMap<Double, FVG> fvgShort = new TreeMap<>();
        candles.stream().filter(x -> x.getFvg() != null).max(Comparator.comparing(Candle::getDateTime)).ifPresentOrElse(x ->
                findFvg(candles, fvgs, fvgLong, fvgShort, candles.indexOf(x)), () -> findFVG(candles));
    }
}
