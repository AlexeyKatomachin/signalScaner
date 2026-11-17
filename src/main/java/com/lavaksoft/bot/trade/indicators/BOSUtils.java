package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.Choch;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.StructureBreak;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.ArrayList;
import java.util.List;

public class BOSUtils {
    private final MarketStructureUtils marketStructureUtils = new MarketStructureUtils();

    /**
     * Compatibility method used by Modern3LevelStrategy. A structure break is confirmed by CLOSE,
     * not by a wick/body heuristic. The broken structural pivot is marked once.
     */
    public boolean hasBOS(ContextFlow contextFlow, Candle currentCandle) {
        if (contextFlow == null || contextFlow.getKeyFractals() == null || contextFlow.getKeyFractals().isEmpty()) return false;
        KeyFractal keyFractal = contextFlow.getKeyFractals().get(contextFlow.getKeyFractals().size() - 1);
        switch (keyFractal.getType()) {
            case HL:
                if (currentCandle.getClose() < keyFractal.getFractal().getCandle().getLow()) {
                    keyFractal.setHasBos(true);
                    contextFlow.setHasBOS(true);
                    return true;
                }
                break;
            case LH:
                if (currentCandle.getClose() > keyFractal.getFractal().getCandle().getHigh()) {
                    keyFractal.setHasBos(true);
                    contextFlow.setHasBOS(true);
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    /** Actual historical BOS/CHoCH events with reference and break candle indexes. */
    public List<StructureBreak> findStructureBreaks(List<Candle> candles) {
        return marketStructureUtils.findStructureBreaks(candles, 2, 2);
    }

    public List<StructureBreak> findStructureBreaks(List<Candle> candles, int left, int right) {
        return marketStructureUtils.findStructureBreaks(candles, left, right);
    }

    /**
     * Reference-project CHoCH: break of the last confirmed pivot that existed before the sweep.
     */
    public Choch detectChoch(List<Candle> candles, int sweepIndex, MarketBias bias, MarketStructureUtils structureUtils) {
        if (sweepIndex < 0 || sweepIndex >= candles.size()) return null;
        List<Candle> beforeAndIncludingSweep = new ArrayList<>(candles.subList(0, sweepIndex + 1));
        SwingPoints points = structureUtils.findSwingPoints(beforeAndIncludingSweep, 2, 2);

        if (bias == MarketBias.BULLISH) {
            if (points.getHighs().isEmpty()) return null;
            int referenceIndex = points.getHighs().get(points.getHighs().size() - 1);
            double level = candles.get(referenceIndex).getHigh();
            for (int j = sweepIndex + 1; j < candles.size(); j++) {
                if (candles.get(j).getClose() > level) return new Choch(j, level, referenceIndex);
            }
        } else if (bias == MarketBias.BEARISH) {
            if (points.getLows().isEmpty()) return null;
            int referenceIndex = points.getLows().get(points.getLows().size() - 1);
            double level = candles.get(referenceIndex).getLow();
            for (int j = sweepIndex + 1; j < candles.size(); j++) {
                if (candles.get(j).getClose() < level) return new Choch(j, level, referenceIndex);
            }
        }
        return null;
    }
}
