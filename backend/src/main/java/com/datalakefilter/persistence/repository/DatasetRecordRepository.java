package com.datalakefilter.persistence.repository;

import com.datalakefilter.persistence.entity.DatasetRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DatasetRecordRepository extends JpaRepository<DatasetRecord, Long> {
    Optional<DatasetRecord> findFirstByObjectPathOrderByCreatedAtDescIdDesc(String objectPath);
}
