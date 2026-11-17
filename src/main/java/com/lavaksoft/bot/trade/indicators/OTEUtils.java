package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.FvgConfluence;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;
import com.lavaksoft.bot.trade.model.OteZone;
import com.lavaksoft.bot.trade.model.StructureBreak;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Order Block + FVG + OTE calculations. */
public class OTEUtils {
    public static final double OTE_FIB_LOW = 0.618;
    public static final double OTE_FIB_HIGH = 0.786;

    private final MarketStructureUtils marketStructureUtils;
    private final FVGUtils fvgUtils;

    public OTEUtils() {
        this(new MarketStructureUtils(), new FVGUtils());
    }

    public OTEUtils(MarketStructureUtils marketStructureUtils, FVGUtils fvgUtils) {
        this.marketStructureUtils = marketStructureUtils;
        this.fvgUtils = fvgUtils;
    }

    public OrderBlock detectOrderBlock(List<Candle> candles, MarketBias bias) {
        List<OrderBlock> blocks = detectOrderBlocks(candles, bias, 150, 2, 2, 1);
        return blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
    }

    public OrderBlock detectOrderBlock(List<Candle> candles, MarketBias bias, int lookback, int left, int right) {
        List<OrderBlock> blocks = detectOrderBlocks(candles, bias, lookback, left, right, 1);
        return blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
    }

    /**
     * Returns several historical OB impulses for visual validation instead of hiding everything
     * behind a single latest setup. The impulse leg ends at the break candle; no future candle is
     * allowed to change its OTE range.
     */
    public List<OrderBlock> detectOrderBlocks(List<Candle> candles, MarketBias bias, int lookback,
                                               int left, int right, int maxResults) {
        List<OrderBlock> result = new ArrayList<>();
        if (bias == MarketBias.NEUTRAL || candles == null || candles.isEmpty()) return result;

        ActionType requiredDirection = bias == MarketBias.BULLISH ? ActionType.LONG : ActionType.SHORT;
        int recentStart = Math.max(0, candles.size() - lookback);
        Set<Integer> usedObIndexes = new HashSet<>();

        for (StructureBreak structureBreak : marketStructureUtils.findStructureBreaks(candles, left, right)) {
            if (structureBreak.getDirection() != requiredDirection) continue;
            if (structureBreak.getReferenceIndex() < recentStart) continue;

            int obIndex = findLastOppositeCandle(candles, structureBreak.getReferenceIndex(),
                    structureBreak.getBreakIndex(), requiredDirection);
            if (obIndex < 0 || !usedObIndexes.add(obIndex)) continue;

            Extremum low = minLow(candles, obIndex, structureBreak.getBreakIndex());
            Extremum high = maxHigh(candles, obIndex, structureBreak.getBreakIndex());
            Candle ob = candles.get(obIndex);
            result.add(new OrderBlock(requiredDirection, obIndex, structureBreak.getBreakIndex(),
                    structureBreak.getReferenceIndex(), ob.getHigh(), ob.getLow(),
                    low.value, high.value, low.index, high.index));
        }

        if (result.size() <= maxResults) return result;
        return new ArrayList<>(result.subList(result.size() - maxResults, result.size()));
    }

    public OteZone computeOteZone(OrderBlock orderBlock) {
        double range = orderBlock.getLegHigh() - orderBlock.getLegLow();
        if (orderBlock.getType() == ActionType.LONG) {
            double top = orderBlock.getLegHigh() - OTE_FIB_LOW * range;
            double bottom = orderBlock.getLegHigh() - OTE_FIB_HIGH * range;
            return new OteZone(bottom, top);
        }
        double bottom = orderBlock.getLegLow() + OTE_FIB_LOW * range;
        double top = orderBlock.getLegLow() + OTE_FIB_HIGH * range;
        return new OteZone(bottom, top);
    }

    public FvgConfluence findConfluentFvg(List<Candle> candles, OrderBlock orderBlock) {
        return findConfluentFvg(candles, orderBlock, 20);
    }

    public FvgConfluence findConfluentFvg(List<Candle> candles, OrderBlock orderBlock, int maxGapBars) {
        List<FVG> fvgs = fvgUtils.findReferenceFVG(candles, orderBlock.getOrderBlockIndex());
        ActionType required = orderBlock.getType();
        double referenceSize = Math.max(1e-9, orderBlock.getTop() - orderBlock.getBottom());

        FvgConfluence best = null;
        for (FVG fvg : fvgs) {
            int index = candles.indexOf(fvg.getCandle());
            if (fvg.getType() != required) continue;
            if (index < orderBlock.getBosIndex()) continue;
            if (index - orderBlock.getBosIndex() > maxGapBars) continue;

            if (zonesOverlap(orderBlock.getBottom(), orderBlock.getTop(), fvg.getLow(), fvg.getHigh())) {
                return new FvgConfluence(fvg, true);
            }
            double distance = Math.min(Math.abs(fvg.getLow() - orderBlock.getTop()),
                    Math.abs(orderBlock.getBottom() - fvg.getHigh()));
            if (distance / referenceSize <= 0.5 && best == null) best = new FvgConfluence(fvg, false);
        }
        return best;
    }

    public static boolean zonesOverlap(double aBottom, double aTop, double bBottom, double bTop) {
        return Math.min(aTop, bTop) >= Math.max(aBottom, bBottom);
    }

    public static double clampToZone(double price, double bottom, double top) {
        return Math.min(Math.max(price, bottom), top);
    }

    private static int findLastOppositeCandle(List<Candle> candles, int referenceIndex, int breakIndex, ActionType direction) {
        for (int i = breakIndex - 1; i > referenceIndex; i--) {
            Candle candle = candles.get(i);
            boolean opposite = direction == ActionType.LONG
                    ? candle.getClose() < candle.getOpen()
                    : candle.getClose() > candle.getOpen();
            if (opposite) return i;
        }
        return -1;
    }

    private static Extremum minLow(List<Candle> candles, int from, int toInclusive) {
        double value = Double.POSITIVE_INFINITY;
        int index = from;
        for (int i = from; i <= toInclusive; i++) {
            if (candles.get(i).getLow() < value) {
                value = candles.get(i).getLow();
                index = i;
            }
        }
        return new Extremum(value, index);
    }

    private static Extremum maxHigh(List<Candle> candles, int from, int toInclusive) {
        double value = Double.NEGATIVE_INFINITY;
        int index = from;
        for (int i = from; i <= toInclusive; i++) {
            if (candles.get(i).getHigh() > value) {
                value = candles.get(i).getHigh();
                index = i;
            }
        }
        return new Extremum(value, index);
    }

    private static final class Extremum {
        private final double value;
        private final int index;

        private Extremum(double value, int index) {
            this.value = value;
            this.index = index;
        }
    }
}
