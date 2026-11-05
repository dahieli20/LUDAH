package com.datalakefilter.controller;

import com.datalakefilter.dto.DataLakeAuditResponse;
import com.datalakefilter.service.DataLakeAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class DataLakeAuditController {

     private final DataLakeAuditService dataLakeAuditService;

    public DataLakeAuditController(DataLakeAuditService dataLakeAuditService) {
        this.dataLakeAuditService = dataLakeAuditService;
    }

    @GetMapping("/api/datasets/audit/global")
    public DataLakeAuditResponse auditDataLake() {
        return dataLakeAuditService.auditDataLake("MANUAL");
    }

    @GetMapping("/api/datasets/audit/repository")
    public DataLakeAuditResponse auditRepository(
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "NO_REGISTRADO") String motivo
    ) {
        return dataLakeAuditService.auditRepository(prefix, motivo);
    }
    
}