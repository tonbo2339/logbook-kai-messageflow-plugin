package logbook.messageflow.plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import logbook.messageflow.config.MessageFlowConfig;
import logbook.messageflow.queue.ApiConsumer;
import logbook.messageflow.queue.ImageConsumer;
import logbook.messageflow.queue.ImageJsonConsumer;
import logbook.messageflow.queue.QueueHolder;
import logbook.messageflow.queue.QueueName;
import logbook.messageflow.web.MessageFlowWebAppConfig;
import logbook.plugin.lifecycle.StartUp;

/**
 * masterアプリケーション起動時に自動実行され、
 * 元 logbook-kai-messageflow が別プロセス・別ウィンドウとして提供していた
 * WebSocket(SockJS)/RESTサーバーとQueue処理を、master内で起動します。
 *
 * META-INF/services/logbook.plugin.lifecycle.StartUp に登録することで
 * ServiceLoader経由でmasterから呼び出されます (専用のデーモンスレッドで実行)。
 */
public class MessageFlowServerStartUp implements StartUp {

    private static final Logger logger = LogManager.getLogger(MessageFlowServerStartUp.class);

    /** シャットダウン処理全体の猶予。これを過ぎたら強制終了する */
    private static final long FORCE_EXIT_MILLIS = 15_000;
    /** 各停止処理 (Spring/Jetty等) の待ち時間の上限 */
    private static final long STOP_STEP_TIMEOUT_MILLIS = 3_000;

    private static Server jettyServer;
    private static AnnotationConfigWebApplicationContext springContext;
    private static ExecutorService apiExecutorService;
    private static ExecutorService imageExecutorService;
    private static ExecutorService imageJsonExecutorService;

