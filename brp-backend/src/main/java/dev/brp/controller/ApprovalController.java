package dev.brp.controller;

import dev.brp.dto.ApprovalDtos.Decision;
import dev.brp.dto.ApprovalDtos.NudgeAck;
import dev.brp.dto.ApprovalDtos.SubmitWithdrawalRequest;
import dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse;
import dev.brp.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The corporate labyrinth, exposed as REST. Submit, approve, reject, nudge —
 * the full paper trail.
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/requests")
    public WithdrawalRequestResponse submit(@Valid @RequestBody SubmitWithdrawalRequest req) {
        return approvalService.submitWithdrawalRequest(req);
    }

    @PostMapping("/requests/{id}/approve")
    public WithdrawalRequestResponse approve(@PathVariable String id,
                                             @Valid @RequestBody Decision decision) {
        return approvalService.approve(id, decision);
    }

    @PostMapping("/requests/{id}/reject")
    public WithdrawalRequestResponse reject(@PathVariable String id,
                                            @Valid @RequestBody Decision decision) {
        return approvalService.reject(id, decision);
    }

    /** "Per my last email" — the sacred corporate reminder ritual. */
    @PostMapping("/requests/{id}/nudge")
    public NudgeAck nudge(@PathVariable String id) {
        return approvalService.perMyLastEmail(id);
    }

    @GetMapping("/requests")
    public List<WithdrawalRequestResponse> inbox(@RequestParam String teamId,
                                                 @RequestParam(required = false) String status) {
        return approvalService.inbox(teamId, status);
    }
}
