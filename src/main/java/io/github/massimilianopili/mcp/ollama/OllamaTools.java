package io.github.massimilianopili.mcp.ollama;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "mcp.ollama.enabled", havingValue = "true")
public class OllamaTools {

    private static final Logger log = LoggerFactory.getLogger(OllamaTools.class);

    private final WebClient webClient;
    private final OllamaProperties props;

    public OllamaTools(@Qualifier("ollamaWebClient") WebClient webClient, OllamaProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "llm_generate",
            description = "Genera testo usando un modello LLM locale via Ollama. "
                    + "Modelli disponibili: qwen3.5:27b (general, 17GB), qwen3:4b (leggero). "
                    + "Usa per generazione testo, analisi, estrazione entità, riassunti. "
                    + "Non streaming — attende la risposta completa. Timeout 5 minuti.")
    public Mono<String> generate(
            @ToolParam(description = "Nome modello Ollama (es: qwen3.5:27b, qwen3:4b)") String model,
            @ToolParam(description = "Prompt di input") String prompt,
            @ToolParam(description = "System prompt (opzionale)", required = false) String system,
            @ToolParam(description = "Temperatura 0.0-2.0 (default 0.7, 0.0 = deterministico)", required = false) Double temperature) {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);

        Map<String, Object> options = new HashMap<>();
        if (temperature != null) {
            options.put("temperature", temperature);
        }
        options.put("num_predict", props.getMaxTokens());
        body.put("options", options);

        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }

        log.info("llm_generate: model={}, prompt length={}", model, prompt.length());

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    String text = String.valueOf(response.getOrDefault("response", ""));
                    Object totalDuration = response.get("total_duration");
                    Object evalCount = response.get("eval_count");
                    String stats = "";
                    if (totalDuration != null && evalCount != null) {
                        double seconds = ((Number) totalDuration).doubleValue() / 1_000_000_000.0;
                        int tokens = ((Number) evalCount).intValue();
                        double tokPerSec = tokens / Math.max(seconds, 0.001);
                        stats = String.format("\n\n---\n_Model: %s | %d tokens | %.1fs | %.1f tok/s_",
                                model, tokens, seconds, tokPerSec);
                    }
                    return text + stats;
                })
                .onErrorResume(e -> Mono.just("ERRORE: " + e.getMessage()));
    }

    @ReactiveTool(name = "llm_chat",
            description = "Chat multi-turno con un modello LLM locale via Ollama. "
                    + "Accetta una lista di messaggi con ruoli (system, user, assistant). "
                    + "Usa per conversazioni, follow-up, analisi iterativa. "
                    + "Formato messaggi: [{\"role\": \"user\", \"content\": \"...\"}, ...]. "
                    + "Non streaming — attende la risposta completa. Timeout 5 minuti.")
    public Mono<String> chat(
            @ToolParam(description = "Nome modello Ollama (es: qwen3.5:27b, qwen3:4b)") String model,
            @ToolParam(description = "Lista messaggi: [{\"role\": \"system|user|assistant\", \"content\": \"...\"}]") List<Map<String, String>> messages,
            @ToolParam(description = "Temperatura 0.0-2.0 (default 0.7)", required = false) Double temperature) {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);

        Map<String, Object> options = new HashMap<>();
        if (temperature != null) {
            options.put("temperature", temperature);
        }
        options.put("num_predict", props.getMaxTokens());
        body.put("options", options);

        log.info("llm_chat: model={}, messages={}", model, messages.size());

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) response.get("message");
                    String text = message != null ? String.valueOf(message.getOrDefault("content", "")) : "";
                    Object totalDuration = response.get("total_duration");
                    Object evalCount = response.get("eval_count");
                    String stats = "";
                    if (totalDuration != null && evalCount != null) {
                        double seconds = ((Number) totalDuration).doubleValue() / 1_000_000_000.0;
                        int tokens = ((Number) evalCount).intValue();
                        double tokPerSec = tokens / Math.max(seconds, 0.001);
                        stats = String.format("\n\n---\n_Model: %s | %d tokens | %.1fs | %.1f tok/s_",
                                model, tokens, seconds, tokPerSec);
                    }
                    return text + stats;
                })
                .onErrorResume(e -> Mono.just("ERRORE: " + e.getMessage()));
    }

    @ReactiveTool(name = "llm_list_models",
            description = "Elenca tutti i modelli LLM disponibili su Ollama. "
                    + "Mostra nome, dimensione, famiglia, quantizzazione e data di modifica.")
    public Mono<String> listModels() {
        return webClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> models = (List<Map<String, Object>>) response.getOrDefault("models", List.of());
                    if (models.isEmpty()) {
                        return "Nessun modello disponibile.";
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%-35s %-10s %-15s %s\n", "NOME", "SIZE", "FAMIGLIA", "MODIFICATO"));
                    sb.append("-".repeat(80)).append("\n");
                    for (Map<String, Object> m : models) {
                        String name = String.valueOf(m.getOrDefault("name", "?"));
                        long size = m.get("size") != null ? ((Number) m.get("size")).longValue() : 0;
                        String sizeStr = formatSize(size);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> details = (Map<String, Object>) m.getOrDefault("details", Map.of());
                        String family = String.valueOf(details.getOrDefault("family", "?"));
                        String quant = String.valueOf(details.getOrDefault("quantization_level", ""));
                        String modified = String.valueOf(m.getOrDefault("modified_at", "?"));
                        if (modified.length() > 10) modified = modified.substring(0, 10);
                        sb.append(String.format("%-35s %-10s %-15s %s\n", name, sizeStr,
                                family + (quant.isEmpty() ? "" : " " + quant), modified));
                    }
                    return sb.toString();
                })
                .onErrorResume(e -> Mono.just("ERRORE: " + e.getMessage()));
    }

    @ReactiveTool(name = "llm_model_info",
            description = "Mostra informazioni dettagliate su un modello Ollama: "
                    + "architettura, parametri, quantizzazione, dimensione contesto, template, licenza.")
    public Mono<String> modelInfo(
            @ToolParam(description = "Nome modello Ollama (es: qwen3.5:27b)") String model) {

        return webClient.post()
                .uri("/api/show")
                .bodyValue(Map.of("name", model))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("# ").append(model).append("\n\n");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = (Map<String, Object>) response.getOrDefault("details", Map.of());
                    sb.append("| Proprietà | Valore |\n|---|---|\n");
                    sb.append(String.format("| **Famiglia** | %s |\n", details.getOrDefault("family", "?")));
                    sb.append(String.format("| **Parametri** | %s |\n", details.getOrDefault("parameter_size", "?")));
                    sb.append(String.format("| **Quantizzazione** | %s |\n", details.getOrDefault("quantization_level", "?")));
                    sb.append(String.format("| **Formato** | %s |\n", details.getOrDefault("format", "?")));

                    @SuppressWarnings("unchecked")
                    Map<String, Object> modelInfo = (Map<String, Object>) response.getOrDefault("model_info", Map.of());
                    for (Map.Entry<String, Object> entry : modelInfo.entrySet()) {
                        String key = entry.getKey();
                        if (key.contains("context_length") || key.contains("embedding_length")
                                || key.contains("head_count") || key.contains("block_count")
                                || key.contains("vocab_size")) {
                            sb.append(String.format("| **%s** | %s |\n", key, entry.getValue()));
                        }
                    }

                    String template = String.valueOf(response.getOrDefault("template", ""));
                    if (!template.isBlank() && template.length() > 200) {
                        template = template.substring(0, 200) + "...";
                    }
                    if (!template.isBlank()) {
                        sb.append("\n**Template**: `").append(template.replace("\n", " ")).append("`\n");
                    }

                    String license = String.valueOf(response.getOrDefault("license", ""));
                    if (!license.isBlank()) {
                        if (license.length() > 300) license = license.substring(0, 300) + "...";
                        sb.append("\n**Licenza**: ").append(license.replace("\n", " ").substring(0, Math.min(license.length(), 150))).append("\n");
                    }

                    return sb.toString();
                })
                .onErrorResume(e -> Mono.just("ERRORE: " + e.getMessage()));
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
