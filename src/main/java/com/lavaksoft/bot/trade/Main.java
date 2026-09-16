package com.lavaksoft.bot.trade;

import com.lavaksoft.bot.trade.backtest.BacktestEngine;
import com.lavaksoft.bot.trade.data.BinanceDataProvider;
import com.lavaksoft.bot.trade.data.CachedMarketDataProvider;
import com.lavaksoft.bot.trade.data.MarketDataProvider;
import com.lavaksoft.bot.trade.notifier.SignalHistory;
import com.lavaksoft.bot.trade.notifier.TelegramNotifier;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Main {
    private static final List<String> TRADE_PARE = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT");
    private static final int THREAD_POOL_SIZE = 4;

    public static void main(String[] args) {
        String apiKey = System.getenv("BINANCE_API_KEY");
        String secretKey = System.getenv("BINANCE_SECRET_KEY");
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        String chatId = System.getenv("TELEGRAM_CHAT_ID");

        MarketDataProvider dataProvider = new CachedMarketDataProvider(
                new BinanceDataProvider(apiKey, secretKey)
        );
        TelegramNotifier notifier = new TelegramNotifier(botToken, chatId);
        SignalHistory signalHistory = new SignalHistory();

        // Парсинг аргументов командной строки
        LocalDate backtestStart = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--backtest") && i + 1 < args.length) {
                backtestStart = LocalDate.parse(args[i + 1], DateTimeFormatter.ISO_LOCAL_DATE);
                break;
            }
        }

        if (backtest(dataProvider, notifier, signalHistory, backtestStart)) return;

//        runRealTime(dataProvider, notifier, signalHistory);
    }

    private static boolean backtest(MarketDataProvider dataProvider, TelegramNotifier notifier, SignalHistory signalHistory, LocalDate backtestStart) {
        if (backtestStart != null) {
            String text = "🚀 Запуск бэктеста с " + backtestStart;
            System.out.println(text);
            notifier.sendTextOnly(text);
            BacktestEngine engine = new BacktestEngine(dataProvider,
                    backtestStart, TRADE_PARE.get(0), notifier, signalHistory);
            engine.run();
            text = "✅ Бэктест завершён.";
            System.out.println(text);
            notifier.sendTextOnly(text);
            return true;
        }
        return false;
    }

//    private static void runRealTime(MarketDataProvider dataProvider, TelegramNotifier notifier, SignalHistory signalHistory) {
//        List<StrategyOld> strategies = List.of(
//                new LiquiditySweepStrategyOld(true),
//                new LiquiditySweepStrategyOld(false),
//                new FvgStrategyOld("4h"),
//                new FvgStrategyOld("1d")
//        );
//
//        // Режим реального времени
//        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
//        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//
//        scheduler.scheduleAtFixedRate(() -> {
//            List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
//            for (String symbol : TRADE_PARE) {
//                for (StrategyOld strategyOld : strategies) {
//                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                        try {
//                            String htfInterval = strategyOld.getHtfInterval();
//                            String ltfInterval = strategyOld.getLtfInterval();
//                            List<Candle> htfData = dataProvider.getKlines(symbol, htfInterval, 200);
//                            List<Candle> ltfData = dataProvider.getKlines(symbol, ltfInterval, 100);
//
//                            if (htfData.isEmpty() || ltfData.isEmpty()) return;
//
//                            SignalDeprecated signalDeprecated = strategyOld.evaluate(symbol, htfData, ltfData);
//                            if (signalDeprecated != null) {
//                                String strategyName = strategyOld.getClass().getSimpleName();
//                                if (signalHistory.canSend(signalDeprecated.getSymbol(), signalDeprecated.getType(), strategyName)) {
//                                    List<FVGZone> allZones = FVGUtils.findAllFVG(htfData);
//                                    byte[] htfScr = ScreenshotGenerator.generateHtfChart(
//                                            htfData,
//                                            "HTF " + htfInterval,
//                                            50,
//                                            18,
//                                            allZones,
//                                            signalDeprecated.getFvgZone()
//                                    );
//                                    Double bosLevel = signalDeprecated.getPrice(); // или точный уровень из стратегии
//                                    boolean bosUpward = signalDeprecated.getType() == SignalType.BUY;
//                                    byte[] ltfScr = ScreenshotGenerator.generateLtfChart(
//                                            ltfData,
//                                            "LTF " + ltfInterval,
//                                            20,
//                                            18,
//                                            bosLevel,
//                                            bosUpward
//                                    );
//                                    notifier.sendSignal(signalDeprecated, htfScr, new byte[0], ltfScr, System.currentTimeMillis());
//                                }
//                            }
//                        } catch (Exception e) {
//                            System.err.println("Error processing " + symbol + ": " + e.getMessage());
//                            e.printStackTrace();
//                        }
//                    }, executor);
//                    futures.add(future);
//                }
//            }
//            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//        }, 0, 5, TimeUnit.MINUTES);
//
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            executor.shutdown();
//            scheduler.shutdown();
//        }));
//    }
}