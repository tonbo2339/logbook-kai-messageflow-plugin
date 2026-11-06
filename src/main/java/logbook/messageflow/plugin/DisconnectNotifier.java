package logbook.messageflow.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import logbook.messageflow.config.MessageFlowConfig;

/**
 * Chrome拡張との接続が「接続中」から「未接続」に変わったとき (DevTools を閉じた等) に、
 * 音とポップアップで知らせます。
 *
 * 一度も接続していない起動直後は通知しません。接続が一瞬途切れてすぐ戻る場合
 * (拡張の自動再接続) に鳴らないよう、未接続が {@link #DISCONNECTED_TICKS} 回連続して
 * 観測されたときだけ通知します (呼び出しは 5 秒間隔なので、5〜10 秒以上の切断)。
 *
 * {@link #check} は JavaFX Application スレッドから呼ぶこと。
 */
final class DisconnectNotifier {

    private static final Logger logger = LogManager.getLogger(DisconnectNotifier.class);

    /** 未接続を何回連続で観測したら通知するか */
    private static final int DISCONNECTED_TICKS = 2;

    private static boolean wasConnected;
    private static int disconnectedTicks;
    private static Alert shownAlert;

    private DisconnectNotifier() {
    }

    static void check(boolean connected, Stage owner) {
        if (connected) {
            wasConnected = true;
            disconnectedTicks = 0;
            if (shownAlert != null) {
                // 再接続できたら、出したままの通知は閉じる
                shownAlert.close();
                shownAlert = null;
            }
            return;
        }
        if (!wasConnected) {
            return;
        }
        disconnectedTicks++;
        if (disconnectedTicks < DISCONNECTED_TICKS) {
            return;
        }
        // 通知した後は、再び接続するまで二重に通知しない
        wasConnected = false;
        disconnectedTicks = 0;
        if (MessageFlowConfig.getInstance().isDisconnectNotify()) {
            notifyDisconnected(owner);
        }
    }

    private static void notifyDisconnected(Stage owner) {
        logger.info("Chrome拡張との接続が切れました (通知)");
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable e) {
            logger.debug("通知音を鳴らせませんでした", e);
        }
        try {
            Alert alert = new Alert(AlertType.WARNING);
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
}
