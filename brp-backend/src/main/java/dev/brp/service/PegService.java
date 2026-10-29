package dev.brp.service;

import dev.brp.entity.PegStateEntity;

import java.util.List;

/**
 * The two anchor legs (docs/12 §9): band absorbs +/-5% drift; two
 * consecutive out-of-band readings auto-file a re-peg vote; execution
 * rides the decision, atomically, with history.
 */
public interface PegService {

    List<PegStateEntity> legs();

    /** Feed one market reading; files the vote on the second consecutive miss. */
    void observe(String leg, double observedRate);

    /** Execute an APPROVED re-peg vote. */
    void executeRepeg(long requestId, String decidedBy);

    /** Approve/reject a pending policy request. */
    void decide(long requestId, boolean approved, String decidedBy);
}
