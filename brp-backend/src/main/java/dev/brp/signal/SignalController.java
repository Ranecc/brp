package dev.brp.signal;

import dev.brp.contract.SignalDtos.SignalAck;
import dev.brp.contract.SignalDtos.SignalCommandDto;
import dev.brp.contract.SignalDtos.SignalPollResult;
import dev.brp.contract.SignalDtos.SignalSetCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin transport layer for the signal push plane. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/signals")
public class SignalController {

    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @PostMapping
    public SignalCommandDto enqueue(@PathVariable String tenantId,
                                    @Valid @RequestBody SignalSetCommand cmd) {
        return signalService.enqueue(tenantId, cmd);
    }

    @GetMapping("/poll")
    public SignalPollResult poll(@PathVariable String tenantId) {
        return signalService.poll(tenantId);
    }

    @PostMapping("/ack")
    public SignalAck ack(@PathVariable String tenantId, @Valid @RequestBody SignalAck ack) {
        return signalService.ack(tenantId, ack);
    }
}
