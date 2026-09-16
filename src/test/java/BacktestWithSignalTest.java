import com.lavaksoft.bot.trade.backtest.BacktestEngine;
import com.lavaksoft.bot.trade.data.MarketDataProvider;
import com.lavaksoft.bot.trade.indicators.FractalUtils;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.IntervalType;
import com.lavaksoft.bot.trade.notifier.ScreenshotGenerator;
import com.lavaksoft.bot.trade.notifier.SignalHistory;
import com.lavaksoft.bot.trade.notifier.TelegramNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Интеграционный тест: загружаем свечи из CSV, прогоняем бэктест,
 * проверяем, что был сгенерирован сигнал (вызов TelegramNotifier.sendNewSignal).
 */
public class BacktestWithSignalTest {

    private TelegramNotifier notifierMock;
    private SignalHistory signalHistory;
    private MarketDataProvider testProvider;

    private List<Candle> m15Candles;
    private List<Candle> h4Candles;
    private List<Candle> d1Candles;

    @BeforeEach
    void setUp() throws IOException {
        // Загружаем m15 свечи из CSV (путь к файлу можно передать через системное свойство)
        System.setProperty("candles.file", "C:\\Users\\user\\IdeaProjects\\freeTime\\bot\\v1\\untitled\\");
        String filePath = System.getProperty("candles.file");
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalStateException("Укажите файл свечей: -Dcandles.file=/path/to/candles_15m.csv");
        }
        m15Candles = loadCandlesFromCSV("BTCUSDT_15m_400.csv");
        // Агрегируем в H4 и D1
        h4Candles = loadCandlesFromCSV("BTCUSDT_4h_200.csv");
        d1Candles = loadCandlesFromCSV("BTCUSDT_1d_200.csv");

        // Фейковый провайдер данных
        testProvider = new TestMarketDataProvider(m15Candles, h4Candles, d1Candles);

        // Мокируем TelegramNotifier, чтобы перехватить вызов отправки сигнала
        notifierMock = mock(TelegramNotifier.class);
        signalHistory = new SignalHistory();
    }

    @Test
    void testFractals () throws IOException {
        FractalUtils fractalUtils = new FractalUtils();

        fractalUtils.findFractals(m15Candles);

        ScreenshotGenerator screenshotGenerator = new ScreenshotGenerator();
        byte[] bytes = screenshotGenerator.generateChartForTest(m15Candles, IntervalType.m15);

        Path destinationFile = Paths.get("output_image.jpg");

        try {
            Files.write(destinationFile, bytes);
            System.out.println("Изображение успешно сохранено!");
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении файла: " + e.getMessage());
        }
    }

    @Test
    void backtestShouldGenerateAtLeastOneSignal() {
        // Берём первые 30 дней для ускорения (можно убрать)
        LocalDate startDate = m15Candles.get(200).getDateTime().toLocalDate();
        LocalDate endDate = startDate.plusDays(30);
        List<Candle> limitedM15 = m15Candles.stream()
                .filter(c -> c.getDateTime().toLocalDate().isBefore(endDate))
                .collect(Collectors.toList());
        testProvider = new TestMarketDataProvider(limitedM15, h4Candles, d1Candles);

        // Запускаем бэктест
        BacktestEngine engine = new BacktestEngine(testProvider, startDate,
                "BTCUSDT", notifierMock, signalHistory);
        engine.run();

        // Проверяем, что хотя бы один сигнал был отправлен
        ArgumentCaptor<byte[]> htfCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> midCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> ltfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(notifierMock, atLeastOnce())
                .sendNewSignal(any(), htfCaptor.capture(), midCaptor.capture(), ltfCaptor.capture(), anyLong());

        assertTrue(htfCaptor.getAllValues().size() > 0,
                "Бэктест не сгенерировал ни одного сигнала");
        System.out.println("✅ Успешно: сгенерировано " + htfCaptor.getAllValues().size() + " сигналов");
    }

    // ---------- вспомогательные методы (агрегация, загрузка CSV) ----------
    private List<Candle> loadCandlesFromCSV(String path) throws IOException {
        List<Candle> candles = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(path));
        boolean firstLine = true;
        for (String line : lines) {
            if (firstLine) { firstLine = false; continue; }
            if (line.isBlank()) continue;
            String[] parts = line.split(",");
            long time = Long.parseLong(parts[0].trim().replace("\"", ""));
            double open = Double.parseDouble(parts[1].trim().replace("\"", ""));
            double high = Double.parseDouble(parts[2].trim().replace("\"", ""));
            double low = Double.parseDouble(parts[3].trim().replace("\"", ""));
            double close = Double.parseDouble(parts[4].trim().replace("\"", ""));
            double volume = parts.length > 5 ? Double.parseDouble(parts[5].trim().replace("\"", "")) : 0.0;
            candles.add(new Candle(time, open, high, low, close, volume));
        }
        candles.sort(Comparator.comparingLong(Candle::getTime));
        return candles;
    }

    private List<Candle> aggregateCandles(List<Candle> source, int minutesTarget) {
        if (source.isEmpty()) return Collections.emptyList();
        List<Candle> result = new ArrayList<>();
        int targetMillis = minutesTarget * 60 * 1000;
        long firstTime = source.get(0).getTime();
        long alignedStart = (firstTime / targetMillis) * targetMillis;
        int i = 0;
        while (i < source.size()) {
            long bucketStart = alignedStart;
            long bucketEnd = bucketStart + targetMillis;
            double open = 0, high = Double.MIN_VALUE, low = Double.MAX_VALUE, close = 0;
            double volumeSum = 0;
            boolean firstInBucket = true;
            while (i < source.size() && source.get(i).getTime() < bucketEnd) {
                Candle c = source.get(i);
                if (firstInBucket) {
                    open = c.getOpen();
                    firstInBucket = false;
                }
                high = Math.max(high, c.getHigh());
                low = Math.min(low, c.getLow());
                close = c.getClose();
                volumeSum += c.getVolume();
                i++;
            }
            if (!firstInBucket) {
                result.add(new Candle(bucketStart, open, high, low, close, volumeSum));
            }
            alignedStart = bucketEnd;
        }
        return result;
    }

    private static class TestMarketDataProvider implements MarketDataProvider {
        private final Map<String, List<Candle>> storage = new HashMap<>();
        public TestMarketDataProvider(List<Candle> m15, List<Candle> h4, List<Candle> d1) {
            storage.put(IntervalType.m15.getName(), new ArrayList<>(m15));
            storage.put(IntervalType.H4.getName(), new ArrayList<>(h4));
            storage.put(IntervalType.D1.getName(), new ArrayList<>(d1));
        }
        @Override
        public List<Candle> getKlines(String symbol, String interval, int limit) {
            List<Candle> all = storage.get(interval);
            if (all == null) return Collections.emptyList();
            if (limit <= 0 || limit >= all.size()) return new ArrayList<>(all);
            return new ArrayList<>(all.subList(all.size() - limit, all.size()));
        }
        @Override
        public List<Candle> getKlines(String symbol, String interval, long startTime, long endTime) {
            List<Candle> all = storage.get(interval);
            if (all == null) return Collections.emptyList();
            return all.stream().filter(c -> c.getTime() >= startTime && c.getTime() <= endTime).collect(Collectors.toList());
        }
        @Override
        public List<Candle> getKlinesClosed(String symbol, String interval, long startTime, long endTime) {
            List<Candle> klines = getKlines(symbol, interval, startTime, endTime);
            if (klines.size() < 2) return Collections.emptyList();
            return klines.subList(0, klines.size() - 2);
        }
    }
}