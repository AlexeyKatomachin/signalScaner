package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.KeyFractalType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds context from structural pairs:
 * bullish = HH + HL, bearish = LH + LL.
 * A context changes only after both sides of the new structure are confirmed.
 */
public class ContextUtils {

    public List<ContextFlow> getContextFlow(List<Candle> candles) {
        List<KeyFractal> keys = candles.stream()
                .map(Candle::getFractal)
                .filter(Objects::nonNull)
                .map(Fractal::getKeyFractal)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(k -> k.getFractal().getCandle().getDateTime()))
                .collect(Collectors.toList());

        List<ContextChange> changes = new ArrayList<>();
        KeyFractal lastHigh = null;
        KeyFractal lastLow = null;
        ActionType current = null;

        for (KeyFractal key : keys) {
            if (key.getFractal().isHigh()) lastHigh = key;
            if (key.getFractal().isLow()) lastLow = key;
            if (lastHigh == null || lastLow == null) continue;

            ActionType detected = detectContext(lastHigh, lastLow);
            if (detected == null || detected == current) continue;

            int highIndex = candles.indexOf(lastHigh.getFractal().getCandle());
            int lowIndex = candles.indexOf(lastLow.getFractal().getCandle());
            int startIndex = Math.max(0, Math.min(highIndex, lowIndex));
            changes.add(new ContextChange(startIndex, detected));
            current = detected;
        }

        List<ContextFlow> result = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            ContextChange change = changes.get(i);
            int from = change.startIndex;
            int toExclusive = i + 1 < changes.size() ? Math.max(from + 1, changes.get(i + 1).startIndex) : candles.size();
            if (from >= candles.size() || toExclusive <= from) continue;

            List<Candle> contextCandles = new ArrayList<>(candles.subList(from, Math.min(candles.size(), toExclusive)));
            List<KeyFractal> contextKeys = contextCandles.stream()
                    .map(Candle::getFractal)
                    .filter(Objects::nonNull)
                    .map(Fractal::getKeyFractal)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (contextKeys.size() < 2) continue;

            ContextFlow flow = new ContextFlow(contextKeys, contextCandles, change.type);
            flow.setHasBOS(contextKeys.stream().anyMatch(KeyFractal::isHasBos));
            result.add(flow);
        }
        return result;
    }

    private static ActionType detectContext(KeyFractal high, KeyFractal low) {
        if (high.getType() == KeyFractalType.HH && low.getType() == KeyFractalType.HL) return ActionType.LONG;
        if (high.getType() == KeyFractalType.LH && low.getType() == KeyFractalType.LL) return ActionType.SHORT;
        return null;
    }

    private static final class ContextChange {
        private final int startIndex;
        private final ActionType type;

        private ContextChange(int startIndex, ActionType type) {
            this.startIndex = startIndex;
            this.type = type;
        }
    }
}
