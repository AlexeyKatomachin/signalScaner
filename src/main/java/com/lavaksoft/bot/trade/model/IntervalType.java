package com.lavaksoft.bot.trade.model;

import static com.lavaksoft.bot.trade.model.IntervalType.Type.HTF;
import static com.lavaksoft.bot.trade.model.IntervalType.Type.LTF;
import static com.lavaksoft.bot.trade.model.IntervalType.Type.MTF;

public enum IntervalType {
    W1("1w", HTF),
    D1("1d", HTF),
    H4("4h", MTF),
    H1("1h", MTF),
    m15("15m", LTF),
    m5("5m", LTF);

    private final String name;
    private final Type type;

    IntervalType(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public enum Type{
        HTF,
        MTF,
        LTF
    }
}
