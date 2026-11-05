package com.datalakefilter.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Service
public class HashService {

    public String calculateFileHash(MultipartFile file) {
        try {
            return calculateHash(file.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hash del archivo", e);
        }
    }

    public String calculateHash(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hash desde InputStream", e);
        }
    }

    public Set<String> calculateRowHashes(MultipartFile file) {
        try {
            return calculateRowHashes(file.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hashes por fila", e);
        }
    }

    public Set<String> calculateRowHashes(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        )) {
            Set<String> rowHashes = new HashSet<>();

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                String normalizedLine = CsvNormalization.row(line);

                if (!normalizedLine.isBlank()) {
                    rowHashes.add(hashText(normalizedLine));
                }
            }

            return rowHashes;

        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hashes por fila desde InputStream", e);
        }
    }

    private String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hash de texto", e);
        }
    }
}