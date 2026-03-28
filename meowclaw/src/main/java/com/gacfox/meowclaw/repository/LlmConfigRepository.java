package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.LlmConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class LlmConfigRepository {
    private final JdbcTemplate jdbcTemplate;
    private final LlmConfigRowMapper rowMapper = new LlmConfigRowMapper();

    public LlmConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LlmConfig> findById(Long id) {
        String sql = "SELECT * FROM llm_configs WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public List<LlmConfig> findAll() {
        String sql = "SELECT * FROM llm_configs ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public LlmConfig save(LlmConfig config) {
        if (config.getId() == null) {
            return insert(config);
        } else {
            return update(config);
        }
    }

    private LlmConfig insert(LlmConfig config) {
        String sql = "INSERT INTO llm_configs (name, api_url, api_key, model, max_context_length, temperature, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                config.getName(),
                config.getApiUrl(),
                config.getApiKey(),
                config.getModel(),
                config.getMaxContextLength(),
                config.getTemperature(),
                config.getCreatedAt(),
                config.getUpdatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        config.setId(id);
        return config;
    }

    private LlmConfig update(LlmConfig config) {
        String sql = "UPDATE llm_configs SET name = ?, api_url = ?, api_key = ?, model = ?, max_context_length = ?, temperature = ?, created_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                config.getName(),
                config.getApiUrl(),
                config.getApiKey(),
                config.getModel(),
                config.getMaxContextLength(),
                config.getTemperature(),
                config.getCreatedAt(),
                config.getUpdatedAt(),
                config.getId());
        return config;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM llm_configs WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static class LlmConfigRowMapper implements RowMapper<LlmConfig> {
        @Override
        public LlmConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            LlmConfig config = new LlmConfig();
            config.setId(rs.getLong("id"));
            config.setName(rs.getString("name"));
            config.setApiUrl(rs.getString("api_url"));
            config.setApiKey(rs.getString("api_key"));
            config.setModel(rs.getString("model"));
            config.setMaxContextLength(rs.getInt("max_context_length"));
            config.setTemperature(rs.getDouble("temperature"));
            config.setCreatedAt(rs.getLong("created_at"));
            config.setUpdatedAt(rs.getLong("updated_at"));
            return config;
        }
    }
}
