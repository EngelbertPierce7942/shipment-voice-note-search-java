package example.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ShipmentVoiceServiceTest {
    @Test void exceptionRequiresReviewEvenWhenDeliveryProofExists() {
        var note = new ShipmentVoiceService.VoiceNote("S-42", "E-9", "Driver reported a damaged seal", "recordings/e9.wav", "proof/e9.jpg", "SEAL_DAMAGE");
        assertEquals("EXCEPTION_REVIEW", ShipmentVoiceService.eventState(note));
        assertEquals("DELIVERED", ShipmentVoiceService.eventState(new ShipmentVoiceService.VoiceNote("S-42", "E-10", "Signed delivery", "recordings/e10.wav", "proof/e10.jpg", null)));
    }
}
