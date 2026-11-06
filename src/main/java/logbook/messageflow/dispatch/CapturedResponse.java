package logbook.messageflow.dispatch;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import logbook.net.ResponseMetaData;

/**
 * Chrome拡張から受け取ったレスポンス情報を、master の {@link ResponseMetaData} として提供します。
 * ボディはChrome拡張側で展開済みのため、gzip の展開は行わない。
 */
final class CapturedResponse implements ResponseMetaData {

    private final String contentType;
    private final byte[] body;
    private final Map<String, String> headers;

    CapturedResponse(String contentType, byte[] body, Map<String, String> headers) {
        this.contentType = contentType;
        this.body = body;
        Map<String, String> lower = new LinkedHashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        this.headers = lower;
    }

    @Override
    public int getStatus() {
        // master は HTTP 200 のレスポンスだけをリスナーに渡すため、それに合わせる
        return 200;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public Optional<InputStream> getResponseBody() {
        // 呼び出しごとに新しいストリームを返す (APIListener は例外時にボディを読み直すため)
        return Optional.of(new ByteArrayInputStream(this.body));
    }

    @Override
    public Optional<String> getHeader(String name) {
        return name != null ? Optional.ofNullable(this.headers.get(name.toLowerCase())) : Optional.empty();
    }
}
