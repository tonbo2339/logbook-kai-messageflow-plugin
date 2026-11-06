package logbook.messageflow.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private DateTimeUtils() {
    }

    /**
     * 現在日時を YYYYMMDDHHmmssSSS 形式の文字列で取得します。
     */
    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(FORMATTER);
    }
}
