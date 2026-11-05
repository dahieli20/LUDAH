package com.datalakefilter.controller;

import com.datalakefilter.service.MinioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class MinioController {

    private final MinioService minioService;

    public MinioController(MinioService minioService) {
        this.minioService = minioService;
    }

    @GetMapping("/api/minio/prefixes")
    public List<String> listPrefixes() {
        return minioService.listPrefixes();
    }

    @GetMapping("/api/minio/test")
    public String testMinio() {
        boolean exists = minioService.bucketExists();

        if (exists) {
            return "Conexión correcta con MinIO. Bucket encontrado: " + minioService.getBucketName();
        }

        return "Conexión correcta con MinIO, pero el bucket no existe: " + minioService.getBucketName();
    }
}