    @Override
    public void run() {
        // 重要: このスレッド(masterが生成した実行スレッド)のContext ClassLoaderは
        // 通常masterの標準クラスローダーのままで、本プラグインを読み込んだ
        // クラスローダーとは別物になっている。Springの@ComponentScanは
        // このContext ClassLoaderを使ってクラスパスを探索するため、
        // 明示的に本プラグイン自身のクラスローダーへ差し替えないと
        // logbook.messageflow.web配下の自作クラスが一切見つからない。
        Thread.currentThread().setContextClassLoader(MessageFlowServerStartUp.class.getClassLoader());
        try {
            TrafficLogConfigurer.configure();
        } catch (Throwable e) {
            // 通信ログの出力先設定に失敗しても、中継機能自体は起動させる
            logger.warn("通信ログの出力先設定に失敗しました (通信ログは app.log に出力されます)", e);
        }
        try {
            startQueueConsumers();
            startServer();
            MessageFlowStatusHolder.setServerStarted(true);
            DisconnectNotifier.install();
            // masterのLauncherにはプラグイン向けの終了SPIが無いため、
            // 独自にシャットダウンフックを登録して後始末を行う。
            // 「見張り役」スレッドは後始末の *前* に起動する (後始末自体が止まった場合にも
            // 効くように)。シャットダウン処理全体 (masterや他プラグインの分も含む) が
            // FORCE_EXIT_MILLIS 以内に終わらない場合は強制終了する。masterの設定保存は
            // 通常1秒未満で終わるため、保存中のデータを壊さないよう十分な猶予を取っている。
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Thread watchdog = new Thread(() -> {
                    try {
                        Thread.sleep(FORCE_EXIT_MILLIS);
                        logger.warn("通常のシャットダウン処理が完了しないため、プロセスを強制終了します");
                        Runtime.getRuntime().halt(0);
                    } catch (InterruptedException ignored) {
                        // 通常通りJVMが終了した場合はここに来ない (正常系)
                    }
                }, "messageflow-force-exit-watchdog");
                watchdog.setDaemon(true);
                watchdog.start();
                shutdown();
            }, "messageflow-shutdown"));
            logger.info("messageflowプラグインの起動処理が完了しました");
        } catch (Throwable e) {
            // ExceptionInInitializerError 等の Error も握りつぶさずログに残すため Throwable で受ける
            logger.error("messageflowプラグインの起動処理に失敗しました", e);
        }
    }

    private void startQueueConsumers() {
        QueueHolder queue = QueueHolder.getInstance();

        // 通信順序を維持するため、SingleThreadExecutor を使用 (元実装を踏襲)
        apiExecutorService = Executors.newSingleThreadExecutor(daemonFactory("messageflow-api-consumer"));
        imageExecutorService = Executors.newSingleThreadExecutor(daemonFactory("messageflow-image-consumer"));
        imageJsonExecutorService = Executors.newSingleThreadExecutor(daemonFactory("messageflow-imagejson-consumer"));

        apiExecutorService.execute(new ApiConsumer(queue.getAPIQueue(), QueueName.API));
        imageExecutorService.execute(new ImageConsumer(queue.getImageQueue(), QueueName.IMAGE));
        imageJsonExecutorService.execute(new ImageJsonConsumer(queue.getImageJsonQueue(), QueueName.IMAGEJSON));
    }

    private void startServer() throws Exception {
        int port = MessageFlowConfig.getInstance().getKoukainissikaiMessageFlowPort();

        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("messageflow-jetty");
        threadPool.setDaemon(true);

        // 重要: Jettyは内部でスケジューラ(タイムアウト管理用)を自動生成するが、
        // 明示的に daemon=true を指定しないとこのスレッドが非デーモンのまま残り、
        // masterのウィンドウを閉じてもjavaプロセスが終了しなくなる。
        ScheduledExecutorScheduler scheduler = new ScheduledExecutorScheduler("messageflow-jetty-scheduler", true);

        jettyServer = new Server(threadPool);
        jettyServer.addBean(scheduler);
        org.eclipse.jetty.server.ServerConnector connector = new org.eclipse.jetty.server.ServerConnector(jettyServer);
        connector.setPort(port);
        connector.setHost("localhost");
        jettyServer.setConnectors(new org.eclipse.jetty.server.Connector[] { connector });

        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");

        // 重要: SpringのWebSocket機能(JettyRequestUpgradeStrategy)は、
        // refresh()実行時に自分が所属するJetty Serverを参照しようとするため、
        // springContext.refresh()より先に contextHandler を jettyServer へ
        // 登録しておく必要がある(順番を間違えるとNPEで起動失敗する)。
        jettyServer.setHandler(contextHandler);

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(contextHandler.getServletContext());
        context.register(MessageFlowWebAppConfig.class);
        context.refresh();
        springContext = context;

        // 診断用: 実際にSpringが認識したBean一覧をログに出す。
        // ServerController / MessageFlowWebSocketConfig が含まれているか確認すること。
        logger.info("messageflow Springコンテキストの登録Bean一覧: "
                + String.join(", ", springContext.getBeanDefinitionNames()));

        DispatcherServlet dispatcherServlet = new DispatcherServlet(springContext);
        ServletHolder servletHolder = new ServletHolder(dispatcherServlet);
        servletHolder.setAsyncSupported(true);
        contextHandler.addServlet(servletHolder, "/*");

        jettyServer.start();

        logger.info("messageflowサーバーをポート " + port + " で起動しました");
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * アプリケーション終了時の停止処理 (run() 内でシャットダウンフックとして登録済み)。
     */
    public static void shutdown() {
        QueueHolder.getInstance().setShuttingDown(true);
        QueueHolder queue = QueueHolder.getInstance();
        safelyShutdown(apiExecutorService, queue.getAPIQueue(), QueueName.API);
        safelyShutdown(imageExecutorService, queue.getImageQueue(), QueueName.IMAGE);
        safelyShutdown(imageJsonExecutorService, queue.getImageJsonQueue(), QueueName.IMAGEJSON);
        runWithTimeout("Jetty HttpClient", logbook.messageflow.util.ProxyHttpClient::shutdown);
        runWithTimeout("Springコンテキスト", () -> {
            if (springContext != null) {
                springContext.close();
            }
        });
        runWithTimeout("SockJS用スケジューラ", logbook.messageflow.web.MessageFlowWebSocketConfig::shutdownScheduler);
        runWithTimeout("Jettyサーバー", () -> {
            if (jettyServer != null) {
                jettyServer.setStopTimeout(STOP_STEP_TIMEOUT_MILLIS);
                jettyServer.stop();
            }
        });
    }

    @FunctionalInterface
    private interface StopStep {
        void run() throws Exception;
    }

    /**
     * 停止処理を別スレッド (daemon) で実行し、上限時間だけ待つ。
     * 停止処理が戻ってこなくてもシャットダウンフック全体は先へ進める
     * (シャットダウンフックが終われば、残ったdaemonスレッドに関係なくJVMは終了する)。
     */
    private static void runWithTimeout(String name, StopStep step) {
        Thread thread = new Thread(() -> {
            try {
                step.run();
            } catch (Throwable e) {
                logger.warn(name + "の停止中にエラーが発生しました", e);
            }
        }, "messageflow-stop-" + name);
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(STOP_STEP_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            logger.warn(name + "の停止が" + STOP_STEP_TIMEOUT_MILLIS + "ms以内に完了しないため、待たずに終了処理を続けます");
        }
    }

    private static void safelyShutdown(ExecutorService executorService, java.util.Queue<String> queue, QueueName queueName) {
        if (executorService == null) {
            return;
        }
        // Queueが空になるまで最大5秒待ってから Executor を停止する
        // (処理中のデータを取りこぼさないため。元実装は最大30秒×Queue数だったが、
        //  直接受け渡しでは処理が速く、終了が長引く方が問題になるため短縮)。
        int attempts = 0;
        while (attempts < 10 && queue != null && !queue.isEmpty()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.info(queueName + " のシャットダウンがタイムアウトしました。強制終了します。");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
