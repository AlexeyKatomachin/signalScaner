package com.lavaksoft.bot.trade.backtest;

import com.lavaksoft.bot.trade.data.MarketDataProvider;
import com.lavaksoft.bot.trade.indicators.BOSUtils;
import com.lavaksoft.bot.trade.indicators.ContextUtils;
import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.indicators.FractalUtils;
import com.lavaksoft.bot.trade.indicators.LiquidityUtils;
import com.lavaksoft.bot.trade.indicators.POIUtils;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.IntervalType;
import com.lavaksoft.bot.trade.notifier.ScreenshotGenerator;
import com.lavaksoft.bot.trade.notifier.SignalHistory;
import com.lavaksoft.bot.trade.notifier.TelegramNotifier;
import com.lavaksoft.bot.trade.strategy.Modern3LevelStrategy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public class BacktestEngine {
    private final MarketDataProvider provider;
    private final LocalDate startDate;
    private final String tradePare;
    private final TelegramNotifier notifier;
    private final SignalHistory signalHistory;
    private final Modern3LevelStrategy strategy;

    public BacktestEngine(MarketDataProvider provider,
                          LocalDate startDate,
                          String tradePare,
                          TelegramNotifier notifier,
                          SignalHistory signalHistory) {
        this.provider = provider;
        this.startDate = startDate;
        this.tradePare = tradePare;
        this.notifier = notifier;
        this.signalHistory = signalHistory;
        this.strategy = new Modern3LevelStrategy(new FractalUtils(), new ContextUtils(), new FVGUtils(), new LiquidityUtils(),
                new POIUtils(), new BOSUtils(), provider, notifier, new ScreenshotGenerator(), tradePare);
    }

    public void run() {
        LocalDateTime timeFrom = LocalDateTime.of(startDate, LocalTime.MIN);
        String message = "\n🔍 Бэктест " + tradePare + " | " + strategy.getClass().getSimpleName() +
                " | HTF=" + IntervalType.D1.getName() +
                " | MID=" + IntervalType.H4.getName() +
                " | LTF=" + IntervalType.m15.getName() + "\n"
                + "From " + timeFrom;
        notifier.sendTextOnly(message);

        List<Candle> candles15m = provider.getKlines(tradePare, IntervalType.m15.getName(), timeFrom.minusDays(2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), timeFrom.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        List<Candle> candles4h = provider.getKlines(tradePare, IntervalType.H4.getName(), timeFrom.minusDays(30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), timeFrom.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        List<Candle> candles1D = provider.getKlines(tradePare, IntervalType.D1.getName(), timeFrom.minusDays(200).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), timeFrom.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());


        strategy.init(null, candles1D, candles4h, null, candles15m, null);
        LocalDateTime now = LocalDateTime.now().minusDays(1);

        for (LocalDateTime delta = timeFrom; delta.compareTo(now.minusDays(1)) < 0; delta = delta.plusMinutes(5)) {
            strategy.iteration(delta);
        }
    }

//    public void runOld() {
//        long startTime = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
//        long endTime = System.currentTimeMillis();
//
//        for (String symbol : tradePare) {
//            for (StrategyOld strategyOld : strategies) {
//                System.out.println("\n🔍 Бэктест " + symbol + " | " + strategyOld.getClass().getSimpleName() +
//                        " | HTF=" + strategyOld.getHtfInterval() +
//                        " | MID=" + strategyOld.getMiddleInterval() +
//                        " | LTF=" + strategyOld.getLtfInterval());
//
//                List<Candle> allHtf = loadAllCandles(symbol, strategyOld.getHtfInterval(), startTime, endTime);
//                List<Candle> allMiddle = loadAllCandles(symbol, strategyOld.getMiddleInterval(), startTime, endTime);
//                List<Candle> allLtf = loadAllCandles(symbol, strategyOld.getLtfInterval(), startTime, endTime);
//
//                if (allHtf.isEmpty() || allMiddle.isEmpty() || allLtf.isEmpty()) {
//                    System.err.println("Нет данных для " + symbol);
//                    continue;
//                }
//
//                // Динамические окна
//                List<Candle> htfWindow = new ArrayList<>();
//                List<Candle> middleWindow = new ArrayList<>();
//                List<Candle> ltfWindow = new ArrayList<>();
//
//                int htfIdx = 0, middleIdx = 0, ltfIdx = 0;
//
//                while (middleIdx < allMiddle.size()) {
//                    Candle currentMiddle = allMiddle.get(middleIdx);
//                    long currentTime = currentMiddle.getTime();
//
//                    // Добавляем HTF свечи до currentTime
//                    while (htfIdx < allHtf.size() && allHtf.get(htfIdx).getTime() <= currentTime) {
//                        htfWindow.add(allHtf.get(htfIdx));
//                        if (htfWindow.size() > 200) htfWindow.remove(0);
//                        htfIdx++;
//                    }
//                    // Добавляем LTF свечи до currentTime
//                    while (ltfIdx < allLtf.size() && allLtf.get(ltfIdx).getTime() <= currentTime) {
//                        ltfWindow.add(allLtf.get(ltfIdx));
//                        if (ltfWindow.size() > 200) ltfWindow.remove(0);
//                        ltfIdx++;
//                    }
//                    // Добавляем среднюю свечу
//                    middleWindow.add(currentMiddle);
//                    if (middleWindow.size() > 100) middleWindow.remove(0);
//
//                    // Анализ
//                    SignalDeprecated signalDeprecated = strategyOld.evaluate(symbol,
//                            new ArrayList<>(htfWindow),
//                            new ArrayList<>(middleWindow),
//                            new ArrayList<>(ltfWindow));
//
//                    if (signalDeprecated != null && signalHistory.canSend(symbol, signalDeprecated.getType(), strategyOld.getClass().getSimpleName())) {
//                        try {
//                            // HTF график со всеми FVG
//                            List<FVGZone> allFvg = FVGUtils.findAllFVG(htfWindow);
//                            byte[] htfScreen = ScreenshotGenerator.generateHtfChart(
//                                    htfWindow, "HTF " + strategyOld.getHtfInterval(),
//                                    50, 18, allFvg, signalDeprecated.getFvgZone());
//
//                            // Middle график (POI) с FVG
//                            List<FVGZone> middleFvg = FVGUtils.findAllFVG(middleWindow);
//                            byte[] middleScreen = ScreenshotGenerator.generateHtfChart(
//                                    middleWindow, "POI " + strategyOld.getMiddleInterval(),
//                                    50, 18, middleFvg, signalDeprecated.getFvgZone());
//
//                            // LTF график с BOS
//                            byte[] ltfScreen = ScreenshotGenerator.generateLtfChart(
//                                    ltfWindow, "LTF " + strategyOld.getLtfInterval(),
//                                    30, 18, signalDeprecated.getBosLevel(), signalDeprecated.isBosUpward());
//
//                            notifier.sendSignal(signalDeprecated, htfScreen, middleScreen, ltfScreen, currentTime);
//                            System.out.println("[SIGNAL SENT] " + signalDeprecated);
//                        } catch (Exception e) {
//                            System.err.println("Ошибка генерации/отправки: " + e.getMessage());
//                            e.printStackTrace();
//                        }
//                    }
//                    middleIdx++;
//                }
//            }
//        }
//    }
}