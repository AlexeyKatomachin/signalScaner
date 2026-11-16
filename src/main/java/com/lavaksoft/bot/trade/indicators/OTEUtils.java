package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.FvgConfluence;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;
import com.lavaksoft.bot.trade.model.OteZone;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.List;

/**
 * Order Block + FVG + OTE logic ported from strategy_ote.py.
 */
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
        return detectOrderBlock(candles, bias, 150, 2, 2);
    }

    public OrderBlock detectOrderBlock(List<Candle> candles, MarketBias bias, int lookback, int left, int right) {
        SwingPoints points = marketStructureUtils.findSwingPoints(candles, left, right);
        int n = candles.size();
        int recentStart = Math.max(0, n - lookback);

        if (bias == MarketBias.BULLISH) {
            for (int p = points.getHighs().size() - 1; p >= 0; p--) {
                int levelIndex = points.getHighs().get(p);
                if (levelIndex < recentStart) continue;
                double level = candles.get(levelIndex).getHigh();
                for (int bosIndex = levelIndex + 1; bosIndex < n; bosIndex++) {
                    if (candles.get(bosIndex).getClose() <= level) continue;
                    Integer obIndex = null;
                    for (int i = bosIndex; i > levelIndex; i--) {
                        if (candles.get(i).getClose() < candles.get(i).getOpen()) {
                            obIndex = i;
                            break;
                        }
                    }
                    if (obIndex == null) continue;
                    double legLow = minLow(candles, obIndex, bosIndex);
                    double legHigh = maxHigh(candles, obIndex, n - 1);
                    return new OrderBlock(ActionType.LONG, obIndex, bosIndex, levelIndex,
                            candles.get(obIndex).getHigh(), candles.get(obIndex).getLow(), legLow, legHigh);
                }
            }
            return null;
        }

        if (bias == MarketBias.BEARISH) {
            for (int p = points.getLows().size() - 1; p >= 0; p--) {
                int levelIndex = points.getLows().get(p);
                if (levelIndex < recentStart) continue;
                double level = candles.get(levelIndex).getLow();
                for (int bosIndex = levelIndex + 1; bosIndex < n; bosIndex++) {
                    if (candles.get(bosIndex).getClose() >= level) continue;
                    Integer obIndex = null;
                    for (int i = bosIndex; i > levelIndex; i--) {
                        if (candles.get(i).getClose() > candles.get(i).getOpen()) {
                            obIndex = i;
                            break;
                        }
                    }
                    if (obIndex == null) continue;
                    double legHigh = maxHigh(candles, obIndex, bosIndex);
                    double legLow = minLow(candles, obIndex, n - 1);
                    return new OrderBlock(ActionType.SHORT, obIndex, bosIndex, levelIndex,
                            candles.get(obIndex).getHigh(), candles.get(obIndex).getLow(), legLow, legHigh);
                }
            }
        }
        return null;
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
        double referenceSize = orderBlock.getTop() - orderBlock.getBottom();
        if (referenceSize == 0) referenceSize = 1.0;

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
            if (distance / referenceSize <= 0.5 && best == null) {
                best = new FvgConfluence(fvg, false);
            }
        }
        return best;
    }

    public static boolean zonesOverlap(double aBottom, double aTop, double bBottom, double bTop) {
        return Math.min(aTop, bTop) >= Math.max(aBottom, bBottom);
    }

    public static double clampToZone(double price, double bottom, double top) {
        return Math.min(Math.max(price, bottom), top);
    }

    private static double minLow(List<Candle> candles, int from, int toInclusive) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = from; i <= toInclusive; i++) min = Math.min(min, candles.get(i).getLow());
        return min;
    }

    private static double maxHigh(List<Candle> candles, int from, int toInclusive) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = from; i <= toInclusive; i++) max = Math.max(max, candles.get(i).getHigh());
        return max;
    }
}
