package com.datalakefilter.service;

import com.datalakefilter.dto.CsvQualityResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CsvQualityService {

    public CsvQualityResult analyze(MultipartFile file) {
        try {
            return analyze(file.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Error al analizar calidad del CSV", e);
        }
    }

    public CsvQualityResult analyze(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        )) {
            String headerLine = CsvNormalization.header(reader.readLine());

            if (headerLine == null || headerLine.isBlank()) {
                return new CsvQualityResult(0, 0, 100.0, 0.0, false, false);
            }

            String[] headers = headerLine.split(",", -1);
            boolean hasDuplicateColumns = hasDuplicates(headers);

            int totalColumns = headers.length;
            int totalRows = 0;
            int totalCells = 0;
            int nullCells = 0;

            Set<String> uniqueRows = new HashSet<>();
            int duplicateRows = 0;

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                totalRows++;

                String normalizedLine = CsvNormalization.row(line);

                if (!uniqueRows.add(normalizedLine)) {
                    duplicateRows++;
                }

                String[] values = line.split(",", -1);

                for (int i = 0; i < totalColumns; i++) {
                    totalCells++;

                    String value = i < values.length ? values[i].trim() : "";

                    if (value.isBlank()) {
                        nullCells++;
                    }
                }
            }

            double nullPercentage = totalCells == 0 ? 100.0 : (nullCells * 100.0) / totalCells;
            double duplicateRowsPercentage = totalRows == 0 ? 0.0 : (duplicateRows * 100.0) / totalRows;

            return new CsvQualityResult(
                    totalRows,
                    totalColumns,
                    round(nullPercentage),
                    round(duplicateRowsPercentage),
                    hasDuplicateColumns,
                    totalRows > 0
            );

        } catch (Exception e) {
            throw new RuntimeException("Error al analizar calidad del CSV", e);
        }
    }

    private boolean hasDuplicates(String[] values) {
        Set<String> seen = new HashSet<>();

        for (String value : values) {
            String normalized = CsvNormalization.row(value);

            if (!seen.add(normalized)) {
                return true;
            }
        }

        return false;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}