package example.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShipmentVoiceService {
    public record VoiceNote(String shipmentId, String eventId, String transcript, String recordingUri, String proofOfDeliveryUri, String exceptionCode) {}
    public record SearchRequest(String shipmentId, String question) {}

    private final String baseUrl;
    private final String key;
    private final String collection;
    private final OpenAIClient ai;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public ShipmentVoiceService(@Value("${logistics.infrai.base-url}") String baseUrl,
                                @Value("${logistics.infrai.collection}") String collection) {
        this.baseUrl = baseUrl;
        this.collection = collection;
        this.key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        this.ai = OpenAIOkHttpClient.builder().baseUrl(baseUrl).apiKey(key).build();
    }

    public static String eventState(VoiceNote note) {
        if (note.exceptionCode() != null && !note.exceptionCode().isBlank()) return "EXCEPTION_REVIEW";
        if (note.proofOfDeliveryUri() != null && !note.proofOfDeliveryUri().isBlank()) return "DELIVERED";
        return "IN_TRANSIT";
    }

    public Map<String, Object> index(VoiceNote note) throws Exception {
        if (note.shipmentId() == null || note.shipmentId().isBlank() || note.eventId() == null || note.eventId().isBlank()
                || note.transcript() == null || note.transcript().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentId, eventId and transcript are required");
        String state = eventState(note);
        Map<String, Object> metadata = Map.of("shipmentId", note.shipmentId(), "eventId", note.eventId(), "transcript", note.transcript(), "state", state,
                "recordingUri", note.recordingUri() == null ? "" : note.recordingUri(),
                "proofOfDeliveryUri", note.proofOfDeliveryUri() == null ? "" : note.proofOfDeliveryUri());
        // The shipment/event pair is stable across retries of the same write.
        String id = note.shipmentId() + ":" + note.eventId();
        vector("/vector/upsert", Map.of("collection", collection, "vectors", List.of(Map.of("id", id, "embedding", embed(note.transcript()), "metadata", metadata))));
        return Map.of("eventId", id, "state", state);
    }

    public JsonNode search(SearchRequest request) throws Exception {
        if (request.shipmentId() == null || request.shipmentId().isBlank() || request.question() == null || request.question().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentId and question are required");
        return vector("/vector/query", Map.of("collection", collection, "embedding", embed(request.question()), "top_k", 5,
                "filter", Map.of("shipmentId", request.shipmentId()), "include_metadata", true)).path("matches");
    }

    private List<Float> embed(String text) {
        return ai.embeddings().create(EmbeddingCreateParams.builder().model("auto").input(text).build()).data().get(0).embedding();
    }

    private JsonNode vector(String path, Object body) throws Exception {
        String payload = json.writeValueAsString(body);
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = json.readTree(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                final long backoff = 1000L << attempt;
                long delay = response.headers().firstValue("Retry-After").map(value -> {
                    try { return Long.parseLong(value) * 1000L; } catch (NumberFormatException ignored) { return backoff; }
                }).orElse(backoff);
                Thread.sleep(delay);
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                HttpStatus status = response.statusCode() >= 400 && response.statusCode() < 500 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
                throw new ResponseStatusException(status, error.path("code").asText("REQUEST_REJECTED") + ": " + error.path("message").asText());
            }
            return envelope.path("data");
        }
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Retry later");
    }
}
