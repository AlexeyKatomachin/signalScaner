package com.lavaksoft.bot.trade.model;

public class FVG {
    private Candle candle;
    private double high;
    private double low;
    private ActionType type;
    private boolean fulfilled = false;
    private boolean tested = false;
    private Candle candleFulfilled;
    private POI poi;

    public FVG(Candle candle, double high, double low, ActionType type) {
        this.candle = candle;
        this.high = high;
        this.low = low;
        this.type = type;
        candle.setFvg(this);
    }

    public Candle getCandle() {
        return candle;
    }

    public void setCandle(Candle candle) {
        this.candle = candle;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public boolean isFulfilled() {
        return fulfilled;
    }

    public void setFulfilled(boolean fulfilled) {
        this.fulfilled = fulfilled;
    }

    public Candle getCandleFulfilled() {
        return candleFulfilled;
    }

    public void setCandleFulfilled(Candle candleFulfilled) {
        this.candleFulfilled = candleFulfilled;
    }

    public POI getPoi() {
        return poi;
    }

    public void setPoi(POI poi) {
        this.poi = poi;
    }

    public boolean isTested() {
        return tested;
    }

    public void setTested(boolean tested) {
        this.tested = tested;
    }
}
