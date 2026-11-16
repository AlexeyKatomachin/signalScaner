package visual;

import com.lavaksoft.bot.trade.model.Candle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CandleCsvLoader {

    private CandleCsvLoader() {
    }

    static List<Candle> load(String resourcePath) throws IOException {
        InputStream stream = CandleCsvLoader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalArgumentException("Test resource not found: " + resourcePath);
        }

        List<Candle> candles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",");
                candles.add(new Candle(
                        parseLong(parts[0]),
                        parseDouble(parts[1]),
                        parseDouble(parts[2]),
                        parseDouble(parts[3]),
                        parseDouble(parts[4]),
                        parts.length > 5 ? parseDouble(parts[5]) : 0.0
                ));
            }
        }

        candles.sort(Comparator.comparingLong(Candle::getTime));
        return candles;
    }

    private static long parseLong(String value) {
        return Long.parseLong(clean(value));
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(clean(value));
    }

    private static String clean(String value) {
        return value.trim().replace("\"", "");
    }
}
