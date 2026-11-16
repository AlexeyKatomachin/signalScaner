package com.lavaksoft.bot.trade.model;

/**
 * Last opposite candle before an impulsive BOS, plus the impulse leg used for OTE.
 */
public class OrderBlock {
    private final ActionType type;
    private final int orderBlockIndex;
    private final int bosIndex;
    private final int levelIndex;
    private final double top;
    private final double bottom;
    private final double legLow;
    private final double legHigh;

    public OrderBlock(ActionType type, int orderBlockIndex, int bosIndex, int levelIndex,
                      double top, double bottom, double legLow, double legHigh) {
        this.type = type;
        this.orderBlockIndex = orderBlockIndex;
        this.bosIndex = bosIndex;
        this.levelIndex = levelIndex;
        this.top = top;
        this.bottom = bottom;
        this.legLow = legLow;
        this.legHigh = legHigh;
    }

    public ActionType getType() {
        return type;
    }

    public int getOrderBlockIndex() {
        return orderBlockIndex;
    }

    public int getBosIndex() {
        return bosIndex;
    }

    public int getLevelIndex() {
        return levelIndex;
    }

    public double getTop() {
        return top;
    }

    public double getBottom() {
        return bottom;
    }

    public double getLegLow() {
        return legLow;
    }

    public double getLegHigh() {
        return legHigh;
    }
}
