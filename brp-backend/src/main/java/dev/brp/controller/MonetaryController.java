package dev.brp.controller;

import dev.brp.dto.MonetaryDtos.RegimeResponse;
import dev.brp.dto.MonetaryDtos.SnapshotResponse;
import dev.brp.dto.MonetaryDtos.WealthAssessmentRow;
import dev.brp.service.RegimeService;
import dev.brp.service.SnapshotService;
import dev.brp.service.WealthLevyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The monetary console (docs/12 §14): regime, snapshot, the levy desks. */
@RestController
@RequestMapping("/api/v1/monetary")
public class MonetaryController {

    private final RegimeService regime;
    private final SnapshotService snapshots;
    private final WealthLevyService wealthLevy;

    public MonetaryController(RegimeService regime, SnapshotService snapshots,
                              WealthLevyService wealthLevy) {
        this.regime = regime;
        this.snapshots = snapshots;
        this.wealthLevy = wealthLevy;
    }

    @GetMapping("/regime")
    public RegimeResponse regime() {
        return regime.regime();
    }

    @PostMapping("/snapshot/run")
    public SnapshotResponse capture() {
        return snapshots.capture();
    }

    @GetMapping("/snapshot/latest")
    public SnapshotResponse latest() {
        return snapshots.latest();
    }

    @PostMapping("/wealth/{weekOf}/assess")
    public List<WealthAssessmentRow> assessWealth(@PathVariable String weekOf) {
        return wealthLevy.assess(weekOf);
    }

    @GetMapping("/wealth/{weekOf}")
    public List<WealthAssessmentRow> wealth(@PathVariable String weekOf) {
        return wealthLevy.assessments(weekOf);
    }
}
