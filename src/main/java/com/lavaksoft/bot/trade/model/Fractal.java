package com.lavaksoft.bot.trade.model;

public class Fractal {
    private Candle candle;
    private boolean isHigh;
    private boolean isLow;

    private KeyFractal keyFractal;
    private Liquidity liquidity;

    public Fractal(Candle candle, boolean isHigh, boolean isLow) {
        this.candle = candle;
        this.isHigh = isHigh;
        this.isLow = isLow;
        candle.setFractal(this);
    }

    public Candle getCandle() {
        return candle;
    }

    public void setCandle(Candle candle) {
        this.candle = candle;
    }

    public boolean isHigh() {
        return isHigh;
    }

    public void setHigh(boolean high) {
        isHigh = high;
    }

    public boolean isLow() {
        return isLow;
    }

    public void setLow(boolean low) {
        isLow = low;
    }

    public KeyFractal getKeyFractal() {
        return keyFractal;
    }

    public void setKeyFractal(KeyFractal keyFractal) {
        this.keyFractal = keyFractal;
    }

    public Liquidity getLiquidity() {
        return liquidity;
    }

    public void setLiquidity(Liquidity liquidity) {
        this.liquidity = liquidity;
    }
}
