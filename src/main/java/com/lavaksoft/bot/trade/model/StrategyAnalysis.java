package com.lavaksoft.bot.trade.model;

import com.lavaksoft.bot.trade.model.FVG;

import java.util.ArrayList;
import java.util.List;

/** Unified snapshot consumed by tests, UI and execution adapter. */
public class StrategyAnalysis {
    private String strategy;
    private MarketBias bias = MarketBias.NEUTRAL;
    private double currentPrice;
    private LiquiditySweep sweep;
    private Choch choch;
    private final List<BprZone> bprZones = new ArrayList<>();
    private OrderBlock orderBlock;
    private FVG fvg;
    private Boolean fvgOverlapsOrderBlock;
    private OteZone oteZone;
    private BreakerBlock breaker;
    private TradeSignal signal;

    public StrategyAnalysis(String strategy) { this.strategy = strategy; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public MarketBias getBias() { return bias; }
    public void setBias(MarketBias bias) { this.bias = bias; }
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
    public LiquiditySweep getSweep() { return sweep; }
    public void setSweep(LiquiditySweep sweep) { this.sweep = sweep; }
    public Choch getChoch() { return choch; }
    public void setChoch(Choch choch) { this.choch = choch; }
    public List<BprZone> getBprZones() { return bprZones; }
    public OrderBlock getOrderBlock() { return orderBlock; }
    public void setOrderBlock(OrderBlock orderBlock) { this.orderBlock = orderBlock; }
    public FVG getFvg() { return fvg; }
    public void setFvg(FVG fvg) { this.fvg = fvg; }
    public Boolean getFvgOverlapsOrderBlock() { return fvgOverlapsOrderBlock; }
    public void setFvgOverlapsOrderBlock(Boolean value) { this.fvgOverlapsOrderBlock = value; }
    public OteZone getOteZone() { return oteZone; }
    public void setOteZone(OteZone oteZone) { this.oteZone = oteZone; }
    public BreakerBlock getBreaker() { return breaker; }
    public void setBreaker(BreakerBlock breaker) { this.breaker = breaker; }
    public TradeSignal getSignal() { return signal; }
    public void setSignal(TradeSignal signal) { this.signal = signal; }
}
