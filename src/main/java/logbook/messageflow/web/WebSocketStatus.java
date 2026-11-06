package logbook.messageflow.web;

import java.util.concurrent.atomic.AtomicInteger;

public final class WebSocketStatus {
    public static final WebSocketStatus API = new WebSocketStatus("API");
    public static final WebSocketStatus IMAGE = new WebSocketStatus("Image");
    public static final WebSocketStatus IMAGE_JSON = new WebSocketStatus("ImageJson");

    private final String name;
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    private WebSocketStatus(String name) {
        this.name = name;
    }

    public void increment() {
        this.connectionCount.incrementAndGet();
    }

    public void decrement() {
        this.connectionCount.decrementAndGet();
    }

    public int getCount() {
        return this.connectionCount.get();
    }

    public String getName() {
        return this.name;
    }

    /** いずれかのWebSocketで1件以上接続があるか */
    public static boolean anyConnected() {
        return API.getCount() > 0 || IMAGE.getCount() > 0 || IMAGE_JSON.getCount() > 0;
    }
}
