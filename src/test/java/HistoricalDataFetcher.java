import com.lavaksoft.bot.trade.data.BinanceDataProvider;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.IntervalType;
import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class HistoricalDataFetcher {

    private static final String API_KEY = System.getenv("BINANCE_API_KEY");
    private static final String SECRET_KEY = System.getenv("BINANCE_SECRET_KEY");

    public static void main(String[] args) throws IOException {
        if (API_KEY == null || SECRET_KEY == null) {
            System.err.println("Установите переменные окружения BINANCE_API_KEY и BINANCE_SECRET_KEY");
            System.exit(1);
        }

        String symbol = "BTCUSDT";
        IntervalType interval = IntervalType.m15;   // дневные свечи
        int limit = 400;                           // нужно ровно 200 свечей

        BinanceDataProvider provider = new BinanceDataProvider(API_KEY, SECRET_KEY);
        List<Candle> candles = provider.getKlines(symbol, interval.getName(), limit);

        if (candles.size() < limit) {
            System.err.println("Получено только " + candles.size() + " свечей. Возможно, недостаточно истории.");
        }

        // Сохраняем в CSV
        String fileName = symbol + "_" + interval.getName() + "_" + limit + ".csv";
        saveToCsv(candles, fileName);

        System.out.println("Сохранено " + candles.size() + " свечей в файл: " + fileName);
    }

    private static void saveToCsv(List<Candle> candles, String fileName) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(Paths.get(fileName).toFile()))) {
            // Заголовок
            writer.writeNext(new String[]{"timestamp", "open", "high", "low", "close", "volume"});

            for (Candle c : candles) {
                writer.writeNext(new String[]{
                        String.valueOf(c.getTime()),
                        String.valueOf(c.getOpen()),
                        String.valueOf(c.getHigh()),
                        String.valueOf(c.getLow()),
                        String.valueOf(c.getClose()),
                        String.valueOf(c.getVolume())
                });
            }
        }
    }
}