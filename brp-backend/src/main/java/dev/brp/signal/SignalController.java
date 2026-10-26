package dev.brp.signal;

import dev.brp.contract.SignalDtos.SignalAck;
import dev.brp.contract.SignalDtos.SignalCommandDto;
import dev.brp.contract.SignalDtos.SignalPollResult;
import dev.brp.contract.SignalDtos.SignalSetCommand;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Signal dispatch: the policy engine (docs/04 section 3) enqueues here, the
 * agent polls, applies the level to the channel, and only then acks —
 * at-least-once delivery with last-writer-wins idempotent execution.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/signals")
public class SignalController {

    private final SignalRepository commands;

    public SignalController(SignalRepository commands) {
        this.commands = commands;
    }

    @PostMapping
    public SignalCommandDto enqueue(@PathVariable String tenantId,
                                    @Valid @RequestBody SignalSetCommand cmd) {
        var saved = commands.save(new SignalCommandEntity(tenantId, cmd.channel(), cmd.level()));
        return toDto(saved);
    }

    @GetMapping("/poll")
    public SignalPollResult poll(@PathVariable String tenantId) {
        List<SignalCommandEntity> pending =
                commands.findTop50ByTenantIdAndStatusOrderByIdAsc(tenantId, "PENDING");
        return new SignalPollResult(pending.stream().map(SignalController::toDto).toList(),
                pending.stream().mapToLong(SignalCommandEntity::getId).max().orElse(0));
    }

    @PostMapping("/ack")
    @Transactional
    public SignalAck ack(@PathVariable String tenantId, @Valid @RequestBody SignalAck ack) {
        for (var cmd : commands.findByTenantIdAndIdIn(tenantId, ack.ids())) {
            cmd.markDelivered();
        }
        return ack;
    }

    private static SignalCommandDto toDto(SignalCommandEntity e) {
        return new SignalCommandDto(e.getId(), e.getChannel(), e.getLevel(),
                e.getStatus(), e.getCreatedAt().toString());
    }
}
