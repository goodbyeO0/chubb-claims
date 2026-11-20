package com.chubb.claims;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class FoundationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.12");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test void healthAndMigration() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(content().string("{\"status\":\"UP\"}"));
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public'", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(1);
    }
    @Test void suppliedSchemaSmoke() throws Exception {
        String sql = Files.readString(Path.of("../verification/schema-smoke.sql"));
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) { statement.execute(sql); }
        assertThat(jdbc.queryForObject("select count(*) from claim", Integer.class)).isZero();
    }
}
