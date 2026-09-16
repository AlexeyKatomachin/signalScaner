package com.lavaksoft.bot.trade.model;

import java.util.List;

public class ContextFlow {
    private List<KeyFractal> keyFractals;
    private List<Candle> candles;
    private ActionType type;
    private boolean hasBOS = false;

    public ContextFlow(List<KeyFractal> keyFractals, List<Candle> candles, ActionType type) {
        this.keyFractals = keyFractals;
        this.candles = candles;
        this.type = type;
    }

    public List<KeyFractal> getKeyFractals() {
        return keyFractals;
    }

    public void setKeyFractals(List<KeyFractal> keyFractals) {
        this.keyFractals = keyFractals;
    }

    public List<Candle> getCandles() {
        return candles;
    }

    public void setCandles(List<Candle> candles) {
        this.candles = candles;
    }

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public boolean isHasBOS() {
        return hasBOS;
    }

    public void setHasBOS(boolean hasBOS) {
        this.hasBOS = hasBOS;
    }
}
