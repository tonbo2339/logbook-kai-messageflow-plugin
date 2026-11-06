package logbook.messageflow.plugin;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.action.Action;
import org.apache.logging.log4j.core.appender.rolling.action.DeleteAction;
import org.apache.logging.log4j.core.appender.rolling.action.IfAccumulatedFileCount;
import org.apache.logging.log4j.core.appender.rolling.action.IfFileName;
import org.apache.logging.log4j.core.appender.rolling.action.PathCondition;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * 通信ログ (ロガー名 "logbook.traffic") の出力先を logs/messageflow_traffic.log に設定します。
 *
 * 元 logbook-kai-messageflow は log4j2-spring.xml で専用の RollingFile を定義していたが、
 * master の log4j2.xml にはこのロガーの定義が無いため、そのままだと root 経由で app.log に
 * 混ざって出力され、通信ログ表示ウィンドウ ({@code MessageFlowLogWindow}) が読む
 * messageflow_traffic.log には何も書かれない。master の設定ファイルは改変しない方針のため、
 * 起動時にプログラムからアペンダーとロガー定義を追加する (元の設定内容を踏襲)。
 */
final class TrafficLogConfigurer {

    static final String LOGGER_NAME = "logbook.traffic";
    private static final String APPENDER_NAME = "MESSAGEFLOW_TRAFFIC";

    private TrafficLogConfigurer() {
    }

    static synchronized void configure() {
        // 実際に通信ログを出力するロガーが属する LoggerContext を対象にする
        LoggerContext context = ((org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME)).getContext();
        Configuration config = context.getConfiguration();
        if (config.getAppender(APPENDER_NAME) != null) {
            return;
        }

        PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern("%d{yyyy.MM.dd HH:mm:ss.SSS} %m%n")
                .withCharset(java.nio.charset.StandardCharsets.UTF_8)
                .build();

        // 過去ログは直近5ファイルまで残す
        PathCondition keepLatest = IfFileName.createNameCondition("*/messageflow_traffic-*.log", null,
                IfAccumulatedFileCount.createFileCountCondition(5));
        DeleteAction delete = DeleteAction.createDeleteAction("logs", false, 2, false, null,
                new PathCondition[] { keepLatest }, null, config);
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
                .withConfig(config)
                .withCustomActions(new Action[] { delete })
                .build();

        RollingFileAppender appender = RollingFileAppender.newBuilder()
                .setName(APPENDER_NAME)
                .setConfiguration(config)
                .setLayout(layout)
                .withFileName("logs/messageflow_traffic.log")
                .withFilePattern("logs/%d{yyyy-MM}/messageflow_traffic-%d{yyyy-MM-dd}.log")
                .withPolicy(TimeBasedTriggeringPolicy.newBuilder().build())
                .withStrategy(strategy)
                .build();
        appender.start();
        config.addAppender(appender);

        // additivity=false で app.log (root) へは流さない
        LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withLoggerName(LOGGER_NAME)
                .withLevel(Level.INFO)
                .withAdditivity(false)
                .withRefs(new AppenderRef[] { AppenderRef.createAppenderRef(APPENDER_NAME, null, null) })
                .withConfig(config)
                .build();
        loggerConfig.addAppender(appender, null, null);
        config.addLogger(LOGGER_NAME, loggerConfig);
        context.updateLoggers();
    }
}
