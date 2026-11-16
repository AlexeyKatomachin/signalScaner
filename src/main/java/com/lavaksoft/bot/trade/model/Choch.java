package com.lavaksoft.bot.trade.model;

public class Choch {
    private final int chochIndex;
    private final double level;
    private final int referenceIndex;

    public Choch(int chochIndex, double level, int referenceIndex) {
        this.chochIndex = chochIndex;
        this.level = level;
        this.referenceIndex = referenceIndex;
    }

    public int getChochIndex() {
        return chochIndex;
    }

    public double getLevel() {
        return level;
    }

    public int getReferenceIndex() {
        return referenceIndex;
    }
}
