package logbook.messageflow.plugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Modality;
import logbook.messageflow.config.MessageFlowConfig;
import logbook.messageflow.queue.QueueHolder;
import logbook.messageflow.web.WebSocketStatus;

/**
 * Chrome拡張との接続が切れたとき (DevTools を閉じた等) に、音とポップアップで知らせます。
 *
 * WebSocket の接続数が変わるたびに呼ばれ ({@link WebSocketStatus#setChangeListener})、
 * 全ての接続が無くなってから {@link #GRACE_MILLIS} 経ってもまだ戻らなければ通知します。
 * 拡張は切れると 3 秒後に再接続を試みるため、それより少し長く待つことで、一瞬の切断では鳴らさない。
 * 一度も接続していない起動直後と、アプリの終了処理中は通知しません。
 */
final class DisconnectNotifier {

    private static final Logger logger = LogManager.getLogger(DisconnectNotifier.class);

    /** 全て切れてから通知するまでの猶予 (拡張の最初の再接続は 3 秒後) */
    private static final long GRACE_MILLIS = 4_000;

    private static final Object LOCK = new Object();
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> pending;
    /** 接続したことがあり、まだ切断を通知していない */
    private static boolean wasConnected;
    /** FX スレッドからのみ触る */
    private static Alert shownAlert;

    private DisconnectNotifier() {
    }

    static void install() {
        WebSocketStatus.setChangeListener(DisconnectNotifier::onConnectionChanged);
    }

    private static void onConnectionChanged() {
        synchronized (LOCK) {
            if (WebSocketStatus.anyConnected()) {
                wasConnected = true;
                if (pending != null) {
                    pending.cancel(false);
                    pending = null;
                }
                Platform.runLater(DisconnectNotifier::closeShownAlert);
                return;
            }
            if (!wasConnected || pending != null) {
                return;
            }
            if (scheduler == null) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "messageflow-disconnect-notifier");
                    t.setDaemon(true);
                    return t;
                });
            }
            pending = scheduler.schedule(DisconnectNotifier::confirmDisconnected, GRACE_MILLIS,
                    TimeUnit.MILLISECONDS);
        }
    }

    private static void confirmDisconnected() {
        synchronized (LOCK) {
            pending = null;
            if (WebSocketStatus.anyConnected() || !wasConnected) {
                return;
            }
            // 通知した後は、再び接続するまで二重に通知しない
            wasConnected = false;
        }
        if (QueueHolder.getInstance().isShuttingDown() || !MessageFlowConfig.getInstance().isDisconnectNotify()) {
            return;
        }
        logger.info("Chrome拡張との接続が切れました (通知)");
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable e) {
            logger.debug("通知音を鳴らせませんでした", e);
        }
        Platform.runLater(DisconnectNotifier::showAlert);
    }

    private static void showAlert() {
        try {
            closeShownAlert();
            Alert alert = new Alert(AlertType.WARNING);
            javafx.stage.Stage owner = MainStageLocator.find();
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.initModality(Modality.NONE);
            alert.setTitle("MessageFlow");
            alert.setHeaderText("Chrome拡張との接続が切れました");
            alert.setContentText("DevTools (logbook-kai パネル) を閉じた可能性があります。\n"
                    + "DevTools の logbook-kai パネルを開き直してください。");
            alert.setOnHidden(e -> {
                if (shownAlert == alert) {
                    shownAlert = null;
                }
            });
            shownAlert = alert;
            alert.show();
        } catch (Throwable e) {
            logger.warn("切断の通知ウィンドウを表示できませんでした", e);
        }
    }

    /** 再接続できたら、出したままの通知は閉じる */
    private static void closeShownAlert() {
        if (shownAlert != null) {
            shownAlert.close();
            shownAlert = null;
        }
    }
}
