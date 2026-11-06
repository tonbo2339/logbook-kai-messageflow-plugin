package logbook.messageflow.plugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.stage.Stage;
import logbook.plugin.lifecycle.StartUp;

/**
 * masterのメインウィンドウのタイトルバーに、messageflowの接続状況を
 * "[MessageFlow: 接続中]" / "[MessageFlow: 未接続]" として追記します。
 *
 * masterはプラグインへ主Stageを渡すSPIを提供していないため、
 * 素のJavaFX API ({@code Window.getWindows()}、{@link MainStageLocator}参照)
 * でメインウィンドウを特定しています。master本体の改造が不要で、
 * アップデートの影響を受けにくい方法です。
 *
 * メインウィンドウの判定は「タイトルが"航海日誌"で始まる、ownerを持たない Stage」。
 * master の {@code logbook.internal.gui.Main} は {@code "航海日誌 " + バージョン} を
 * タイトルに設定しているため、これと一致する。master側でタイトルの接頭辞が
 * 変わった場合は {@link MainStageLocator#TITLE_PREFIX} を追従させること。
 */
public class MessageFlowTitleStartUp implements StartUp {

    private static final Logger logger = LogManager.getLogger(MessageFlowTitleStartUp.class);
    private static final String TITLE_PREFIX = MainStageLocator.TITLE_PREFIX;
    private static final String MARK_CONNECTED = " [MessageFlow: 接続中]";
    private static final String MARK_NOT_CONNECTED = " [MessageFlow: 未接続]";

    @Override
    public void run() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "messageflow-title-updater");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleWithFixedDelay(this::updateTitle, 3, 5, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));
    }

    private void updateTitle() {
        try {
            Platform.runLater(() -> {
                Stage mainStage = MainStageLocator.find();
                if (mainStage == null) {
                    return;
                }
                MessageFlowStatusHolder.onConnectionObserved();
                boolean connected = MessageFlowStatusHolder.isConnected();
                DisconnectNotifier.check(connected, mainStage);

                String base = stripMark(mainStage.getTitle());
                String newTitle = base + (connected ? MARK_CONNECTED : MARK_NOT_CONNECTED);
                if (!newTitle.equals(mainStage.getTitle())) {
                    mainStage.setTitle(newTitle);
                }
            });
        } catch (Exception e) {
            logger.warn("タイトルバー更新中にエラーが発生しました", e);
        }
    }

    private static String stripMark(String title) {
        if (title == null) {
            return TITLE_PREFIX;
        }
        return title.replace(MARK_CONNECTED, "").replace(MARK_NOT_CONNECTED, "");
    }
}
