package logbook.messageflow.plugin.gui;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * 通信ログ表示ウィンドウ。
 *
 * 元 logbook-kai-messageflow の LogBookMessageFlowView#showLogWindow() を
 * ほぼそのまま移植したものです。logbook.traffic ロガーが出力する
 * messageflow_traffic.log ({@code TrafficLogConfigurer} で出力先を設定) の末尾を読み込み、
 * API/RESOURCE種別でフィルタして表示します。
 */
public final class MessageFlowLogWindow {

    private static final Logger logger = LogManager.getLogger(MessageFlowLogWindow.class);
    private static final String LOG_FILE_PATH = "logs/messageflow_traffic.log";
    /** 一度に読み込むのはファイル末尾のこのバイト数まで (5秒ごとの自動更新でファイル全体を読まないため) */
    private static final int TAIL_BYTES = 1024 * 1024;

    private static Stage openStage;

    private MessageFlowLogWindow() {
    }

    public static void show(Window owner) {
        if (openStage != null) {
            openStage.toFront();
            return;
        }

        Stage logStage = new Stage();
        logStage.setTitle("通信ログ");
        logStage.initOwner(owner);

        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        CheckBox autoRefreshCheck = new CheckBox("自動更新");
        autoRefreshCheck.setSelected(true);

        CheckBox apiCheck = new CheckBox("API");
        apiCheck.setSelected(true);

        CheckBox resourceCheck = new CheckBox("RESOURCE");
        resourceCheck.setSelected(true);

        Button refreshBtn = new Button("更新");
        refreshBtn.setOnAction(e -> refreshLog(textArea, apiCheck, resourceCheck));

        // 初回ロード
        refreshBtn.fire();

        // 5秒毎に自動更新
        Timeline autoRefreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(5), e -> {
                    if (autoRefreshCheck.isSelected()) {
                        refreshBtn.fire();
                    }
                }));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();

        logStage.setOnCloseRequest(e -> {
            autoRefreshTimeline.stop();
            openStage = null;
        });

        HBox footer = new HBox(15);
        footer.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(autoRefreshCheck, apiCheck, resourceCheck, spacer, refreshBtn);

        VBox logRoot = new VBox(10, textArea, footer);
        logRoot.setPadding(new Insets(10));
        logRoot.setAlignment(Pos.CENTER);

        logStage.setScene(new Scene(logRoot, 800, 500));
        openStage = logStage;
        logStage.show();
    }

    /**
     * ファイル末尾の最大 maxBytes バイトを読み込み、行に分割して返します。
     * 途中から読んだ場合、先頭の欠けた1行は捨てます。不正なバイト列は置換文字にします。
     */
    private static List<String> readTailLines(String path, int maxBytes) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long length = file.length();
            long start = Math.max(0, length - maxBytes);
            byte[] buf = new byte[(int) (length - start)];
            file.seek(start);
            file.readFully(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            List<String> lines = Arrays.asList(text.split("\r?\n"));
            if (start > 0 && !lines.isEmpty()) {
                lines = lines.subList(1, lines.size());
            }
            return lines;
        }
    }

    private static void refreshLog(TextArea textArea, CheckBox apiCheck, CheckBox resourceCheck) {
        try {
            List<String> lines = readTailLines(LOG_FILE_PATH, TAIL_BYTES);

            List<String> filteredLines = lines.stream()
                    .filter(line -> {
                        boolean isApi = line.contains("/kcsapi/");
                        boolean isResource = line.contains("/kcs2/");
                        if (isApi) {
                            return apiCheck.isSelected();
                        }
                        if (isResource) {
                            return resourceCheck.isSelected();
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            // 直近500行に制限 (メモリ保護)
            int start = Math.max(0, filteredLines.size() - 500);
            String logContent = String.join("\n", filteredLines.subList(start, filteredLines.size()));
            textArea.setText(logContent);
            textArea.setScrollTop(Double.MAX_VALUE);
            textArea.selectPositionCaret(textArea.getLength());
            textArea.deselect();
        } catch (IOException ex) {
            logger.error("ログファイルの読み込みに失敗しました", ex);
            textArea.setText("ログファイルの読み込みに失敗しました: " + ex.getMessage());
        }
    }
}
