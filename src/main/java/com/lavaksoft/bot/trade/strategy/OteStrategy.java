package com.lavaksoft.bot.trade.strategy;

import com.lavaksoft.bot.trade.indicators.MarketStructureUtils;
import com.lavaksoft.bot.trade.indicators.OTEUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FvgConfluence;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;
import com.lavaksoft.bot.trade.model.OteZone;
import com.lavaksoft.bot.trade.model.StrategyAnalysis;
import com.lavaksoft.bot.trade.model.TradeSignal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Exact Java adaptation of strategy_ote.py.
 */
public class OteStrategy implements SignalStrategy {
    private static final double[] R_MULTIPLES = {1.0, 2.0, 3.5};

    private final MarketStructureUtils structureUtils;
    private final OTEUtils oteUtils;
    private final boolean longOnly;
    private final Set<Integer> avoidHoursUtc;
    private final Set<Integer> allowHoursUtc;

    public OteStrategy() {
        this(new MarketStructureUtils(), new OTEUtils(), false, Collections.emptySet(), null);
    }

    public OteStrategy(MarketStructureUtils structureUtils, OTEUtils oteUtils,
                       boolean longOnly, Set<Integer> avoidHoursUtc, Set<Integer> allowHoursUtc) {
        this.structureUtils = structureUtils;
        this.oteUtils = oteUtils;
        this.longOnly = longOnly;
        this.avoidHoursUtc = avoidHoursUtc == null ? Collections.emptySet() : avoidHoursUtc;
        this.allowHoursUtc = allowHoursUtc;
    }

    @Override
    public String getName() {
        return "ote";
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

        OrderBlock orderBlock = oteUtils.detectOrderBlock(htf, bias);
        result.setOrderBlock(orderBlock);
        if (orderBlock == null) return result;

        FvgConfluence confluence = oteUtils.findConfluentFvg(htf, orderBlock);
        if (confluence == null) return result;
        result.setFvg(confluence.getFvg());
        result.setFvgOverlapsOrderBlock(confluence.isOverlapsOrderBlock());

        OteZone ote = oteUtils.computeOteZone(orderBlock);
        result.setOteZone(ote);

        boolean inOte = OTEUtils.zonesOverlap(current.getLow(), current.getHigh(), ote.getBottom(), ote.getTop());
        boolean inConfluence = OTEUtils.zonesOverlap(current.getLow(), current.getHigh(), orderBlock.getBottom(), orderBlock.getTop()) ||
                OTEUtils.zonesOverlap(current.getLow(), current.getHigh(), confluence.getFvg().getLow(), confluence.getFvg().getHigh());
        if (!(inOte && inConfluence)) return result;

        double entry = OTEUtils.clampToZone(current.getClose(), ote.getBottom(), ote.getTop());
        ActionType direction;
        double stop;
        double risk;
        int sign;
        if (bias == MarketBias.BULLISH) {
            stop = Math.min(orderBlock.getBottom(), ote.getBottom()) * 0.999;
            risk = entry - stop;
            direction = ActionType.LONG;
            sign = 1;
        } else {
            stop = Math.max(orderBlock.getTop(), ote.getTop()) * 1.001;
            risk = stop - entry;
            direction = ActionType.SHORT;
            sign = -1;
        }
        if (risk <= 0) return result;

        int hourUtc = Instant.ofEpochMilli(current.getTime()).atZone(ZoneOffset.UTC).getHour();
        if (longOnly && direction != ActionType.LONG) return result;
        if (avoidHoursUtc.contains(hourUtc)) return result;
        if (allowHoursUtc != null && !allowHoursUtc.contains(hourUtc)) return result;

        result.setSignal(new TradeSignal(
                getName(),
                "ote:" + direction + ":" + orderBlock.getOrderBlockIndex(),
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
