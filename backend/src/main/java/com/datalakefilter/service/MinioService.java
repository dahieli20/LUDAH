package com.datalakefilter.service;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.web.multipart.MultipartFile;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public boolean bucketExists() {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al verificar el bucket en MinIO", e);
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public void uploadFile(String objectName, MultipartFile file) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al subir archivo a MinIO", e);
        }
    }

    public List<String> listFiles(String prefix) {
        try {
            List<String> files = new ArrayList<>();

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();

                if (!item.isDir()) {
                    files.add(item.objectName());
                }
            }

            return files;
        } catch (Exception e) {
            throw new RuntimeException("Error al listar archivos en MinIO", e);
        }
    }

    /** Prefijos virtuales de MinIO, incluidos los padres de carpetas anidadas. */
    public List<String> listPrefixes() {
        Set<String> prefixes = new TreeSet<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
            for (Result<Item> result : results) {
                String objectName = result.get().objectName();
                for (int index = objectName.indexOf('/'); index >= 0;
                     index = objectName.indexOf('/', index + 1)) {
                    if (index > 0) prefixes.add(objectName.substring(0, index + 1));
                }
            }
            return new ArrayList<>(prefixes);
        } catch (Exception e) {
            throw new RuntimeException("Error al consultar las rutas disponibles en MinIO", e);
        }
    }

    public InputStream getFile(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener archivo desde MinIO", e);
        }
    }

    public void moveFile(String sourceObjectName, String targetObjectName) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(targetObjectName)
                            .source(
                                    CopySource.builder()
                                            .bucket(bucketName)
                                            .object(sourceObjectName)
                                            .build()
                            )
                            .build()
            );

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(sourceObjectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al mover archivo en MinIO", e);
        }
    }
}
