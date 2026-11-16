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
import com.lavaksoft.bot.trade.model.MarketBias;
import com.lavaksoft.bot.trade.model.OrderBlock;
import com.lavaksoft.bot.trade.model.OteZone;
import com.lavaksoft.bot.trade.model.POI;
import com.lavaksoft.bot.trade.model.SwingPoints;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class IndicatorChartRenderer {

    private static final int WIDTH = 3200;
    private static final int HEIGHT = 1200;
    private static final int LEFT = 90;
    private static final int RIGHT = 40;
    private static final int TOP = 70;
    private static final int BOTTOM = 80;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    void renderBefore(List<Candle> candles, String title, Path output) throws IOException {
        render(candles, title + " — BEFORE", output, canvas -> {
        });
    }

    void renderFractals(List<Candle> candles, Path output) throws IOException {
        render(candles, "Fractals — AFTER", output, canvas -> {
            for (int i = 0; i < candles.size(); i++) {
                Fractal fractal = candles.get(i).getFractal();
                if (fractal == null) continue;
                if (fractal.isHigh()) canvas.marker(i, candles.get(i).getHigh(), "FH", new Color(255, 210, 70));
                if (fractal.isLow()) canvas.marker(i, candles.get(i).getLow(), "FL", new Color(80, 220, 255));
            }
        });
    }

    void renderKeyFractals(List<Candle> candles, Path output) throws IOException {
        render(candles, "Key fractals — AFTER", output, canvas -> {
            for (int i = 0; i < candles.size(); i++) {
                Fractal fractal = candles.get(i).getFractal();
                if (fractal == null || fractal.getKeyFractal() == null) continue;
                KeyFractal key = fractal.getKeyFractal();
                double price = fractal.isHigh() ? candles.get(i).getHigh() : candles.get(i).getLow();
                canvas.marker(i, price, key.getType().name(), new Color(255, 165, 0));
            }
        });
    }

    void renderFvg(List<Candle> candles, List<FVG> fvgs, String title, Path output) throws IOException {
        render(candles, title + " — AFTER", output, canvas -> drawFvgs(canvas, candles, fvgs));
    }

    void renderContexts(List<Candle> candles, List<ContextFlow> contexts, Path output) throws IOException {
        render(candles, "Context flows — AFTER", output, canvas -> {
            for (int i = 0; i < contexts.size(); i++) {
                ContextFlow context = contexts.get(i);
                if (context.getCandles().isEmpty()) continue;
                int from = candles.indexOf(context.getCandles().get(0));
                int to = candles.indexOf(context.getCandles().get(context.getCandles().size() - 1));
                if (from < 0 || to < 0) continue;
                Color color = context.getType() == ActionType.LONG
                        ? new Color(45, 170, 105, 32)
                        : new Color(210, 70, 80, 32);
                canvas.verticalBand(from, to, color, "CTX " + (i + 1) + " " + context.getType() + (context.isHasBOS() ? " BOS" : ""));
            }
        });
    }

    void renderLiquidity(List<Candle> candles, List<Liquidity> liquidities, Path output) throws IOException {
        render(candles, "Liquidity — AFTER", output, canvas -> {
            for (Liquidity liquidity : liquidities) {
                Candle source = liquidity.getFractal().getCandle();
                int from = candles.indexOf(source);
                if (from < 0) continue;
                double price = liquidity.getFractal().isHigh() ? source.getHigh() : source.getLow();
                Color color = liquidity.getFractal().isHigh() ? new Color(255, 190, 80) : new Color(80, 210, 255);
                canvas.horizontal(from, candles.size() - 1, price, color,
                        liquidity.getType().name() + (liquidity.isFulfilled() ? " X" : ""));
            }
        });
    }

    void renderPoi(List<Candle> candles, List<POI> pois, Path output) throws IOException {
        render(candles, "Points of interest — AFTER", output, canvas -> {
            for (POI poi : pois) {
                int index = -1;
                if (poi.getFvg() != null) index = candles.indexOf(poi.getFvg().getCandle());
                else if (poi.getLiquidity() != null)
                    index = candles.indexOf(poi.getLiquidity().getFractal().getCandle());
                if (index < 0) continue;
                Color color = poi.getType() == ActionType.LONG ? new Color(50, 220, 120) : new Color(255, 100, 100);
                canvas.marker(index, poi.getValue(), "POI " + poi.getType(), color);
            }
        });
    }

    void renderBos(List<Candle> candles, List<KeyFractal> keyFractals, Path output) throws IOException {
        render(candles, "BOS flags — AFTER", output, canvas -> {
            for (KeyFractal key : keyFractals) {
                if (!key.isHasBos()) continue;
                Candle source = key.getFractal().getCandle();
                int from = candles.indexOf(source);
                if (from < 0) continue;
                double price = key.getFractal().isHigh() ? source.getHigh() : source.getLow();
                canvas.horizontal(from, candles.size() - 1, price, new Color(255, 80, 220), "BOS " + key.getType());
            }
        });
    }

    void renderMarketStructure(List<Candle> candles, SwingPoints points, MarketBias bias, Path output) throws IOException {
        render(candles, "Market structure / bias=" + bias + " — AFTER", output, canvas -> {
            for (Integer index : points.getHighs())
                canvas.marker(index, candles.get(index).getHigh(), "SH", new Color(255, 196, 80));
            for (Integer index : points.getLows())
                canvas.marker(index, candles.get(index).getLow(), "SL", new Color(85, 205, 255));
            canvas.topLabel("BIAS: " + bias, new Color(220, 230, 245));
        });
    }

    void renderSweepChoch(List<Candle> candles, LiquiditySweep sweep, Choch choch, Path output) throws IOException {
        render(candles, "Liquidity sweep + CHoCH — AFTER", output, canvas -> {
            if (sweep != null) {
                canvas.horizontal(sweep.getSweptIndex(), sweep.getSweepIndex(), sweep.getLevel(), new Color(255, 190, 70), "swept liquidity");
                canvas.marker(sweep.getSweepIndex(), sweep.getExtreme(), "SWEEP", new Color(255, 110, 80));
            }
            if (choch != null) {
                canvas.horizontal(choch.getReferenceIndex(), choch.getChochIndex(), choch.getLevel(), new Color(190, 100, 255), "CHoCH level");
                canvas.marker(choch.getChochIndex(), candles.get(choch.getChochIndex()).getClose(), "CHoCH", new Color(220, 130, 255));
            }
        });
    }

    void renderOte(List<Candle> candles, OrderBlock orderBlock, FVG fvg, OteZone ote, Path output) throws IOException {
        render(candles, "Order Block + FVG + OTE — AFTER", output, canvas -> {
            if (orderBlock != null) {
                canvas.zone(orderBlock.getOrderBlockIndex(), candles.size() - 1,
                        orderBlock.getBottom(), orderBlock.getTop(), new Color(255, 185, 70, 55), "OB " + orderBlock.getType());
                canvas.marker(orderBlock.getBosIndex(), candles.get(orderBlock.getBosIndex()).getClose(), "BOS", new Color(255, 210, 100));
            }
            if (fvg != null) drawFvgs(canvas, candles, java.util.Collections.singletonList(fvg));
            if (ote != null) {
                int from = orderBlock == null ? 0 : orderBlock.getBosIndex();
                canvas.zone(from, candles.size() - 1, ote.getBottom(), ote.getTop(), new Color(120, 100, 255, 50), "OTE 0.618-0.786");
                canvas.horizontal(from, candles.size() - 1, ote.getMid(), new Color(170, 150, 255), "OTE MID");
            }
        });
    }

    void renderBpr(List<Candle> candles, List<FVG> fvgs, List<BprZone> zones, Path output) throws IOException {
        render(candles, "Balanced Price Ranges — AFTER", output, canvas -> {
            drawFvgs(canvas, candles, fvgs);
            for (BprZone zone : zones) {
                canvas.zone(zone.getFormedIndex(), candles.size() - 1, zone.getBottom(), zone.getTop(),
                        new Color(255, 90, 215, 55), "BPR");
            }
        });
    }

    void renderBreaker(List<Candle> candles, BreakerBlock breaker, Path output) throws IOException {
        render(candles, "Breaker block — AFTER", output, canvas -> {
            if (breaker == null) return;
            canvas.zone(breaker.getOriginOrderBlockIndex(), candles.size() - 1,
                    breaker.getBottom(), breaker.getTop(), new Color(70, 185, 255, 55), "BREAKER");
            canvas.marker(breaker.getInvalidationIndex(), candles.get(breaker.getInvalidationIndex()).getClose(),
                    "INVALIDATED OB", new Color(70, 200, 255));
        });
    }

    private static void drawFvgs(Canvas canvas, List<Candle> candles, List<FVG> fvgs) {
        for (FVG fvg : fvgs) {
            int from = candles.indexOf(fvg.getCandle());
            if (from < 0) continue;
            int to = fvg.getCandleFulfilled() == null ? candles.size() - 1 : Math.max(from, candles.indexOf(fvg.getCandleFulfilled()));
            Color color = fvg.getType() == ActionType.LONG ? new Color(50, 205, 120, 55) : new Color(255, 90, 90, 55);
            canvas.zone(from, to, fvg.getLow(), fvg.getHigh(), color,
                    "FVG " + fvg.getType() + (fvg.isFulfilled() ? " fulfilled" : " active"));
        }
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
    private interface Overlay {
        void draw(Canvas canvas);
    }

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
            double padding = Math.max((max - min) * 0.03, 0.0000001);
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
            int bodyWidth = Math.max(2, Math.min(10, (int) Math.round(step * 0.65)));
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

        private void horizontal(int fromIndex, int toIndex, double price, Color color, String label) {
            fromIndex = clampIndex(fromIndex);
            toIndex = clampIndex(toIndex);
            int y = y(price);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(x(fromIndex), y, x(toIndex), y);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.drawString(label, x(fromIndex) + 4, y - 4);
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

        private void verticalBand(int fromIndex, int toIndex, Color color, String label) {
            fromIndex = clampIndex(fromIndex);
            toIndex = clampIndex(toIndex);
            int left = Math.min(x(fromIndex), x(toIndex));
            int right = Math.max(x(fromIndex), x(toIndex));
            g.setColor(color);
            g.fillRect(left, TOP, Math.max(3, right - left), (int) plotHeight);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 220));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.drawString(label, left + 5, TOP + 16);
        }

        private void topLabel(String text, Color color) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(color);
            g.drawString(text, WIDTH - RIGHT - 220, 35);
        }

        private int clampIndex(int index) {
            return Math.max(0, Math.min(candles.size() - 1, index));
        }

        private int x(int index) {
            return LEFT + (int) Math.round(index * step);
        }

        private int y(double price) {
            double normalized = (price - minPrice) / (maxPrice - minPrice);
            return TOP + (int) Math.round((1.0 - normalized) * plotHeight);
        }
    }
}
