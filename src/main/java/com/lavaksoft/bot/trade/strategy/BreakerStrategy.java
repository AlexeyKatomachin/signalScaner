package com.lavaksoft.bot.trade.strategy;

import com.lavaksoft.bot.trade.indicators.BreakerUtils;
import com.lavaksoft.bot.trade.indicators.MarketStructureUtils;
import com.lavaksoft.bot.trade.indicators.OTEUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.BreakerBlock;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.StrategyAnalysis;
import com.lavaksoft.bot.trade.model.TradeSignal;

import java.util.List;

/**
 * Exact Java adaptation of strategy_breaker.py.
 */
public class BreakerStrategy implements SignalStrategy {
    private static final double[] R_MULTIPLES = {1.0, 2.0, 3.5};

    private final MarketStructureUtils structureUtils;
    private final BreakerUtils breakerUtils;

    public BreakerStrategy() {
        this(new MarketStructureUtils(), new BreakerUtils());
    }

    public BreakerStrategy(MarketStructureUtils structureUtils, BreakerUtils breakerUtils) {
        this.structureUtils = structureUtils;
        this.breakerUtils = breakerUtils;
    }

    @Override
    public String getName() {
        return "breaker";
    }

    @Override
    public StrategyAnalysis analyze(List<Candle> htf, List<Candle> ltf) {
        StrategyAnalysis result = new StrategyAnalysis(getName());
        if (htf == null || htf.isEmpty() || ltf == null || ltf.isEmpty()) return result;

        Candle current = ltf.get(ltf.size() - 1);
        result.setCurrentPrice(current.getClose());
        MarketBias bias = structureUtils.marketBias(htf);
        result.setBias(bias);
        if (bias == MarketBias.NEUTRAL) return result;

        BreakerBlock breaker = breakerUtils.detectBreakerBlock(htf, bias);
        result.setBreaker(breaker);
        if (breaker == null) return result;

        if (!OTEUtils.zonesOverlap(current.getLow(), current.getHigh(), breaker.getBottom(), breaker.getTop()))
            return result;
        double entry = OTEUtils.clampToZone(current.getClose(), breaker.getBottom(), breaker.getTop());

        ActionType direction;
        double stop;
        double risk;
        int sign;
        if (bias == MarketBias.BULLISH) {
            stop = breaker.getBottom() * 0.999;
            risk = entry - stop;
            direction = ActionType.LONG;
            sign = 1;
        } else {
            stop = breaker.getTop() * 1.001;
            risk = stop - entry;
            direction = ActionType.SHORT;
            sign = -1;
        }
        if (risk <= 0) return result;

        result.setSignal(new TradeSignal(
                getName(),
                "breaker:" + direction + ":" + breaker.getInvalidationIndex(),
                direction, bias, entry, stop,
                round2(entry + sign * risk * R_MULTIPLES[0]),
                round2(entry + sign * risk * R_MULTIPLES[1]),
                round2(entry + sign * risk * R_MULTIPLES[2]),
                current.getTime()));
        return result;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
