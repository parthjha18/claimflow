package com.claimflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** If an entity and the Flyway schema disagree, Hibernate's validate mode fails the context start. */
class SchemaMappingIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextStartsWithFlywaySchemaAndHibernateValidate() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name in "
                        + "('organizations','users','claims','claim_line_items','approval_steps','audit_logs','idempotency_keys')",
                Integer.class);
        assertThat(tables).isEqualTo(7);
    }
}
