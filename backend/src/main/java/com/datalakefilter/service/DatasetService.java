package com.datalakefilter.service;

import com.datalakefilter.dto.DatasetDecisionRequest;
import com.datalakefilter.dto.CsvQualityResult;
import com.datalakefilter.dto.UploadDatasetResponse;
import com.datalakefilter.persistence.entity.DatasetRecord;
import com.datalakefilter.persistence.repository.DatasetRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DatasetService {

    private final MinioService minioService;
    private final HashService hashService;
    private final CsvQualityService csvQualityService;
    private final DatasetRecordRepository datasetRecordRepository;

    public DatasetService(
            MinioService minioService,
            HashService hashService,
            CsvQualityService csvQualityService,
            DatasetRecordRepository datasetRecordRepository
    ) {
        this.minioService = minioService;
        this.hashService = hashService;
        this.csvQualityService = csvQualityService;
        this.datasetRecordRepository = datasetRecordRepository;
    }

    public UploadDatasetResponse processDataset(MultipartFile file) {

        String fileName = file.getOriginalFilename();

        if (fileName == null || fileName.isBlank()) {
            return persist(new UploadDatasetResponse(
                    null,
                    null,
                    "REJECTED",
                    "El archivo no tiene nombre.",
                    null,
                    null,
                    null,
                    null
            ));
        }

        if (!fileName.toLowerCase().endsWith(".csv")) {
            return persist(new UploadDatasetResponse(
                    fileName,
                    null,
                    "REJECTED",
                    "Solo se permiten archivos CSV.",
                    null,
                    null,
                    null,
                    null
            ));
        }

        CsvQualityResult quality = csvQualityService.analyze(file);
        if (fileNameExistsInRaw(fileName)) {
                String rejectedPath = "rejected/" + fileName;

                minioService.uploadFile(rejectedPath, file);

                return persist(new UploadDatasetResponse(
                        fileName,
                        rejectedPath,
                        "REJECTED",
                        "Ya existe un archivo con el mismo nombre en raw/. El archivo fue rechazado directamente.",
                        null,
                        100.0,
                        "raw/" + fileName,
                        quality
                ));
        }

        if (!quality.hasDataRows()) {
            String rejectedPath = "rejected/" + fileName;

            minioService.uploadFile(rejectedPath, file);

            return persist(new UploadDatasetResponse(
                    fileName,
                    rejectedPath,
                    "REJECTED",
                    "El CSV no contiene filas de datos.",
                    null,
                    null,
                    null,
                    quality
            ));
        }

        if (quality.hasDuplicateColumns()) {
            String reviewPath = "review/" + fileName;

            minioService.uploadFile(reviewPath, file);

            return persist(new UploadDatasetResponse(
                    fileName,
                    reviewPath,
                    "REVIEW",
                    "El CSV contiene columnas duplicadas.",
                    null,
                    null,
                    null,
                    quality
            ));
        }

        if (quality.nullPercentage() >= 40.0) {
            String reviewPath = "review/" + fileName;

            minioService.uploadFile(reviewPath, file);

            return persist(new UploadDatasetResponse(
                    fileName,
                    reviewPath,
                    "REVIEW",
                    "El CSV contiene un porcentaje alto de valores nulos.",
                    null,
                    null,
                    null,
                    quality
            ));
        }

        String newFileHash = hashService.calculateFileHash(file);
        Set<String> newRowHashes = hashService.calculateRowHashes(file);

        List<String> rawFiles = minioService.listFiles("raw/");

        double highestPartialMatch = 0.0;
        String mostSimilarFile = null;

        for (String rawFile : rawFiles) {

            try (
                    InputStream existingFileForHash = minioService.getFile(rawFile);
                    InputStream existingFileForRows = minioService.getFile(rawFile)
            ) {

                String existingHash = hashService.calculateHash(existingFileForHash);

                if (newFileHash.equals(existingHash)) {

                    String rejectedPath = "rejected/" + fileName;

                    minioService.uploadFile(rejectedPath, file);

                    return persist(new UploadDatasetResponse(
                            fileName,
                            rejectedPath,
                            "REJECTED",
                            "Archivo duplicado exacto. Coincide con: " + rawFile,
                            newFileHash,
                            100.0,
                            rawFile,
                            quality
                    ));
                }

                Set<String> existingRowHashes =
                        hashService.calculateRowHashes(existingFileForRows);

                double matchPercentage =
                        calculateMatchPercentage(newRowHashes, existingRowHashes);

                if (matchPercentage > highestPartialMatch) {
                    highestPartialMatch = matchPercentage;
                    mostSimilarFile = rawFile;
                }

            } catch (Exception e) {
                throw new RuntimeException(
                        "Error al comparar archivo con: " + rawFile,
                        e
                );
            }
        }

        if (highestPartialMatch >= 65.0) {

            String reviewPath = "review/" + fileName;

            minioService.uploadFile(reviewPath, file);

            return persist(new UploadDatasetResponse(
                    fileName,
                    reviewPath,
                    "REVIEW",
                    buildPartialMatchMessage(highestPartialMatch, mostSimilarFile),
                    newFileHash,
                    highestPartialMatch,
                    mostSimilarFile,
                    quality
            ));
        }

        String rawPath = "raw/" + fileName;

        minioService.uploadFile(rawPath, file);

        return persist(new UploadDatasetResponse(
                fileName,
                rawPath,
                "ACCEPTED",
                "Archivo nuevo. Guardado en raw.",
                newFileHash,
                highestPartialMatch,
                mostSimilarFile,
                quality
        ));
    }

    public UploadDatasetResponse approveDataset(DatasetDecisionRequest request) {
        return resolveReviewDecision(
                request,
                "ACCEPTED",
                "raw",
                "Documento aprobado manualmente por el usuario. Guardado en raw."
        );
    }

    public UploadDatasetResponse rejectDataset(DatasetDecisionRequest request) {
        return resolveReviewDecision(
                request,
                "REJECTED",
                "rejected",
                "Documento rechazado manualmente por el usuario. Guardado en rejected."
        );
    }

    private double calculateMatchPercentage(
            Set<String> newRows,
            Set<String> existingRows
    ) {

        if (newRows == null || newRows.isEmpty() || existingRows == null || existingRows.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(newRows);

        intersection.retainAll(existingRows);

        int comparableRows = Math.min(newRows.size(), existingRows.size());

        return Math.round(
                ((intersection.size() * 100.0) / comparableRows) * 100.0
        ) / 100.0;
    }

        private String buildPartialMatchMessage(double matchPercentage, String matchedFile) {
        String formattedPercentage = formatPercentage(matchPercentage);

        if (Double.compare(matchPercentage, 100.0) == 0) {
                return "Coeficiente de solapamiento: 100% respecto del conjunto con menos filas únicas. Dataset comparado: "
                        + matchedFile
                        + ". Esto indica inclusión completa del conjunto menor, sin implicar duplicidad exacta. Podría tratarse de una copia parcial, una versión reducida "
                        + "o un subconjunto de datos ya almacenado. Se requiere revisión antes de incorporarlo al Data Lake.";
        }

        return "Coeficiente de solapamiento: "
                + formattedPercentage
                + "% respecto del conjunto con menos filas únicas. Dataset comparado: "
                + matchedFile
                + ". Esto puede indicar redundancia parcial, por lo que se recomienda revisión antes de almacenarlo definitivamente.";
        }

        private String formatPercentage(double percentage) {
                if (percentage % 1 == 0) {
                return String.valueOf((int) percentage);
                }

                return String.valueOf(percentage);
        }

    private UploadDatasetResponse resolveReviewDecision(
            DatasetDecisionRequest request,
            String status,
            String destinationFolder,
            String message
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "La solicitud de decisión manual es obligatoria."
            );
        }

        String sourcePath = validateReviewPath(request.path());
        String fileName = resolveFileName(request.fileName(), sourcePath);

        if ("ACCEPTED".equals(status) && fileNameExistsInRaw(fileName)) {
                String rejectedPath = "rejected/" + fileName;

                minioService.moveFile(sourcePath, rejectedPath);

                return persist(new UploadDatasetResponse(
                        fileName,
                        rejectedPath,
                        "REJECTED",
                        "No se puede aprobar el documento porque ya existe un archivo con el mismo nombre en raw/. Fue enviado a rejected/.",
                        request.fileHash(),
                        100.0,
                        "raw/" + fileName,
                        request.quality()
                ));
        }

        String destinationPath = destinationFolder + "/" + fileName;

        minioService.moveFile(sourcePath, destinationPath);

        return persist(new UploadDatasetResponse(
                fileName,
                destinationPath,
                status,
                message,
                request.fileHash(),
                request.partialMatchPercentage(),
                request.matchedWith(),
                request.quality()
        ));
    }

    private UploadDatasetResponse persist(UploadDatasetResponse response) {
        datasetRecordRepository.save(DatasetRecord.from(response));
        return response;
    }

    private String validateReviewPath(String path) {
        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "La ruta del archivo en revisión es obligatoria."
            );
        }

        if (!path.startsWith("review/")) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Solo se pueden aprobar o rechazar archivos ubicados en review."
            );
        }

        return path;
    }

    private String resolveFileName(String fileName, String sourcePath) {
        String resolvedFileName = fileName;

        if (resolvedFileName == null || resolvedFileName.isBlank()) {
            int separatorIndex = sourcePath.lastIndexOf("/");
            resolvedFileName = separatorIndex >= 0
                    ? sourcePath.substring(separatorIndex + 1)
                    : sourcePath;
        }

        if (resolvedFileName.isBlank()
                || resolvedFileName.contains("/")
                || resolvedFileName.contains("\\")) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "El nombre del archivo no es válido."
            );
        }

        return resolvedFileName;
    }

    private boolean fileNameExistsInRaw(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        return minioService.listFiles("raw/")
                .stream()
                .map(this::extractFileName)
                .anyMatch(existingFileName ->
                        existingFileName.equalsIgnoreCase(fileName.trim())
                );
    }

    private String extractFileName(String objectPath) {
        int separatorIndex = objectPath.lastIndexOf("/");
        return separatorIndex >= 0
                ? objectPath.substring(separatorIndex + 1)
                : objectPath;
    }
}
