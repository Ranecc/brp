package dev.brp.controller;

import dev.brp.dto.ReconciliationDtos.FindingResponse;
import dev.brp.dto.ReconciliationDtos.RunReport;
import dev.brp.service.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The reconciliation desk (docs/10 R-1): the invariants, in writing. */
@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService desk;

    public ReconciliationController(ReconciliationService desk) {
        this.desk = desk;
    }

    @PostMapping("/run")
    public RunReport run() {
        return desk.reconcile();
    }

    @GetMapping("/findings")
    public List<FindingResponse> findings() {
        return desk.findings();
    }
}
