package com.datalakefilter.persistence.entity;

import com.datalakefilter.dto.CsvQualityResult;
import com.datalakefilter.dto.UploadDatasetResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dataset_records")
public class DatasetRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "object_path", length = 1024)
    private String objectPath;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "partial_match_percentage")
    private Double partialMatchPercentage;

    @Column(name = "matched_with", length = 1024)
    private String matchedWith;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "total_columns")
    private Integer totalColumns;

    @Column(name = "null_percentage")
    private Double nullPercentage;

    @Column(name = "duplicate_rows_percentage")
    private Double duplicateRowsPercentage;

    @Column(name = "has_duplicate_columns")
    private Boolean hasDuplicateColumns;

    @Column(name = "has_data_rows")
    private Boolean hasDataRows;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DatasetRecord() {
    }

    public static DatasetRecord from(UploadDatasetResponse response) {
        DatasetRecord record = new DatasetRecord();
        record.fileName = response.fileName() == null ? "SIN_NOMBRE" : response.fileName();
        record.objectPath = response.path();
        record.status = response.status();
        record.message = response.message();
        record.fileHash = response.fileHash();
        record.partialMatchPercentage = response.partialMatchPercentage();
        record.matchedWith = response.matchedWith();

        CsvQualityResult quality = response.quality();
        if (quality != null) {
            record.totalRows = quality.totalRows();
            record.totalColumns = quality.totalColumns();
            record.nullPercentage = quality.nullPercentage();
            record.duplicateRowsPercentage = quality.duplicateRowsPercentage();
            record.hasDuplicateColumns = quality.hasDuplicateColumns();
            record.hasDataRows = quality.hasDataRows();
        }

        return record;
    }

    public UploadDatasetResponse toResponse(
            String currentPath,
            String currentStatus,
            long size
    ) {
        CsvQualityResult quality = null;

        if (totalRows != null
                && totalColumns != null
                && nullPercentage != null
                && duplicateRowsPercentage != null
                && hasDuplicateColumns != null
                && hasDataRows != null) {
            quality = new CsvQualityResult(
                    totalRows,
                    totalColumns,
                    nullPercentage,
                    duplicateRowsPercentage,
                    hasDuplicateColumns,
                    hasDataRows
            );
        }

        return new UploadDatasetResponse(
                fileName,
                currentPath,
                currentStatus,
                message,
                fileHash,
                partialMatchPercentage,
                matchedWith,
                quality,
                size
        );
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
