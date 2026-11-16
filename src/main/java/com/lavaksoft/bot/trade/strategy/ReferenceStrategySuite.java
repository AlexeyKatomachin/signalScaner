package com.lavaksoft.bot.trade.strategy;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.StrategyAnalysis;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs all imported/reference strategies on the same HTF/LTF snapshots.
 */
public class ReferenceStrategySuite {
    private final List<SignalStrategy> strategies;

    public ReferenceStrategySuite() {
        this(Arrays.asList(new BprStrategy(), new OteStrategy(), new BreakerStrategy()));
    }

    public ReferenceStrategySuite(List<SignalStrategy> strategies) {
        this.strategies = strategies;
    }

    public Map<String, StrategyAnalysis> analyze(List<Candle> higherTimeframe, List<Candle> lowerTimeframe) {
        Map<String, StrategyAnalysis> result = new LinkedHashMap<>();
        for (SignalStrategy strategy : strategies) {
            result.put(strategy.getName(), strategy.analyze(higherTimeframe, lowerTimeframe));
        }
        return result;
    }
}
