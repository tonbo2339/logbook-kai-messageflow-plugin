package logbook.messageflow.queue;

public enum QueueName {
    API("APIQueue"),
    IMAGE("ImageQueue"),
    IMAGEJSON("ImageJsonQueue"),
    NONE("none");

    private final String queueName;

    QueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        return this.queueName;
    }

    public static QueueName getQueue(String queueName) {
        for (QueueName qn : QueueName.values()) {
            if (qn.queueName.equals(queueName)) {
                return qn;
            }
        }
        return NONE;
    }
}
