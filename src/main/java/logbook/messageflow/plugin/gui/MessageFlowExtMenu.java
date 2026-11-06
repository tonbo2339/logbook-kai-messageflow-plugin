package logbook.messageflow.plugin.gui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import logbook.messageflow.plugin.MessageFlowStatusHolder;
import logbook.plugin.gui.MainExtMenu;

/**
 * masterの「拡張」メニューに、messageflowの接続状況を示す
 * メニュー項目 (赤/緑の丸 + ラベル) を追加します。
 * クリックすると詳細ダイアログ ({@link MessageFlowDetailDialog}) を開きます。
 */
public class MessageFlowExtMenu implements MainExtMenu {

    private static final Circle indicator = new Circle(5);
    private static final MenuItem menuItem = new MenuItem("MessageFlow", indicator);

    static {
        indicator.setFill(Color.GRAY);
        menuItem.setOnAction(event -> MessageFlowDetailDialog.showDialog());

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "messageflow-menu-indicator");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(MessageFlowExtMenu::refreshIndicator, 3, 5, TimeUnit.SECONDS);
    }

    private static void refreshIndicator() {
        boolean connected = MessageFlowStatusHolder.isConnected();
        Platform.runLater(() -> {
            indicator.setFill(connected ? Color.LIMEGREEN : Color.CRIMSON);
            menuItem.setText(connected ? "MessageFlow (接続中)" : "MessageFlow (未接続)");
        });
    }

    @Override
    public MenuItem getContent() {
        return menuItem;
    }
}
