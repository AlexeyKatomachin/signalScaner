package visual;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.BprZone;
import com.lavaksoft.bot.trade.model.BreakerBlock;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.Choch;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.Liquidity;
import com.lavaksoft.bot.trade.model.LiquiditySweep;
import com.lavaksoft.bot.trade.model.LiquidityType;
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;
import com.lavaksoft.bot.trade.model.OteZone;
import com.lavaksoft.bot.trade.model.POI;
import com.lavaksoft.bot.trade.model.StructureBreak;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class IndicatorChartRenderer {

    private static final int WIDTH = 3200;
    private static final int HEIGHT = 1300;
    private static final int LEFT = 90;
    private static final int RIGHT = 40;
    private static final int TOP = 75;
    private static final int BOTTOM = 80;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    void renderBefore(List<Candle> candles, String title, Path output) throws IOException {
        render(candles, title + " — BEFORE", output, canvas -> { });
    }

    void renderFractals(List<Candle> candles, Path output) throws IOException {
        render(candles, "Fractals — AFTER", output, canvas -> {
            for (int i = 0; i < candles.size(); i++) {
                Fractal fractal = candles.get(i).getFractal();
                if (fractal == null) continue;
                if (fractal.isHigh()) canvas.marker(i, candles.get(i).getHigh(), "FH", new Color(255, 205, 70));
                if (fractal.isLow()) canvas.marker(i, candles.get(i).getLow(), "FL", new Color(70, 205, 255));
            }
        });
    }

    void renderKeyFractals(List<Candle> candles, List<KeyFractal> keys, Path output) throws IOException {
        render(candles, "Key fractals / HH-HL-LH-LL — AFTER", output, canvas -> {
            List<KeyFractal> visible = new ArrayList<>(keys);
            visible.sort(Comparator.comparing(k -> k.getFractal().getCandle().getDateTime()));
            KeyFractal previous = null;
            for (KeyFractal key : visible) {
                int index = candles.indexOf(key.getFractal().getCandle());
                if (index < 0) continue;
                double price = pivotPrice(key);
                Color color = key.getFractal().isHigh() ? new Color(255, 180, 65) : new Color(80, 205, 255);
                if (previous != null) {
                    int previousIndex = candles.indexOf(previous.getFractal().getCandle());
                    if (previousIndex >= 0) canvas.segment(previousIndex, pivotPrice(previous), index, price, new Color(150, 155, 175), 1.4f);
                }
                canvas.marker(index, price, key.getType().name(), color);
                previous = key;
            }
        });
    }

    void renderFvg(List<Candle> candles, List<FVG> fvgs, String title, Path output) throws IOException {
        render(candles, title + " — AFTER", output, canvas -> drawFvgs(canvas, candles, fvgs));
    }

    void renderContexts(List<Candle> candles, List<ContextFlow> contexts, Path output) throws IOException {
        render(candles, "Context from HH/HL and LH/LL — AFTER", output, canvas -> {
            for (ContextFlow context : contexts) {
                List<KeyFractal> keys = context.getKeyFractals();
                Color contextColor = context.getType() == ActionType.LONG
                        ? new Color(55, 215, 125) : new Color(245, 95, 105);
                for (int i = 0; i < keys.size(); i++) {
                    KeyFractal key = keys.get(i);
                    int index = candles.indexOf(key.getFractal().getCandle());
                    if (index < 0) continue;
                    double price = pivotPrice(key);
                    canvas.marker(index, price, key.getType().name(), contextColor);
                    if (i > 0) {
                        KeyFractal previous = keys.get(i - 1);
                        int previousIndex = candles.indexOf(previous.getFractal().getCandle());
                        if (previousIndex >= 0) canvas.segment(previousIndex, pivotPrice(previous), index, price, contextColor, 2.0f);
                    }
                }
                if (!keys.isEmpty()) {
                    int first = candles.indexOf(keys.get(0).getFractal().getCandle());
                    canvas.labelAtIndex(first, "CTX " + context.getType(), contextColor);
                }
            }
        });
    }

    void renderLiquidity(List<Candle> candles, List<Liquidity> liquidities, Path output) throws IOException {
        render(candles, "Liquidity / EQH-EQL — AFTER", output, canvas -> {
            List<Liquidity> equal = new ArrayList<>();
            List<Liquidity> active = new ArrayList<>();
            for (Liquidity liquidity : liquidities) {
                if (liquidity.getType() == LiquidityType.EQH || liquidity.getType() == LiquidityType.EQL) equal.add(liquidity);
                else if (!liquidity.isFulfilled()) active.add(liquidity);
            }
            equal.sort(Comparator.comparing(l -> l.getFractal().getCandle().getDateTime()));
            int equalFrom = Math.max(0, equal.size() - 14);
            for (int i = equalFrom; i < equal.size(); i++) {
                Liquidity liquidity = equal.get(i);
                Candle source = liquidity.getFractal().getCandle();
                int index = candles.indexOf(source);
                if (index < 0) continue;
                double price = liquidity.getType() == LiquidityType.EQH ? source.getHigh() : source.getLow();
                Color color = liquidity.getType() == LiquidityType.EQH ? new Color(255, 190, 70) : new Color(70, 205, 255);
                canvas.marker(index, price, liquidity.getType().name(), color);
                canvas.horizontal(index, Math.min(candles.size() - 1, index + 28), price, color, liquidity.getType().name());
            }

            active.sort(Comparator.comparing(l -> l.getFractal().getCandle().getDateTime()));
            int activeFrom = Math.max(0, active.size() - 6);
            for (int i = activeFrom; i < active.size(); i++) {
                Liquidity liquidity = active.get(i);
                Candle source = liquidity.getFractal().getCandle();
                int index = candles.indexOf(source);
                if (index < 0) continue;
                double price = liquidity.getFractal().isHigh() ? source.getHigh() : source.getLow();
                Color color = liquidity.getFractal().isHigh() ? new Color(230, 165, 65) : new Color(75, 175, 235);
                canvas.horizontal(index, Math.min(candles.size() - 1, index + 18), price, color, liquidity.getType().name());
            }
        });
    }

    void renderPoi(List<Candle> candles, List<POI> pois, Path output) throws IOException {
        render(candles, "Points of interest — AFTER", output, canvas -> {
            int from = Math.max(0, pois.size() - 16);
            for (int p = from; p < pois.size(); p++) {
                POI poi = pois.get(p);
                int index = -1;
                if (poi.getFvg() != null) index = candles.indexOf(poi.getFvg().getCandle());
                else if (poi.getLiquidity() != null) index = candles.indexOf(poi.getLiquidity().getFractal().getCandle());
                if (index < 0) continue;
                Color color = poi.getType() == ActionType.LONG ? new Color(50, 220, 120) : new Color(255, 100, 100);
                canvas.marker(index, poi.getValue(), "POI " + poi.getType(), color);
            }
        });
    }

    void renderBos(List<Candle> candles, List<StructureBreak> breaks, Path output) throws IOException {
        render(candles, "BOS / CHoCH structure breaks — AFTER", output, canvas -> {
            int from = Math.max(0, breaks.size() - 14);
            for (int i = from; i < breaks.size(); i++) {
                StructureBreak b = breaks.get(i);
                Color color = b.getType().name().equals("CHOCH") ? new Color(205, 105, 255)
                        : b.getDirection() == ActionType.LONG ? new Color(70, 220, 130) : new Color(255, 95, 105);
                canvas.horizontal(b.getReferenceIndex(), b.getBreakIndex(), b.getLevel(), color,
                        b.getType() + " " + b.getDirection());
                canvas.marker(b.getBreakIndex(), candles.get(b.getBreakIndex()).getClose(), b.getType().name(), color);
                canvas.marker(b.getReferenceIndex(), b.getLevel(), "PIVOT", color);
            }
        });
    }

    void renderMarketStructure(List<Candle> candles, List<KeyFractal> keys, MarketBias bias, Path output) throws IOException {
        render(candles, "Market structure / bias=" + bias + " — AFTER", output, canvas -> {
            KeyFractal previous = null;
            for (KeyFractal key : keys) {
                int index = candles.indexOf(key.getFractal().getCandle());
                if (index < 0) continue;
                double price = pivotPrice(key);
                Color color = key.getFractal().isHigh() ? new Color(255, 190, 75) : new Color(70, 205, 255);
                if (previous != null) {
                    int pIndex = candles.indexOf(previous.getFractal().getCandle());
                    if (pIndex >= 0) canvas.segment(pIndex, pivotPrice(previous), index, price, new Color(160, 165, 185), 1.7f);
                }
                canvas.marker(index, price, key.getType().name(), color);
                previous = key;
            }
            canvas.topLabel("BIAS: " + bias, new Color(220, 230, 245));
        });
    }

    void renderSweepChoch(List<Candle> candles, List<LiquiditySweep> sweeps, List<Choch> chochs, Path output) throws IOException {
        render(candles, "Main structure liquidity sweeps + CHoCH — AFTER", output, canvas -> {
            for (LiquiditySweep sweep : sweeps) {
                boolean lowSweep = sweep.getExtreme() < sweep.getLevel();
                Color color = lowSweep ? new Color(80, 215, 145) : new Color(255, 160, 70);
                canvas.horizontal(sweep.getSweptIndex(), sweep.getSweepIndex(), sweep.getLevel(), color, lowSweep ? "SSL SWEEP" : "BSL SWEEP");
                canvas.marker(sweep.getSweepIndex(), sweep.getExtreme(), "SWEEP", color);
            }
            for (Choch choch : chochs) {
                if (choch == null) continue;
                Color color = new Color(205, 105, 255);
                canvas.horizontal(choch.getReferenceIndex(), choch.getChochIndex(), choch.getLevel(), color, "CHoCH");
                canvas.marker(choch.getChochIndex(), candles.get(choch.getChochIndex()).getClose(), "CHoCH", color);
            }
        });
    }

    void renderOte(List<Candle> candles, List<OrderBlock> blocks, List<FVG> fvgs, List<OteZone> zones, Path output) throws IOException {
        render(candles, "Order Block + FVG + OTE (multiple setups) — AFTER", output, canvas -> {
            for (int i = 0; i < blocks.size(); i++) {
                OrderBlock ob = blocks.get(i);
                OteZone ote = zones.get(i);
                int to = Math.min(candles.size() - 1, ob.getBosIndex() + 24);
                canvas.zone(ob.getOrderBlockIndex(), to, ob.getBottom(), ob.getTop(), new Color(255, 185, 70, 45), "OB #" + (i + 1));
                canvas.zone(ob.getBosIndex(), to, ote.getBottom(), ote.getTop(), new Color(120, 100, 255, 42), "OTE #" + (i + 1));
                canvas.horizontal(ob.getBosIndex(), to, ote.getMid(), new Color(170, 150, 255), "OTE MID");
                canvas.marker(ob.getLevelIndex(), ob.getType() == ActionType.LONG ? candles.get(ob.getLevelIndex()).getHigh() : candles.get(ob.getLevelIndex()).getLow(), "STRUCT", new Color(255, 210, 100));
                if (ob.getLegLowIndex() >= 0) canvas.marker(ob.getLegLowIndex(), ob.getLegLow(), "LEG LOW", new Color(70, 205, 255));
                if (ob.getLegHighIndex() >= 0) canvas.marker(ob.getLegHighIndex(), ob.getLegHigh(), "LEG HIGH", new Color(255, 190, 75));
                canvas.marker(ob.getBosIndex(), candles.get(ob.getBosIndex()).getClose(), "BREAK", new Color(255, 210, 100));
                if (i < fvgs.size() && fvgs.get(i) != null) drawFvgs(canvas, candles, java.util.Collections.singletonList(fvgs.get(i)));
            }
        });
    }

    void renderBpr(List<Candle> candles, List<BprZone> zones, Path output) throws IOException {
        render(candles, "Balanced Price Ranges — AFTER", output, canvas -> {
            for (int i = 0; i < zones.size(); i++) {
                BprZone zone = zones.get(i);
                int firstIndex = candles.indexOf(zone.getFirstFvg().getCandle());
                int secondIndex = candles.indexOf(zone.getSecondFvg().getCandle());
                int to = Math.min(candles.size() - 1, zone.getFormedIndex() + 22);
                drawSingleFvg(canvas, candles, zone.getFirstFvg(), Math.min(to, Math.max(zone.getFormedIndex(), firstIndex + 10)), "FVG A");
                drawSingleFvg(canvas, candles, zone.getSecondFvg(), Math.min(to, Math.max(zone.getFormedIndex(), secondIndex + 10)), "FVG B");
                canvas.zone(zone.getFormedIndex(), to, zone.getBottom(), zone.getTop(), new Color(255, 90, 215, 65), "BPR #" + (i + 1));
            }
        });
    }

    void renderBreaker(List<Candle> candles, List<BreakerBlock> breakers, Path output) throws IOException {
        render(candles, "Breaker blocks — AFTER", output, canvas -> {
            for (int i = 0; i < breakers.size(); i++) {
                BreakerBlock breaker = breakers.get(i);
                int to = Math.min(candles.size() - 1, breaker.getInvalidationIndex() + 24);
                canvas.zone(breaker.getOriginOrderBlockIndex(), to, breaker.getBottom(), breaker.getTop(), new Color(70, 185, 255, 52), "BREAKER #" + (i + 1));
                canvas.marker(breaker.getOriginOrderBlockIndex(), (breaker.getTop() + breaker.getBottom()) / 2.0, "ORIGIN OB", new Color(70, 200, 255));
                canvas.marker(breaker.getInvalidationIndex(), candles.get(breaker.getInvalidationIndex()).getClose(), "INVALIDATED", new Color(70, 220, 255));
            }
        });
    }

    private static double pivotPrice(KeyFractal key) {
        Candle candle = key.getFractal().getCandle();
        return key.getFractal().isHigh() ? candle.getHigh() : candle.getLow();
    }

    private static void drawFvgs(Canvas canvas, List<Candle> candles, List<FVG> fvgs) {
        int fromList = Math.max(0, fvgs.size() - 10);
        for (int i = fromList; i < fvgs.size(); i++) {
            FVG fvg = fvgs.get(i);
            int from = candles.indexOf(fvg.getCandle());
            if (from < 0) continue;
            int fulfilled = fvg.getCandleFulfilled() == null ? -1 : candles.indexOf(fvg.getCandleFulfilled());
            int to = fulfilled >= from ? fulfilled : Math.min(candles.size() - 1, from + 24);
            drawSingleFvg(canvas, candles, fvg, to, "FVG " + fvg.getType());
        }
    }

    private static void drawSingleFvg(Canvas canvas, List<Candle> candles, FVG fvg, int to, String label) {
        int from = candles.indexOf(fvg.getCandle());
        if (from < 0) return;
        Color color = fvg.getType() == ActionType.LONG ? new Color(50, 205, 120, 50) : new Color(255, 90, 90, 50);
        canvas.zone(from, Math.max(from + 1, to), fvg.getLow(), fvg.getHigh(), color, label);
    }

    private void render(List<Candle> candles, String title, Path output, Overlay overlay) throws IOException {
        if (candles.isEmpty()) throw new IllegalArgumentException("No candles to render");
        Files.createDirectories(output.getParent());
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(18, 18, 24));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            Canvas canvas = new Canvas(g, candles);
            canvas.drawAxes(title);
            canvas.drawCandles();
            overlay.draw(canvas);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", output.toFile());
    }

    @FunctionalInterface
    private interface Overlay { void draw(Canvas canvas); }

    private static final class Canvas {
        private final Graphics2D g;
        private final List<Candle> candles;
        private final double minPrice;
        private final double maxPrice;
        private final double plotWidth = WIDTH - LEFT - RIGHT;
        private final double plotHeight = HEIGHT - TOP - BOTTOM;
        private final double step;

        private Canvas(Graphics2D g, List<Candle> candles) {
            this.g = g;
            this.candles = candles;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Candle candle : candles) {
                min = Math.min(min, candle.getLow());
                max = Math.max(max, candle.getHigh());
            }
            double padding = Math.max((max - min) * 0.04, 0.0000001);
            this.minPrice = min - padding;
            this.maxPrice = max + padding;
            this.step = plotWidth / Math.max(1, candles.size() - 1);
        }

        private void drawAxes(String title) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.setColor(Color.WHITE);
            g.drawString(title, LEFT, 35);
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            for (int i = 0; i <= 8; i++) {
                double price = minPrice + (maxPrice - minPrice) * i / 8.0;
                int y = y(price);
                g.setColor(new Color(65, 65, 80));
                g.drawLine(LEFT, y, WIDTH - RIGHT, y);
                g.setColor(new Color(210, 210, 220));
                g.drawString(String.format("%.2f", price), 8, y + 4);
            }
            int labels = Math.min(10, Math.max(1, candles.size() - 1));
            for (int i = 0; i <= labels; i++) {
                int index = Math.min(candles.size() - 1, (int) Math.round((candles.size() - 1) * i / (double) labels));
                int x = x(index);
                g.setColor(new Color(65, 65, 80));
                g.drawLine(x, TOP, x, HEIGHT - BOTTOM);
                g.setColor(new Color(210, 210, 220));
                g.drawString(candles.get(index).getDateTime().format(TIME_FORMAT), x - 35, HEIGHT - 35);
            }
        }

        private void drawCandles() {
            int bodyWidth = Math.max(3, Math.min(14, (int) Math.round(step * 0.68)));
            for (int i = 0; i < candles.size(); i++) {
                Candle candle = candles.get(i);
                int x = x(i);
                int yHigh = y(candle.getHigh());
                int yLow = y(candle.getLow());
                int yOpen = y(candle.getOpen());
                int yClose = y(candle.getClose());
                Color candleColor = candle.getClose() >= candle.getOpen() ? new Color(70, 210, 135) : new Color(235, 85, 95);
                g.setColor(candleColor);
                g.drawLine(x, yHigh, x, yLow);
                int top = Math.min(yOpen, yClose);
                int height = Math.max(2, Math.abs(yOpen - yClose));
                g.fillRect(x - bodyWidth / 2, top, bodyWidth, height);
            }
        }

        private void marker(int index, double price, String text, Color color) {
            if (index < 0 || index >= candles.size()) return;
            int x = x(index);
            int y = y(price);
            g.setColor(color);
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            int textY = price >= candles.get(index).getClose() ? y - 8 : y + 18;
            g.drawString(text, x + 5, textY);
        }

        private void labelAtIndex(int index, String text, Color color) {
            if (index < 0 || index >= candles.size()) return;
            g.setColor(color);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.drawString(text, x(index) + 4, TOP + 18);
        }

        private void horizontal(int fromIndex, int toIndex, double price, Color color, String label) {
            fromIndex = clampIndex(fromIndex);
            toIndex = clampIndex(toIndex);
            int y = y(price);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine(x(fromIndex), y, x(toIndex), y);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.drawString(label, x(fromIndex) + 4, y - 4);
        }

        private void segment(int fromIndex, double fromPrice, int toIndex, double toPrice, Color color, float width) {
            g.setColor(color);
            g.setStroke(new BasicStroke(width));
            g.drawLine(x(clampIndex(fromIndex)), y(fromPrice), x(clampIndex(toIndex)), y(toPrice));
        }

        private void zone(int fromIndex, int toIndex, double low, double high, Color color, String label) {
            fromIndex = clampIndex(fromIndex);
            toIndex = clampIndex(toIndex);
            int x1 = x(fromIndex);
            int x2 = x(toIndex);
            int y1 = y(high);
            int y2 = y(low);
            int left = Math.min(x1, x2);
            int top = Math.min(y1, y2);
            int width = Math.max(3, Math.abs(x2 - x1));
            int height = Math.max(2, Math.abs(y2 - y1));
            g.setColor(color);
            g.fill(new Rectangle2D.Double(left, top, width, height));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 210));
            g.draw(new Rectangle2D.Double(left, top, width, height));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.drawString(label, left + 3, Math.max(TOP + 12, top - 3));
        }

        private void topLabel(String text, Color color) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(color);
            g.drawString(text, WIDTH - RIGHT - 220, 35);
        }

        private int clampIndex(int index) { return Math.max(0, Math.min(candles.size() - 1, index)); }
        private int x(int index) { return LEFT + (int) Math.round(index * step); }
        private int y(double price) {
            double normalized = (price - minPrice) / (maxPrice - minPrice);
            return TOP + (int) Math.round((1.0 - normalized) * plotHeight);
        }
    }
}
