package com.lavaksoft.bot.trade.mt5;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavaksoft.bot.trade.model.TradeSignal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Java side of the same local-file protocol used by the supplied Python project.
 */
public class Mt5TradeExecutor {
    private final Path filesDirectory;
    private final String prefix;
    private final ObjectMapper mapper = new ObjectMapper();

    public Mt5TradeExecutor(Path filesDirectory) {
        this(filesDirectory, "signalScanner");
    }

    public Mt5TradeExecutor(Path filesDirectory, String prefix) {
        this.filesDirectory = filesDirectory;
        this.prefix = prefix;
    }

    public void placeOrder(String symbol, TradeSignal signal, double volume, String comment) throws IOException {
        Files.createDirectories(filesDirectory);
        String line = String.format(java.util.Locale.US,
                "OPEN|%s|%s|%.2f|%.2f|%.2f|%.2f|%.2f|%s%n",
                symbol,
                signal.getDirection().name(),
                volume,
                round2(signal.getStop()),
                round2(signal.getTp1()),
                round2(signal.getTp2()),
                round2(signal.getTp3()),
                comment == null ? signal.getStrategy() : comment);
        Files.write(commandsFile(), line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Mt5AccountSnapshot getAccountSnapshot() throws IOException {
        Path file = positionsFile();
        if (!Files.exists(file)) throw new IOException("MT5 positions snapshot not found: " + file);
        JsonNode root = mapper.readTree(file.toFile());
        Mt5AccountSnapshot result = new Mt5AccountSnapshot();
        result.setBalance(root.path("balance").asDouble());
        result.setEquity(root.path("equity").asDouble());
        result.setConnected(root.path("debug_connected").asBoolean(false));
        result.setTradeAllowed(root.path("debug_trade_allowed").asBoolean(false));
        result.setLogin(root.path("debug_login").asLong());
        result.setServer(root.path("debug_server").asText(null));
        int tradeMode = root.path("debug_trade_mode").asInt(-1);
        result.setAccountKind(tradeMode == 0 ? "demo" : tradeMode == 1 ? "contest" : tradeMode == 2 ? "real" : "unknown");

        List<Mt5Position> positions = new ArrayList<>();
        for (JsonNode p : root.path("positions")) {
            Mt5Position position = new Mt5Position();
            position.setTicket(p.path("ticket").asLong());
            position.setSymbol(p.path("symbol").asText());
            position.setDirection(p.path("direction").asText());
            position.setVolume(p.path("volume").asDouble());
            position.setEntry(p.path("entry").asDouble());
            position.setCurrent(p.path("current").asDouble());
            position.setSl(p.path("sl").asDouble());
            position.setTp(p.path("tp").asDouble());
            position.setPl(p.path("pl").asDouble());
            positions.add(position);
        }
        result.setPositions(positions);
        return result;
    }

    public Mt5ExecutionResult getLastResult() throws IOException {
        Path file = resultsFile();
        if (!Files.exists(file)) return null;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 4) return null;
            return new Mt5ExecutionResult(p[0], p[p.length - 3], p[p.length - 2], p[p.length - 1]);
        }
        return null;
    }

    public static double computeVolume(double balance, double entry, double stop, double riskPct,
                                       double contractSize, double volumeStep, double minVolume) {
        double riskAmount = balance * (riskPct / 100.0);
        double priceRisk = Math.abs(entry - stop);
        if (priceRisk <= 0 || riskAmount <= 0 || contractSize <= 0) return 0.0;
        double rawVolume = riskAmount / (priceRisk * contractSize);
        if (rawVolume < minVolume) return 0.0;
        long steps = Math.max(1L, Math.round(rawVolume / volumeStep));
        return Math.round(steps * volumeStep * 100.0) / 100.0;
    }

    public Path commandsFile() {
        return filesDirectory.resolve(prefix + "_commands.txt");
    }

    public Path resultsFile() {
        return filesDirectory.resolve(prefix + "_results.txt");
    }

    public Path positionsFile() {
        return filesDirectory.resolve(prefix + "_positions.json");
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
