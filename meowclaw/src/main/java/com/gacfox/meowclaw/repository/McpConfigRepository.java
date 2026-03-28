package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.McpConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class McpConfigRepository {
    private final JdbcTemplate jdbcTemplate;
    private final McpConfigRowMapper rowMapper = new McpConfigRowMapper();

    public McpConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<McpConfig> findById(Long id) {
        String sql = "SELECT * FROM mcp_configs WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public Optional<McpConfig> findByName(String name) {
        String sql = "SELECT * FROM mcp_configs WHERE name = ?";
        return jdbcTemplate.query(sql, rowMapper, name).stream().findFirst();
    }

    public List<McpConfig> findAll() {
        String sql = "SELECT * FROM mcp_configs ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public McpConfig save(McpConfig mcpConfig) {
        if (mcpConfig.getId() == null) {
            return insert(mcpConfig);
        } else {
            return update(mcpConfig);
        }
    }

    private McpConfig insert(McpConfig mcpConfig) {
        String sql = "INSERT INTO mcp_configs (name, transport_type, command, args, env_vars, url, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                mcpConfig.getName(),
                mcpConfig.getTransportType(),
                mcpConfig.getCommand(),
                mcpConfig.getArgs(),
                mcpConfig.getEnvVars(),
                mcpConfig.getUrl(),
                mcpConfig.getCreatedAt(),
                mcpConfig.getUpdatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        mcpConfig.setId(id);
        return mcpConfig;
    }

    private McpConfig update(McpConfig mcpConfig) {
        String sql = "UPDATE mcp_configs SET name = ?, transport_type = ?, command = ?, args = ?, env_vars = ?, url = ?, created_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                mcpConfig.getName(),
                mcpConfig.getTransportType(),
                mcpConfig.getCommand(),
                mcpConfig.getArgs(),
                mcpConfig.getEnvVars(),
                mcpConfig.getUrl(),
                mcpConfig.getCreatedAt(),
                mcpConfig.getUpdatedAt(),
                mcpConfig.getId());
        return mcpConfig;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM mcp_configs WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static class McpConfigRowMapper implements RowMapper<McpConfig> {
        @Override
        public McpConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            McpConfig mcpConfig = new McpConfig();
            mcpConfig.setId(rs.getLong("id"));
            mcpConfig.setName(rs.getString("name"));
            mcpConfig.setTransportType(rs.getString("transport_type"));
            mcpConfig.setCommand(rs.getString("command"));
            mcpConfig.setArgs(rs.getString("args"));
            mcpConfig.setEnvVars(rs.getString("env_vars"));
            mcpConfig.setUrl(rs.getString("url"));
            mcpConfig.setCreatedAt(rs.getLong("created_at"));
            mcpConfig.setUpdatedAt(rs.getLong("updated_at"));
            return mcpConfig;
        }
    }
}