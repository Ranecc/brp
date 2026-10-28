package dev.brp.service;

import dev.brp.dto.MonetaryDtos.WealthAssessmentRow;

import java.util.List;

/**
 * The weekly wealth levy (docs/12 §6): step brackets, V<0.5 hoarding
 * surcharge, regime-gated, burned into SYSTEM_SINK — never spent.
 */
public interface WealthLevyService {

    List<WealthAssessmentRow> assess(String weekOf);

    List<WealthAssessmentRow> assessments(String weekOf);
}
