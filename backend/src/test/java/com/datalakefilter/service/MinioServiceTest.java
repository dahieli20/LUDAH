package com.datalakefilter.service;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioServiceTest {
    private final MinioClient client = mock(MinioClient.class);
    private final MinioService service = new MinioService(client);

    private Result<Item> item(String name) {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn(name);
        return new Result<>(item);
    }

    @Test void listsUniqueSortedParentsNestedPathsAndDirectoryMarkersWithoutRoot() {
        ReflectionTestUtils.setField(service, "bucketName", "test");
        List<Result<Item>> objects = List.of(
                item("pruebas/escenario 1/a.csv"), item("pruebas/escenario 1/b.csv"),
                item("raw/base.csv"), item("vacia/"), item("docs/nota.txt"), item("archivo-raiz.csv"));
        when(client.listObjects(any(ListObjectsArgs.class))).thenReturn(objects);
        assertEquals(List.of("docs/", "pruebas/", "pruebas/escenario 1/", "raw/", "vacia/"), service.listPrefixes());
        var captor = org.mockito.ArgumentCaptor.forClass(ListObjectsArgs.class);
        verify(client).listObjects(captor.capture());
        assertEquals("test", captor.getValue().bucket());
        assertTrue(captor.getValue().recursive());
        verifyNoMoreInteractions(client);
    }

    @Test void emptyBucketHasNoSelectableRoot() {
        ReflectionTestUtils.setField(service, "bucketName", "test");
        when(client.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());
        assertEquals(List.of(), service.listPrefixes());
    }

    @Test void listingErrorsAreNotReportedAsEmptyBucket() {
        ReflectionTestUtils.setField(service, "bucketName", "test");
        when(client.listObjects(any(ListObjectsArgs.class))).thenThrow(new RuntimeException("offline"));
        assertThrows(RuntimeException.class, service::listPrefixes);
    }
}
