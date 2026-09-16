package com.lavaksoft.bot.trade.model;

public class Liquidity {
    private Fractal fractal;
    private LiquidityType type;
    private POI poi;
    private boolean fulfilled = false;

    public Liquidity(Fractal fractal, LiquidityType type) {
        this.fractal = fractal;
        this.type = type;
        fractal.setLiquidity(this);
    }

    public Fractal getFractal() {
        return fractal;
    }

    public void setFractal(Fractal fractal) {
        this.fractal = fractal;
    }

    public LiquidityType getType() {
        return type;
    }

    public void setType(LiquidityType type) {
        this.type = type;
    }

    public POI getPoi() {
        return poi;
    }

    public void setPoi(POI poi) {
        this.poi = poi;
    }

    public boolean isFulfilled() {
        return fulfilled;
    }

    public void setFulfilled(boolean fulfilled) {
        this.fulfilled = fulfilled;
    }
}
