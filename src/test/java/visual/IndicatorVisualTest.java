package visual;

import com.lavaksoft.bot.trade.indicators.ContextUtils;
import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.indicators.FractalUtils;
import com.lavaksoft.bot.trade.indicators.LiquidityUtils;
import com.lavaksoft.bot.trade.indicators.POIUtils;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.Liquidity;
import com.lavaksoft.bot.trade.model.POI;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorVisualTest {

    private static final String DATASET = "/market-data/BTCUSDT_15m_400.csv";
    private static final Path OUTPUT = Path.of("target", "visual-tests");
    private final IndicatorChartRenderer renderer = new IndicatorChartRenderer();

    @Test
    void generateFractalsImage() throws IOException {
        List<Candle> candles = freshCandles();
        List<Fractal> fractals = new FractalUtils().findFractals(candles);

        assertFalse(fractals.isEmpty(), "Fractal detector returned no results");
        Path file = OUTPUT.resolve("01-fractals.png");
        renderer.renderFractals(candles, file);
        assertImage(file);
    }

    @Test
    void generateKeyFractalsImage() throws IOException {
        List<Candle> candles = freshCandles();
        FractalUtils fractalUtils = new FractalUtils();
        fractalUtils.findFractals(candles);
        List<KeyFractal> keyFractals = fractalUtils.findKeyFractals(candles);

        assertFalse(keyFractals.isEmpty(), "Key-fractal detector returned no results");
        Path file = OUTPUT.resolve("02-key-fractals.png");
        renderer.renderKeyFractals(candles, file);
        assertImage(file);
    }

    @Test
    void generateFvgImage() throws IOException {
        List<Candle> candles = freshCandles();
        new FVGUtils().findFVG(candles);
        List<FVG> allDetected = candles.stream()
                .map(Candle::getFvg)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        assertFalse(allDetected.isEmpty(), "FVG detector returned no results");
        Path file = OUTPUT.resolve("03-fvg.png");
        renderer.renderFvg(candles, allDetected, file);
        assertImage(file);
    }

    @Test
    void generateLiquidityImage() throws IOException {
        AnalysisPipeline result = analyze();

        assertFalse(result.contexts.isEmpty(), "Context detector returned no results");
        assertFalse(result.liquidities.isEmpty(), "Liquidity detector returned no results");
        Path file = OUTPUT.resolve("04-liquidity.png");
        renderer.renderLiquidity(result.candles, result.liquidities, file);
        assertImage(file);
    }

    @Test
    void generatePoiImage() throws IOException {
        AnalysisPipeline result = analyze();

        assertFalse(result.pois.isEmpty(), "POI detector returned no results");
        Path file = OUTPUT.resolve("05-poi.png");
        renderer.renderPoi(result.candles, result.pois, file);
        assertImage(file);
    }

    @Test
    void generateBosImage() throws IOException {
        AnalysisPipeline result = analyze();
        List<KeyFractal> keyFractals = result.candles.stream()
                .map(Candle::getFractal)
                .filter(Objects::nonNull)
                .map(Fractal::getKeyFractal)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        assertTrue(keyFractals.stream().anyMatch(KeyFractal::isHasBos),
                "No key fractal is marked with BOS on this dataset");
        Path file = OUTPUT.resolve("06-bos.png");
        renderer.renderBos(result.candles, keyFractals, file);
        assertImage(file);
    }

    private AnalysisPipeline analyze() throws IOException {
        List<Candle> candles = freshCandles();
        FractalUtils fractalUtils = new FractalUtils();
        fractalUtils.findFractals(candles);
        fractalUtils.findKeyFractals(candles);
        new FVGUtils().findFVG(candles);

        List<ContextFlow> contexts = new ContextUtils().getContextFlow(candles);
        List<Liquidity> liquidities = contexts.isEmpty()
                ? List.of()
                : new LiquidityUtils().findLiquidity(candles, contexts);
        List<POI> pois = new POIUtils().findPOI(candles);
        return new AnalysisPipeline(candles, contexts, liquidities, pois);
    }

    private List<Candle> freshCandles() throws IOException {
        return CandleCsvLoader.load(DATASET);
    }

    private void assertImage(Path file) throws IOException {
        assertTrue(Files.exists(file), "Image was not created: " + file);
        assertTrue(Files.size(file) > 0, "Image is empty: " + file);
    }

    private static final class AnalysisPipeline {
        private final List<Candle> candles;
        private final List<ContextFlow> contexts;
        private final List<Liquidity> liquidities;
        private final List<POI> pois;

        private AnalysisPipeline(List<Candle> candles,
                                 List<ContextFlow> contexts,
                                 List<Liquidity> liquidities,
                                 List<POI> pois) {
            this.candles = candles;
            this.contexts = contexts;
            this.liquidities = liquidities;
            this.pois = pois;
        }
    }
}
