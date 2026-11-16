package com.lavaksoft.bot.trade.strategy;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.StrategyAnalysis;

import java.util.List;

/**
 * Stateless, test-friendly strategy: indicators in -> structured signal snapshot out.
 */
public interface SignalStrategy {
    String getName();

    StrategyAnalysis analyze(List<Candle> higherTimeframe, List<Candle> lowerTimeframe);
}
