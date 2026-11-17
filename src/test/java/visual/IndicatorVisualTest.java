package visual;

import com.lavaksoft.bot.trade.indicators.BOSUtils;
import com.lavaksoft.bot.trade.indicators.BPRUtils;
import com.lavaksoft.bot.trade.indicators.BreakerUtils;
import com.lavaksoft.bot.trade.indicators.ContextUtils;
import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.indicators.FractalUtils;
import com.lavaksoft.bot.trade.indicators.LiquidityUtils;
import com.lavaksoft.bot.trade.indicators.MarketStructureUtils;
import com.lavaksoft.bot.trade.indicators.OTEUtils;
import com.lavaksoft.bot.trade.indicators.POIUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.BprZone;
import com.lavaksoft.bot.trade.model.BreakerBlock;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.Choch;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.FvgConfluence;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.Liquidity;
import com.lavaksoft.bot.trade.model.LiquiditySweep;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;
import com.lavaksoft.bot.trade.model.OteZone;
import com.lavaksoft.bot.trade.model.POI;
import com.lavaksoft.bot.trade.model.StructureBreak;
import com.lavaksoft.bot.trade.model.SwingPoints;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Human-readable indicator validation. Each scenario generates before.png, after.png,
 * context.json and result.txt and is linked from target/indicator-tests/index.html.
 */
class IndicatorVisualTest {

    private static final String REAL_BTC = "/market-data/BTCUSDT_15m_400.csv";
    private static final String FRACTAL_BASIC = "/indicator-scenarios/fractal/basic.csv";
    private static final String FVG_BULLISH = "/indicator-scenarios/fvg/bullish.csv";
    private static final String FVG_BEARISH = "/indicator-scenarios/fvg/bearish.csv";

    private final IndicatorChartRenderer renderer = new IndicatorChartRenderer();

