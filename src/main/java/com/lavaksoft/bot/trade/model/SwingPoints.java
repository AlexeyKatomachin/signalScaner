package com.lavaksoft.bot.trade.model;

import java.util.Collections;
import java.util.List;

/**
 * Confirmed pivot indexes. An index is confirmed only after right-side candles exist.
 */
public class SwingPoints {
    private final List<Integer> highs;
    private final List<Integer> lows;

    public SwingPoints(List<Integer> highs, List<Integer> lows) {
        this.highs = Collections.unmodifiableList(highs);
        this.lows = Collections.unmodifiableList(lows);
    }

    public List<Integer> getHighs() {
        return highs;
    }

    public List<Integer> getLows() {
        return lows;
    }
}
