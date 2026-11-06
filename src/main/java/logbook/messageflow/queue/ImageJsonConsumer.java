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

public class ImageJsonConsumer extends BaseConsumer {

    private static final Logger logger = LogManager.getLogger(ImageJsonConsumer.class);

    public ImageJsonConsumer(BlockingQueue<String> queue, QueueName queueName) {
        super(queue, queueName);
    }

    @Override
    protected String expectedMethod() {
        return "GET";
    }

    @Override
    protected String label() {
        return "ImageJson";
    }

    @Override
    protected String responseContentType() {
        return "application/json";
    }

    @Override
    protected byte[] decodeResponseBody(String responseBody) {
        return responseBody.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void sendViaProxy(JsonObject json, String uri, String responseBody) throws Exception {
        String hashKey = getQueueName() + DateTimeUtils.getCurrentTimestamp();
        CacheHolder<String, String> cacheHolder = CacheHolder.getInstance(QueueName.IMAGEJSON);
        cacheHolder.put(hashKey, responseBody);

        MessageFlowConfig config = MessageFlowConfig.getInstance();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", hashKey);
        headers.put("Origin", config.getKoukainissikaiMessageFlowOrigin());
        headers.put("Host", config.getKoukainissikaiMessageFlowHost());
        headers.put("Proxy-Connection", "keep-alive");
        headers.put("x-koukainissikai", hashKey);
        headers.put("x-koukainissikai-sendtype", "json");
        headers.put("x-koukainissikai-receivedat", getJsonToString(json, "receivedTime"));
        headers.put("x-koukainissikai-requestat", String.valueOf(System.currentTimeMillis()));

        String response = ProxyHttpClient.getAsString(uri, headers);
        if (logger.isDebugEnabled()) {
            logger.debug("response : " + response);
        }
    }
}
