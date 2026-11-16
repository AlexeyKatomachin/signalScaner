package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.KeyFractalType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContextUtils {

    public List<ContextFlow> getContextFlow(List<Candle> candles) {
        List<ContextFlow> context = new ArrayList<>();
        KeyFractal previous = null;
        KeyFractal prePrevious = null;
        int previousContextEndIndex = 0;

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            if (candle.getFractal() != null && candle.getFractal().getKeyFractal() != null) {
                KeyFractal keyFractal = candle.getFractal().getKeyFractal();
                if (previous == null) {
                    prePrevious = previous;
                    previous = keyFractal;
                    continue;
                } else {
                    prePrevious = previous;
                    previous = keyFractal;
                }
                if (keyFractal.getType().equals(KeyFractalType.HH) && prePrevious.getType().equals(KeyFractalType.LL)) {
                    if (context.isEmpty()) {
                        List<Candle> contextCandles = new ArrayList<>(candles.subList(0, i));
                        context.add(new ContextFlow(contextCandles.stream()
                                .filter(x -> x.getFractal() != null && x.getFractal().getKeyFractal() != null)
                                .map(x -> x.getFractal().getKeyFractal()).collect(Collectors.toList()), contextCandles, ActionType.SHORT));
                        previousContextEndIndex = i;
                    } else {
                        List<Candle> contextCandles = new ArrayList<>(candles.subList(previousContextEndIndex, i));
                        context.get(context.size() - 1).setHasBOS(prePrevious.isHasBos());
                        context.add(new ContextFlow(contextCandles.stream()
                                .filter(x -> x.getFractal() != null && x.getFractal().getKeyFractal() != null)
                                .map(x -> x.getFractal().getKeyFractal()).collect(Collectors.toList()), contextCandles, ActionType.SHORT));
                    }
                }
                if (keyFractal.getType().equals(KeyFractalType.LL) && prePrevious.getType().equals(KeyFractalType.HH)) {
                    if (context.isEmpty()) {
                        List<Candle> contextCandles = new ArrayList<>(candles.subList(0, i));
                        context.add(new ContextFlow(contextCandles.stream()
                                .filter(x -> x.getFractal() != null && x.getFractal().getKeyFractal() != null)
                                .map(x -> x.getFractal().getKeyFractal()).collect(Collectors.toList()), contextCandles, ActionType.LONG));
                        previousContextEndIndex = i;
                    } else {
                        List<Candle> contextCandles = new ArrayList<>(candles.subList(previousContextEndIndex, i));
                        context.get(context.size() - 1).setHasBOS(prePrevious.isHasBos());
                        context.add(new ContextFlow(contextCandles.stream()
                                .filter(x -> x.getFractal() != null && x.getFractal().getKeyFractal() != null)
                                .map(x -> x.getFractal().getKeyFractal()).collect(Collectors.toList()), contextCandles, ActionType.LONG));
                    }
                }
                if (keyFractal.isHasBos() && context.size() > 0) {
                    context.get(context.size() - 1).setHasBOS(keyFractal.isHasBos());
                }
            }
        }

        return context;
    }
}
