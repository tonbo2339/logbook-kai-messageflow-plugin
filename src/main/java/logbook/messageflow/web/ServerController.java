package logbook.messageflow.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import logbook.messageflow.cache.CacheHolder;
import logbook.messageflow.queue.QueueName;

import javax.servlet.http.HttpServletRequest;

/**
 * 航海日誌改からのProxy通信を受ける、サーバーとして稼働する機能です。
 * 元 logbook-kai-messageflow の ServerController を、javax.servlet (Jetty 9.4) 向けに移植したもの。
 *
 * 通信内容を master へHTTP経由で渡す互換モード
 * ({@code MessageFlowConfig#isDirectDispatch()} が false) の場合にのみ使われる。
 *
 * ボディはすべて UTF-8 のバイト列で返す。素の Spring 5.3 は String を text/plain で返すと
 * ISO-8859-1 でエンコードし、また Content-Length を文字数で設定すると
 * マルチバイト文字を含む場合にボディが途中で切れるため (元実装は Spring Boot の UTF-8 既定に依存していた)。
 */
@RestController
public class ServerController {

    private static final Logger logger = LogManager.getLogger(ServerController.class);
    private static final Logger trafficLogger = LogManager.getLogger("logbook.traffic");

    @RequestMapping(value = "/kcsapi/**", method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE })
    public ResponseEntity<?> handleApiRequests(HttpServletRequest request) {
        String uri = request.getRequestURI();
        trafficLogger.info("Server機能処理開始(API): " + uri);
        String hashKey = request.getHeader("x-koukainissikai");

        CacheHolder<String, String> cacheHolder = CacheHolder.getInstance(QueueName.API);
        String responseBody = cacheHolder.get(hashKey);
        if (responseBody == null) {
            return cacheMiss(hashKey);
        }

        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        trafficLogger.info("Server機能処理完了(API): " + uri + " APIDataSize " + body.length);
        return ok(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8), body);
    }

    @GetMapping(value = "/kcs2/**")
    public ResponseEntity<?> handleResourcesRequests(HttpServletRequest request) {
        String uri = request.getRequestURI();
        trafficLogger.info("Server機能処理開始(Resource): " + uri);
        String hashKey = request.getHeader("x-koukainissikai");
        String sendType = request.getHeader("x-koukainissikai-sendtype");

        if ("image".equals(sendType)) {
            CacheHolder<String, String> cacheHolder = CacheHolder.getInstance(QueueName.IMAGE);
            String responseBody = cacheHolder.get(hashKey);
            if (responseBody == null) {
                return cacheMiss(hashKey);
            }
            byte[] imageBytes = Base64.getDecoder().decode(responseBody);
            trafficLogger.info("Server機能処理完了(Resource): " + uri + " ImageDataSize " + imageBytes.length);
            return ok(MediaType.IMAGE_PNG, imageBytes);
        } else if ("json".equals(sendType)) {
            CacheHolder<String, String> cacheHolder = CacheHolder.getInstance(QueueName.IMAGEJSON);
            String responseBody = cacheHolder.get(hashKey);
            if (responseBody == null) {
                return cacheMiss(hashKey);
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            trafficLogger.info("Server機能処理完了(Resource): " + uri + " ImageJsonDataSize " + body.length);
            return ok(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8), body);
        }
        return ResponseEntity.ok().body("");
    }

    private static ResponseEntity<byte[]> ok(MediaType contentType, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(body.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONNECTION, "close")
                .header("x-koukainissikai-responseat", String.valueOf(System.currentTimeMillis()))
                .contentType(contentType)
                .headers(headers)
                .body(body);
    }

    private static ResponseEntity<String> cacheMiss(String hashKey) {
        logger.warn("Cache miss for key: " + hashKey);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("No data found : " + hashKey);
    }
}
