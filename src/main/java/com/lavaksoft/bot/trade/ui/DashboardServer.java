package com.lavaksoft.bot.trade.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Minimal Java-native dashboard. It is intentionally isolated from strategy code.
 */
public class DashboardServer {
    private final DashboardStateStore store;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;

    public DashboardServer(int port, DashboardStateStore store) throws IOException {
        this.store = store;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/state", this::state);
        server.createContext("/app.js", e -> resource(e, "/dashboard/app.js", "application/javascript; charset=utf-8"));
        server.createContext("/styles.css", e -> resource(e, "/dashboard/styles.css", "text/css; charset=utf-8"));
        server.createContext("/", e -> resource(e, "/dashboard/index.html", "text/html; charset=utf-8"));
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    private void state(HttpExchange exchange) throws IOException {
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(store.get());
        send(exchange, 200, "application/json; charset=utf-8", bytes);
    }

    private void resource(HttpExchange exchange, String path, String contentType) throws IOException {
        try (InputStream in = DashboardServer.class.getResourceAsStream(path)) {
            if (in == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(exchange, 200, contentType, in.readAllBytes());
        }
    }

    private static void send(HttpExchange exchange, int code, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
