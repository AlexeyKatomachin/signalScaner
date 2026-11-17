package reference;

import com.lavaksoft.bot.trade.indicators.LiquidityUtils;
import com.lavaksoft.bot.trade.indicators.MarketStructureUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.StructureBreak;
import com.lavaksoft.bot.trade.model.StructureBreakType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureIndicatorLogicTest {

    @Test
    void structureBreakIsCreatedOnTheActualCloseThroughConfirmedPivot() {
        List<Candle> candles = Arrays.asList(
                c(0, 100, 101, 99, 100),
                c(1, 100, 103, 100, 102),
                c(2, 102, 106, 101, 105), // confirmed swing high after bars 3 and 4
                c(3, 105, 104, 100, 101),
                c(4, 101, 103, 98, 102),
                c(5, 102, 107, 101, 105), // wick above 106, close below: NOT a break
                c(6, 105, 108, 104, 107)  // close through 106: break
        );

        List<StructureBreak> breaks = new MarketStructureUtils().findStructureBreaks(candles, 2, 2);
        StructureBreak bullish = breaks.stream().filter(b -> b.getDirection() == ActionType.LONG).findFirst().orElseThrow();
        assertEquals(2, bullish.getReferenceIndex());
        assertEquals(6, bullish.getBreakIndex());
        assertEquals(106.0, bullish.getLevel(), 1e-9);
        assertEquals(StructureBreakType.BOS, bullish.getType());
    }

    @Test
    void equalHighToleranceIsPriceBasedNotDistanceBased() {
        List<Candle> candles = Arrays.asList(
                c(0, 100, 101.0, 99, 100),
                c(1, 100, 102.0, 99, 101),
                c(2, 101, 105.00, 100, 103),
                c(3, 103, 104.0, 101, 102),
                c(4, 102, 103.5, 100, 101),
                c(5, 101, 105.08, 100, 103), // close enough to index 2
                c(6, 103, 104.0, 101, 102),
                c(7, 102, 110.0, 100, 108)  // clearly not equal
        );
        LiquidityUtils liquidity = new LiquidityUtils();
        assertTrue(liquidity.areEqualHighs(candles, 2, 5));
        assertFalse(liquidity.areEqualHighs(candles, 2, 7));
    }

    private static Candle c(long i, double open, double high, double low, double close) {
        return new Candle(i * 60_000L, open, high, low, close, 1);
    }
}
