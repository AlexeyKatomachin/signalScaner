package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.KeyFractalType;
import com.lavaksoft.bot.trade.model.StructureBreak;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fractal and structural-pivot utilities.
 *
 * Basic fractal = 1 bar on the left + 1 bar on the right.
 * Key fractal = stronger confirmed swing (2 left + 2 right), classified by comparing
 * it with the previous swing of the same side: HH/LH for highs and HL/LL for lows.
 */
public class FractalUtils {
    private final MarketStructureUtils structureUtils = new MarketStructureUtils();

    public List<Fractal> findFractals(List<Candle> candles) {
        for (Candle candle : candles) candle.setFractal(null);
        List<Fractal> fractals = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            double curHigh = candles.get(i).getHigh();
            double curLow = candles.get(i).getLow();
            boolean isHigh = candles.get(i - 1).getHigh() < curHigh && candles.get(i + 1).getHigh() < curHigh;
            boolean isLow = candles.get(i - 1).getLow() > curLow && candles.get(i + 1).getLow() > curLow;
            if (isHigh || isLow) fractals.add(new Fractal(candles.get(i), isHigh, isLow));
        }
        return fractals;
    }

    /** Recompute is deliberately deterministic; correctness is more important than micro-optimising the prototype. */
    public void updateFractals(List<Candle> candles) {
        findFractals(candles);
    }

    /**
     * Builds a clean structural sequence. Consecutive pivots on the same side are collapsed
     * to the more extreme one, then every point is labelled HH/HL/LH/LL.
     */
    public List<KeyFractal> findKeyFractals(List<Candle> candles) {
        if (candles.stream().noneMatch(c -> c.getFractal() != null)) findFractals(candles);
        candles.stream().map(Candle::getFractal).filter(f -> f != null).forEach(f -> f.setKeyFractal(null));

        SwingPoints points = structureUtils.findSwingPoints(candles, 2, 2);
        List<PivotRef> raw = new ArrayList<>();
        for (Integer index : points.getHighs()) raw.add(new PivotRef(index, true));
        for (Integer index : points.getLows()) raw.add(new PivotRef(index, false));
        raw.sort(Comparator.comparingInt(p -> p.index));

        List<PivotRef> pivots = collapseSameSide(candles, raw);
        List<KeyFractal> result = new ArrayList<>();
        Double previousHigh = null;
        Double previousLow = null;
        Map<Integer, KeyFractal> keyByIndex = new HashMap<>();

        for (PivotRef pivot : pivots) {
            Candle candle = candles.get(pivot.index);
            Fractal fractal = candle.getFractal();
            if (fractal == null) {
                fractal = new Fractal(candle, pivot.high, !pivot.high);
            } else if (pivot.high) {
                fractal.setHigh(true);
            } else {
                fractal.setLow(true);
            }

            KeyFractalType type;
            if (pivot.high) {
                type = previousHigh == null || candle.getHigh() > previousHigh ? KeyFractalType.HH : KeyFractalType.LH;
                previousHigh = candle.getHigh();
            } else {
                type = previousLow == null || candle.getLow() < previousLow ? KeyFractalType.LL : KeyFractalType.HL;
                previousLow = candle.getLow();
            }
            KeyFractal key = new KeyFractal(fractal, type);
            result.add(key);
            keyByIndex.put(pivot.index, key);
        }

        // Keep the old hasBos flag meaningful for existing strategy code: it now means that
        // this exact structural pivot was actually closed through later in history.
        for (StructureBreak structureBreak : structureUtils.findStructureBreaks(candles, 2, 2)) {
            KeyFractal reference = keyByIndex.get(structureBreak.getReferenceIndex());
            if (reference != null) reference.setHasBos(true);
        }
        return result;
    }

    public void updateKeyFractals(List<Candle> candles) {
        findKeyFractals(candles);
    }

    private static List<PivotRef> collapseSameSide(List<Candle> candles, List<PivotRef> raw) {
        List<PivotRef> result = new ArrayList<>();
        for (PivotRef current : raw) {
            if (result.isEmpty()) {
                result.add(current);
                continue;
            }
            PivotRef last = result.get(result.size() - 1);
            if (last.index == current.index) {
                // An outside bar may mathematically be both. Keep both only if it is followed by
                // an opposite pivot later; choosing the larger excursion is more stable here.
                double highExcursion = candles.get(current.index).getHigh() - candles.get(current.index).getClose();
                double lowExcursion = candles.get(current.index).getClose() - candles.get(current.index).getLow();
                result.set(result.size() - 1, new PivotRef(current.index, highExcursion >= lowExcursion));
                continue;
            }
            if (last.high == current.high) {
                boolean currentMoreExtreme = current.high
                        ? candles.get(current.index).getHigh() > candles.get(last.index).getHigh()
                        : candles.get(current.index).getLow() < candles.get(last.index).getLow();
                if (currentMoreExtreme) result.set(result.size() - 1, current);
            } else {
                result.add(current);
            }
        }
        return result;
    }

    @Deprecated
    public static List<Fractal> findFractalHighs(List<Candle> candles) {
        List<Fractal> result = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            if (candles.get(i).getHigh() > candles.get(i - 1).getHigh() && candles.get(i).getHigh() > candles.get(i + 1).getHigh()) {
                result.add(new Fractal(candles.get(i), true, false));
            }
        }
        return result;
    }

    @Deprecated
    public static List<Fractal> findFractalLows(List<Candle> candles) {
        List<Fractal> result = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            if (candles.get(i).getLow() < candles.get(i - 1).getLow() && candles.get(i).getLow() < candles.get(i + 1).getLow()) {
                result.add(new Fractal(candles.get(i), false, true));
            }
        }
        return result;
    }

    @Deprecated
    public static boolean isLiquiditySwept(List<Candle> candles, int fractalIndex, boolean isHigh) {
        if (fractalIndex + 1 >= candles.size()) return false;
        Candle fractalCandle = candles.get(fractalIndex);
        Candle nextCandle = candles.get(fractalIndex + 1);
        return isHigh
                ? nextCandle.getHigh() > fractalCandle.getHigh() && nextCandle.getClose() < fractalCandle.getHigh()
                : nextCandle.getLow() < fractalCandle.getLow() && nextCandle.getClose() > fractalCandle.getLow();
    }

    public static boolean hasBOS(List<Candle> candles, boolean upward) {
        return getBOSLevel(candles, upward) != null;
    }

    @Deprecated
    public static Double getBOSLevel(List<Candle> candles, boolean upward) {
        if (candles.size() < 5) return null;
        MarketStructureUtils structure = new MarketStructureUtils();
        List<StructureBreak> breaks = structure.findStructureBreaks(candles, 2, 2);
        for (int i = breaks.size() - 1; i >= 0; i--) {
            StructureBreak b = breaks.get(i);
            if ((upward && b.getDirection() == com.lavaksoft.bot.trade.model.ActionType.LONG)
                    || (!upward && b.getDirection() == com.lavaksoft.bot.trade.model.ActionType.SHORT)) {
                return b.getLevel();
            }
        }
        return null;
    }

    /** Kept for binary/source compatibility with older tests; full recomputation is now preferred. */
    public int updateKeyFractalFromIndex(List<Fractal> fractals, List<KeyFractal> keyFractals,
                                         Fractal localExtremumLow, Fractal localExtremumHigh, int i) {
        return i;
    }

    private static final class PivotRef {
        private final int index;
        private final boolean high;

        private PivotRef(int index, boolean high) {
            this.index = index;
            this.high = high;
        }
    }
}
