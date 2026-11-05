package com.datalakefilter.dto;

import java.util.List;

public record DataLakeAuditResponse(
        int totalFiles,
        int rawFiles,
        int reviewFiles,
        int rejectedFiles,
        double ingestionRiskPercentage,
        double qualityRiskPercentage,
        double exactDuplicationRiskPercentage,
        double redundancyRiskPercentage,
        double dataSwampIndexPercentage,
        double globalRiskPercentage,
        String classification,
        String message,
        List<AuditFactorResponse> factors,
        String auditMode,
        String auditedPrefix,
        Double cleanMax,
        Double frontierMax
) {
}
