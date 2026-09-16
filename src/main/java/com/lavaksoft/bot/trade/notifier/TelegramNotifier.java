package com.lavaksoft.bot.trade.notifier;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Order;
import com.lavaksoft.bot.trade.model.Setup;
import com.lavaksoft.bot.trade.model.Signal;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TelegramNotifier extends TelegramLongPollingBot {
    private final String botToken;
    private final String chatId;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public TelegramNotifier(String botToken, String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return "TradingSignalBot";
    }

    @Override
    public void onUpdateReceived(org.telegram.telegrambots.meta.api.objects.Update update) {
    }

    private void sendSignal(Signal signal, byte[] htfScreenshot, byte[] middleScreenshot, byte[] ltfScreenshot, long timestampMillis) {
        String directionEmoji = signal.getOrder().getType().equals(ActionType.LONG) ? "🟢" : "🔴";
        String directionText = signal.getOrder().getType().equals(ActionType.LONG) ? "ПОКУПКА" : "ПРОДАЖА";
        String dateStr = TIME_FMT.format(Instant.ofEpochMilli(timestampMillis));

        StringBuilder caption = new StringBuilder();
        caption.append(String.format("%s *%s %s*\n", directionEmoji, directionText, signal.getOrder().getSetup().getTradingPare()));
        caption.append("\n📊 *Детали сигнала:*\n");
        caption.append(String.format("💰 Цена входа: `%.4f`\n", signal.getOrder().getEnterPrice()));
        caption.append(String.format("Stop Loss: `%.4f`\n", signal.getOrder().getStopLoss()));
        caption.append(String.format("TakeProfit: `%.4f`\n", signal.getOrder().getTakeProfit()));
        caption.append(String.format("Частичное закрытие: `%.4f`\n", signal.getOrder().getPartialClosePrice()));
        caption.append(String.format("RR: `%.4f`\n", signal.getOrder().getRR()));
        Setup setup = signal.getOrder().getSetup();
        if (setup.isFvgGrab()) {
            caption.append("📈 Снятие FVG на MTF\n");
        }
        if (setup.isFvgHtfGrab()) {
            caption.append("📈 Снятие FVG на HTF\n");
        }
        if (setup.isSweepLiquidity()) {
            caption.append("📈 Liquidity Sweep на MTF\n");
        }
        if (setup.isSweepHtfLiquidity()) {
            caption.append("📈 Liquidity Sweep на HTF\n");
        }
        caption.append("📉 *LTF подтверждение* – BOS\n\n");
        caption.append(String.format("⏰ Время: `%s` UTC\n", dateStr));
        caption.append("\n🤖 *Smart Money Bot*");

        List<InputMedia> mediaList = new ArrayList<>();
        addPhoto(mediaList, htfScreenshot, caption.toString());
        addPhoto(mediaList, middleScreenshot, null);
        addPhoto(mediaList, ltfScreenshot, null);

        sendMediaOrText(mediaList, caption.toString());
    }

    public void sendFulfilledSignal(Signal signal, long time) {
        String dateStr = TIME_FMT.format(Instant.ofEpochMilli(time));
        Order order = signal.getOrder();
        StringBuilder caption = new StringBuilder();
        if (order.isFulfilledByBU()) {
            caption.append("⏰ Сделка закрыта: Без Убыток\n")
                    .append(String.format("Пара: %s", order.getSetup().getTradingPare()))
                    .append(String.format("Направление: %s", order.getType().name()))
                    .append(String.format("Вход: `%.4f`", order.getEnterPrice()))
                    .append(String.format("БУ: `%.4f`", order.getEnterPrice()))
                    .append(String.format("Time: %s", dateStr));
        } else if (order.isFulfilledByStop()) {
            caption.append("❌ Сделка закрыта: STOP LOSS\n")
                    .append(String.format("Пара: %s", order.getSetup().getTradingPare()))
                    .append(String.format("Направление: %s", order.getType().name()))
                    .append(String.format("Вход: `%.4f`", order.getEnterPrice()))
                    .append(String.format("Цена закрытия: `%.4f`", order.getStopLoss()))
                    .append(String.format("Time: %s", dateStr));
        } else if (order.isFulfilledByTake()) {
            caption.append("✅ Сделка закрыта (пропущено): TAKE PROFIT\n")
                    .append(String.format("Пара: %s", order.getSetup().getTradingPare()))
                    .append(String.format("Направление: %s", order.getType().name()))
                    .append(String.format("Вход: `%.4f`", order.getEnterPrice()))
                    .append(String.format("TP: `%.4f`", order.getTakeProfit()))
                    .append(String.format("Time: %s", dateStr));
        }
        sendTextOnly(caption.toString());
    }

    public void sendPartialCloseSignal(Signal signal, long time) {
        String dateStr = TIME_FMT.format(Instant.ofEpochMilli(time));
        Order order = signal.getOrder();
        // TODO: реализовать частичное закрытие
    }

    public void sendNewSignal(Signal signal, byte[] htfScreenshot, byte[] middleScreenshot, byte[] ltfScreenshot, long timestampMillis) {
        sendSignal(signal, htfScreenshot, middleScreenshot, ltfScreenshot, timestampMillis);
    }

    private void addPhoto(List<InputMedia> list, byte[] img, String caption) {
        if (img == null || img.length == 0) return;
        InputMediaPhoto photo = new InputMediaPhoto();
        photo.setMedia(new ByteArrayInputStream(img), "chart.png");
        if (caption != null) {
            photo.setCaption(caption);
            photo.setParseMode("Markdown");
        }
        list.add(photo);
    }

    /**
     * Отправляет либо один фото (SendPhoto), либо группу (SendMediaGroup), либо только текст.
     */
    private void sendMediaOrText(List<InputMedia> mediaList, String fallbackText) {
        if (mediaList.isEmpty()) {
            sendTextOnly(fallbackText);
            return;
        }

        if (mediaList.size() == 1) {
            // Отправляем как одиночное фото
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            InputMediaPhoto inputMediaPhoto = (InputMediaPhoto) mediaList.get(0);
            sendPhoto.setPhoto(new InputFile(inputMediaPhoto.getNewMediaStream(), inputMediaPhoto.getMediaName()));
            sendPhoto.setCaption(mediaList.get(0).getCaption());
            sendPhoto.setParseMode(mediaList.get(0).getParseMode());
            try {
                execute(sendPhoto);
                System.out.println("✅ Отправлено одиночное фото");
            } catch (TelegramApiException e) {
                System.err.println("Ошибка отправки фото: " + e.getMessage());
                sendTextOnly(fallbackText);
            }
        } else if (mediaList.size() >= 2 && mediaList.size() <= 10) {
            // Отправляем группой
            SendMediaGroup mediaGroup = new SendMediaGroup();
            mediaGroup.setChatId(chatId);
            mediaGroup.setMedias(mediaList);
            try {
                execute(mediaGroup);
                System.out.println("✅ Отправлен альбом с " + mediaList.size() + " фото");
            } catch (TelegramApiException e) {
                System.err.println("Ошибка отправки альбома: " + e.getMessage());
                sendTextOnly(fallbackText);
            }
        } else {
            // >10 – по документации нельзя, отправим только текст
            System.err.println("Слишком много медиа (" + mediaList.size() + "), отправляю только текст");
            sendTextOnly(fallbackText);
        }
    }

    public void sendTextOnly(String text) {
        SendMessage msg = new SendMessage(chatId, text);
        msg.setParseMode("Markdown");
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendTestPhoto(byte[] testTfScreenshot) {
        StringBuilder caption = new StringBuilder();
        caption.append("\n📊 *Test time frame Screen:*\n");

        List<InputMedia> mediaList = new ArrayList<>();
        addPhoto(mediaList, testTfScreenshot, caption.toString());

        sendMediaOrText(mediaList, caption.toString());
    }
}