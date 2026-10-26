package dev.brp;

import dev.brp.service.TpsReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The TPS report stops being theater: agent heartbeats flow through the
 * telemetry plane into the fleet monitor, and the report reads real mspt.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HeartbeatTpsTest {

    @Autowired MockMvc mvc;
    @Autowired TpsReportService tpsReports;
    @Autowired dev.brp.repository.AgentStateRepository agents;

    private void heartbeat(String eventId, String agentId, double mspt) throws Exception {
        String body = """
                {"events":[{"eventId":"%s","type":"agent.heartbeat","schemaVersion":1,
                "agentId":"%s","tenantId":null,"reliability":"TELEMETRY",
                "payload":{"mspt":%s,"tps":20.0,"backlog":2}}]}
                """.formatted(eventId, agentId, mspt);
        mvc.perform(post("/api/v1/telemetry/batches")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void heartbeatsMaterializeIntoRealReportNumbers() throws Exception {
        heartbeat("hb-" + java.util.UUID.randomUUID(), "ag-farm-cluster", 8.0);
        heartbeat("hb-" + java.util.UUID.randomUUID(), "ag-museum-server", 12.0);

        // order-proof: the report must reflect exactly the fleet's stored mean
        double expected = agents.findAll().stream()
                .mapToDouble(dev.brp.entity.AgentStateEntity::getMspt).average().orElse(0.0);
        var report = tpsReports.fileWeeklyReport(true);
        assertThat(report.averageMspt()).isEqualTo(expected);
        assertThat(expected).isGreaterThanOrEqualTo(10.0 * 2 / 2 - 2.0); // ours landed
        assertThat(report.lines()).anyMatch(l -> l.contains("Ticks per second"));
    }

    @Test
    void duplicateHeartbeatsDoNotSkewTheFleetAverage() throws Exception {
        String eventId = "hb-" + java.util.UUID.randomUUID();
        heartbeat(eventId, "ag-lonely", 20.0);
        heartbeat(eventId, "ag-lonely", 20.0);   // exact redelivery

        // upsert by agent id: exactly one row for ag-lonely, value unchanged
        var lonely = agents.findAll().stream()
                .filter(a -> a.getAgentId().equals("ag-lonely")).toList();
        assertThat(lonely).hasSize(1);
        assertThat(lonely.get(0).getMspt()).isEqualTo(20.0);
    }
}
