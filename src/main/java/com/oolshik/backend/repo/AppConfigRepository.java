package com.oolshik.backend.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AppConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AppConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findValueByKey(String key) {
        List<String> values = jdbcTemplate.query(
                "select config_value from app_config where config_key = ?",
                (rs, rowNum) -> rs.getString("config_value"),
                key
        );
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
    }
}
