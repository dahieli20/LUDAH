package com.datalakefilter.dto;

public record UploadDatasetResponse(
        String fileName,
        String path,
        String status,
        String message,
        String fileHash,
        Double partialMatchPercentage,
        String matchedWith,
        CsvQualityResult quality,
        Long size
) {
    public UploadDatasetResponse(
            String fileName,
            String path,
            String status,
            String message,
            String fileHash,
            Double partialMatchPercentage,
            String matchedWith,
            CsvQualityResult quality
    ) {
        this(
                fileName,
                path,
                status,
                message,
                fileHash,
                partialMatchPercentage,
                matchedWith,
                quality,
                null
        );
    }
}
