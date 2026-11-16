package com.lavaksoft.bot.trade.model;

/**
 * Directional market-structure bias used by the reference ICT/SMC strategies.
 */
public enum MarketBias {
    BULLISH,
    BEARISH,
    NEUTRAL;

    public ActionType toActionType() {
        if (this == BULLISH) return ActionType.LONG;
        if (this == BEARISH) return ActionType.SHORT;
        return null;
    }
}
