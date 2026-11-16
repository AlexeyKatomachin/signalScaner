package com.lavaksoft.bot.trade.model;

/**
 * Overlap of opposite-direction FVGs that are close enough in time.
 */
public class BprZone {
    private final double bottom;
    private final double top;
    private final int formedIndex;
    private final long time;
    private final FVG firstFvg;
    private final FVG secondFvg;

    public BprZone(double bottom, double top, int formedIndex, long time, FVG firstFvg, FVG secondFvg) {
        this.bottom = bottom;
        this.top = top;
        this.formedIndex = formedIndex;
        this.time = time;
        this.firstFvg = firstFvg;
        this.secondFvg = secondFvg;
    }

    public double getBottom() {
        return bottom;
    }

    public double getTop() {
        return top;
    }

    public int getFormedIndex() {
        return formedIndex;
    }

    public long getTime() {
        return time;
    }

    public FVG getFirstFvg() {
        return firstFvg;
    }

    public FVG getSecondFvg() {
        return secondFvg;
    }
}
