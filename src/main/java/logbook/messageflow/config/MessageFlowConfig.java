package logbook.messageflow.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

/**
 * messageflow機能の設定を保持します。
 *
 * 元 logbook-kai-messageflow の LogBookKaiMessageFlowConfig を移植したものです。
 *
 * 設定ファイルの置き場所は、master (logbook-kai) 本体の設定ファイル
 * ({@code logbook.internal.Config}) が採用している規約
 * (実行フォルダ直下の {@code config} フォルダにJSON形式で保存) に合わせている。
 * ただし {@code logbook.internal.Config} クラス自体は "internal" パッケージ
 * (master内部実装、互換性非保証) のため直接依存はせず、置き場所と形式だけを
 * 踏襲した独自実装にしている。
 *
 * 設定の優先順位 (上ほど優先):
 *   1. システムプロパティ (-Dport=xxxx -Dkaiport=xxxx)
 *   2. config/messageflow.json
 *   3. デフォルト値
 *
 * 航海日誌改のポート (kaiport) は、明示的に指定されていなければ master の設定
 * ({@code logbook.bean.AppConfig#getListenPort()}、公開パッケージ) に自動で追従する。
 * master側でプロキシのポートを変えた環境 (8888以外) でも設定の食い違いが起きないようにするため。
 */
public final class MessageFlowConfig {

    private static final Logger logger = LogManager.getLogger(MessageFlowConfig.class);
    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("messageflow.json");
    /** master の設定からポート番号を読めなかった警告を出したか (INSTANCE より前に宣言すること) */
    private static volatile boolean masterPortWarned;

    // 重要: static フィールドは宣言順に初期化されるため、INSTANCE は
    // コンストラクタが参照する CONFIG_DIR / CONFIG_FILE より後に宣言すること。
    // 先に宣言するとコンストラクタ実行時に CONFIG_FILE が null になり、
    // NullPointerException (ExceptionInInitializerError) で初期化に失敗する。
    private static final MessageFlowConfig INSTANCE = new MessageFlowConfig();

    /** ログ出力の対象ロガー名。このパッケージ配下のクラスのログをまとめて制御する。 */
    private static final String LOG_TARGET = "logbook.messageflow";

    /** master の設定が読めない場合に使う航海日誌改のポート */
    private static final int FALLBACK_KAI_PORT = 8888;

    /** 航海日誌改ポートの明示指定。null の場合は master の設定 (listenPort) に追従する。 */
    private Integer koukainissikaiPortOverride = null;
    /** 航海日誌改MessageFlowデフォルトポート 8890 */
    private int koukainissikaiMessageFlowPort = 8890;
    /** デバッグログ (通信内容の詳細等) を出力するかどうか。デフォルトはOFF。 */
    private boolean debugLogEnabled = false;
    /**
     * 受け取った通信内容を master のリスナーへ直接渡すかどうか。デフォルトはON。
     * OFF の場合は従来通り master のプロキシへHTTPで送って折り返させる (互換モード)。
     * 切り替えは再起動不要で、次の通信から反映される。
     */
    private volatile boolean directDispatch = true;
    /**
     * Chrome拡張との接続が切れたとき (DevTools を閉じた等) に、音とポップアップで知らせるかどうか。
     * デフォルトはON。切り替えは再起動不要で、すぐに反映される。
     */
    private volatile boolean disconnectNotify = true;

    private MessageFlowConfig() {
        loadFromJsonFile();

        String port = System.getProperty("port");
        if (port != null && port.matches("\\d+")) {
            this.koukainissikaiMessageFlowPort = Integer.parseInt(port);
        }
        String kaiPort = System.getProperty("kaiport");
        if (kaiPort != null && kaiPort.matches("\\d+")) {
            this.koukainissikaiPortOverride = Integer.parseInt(kaiPort);
        }

        applyLogLevel();

        logger.info("messageflow設定: port=" + this.koukainissikaiMessageFlowPort
                + " kaiport=" + getKoukainissikaiPort() + (isKoukainissikaiPortAuto() ? "(自動)" : "")
                + " debugLog=" + this.debugLogEnabled
                + " directDispatch=" + this.directDispatch
                + " disconnectNotify=" + this.disconnectNotify);
    }

    /**
     * 現在の debugLogEnabled の値を、実際のログレベルに反映します。
     * ログレベルの変更はアプリの再起動なしにその場で反映されます。
     */
    private void applyLogLevel() {
        Configurator.setLevel(LOG_TARGET, this.debugLogEnabled ? Level.DEBUG : Level.INFO);
    }

    private void loadFromJsonFile() {
        if (!Files.exists(CONFIG_FILE)) {
            writeJsonFile();
            return;
        }
        try (InputStream in = Files.newInputStream(CONFIG_FILE);
                JsonReader reader = Json.createReader(in)) {
            JsonObject json = reader.readObject();
            if (json.containsKey("port")) {
                this.koukainissikaiMessageFlowPort = json.getInt("port", this.koukainissikaiMessageFlowPort);
            }
            if (json.containsKey("kaiport")) {
                this.koukainissikaiPortOverride = json.getInt("kaiport");
            }
            if (json.containsKey("debugLog")) {
                this.debugLogEnabled = json.getBoolean("debugLog", this.debugLogEnabled);
            }
            if (json.containsKey("directDispatch")) {
                this.directDispatch = json.getBoolean("directDispatch", this.directDispatch);
            }
            if (json.containsKey("disconnectNotify")) {
                this.disconnectNotify = json.getBoolean("disconnectNotify", this.disconnectNotify);
            }
        } catch (Exception e) {
            logger.warn(CONFIG_FILE + " の読み込みに失敗しました。デフォルト値を使用します。", e);
        }
    }

