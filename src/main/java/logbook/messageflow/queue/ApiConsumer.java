package logbook.messageflow.queue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.json.JsonObject;
import logbook.messageflow.cache.CacheHolder;
import logbook.messageflow.config.MessageFlowConfig;
import logbook.messageflow.util.DateTimeUtils;
import logbook.messageflow.util.ProxyHttpClient;

public class ApiConsumer extends BaseConsumer {

    private static final Logger logger = LogManager.getLogger(ApiConsumer.class);

    public ApiConsumer(BlockingQueue<String> queue, QueueName queueName) {
        super(queue, queueName);
    }

    @Override
    protected String expectedMethod() {
        return "POST";
    }

    @Override
    protected String label() {
        return "API";
    }

    @Override
    protected String responseContentType() {
        return "text/plain";
    }

    @Override
    protected byte[] decodeResponseBody(String responseBody) {
        // "svdata={...}" 形式の文字列
        return responseBody.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void sendViaProxy(JsonObject json, String uri, String responseBody) throws Exception {
        String postData = getJsonToString(json, "postData");

        String hashKey = getQueueName() + DateTimeUtils.getCurrentTimestamp();
        CacheHolder<String, String> cacheHolder = CacheHolder.getInstance(QueueName.API);
        cacheHolder.put(hashKey, responseBody);

        MessageFlowConfig config = MessageFlowConfig.getInstance();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", hashKey); // 航海日誌改のProxy内で上書きされるがデバッグ用
        headers.put("Origin", config.getKoukainissikaiMessageFlowOrigin());
        headers.put("Host", config.getKoukainissikaiMessageFlowHost());
        headers.put("Proxy-Connection", "keep-alive");
        headers.put("x-koukainissikai", hashKey);
        headers.put("x-koukainissikai-receivedat", getJsonToString(json, "receivedTime"));
        headers.put("x-koukainissikai-requestat", String.valueOf(System.currentTimeMillis()));

        String response = ProxyHttpClient.post(uri, postData, headers);
        if (logger.isDebugEnabled()) {
            logger.debug("response : " + response);
        }
    }
}
