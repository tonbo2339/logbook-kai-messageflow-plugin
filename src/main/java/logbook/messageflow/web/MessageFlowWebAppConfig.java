package logbook.messageflow.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * このパッケージ (logbook.messageflow.web) にある
 * {@link MessageFlowWebSocketConfig} (SockJS/WebSocketエンドポイント) と
 * {@link ServerController} (REST エンドポイント) をまとめて有効化します。
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "logbook.messageflow.web")
public class MessageFlowWebAppConfig {

    /**
     * コンテキスト停止時のライフサイクル停止待ちの上限を短くする。
     * デフォルト(30秒)のままだと、Chrome拡張が接続中にアプリを閉じた場合に
     * webSocketHandlerMapping の停止完了を30秒待ち、その間プロセスが残り続ける。
     * (bean名 "lifecycleProcessor" で登録すると Spring がこれを使う)
     */
    @Bean
    public DefaultLifecycleProcessor lifecycleProcessor() {
        DefaultLifecycleProcessor processor = new DefaultLifecycleProcessor();
        processor.setTimeoutPerShutdownPhase(3000);
        return processor;
    }
}
