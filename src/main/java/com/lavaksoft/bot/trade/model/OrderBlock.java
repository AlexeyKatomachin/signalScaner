package com.lavaksoft.bot.trade.model;

/**
 * Last opposite candle before a confirmed structural break, plus the impulse leg used for OTE.
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
    private final int legLowIndex;
    private final int legHighIndex;

    public OrderBlock(ActionType type, int orderBlockIndex, int bosIndex, int levelIndex,
                      double top, double bottom, double legLow, double legHigh) {
        this(type, orderBlockIndex, bosIndex, levelIndex, top, bottom, legLow, legHigh, -1, -1);
    }

    public OrderBlock(ActionType type, int orderBlockIndex, int bosIndex, int levelIndex,
                      double top, double bottom, double legLow, double legHigh,
                      int legLowIndex, int legHighIndex) {
        this.type = type;
        this.orderBlockIndex = orderBlockIndex;
        this.bosIndex = bosIndex;
        this.levelIndex = levelIndex;
        this.top = top;
        this.bottom = bottom;
        this.legLow = legLow;
        this.legHigh = legHigh;
        this.legLowIndex = legLowIndex;
        this.legHighIndex = legHighIndex;
    }

    public ActionType getType() { return type; }
    public int getOrderBlockIndex() { return orderBlockIndex; }
    public int getBosIndex() { return bosIndex; }
    public int getLevelIndex() { return levelIndex; }
    public double getTop() { return top; }
    public double getBottom() { return bottom; }
    public double getLegLow() { return legLow; }
    public double getLegHigh() { return legHigh; }
    public int getLegLowIndex() { return legLowIndex; }
    public int getLegHighIndex() { return legHighIndex; }
}
