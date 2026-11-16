package com.lavaksoft.bot.trade.model;

public class OteZone {
    private final double bottom;
    private final double top;

    public OteZone(double bottom, double top) {
        this.bottom = Math.min(bottom, top);
        this.top = Math.max(bottom, top);
    }

    public double getBottom() { return bottom; }
    public double getTop() { return top; }
    public double getMid() { return (bottom + top) / 2.0; }
}
