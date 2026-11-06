package logbook.messageflow.plugin.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import logbook.messageflow.config.MessageFlowConfig;

/**
 * messageflowの設定 (ポート番号・受け渡し方式・切断の通知・デバッグログ有無) をGUIから変更するためのダイアログ。
 *
 * ポート番号の変更はサーバーの動的再起動を行わない (安全性・実装コストの都合上)ため、
 * ポート番号を変更した場合のみ「航海日誌改の再起動が必要」であることを案内する。
 * 受け渡し方式とデバッグログの有無は、保存と同時にその場で反映される。
 */
public final class MessageFlowSettingsDialog {

    private MessageFlowSettingsDialog() {
    }

    public static void show(Window owner) {
        MessageFlowConfig config = MessageFlowConfig.getInstance();
        int currentPort = config.getKoukainissikaiMessageFlowPort();
        int currentKaiPort = config.getKoukainissikaiPort();

        Label portLabel = new Label("messageflowポート番号:");
        TextField portField = new TextField(String.valueOf(currentPort));

        Label kaiPortLabel = new Label("航海日誌改ポート番号:");
        TextField kaiPortField = new TextField(String.valueOf(currentKaiPort));

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: crimson;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        grid.addRow(0, portLabel, portField);
        grid.addRow(1, kaiPortLabel, kaiPortField);

        CheckBox directDispatchCheck = new CheckBox("通信内容を航海日誌改へ直接渡す (推奨)");
        directDispatchCheck.setSelected(config.isDirectDispatch());
        Label directDispatchNote = new Label(
                "OFFにすると、従来と同じく航海日誌改のプロキシ(航海日誌改ポート番号)を経由して渡します。");
        directDispatchNote.setWrapText(true);
        directDispatchNote.setStyle("-fx-font-size: 11px;");

        CheckBox disconnectNotifyCheck = new CheckBox("接続が切れたときに通知する (音とポップアップ)");
        disconnectNotifyCheck.setSelected(config.isDisconnectNotify());
        Label disconnectNotifyNote = new Label(
                "Chromeの DevTools (logbook-kai パネル) を閉じたときなどに知らせます。");
        disconnectNotifyNote.setWrapText(true);
        disconnectNotifyNote.setStyle("-fx-font-size: 11px;");

        CheckBox debugLogCheck = new CheckBox("デバッグログを出力する (通信内容の詳細をログに記録)");
        debugLogCheck.setSelected(config.isDebugLogEnabled());

        Label noteLabel = new Label(
                "※ ポート番号の変更を反映するには航海日誌改の再起動が必要です。\n"
                        + "航海日誌改ポート番号を本体の設定と同じ値にすると、以後は本体の設定に自動で追従します。\n"
                        + "受け渡し方式・切断の通知・デバッグログの設定はすぐに反映されます。");
        noteLabel.setWrapText(true);

        Button saveButton = new Button("保存");
        Button cancelButton = new Button("キャンセル");

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buttonBox.getChildren().addAll(spacer, cancelButton, saveButton);

        VBox dispatchBox = new VBox(3, directDispatchCheck, directDispatchNote);
        VBox notifyBox = new VBox(3, disconnectNotifyCheck, disconnectNotifyNote);
        VBox root = new VBox(12, grid, dispatchBox, notifyBox, debugLogCheck, noteLabel, errorLabel, buttonBox);
        root.setPadding(new Insets(15));

        Stage stage = new Stage();
        stage.setTitle("MessageFlow 設定");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(new Scene(root, 480, 400));

        cancelButton.setOnAction(e -> stage.close());

        saveButton.setOnAction(e -> {
            Integer port = parsePort(portField.getText());
            Integer kaiPort = parsePort(kaiPortField.getText());
            if (port == null || kaiPort == null) {
                errorLabel.setText("ポート番号は1〜65535の数値で入力してください。");
                return;
            }
            boolean portChanged = port != currentPort || kaiPort != currentKaiPort;

            config.setKoukainissikaiMessageFlowPort(port);
            config.setKoukainissikaiPort(kaiPort);
            config.setDirectDispatch(directDispatchCheck.isSelected());
            config.setDisconnectNotify(disconnectNotifyCheck.isSelected());
            config.setDebugLogEnabled(debugLogCheck.isSelected());
            config.save();

            stage.close();

            Alert alert = new Alert(AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle("MessageFlow 設定");
            alert.setHeaderText(null);
            alert.setContentText(portChanged
                    ? "設定を保存しました。ポート番号の変更を反映するには航海日誌改を再起動してください。"
                    : "設定を保存しました。");
            alert.showAndWait();
        });

        stage.showAndWait();
    }

    private static Integer parsePort(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.matches("\\d+")) {
            return null;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 1 || value > 65535) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
