package com.lavaksoft.bot.trade.notifier;

import com.lavaksoft.bot.trade.model.ActionType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SignalHistory {
    private final ConcurrentHashMap<String, Long> lastSignalTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(4);

    public boolean canSend(String symbol, ActionType type, String strategyName) {
        String key = symbol + ":" + type + ":" + strategyName;
        long now = System.currentTimeMillis();
        lastSignalTime.compute(key, (k, v) -> {
            if (v == null || now - v > COOLDOWN_MILLIS) return now;
            return v;
        });
        return lastSignalTime.get(key) == now;
    }
}
