package dev.brp.mod.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The meter's arithmetic (docs/09 section 3): report loaded ∩ registered,
 * never unregistered loads (rogue loaders are the detective's case, not the
 * meter's) and never registered-but-idle chunks (no load, no bill).
 */
class ChunkMinutesTest {

    @Test
    void intersectionOnlyAndTheKeySplitsCleanly() {
        Set<String> loaded = Set.of(
                "minecraft:overworld|0,0",
                "minecraft:overworld|1,1",     // loaded but NOT registered: ignored
                "minecraft:the_nether|7,7");
        Set<String> registered = Set.of(
                "minecraft:overworld|0,0",
                "minecraft:the_nether|7,7",
                "minecraft:overworld|9,9");    // registered but NOT loaded: ignored

        List<Map<String, Object>> payloads = AgentRuntime.chunkMinutePayloads(loaded, registered);
        assertEquals(2, payloads.size());
        var first = payloads.stream()
                .filter(p -> p.get("chunkKey").equals("0,0")).findFirst().orElseThrow();
        assertEquals("minecraft:overworld", first.get("dimension"));
        assertEquals("chunk.load.minutes", first.get("type"));
        assertEquals(1L, first.get("minutes"));
    }

    @Test
    void emptySetsBillNobody() {
        assertEquals(List.of(), AgentRuntime.chunkMinutePayloads(Set.of(), Set.of("a|0,0")));
        assertEquals(List.of(), AgentRuntime.chunkMinutePayloads(Set.of("a|0,0"), Set.of()));
    }
}
