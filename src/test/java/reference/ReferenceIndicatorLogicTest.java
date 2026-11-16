package reference;

import com.lavaksoft.bot.trade.indicators.BPRUtils;
import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.indicators.OTEUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.BprZone;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.OrderBlock;
import com.lavaksoft.bot.trade.model.OteZone;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceIndicatorLogicTest {

    @Test
    void referenceFvgUsesFirstAndThirdCandleWicks() {
        List<Candle> candles = Arrays.asList(
                c(0, 100, 103, 99, 102),
                c(1, 102, 108, 101, 107),
                c(2, 107, 110, 105, 109)
        );
        List<FVG> fvgs = new FVGUtils().findReferenceFVG(candles);
        assertEquals(1, fvgs.size());
        assertEquals(ActionType.LONG, fvgs.get(0).getType());
        assertEquals(103.0, fvgs.get(0).getLow(), 1e-9);
        assertEquals(105.0, fvgs.get(0).getHigh(), 1e-9);
    }

    @Test
    void oteZoneMatchesPythonFormula() {
        OTEUtils utils = new OTEUtils();
        OrderBlock bullish = new OrderBlock(ActionType.LONG, 1, 2, 0, 110, 100, 100, 200);
        OteZone zone = utils.computeOteZone(bullish);
        assertEquals(121.4, zone.getBottom(), 1e-9);
        assertEquals(138.2, zone.getTop(), 1e-9);
    }

    @Test
    void bprIsOverlapOfOppositeFvgs() {
        List<Candle> candles = Arrays.asList(c(0, 1, 2, 0, 1), c(1, 1, 2, 0, 1), c(2, 1, 2, 0, 1), c(3, 1, 2, 0, 1), c(4, 1, 2, 0, 1));
        FVG bullish = new FVG(candles.get(2), 110, 100, ActionType.LONG);
        FVG bearish = new FVG(candles.get(4), 115, 105, ActionType.SHORT);
        List<BprZone> zones = new BPRUtils().findBprZones(candles, Arrays.asList(bullish, bearish), 15);
        assertEquals(1, zones.size());
        assertEquals(105, zones.get(0).getBottom(), 1e-9);
        assertEquals(110, zones.get(0).getTop(), 1e-9);
        assertEquals(4, zones.get(0).getFormedIndex());
    }

    private static Candle c(long i, double o, double h, double l, double cl) {
        return new Candle(i * 60_000L, o, h, l, cl, 1);
    }
}
