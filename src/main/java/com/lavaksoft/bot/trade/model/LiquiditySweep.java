package com.lavaksoft.bot.trade.model;

public class LiquiditySweep {
    private final int sweptIndex;
    private final int sweepIndex;
    private final double level;
    private final double extreme;

    public LiquiditySweep(int sweptIndex, int sweepIndex, double level, double extreme) {
        this.sweptIndex = sweptIndex;
        this.sweepIndex = sweepIndex;
        this.level = level;
        this.extreme = extreme;
    }

    public int getSweptIndex() { return sweptIndex; }
    public int getSweepIndex() { return sweepIndex; }
    public double getLevel() { return level; }
    public double getExtreme() { return extreme; }
}
