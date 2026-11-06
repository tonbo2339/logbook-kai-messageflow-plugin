package logbook.messageflow.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FutureResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;

import logbook.messageflow.config.MessageFlowConfig;

/**
 * masterのプロキシ(ReverseProxyServlet)経由でmessageflow自身へ折り返す通信を行います。
 *
 * 重要: masterのReverseProxyServletは、リクエストの "Host" ヘッダーの値を元に
 * 転送先URLを組み立てる実装になっている
 * ({@code logbook.internal.proxy.ProxyServlet#rewriteURI}、デフォルト実装は
 * {@code request.getRequestURL()} をそのまま使う)。
 * そのため、Hostヘッダーを messageflow 自身 (localhost:<messageflowPort>) に
 * 固定して送ることで、master → messageflow への「折り返し」を発生させ、
 * messageflow側の {@link logbook.messageflow.web.ServerController} が
 * x-koukainissikai ヘッダーのキーでキャッシュ済みレスポンスを返す、という
 * 一連の流れが成立する。
 *
 * java.net.http.HttpClient (JDK標準) は "Host" ヘッダーを制限ヘッダーとして
 * アプリケーションコードから設定させないため使用できない。
 * masterが実行時に提供する Jetty の HttpClient (jetty-client) を代わりに使う。
 */
public final class ProxyHttpClient {

    /**
     * レスポンスをバッファリングする上限 (32MB、元実装の WebClient maxInMemorySize に合わせる)。
     * {@link Request#send()} のデフォルト上限は2MBで、api_start2/getData や大きめの画像が
     * "Buffering capacity 2097152 exceeded" で失敗し、masterでの処理まで中断されていた。
     */
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

    private static volatile HttpClient client;

    private ProxyHttpClient() {
    }

    private static synchronized HttpClient getClient() {
        if (client == null) {
            // 重要: HttpClientはExecutor/Schedulerを明示指定しないと、
            // 内部で非デーモンスレッドのQueuedThreadPool/Schedulerを
            // 自動生成してしまい、アプリを閉じてもjavaプロセスが
            // 終了しなくなる。必ずdaemon=trueで明示的に渡すこと。
            QueuedThreadPool executor = new QueuedThreadPool();
            executor.setName("messageflow-http-client");
            executor.setDaemon(true);

            HttpClient c = new HttpClient();
            c.setExecutor(executor);
            c.setScheduler(new ScheduledExecutorScheduler("messageflow-http-client-scheduler", true));
            c.setName("messageflow-http-client");
            try {
                c.start();
            } catch (Exception e) {
                throw new IllegalStateException("Jetty HttpClientの起動に失敗しました", e);
            }
            client = c;
        }
        return client;
    }

    /** アプリケーション終了時に呼び出すこと。 */
    public static void shutdown() {
        HttpClient c = client;
        if (c != null) {
            try {
                c.stop();
            } catch (Exception e) {
                // 終了時なので無視
            }
        }
    }

    private static String resolveUrl(String uri) {
        if (uri != null && (uri.startsWith("http://") || uri.startsWith("https://"))) {
            return uri;
        }
        int proxyPort = MessageFlowConfig.getInstance().getKoukainissikaiPort();
        String path = (uri != null && uri.startsWith("/")) ? uri : "/" + uri;
        return "http://localhost:" + proxyPort + path;
    }

    private static void applyHeaders(Request request, Map<String, String> headers) {
        headers.forEach((key, value) -> {
            if ("host".equalsIgnoreCase(key)) {
                // Jetty HttpClientは接続先ホストとは別にHostヘッダーを
                // 明示的に上書きできる (これがmasterへの折り返しの要)。
                request.header(HttpHeader.HOST, value);
            } else {
                request.header(key, value);
            }
        });
    }

    private static ContentResponse send(Request request) throws Exception {
        FutureResponseListener listener = new FutureResponseListener(request, MAX_RESPONSE_BYTES);
        request.send(listener);
        ContentResponse response;
        try {
            response = listener.get();
        } catch (ExecutionException e) {
            request.abort(e.getCause());
            throw e;
        }
        // 200以外 (キャッシュミスの404等) は master 側で処理されないため、失敗として扱う
        if (response.getStatus() != 200) {
            throw new IllegalStateException("HTTP " + response.getStatus() + " " + request.getURI());
        }
        return response;
    }

    public static String post(String uri, String body, Map<String, String> headers) throws Exception {
        Request request = getClient().newRequest(resolveUrl(uri))
                .method(HttpMethod.POST)
                .timeout(10, TimeUnit.SECONDS);
        applyHeaders(request, headers);
        if (body != null) {
            request.content(new StringContentProvider(body, StandardCharsets.UTF_8));
        }
        ContentResponse response = send(request);
        return response.getContentAsString();
    }

    public static String getAsString(String uri, Map<String, String> headers) throws Exception {
        Request request = getClient().newRequest(resolveUrl(uri))
                .method(HttpMethod.GET)
                .timeout(10, TimeUnit.SECONDS);
        applyHeaders(request, headers);
        ContentResponse response = send(request);
        return response.getContentAsString();
    }

    public static byte[] getAsBytes(String uri, Map<String, String> headers) throws Exception {
        Request request = getClient().newRequest(resolveUrl(uri))
                .method(HttpMethod.GET)
                .timeout(10, TimeUnit.SECONDS);
        applyHeaders(request, headers);
        ContentResponse response = send(request);
        return response.getContent();
    }
}
