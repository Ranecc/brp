package dev.brp.signal;

import dev.brp.contract.SignalDtos.SignalAck;
import dev.brp.contract.SignalDtos.SignalCommandDto;
import dev.brp.contract.SignalDtos.SignalPollResult;
import dev.brp.contract.SignalDtos.SignalSetCommand;

/** Signal push plane contract (impl: {@link SignalServiceImpl}). */
public interface SignalService {

    SignalCommandDto enqueue(String tenantId, SignalSetCommand cmd);

    SignalPollResult poll(String tenantId);

    SignalAck ack(String tenantId, SignalAck ack);
}
