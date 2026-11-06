package logbook.messageflow.dispatch;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import logbook.net.RequestMetaData;

/**
 * Chrome拡張から受け取ったリクエスト情報を、master の {@link RequestMetaData} として提供します。
 *
 * パラメータの解析方法は master の {@code logbook.internal.net.RequestMetaDataWrapper#set(InputStream)}
 * (リクエストボディ全体を URL デコードしてから '&' と '=' で分割) に合わせている。
 */
final class CapturedRequest implements RequestMetaData {

    private final String method;
    private final String requestURI;
    private final String queryString;
    private final String contentType;
    private final byte[] body;
    private final Map<String, List<String>> parameterMap;
    private final Map<String, String> headers;

    CapturedRequest(String method, String requestURI, String queryString, String contentType, String body,
            Map<String, String> headers) {
        this.method = method;
        this.requestURI = requestURI;
        this.queryString = queryString;
        this.contentType = contentType;
        this.body = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
        this.parameterMap = body != null ? parseParameters(body) : Collections.emptyMap();
        Map<String, String> lower = new LinkedHashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        this.headers = lower;
    }

    private static Map<String, List<String>> parseParameters(String body) {
        String decoded;
        try {
            decoded = URLDecoder.decode(body, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            decoded = "";
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String part : decoded.split("&")) {
            String key;
            String value;
            int idx = part.indexOf('=');
            if (idx > 0) {
                key = part.substring(0, idx);
                value = part.substring(idx + 1);
            } else {
                key = part;
                value = null;
            }
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    @Override
    public Map<String, List<String>> getParameterMap() {
        return this.parameterMap;
    }

    @Override
    public String getQueryString() {
        return this.queryString;
    }

    @Override
    public String getRequestURI() {
        return this.requestURI;
    }

    @Override
    public Optional<InputStream> getRequestBody() {
        // 呼び出しごとに新しいストリームを返す (複数のリスナーが読んでも壊れないように)
        return this.body != null ? Optional.of(new ByteArrayInputStream(this.body)) : Optional.empty();
    }

    @Override
    public Optional<String> getHeader(String name) {
        return name != null ? Optional.ofNullable(this.headers.get(name.toLowerCase())) : Optional.empty();
    }
}
