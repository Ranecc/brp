package dev.brp.signal;

import dev.brp.contract.SignalDtos.SignalAck;
import dev.brp.contract.SignalDtos.SignalCommandDto;
import dev.brp.contract.SignalDtos.SignalPollResult;
import dev.brp.contract.SignalDtos.SignalSetCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * At-least-once dispatch with ack'd delivery; agent-side execution is
 * last-writer-wins idempotent (docs/02 section 5, docs/04 section 3).
 */
@Service
public class SignalServiceImpl implements SignalService {

    private final SignalRepository commands;

    public SignalServiceImpl(SignalRepository commands) {
        this.commands = commands;
    }

    @Override
    public SignalCommandDto enqueue(String tenantId, SignalSetCommand cmd) {
        return toDto(commands.save(new SignalCommandEntity(tenantId, cmd.channel(), cmd.level())));
    }

    @Override
    public SignalPollResult poll(String tenantId) {
        List<SignalCommandEntity> pending =
                commands.findTop50ByTenantIdAndStatusOrderByIdAsc(tenantId, "PENDING");
        return new SignalPollResult(pending.stream().map(SignalServiceImpl::toDto).toList(),
                pending.stream().mapToLong(SignalCommandEntity::getId).max().orElse(0));
    }

    @Override
    @Transactional
    public SignalAck ack(String tenantId, SignalAck ack) {
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