    @Test
    void fractalRealMarketScenario() throws IOException {
        // Exact small assertion remains, but the human-facing chart uses enough real candles to be readable.
        List<Candle> synthetic = load(FRACTAL_BASIC);
        List<Fractal> exact = new FractalUtils().findFractals(synthetic);
        assertFalse(exact.isEmpty());
        assertTrue(synthetic.get(1).getFractal() != null && synthetic.get(1).getFractal().isHigh());
        assertTrue(synthetic.get(2).getFractal() != null && synthetic.get(2).getFractal().isLow());

        List<Candle> candles = tail(load(REAL_BTC), 180);
        IndicatorArtifacts.Scenario scenario = start("fractal", "btc-real-readable", REAL_BTC, candles, "Fractal");
        List<Fractal> fractals = new FractalUtils().findFractals(candles);
        renderer.renderFractals(candles, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "Fractal", "count", fractals.size(), "fractals", fractalMaps(candles, fractals)),
                "Real candles shown: " + candles.size(),
                "Detected basic fractals: " + fractals.size(),
                "FH/FL must sit exactly on local wick extrema; the synthetic anchor assertion is still executed before rendering.");
    }

    @Test
    void keyFractalRealMarketScenario() throws IOException {
        List<Candle> candles = tail(load(REAL_BTC), 220);
        IndicatorArtifacts.Scenario scenario = start("key-fractal", "btc-real", REAL_BTC, candles, "Key fractal / market structure");
        FractalUtils utils = new FractalUtils();
        utils.findFractals(candles);
        List<KeyFractal> keys = utils.findKeyFractals(candles);
        assertFalse(keys.isEmpty());
        renderer.renderKeyFractals(candles, keys, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "KeyFractal", "count", keys.size(), "detected", keyMaps(candles, keys)),
                "Key pivots: " + keys.size(),
                "Highs are HH/LH versus the previous structural high; lows are HL/LL versus the previous structural low.",
                "The zig-zag must visually show context transitions rather than isolated orange dots.");
    }

    @Test
    void legacyFvgRealMarketScenario() throws IOException {
        // Preserve the exact legacy rule assertion.
        List<Candle> synthetic = load(FVG_BULLISH);
        List<FVG> exact = new FVGUtils().findFVG(synthetic);
        assertFalse(exact.isEmpty());
        assertEquals(ActionType.LONG, exact.get(0).getType());
        assertEquals(103.0, exact.get(0).getLow(), 1e-9);
        assertEquals(105.0, exact.get(0).getHigh(), 1e-9);

        List<Candle> candles = tail(load(REAL_BTC), 150);
        IndicatorArtifacts.Scenario scenario = start("fvg-legacy", "btc-real-readable", REAL_BTC, candles, "Legacy FVG");
        List<FVG> fvgs = new FVGUtils().findFVG(candles);
        assertFalse(fvgs.isEmpty());
        renderer.renderFvg(candles, fvgs, "Legacy FVG", scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "FVG_LEGACY", "count", fvgs.size(), "fvgs", fvgMaps(candles, fvgs)),
                "Real candles shown: " + candles.size(),
                "Legacy FVG count: " + fvgs.size(),
                "Only the most recent zones are rendered and active zones no longer cover the whole chart forever.");
    }

    @Test
    void referenceFvgRealMarketScenario() throws IOException {
        // Both directions are still verified exactly on deterministic fixtures.
        assertReferenceFvg(load(FVG_BULLISH), ActionType.LONG, 103.0, 105.0);
        assertReferenceFvg(load(FVG_BEARISH), ActionType.SHORT, 105.0, 107.0);

        List<Candle> candles = tail(load(REAL_BTC), 150);
        IndicatorArtifacts.Scenario scenario = start("fvg-reference", "btc-real-readable", REAL_BTC, candles, "Reference FVG");
        List<FVG> fvgs = new FVGUtils().findReferenceFVG(candles);
        assertFalse(fvgs.isEmpty());
        renderer.renderFvg(candles, fvgs, "Reference FVG", scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "FVG_REFERENCE", "count", fvgs.size(), "fvgs", fvgMaps(candles, fvgs)),
                "Real candles shown: " + candles.size(),
                "Reference FVG count: " + fvgs.size(),
                "Synthetic LONG and SHORT definitions are asserted before the real-market visualization.");
    }

    @Test
    void marketStructureRealMarketScenario() throws IOException {
        List<Candle> candles = tail(load(REAL_BTC), 220);
        IndicatorArtifacts.Scenario scenario = start("market-structure", "btc-real", REAL_BTC, candles, "Market structure");
        FractalUtils fractalUtils = new FractalUtils();
        fractalUtils.findFractals(candles);
        List<KeyFractal> keys = fractalUtils.findKeyFractals(candles);
        MarketBias bias = new MarketStructureUtils().marketBias(candles, 2, 2);
        assertFalse(keys.isEmpty());
        renderer.renderMarketStructure(candles, keys, bias, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "MarketStructure", "bias", bias, "keyFractals", keyMaps(candles, keys)),
                "Bias: " + bias,
                "Real candles shown: " + candles.size(),
                "Read the structure directly from the HH/HL/LH/LL zig-zag.");
    }

    @Test
    void sweepAndChochMainStructureScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("sweep-choch", "btc-main-structure", REAL_BTC, candles, "Main structure sweep + CHoCH");
        MarketStructureUtils structure = new MarketStructureUtils();
        SwingPoints points = structure.findSwingPoints(candles, 2, 2);
        List<LiquiditySweep> sweeps = new BPRUtils().detectLiquiditySweeps(candles, points, 8);
        assertFalse(sweeps.isEmpty());

        BOSUtils bos = new BOSUtils();
        List<Choch> chochs = new ArrayList<>();
        for (LiquiditySweep sweep : sweeps) {
            MarketBias expectedReversal = sweep.getExtreme() < sweep.getLevel() ? MarketBias.BULLISH : MarketBias.BEARISH;
            Choch choch = bos.detectChoch(candles, sweep.getSweepIndex(), expectedReversal, structure);
            if (choch != null) chochs.add(choch);
        }
        renderer.renderSweepChoch(candles, sweeps, chochs, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "SweepChoch", "sweeps", sweepMaps(sweeps), "chochCount", chochs.size()),
                "Main-structure sweeps shown: " + sweeps.size(),
                "CHoCH confirmations shown: " + chochs.size(),
                "Unlike the old tiny synthetic chart this runs across the complete 400-candle fixture.");
    }

    @Test
    void contextRealMarketScenario() throws IOException {
        AnalysisPipeline p = legacyPipeline();
        IndicatorArtifacts.Scenario scenario = start("context", "btc-real", REAL_BTC, p.candles, "Context");
        assertFalse(p.contexts.isEmpty(), "No structural contexts produced");
        renderer.renderContexts(p.candles, p.contexts, scenario.after());
        List<Map<String, Object>> contexts = new ArrayList<>();
        for (int i = 0; i < p.contexts.size(); i++) {
            ContextFlow c = p.contexts.get(i);
            contexts.add(IndicatorArtifacts.map("index", i, "type", c.getType(), "hasBOS", c.isHasBOS(),
                    "keyFractals", keyMaps(p.candles, c.getKeyFractals())));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "Context", "count", p.contexts.size(), "contexts", contexts),
                "Contexts: " + p.contexts.size(),
                "LONG context is built from HH+HL; SHORT context from LH+LL.",
                "The chart now shows the structural zig-zag instead of opaque full-height background bands.");
    }

    @Test
    void liquidityRealMarketScenario() throws IOException {
        AnalysisPipeline p = legacyPipeline();
        IndicatorArtifacts.Scenario scenario = start("liquidity", "btc-real", REAL_BTC, p.candles, "Liquidity");
        renderer.renderLiquidity(p.candles, p.liquidities, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "Liquidity", "count", p.liquidities.size(), "liquidity", liquidityMaps(p.candles, p.liquidities)),
                "Liquidity objects: " + p.liquidities.size(),
                "EQH/EQL now use an ATR-relative price tolerance; candle distance no longer widens the allowed price error.",
                "The chart intentionally renders only equal levels plus a few recent active liquidity levels.");
    }

    @Test
    void poiRealMarketScenario() throws IOException {
        AnalysisPipeline p = legacyPipeline();
        IndicatorArtifacts.Scenario scenario = start("poi", "btc-real", REAL_BTC, p.candles, "POI");
        renderer.renderPoi(p.candles, p.pois, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "POI", "count", p.pois.size()),
                "POIs: " + p.pois.size(),
                "Only the latest POIs are rendered to keep the chart readable.");
    }

    @Test
    void bosRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("bos", "btc-real", REAL_BTC, candles, "BOS / CHoCH");
        List<StructureBreak> breaks = new BOSUtils().findStructureBreaks(candles, 2, 2);
        assertFalse(breaks.isEmpty(), "No structure breaks were produced on the real-market fixture");
        renderer.renderBos(candles, breaks, scenario.after());
        List<Map<String, Object>> items = new ArrayList<>();
        for (StructureBreak b : breaks) {
            items.add(IndicatorArtifacts.map("type", b.getType(), "direction", b.getDirection(),
                    "referenceIndex", b.getReferenceIndex(), "breakIndex", b.getBreakIndex(), "level", b.getLevel()));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "BOS_CHOCH", "count", breaks.size(), "breaks", items),
                "Structure breaks: " + breaks.size(),
                "Each line now stops on the candle whose CLOSE actually broke the confirmed pivot.",
                "A break against the previous structural trend is labelled CHoCH; continuation is BOS.");
    }

    @Test
    void oteMultipleRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("ote", "btc-real-multiple", REAL_BTC, candles, "OTE multiple setups");
        OTEUtils utils = new OTEUtils();
        List<OrderBlock> blocks = new ArrayList<>();
        blocks.addAll(utils.detectOrderBlocks(candles, MarketBias.BULLISH, 350, 2, 2, 4));
        blocks.addAll(utils.detectOrderBlocks(candles, MarketBias.BEARISH, 350, 2, 2, 4));
        blocks.sort(Comparator.comparingInt(OrderBlock::getBosIndex));
        if (blocks.size() > 6) blocks = new ArrayList<>(blocks.subList(blocks.size() - 6, blocks.size()));
        assertFalse(blocks.isEmpty(), "No historical OTE impulses found");

        List<OteZone> zones = new ArrayList<>();
        List<FVG> fvgs = new ArrayList<>();
        List<Map<String, Object>> setups = new ArrayList<>();
        for (OrderBlock ob : blocks) {
            OteZone zone = utils.computeOteZone(ob);
            FvgConfluence confluence = utils.findConfluentFvg(candles, ob);
            zones.add(zone);
            fvgs.add(confluence == null ? null : confluence.getFvg());
            setups.add(IndicatorArtifacts.map("orderBlock", orderBlockMap(ob),
                    "ote", IndicatorArtifacts.map("bottom", zone.getBottom(), "top", zone.getTop(), "mid", zone.getMid()),
                    "fvg", confluence == null ? null : fvgMap(candles, confluence.getFvg())));
        }
        renderer.renderOte(candles, blocks, fvgs, zones, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "OTE", "setupCount", blocks.size(), "setups", setups),
                "OTE setups shown: " + blocks.size(),
                "LEG LOW / LEG HIGH markers show exactly which impulse range produces 0.618-0.786.",
                "The impulse is frozen at the structure-break candle; later candles cannot modify the OTE range.");
    }

    @Test
    void bprReadableRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("bpr", "btc-real-readable", REAL_BTC, candles, "BPR readable");
        List<FVG> fvgs = new FVGUtils().findReferenceFVG(candles);
        List<BprZone> zones = new BPRUtils().findRecentBprZones(candles, fvgs, 15, 6);
        assertFalse(zones.isEmpty(), "No recent BPR overlaps found");
        renderer.renderBpr(candles, zones, scenario.after());
        List<Map<String, Object>> items = new ArrayList<>();
        for (BprZone z : zones) {
            items.add(IndicatorArtifacts.map("formedIndex", z.getFormedIndex(), "time", z.getTime(),
                    "bottom", z.getBottom(), "top", z.getTop(),
                    "firstFvg", fvgMap(candles, z.getFirstFvg()), "secondFvg", fvgMap(candles, z.getSecondFvg())));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "BPR", "shown", zones.size(), "zones", items),
                "BPR zones shown: " + zones.size(),
                "Only the recent BPRs and their two parent FVGs are rendered.",
                "No full-history carpet of unrelated FVG rectangles is drawn anymore.");
    }

    @Test
    void breakerMultipleRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("breaker", "btc-real-multiple", REAL_BTC, candles, "Breaker multiple");
        BreakerUtils utils = new BreakerUtils();
        List<BreakerBlock> breakers = new ArrayList<>();
        breakers.addAll(utils.detectBreakerBlocks(candles, MarketBias.BULLISH, 350, 4));
        breakers.addAll(utils.detectBreakerBlocks(candles, MarketBias.BEARISH, 350, 4));
        breakers.sort(Comparator.comparingInt(BreakerBlock::getInvalidationIndex));
        if (breakers.size() > 6) breakers = new ArrayList<>(breakers.subList(breakers.size() - 6, breakers.size()));
        assertFalse(breakers.isEmpty(), "No breakers found on the real-market fixture");
        renderer.renderBreaker(candles, breakers, scenario.after());
        List<Map<String, Object>> items = new ArrayList<>();
        for (BreakerBlock b : breakers) {
            items.add(IndicatorArtifacts.map("originOrderBlockIndex", b.getOriginOrderBlockIndex(),
                    "invalidationIndex", b.getInvalidationIndex(), "bottom", b.getBottom(), "top", b.getTop()));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "Breaker", "count", breakers.size(), "breakers", items),
                "Breakers shown: " + breakers.size(),
                "Each zone starts at the original OB and is explicitly marked at its invalidation candle.");
    }

    private AnalysisPipeline legacyPipeline() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        FractalUtils fractal = new FractalUtils();
        fractal.findFractals(candles);
        fractal.findKeyFractals(candles);
        new FVGUtils().findFVG(candles);
        List<ContextFlow> contexts = new ContextUtils().getContextFlow(candles);
        List<Liquidity> liquidities = contexts.isEmpty() ? List.of() : new LiquidityUtils().findLiquidity(candles, contexts);
        List<POI> pois = new POIUtils().findPOI(candles);
        return new AnalysisPipeline(candles, contexts, liquidities, pois);
    }

    private IndicatorArtifacts.Scenario start(String indicator, String name, String dataset, List<Candle> candles, String title) throws IOException {
        IndicatorArtifacts.Scenario scenario = IndicatorArtifacts.create(indicator, name, dataset, candles.size());
        renderer.renderBefore(candles, title, scenario.before());
        assertImage(scenario.before());
        return scenario;
    }

    private void finish(IndicatorArtifacts.Scenario scenario, Map<String, Object> context, String... resultLines) throws IOException {
        assertImage(scenario.after());
        IndicatorArtifacts.finish(scenario, context, List.of(resultLines));
        assertTrue(Files.exists(scenario.context()));
        assertTrue(Files.exists(scenario.result()));
        assertTrue(Files.exists(IndicatorArtifacts.ROOT.resolve("index.html")));
    }

    private static List<Candle> load(String dataset) throws IOException { return CandleCsvLoader.load(dataset); }

    private static List<Candle> tail(List<Candle> candles, int count) {
        if (candles.size() <= count) return candles;
        return new ArrayList<>(candles.subList(candles.size() - count, candles.size()));
    }

    private static void assertImage(java.nio.file.Path file) throws IOException {
        assertTrue(Files.exists(file), "Image was not created: " + file);
        assertTrue(Files.size(file) > 0, "Image is empty: " + file);
    }

    private static void assertReferenceFvg(List<Candle> candles, ActionType type, double low, double high) {
        List<FVG> fvgs = new FVGUtils().findReferenceFVG(candles);
        assertFalse(fvgs.isEmpty());
        assertEquals(type, fvgs.get(0).getType());
        assertEquals(low, fvgs.get(0).getLow(), 1e-9);
        assertEquals(high, fvgs.get(0).getHigh(), 1e-9);
    }

    private static List<Map<String, Object>> fractalMaps(List<Candle> candles, List<Fractal> fractals) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Fractal f : fractals) result.add(IndicatorArtifacts.map("index", candles.indexOf(f.getCandle()),
                "high", f.isHigh(), "low", f.isLow(), "highPrice", f.getCandle().getHigh(), "lowPrice", f.getCandle().getLow()));
        return result;
    }

    private static List<Map<String, Object>> keyMaps(List<Candle> candles, List<KeyFractal> keys) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (KeyFractal key : keys) {
            Candle c = key.getFractal().getCandle();
            result.add(IndicatorArtifacts.map("index", candles.indexOf(c), "time", c.getTime(), "type", key.getType(),
                    "price", key.getFractal().isHigh() ? c.getHigh() : c.getLow(), "broken", key.isHasBos()));
        }
        return result;
    }

    private static List<Map<String, Object>> fvgMaps(List<Candle> candles, List<FVG> fvgs) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FVG fvg : fvgs) items.add(fvgMap(candles, fvg));
        return items;
    }

    private static Map<String, Object> fvgMap(List<Candle> candles, FVG fvg) {
        if (fvg == null) return null;
        return IndicatorArtifacts.map("index", candles.indexOf(fvg.getCandle()), "time", fvg.getCandle().getTime(), "type", fvg.getType(),
                "low", fvg.getLow(), "high", fvg.getHigh(), "tested", fvg.isTested(), "fulfilled", fvg.isFulfilled());
    }

    private static List<Map<String, Object>> liquidityMaps(List<Candle> candles, List<Liquidity> liquidities) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Liquidity l : liquidities) {
            Candle c = l.getFractal().getCandle();
            result.add(IndicatorArtifacts.map("index", candles.indexOf(c), "type", l.getType(), "fulfilled", l.isFulfilled(),
                    "price", l.getFractal().isHigh() ? c.getHigh() : c.getLow()));
        }
        return result;
    }

    private static List<Map<String, Object>> sweepMaps(List<LiquiditySweep> sweeps) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LiquiditySweep s : sweeps) result.add(IndicatorArtifacts.map("sweptIndex", s.getSweptIndex(), "sweepIndex", s.getSweepIndex(),
                "level", s.getLevel(), "extreme", s.getExtreme()));
        return result;
    }

    private static Map<String, Object> orderBlockMap(OrderBlock ob) {
        return IndicatorArtifacts.map("type", ob.getType(), "orderBlockIndex", ob.getOrderBlockIndex(), "bosIndex", ob.getBosIndex(),
                "levelIndex", ob.getLevelIndex(), "bottom", ob.getBottom(), "top", ob.getTop(),
                "legLow", ob.getLegLow(), "legLowIndex", ob.getLegLowIndex(),
                "legHigh", ob.getLegHigh(), "legHighIndex", ob.getLegHighIndex());
    }

    private static final class AnalysisPipeline {
        private final List<Candle> candles;
        private final List<ContextFlow> contexts;
        private final List<Liquidity> liquidities;
        private final List<POI> pois;

        private AnalysisPipeline(List<Candle> candles, List<ContextFlow> contexts, List<Liquidity> liquidities, List<POI> pois) {
            this.candles = candles;
            this.contexts = contexts;
            this.liquidities = liquidities;
            this.pois = pois;
        }
    }
}