    /**
     * 現在の設定値を config/messageflow.json に保存します。
     * GUIの設定ダイアログから呼び出されることを想定しています。
     *
     * port / kaiport の変更反映には航海日誌改の再起動が必要です
     * (サーバーの動的再起動は行いません)。一方 debugLogEnabled は
     * このメソッド内でログレベルにその場で反映されるため、再起動は不要です。
     */
    public synchronized void save() {
        writeJsonFile();
        applyLogLevel();
        logger.info("messageflow設定を保存しました: port=" + this.koukainissikaiMessageFlowPort
                + " kaiport=" + getKoukainissikaiPort() + (isKoukainissikaiPortAuto() ? "(自動)" : "")
                + " debugLog=" + this.debugLogEnabled
                + " directDispatch=" + this.directDispatch
                + " disconnectNotify=" + this.disconnectNotify);
    }

    private void writeJsonFile() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            JsonObjectBuilder builder = Json.createObjectBuilder()
                    .add("port", this.koukainissikaiMessageFlowPort);
            // kaiport は明示指定がある場合のみ保存する (未保存 = master の設定に追従)
            if (this.koukainissikaiPortOverride != null) {
                builder.add("kaiport", this.koukainissikaiPortOverride);
            }
            JsonObject json = builder
                    .add("debugLog", this.debugLogEnabled)
                    .add("directDispatch", this.directDispatch)
                    .add("disconnectNotify", this.disconnectNotify)
                    .build();

            Map<String, Object> writerConfig = Map.of(JsonGenerator.PRETTY_PRINTING, true);
            JsonWriterFactory writerFactory = Json.createWriterFactory(writerConfig);
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE);
                    JsonWriter writer = writerFactory.createWriter(out, StandardCharsets.UTF_8)) {
                writer.writeObject(json);
            }
        } catch (Exception e) {
            logger.warn(CONFIG_FILE + " の書き込みに失敗しました。", e);
        }
    }

    public static MessageFlowConfig getInstance() {
        return INSTANCE;
    }

    /**
     * 航海日誌改 (master) のプロキシのポート番号を返却します。
     * 明示指定が無ければ master の設定値 (listenPort) を返します。
     */
    public int getKoukainissikaiPort() {
        Integer override = this.koukainissikaiPortOverride;
        return override != null ? override : getMasterListenPort();
    }

    /**
     * 航海日誌改のポート番号を設定します。
     * master の設定値と同じ値の場合は明示指定を解除し、以後 master の設定に追従させます。
     */
    public void setKoukainissikaiPort(int koukainissikaiPort) {
        this.koukainissikaiPortOverride = koukainissikaiPort == getMasterListenPort() ? null : koukainissikaiPort;
    }

    /** 航海日誌改のポート番号が master の設定に追従しているか */
    public boolean isKoukainissikaiPortAuto() {
        return this.koukainissikaiPortOverride == null;
    }

    private static int getMasterListenPort() {
        try {
            return logbook.bean.AppConfig.get().getListenPort();
        } catch (Throwable e) {
            // master のバージョン違い等で読めない場合は既定値を使う (警告は初回のみ)
            if (masterPortWarned) {
                return FALLBACK_KAI_PORT;
            }
            masterPortWarned = true;
            logger.warn("航海日誌改の設定からポート番号を取得できませんでした。" + FALLBACK_KAI_PORT + " を使用します。", e);
            return FALLBACK_KAI_PORT;
        }
    }

    public int getKoukainissikaiMessageFlowPort() {
        return this.koukainissikaiMessageFlowPort;
    }

    public void setKoukainissikaiMessageFlowPort(int koukainissikaiMessageFlowPort) {
        this.koukainissikaiMessageFlowPort = koukainissikaiMessageFlowPort;
    }

    public boolean isDebugLogEnabled() {
        return this.debugLogEnabled;
    }

    public void setDebugLogEnabled(boolean debugLogEnabled) {
        this.debugLogEnabled = debugLogEnabled;
    }

    public boolean isDirectDispatch() {
        return this.directDispatch;
    }

    public void setDirectDispatch(boolean directDispatch) {
        this.directDispatch = directDispatch;
    }

    public boolean isDisconnectNotify() {
        return this.disconnectNotify;
    }

    public void setDisconnectNotify(boolean disconnectNotify) {
        this.disconnectNotify = disconnectNotify;
    }

    /**
     * サーバー機能のOrigin情報を返却します
     * @see logbook.messageflow.web.ServerController
     */
    public String getKoukainissikaiMessageFlowOrigin() {
        return "http://localhost:" + this.koukainissikaiMessageFlowPort;
    }

    /**
     * サーバー機能のHost情報を返却します
     * @see logbook.messageflow.web.ServerController
     */
    public String getKoukainissikaiMessageFlowHost() {
        return "localhost:" + this.koukainissikaiMessageFlowPort;
    }
}
