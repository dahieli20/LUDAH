package com.datalakefilter.persistence.entity;

import com.datalakefilter.dto.DataLakeAuditResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_records")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audited_prefix", nullable = false, length = 1024)
    private String auditedPrefix;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "raw_files", nullable = false)
    private int rawFiles;

    @Column(name = "review_files", nullable = false)
    private int reviewFiles;

    @Column(name = "rejected_files", nullable = false)
    private int rejectedFiles;

    @Column(name = "ingestion_risk_percentage", nullable = false)
    private double ingestionRiskPercentage;

    @Column(name = "quality_risk_percentage", nullable = false)
    private double qualityRiskPercentage;

    @Column(name = "exact_duplication_risk_percentage", nullable = false)
    private double exactDuplicationRiskPercentage;

    @Column(name = "redundancy_risk_percentage", nullable = false)
    private double redundancyRiskPercentage;

    @Column(name = "data_swamp_index_percentage", nullable = false)
    private double dataSwampIndexPercentage;

    @Column(nullable = false, length = 64)
    private String classification;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "clean_max")
    private Double cleanMax;

    @Column(name = "frontier_max")
    private Double frontierMax;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditRecord() {
    }

    public static AuditRecord from(
            String prefix,
            DataLakeAuditResponse response,
            String motivo
    ) {
        AuditRecord record = new AuditRecord();
        record.auditedPrefix = prefix == null || prefix.isBlank()
                ? "GLOBAL"
                : prefix;

        record.totalFiles = response.totalFiles();
        record.rawFiles = response.rawFiles();
        record.reviewFiles = response.reviewFiles();
        record.rejectedFiles = response.rejectedFiles();
        record.ingestionRiskPercentage = response.ingestionRiskPercentage();
        record.qualityRiskPercentage = response.qualityRiskPercentage();
        record.exactDuplicationRiskPercentage =
                response.exactDuplicationRiskPercentage();
        record.redundancyRiskPercentage = response.redundancyRiskPercentage();
        record.dataSwampIndexPercentage = response.dataSwampIndexPercentage();
        record.classification = response.classification();
        record.message = response.message();
        record.cleanMax = response.cleanMax();
        record.frontierMax = response.frontierMax();
        record.motivo = motivo == null || motivo.isBlank()
                ? "NO_REGISTRADO"
                : motivo;

        return record;
    }

    public static AuditRecord from(
            String prefix,
            DataLakeAuditResponse response
    ) {
        return from(prefix, response, "NO_REGISTRADO");
    }

    @Column(name = "motivo", nullable = false, length = 50)
    private String motivo;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getMotivo() {
        return motivo;
    }

}
