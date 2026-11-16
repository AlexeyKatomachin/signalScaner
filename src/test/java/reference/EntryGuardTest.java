package reference;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.TradeSignal;
import com.lavaksoft.bot.trade.mt5.EntryGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryGuardTest {
    @Test
    void acceptsPriceCloseToPlannedEntry() {
        TradeSignal signal = new TradeSignal("ote", "x", ActionType.LONG, MarketBias.BULLISH, 100, 90, 110, 120, 135, 0);
        EntryGuard.Verdict verdict = new EntryGuard().check(signal, 102, 0);
        assertTrue(verdict.isOk());
        assertEquals(0.2, verdict.getGapR(), 1e-9);
    }

    @Test
    void blocksPriceThatAlreadyReachedTp1() {
        TradeSignal signal = new TradeSignal("ote", "x", ActionType.LONG, MarketBias.BULLISH, 100, 90, 110, 120, 135, 0);
        EntryGuard.Verdict verdict = new EntryGuard().check(signal, 109, 0);
        assertFalse(verdict.isOk());
        assertEquals("tp1_passed", verdict.getCode());
    }
}
