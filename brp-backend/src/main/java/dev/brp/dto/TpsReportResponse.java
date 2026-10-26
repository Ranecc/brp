package dev.brp.dto;

import java.util.List;

/** Weekly TPS report. Cover sheet not included unless requested. Flair counted. */
public record TpsReportResponse(
        String reportId,
        String period,
        boolean coverSheetIncluded,
        double averageMspt,
        double synergyScore,            // unitless, unfalsifiable
        int piecesOfFlair,              // minimum 15
        boolean caseOfTheMondaysDetected,
        List<String> lines) {}
