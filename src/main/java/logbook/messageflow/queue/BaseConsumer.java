package logbook.messageflow.queue;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import logbook.messageflow.config.MessageFlowConfig;
import logbook.messageflow.dispatch.DirectDispatcher;

/**
 * Queueに溜まったChrome拡張からの通信内容を1件ずつ取り出し、master へ渡します。
 *
 * 渡し方は2通り ({@link MessageFlowConfig#isDirectDispatch()} で切り替え):
 * <ul>
 * <li>直接 (既定): master のリスナーを直接呼び出す ({@link DirectDispatcher})</li>
 * <li>HTTP経由 (互換モード): 元 logbook-kai-messageflow と同じく、master のプロキシへ
 * HTTPで送って自分自身のサーバーへ折り返させる ({@link #sendViaProxy})</li>
 * </ul>
 */
public abstract class BaseConsumer implements Runnable {

    private static final Logger logger = LogManager.getLogger(BaseConsumer.class);
    protected static final Logger trafficLogger = LogManager.getLogger("logbook.traffic");

    /** Queueが空のときに次のデータを待つ時間 */
    private static final long POLL_TIMEOUT_MILLIS = 100;

    private final QueueName queueName;
    private final BlockingQueue<String> queue;

    private volatile boolean running = true;
    private volatile boolean isShutDown = false;

    protected BaseConsumer(BlockingQueue<String> queue, QueueName queueName) {
        this.queue = queue;
        this.queueName = queueName;
    }

    /**
     * stop()か、QueueHolder.getInstance().setShuttingDown(true)の検知後、
     * Queueが空で終了します
     */
    @Override
    public void run() {
        try {
            int i = 0;
            while (this.running) {
                if (!this.isShutDown && QueueHolder.getInstance().isShuttingDown()) {
                    logger.info(this.queueName.getQueueName() + " ConsumerのShutdown処理を検知しました Shutdown処理を開始します");
                    this.isShutDown = true;
                }
                // データがあれば即座に処理し、空のときだけ最大100ms待つ
                // (元実装は1件処理するごとに100ms休んでいたため、1秒に最大10件しか処理できなかった)
                String data = this.queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (data != null) {
                    QueueStatus.incrementQueueCount(this.queueName);
                    try {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Queueからの取得データ : " + data);
                        }
                        JsonObject json;
                        try (JsonReader jsonreader = Json.createReader(new StringReader(data))) {
                            json = jsonreader.readObject();
                        }
                        sendData(json);
                    } catch (Exception e) {
                        logger.error(this.queueName + " : " + data, e);
                    }
                } else {
                    QueueStatus.ensureCurrentMinuteExists(this.queueName);
                    if (this.isShutDown) {
                        logger.info(this.queueName + " ConsumerのQueueが空を確認 終了します");
                        this.running = false;
                    } else {
                        i++;
                        if (i % 100 == 0) {
                            logger.debug(this.queueName + " is empty...");
                            i = 0;
                        }
                    }
                }
            }
            logger.info(this.queueName + " Consumerを終了しました");
        } catch (InterruptedException e) {
            logger.error(this.queueName + " Consumerを強制終了しました");
            Thread.currentThread().interrupt();
        }
    }

    private void sendData(JsonObject json) {
        String method = getJsonToString(json, "method");
        String uri = getJsonToString(json, "uri");
        if (!expectedMethod().equals(method)) {
            return;
        }
        String responseBody = getJsonToString(json, "responseBody");

        trafficLogger.info("通信処理開始(" + label() + "): " + uri);
        try {
            if (MessageFlowConfig.getInstance().isDirectDispatch() && DirectDispatcher.isAvailable()) {
                int handled = dispatchDirect(json, method, uri, responseBody);
                trafficLogger.info("通信処理終了(" + label() + ", 直接): " + uri
                        + (handled == 0 ? " (処理対象のリスナーなし)" : ""));
            } else {
                sendViaProxy(json, uri, responseBody);
                trafficLogger.info("通信処理終了(" + label() + "): " + uri);
            }
        } catch (Exception e) {
            logger.error(label() + "通信に失敗しました: " + uri, e);
        }
    }

    private int dispatchDirect(JsonObject json, String method, String uri, String responseBody) {
        String now = String.valueOf(System.currentTimeMillis());
        // master の APIListener が性能ログ (受信→処理開始→応答の所要時間) に使うヘッダー
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("x-koukainissikai-receivedat", getJsonToString(json, "receivedTime"));
        requestHeaders.put("x-koukainissikai-requestat", now);
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put("x-koukainissikai-responseat", now);

        String queryString = getJsonToString(json, "queryString");
        if (queryString.startsWith("?")) {
            queryString = queryString.substring(1);
        }
        String postData = "POST".equals(method) ? getJsonToString(json, "postData") : null;
        byte[] body = decodeResponseBody(responseBody);
        if (logger.isDebugEnabled()) {
            logger.debug(label() + " 直接受け渡し uri=" + uri + " size=" + body.length);
        }
        return DirectDispatcher.dispatch(method, uri, queryString.isEmpty() ? null : queryString, postData,
                requestHeaders, responseContentType(), body, responseHeaders);
    }

    /** 処理対象のHTTPメソッド */
    protected abstract String expectedMethod();

    /** ログ出力用の種別名 */
    protected abstract String label();

    /** master へ渡すレスポンスの Content-Type */
    protected abstract String responseContentType();

    /** Chrome拡張から受け取ったレスポンスボディ文字列を、実際のバイト列に変換します */
    protected abstract byte[] decodeResponseBody(String responseBody);

    /** HTTP経由 (互換モード) で master へ渡します */
    protected abstract void sendViaProxy(JsonObject json, String uri, String responseBody) throws Exception;

    protected QueueName getQueueName() {
        return this.queueName;
    }

    public void stop() {
        logger.info(this.queueName + " Consumerのshutdownを開始");
        this.isShutDown = true;
    }

    protected String getJsonToString(JsonObject json, String key) {
        JsonValue jsonVal = json.get(key);
        if (jsonVal == null) {
            return "";
        }
        if (jsonVal instanceof JsonString jsonString) {
            return jsonString.getString();
        }
        return jsonVal.toString();
    }
}
