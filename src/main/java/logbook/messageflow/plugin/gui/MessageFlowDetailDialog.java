package logbook.messageflow.plugin.gui;

import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import logbook.messageflow.config.MessageFlowConfig;
import logbook.messageflow.dispatch.DirectDispatcher;
import logbook.messageflow.plugin.MainStageLocator;
import logbook.messageflow.plugin.MessageFlowStatusHolder;
import logbook.messageflow.queue.QueueStatus;
import logbook.messageflow.web.WebSocketStatus;

/**
 * messageflowの状態確認ダイアログ。
 *
 * 元 logbook-kai-messageflow の LogBookMessageFlowView (常時表示の別ウィンドウ) を、
 * masterのメインウィンドウの子ダイアログとして必要な時だけ開く形に置き換えたものです。
 * (owner を持つため、独立したウィンドウとしてはカウントされません)
 */
public final class MessageFlowDetailDialog {

    private static Stage openStage;

    private MessageFlowDetailDialog() {
    }

    public static void showDialog() {
        if (openStage != null) {
            openStage.toFront();
            return;
        }

        MessageFlowConfig config = MessageFlowConfig.getInstance();

        Label portLabel = new Label(
                "起動Port: " + config.getKoukainissikaiMessageFlowPort()
                        + " / 航海日誌改Port: " + config.getKoukainissikaiPort());
        Label statusLabel = new Label();
        Label queueLabel = new Label();
        Label wsCountLabel = new Label();
        Label modeLabel = new Label();

        Button settingsButton = new Button("設定");
        Button logButton = new Button("通信ログ表示");

        HBox topBox = new HBox(10);
        topBox.setAlignment(Pos.CENTER_LEFT);
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topBox.getChildren().addAll(portLabel, topSpacer, settingsButton, logButton);

        VBox root = new VBox(10, topBox, statusLabel, modeLabel, wsCountLabel, queueLabel);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.TOP_LEFT);

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> refresh(statusLabel, modeLabel, queueLabel, wsCountLabel)));
        timeline.setCycleCount(Timeline.INDEFINITE);
        refresh(statusLabel, modeLabel, queueLabel, wsCountLabel);
        timeline.play();

        Stage stage = new Stage();
        stage.setTitle("MessageFlow 状態");
        stage.setScene(new Scene(root, 440, 240));
        stage.initOwner(MainStageLocator.find());

        settingsButton.setOnAction(e -> MessageFlowSettingsDialog.show(stage));
        logButton.setOnAction(e -> MessageFlowLogWindow.show(stage));

        stage.setOnCloseRequest(e -> {
            timeline.stop();
            openStage = null;
        });

        openStage = stage;
        stage.show();
    }

    private static void refresh(Label statusLabel, Label modeLabel, Label queueLabel, Label wsCountLabel) {
        MessageFlowStatusHolder.onConnectionObserved();
        boolean connected = MessageFlowStatusHolder.isConnected();
        statusLabel.setText("接続状況: " + (connected ? "接続中" : "未接続"));

        boolean direct = MessageFlowConfig.getInstance().isDirectDispatch() && DirectDispatcher.isAvailable();
        modeLabel.setText("受け渡し方式: " + (direct ? "直接" : "HTTP経由 (航海日誌改Port "
                + MessageFlowConfig.getInstance().getKoukainissikaiPort() + ")"));

        wsCountLabel.setText(String.format("WebSocket接続数  API:%d Image:%d ImageJson:%d",
                WebSocketStatus.API.getCount(), WebSocketStatus.IMAGE.getCount(), WebSocketStatus.IMAGE_JSON.getCount()));

        Map<String, Integer> counts = QueueStatus.getQueueTotalCounts();
        StringBuilder sb = new StringBuilder("直近5分の処理件数  ");
        counts.forEach((k, v) -> sb.append(k).append(":").append(v).append("  "));
        queueLabel.setText(sb.toString());
    }
}
