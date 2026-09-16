package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Утилиты для работы с FVG (Fair Value Gap / Imbalance).
 * FVG – это разрыв между свечами, который не перекрывается телами соседних свечей.
 * Классически определяется по теням (High/Low) трёх свечей.
 */
public class FVGUtils {

    public static boolean checkBelongFVG(FVG fvg, Candle candle) {
        return (fvg.getHigh() < candle.getHigh() && fvg.getHigh() > candle.getLow()) ||
                (fvg.getLow() < candle.getHigh() && fvg.getLow() > candle.getLow());
    }

    public List<FVG> findFVG(List<Candle> candles) {
        List<FVG> FVGs = new ArrayList<>();
        TreeMap<Double, FVG> fvgLong = new TreeMap<>();
        TreeMap<Double, FVG> fvgShort = new TreeMap<>();


        findFvg(candles, FVGs, fvgLong, fvgShort, 1);

        fvgShort.putAll(fvgLong);
        return new ArrayList<>(fvgShort.values());
    }

    private void findFvg(List<Candle> candles, List<FVG> FVGs, TreeMap<Double, FVG> fvgLong, TreeMap<Double, FVG> fvgShort, int indexFrom) {
        Candle current = null;
        Candle previous = null;
        Candle next = null;
        for (int i = indexFrom; i < candles.size() - 1; i++) {
            current = candles.get(i);
            previous = candles.get(i - 1);
            next = candles.get(i + 1);

            checkTestOrFulfilled(fvgLong, fvgShort, next);
            addFVG(FVGs, fvgLong, fvgShort, current, previous, next);
        }
    }

    private static void addFVG(List<FVG> FVGs, TreeMap<Double, FVG> fvgLong, TreeMap<Double, FVG> fvgShort, Candle current, Candle previous, Candle next) {
        if (current.getOpen() - current.getClose() > 0) {
            if (previous.getLow() - next.getHigh() > 0) {
                FVG fvg = new FVG(current, previous.getLow(), next.getHigh(), ActionType.SHORT);
                FVGs.add(fvg);
                fvgShort.put(fvg.getLow(), fvg);
            }
        } else {
            if (next.getLow() - previous.getHigh() > 0) {
                FVG fvg = new FVG(current, next.getLow(), previous.getHigh(), ActionType.LONG);
                FVGs.add(fvg);
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
            if (next.getLow() < fvg.getHigh() && !fvg.isTested()) {
                fvg.setTested(true);
            }
        }

        if (fvgShort.floorEntry(next.getHigh()) != null) {
            FVG fvg = fvgShort.floorEntry(next.getHigh()).getValue();
            if (fvg.getHigh() > next.getLow() && fvg.getHigh() <= next.getHigh()) {
                fvg.setFulfilled(true);
                fvg.setCandleFulfilled(next);
                fvgShort.remove(fvg.getHigh(), fvg);
            }
            if (next.getHigh() > fvg.getLow() && !fvg.isTested()) {
                fvg.setTested(true);
            }
        }
    }


    public void updateFVG(List<Candle> candles) {
        List<FVG> FVGs = new ArrayList<>();
        TreeMap<Double, FVG> fvgLong = new TreeMap<>();
        TreeMap<Double, FVG> fvgShort = new TreeMap<>();

        candles.stream().filter(x -> x.getFvg() != null).max(Comparator.comparing(Candle::getDateTime)).ifPresentOrElse(x -> {
            findFvg(candles, FVGs, fvgLong, fvgShort, candles.indexOf(x));
        }, () -> findFVG(candles));
    }
}