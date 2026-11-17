package com.lavaksoft.bot.trade.model;

/**
 * A confirmed close through a previously confirmed structural pivot.
 *
 * The reference pivot must already be confirmed when the break candle closes,
 * which makes this object safe to use in replay/backtest code.
 */
public class StructureBreak {
    private final StructureBreakType type;
    private final ActionType direction;
    private final int referenceIndex;
    private final int breakIndex;
    private final double level;

    public StructureBreak(StructureBreakType type, ActionType direction, int referenceIndex, int breakIndex, double level) {
        this.type = type;
        this.direction = direction;
        this.referenceIndex = referenceIndex;
        this.breakIndex = breakIndex;
        this.level = level;
    }

    public StructureBreakType getType() {
        return type;
    }

    public ActionType getDirection() {
        return direction;
    }

    public int getReferenceIndex() {
        return referenceIndex;
    }

    public int getBreakIndex() {
        return breakIndex;
    }

    public double getLevel() {
        return level;
    }
}
