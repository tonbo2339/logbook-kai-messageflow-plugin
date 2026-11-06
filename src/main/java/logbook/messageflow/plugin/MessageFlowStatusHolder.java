package logbook.messageflow.plugin;

import logbook.messageflow.web.WebSocketStatus;

/**
 * タイトルバー表示・メニュー表示の両方から参照する、現在の接続状態です。
 */
public final class MessageFlowStatusHolder {

    private static volatile long connectedSince = -1;
    private static volatile boolean serverStarted = false;

    private MessageFlowStatusHolder() {
    }

    public static void setServerStarted(boolean started) {
        serverStarted = started;
    }

    public static boolean isServerStarted() {
        return serverStarted;
    }

    public static boolean isConnected() {
        return serverStarted && WebSocketStatus.anyConnected();
    }

    /** 接続が確立された最初のタイミングを記録します (経過時間表示用)。 */
    public static void onConnectionObserved() {
        if (isConnected() && connectedSince < 0) {
            connectedSince = System.currentTimeMillis();
        } else if (!isConnected()) {
            connectedSince = -1;
        }
    }

    public static long getConnectedSince() {
        return connectedSince;
    }
}
