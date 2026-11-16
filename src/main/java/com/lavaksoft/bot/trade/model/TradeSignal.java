package com.lavaksoft.bot.trade.model;

/**
 * Strategy output independent from broker execution and the legacy Order model.
 */
public class TradeSignal {
    private final String strategy;
    private final String setupId;
    private final ActionType direction;
    private final MarketBias bias;
    private final double entry;
    private final double stop;
    private final double tp1;
    private final double tp2;
    private final double tp3;
    private final long time;

    public TradeSignal(String strategy, String setupId, ActionType direction, MarketBias bias,
                       double entry, double stop, double tp1, double tp2, double tp3, long time) {
        this.strategy = strategy;
        this.setupId = setupId;
        this.direction = direction;
        this.bias = bias;
        this.entry = entry;
        this.stop = stop;
        this.tp1 = tp1;
        this.tp2 = tp2;
        this.tp3 = tp3;
        this.time = time;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getSetupId() {
        return setupId;
    }

    public ActionType getDirection() {
        return direction;
    }

    public MarketBias getBias() {
        return bias;
    }

    public double getEntry() {
        return entry;
    }

    public double getStop() {
        return stop;
    }

    public double getTp1() {
        return tp1;
    }

    public double getTp2() {
        return tp2;
    }

    public double getTp3() {
        return tp3;
    }

    public long getTime() {
        return time;
    }
}
