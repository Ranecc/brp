package dev.brp.service;

import dev.brp.dto.ApprovalDtos.Decision;
import dev.brp.dto.ApprovalDtos.NudgeAck;
import dev.brp.dto.ApprovalDtos.SubmitWithdrawalRequest;
import dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse;

import java.util.List;

/**
 * Guild treasury withdrawal approvals (docs/07-tenants.md). The corporate
 * approval labyrinth, faithfully recreated in a sandbox game.
 */
public interface ApprovalService {

    WithdrawalRequestResponse submitWithdrawalRequest(SubmitWithdrawalRequest req);

    WithdrawalRequestResponse approve(String requestId, Decision decision);

    WithdrawalRequestResponse reject(String requestId, Decision decision);

    NudgeAck perMyLastEmail(String requestId);

    List<WithdrawalRequestResponse> inbox(String teamId, String status);
}
