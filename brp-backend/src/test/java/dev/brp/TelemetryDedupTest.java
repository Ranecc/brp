package dev.brp;

import dev.brp.contract.TelemetryDtos.EventEnvelope;
import dev.brp.contract.TelemetryDtos.TelemetryAck;
import dev.brp.contract.TelemetryDtos.TelemetryBatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Telemetry plane: same eventId delivered twice counts as one accepted, one duplicate. */
@SpringBootTest
@AutoConfigureMockMvc
class TelemetryDedupTest {

    @Autowired MockMvc mvc;

    private static String body(String eventId) {
        return """
                {"events":[{"eventId":"%s","type":"player.joined","schemaVersion":1,
                "agentId":"ag-1","tenantId":"t-1","reliability":"TELEMETRY","payload":{}}]}
                """.formatted(eventId);
    }

    @Test
    void duplicateEventIdsAreCountedAsDuplicates() throws Exception {
        String eventId = "ev-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/telemetry/batches")
                        .contentType(APPLICATION_JSON).content(body(eventId)))
                .andExpect(status().isOk());

        MvcResult result = mvc.perform(post("/api/v1/telemetry/batches")
                        .contentType(APPLICATION_JSON).content(body(eventId)))
                .andExpect(status().isOk()).andReturn();

        // second delivery: accepted=0, duplicates=1 — agent may advance its watermark
        assertThat(result.getResponse().getContentAsString()).contains("\"accepted\":0");
        assertThat(result.getResponse().getContentAsString()).contains("\"duplicates\":1");
    }

    @Test
    void oversizeBatchIsRejected() throws Exception {
        var events = new java.util.ArrayList<EventEnvelope>();
        for (int i = 0; i < 1001; i++) {
            events.add(new EventEnvelope("ev-" + UUID.randomUUID(), "player.moved", 1,
                    "ag-1", "t-1", null, "TELEMETRY", java.util.Map.of()));
        }
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(new TelemetryBatch(events));

        mvc.perform(post("/api/v1/telemetry/batches")
                        .contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}
