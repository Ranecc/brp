package dev.brp.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {}

    public record InventoryLine(
            @NotBlank String containerId,
            @NotBlank String itemId,
            @NotNull Long amount,
            @NotBlank String capturedAt) {}

    /** batchMode: DELTA (amounts are increments) or SNAPSHOT (amounts are absolute levels). */
    public record InventoryBatch(
            @NotBlank String batchMode,
            @NotEmpty @Size(max = 1000) List<InventoryLine> lines) {}   // telemetry caps at 1000; so do we

    public record InventoryAck(int upserted) {}
}
