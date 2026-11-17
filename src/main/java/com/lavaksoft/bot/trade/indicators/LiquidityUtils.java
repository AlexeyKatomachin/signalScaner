package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.Liquidity;
import com.lavaksoft.bot.trade.model.LiquidityType;
import com.lavaksoft.bot.trade.model.POI;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.lavaksoft.bot.trade.model.LiquidityType.BSL;
import static com.lavaksoft.bot.trade.model.LiquidityType.INNER_BSL;
import static com.lavaksoft.bot.trade.model.LiquidityType.INNER_SSL;
import static com.lavaksoft.bot.trade.model.LiquidityType.PDH;
import static com.lavaksoft.bot.trade.model.LiquidityType.PDL;
import static com.lavaksoft.bot.trade.model.LiquidityType.PWH;
import static com.lavaksoft.bot.trade.model.LiquidityType.PWL;
import static com.lavaksoft.bot.trade.model.LiquidityType.SSL;

public class LiquidityUtils {
    public List<Liquidity> findLiquidity(List<Candle> candles, List<ContextFlow> contextFlows) {
        TreeMap<Double, Liquidity> liquidities = new TreeMap<>();
        ContextFlow actualContext = contextFlows.get(contextFlows.size() - 1);
        TreeMap<Double, Liquidity> highLiquidity = new TreeMap<>();
        TreeMap<Double, Liquidity> lowLiquidity = new TreeMap<>();

        initLiquidity(candles, highLiquidity, lowLiquidity);
        findEQH(candles, highLiquidity);
        findEQL(candles, highLiquidity, lowLiquidity);
        setInnerLiquidity(actualContext, highLiquidity, lowLiquidity);

        liquidities.putAll(highLiquidity);
        liquidities.putAll(lowLiquidity);
        return new ArrayList<>(liquidities.values());
    }

    private static void setInnerLiquidity(ContextFlow actualContext, TreeMap<Double, Liquidity> highLiquidity, TreeMap<Double, Liquidity> lowLiquidity) {
        if (actualContext != null && actualContext.getKeyFractals() != null && actualContext.getKeyFractals().size() >= 2) {
            KeyFractal keyFractalLast = actualContext.getKeyFractals().get(actualContext.getKeyFractals().size() - 1);
            KeyFractal keyFractalPreviousLast = actualContext.getKeyFractals().get(actualContext.getKeyFractals().size() - 2);
            if (keyFractalLast.getFractal().isHigh()) {
                if (keyFractalPreviousLast.getFractal().isLow()) {
                    NavigableMap<Double, Liquidity> actualHigh = highLiquidity.subMap(keyFractalPreviousLast.getFractal().getCandle().getLow(),
                            true, keyFractalLast.getFractal().getCandle().getHigh(), false);
                    if (!actualHigh.isEmpty()) {
                        actualHigh.values().forEach(liquidity -> liquidity.setType(INNER_BSL));
                    }
                    NavigableMap<Double, Liquidity> actualLow = lowLiquidity.subMap(keyFractalPreviousLast.getFractal().getCandle().getLow(),
                            false, keyFractalLast.getFractal().getCandle().getHigh(), true);
                    if (!actualLow.isEmpty()) {
                        actualLow.values().forEach(liquidity -> liquidity.setType(INNER_SSL));
                    }
                }
            } else {
                if (keyFractalPreviousLast.getFractal().isHigh()) {
                    NavigableMap<Double, Liquidity> actualHigh = highLiquidity.subMap(keyFractalLast.getFractal().getCandle().getLow(),
                            true, keyFractalPreviousLast.getFractal().getCandle().getHigh(), false);
                    if (!actualHigh.isEmpty()) {
                        actualHigh.values().forEach(liquidity -> liquidity.setType(INNER_BSL));
                    }
                    NavigableMap<Double, Liquidity> actualLow = lowLiquidity.subMap(keyFractalLast.getFractal().getCandle().getLow(),
                            false, keyFractalPreviousLast.getFractal().getCandle().getHigh(), true);
                    if (!actualLow.isEmpty()) {
                        actualLow.values().forEach(liquidity -> liquidity.setType(INNER_SSL));
                    }
                }
            }
        }
    }

