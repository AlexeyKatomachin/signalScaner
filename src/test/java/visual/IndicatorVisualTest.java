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
import com.lavaksoft.bot.trade.model.SwingPoints;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Human-readable validation suite for market indicators.
 * <p>
 * Every scenario produces:
 * - before.png  : raw candles only;
 * - after.png   : the exact same candles with the indicator overlay;
 * - context.json: machine-readable values detected by the indicator;
 * - result.txt  : short summary/checklist;
 * - target/indicator-tests/index.html aggregates all scenarios.
 */
class IndicatorVisualTest {

    private static final String REAL_BTC = "/market-data/BTCUSDT_15m_400.csv";
    private static final String FRACTAL_BASIC = "/indicator-scenarios/fractal/basic.csv";
    private static final String FVG_BULLISH = "/indicator-scenarios/fvg/bullish.csv";
    private static final String FVG_BEARISH = "/indicator-scenarios/fvg/bearish.csv";
    private static final String STRUCTURE_BULLISH = "/indicator-scenarios/market-structure/bullish.csv";
    private static final String SWEEP_CHOCH_BULLISH = "/indicator-scenarios/market-structure/sweep-choch-bullish.csv";

    private final IndicatorChartRenderer renderer = new IndicatorChartRenderer();

    @Test
    void fractalSyntheticScenario() throws IOException {
        List<Candle> candles = load(FRACTAL_BASIC);
        IndicatorArtifacts.Scenario scenario = start("fractal", "synthetic-basic", FRACTAL_BASIC, candles, "Fractal");

        List<Fractal> fractals = new FractalUtils().findFractals(candles);
        assertFalse(fractals.isEmpty());
        assertTrue(candles.get(1).getFractal() != null && candles.get(1).getFractal().isHigh(), "Index 1 must be a high fractal");
        assertTrue(candles.get(2).getFractal() != null && candles.get(2).getFractal().isLow(), "Index 2 must be a low fractal");

        renderer.renderFractals(candles, scenario.after());
        List<Map<String, Object>> detected = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Fractal f = candles.get(i).getFractal();
            if (f != null && (f.isHigh() || f.isLow())) {
                detected.add(IndicatorArtifacts.map("index", i, "time", candles.get(i).getTime(), "high", f.isHigh(), "low", f.isLow(),
                        "price", f.isHigh() ? candles.get(i).getHigh() : candles.get(i).getLow()));
            }
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "Fractal", "detected", detected),
                "Detected fractals: " + detected.size(),
                "Expected anchors: index 1 = FH, index 2 = FL",
                "Visual check: every marker should sit exactly on the candle wick extremum.");
    }

    @Test
    void keyFractalRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("key-fractal", "btc-real", REAL_BTC, candles, "Key fractal");
        FractalUtils utils = new FractalUtils();
        utils.findFractals(candles);
        List<KeyFractal> keys = utils.findKeyFractals(candles);
        assertFalse(keys.isEmpty(), "Key-fractal detector returned no results");
        renderer.renderKeyFractals(candles, scenario.after());

        List<Map<String, Object>> detected = new ArrayList<>();
        for (KeyFractal key : keys) {
            Candle c = key.getFractal().getCandle();
            detected.add(IndicatorArtifacts.map("index", candles.indexOf(c), "time", c.getTime(), "type", key.getType(),
                    "price", key.getFractal().isHigh() ? c.getHigh() : c.getLow(), "bos", key.isHasBos()));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "KeyFractal", "count", keys.size(), "detected", detected),
                "Key fractals: " + keys.size(),
                "Visual check: HH/HL/LH/LL must match the market structure you expect.",
                "Important: this is the legacy signalScaner key-fractal algorithm.");
    }

    @Test
    void legacyFvgSyntheticScenario() throws IOException {
        List<Candle> candles = load(FVG_BULLISH);
        IndicatorArtifacts.Scenario scenario = start("fvg-legacy", "bullish-synthetic", FVG_BULLISH, candles, "Legacy FVG");
        List<FVG> fvgs = new FVGUtils().findFVG(candles);
        assertFalse(fvgs.isEmpty());
        FVG first = fvgs.get(0);
        assertEquals(ActionType.LONG, first.getType());
        assertEquals(103.0, first.getLow(), 1e-9);
        assertEquals(105.0, first.getHigh(), 1e-9);
        renderer.renderFvg(candles, fvgs, "Legacy FVG", scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "FVG_LEGACY", "count", fvgs.size(), "fvgs", fvgMaps(candles, fvgs)),
                "Legacy FVG count: " + fvgs.size(),
                "Expected first bullish FVG: 103.0 .. 105.0",
                "Visual check: the green FVG zone must be between candle #0 high and candle #2 low.");
    }

    @Test
    void referenceFvgBullishSyntheticScenario() throws IOException {
        referenceFvgScenario("bullish-synthetic", FVG_BULLISH, ActionType.LONG, 103.0, 105.0);
    }

    @Test
    void referenceFvgBearishSyntheticScenario() throws IOException {
        referenceFvgScenario("bearish-synthetic", FVG_BEARISH, ActionType.SHORT, 105.0, 107.0);
    }

    @Test
    void marketStructureSyntheticScenario() throws IOException {
        List<Candle> candles = load(STRUCTURE_BULLISH);
        IndicatorArtifacts.Scenario scenario = start("market-structure", "bullish-synthetic", STRUCTURE_BULLISH, candles, "Market structure");
        MarketStructureUtils utils = new MarketStructureUtils();
        SwingPoints points = utils.findSwingPoints(candles, 2, 2);
        MarketBias bias = utils.marketBias(candles, 2, 2);
        assertEquals(List.of(2, 6, 10), points.getHighs());
        assertEquals(List.of(4, 8, 12), points.getLows());
        assertEquals(MarketBias.BULLISH, bias);
        renderer.renderMarketStructure(candles, points, bias, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "MarketStructure", "bias", bias,
                        "swingHighIndexes", points.getHighs(), "swingLowIndexes", points.getLows()),
                "Bias: " + bias,
                "Swing highs: " + points.getHighs(),
                "Swing lows: " + points.getLows(),
                "Expected: higher highs and higher lows => BULLISH.");
    }

    @Test
    void sweepAndChochSyntheticScenario() throws IOException {
        List<Candle> candles = load(SWEEP_CHOCH_BULLISH);
        IndicatorArtifacts.Scenario scenario = start("sweep-choch", "bullish-synthetic", SWEEP_CHOCH_BULLISH, candles, "Sweep + CHoCH");
        MarketStructureUtils structure = new MarketStructureUtils();
        BPRUtils bpr = new BPRUtils();
        BOSUtils bos = new BOSUtils();

        List<Candle> knownAtSweep = new ArrayList<>(candles.subList(0, 12));
        SwingPoints knownPoints = structure.findSwingPoints(knownAtSweep, 2, 2);
        LiquiditySweep sweep = bpr.detectLiquiditySweep(knownAtSweep, knownPoints, MarketBias.BULLISH, 50);
        assertNotNull(sweep, "Expected bullish liquidity sweep was not detected");
        assertEquals(8, sweep.getSweptIndex());
        assertEquals(11, sweep.getSweepIndex());

        Choch choch = bos.detectChoch(candles, sweep.getSweepIndex(), MarketBias.BULLISH, structure);
        assertNotNull(choch, "Expected CHoCH was not detected");
        assertEquals(12, choch.getChochIndex());
        assertEquals(6, choch.getReferenceIndex());
        renderer.renderSweepChoch(candles, sweep, choch, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "Sweep+CHoCH",
                        "sweep", IndicatorArtifacts.map("sweptIndex", sweep.getSweptIndex(), "sweepIndex", sweep.getSweepIndex(),
                                "level", sweep.getLevel(), "extreme", sweep.getExtreme()),
                        "choch", IndicatorArtifacts.map("referenceIndex", choch.getReferenceIndex(), "chochIndex", choch.getChochIndex(), "level", choch.getLevel())),
                "Sweep: candle " + sweep.getSweepIndex() + " takes liquidity from candle " + sweep.getSweptIndex(),
                "CHoCH: candle " + choch.getChochIndex() + " closes through level from candle " + choch.getReferenceIndex(),
                "Timing check: sweep is detected using only candles available up to the sweep candle.");
    }

    @Test
    void contextRealMarketScenario() throws IOException {
        AnalysisPipeline p = legacyPipeline();
        IndicatorArtifacts.Scenario scenario = start("context", "btc-real", REAL_BTC, p.candles, "Context");
        assertFalse(p.contexts.isEmpty(), "Context detector returned no results");
        renderer.renderContexts(p.candles, p.contexts, scenario.after());
        List<Map<String, Object>> contexts = new ArrayList<>();
        for (int i = 0; i < p.contexts.size(); i++) {
            ContextFlow context = p.contexts.get(i);
            contexts.add(IndicatorArtifacts.map("index", i, "type", context.getType(), "hasBOS", context.isHasBOS(),
                    "fromIndex", p.candles.indexOf(context.getCandles().get(0)),
                    "toIndex", p.candles.indexOf(context.getCandles().get(context.getCandles().size() - 1)),
                    "keyFractals", context.getKeyFractals().size()));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "Context", "count", p.contexts.size(), "contexts", contexts),
                "Contexts: " + p.contexts.size(),
                "Visual check: shaded LONG/SHORT ranges should switch where your structural context actually changes.");
    }

    @Test
    void liquidityRealMarketScenario() throws IOException {
        AnalysisPipeline p = legacyPipeline();
        IndicatorArtifacts.Scenario scenario = start("liquidity", "btc-real", REAL_BTC, p.candles, "Liquidity");
        assertFalse(p.liquidities.isEmpty(), "Liquidity detector returned no results");
        renderer.renderLiquidity(p.candles, p.liquidities, scenario.after());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Liquidity l : p.liquidities) {
            Candle c = l.getFractal().getCandle();
            items.add(IndicatorArtifacts.map("index", p.candles.indexOf(c), "time", c.getTime(), "type", l.getType(),
                    "price", l.getFractal().isHigh() ? c.getHigh() : c.getLow(), "fulfilled", l.isFulfilled()));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "Liquidity", "count", p.liquidities.size(), "levels", items),
                "Liquidity levels: " + p.liquidities.size(),
                "Visual check: BSL/SSL/EQH/EQL lines must originate from the intended fractal levels.");
    }

    @Test
    void poiRealMarketScenario() throws IOException {
        AnalysisPipeline p = legacyPipeline();
        IndicatorArtifacts.Scenario scenario = start("poi", "btc-real", REAL_BTC, p.candles, "POI");
        assertFalse(p.pois.isEmpty(), "POI detector returned no results");
        renderer.renderPoi(p.candles, p.pois, scenario.after());
        List<Map<String, Object>> items = new ArrayList<>();
        for (POI poi : p.pois) {
            items.add(IndicatorArtifacts.map("type", poi.getType(), "value", poi.getValue(),
                    "source", poi.getFvg() != null ? "FVG" : "LIQUIDITY"));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "POI", "count", p.pois.size(), "pois", items),
                "POIs: " + p.pois.size(),
                "Visual check: POI markers should correspond only to active FVG/liquidity objects.");
    }

    @Test
    void bosRealMarketScenario() throws IOException {
        AnalysisPipeline p = legacyPipeline();
        IndicatorArtifacts.Scenario scenario = start("bos", "btc-real", REAL_BTC, p.candles, "BOS");
        List<KeyFractal> keys = p.candles.stream().map(Candle::getFractal).filter(Objects::nonNull)
                .map(Fractal::getKeyFractal).filter(Objects::nonNull).collect(Collectors.toList());
        List<KeyFractal> bos = keys.stream().filter(KeyFractal::isHasBos).collect(Collectors.toList());
        assertFalse(bos.isEmpty(), "No BOS flags were produced on the real-market fixture");
        renderer.renderBos(p.candles, keys, scenario.after());
        List<Map<String, Object>> items = new ArrayList<>();
        for (KeyFractal key : bos) {
            Candle c = key.getFractal().getCandle();
            items.add(IndicatorArtifacts.map("index", p.candles.indexOf(c), "type", key.getType(),
                    "price", key.getFractal().isHigh() ? c.getHigh() : c.getLow()));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "BOS", "count", bos.size(), "bos", items),
                "BOS flags: " + bos.size(),
                "Visual check: every BOS line must be tied to the structural level that was actually broken.");
    }

    @Test
    void oteRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("ote", "btc-real", REAL_BTC, candles, "OTE");
        MarketStructureUtils structure = new MarketStructureUtils();
        MarketBias bias = structure.marketBias(candles);
        OTEUtils oteUtils = new OTEUtils();
        OrderBlock ob = oteUtils.detectOrderBlock(candles, bias);
        assertNotNull(ob, "No Order Block detected for current market bias");
        OteZone ote = oteUtils.computeOteZone(ob);
        FvgConfluence confluence = oteUtils.findConfluentFvg(candles, ob);
        assertNotNull(ote);
        assertNotNull(confluence, "Expected an FVG near/inside the detected Order Block on this fixture");
        renderer.renderOte(candles, ob, confluence.getFvg(), ote, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "OTE", "bias", bias,
                        "orderBlock", orderBlockMap(ob),
                        "ote", IndicatorArtifacts.map("bottom", ote.getBottom(), "top", ote.getTop(), "mid", ote.getMid()),
                        "fvg", fvgMap(candles, confluence.getFvg()), "fvgOverlapsOrderBlock", confluence.isOverlapsOrderBlock()),
                "Bias: " + bias,
                "Order block: index " + ob.getOrderBlockIndex() + ", BOS index " + ob.getBosIndex(),
                "OTE zone: " + ote.getBottom() + " .. " + ote.getTop(),
                "FVG confluence overlaps OB: " + confluence.isOverlapsOrderBlock(),
                "Visual check: OB, FVG and OTE must describe the same impulse leg.");
    }

    @Test
    void bprRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("bpr", "btc-real", REAL_BTC, candles, "BPR");
        List<FVG> fvgs = new FVGUtils().findReferenceFVG(candles);
        List<BprZone> zones = new BPRUtils().findBprZones(candles, fvgs, 15);
        assertFalse(fvgs.isEmpty());
        assertFalse(zones.isEmpty(), "No BPR overlaps were found on the real-market fixture");
        renderer.renderBpr(candles, fvgs, zones, scenario.after());
        List<Map<String, Object>> items = new ArrayList<>();
        for (BprZone z : zones) {
            items.add(IndicatorArtifacts.map("formedIndex", z.getFormedIndex(), "time", z.getTime(), "bottom", z.getBottom(), "top", z.getTop()));
        }
        finish(scenario, IndicatorArtifacts.map("indicator", "BPR", "referenceFvgCount", fvgs.size(), "bprCount", zones.size(), "zones", items),
                "Reference FVGs: " + fvgs.size(),
                "BPR zones: " + zones.size(),
                "Visual check: every magenta BPR must be the overlap of opposite-direction FVGs.");
    }

    @Test
    void breakerRealMarketScenario() throws IOException {
        List<Candle> candles = load(REAL_BTC);
        IndicatorArtifacts.Scenario scenario = start("breaker", "btc-real", REAL_BTC, candles, "Breaker");
        MarketBias bias = new MarketStructureUtils().marketBias(candles);
        BreakerBlock breaker = new BreakerUtils().detectBreakerBlock(candles, bias);
        assertNotNull(breaker, "No breaker was found on the real-market fixture");
        renderer.renderBreaker(candles, breaker, scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "Breaker", "bias", bias,
                        "breaker", IndicatorArtifacts.map("originOrderBlockIndex", breaker.getOriginOrderBlockIndex(),
                                "invalidationIndex", breaker.getInvalidationIndex(), "bottom", breaker.getBottom(), "top", breaker.getTop())),
                "Bias: " + bias,
                "Origin OB index: " + breaker.getOriginOrderBlockIndex(),
                "Invalidation index: " + breaker.getInvalidationIndex(),
                "Breaker: " + breaker.getBottom() + " .. " + breaker.getTop(),
                "Visual check: the original opposite OB must be invalidated before it becomes a breaker.");
    }

    private void referenceFvgScenario(String scenarioName, String dataset, ActionType expectedType, double expectedLow, double expectedHigh) throws IOException {
        List<Candle> candles = load(dataset);
        IndicatorArtifacts.Scenario scenario = start("fvg-reference", scenarioName, dataset, candles, "Reference FVG");
        List<FVG> fvgs = new FVGUtils().findReferenceFVG(candles);
        assertFalse(fvgs.isEmpty());
        FVG first = fvgs.get(0);
        assertEquals(expectedType, first.getType());
        assertEquals(expectedLow, first.getLow(), 1e-9);
        assertEquals(expectedHigh, first.getHigh(), 1e-9);
        renderer.renderFvg(candles, fvgs, "Reference FVG", scenario.after());
        finish(scenario, IndicatorArtifacts.map("indicator", "FVG_REFERENCE", "count", fvgs.size(), "fvgs", fvgMaps(candles, fvgs)),
                "Reference FVG count: " + fvgs.size(),
                "Expected first FVG: " + expectedType + " " + expectedLow + " .. " + expectedHigh,
                "Definition: candle[2].low > candle[0].high for LONG; candle[2].high < candle[0].low for SHORT.");
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

    private static List<Candle> load(String dataset) throws IOException {
        return CandleCsvLoader.load(dataset);
    }

    private static void assertImage(java.nio.file.Path file) throws IOException {
        assertTrue(Files.exists(file), "Image was not created: " + file);
        assertTrue(Files.size(file) > 0, "Image is empty: " + file);
    }

    private static List<Map<String, Object>> fvgMaps(List<Candle> candles, List<FVG> fvgs) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FVG fvg : fvgs) items.add(fvgMap(candles, fvg));
        return items;
    }

    private static Map<String, Object> fvgMap(List<Candle> candles, FVG fvg) {
        return IndicatorArtifacts.map("index", candles.indexOf(fvg.getCandle()), "time", fvg.getCandle().getTime(), "type", fvg.getType(),
                "low", fvg.getLow(), "high", fvg.getHigh(), "tested", fvg.isTested(), "fulfilled", fvg.isFulfilled());
    }

    private static Map<String, Object> orderBlockMap(OrderBlock ob) {
        return IndicatorArtifacts.map("type", ob.getType(), "orderBlockIndex", ob.getOrderBlockIndex(), "bosIndex", ob.getBosIndex(),
                "levelIndex", ob.getLevelIndex(), "bottom", ob.getBottom(), "top", ob.getTop(),
                "legLow", ob.getLegLow(), "legHigh", ob.getLegHigh());
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
