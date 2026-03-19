package io.github.massimilianopili.mcp.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "mcp.ollama.enabled", havingValue = "true")
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig {

    private static final Logger log = LoggerFactory.getLogger(OllamaConfig.class);

    @Bean("ollamaWebClient")
    public WebClient ollamaWebClient(OllamaProperties props) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));

        log.info("Ollama WebClient configurato: {} (timeout {}s)", props.getBaseUrl(), props.getTimeoutSeconds());

        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
