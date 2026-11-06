package logbook.messageflow.web;

import java.io.StringReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.jetty.JettyRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import jakarta.json.Json;
import jakarta.json.stream.JsonParser;
import logbook.messageflow.queue.QueueHolder;

/**
 * 元 logbook-kai-messageflow の WebSocketConfig を移植したものです。
 *
 * Spring Boot の自動設定ではなく、master が使用する Jetty 9.4 上で
 * SockJS + WebSocket を動かすため、JettyRequestUpgradeStrategy を明示的に指定しています
 * (Spring 5.3 と Jetty 9.4.57 の組み合わせで動作確認済み)。
 */
@Configuration
@EnableWebSocket
public class MessageFlowWebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LogManager.getLogger(MessageFlowWebSocketConfig.class);
    private static final Logger trafficLogger = LogManager.getLogger("logbook.traffic");
    private static org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler sharedTaskScheduler;

    /**
     * アプリケーション終了時にTaskSchedulerを明示的に破棄する。
     * (daemon指定済みのためJVM終了はブロックしないが、行儀として停止させる)
     */
    public static void shutdownScheduler() {
        if (sharedTaskScheduler != null) {
            sharedTaskScheduler.shutdown();
        }
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Jetty WebSocket実装のデフォルト最大メッセージサイズ(64KB)では
        // 画像・大きめのJSONデータを受信しきれず切断されてしまうため、
        // 上限を引き上げる (20MB)。
        org.eclipse.jetty.websocket.api.WebSocketPolicy policy =
                org.eclipse.jetty.websocket.api.WebSocketPolicy.newServerPolicy();
        policy.setMaxTextMessageSize(20 * 1024 * 1024);
        policy.setMaxBinaryMessageSize(20 * 1024 * 1024);
        policy.setMaxTextMessageBufferSize(20 * 1024 * 1024);
        policy.setMaxBinaryMessageBufferSize(20 * 1024 * 1024);

        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler(new JettyRequestUpgradeStrategy(policy));

        // 重要: withSockJS() はデフォルトだと内部で「非デーモンスレッド」の
        // TaskScheduler (SockJS-N という名前のスレッド) を自動生成してしまい、
        // これがmasterのウィンドウを閉じてもjavaプロセスが終了しない原因になる。
        // 必ずデーモンスレッドのTaskSchedulerを明示的に渡すこと。
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(4);
        taskScheduler.setThreadNamePrefix("messageflow-sockjs-");
        taskScheduler.setDaemon(true);
        taskScheduler.initialize();
        sharedTaskScheduler = taskScheduler;

        registry.addHandler(new ApiWebSocketHandler(), "/api")
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setTaskScheduler(taskScheduler)
                .setHeartbeatTime(20000)
                .setWebSocketEnabled(true)
                .setStreamBytesLimit(32 * 1024 * 1024);

        registry.addHandler(new ImageWebSocketHandler(), "/image")
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setTaskScheduler(taskScheduler)
                .setHeartbeatTime(20000)
                .setWebSocketEnabled(true)
                .setStreamBytesLimit(32 * 1024 * 1024);

        registry.addHandler(new ImageJsonWebSocketHandler(), "/imageJson")
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setTaskScheduler(taskScheduler)
                .setHeartbeatTime(20000)
                .setWebSocketEnabled(true)
                .setStreamBytesLimit(32 * 1024 * 1024);
    }

    private static class ApiWebSocketHandler extends MyWebSocketHandler {
        ApiWebSocketHandler() {
            super(WebSocketStatus.API);
        }

        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            handleAndOffer(session, message, QueueHolder.getInstance().getAPIQueue(), "API");
        }
    }

    private static class ImageWebSocketHandler extends MyWebSocketHandler {
        ImageWebSocketHandler() {
            super(WebSocketStatus.IMAGE);
        }

        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            handleAndOffer(session, message, QueueHolder.getInstance().getImageQueue(), "Image");
        }
    }

    private static class ImageJsonWebSocketHandler extends MyWebSocketHandler {
        ImageJsonWebSocketHandler() {
            super(WebSocketStatus.IMAGE_JSON);
        }

        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            handleAndOffer(session, message, QueueHolder.getInstance().getImageJsonQueue(), "ImageJson");
        }
    }

    private static void handleAndOffer(WebSocketSession session, TextMessage message,
            java.util.Queue<String> queue, String label) throws Exception {
        String payload = message.getPayload();
        if (logger.isDebugEnabled()) {
            logger.debug(label + " received message size : " + payload.length());
        }

        if (QueueHolder.getInstance().isShuttingDown()) {
            logger.warn(label + " queue is shutting down. Skipping message processing.");
            session.sendMessage(new TextMessage("shutdown"));
            return;
        }

        payload = addReceivedTime(payload);

        String uri = extractUri(payload);

        queue.offer(payload);

        trafficLogger.info("WebSocket受信(" + label + "): " + uri + " 受信バイト数 : " + payload.length());
        session.sendMessage(new TextMessage("ok"));
    }

    private static class MyWebSocketHandler extends TextWebSocketHandler {

        private final WebSocketStatus status;

        MyWebSocketHandler(WebSocketStatus status) {
            this.status = status;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            this.status.increment();
            logger.info("Connection established: " + session.getId());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            this.status.decrement();
            logger.info("Connection closed: " + session.getId() + ", Status: " + status);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            logger.error("Transport error: " + exception.getMessage());
        }
    }

    /**
     * ログ出力用に、payload のトップレベルの "uri" の値だけを取り出す。
     * 画像やマスターデータは数MBになるため、全体を解析せずストリーミングで読み、
     * 見つかった時点で打ち切る (Chrome拡張は uri をボディより前に送ってくる)。
     */
    private static String extractUri(String payload) {
        try (JsonParser parser = Json.createParser(new StringReader(payload))) {
            int depth = 0;
            while (parser.hasNext()) {
                JsonParser.Event event = parser.next();
                switch (event) {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    if (depth == 1 && "uri".equals(parser.getString())) {
                        if (parser.next() == JsonParser.Event.VALUE_STRING) {
                            return parser.getString();
                        }
                        return "unknown";
                    }
                    break;
                default:
                    break;
                }
            }
        } catch (Exception e) {
            // ログ用のため無視
        }
        return "unknown";
    }

    /**
     * payloadに受信時間を追加する
     */
    private static String addReceivedTime(String payload) {
        if (payload == null) {
            return null;
        }
        String trimmed = payload.trim();
        if (trimmed.endsWith("}")) {
            return trimmed.substring(0, trimmed.length() - 1) + ",\"receivedTime\":" + System.currentTimeMillis() + "}";
        }
        return payload;
    }
}
