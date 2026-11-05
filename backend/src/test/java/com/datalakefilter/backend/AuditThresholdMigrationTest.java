package com.datalakefilter.backend;

import com.datalakefilter.dto.DataLakeAuditResponse;
import com.datalakefilter.persistence.entity.AuditRecord;
import com.datalakefilter.persistence.repository.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"
})
class AuditThresholdMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditRecordRepository records;

    @Test void migrationKeepsUnknownHistoryNullAndPersistsOriginalThresholds() {
        jdbc.update("INSERT INTO audit_records (audited_prefix,total_files,raw_files,review_files,rejected_files,"
                + "ingestion_risk_percentage,quality_risk_percentage,exact_duplication_risk_percentage,"
                + "redundancy_risk_percentage,data_swamp_index_percentage,classification) "
                + "VALUES ('old',0,0,0,0,0,0,0,0,0,'SIN_DATOS')");
        assertNull(jdbc.queryForObject("SELECT clean_max FROM audit_records WHERE audited_prefix='old'", Double.class));
        var response = new DataLakeAuditResponse(1,1,0,0,0,0,0,0,28,28,"ZONA_FRONTERA","test",
                List.of(),"REPOSITORY","experimento/",25.0,50.0);
        records.saveAndFlush(AuditRecord.from("experimento/", response));
        jdbc.update("UPDATE risk_threshold_config SET clean_max=30 WHERE id=1");
        assertEquals(25.0, jdbc.queryForObject("SELECT clean_max FROM audit_records WHERE audited_prefix='experimento/'", Double.class));
        assertEquals(50.0, jdbc.queryForObject("SELECT frontier_max FROM audit_records WHERE audited_prefix='experimento/'", Double.class));
    }
}
