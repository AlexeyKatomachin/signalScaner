package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.KeyFractal;

public class BOSUtils {
    public boolean hasBOS (ContextFlow contextFlow, Candle currentCandle) {
        KeyFractal keyFractal = contextFlow.getKeyFractals().get(contextFlow.getKeyFractals().size() - 1);
        return checkBos(contextFlow, currentCandle, keyFractal);
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
