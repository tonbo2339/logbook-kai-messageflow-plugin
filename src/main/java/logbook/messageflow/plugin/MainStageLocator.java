package logbook.messageflow.plugin;

import java.util.List;

import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * masterのメインウィンドウ(Stage)を素のJavaFX APIで探すユーティリティです。
 * masterはプラグインへ主Stageを渡すSPIを持たないため、
 * {@link Window#getWindows()} から「ownerを持たず、タイトルが
 * "航海日誌"で始まる」Stageを探します。
 *
 * 呼び出しは必ずJavaFX Applicationスレッド上で行うこと
 * (Platform.runLater(...)の中から呼び出す)。
 */
public final class MainStageLocator {

    public static final String TITLE_PREFIX = "航海日誌";

    private MainStageLocator() {
    }

    public static Stage find() {
        List<Window> windows = Window.getWindows();
        for (Window window : windows) {
            if (window instanceof Stage stage
                    && stage.getOwner() == null
                    && stage.getTitle() != null
                    && stage.getTitle().startsWith(TITLE_PREFIX)) {
                return stage;
            }
        }
        return null;
    }
}
