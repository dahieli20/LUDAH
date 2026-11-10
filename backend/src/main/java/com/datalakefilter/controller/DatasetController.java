package com.datalakefilter.controller;

import com.datalakefilter.dto.DatasetDecisionRequest;
import com.datalakefilter.dto.UploadDatasetResponse;
import com.datalakefilter.service.DatasetService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@CrossOrigin(origins = "http://localhost:4200")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping("/upload")
    public UploadDatasetResponse uploadDataset(@RequestParam("file") MultipartFile file) {
        return datasetService.processDataset(file);
    }

    @PostMapping("/review/approve")
    public UploadDatasetResponse approveDataset(
            @RequestBody DatasetDecisionRequest request
    ) {
        return datasetService.approveDataset(request);
    }

    @PostMapping("/review/reject")
    public UploadDatasetResponse rejectDataset(
            @RequestBody DatasetDecisionRequest request
    ) {
        return datasetService.rejectDataset(request);
    }

    @GetMapping("/status/{status}")
    public List<UploadDatasetResponse> getDatasetsByStatus(
            @PathVariable String status
    ) {
        return datasetService.findByStatus(status);
    }
}
