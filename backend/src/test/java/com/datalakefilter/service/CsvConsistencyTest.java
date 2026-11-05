package com.datalakefilter.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CsvConsistencyTest {
    private final CsvQualityService quality = new CsvQualityService();
    private final HashService hashes = new HashService();
    private ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test void blanksAreSkippedButEmptyFieldsAreCountedAndBomDoesNotHideDuplicateHeaders() {
        String csv = "\uFEFFNombre,nombre\n Ana,X \n\n ana,x\n,,\n   \n";
        var result = quality.analyze(stream(csv));
        var upload = new MockMultipartFile("file", "a.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(result, quality.analyze(upload));
        assertEquals(3, result.totalRows());
        assertEquals(33.33, result.nullPercentage());
        assertEquals(33.33, result.duplicateRowsPercentage());
        assertTrue(result.hasDuplicateColumns());
        assertEquals(2, hashes.calculateRowHashes(stream(csv)).size());
    }

    @Test void internalSpacesAndBinaryHashesRemainDistinct() {
        assertEquals(2, hashes.calculateRowHashes(stream("a,b\n1,A  B\n1,A B\n")).size());
        assertEquals(hashes.calculateRowHashes(stream("a,b\n 1,ANA \n")),
                hashes.calculateRowHashes(stream("x,y\n1,ana\n\n")));
        assertNotEquals(hashes.calculateHash(stream("a\nAna\n")), hashes.calculateHash(stream("a\nana\n")));
    }

    @Test void headerOnlyHasNoData() {
        assertFalse(quality.analyze(stream("\uFEFFa,b\n\n")).hasDataRows());
    }
}
