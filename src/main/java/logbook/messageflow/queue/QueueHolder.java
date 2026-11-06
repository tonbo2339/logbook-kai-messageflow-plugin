package logbook.messageflow.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class QueueHolder {
    private static final QueueHolder INSTANCE = new QueueHolder();

    private final BlockingQueue<String> apiQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> imageQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> imageJsonQueue = new LinkedBlockingQueue<>();

    private volatile boolean isShuttingDown = false;

    private QueueHolder() {
    }

    public static QueueHolder getInstance() {
        return INSTANCE;
    }

    public BlockingQueue<String> getAPIQueue() {
        return this.apiQueue;
    }

    public BlockingQueue<String> getImageQueue() {
        return this.imageQueue;
    }

    public BlockingQueue<String> getImageJsonQueue() {
        return this.imageJsonQueue;
    }

    public boolean isShuttingDown() {
        return this.isShuttingDown;
    }

    public void setShuttingDown(boolean shuttingDown) {
        this.isShuttingDown = shuttingDown;
    }
}
