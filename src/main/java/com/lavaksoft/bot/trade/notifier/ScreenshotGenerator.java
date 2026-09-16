package com.lavaksoft.bot.trade.notifier;

import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.IntervalType;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.Setup;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.xy.DefaultHighLowDataset;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ScreenshotGenerator {

    public byte[] generateChart(Setup setup, List<Candle> candles, IntervalType timeframe) throws IOException {
        if (candles == null || candles.isEmpty()) return new byte[0];

        // 1. Подготовка данных (обрезаем до maxCandles)
        ChartData chartData = prepareChartData(candles);
        List<Candle> currentCandles = chartData.currentCandles;
        int startIdx = chartData.startIdx;          // <-- извлекаем startIdx
        int optimalWidth = chartData.optimalWidth;

        // 2. Создание графика и настройка осей
        JFreeChart chart = createConfiguredChart(currentCandles, timeframe, optimalWidth);
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

        // 3. Рендеринг в изображение и получение графического контекста
        BufferedImage image = chart.createBufferedImage(1200, 1000);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle2D dataArea = image.getRaster().getBounds();
        double minX = domainAxis.valueToJava2D(domainAxis.getLowerBound(), dataArea, RectangleEdge.BOTTOM);
        double maxX = domainAxis.valueToJava2D(domainAxis.getUpperBound(), dataArea, RectangleEdge.BOTTOM);

        // 4. Отрисовка фракталов (линии и маркеры ключевых) - передаём domainAxis
        drawFractals(g, currentCandles, domainAxis, rangeAxis, dataArea, minX, maxX);

        // 5. Отрисовка BOS (если нужно)
        if (setup != null && setup.isBosLtf() && timeframe.getType().equals(IntervalType.Type.LTF)) {
            drawBosLine(g, currentCandles, rangeAxis, dataArea, minX, maxX);
        }

        // 6. Отрисовка FVG (если нужно)
        if ((setup.isFvgGrab() && timeframe.getType().equals(IntervalType.Type.MTF)) ||
                (setup.isFvgHtfGrab() && timeframe.getType().equals(IntervalType.Type.HTF))) {
            drawFvgZone(g, currentCandles, candles, startIdx, rangeAxis, domainAxis, dataArea);
        }

        g.dispose();
        return toByteArray(image);
    }


    public byte[] generateChartForTest(List<Candle> candles, IntervalType timeframe) throws IOException {
        // 1. Подготовка данных (обрезаем до maxCandles)
        ChartData chartData = prepareChartData(candles);
        List<Candle> currentCandles = chartData.currentCandles;
        int startIdx = chartData.startIdx;          // <-- извлекаем startIdx
        int optimalWidth = chartData.optimalWidth;

        // 2. Создание графика и настройка осей
        JFreeChart chart = createConfiguredChart(currentCandles, timeframe, optimalWidth);
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

        // 3. Рендеринг в изображение и получение графического контекста
        BufferedImage image = chart.createBufferedImage(1200, 1000);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle2D dataArea = image.getRaster().getBounds();
        double minX = domainAxis.valueToJava2D(domainAxis.getLowerBound(), dataArea, RectangleEdge.BOTTOM);
        double maxX = domainAxis.valueToJava2D(domainAxis.getUpperBound(), dataArea, RectangleEdge.BOTTOM);

        // 4. Отрисовка фракталов (линии и маркеры ключевых) - передаём domainAxis
        drawFractals(g, currentCandles, domainAxis, rangeAxis, dataArea, minX, maxX);

        // 5. Отрисовка BOS (если нужно)
//        if (setup != null && setup.isBosLtf() && timeframe.getType().equals(IntervalType.Type.LTF)) {
//            drawBosLine(g, currentCandles, rangeAxis, dataArea, minX, maxX);
//        }

        // 6. Отрисовка FVG (если нужно)
//        if ((setup.isFvgGrab() && timeframe.getType().equals(IntervalType.Type.MTF)) ||
//                (setup.isFvgHtfGrab() && timeframe.getType().equals(IntervalType.Type.HTF))) {
//            drawFvgZone(g, currentCandles, candles, startIdx, rangeAxis, domainAxis, dataArea);
//        }

        g.dispose();
        return toByteArray(image);
    }

    // ====================== ЛОГИЧЕСКИЕ БЛОКИ ======================

    private static class ChartData {
        List<Candle> currentCandles;
        int size;
        int optimalWidth;
        int startIdx;
    }

    private ChartData prepareChartData(List<Candle> candles) {
        int maxCandles = 75;
        int candleWidth = 18;
        int startIdx = (maxCandles > 0) ? Math.max(0, candles.size() - maxCandles) : 0;
        List<Candle> currentCandles = candles.subList(startIdx, candles.size());
        int size = currentCandles.size();
        int optimalWidth = Math.max(candleWidth, 800 / Math.max(1, size));
        ChartData data = new ChartData();
        data.currentCandles = currentCandles;
        data.size = size;
        data.optimalWidth = optimalWidth;
        data.startIdx = startIdx;
        return data;
    }

    private JFreeChart createConfiguredChart(List<Candle> candles, IntervalType timeframe, int optimalWidth) {
        int size = candles.size();
        Date[] dates = new Date[size];
        double[] opens = new double[size];
        double[] highs = new double[size];
        double[] lows = new double[size];
        double[] closes = new double[size];
        double[] volumes = new double[size];

        for (int i = 0; i < size; i++) {
            Candle c = candles.get(i);
            dates[i] = Date.from(c.getDateTime().atZone(java.time.ZoneId.systemDefault()).toInstant());
            opens[i] = c.getOpen();
            highs[i] = c.getHigh();
            lows[i] = c.getLow();
            closes[i] = c.getClose();
        }

        DefaultHighLowDataset dataset = new DefaultHighLowDataset(timeframe, dates, highs, lows, opens, closes, volumes);
        JFreeChart chart = ChartFactory.createCandlestickChart(timeframe.getName(), "Time", "Price", dataset, true);
        XYPlot plot = (XYPlot) chart.getPlot();

        // Оформление
        plot.setBackgroundPaint(new Color(18, 18, 24));
        plot.setDomainGridlinePaint(new Color(80, 80, 100));
        plot.setRangeGridlinePaint(new Color(80, 80, 100));
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);

        CandlestickRenderer renderer = (CandlestickRenderer) plot.getRenderer();
        renderer.setCandleWidth(optimalWidth);
        renderer.setDrawVolume(false);
        renderer.setSeriesPaint(0, Color.WHITE);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setLabelFont(new Font("Segoe UI", Font.PLAIN, 12));
        domainAxis.setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));
        domainAxis.setLabelPaint(Color.WHITE);
        domainAxis.setTickLabelPaint(Color.WHITE);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
        for (Candle c : candles) {
            minPrice = Math.min(minPrice, c.getLow());
            maxPrice = Math.max(maxPrice, c.getHigh());
        }
        double padding = (maxPrice - minPrice) * 0.02;
        rangeAxis.setRange(minPrice - padding, maxPrice + padding);
        rangeAxis.setAutoRange(false);
        rangeAxis.setLowerMargin(0.0);
        rangeAxis.setUpperMargin(0.0);
        rangeAxis.setLabelFont(new Font("Segoe UI", Font.PLAIN, 12));
        rangeAxis.setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 10));
        rangeAxis.setLabelPaint(Color.WHITE);
        rangeAxis.setTickLabelPaint(Color.WHITE);

        chart.setBackgroundPaint(new Color(18, 18, 24));
        chart.getTitle().setPaint(Color.WHITE);
        chart.getTitle().setFont(new Font("Segoe UI", Font.BOLD, 16));

        return chart;
    }

    // Исправленный метод drawFractals: добавлен параметр DateAxis domainAxis
    private void drawFractals(Graphics2D g, List<Candle> candles, DateAxis domainAxis, NumberAxis rangeAxis,
                              Rectangle2D dataArea, double minX, double maxX) {
        // Разделяем фракталы на верхние и нижние
        List<Fractal> fractalHighs = candles.stream()
                .filter(c -> c.getFractal() != null && c.getFractal().isHigh())
                .map(Candle::getFractal)
                .collect(Collectors.toList());
        List<Fractal> fractalLows = candles.stream()
                .filter(c -> c.getFractal() != null && c.getFractal().isLow())
                .map(Candle::getFractal)
                .collect(Collectors.toList());

        // Устанавливаем пунктирный стиль для линий фракталов
        Stroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0);
        g.setStroke(dashed);
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 11);
        g.setFont(labelFont);

        // Рисуем линии для high-фракталов
        for (Fractal f : fractalHighs) {
            double price = f.getCandle().getHigh();
            double y = rangeAxis.valueToJava2D(price, dataArea, RectangleEdge.LEFT);
            drawHorizontalLine(g, minX, maxX, y, new Color(180, 180, 100, 100), g.getStroke());
            g.setColor(Color.YELLOW);
            g.drawString("Fractal", (int) (maxX - 50), (int) y - 5);
        }
        // Рисуем линии для low-фракталов
        for (Fractal f : fractalLows) {
            double price = f.getCandle().getLow();
            double y = rangeAxis.valueToJava2D(price, dataArea, RectangleEdge.LEFT);
            drawHorizontalLine(g, minX, maxX, y, new Color(100, 180, 180, 100), g.getStroke());
            g.setColor(Color.CYAN);
            g.drawString("Fractal", (int) (maxX - 50), (int) y - 5);
        }

        // Рисуем маркеры ключевых фракталов (кружок с K)
        for (Candle c : candles) {
            Fractal f = c.getFractal();
            if (f != null && f.getKeyFractal() != null) {
                double price = f.isHigh() ? c.getHigh() : c.getLow();
                double x = domainAxis.valueToJava2D(c.getTime() / 1000.0, dataArea, RectangleEdge.BOTTOM);
                double y = rangeAxis.valueToJava2D(price, dataArea, RectangleEdge.LEFT);
                g.setColor(new Color(255, 165, 0));
                g.fillOval((int) x - 5, (int) y - 5, 10, 10);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g.drawString("K", (int) x - 3, (int) y + 4);
            }
        }
    }

    private void drawBosLine(Graphics2D g, List<Candle> candles, NumberAxis rangeAxis,
                             Rectangle2D dataArea, double minX, double maxX) {
        KeyFractal lastKeyFractal = candles.stream()
                .filter(c -> c.getFractal() != null && c.getFractal().getKeyFractal() != null)
                .map(c -> c.getFractal().getKeyFractal())
                .max(Comparator.comparing(kf -> kf.getFractal().getCandle().getDateTime()))
                .orElseThrow(() -> new RuntimeException("Не найден Key Fractal для BOS"));

        double price = lastKeyFractal.getFractal().isHigh()
                ? lastKeyFractal.getFractal().getCandle().getHigh()
                : lastKeyFractal.getFractal().getCandle().getLow();
        double y = rangeAxis.valueToJava2D(price, dataArea, RectangleEdge.LEFT);
        Color color = lastKeyFractal.getFractal().isHigh() ? new Color(0, 255, 0) : new Color(255, 0, 0);
        drawHorizontalLine(g, minX, maxX, y, color, new BasicStroke(3.0f));

        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g.setColor(color);
        g.drawString("BOS", (int) (maxX - 40), (int) y - 8);
    }

    // Исправлен сигнатура: убран лишний параметр Setup (не используется)
    private void drawFvgZone(Graphics2D g, List<Candle> currentCandles,
                             List<Candle> originalCandles, int startIdx,
                             NumberAxis rangeAxis, DateAxis domainAxis, Rectangle2D dataArea) {
        // Находим последний подходящий FVG
        FVG zone = currentCandles.stream()
                .filter(c -> c.getFvg() != null && FVGUtils.checkBelongFVG(c.getFvg(), currentCandles.get(currentCandles.size() - 1)))
                .map(Candle::getFvg)
                .max(Comparator.comparing(f -> f.getCandle().getDateTime()))
                .orElseThrow(() -> new RuntimeException("Нет FVG, но в Setup он указан"));

        int startIdxInSublist = originalCandles.indexOf(zone.getCandle()) - startIdx;
        int endIdxInSublist = originalCandles.size() - startIdx - 1;
        if (startIdxInSublist < 0 || endIdxInSublist >= currentCandles.size() || startIdxInSublist > endIdxInSublist) {
            return;
        }

        long startTime = currentCandles.get(startIdxInSublist).getTime();
        long endTime = currentCandles.get(endIdxInSublist).getTime();
        long step = (endTime - startTime) / (endIdxInSublist - startIdxInSublist + 1);
        double leftSec = startTime / 1000.0;
        double rightSec = (endTime + step / 2) / 1000.0;

        double x1 = domainAxis.valueToJava2D(leftSec, dataArea, RectangleEdge.BOTTOM);
        double x2 = domainAxis.valueToJava2D(rightSec, dataArea, RectangleEdge.BOTTOM);
        double yLow = rangeAxis.valueToJava2D(zone.getLow(), dataArea, RectangleEdge.LEFT);
        double yHigh = rangeAxis.valueToJava2D(zone.getHigh(), dataArea, RectangleEdge.LEFT);

        Color fillColor, borderColor;
        if (zone.getType().equals(ActionType.LONG)) {
            fillColor = new Color(0, 255, 0, 80);
            borderColor = new Color(0, 200, 0);
        } else {
            fillColor = new Color(255, 0, 0, 80);
            borderColor = new Color(200, 0, 0);
        }

        double rectY = Math.min(yLow, yHigh);
        double rectHeight = Math.abs(yLow - yHigh);
        Rectangle2D rect = new Rectangle2D.Double(x1, rectY, x2 - x1, rectHeight);
        drawFilledRectangleWithCenteredText(g, rect, fillColor, borderColor, "FVG", new Font("Segoe UI", Font.BOLD, 12));
    }

    private byte[] toByteArray(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private void drawHorizontalLine(Graphics2D g, double x1, double x2, double y, Color color, Stroke stroke) {
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        g.setColor(color);
        g.setStroke(stroke);
        g.drawLine((int) x1, (int) y, (int) x2, (int) y);
        g.setStroke(oldStroke);
        g.setColor(oldColor);
    }

    private void drawFilledRectangleWithCenteredText(Graphics2D g, Rectangle2D rect,
                                                     Color fillColor, Color borderColor,
                                                     String text, Font textFont) {
        Color oldColor = g.getColor();
        Font oldFont = g.getFont();
        Stroke oldStroke = g.getStroke();

        g.setColor(fillColor);
        g.fill(rect);

        g.setColor(borderColor);
        g.setStroke(new BasicStroke(1.0f));
        g.draw(rect);

        g.setFont(textFont);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();

        double textX = rect.getCenterX() - textWidth / 2.0;
        double textY = rect.getCenterY() + textHeight / 2.0 - fm.getDescent();

        g.setColor(borderColor);
        g.drawString(text, (int) textX, (int) textY);

        g.setColor(oldColor);
        g.setFont(oldFont);
        g.setStroke(oldStroke);
    }

    // ========== ТЕСТОВЫЙ МЕТОД ==========

    public byte[] testScreenshot(List<Candle> candles, IntervalType type, String pare) {
        Setup setup = new Setup(pare);
        setup.setBosLtf(true);
        setup.setFvgGrab(true);
        try {
            return generateChart(setup, candles, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}