package logbook.messageflow.queue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直近5分間のQueue処理件数を保持します (元 logbook-kai-messageflow の移植)。
 */
public class QueueStatus {
    private static final Map<String, List<String>> apiQueueCounts = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> imageQueueCounts = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> imageJsonQueueCounts = new ConcurrentHashMap<>();
    private static final DateTimeFormatter FORMATTER_HHmm = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter FORMATTER_HHmmssSSS = DateTimeFormatter.ofPattern("HHmmssSSS");

    private QueueStatus() {
    }

    public static synchronized void incrementQueueCount(QueueName queue) {
        LocalDateTime dateTime = LocalDateTime.now();
        String key = dateTime.format(FORMATTER_HHmm);
        String incrementTime = dateTime.format(FORMATTER_HHmmssSSS);
        Map<String, List<String>> map = mapFor(queue);
        if (map == null) {
            return;
        }
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(incrementTime);
        pruneOld(map, dateTime);
    }

    public static synchronized void ensureCurrentMinuteExists(QueueName queue) {
        LocalDateTime dateTime = LocalDateTime.now();
        String key = dateTime.format(FORMATTER_HHmm);
        Map<String, List<String>> map = mapFor(queue);
        if (map == null) {
            return;
        }
        map.computeIfAbsent(key, k -> new ArrayList<>());
        pruneOld(map, dateTime);
    }

    private static Map<String, List<String>> mapFor(QueueName queue) {
        if (QueueName.API.equals(queue)) {
            return apiQueueCounts;
        } else if (QueueName.IMAGE.equals(queue)) {
            return imageQueueCounts;
        } else if (QueueName.IMAGEJSON.equals(queue)) {
            return imageJsonQueueCounts;
        }
        return null;
    }

    private static void pruneOld(Map<String, List<String>> map, LocalDateTime dateTime) {
        String thresholdKey = getThresholdMinuteKey(dateTime);
        if (map.containsKey(thresholdKey)) {
            map.keySet().removeIf(k -> k.compareTo(thresholdKey) < 0);
        }
    }

    private static String getThresholdMinuteKey(LocalDateTime dateTime) {
        return dateTime.minusMinutes(5).format(FORMATTER_HHmm);
    }

    public static Map<String, Integer> getQueueTotalCounts() {
        Map<String, Integer> totalCounts = new HashMap<>();
        totalCounts.put(QueueName.API.getQueueName(), apiQueueCounts.values().stream().mapToInt(List::size).sum());
        totalCounts.put(QueueName.IMAGE.getQueueName(), imageQueueCounts.values().stream().mapToInt(List::size).sum());
        totalCounts.put(QueueName.IMAGEJSON.getQueueName(),
                imageJsonQueueCounts.values().stream().mapToInt(List::size).sum());
        return totalCounts;
    }
}
