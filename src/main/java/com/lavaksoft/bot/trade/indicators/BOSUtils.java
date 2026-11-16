package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.Choch;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.SwingPoints;

import java.util.ArrayList;
import java.util.List;

public class BOSUtils {
    public boolean hasBOS(ContextFlow contextFlow, Candle currentCandle) {
        KeyFractal keyFractal = contextFlow.getKeyFractals().get(contextFlow.getKeyFractals().size() - 1);
        return checkBos(contextFlow, currentCandle, keyFractal);
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

    private static boolean checkBos(ContextFlow contextFlow, Candle currentCandle, KeyFractal keyFractal) {
        switch (keyFractal.getType()) {
            case HL:
                double layerLow = keyFractal.getFractal().getCandle().getLow();
                if (currentCandle.getLow() < layerLow && (layerLow - currentCandle.getClose()) + (layerLow - currentCandle.getOpen()) > 0) {
                    keyFractal.setHasBos(true);
                    contextFlow.setHasBOS(true);
                    return true;
                }
                break;
            case LH:
                double layerHigh = keyFractal.getFractal().getCandle().getHigh();
                if (currentCandle.getHigh() > layerHigh && (currentCandle.getClose() - layerHigh) + (currentCandle.getOpen() - layerHigh) > 0) {
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
}
