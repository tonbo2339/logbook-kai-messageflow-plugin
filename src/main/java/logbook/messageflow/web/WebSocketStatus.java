package logbook.messageflow.web;

import java.util.concurrent.atomic.AtomicInteger;

public final class WebSocketStatus {
    public static final WebSocketStatus API = new WebSocketStatus("API");
    public static final WebSocketStatus IMAGE = new WebSocketStatus("Image");
    public static final WebSocketStatus IMAGE_JSON = new WebSocketStatus("ImageJson");

    /** 接続数が変わったときに呼ばれる (切断の通知用)。どのスレッドから呼ばれるかは不定。 */
    private static volatile Runnable changeListener;

    private final String name;
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    private WebSocketStatus(String name) {
        this.name = name;
    }

    public static void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    private static void fireChanged() {
        Runnable listener = changeListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                // 通知側の失敗で、接続の処理を止めない
            }
        }
    }

    public void increment() {
        this.connectionCount.incrementAndGet();
        fireChanged();
    }

    public void decrement() {
        this.connectionCount.decrementAndGet();
        fireChanged();
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
