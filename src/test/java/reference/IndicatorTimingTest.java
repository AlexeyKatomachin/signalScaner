package reference;

import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.indicators.MarketStructureUtils;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.SwingPoints;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against accidental look-ahead in indicators that require right-side candles.
 */
class IndicatorTimingTest {

    @Test
    void swingNeedsTwoRightSideCandlesBeforeItBecomesVisible() {
        List<Candle> candles = List.of(
                c(0, 100, 101, 99, 100),
                c(1, 100, 103, 100, 102),
                c(2, 102, 106, 101, 105), // candidate swing high
                c(3, 105, 104, 100, 101),
                c(4, 101, 103, 98, 102)
        );
        MarketStructureUtils utils = new MarketStructureUtils();

        SwingPoints tooEarly = utils.findSwingPoints(new ArrayList<>(candles.subList(0, 4)), 2, 2);
        assertFalse(tooEarly.getHighs().contains(2), "Pivot at index 2 cannot be confirmed with only one right-side candle");

        SwingPoints confirmed = utils.findSwingPoints(candles, 2, 2);
        assertTrue(confirmed.getHighs().contains(2), "Pivot at index 2 must become visible after two right-side candles exist");
    }

    @Test
    void referenceFvgCannotExistBeforeThirdCandleArrives() {
        List<Candle> firstTwo = List.of(
                c(0, 100, 103, 99, 102),
                c(1, 102, 108, 101, 107)
        );
        assertTrue(new FVGUtils().findReferenceFVG(firstTwo).isEmpty());

        List<Candle> three = new ArrayList<>(firstTwo);
        three.add(c(2, 107, 110, 105, 109));
        assertFalse(new FVGUtils().findReferenceFVG(three).isEmpty());
    }

    private static Candle c(long i, double open, double high, double low, double close) {
        return new Candle(i * 60_000L, open, high, low, close, 1);
    }
}
