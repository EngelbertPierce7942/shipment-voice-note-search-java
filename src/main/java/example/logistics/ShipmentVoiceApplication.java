package example.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ShipmentVoiceApplication {
    private final ShipmentVoiceService service;
    public ShipmentVoiceApplication(ShipmentVoiceService service) { this.service = service; }
    public static void main(String[] args) { SpringApplication.run(ShipmentVoiceApplication.class, args); }

    @PostMapping("/shipments/events")
    public Map<String, Object> index(@RequestBody ShipmentVoiceService.VoiceNote note) throws Exception { return service.index(note); }

    @PostMapping("/shipments/search")
    public JsonNode search(@RequestBody ShipmentVoiceService.SearchRequest request) throws Exception { return service.search(request); }
}
