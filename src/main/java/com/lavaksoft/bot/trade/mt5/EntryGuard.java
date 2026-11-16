package com.lavaksoft.bot.trade.mt5;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.TradeSignal;

/**
 * Prevents stale market execution when price moved too far from the calculated signal entry.
 */
public class EntryGuard {
    private final double maxGapR;
    private final double minGapR;
    private final double minTp1LeftR;

    public EntryGuard() {
        this(0.5, -0.5, 0.2);
    }

    public EntryGuard(double maxGapR, double minGapR, double minTp1LeftR) {
        this.maxGapR = maxGapR > 0 ? maxGapR : 0.5;
        this.minGapR = minGapR < 0 ? minGapR : -0.5;
        this.minTp1LeftR = Math.max(0, minTp1LeftR);
    }

    public Verdict check(TradeSignal signal, double bidPrice, double spread) {
        if (signal == null || !Double.isFinite(bidPrice) || bidPrice <= 0)
            return Verdict.block("no_price", "Market price unavailable");
        int sign = signal.getDirection() == ActionType.LONG ? 1 : -1;
        double risk = Math.abs(signal.getEntry() - signal.getStop());
        if (risk <= 0 || (signal.getEntry() - signal.getStop()) * sign <= 0 || (signal.getTp1() - signal.getEntry()) * sign <= 0) {
            return Verdict.block("bad_input", "Signal levels are inconsistent");
        }
        double effectivePrice = signal.getDirection() == ActionType.LONG ? bidPrice + Math.max(0, spread) : bidPrice;
        double gapR = (effectivePrice - signal.getEntry()) * sign / risk;
        double tp1LeftR = (signal.getTp1() - effectivePrice) * sign / risk;
        double stopLeftR = (effectivePrice - signal.getStop()) * sign / risk;
        if (stopLeftR <= 0)
            return Verdict.block("beyond_stop", "Price is already beyond the planned stop", gapR, tp1LeftR, stopLeftR, effectivePrice);
        if (tp1LeftR <= minTp1LeftR)
            return Verdict.block("tp1_passed", "Too little distance remains to TP1", gapR, tp1LeftR, stopLeftR, effectivePrice);
        if (gapR > maxGapR)
            return Verdict.block("stale_gap", "Price moved too far in trade direction", gapR, tp1LeftR, stopLeftR, effectivePrice);
        if (gapR < minGapR)
            return Verdict.block("against_stop", "Price moved too far toward the stop", gapR, tp1LeftR, stopLeftR, effectivePrice);
        return Verdict.allow(gapR, tp1LeftR, stopLeftR, effectivePrice);
    }

    public static class Verdict {
        private final boolean ok;
        private final String code;
        private final String reason;
        private final Double gapR;
        private final Double tp1LeftR;
        private final Double stopLeftR;
        private final Double effectivePrice;

        private Verdict(boolean ok, String code, String reason, Double gapR, Double tp1LeftR, Double stopLeftR, Double effectivePrice) {
            this.ok = ok;
            this.code = code;
            this.reason = reason;
            this.gapR = gapR;
            this.tp1LeftR = tp1LeftR;
            this.stopLeftR = stopLeftR;
            this.effectivePrice = effectivePrice;
        }

        static Verdict allow(double gapR, double tp1LeftR, double stopLeftR, double price) {
            return new Verdict(true, "ok", "Entry is within configured R limits", gapR, tp1LeftR, stopLeftR, price);
        }

        static Verdict block(String code, String reason) {
            return new Verdict(false, code, reason, null, null, null, null);
        }

        static Verdict block(String code, String reason, double gapR, double tp1LeftR, double stopLeftR, double price) {
            return new Verdict(false, code, reason, gapR, tp1LeftR, stopLeftR, price);
        }

        public boolean isOk() {
            return ok;
        }

        public String getCode() {
            return code;
        }

        public String getReason() {
            return reason;
        }

        public Double getGapR() {
            return gapR;
        }

        public Double getTp1LeftR() {
            return tp1LeftR;
        }

        public Double getStopLeftR() {
            return stopLeftR;
        }

        public Double getEffectivePrice() {
            return effectivePrice;
        }
    }
}
