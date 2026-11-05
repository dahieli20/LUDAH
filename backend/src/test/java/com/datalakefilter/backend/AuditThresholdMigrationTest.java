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
        records.saveAndFlush(AuditRecord.from("experimento/", response, "CAMBIO_UMBRALES"));
        jdbc.update("UPDATE risk_threshold_config SET clean_max=30 WHERE id=1");
        assertEquals(25.0, jdbc.queryForObject("SELECT clean_max FROM audit_records WHERE audited_prefix='experimento/'", Double.class));
        assertEquals(50.0, jdbc.queryForObject("SELECT frontier_max FROM audit_records WHERE audited_prefix='experimento/'", Double.class));
        assertEquals("CAMBIO_UMBRALES", jdbc.queryForObject("SELECT motivo FROM audit_records WHERE audited_prefix='experimento/'", String.class));
        assertEquals("NO_REGISTRADO", jdbc.queryForObject("SELECT motivo FROM audit_records WHERE audited_prefix='old'", String.class));
    }
    @Test void upgradesExistingThresholdHistoryFromV2ToV3() {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:upgrade-v2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).target("2").load().migrate();
        var existing = new JdbcTemplate(dataSource);
        existing.update("INSERT INTO audit_records (audited_prefix,total_files,raw_files,review_files,rejected_files,"
                + "ingestion_risk_percentage,quality_risk_percentage,exact_duplication_risk_percentage,"
                + "redundancy_risk_percentage,data_swamp_index_percentage,classification,clean_max,frontier_max) "
                + "VALUES ('v2',1,1,0,0,0,0,0,0,0,'DATA_LAKE_SALUDABLE',25,50)");
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).load().migrate();
        assertEquals(25.0, existing.queryForObject("SELECT clean_max FROM audit_records WHERE audited_prefix='v2'", Double.class));
        assertEquals(50.0, existing.queryForObject("SELECT frontier_max FROM audit_records WHERE audited_prefix='v2'", Double.class));
        assertEquals("NO_REGISTRADO", existing.queryForObject("SELECT motivo FROM audit_records WHERE audited_prefix='v2'", String.class));
    }

}
