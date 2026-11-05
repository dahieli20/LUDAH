package com.datalakefilter.service;

import com.datalakefilter.dto.RiskThresholdConfigResponse;
import com.datalakefilter.persistence.entity.AuditRecord;
import com.datalakefilter.persistence.repository.AuditRecordRepository;
import io.minio.*;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataLakeAuditServiceTest {
    MinioClient minio = mock(MinioClient.class);
    RiskThresholdConfigService thresholds = mock(RiskThresholdConfigService.class);
    AuditRecordRepository records = mock(AuditRecordRepository.class);
    DataLakeAuditService service = new DataLakeAuditService(minio, thresholds, records,
            new CsvQualityService(), new HashService());

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "bucketName", "test");
        when(thresholds.getConfig()).thenReturn(new RiskThresholdConfigResponse(25, 50, "", "", ""));
    }

    private void files(Map<String, String> files) throws Exception {
        when(minio.listObjects(any(ListObjectsArgs.class))).thenAnswer(inv -> {
            ListObjectsArgs args = inv.getArgument(0);
            return files.keySet().stream().filter(k -> k.startsWith(args.prefix())).map(k -> {
                Item item = mock(Item.class);
                when(item.objectName()).thenReturn(k);
                return new Result<>(item);
            }).toList();
        });
        when(minio.getObject(any(GetObjectArgs.class))).thenAnswer(inv -> {
            GetObjectArgs args = inv.getArgument(0);
            return new GetObjectResponse(null, "test", null, args.object(),
                    new ByteArrayInputStream(files.get(args.object()).getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Test void scopesCsvRecursivelyAndSavesThresholdSnapshot() throws Exception {
        files(Map.of("experimento/sub/a.csv", "a,b\n1,X\n\n", "experimento/readme.txt", "ignore",
                "otro/b.csv", "a,b\n,\n"));
        var response = service.auditRepository(" experimento ");
        assertEquals(1, response.totalFiles());
        assertEquals("experimento/", response.auditedPrefix());
        assertEquals("REPOSITORY", response.auditMode());
        assertEquals(0, response.qualityRiskPercentage());
        assertEquals(25.0, response.cleanMax());
        ArgumentCaptor<AuditRecord> saved = ArgumentCaptor.forClass(AuditRecord.class);
        verify(records).save(saved.capture());
        assertEquals(25.0, ReflectionTestUtils.getField(saved.getValue(), "cleanMax"));
        when(thresholds.getConfig()).thenReturn(new RiskThresholdConfigResponse(30, 60, "", "", ""));
        var second = service.auditRepository("experimento");
        assertEquals(30.0, second.cleanMax());
        assertEquals(25.0, ReflectionTestUtils.getField(saved.getValue(), "cleanMax"));
        verify(thresholds, times(2)).getConfig();
        verify(thresholds, never()).getCleanMax();
        verify(thresholds, never()).getFrontierMax();
    }

    @Test void emptyRepositoryIsNotHealthy() throws Exception {
        files(Map.of("otro/a.csv", "a\n1\n"));
        var response = service.auditRepository("vacio");
        assertEquals("SIN_DATOS", response.classification());
        assertEquals(0, response.totalFiles());
        assertEquals("vacio/", response.auditedPrefix());
    }

    @Test void globalAuditUsesActualZoneCountsAndSkipsBlankLines() throws Exception {
        files(Map.of("raw/a.csv", "a,b\n1,X\n\n", "review/b.csv", "a\n1\n", "rejected/c.csv", "a\n2\n"));
        var response = service.auditDataLake();
        assertEquals("GLOBAL", response.auditMode());
        assertEquals(50, response.ingestionRiskPercentage());
        assertEquals(0, response.qualityRiskPercentage());
        assertEquals(3, response.totalFiles());
    }

    @Test void exactCopiesContributeToDAndNotR() throws Exception {
        files(Map.of("raw/a.csv", "a\n1\n", "raw/b.csv", "a\n1\n", "raw/c.csv", "a\n1\n"));
        var response = service.auditDataLake();
        assertEquals(66.67, response.exactDuplicationRiskPercentage());
        assertEquals(0, response.redundancyRiskPercentage());
    }
    @Test void changingThresholdsChangesCategoryWithoutChangingDsi() throws Exception {
        files(Map.of("raw/a.csv", "a,a\n,\n,\n"));
        var first = service.auditDataLake();
        assertEquals(29.75, first.dataSwampIndexPercentage());
        assertEquals("ZONA_FRONTERA", first.classification());
        when(thresholds.getConfig()).thenReturn(new RiskThresholdConfigResponse(30, 50, "", "", ""));
        var second = service.auditDataLake();
        assertEquals(first.dataSwampIndexPercentage(), second.dataSwampIndexPercentage());
        assertEquals("DATA_LAKE_SALUDABLE", second.classification());
        assertEquals(25.0, first.cleanMax());
    }

}
