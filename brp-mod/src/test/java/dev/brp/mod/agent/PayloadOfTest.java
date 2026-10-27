package dev.brp.mod.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The telemetry payload round-trip (audit #3): the type tag travels inside
 * the JSON, numbers arrive as numbers, and a malformed line ships as an
 * empty map instead of killing the pipeline.
 */
class PayloadOfTest {

    @Test
    void numbersStayNumbersAndTypeIsExtracted() {
        String json = AgentRuntime.gsonlessJson(Map.of(
                "type", "agent.heartbeat", "mspt", 4.2, "tps", 19.5, "backlog", 7));
        Map<String, Object> payload = AgentRuntime.payloadOf(json);
        assertEquals(4.2, (Double) payload.get("mspt"), 1e-9);
        assertEquals(7.0, (Double) payload.get("backlog"), 1e-9);
        assertEquals("agent.heartbeat", payload.get("type"));
    }

    @Test
    void malformedLinesYieldEmptyPayloadsNotExceptions() {
        assertEquals(Map.of(), AgentRuntime.payloadOf("this is not json"));
        assertEquals(Map.of(), AgentRuntime.payloadOf(null));
        assertEquals(Map.of(), AgentRuntime.payloadOf("  "));
    }
}
