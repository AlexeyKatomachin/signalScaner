package com.lavaksoft.bot.trade.model;

public class BreakerBlock {
    private final double bottom;
    private final double top;
    private final int invalidationIndex;
    private final int originOrderBlockIndex;

    public BreakerBlock(double bottom, double top, int invalidationIndex, int originOrderBlockIndex) {
        this.bottom = bottom;
        this.top = top;
        this.invalidationIndex = invalidationIndex;
        this.originOrderBlockIndex = originOrderBlockIndex;
    }

    public double getBottom() {
        return bottom;
    }

    public double getTop() {
        return top;
    }

    public int getInvalidationIndex() {
        return invalidationIndex;
    }

    public int getOriginOrderBlockIndex() {
        return originOrderBlockIndex;
    }
}
