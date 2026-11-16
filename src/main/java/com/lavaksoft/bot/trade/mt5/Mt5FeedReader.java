package com.lavaksoft.bot.trade.mt5;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavaksoft.bot.trade.model.Candle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the exact feed files produced by SignalScannerBridge.mq5.
 */
public class Mt5FeedReader {
    private static final long MAX_HEARTBEAT_AGE_MS = 60_000L;
    private static final long MAX_FILE_AGE_MS = 120_000L;

    private final Path feedDirectory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mt5FeedReader(Path feedDirectory) {
        this.feedDirectory = feedDirectory;
    }

    public Path getFeedDirectory() {
        return feedDirectory;
    }

    public Mt5Heartbeat heartbeat(String key) {
        Path file = feedDirectory.resolve("feed_" + key + "_heartbeat.json");
        if (!Files.exists(file)) return null;
        try {
            JsonNode node = objectMapper.readTree(file.toFile());
            long utc = node.path("utc").asLong();
            long ageMs = System.currentTimeMillis() - utc * 1000L;
            boolean connected = node.path("connected").asBoolean(false);
            if (!connected || ageMs > MAX_HEARTBEAT_AGE_MS) return null;

            Mt5Heartbeat hb = new Mt5Heartbeat();
            hb.setUtc(utc);
            hb.setServerOffset(node.path("server_offset").asLong());
            hb.setFeedMode(node.path("feed_mode").asText(null));
            hb.setConnected(connected);
            hb.setSymbol(node.path("symbol").asText(null));
            hb.setVersion(node.path("version").asText(null));
            hb.setAgeSeconds(ageMs / 1000.0);
            return hb;
        } catch (Exception e) {
            return null;
        }
    }

    public String problem(String key, String expectedSymbol) {
        Path heartbeatFile = feedDirectory.resolve("feed_" + key + "_heartbeat.json");
        if (!Files.exists(heartbeatFile)) return "MT5 heartbeat is missing";
        try {
            JsonNode node = objectMapper.readTree(heartbeatFile.toFile());
            long ageMs = System.currentTimeMillis() - node.path("utc").asLong() * 1000L;
            if (!node.path("connected").asBoolean(false)) return "MetaTrader is disconnected from broker";
            if (ageMs > MAX_HEARTBEAT_AGE_MS) return "MT5 heartbeat is stale";
            String actualSymbol = node.path("symbol").asText("");
            if (expectedSymbol != null && !expectedSymbol.isBlank() && !actualSymbol.isBlank()
                    && !actualSymbol.equalsIgnoreCase(expectedSymbol)) {
                return "EA symbol mismatch: expected " + expectedSymbol + ", got " + actualSymbol;
            }
            for (String tf : new String[]{"M5", "M15"}) {
                Path feed = feedDirectory.resolve("feed_" + key + "_" + tf + ".csv");
                if (!Files.exists(feed)) return tf + " feed is missing";
                if (System.currentTimeMillis() - Files.getLastModifiedTime(feed).toMillis() > MAX_FILE_AGE_MS) {
                    return tf + " feed is stale";
                }
            }
            return null;
        } catch (Exception e) {
            return "MT5 feed is unreadable: " + e.getMessage();
        }
    }

    /**
     * The final CSV row is the forming candle, exactly as in the source Python project.
     */
    public List<Candle> readFeed(String key, String timeframe, boolean includeFormingCandle) {
        if (heartbeat(key) == null) return new ArrayList<>();
        Path file = feedDirectory.resolve("feed_" + key + "_" + timeframe.toUpperCase() + ".csv");
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            if (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() > MAX_FILE_AGE_MS) {
                return new ArrayList<>();
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() <= 1) return new ArrayList<>();
            String[] header = lines.get(0).trim().split(",");
            Map<String, Integer> columns = new HashMap<>();
            for (int i = 0; i < header.length; i++) columns.put(header[i].trim(), i);

            List<Candle> candles = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                long time = Long.parseLong(p[columns.get("time_utc")].trim()) * 1000L;
                double open = Double.parseDouble(p[columns.get("open")].trim());
                double high = Double.parseDouble(p[columns.get("high")].trim());
                double low = Double.parseDouble(p[columns.get("low")].trim());
                double close = Double.parseDouble(p[columns.get("close")].trim());
                double volume = columns.containsKey("tick_volume")
                        ? Double.parseDouble(p[columns.get("tick_volume")].trim()) : 0.0;
                candles.add(new Candle(time, open, high, low, close, volume));
            }
            if (!includeFormingCandle && !candles.isEmpty()) candles.remove(candles.size() - 1);
            return candles;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
