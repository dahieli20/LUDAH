package com.datalakefilter.service;

import com.datalakefilter.dto.CsvQualityResult;
import com.datalakefilter.dto.DatasetDecisionRequest;
import com.datalakefilter.dto.UploadDatasetResponse;
import com.datalakefilter.persistence.entity.DatasetRecord;
import com.datalakefilter.persistence.repository.DatasetRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock
    private MinioService minioService;

    @Mock
    private HashService hashService;

    @Mock
    private CsvQualityService csvQualityService;

    @Mock
    private DatasetRecordRepository datasetRecordRepository;

    @InjectMocks
    private DatasetService datasetService;

    @Test
    void partialMatchAtOneHundredExplainsRowsAreContainedInsteadOfExactDuplicate() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alumnos_parcial.csv",
                "text/csv",
                "cedula,nombre\n1,Ana\n2,Luis\n3,Camila\n4,Jorge\n".getBytes()
        );
        CsvQualityResult quality = new CsvQualityResult(
                4,
                2,
                0.0,
                0.0,
                false,
                true
        );
        String rawFile = "raw/alumnos_filas_duplicadas - copia.csv";

        when(csvQualityService.analyze(file)).thenReturn(quality);
        when(minioService.listFiles("raw/")).thenReturn(List.of(rawFile));
        when(hashService.calculateFileHash(file)).thenReturn("new-hash");
        when(hashService.calculateRowHashes(file)).thenReturn(Set.of("row-1", "row-2", "row-3", "row-4"));
        when(minioService.getFile(rawFile)).thenReturn(
                new ByteArrayInputStream("hash".getBytes()),
                new ByteArrayInputStream("rows".getBytes())
        );
        when(hashService.calculateHash(any(InputStream.class))).thenReturn("existing-hash");
        when(hashService.calculateRowHashes(any(InputStream.class))).thenReturn(Set.of("row-1", "row-2", "row-3"));

        UploadDatasetResponse response = datasetService.processDataset(file);

        verify(minioService).uploadFile("review/alumnos_parcial.csv", file);
        assertEquals("REVIEW", response.status());
        assertEquals(100.0, response.partialMatchPercentage());
        assertEquals(rawFile, response.matchedWith());
        assertEquals(
                "Coeficiente de solapamiento: 100% respecto del conjunto con menos filas únicas. Dataset comparado: raw/alumnos_filas_duplicadas - copia.csv. Esto indica inclusión completa del conjunto menor, sin implicar duplicidad exacta. Podría tratarse de una copia parcial, una versión reducida o un subconjunto de datos ya almacenado. Se requiere revisión antes de incorporarlo al Data Lake.",
                response.message()
                );
        }

    @Test
    void approveDatasetMovesReviewFileToRaw() {
        CsvQualityResult quality = new CsvQualityResult(
                10,
                4,
                5.0,
                0.0,
                false,
                true
        );
        DatasetDecisionRequest request = new DatasetDecisionRequest(
                "alumnos.csv",
                "review/alumnos.csv",
                "abc123",
                65.0,
                "raw/alumnos_base.csv",
                quality
        );

        UploadDatasetResponse response = datasetService.approveDataset(request);

        verify(minioService).moveFile("review/alumnos.csv", "raw/alumnos.csv");
        assertEquals("alumnos.csv", response.fileName());
        assertEquals("raw/alumnos.csv", response.path());
        assertEquals("ACCEPTED", response.status());
        assertEquals("abc123", response.fileHash());
        assertEquals(65.0, response.partialMatchPercentage());
        assertEquals("raw/alumnos_base.csv", response.matchedWith());
        assertEquals(quality, response.quality());
    }

    @Test
    void rejectDatasetMovesReviewFileToRejected() {
        DatasetDecisionRequest request = new DatasetDecisionRequest(
                "alumnos.csv",
                "review/alumnos.csv",
                null,
                null,
                null,
                null
        );

        UploadDatasetResponse response = datasetService.rejectDataset(request);

        verify(minioService).moveFile("review/alumnos.csv", "rejected/alumnos.csv");
        assertEquals("rejected/alumnos.csv", response.path());
        assertEquals("REJECTED", response.status());
    }

    @Test
    void decisionRequiresReviewSourcePath() {
        DatasetDecisionRequest request = new DatasetDecisionRequest(
                "alumnos.csv",
                "raw/alumnos.csv",
                null,
                null,
                null,
                null
        );

        assertThrows(
                ResponseStatusException.class,
                () -> datasetService.approveDataset(request)
        );
    }

    @Test
    void listsCurrentReviewFilesFromMinioWithTheirPersistedAnalysis() {
        CsvQualityResult quality = new CsvQualityResult(
                12,
                4,
                45.0,
                0.0,
                false,
                true
        );
        UploadDatasetResponse persistedResponse = new UploadDatasetResponse(
                "alumnos.csv",
                "review/alumnos.csv",
                "REVIEW",
                "El CSV contiene un porcentaje alto de valores nulos.",
                "abc123",
                70.0,
                "raw/base.csv",
                quality
        );
        DatasetRecord record = DatasetRecord.from(persistedResponse);

        when(minioService.listStoredObjects("review/")).thenReturn(List.of(
                new MinioService.StoredObject("review/alumnos.csv", 512L)
        ));
        when(datasetRecordRepository.findFirstByObjectPathOrderByCreatedAtDescIdDesc(
                "review/alumnos.csv"
        )).thenReturn(Optional.of(record));

        List<UploadDatasetResponse> responses = datasetService.findByStatus("review");

        assertEquals(1, responses.size());
        UploadDatasetResponse response = responses.get(0);
        assertEquals("REVIEW", response.status());
        assertEquals("review/alumnos.csv", response.path());
        assertEquals(512L, response.size());
        assertEquals(quality, response.quality());
        assertEquals(persistedResponse.message(), response.message());
    }

    @Test
    void listsLegacyMinioFilesEvenWithoutDatabaseHistory() {
        when(minioService.listStoredObjects("raw/")).thenReturn(List.of(
                new MinioService.StoredObject("raw/legado.csv", 2048L),
                new MinioService.StoredObject("raw/nota.txt", 20L)
        ));
        when(datasetRecordRepository.findFirstByObjectPathOrderByCreatedAtDescIdDesc(
                "raw/legado.csv"
        )).thenReturn(Optional.empty());

        List<UploadDatasetResponse> responses = datasetService.findByStatus("ACCEPTED");

        assertEquals(1, responses.size());
        UploadDatasetResponse response = responses.get(0);
        assertEquals("legado.csv", response.fileName());
        assertEquals("raw/legado.csv", response.path());
        assertEquals("ACCEPTED", response.status());
        assertEquals(2048L, response.size());
        assertNull(response.quality());
        assertEquals("Archivo existente en MinIO. Guardado en raw/.", response.message());
    }

    @Test
    void rejectsUnknownStatusQueries() {
        assertThrows(
                ResponseStatusException.class,
                () -> datasetService.findByStatus("UNKNOWN")
        );
    }
}
