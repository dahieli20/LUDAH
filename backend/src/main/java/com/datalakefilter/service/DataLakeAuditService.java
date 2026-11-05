package com.datalakefilter.service;

import com.datalakefilter.dto.AuditFactorResponse;
import com.datalakefilter.dto.DataLakeAuditResponse;
import com.datalakefilter.dto.RiskThresholdConfigResponse;
import com.datalakefilter.dto.CsvQualityResult;
import com.datalakefilter.persistence.entity.AuditRecord;
import com.datalakefilter.persistence.repository.AuditRecordRepository;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DataLakeAuditService {

    private static final double QUALITY_WEIGHT = 0.35;
    private static final double EXACT_DUPLICATION_WEIGHT = 0.30;
    private static final double REDUNDANCY_WEIGHT = 0.35;
    private final RiskThresholdConfigService riskThresholdConfigService;

    private final CsvQualityService csvQualityService;
    private final HashService hashService;
    private final MinioClient minioClient;
    private final AuditRecordRepository auditRecordRepository;

    @Value("${minio.bucket}")
    private String bucketName;

    public DataLakeAuditService(
            MinioClient minioClient,
            RiskThresholdConfigService riskThresholdConfigService,
            AuditRecordRepository auditRecordRepository,
            CsvQualityService csvQualityService,
            HashService hashService
    ) {
        this.csvQualityService = csvQualityService;
        this.hashService = hashService;
        this.minioClient = minioClient;
        this.riskThresholdConfigService = riskThresholdConfigService;
        this.auditRecordRepository = auditRecordRepository;
    }

    public DataLakeAuditResponse auditDataLake() {
        DataLakeAuditResponse response = calculateDataLakeAudit();
        auditRecordRepository.save(AuditRecord.from("GLOBAL", response));
        return response;
    }

    private DataLakeAuditResponse calculateDataLakeAudit() {
        RiskThresholdConfigResponse config = riskThresholdConfigService.getConfig();
        List<String> rawFiles = listCsvFiles("raw/");
        List<String> reviewFiles = listCsvFiles("review/");
        List<String> rejectedFiles = listCsvFiles("rejected/");

        int rawCount = rawFiles.size();
        int reviewCount = reviewFiles.size();
        int rejectedCount = rejectedFiles.size();
        int totalFiles = rawCount + reviewCount + rejectedCount;
        double ingestionRisk = calculateIngestionRisk(totalFiles, reviewCount, rejectedCount);

        if (totalFiles == 0) {
            return buildEmptyResponse(
                    totalFiles,
                    rawCount,
                    reviewCount,
                    rejectedCount,
                    ingestionRisk,
                    "El Data Lake todavía no contiene archivos para auditar.", config, "GLOBAL", "raw/"
            );
        }

        if (rawFiles.isEmpty()) {
            return buildEmptyResponse(
                    totalFiles,
                    rawCount,
                    reviewCount,
                    rejectedCount,
                    ingestionRisk,
                    "No hay datasets activos en raw/ para calcular el DSI. El IRI del flujo de ingestión es "
                            + ingestionRisk
                            + "%.", config, "GLOBAL", "raw/"
            );
        }

        RepositoryAuditMetrics metrics = calculateRepositoryMetrics(rawFiles);
        double dataSwampIndex = calculateDataSwampIndex(metrics);
        String classification = classify(dataSwampIndex, config);

        return new DataLakeAuditResponse(
                totalFiles,
                rawCount,
                reviewCount,
                rejectedCount,
                ingestionRisk,
                metrics.qualityRisk(),
                metrics.exactDuplicationRisk(),
                metrics.redundancyRisk(),
                dataSwampIndex,
                dataSwampIndex,
                classification,
                buildDataLakeMessage(classification, dataSwampIndex, ingestionRisk),
                buildDsiFactors(metrics), "GLOBAL", "raw/", config.cleanMax(), config.frontierMax()
        );
    }

    public DataLakeAuditResponse auditRepository(String prefix) {
        String normalizedPrefix = normalizePrefix(prefix);
        DataLakeAuditResponse response = calculateRepositoryAudit(normalizedPrefix);
        auditRecordRepository.save(AuditRecord.from(normalizedPrefix, response));
        return response;
    }

    private DataLakeAuditResponse calculateRepositoryAudit(String normalizedPrefix) {
        RiskThresholdConfigResponse config = riskThresholdConfigService.getConfig();
        List<String> repositoryFiles = listCsvFiles(normalizedPrefix);
        int totalFiles = repositoryFiles.size();

        if (totalFiles == 0) {
            return buildEmptyResponse(
                    0,
                    0,
                    0,
                    0,
                    0,
                    "No se encontraron archivos CSV para auditar en la ruta indicada.", config, "REPOSITORY", normalizedPrefix
            );
        }

        RepositoryAuditMetrics metrics = calculateRepositoryMetrics(repositoryFiles);
        double dataSwampIndex = calculateDataSwampIndex(metrics);
        String classification = classify(dataSwampIndex, config);

        return new DataLakeAuditResponse(
                totalFiles,
                totalFiles,
                0,
                0,
                0,
                metrics.qualityRisk(),
                metrics.exactDuplicationRisk(),
                metrics.redundancyRisk(),
                dataSwampIndex,
                dataSwampIndex,
                classification,
                buildRepositoryMessage(classification, dataSwampIndex),
                buildDsiFactors(metrics), "REPOSITORY", normalizedPrefix, config.cleanMax(), config.frontierMax()
        );
    }

    private DataLakeAuditResponse buildEmptyResponse(
            int totalFiles,
            int rawFiles,
            int reviewFiles,
            int rejectedFiles,
            double ingestionRisk,
            String message, RiskThresholdConfigResponse config, String mode, String prefix
    ) {
        return new DataLakeAuditResponse(
                totalFiles,
                rawFiles,
                reviewFiles,
                rejectedFiles,
                ingestionRisk,
                0,
                0,
                0,
                0,
                0,
                "SIN_DATOS",
                message,
                List.of(), mode, prefix, config.cleanMax(), config.frontierMax()
        );
    }

    private RepositoryAuditMetrics calculateRepositoryMetrics(List<String> repositoryFiles) {
        List<RepositoryFileFingerprint> fingerprints = buildFingerprints(repositoryFiles);

        return new RepositoryAuditMetrics(
                calculateQualityRisk(repositoryFiles),
                calculateExactDuplicationRisk(fingerprints),
                calculatePartialRedundancyRisk(fingerprints)
        );
    }

    private List<RepositoryFileFingerprint> buildFingerprints(List<String> repositoryFiles) {
        List<RepositoryFileFingerprint> fingerprints = new ArrayList<>();

        for (String repositoryFile : repositoryFiles) {
            fingerprints.add(new RepositoryFileFingerprint(
                    repositoryFile,
                    calculateSha256(repositoryFile),
                    readNormalizedDataRows(repositoryFile)
            ));
        }

        return fingerprints;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return "";
        }

        String normalized = prefix.trim();

        if (normalized.equals("/")) {
            return "";
        }

        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }

        return normalized;
    }

    private List<String> listCsvFiles(String prefix) {
        List<String> files = new ArrayList<>();

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();

                if (!item.isDir() && item.objectName().toLowerCase().endsWith(".csv")) {
                    files.add(item.objectName());
                }
            }

            return files;
        } catch (Exception e) {
            throw new RuntimeException("Error al listar archivos en MinIO: " + prefix, e);
        }
    }

    private double calculateIngestionRisk(int totalFiles, int reviewFiles, int rejectedFiles) {
        if (totalFiles == 0) {
            return 0;
        }

        return round(((reviewFiles * 50.0) + (rejectedFiles * 100.0)) / totalFiles);
    }

    private double calculateQualityRisk(List<String> repositoryFiles) {
        if (repositoryFiles.isEmpty()) {
            return 0;
        }

        double totalRisk = 0;

        for (String repositoryFile : repositoryFiles) {
            totalRisk += auditCsvQualityRisk(repositoryFile);
        }

        return round(totalRisk / repositoryFiles.size());
    }

    private double auditCsvQualityRisk(String objectName) {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
            CsvQualityResult quality = csvQualityService.analyze(inputStream);
            if (!quality.hasDataRows()) return 100;
            return round(quality.nullPercentage() * 0.50
                    + quality.duplicateRowsPercentage() * 0.30
                    + (quality.hasDuplicateColumns() ? 100 : 0) * 0.20);
        } catch (Exception e) {
            throw new RuntimeException("Error al auditar calidad del archivo: " + objectName, e);
        }
    }

    private double calculateExactDuplicationRisk(List<RepositoryFileFingerprint> fingerprints) {
        if (fingerprints.isEmpty()) {
            return 0;
        }

        Set<String> uniqueHashes = new HashSet<>();

        for (RepositoryFileFingerprint fingerprint : fingerprints) {
            uniqueHashes.add(fingerprint.sha256());
        }

        int duplicatedFiles = fingerprints.size() - uniqueHashes.size();

        return round((duplicatedFiles * 100.0) / fingerprints.size());
    }

    private double calculatePartialRedundancyRisk(List<RepositoryFileFingerprint> fingerprints) {
        if (fingerprints.size() <= 1) {
            return 0;
        }

        Map<String, Double> riskByFile = new HashMap<>();

        for (RepositoryFileFingerprint fingerprint : fingerprints) {
            riskByFile.put(fingerprint.objectName(), 0.0);
        }

        for (int i = 0; i < fingerprints.size(); i++) {
            for (int j = i + 1; j < fingerprints.size(); j++) {
                RepositoryFileFingerprint first = fingerprints.get(i);
                RepositoryFileFingerprint second = fingerprints.get(j);

                double pairRisk = calculatePairRedundancyRisk(first, second);

                if (pairRisk > 0) {
                    riskByFile.put(
                            first.objectName(),
                            Math.max(riskByFile.get(first.objectName()), pairRisk)
                    );
                    riskByFile.put(
                            second.objectName(),
                            Math.max(riskByFile.get(second.objectName()), pairRisk)
                    );
                }
            }
        }

        double totalRisk = 0;

        for (double risk : riskByFile.values()) {
            totalRisk += risk;
        }

        return round(totalRisk / fingerprints.size());
    }

    private double calculatePairRedundancyRisk(
            RepositoryFileFingerprint first,
            RepositoryFileFingerprint second
    ) {
        if (first.sha256().equals(second.sha256())) {
            return 0;
        }

        double similarity = calculateRowSimilarityPercentage(
                first.normalizedRows(),
                second.normalizedRows()
        );

        if (similarity == 100) {
            return 100;
        }

        if (similarity >= 95) {
            return 90;
        }

        if (similarity >= 80) {
            return 70;
        }

        if (similarity >= 65) {
            return 45;
        }

        return 0;
    }

    private double calculateRowSimilarityPercentage(Set<String> firstRows, Set<String> secondRows) {
        if (firstRows.isEmpty() || secondRows.isEmpty()) {
            return 0;
        }

        Set<String> intersection = new HashSet<>(firstRows);
        intersection.retainAll(secondRows);

        int comparableRows = Math.min(firstRows.size(), secondRows.size());

        return round((intersection.size() * 100.0) / comparableRows);
    }

    private Set<String> readNormalizedDataRows(String objectName) {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
            return hashService.calculateRowHashes(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Error al leer filas del archivo: " + objectName, e);
        }
    }

    private String calculateSha256(String objectName) {
        try (
                InputStream inputStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build()
                )
        ) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hash del archivo: " + objectName, e);
        }
    }

    private double calculateDataSwampIndex(RepositoryAuditMetrics metrics) {
        return round(
                (metrics.qualityRisk() * QUALITY_WEIGHT)
                        + (metrics.exactDuplicationRisk() * EXACT_DUPLICATION_WEIGHT)
                        + (metrics.redundancyRisk() * REDUNDANCY_WEIGHT)
        );
    }

    private List<AuditFactorResponse> buildDsiFactors(RepositoryAuditMetrics metrics) {
        return List.of(
                new AuditFactorResponse(
                        "Q - Riesgo por calidad",
                        metrics.qualityRisk(),
                        QUALITY_WEIGHT,
                        round(metrics.qualityRisk() * QUALITY_WEIGHT),
                        "Calcula Qi = 0,50Ni + 0,30Fi + 0,20Ci sobre nulos, filas duplicadas internas y columnas duplicadas."
                ),
                new AuditFactorResponse(
                        "D - Riesgo por duplicidad exacta",
                        metrics.exactDuplicationRisk(),
                        EXACT_DUPLICATION_WEIGHT,
                        round(metrics.exactDuplicationRisk() * EXACT_DUPLICATION_WEIGHT),
                        "Calcula D = 100 × (N - H) / N mediante hash global SHA-256."
                ),
                new AuditFactorResponse(
                        "R - Riesgo por redundancia parcial",
                        metrics.redundancyRisk(),
                        REDUNDANCY_WEIGHT,
                        round(metrics.redundancyRisk() * REDUNDANCY_WEIGHT),
                        "Compara hashes por fila y convierte la similitud en riesgo con la escala 65%, 80%, 95% y 100%."
                )
        );
    }

    private String classify(double dataSwampIndex, RiskThresholdConfigResponse config) {
        double cleanMax = config.cleanMax();
        double frontierMax = config.frontierMax();

        if (dataSwampIndex <= cleanMax) {
            return "DATA_LAKE_SALUDABLE";
        }

        if (dataSwampIndex <= frontierMax) {
            return "ZONA_FRONTERA";
        }

        return "DATA_SWAMP";
    }

    private String buildDataLakeMessage(
            String classification,
            double dataSwampIndex,
            double ingestionRisk
    ) {
        return switch (classification) {
            case "DATA_LAKE_SALUDABLE" ->
                    "DSI: " + dataSwampIndex + "%. El Data Lake activo se mantiene saludable. IRI: "
                            + ingestionRisk
                            + "%.";
            case "ZONA_FRONTERA" ->
                    "DSI: " + dataSwampIndex + "%. El Data Lake presenta señales de degradación. IRI: "
                            + ingestionRisk
                            + "%.";
            case "DATA_SWAMP" ->
                    "DSI: " + dataSwampIndex + "%. El Data Lake presenta alto riesgo de Data Swamp. IRI: "
                            + ingestionRisk
                            + "%.";
            default ->
                    "No se pudo determinar la clasificación del Data Lake.";
        };
    }

    private String buildRepositoryMessage(String classification, double dataSwampIndex) {
        return switch (classification) {
            case "DATA_LAKE_SALUDABLE" ->
                    "El repositorio auditado se mantiene saludable. DSI: " + dataSwampIndex + "%.";
            case "ZONA_FRONTERA" ->
                    "El repositorio auditado se encuentra en zona frontera. DSI: " + dataSwampIndex + "%.";
            case "DATA_SWAMP" ->
                    "El repositorio auditado presenta alto riesgo de Data Swamp. DSI: " + dataSwampIndex + "%.";
            default ->
                    "No se pudo determinar la clasificación del repositorio auditado.";
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record RepositoryAuditMetrics(
            double qualityRisk,
            double exactDuplicationRisk,
            double redundancyRisk
    ) {
    }

    private record RepositoryFileFingerprint(
            String objectName,
            String sha256,
            Set<String> normalizedRows
    ) {
    }
}
