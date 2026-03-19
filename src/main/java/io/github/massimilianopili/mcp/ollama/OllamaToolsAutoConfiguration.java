package io.github.massimilianopili.mcp.ollama;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "mcp.ollama.enabled", havingValue = "true", matchIfMissing = false)
@Import({OllamaConfig.class, OllamaTools.class})
public class OllamaToolsAutoConfiguration {
}
