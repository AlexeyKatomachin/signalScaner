package com.lavaksoft.bot.trade.strategy;

import com.lavaksoft.bot.trade.indicators.BOSUtils;
import com.lavaksoft.bot.trade.indicators.BPRUtils;
import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.indicators.MarketStructureUtils;
import com.lavaksoft.bot.trade.indicators.OTEUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.BprZone;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.Choch;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.LiquiditySweep;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.StrategyAnalysis;
import com.lavaksoft.bot.trade.model.SwingPoints;
import com.lavaksoft.bot.trade.model.TradeSignal;

import java.util.List;

/**
 * Exact Java adaptation of strategy_bpr.py; timeframe choice is left to the caller.
 */
public class BprStrategy implements SignalStrategy {
    private static final double[] R_MULTIPLES = {1.0, 2.0, 3.5};

    private final MarketStructureUtils structureUtils;
    private final FVGUtils fvgUtils;
    private final BPRUtils bprUtils;
    private final BOSUtils bosUtils;

    public BprStrategy() {
        this(new MarketStructureUtils(), new FVGUtils(), new BPRUtils(), new BOSUtils());
    }

    public BprStrategy(MarketStructureUtils structureUtils, FVGUtils fvgUtils, BPRUtils bprUtils, BOSUtils bosUtils) {
        this.structureUtils = structureUtils;
        this.fvgUtils = fvgUtils;
        this.bprUtils = bprUtils;
        this.bosUtils = bosUtils;
    }

    @Override
    public String getName() {
        return "bpr";
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

        SwingPoints swings = structureUtils.findSwingPoints(ltf);
        LiquiditySweep sweep = bprUtils.detectLiquiditySweep(ltf, swings, bias, 40);
        result.setSweep(sweep);
        if (sweep == null) return result;

        Choch choch = bosUtils.detectChoch(ltf, sweep.getSweepIndex(), bias, structureUtils);
        result.setChoch(choch);
        if (choch == null) return result;

        int fvgStart = Math.max(0, sweep.getSweepIndex() - 10);
        List<FVG> fvgs = fvgUtils.findReferenceFVG(ltf, fvgStart);
        List<BprZone> zones = bprUtils.findBprZones(ltf, fvgs, 30);
        result.getBprZones().addAll(zones);
        if (zones.isEmpty()) return result;

        BprZone zone = zones.get(zones.size() - 1);
        if (!OTEUtils.zonesOverlap(current.getLow(), current.getHigh(), zone.getBottom(), zone.getTop())) return result;

        double entry = OTEUtils.clampToZone(current.getClose(), zone.getBottom(), zone.getTop());
        ActionType direction;
        double stop;
        double risk;
        int sign;
        if (bias == MarketBias.BULLISH) {
            stop = Math.min(sweep.getExtreme(), zone.getBottom()) * 0.999;
            risk = entry - stop;
            direction = ActionType.LONG;
            sign = 1;
        } else {
            stop = Math.max(sweep.getExtreme(), zone.getTop()) * 1.001;
            risk = stop - entry;
            direction = ActionType.SHORT;
            sign = -1;
        }
        if (risk <= 0) return result;

        result.setSignal(new TradeSignal(
                getName(),
                "bpr:" + direction + ":" + zone.getFormedIndex(),
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
