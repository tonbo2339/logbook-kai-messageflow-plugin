package logbook.messageflow.dispatch;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import logbook.core.LogBookCoreServices;
import logbook.listener.ContentListenerSpi;

/**
 * Chrome拡張から受け取った通信内容を、master のプロキシを経由せず、
 * master の {@link ContentListenerSpi} 実装 (APIListener / ImageListener 等) へ直接渡します。
 *
 * 従来 (元 logbook-kai-messageflow と同じ方式) は、受け取った内容を一旦キャッシュし、
 * master のプロキシへHTTPで送って自分自身へ折り返させることで master に処理させていた。
 * 本プラグインは master と同一プロセスで動くため、master の公開SPI
 * ({@code logbook.core.LogBookCoreServices}, {@code logbook.listener.ContentListenerSpi},
 * {@code logbook.net.*}) を使えば、その折り返しを省略できる。
 *
 * master の {@code ReverseProxyServlet#invoke} と同様に、全リスナーに対して
 * test() が true のものへ accept() を呼び出す。
 */
public final class DirectDispatcher {

    private static final Logger logger = LogManager.getLogger(DirectDispatcher.class);

    private static volatile List<ContentListenerSpi> listeners;
    private static volatile Boolean available;

    private DirectDispatcher() {
    }

    /**
     * master のSPIが利用できるか (古い master 等で利用できない場合は false)。
     */
    public static boolean isAvailable() {
        Boolean a = available;
        if (a == null) {
            synchronized (DirectDispatcher.class) {
                if (available == null) {
                    try {
                        getListeners();
                        available = true;
                    } catch (Throwable e) {
                        logger.warn("航海日誌改のリスナーを取得できないため、直接受け渡しは利用できません (HTTP経由で動作します)", e);
                        available = false;
                    }
                }
                a = available;
            }
        }
        return a;
    }

    private static List<ContentListenerSpi> getListeners() {
        List<ContentListenerSpi> l = listeners;
        if (l == null) {
            synchronized (DirectDispatcher.class) {
                if (listeners == null) {
                    listeners = LogBookCoreServices.getServiceProviders(ContentListenerSpi.class)
                            .collect(Collectors.toList());
                    logger.info("直接受け渡し先のリスナー: " + listeners.stream()
                            .map(x -> x.getClass().getName()).collect(Collectors.joining(", ")));
                }
                l = listeners;
            }
        }
        return l;
    }

    /**
     * 通信内容を master のリスナーへ渡します。
     *
     * @param method          HTTPメソッド
     * @param uri             リクエストURI (パス部分)
     * @param queryString     クエリ文字列 (先頭の '?' を除く、無ければ null)
     * @param requestBody     リクエストボディ (POSTデータ、無ければ null)
     * @param requestHeaders  リクエストヘッダー
     * @param contentType     レスポンスの Content-Type
     * @param responseBody    レスポンスボディ
     * @param responseHeaders レスポンスヘッダー
     * @return 処理したリスナーの数
     */
    public static int dispatch(String method, String uri, String queryString, String requestBody,
            Map<String, String> requestHeaders, String contentType, byte[] responseBody,
            Map<String, String> responseHeaders) {
        String requestContentType = requestBody != null ? "application/x-www-form-urlencoded" : null;
        CapturedRequest request = new CapturedRequest(method, uri, queryString, requestContentType, requestBody,
                requestHeaders);
        CapturedResponse response = new CapturedResponse(contentType, responseBody, responseHeaders);

        int count = 0;
        for (ContentListenerSpi listener : getListeners()) {
            try {
                if (listener.test(request)) {
                    // 呼び出し元 (Queueごとの単一スレッド) で同期的に呼ぶことで、受信順を保つ。
                    // APIListener は内部で master のスレッドプールへ処理を投げるため、ここで長く止まることはない。
                    listener.accept(request, response);
                    count++;
                }
            } catch (Exception e) {
                logger.warn("リスナーの処理中に例外が発生しました: " + listener.getClass().getName() + " uri=" + uri, e);
            }
        }
        return count;
    }
}