    /**
     * Equal highs/lows are price-equality concepts. The old implementation made the allowed
     * price difference grow with candle distance, which produced visibly unrelated EQH/EQL.
     * We now use an ATR-relative tolerance (10% of ATR) and compare pivots chronologically.
     */
    private static void findEQL(List<Candle> candles, TreeMap<Double, Liquidity> highLiquidity, TreeMap<Double, Liquidity> lowLiquidity) {
        markEqualLevels(candles, new ArrayList<>(lowLiquidity.values()), false);
    }

    private static void findEQH(List<Candle> candles, TreeMap<Double, Liquidity> highLiquidity) {
        markEqualLevels(candles, new ArrayList<>(highLiquidity.values()), true);
    }

    public boolean areEqualHighs(List<Candle> candles, int firstIndex, int secondIndex) {
        return areEqualLevels(candles, firstIndex, secondIndex, true);
    }

    public boolean areEqualLows(List<Candle> candles, int firstIndex, int secondIndex) {
        return areEqualLevels(candles, firstIndex, secondIndex, false);
    }

    private static boolean areEqualLevels(List<Candle> candles, int firstIndex, int secondIndex, boolean highs) {
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= candles.size() || secondIndex >= candles.size() || firstIndex == secondIndex) return false;
        int later = Math.max(firstIndex, secondIndex);
        double first = highs ? candles.get(firstIndex).getHigh() : candles.get(firstIndex).getLow();
        double second = highs ? candles.get(secondIndex).getHigh() : candles.get(secondIndex).getLow();
        double tolerance = Math.max(atr(candles, later, 200) * 0.10, Math.abs(second) * 0.00005);
        return Math.abs(first - second) <= tolerance;
    }

    private static void markEqualLevels(List<Candle> candles, List<Liquidity> candidates, boolean highs) {
        candidates.sort((a, b) -> Integer.compare(
                candles.indexOf(a.getFractal().getCandle()),
                candles.indexOf(b.getFractal().getCandle())));

        Liquidity previous = null;
        for (Liquidity current : candidates) {
            if (previous == null) {
                previous = current;
                continue;
            }
            int previousIndex = candles.indexOf(previous.getFractal().getCandle());
            int currentIndex = candles.indexOf(current.getFractal().getCandle());
            if (previousIndex < 0 || currentIndex < 0 || currentIndex <= previousIndex) {
                previous = current;
                continue;
            }

            double previousPrice = highs
                    ? previous.getFractal().getCandle().getHigh()
                    : previous.getFractal().getCandle().getLow();
            double currentPrice = highs
                    ? current.getFractal().getCandle().getHigh()
                    : current.getFractal().getCandle().getLow();
            if (areEqualLevels(candles, previousIndex, currentIndex, highs)) {
                previous.setType(highs ? LiquidityType.EQH : LiquidityType.EQL);
                current.setType(highs ? LiquidityType.EQH : LiquidityType.EQL);
            }
            previous = current;
        }
    }

    private static double atr(List<Candle> candles, int endIndex, int period) {
        int from = Math.max(0, endIndex - period + 1);
        double sum = 0.0;
        int count = 0;
        for (int i = from; i <= endIndex; i++) {
            Candle candle = candles.get(i);
            double tr = candle.getHigh() - candle.getLow();
            if (i > 0) {
                double previousClose = candles.get(i - 1).getClose();
                tr = Math.max(tr, Math.abs(candle.getHigh() - previousClose));
                tr = Math.max(tr, Math.abs(candle.getLow() - previousClose));
            }
            sum += tr;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static void initLiquidity(List<Candle> candles, TreeMap<Double, Liquidity> highLiquidity, TreeMap<Double, Liquidity> lowLiquidity) {
        List<Fractal> fractals = candles.stream()
                .map(Candle::getFractal)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Fractal currentFractal = null;
        for (Fractal fractal : fractals) {
            currentFractal = fractal;
            if (currentFractal.isHigh()) {
                SortedMap<Double, Liquidity> highToDelete = highLiquidity.subMap(0D, currentFractal.getCandle().getHigh());
                if (!highToDelete.isEmpty()) {
                    highToDelete.values().forEach(v -> v.setFulfilled(true));
                    highToDelete.clear();
                }
                highLiquidity.put(currentFractal.getCandle().getHigh(), new Liquidity(currentFractal, LiquidityType.BSL));
            }
            if (currentFractal.isLow()) {
                if ((!lowLiquidity.isEmpty()) && (lowLiquidity.lastKey() < currentFractal.getCandle().getLow())) {
                    SortedMap<Double, Liquidity> lowToDelete = lowLiquidity.subMap(lowLiquidity.lastKey(), currentFractal.getCandle().getLow());
                    if (!lowToDelete.isEmpty()) {
                        lowToDelete.values().forEach(v -> v.setFulfilled(true));
                        lowToDelete.clear();
                    }
                }
                lowLiquidity.put(currentFractal.getCandle().getLow(), new Liquidity(currentFractal, LiquidityType.SSL));
            }
        }
    }

    public void updateDaylyLiquidity(Candle previousDayCandle, List<Candle> mtfCandles) {
        Candle currentCandle;
        double deltaHigh;
        double deltaLow;
        mtfCandles.stream().filter(x -> x.getFractal() != null && x.getFractal().getLiquidity() != null
                        && (x.getFractal().getLiquidity().getType().equals(PDH) || x.getFractal().getLiquidity().getType().equals(PDL)))
                .forEach(x -> {
                    Liquidity liquidity = x.getFractal().getLiquidity();
                    if (liquidity.getType().equals(PDH)) {
                        liquidity.setType(BSL);
                    } else {
                        liquidity.setType(SSL);
                    }
                });
        for (int i = mtfCandles.size() - 1; i >= 0; i--) {
            currentCandle = mtfCandles.get(i);
            deltaHigh = currentCandle.getHigh() - previousDayCandle.getHigh();
            deltaHigh = ((deltaHigh / previousDayCandle.getHigh()) * 100) + 1;
            if (deltaHigh < 2 && deltaHigh > 0 && currentCandle.getFractal() != null && currentCandle.getFractal().isHigh()) {
                new POI(ActionType.SHORT, null, new Liquidity(currentCandle.getFractal(), PDH));
            }
            deltaLow = currentCandle.getLow() - previousDayCandle.getLow();
            deltaLow = ((deltaLow / previousDayCandle.getLow()) * 100) + 1;
            if (deltaLow < 2 && deltaLow > 0 && currentCandle.getFractal() != null && currentCandle.getFractal().isLow()) {
                new POI(ActionType.LONG, null, new Liquidity(currentCandle.getFractal(), PDL));
            }
        }
    }

    public void updateWeeklyLiquidity(Candle previousWeekCandle, List<Candle> mtfCandles) {
        Candle currentCandle;
        double deltaHigh;
        double deltaLow;
        mtfCandles.stream().filter(x -> x.getFractal() != null && x.getFractal().getLiquidity() != null
                        && (x.getFractal().getLiquidity().getType().equals(PWH) || x.getFractal().getLiquidity().getType().equals(PWL)))
                .forEach(x -> {
                    Liquidity liquidity = x.getFractal().getLiquidity();
                    if (liquidity.getType().equals(PWH)) {
                        liquidity.setType(BSL);
                    } else {
                        liquidity.setType(SSL);
                    }
                });
        for (int i = mtfCandles.size() - 1; i >= 0; i--) {
            currentCandle = mtfCandles.get(i);
            deltaHigh = currentCandle.getHigh() - previousWeekCandle.getHigh();
            deltaHigh = ((deltaHigh / previousWeekCandle.getHigh()) * 100) + 1;
            if (deltaHigh < 2 && deltaHigh > 0 && currentCandle.getFractal() != null && currentCandle.getFractal().isHigh()) {
                new POI(ActionType.SHORT, null, new Liquidity(currentCandle.getFractal(), PWH));
            }
            deltaLow = currentCandle.getLow() - previousWeekCandle.getLow();
            deltaLow = ((deltaLow / previousWeekCandle.getLow()) * 100) + 1;
            if (deltaLow < 2 && deltaLow > 0 && currentCandle.getFractal() != null && currentCandle.getFractal().isLow()) {
                new POI(ActionType.LONG, null, new Liquidity(currentCandle.getFractal(), PWL));
            }
        }
    }
}
