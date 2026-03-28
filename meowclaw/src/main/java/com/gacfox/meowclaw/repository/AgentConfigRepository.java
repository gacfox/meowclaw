package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.AgentConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentConfigRepository {
    private final JdbcTemplate jdbcTemplate;
    private final AgentRowMapper rowMapper = new AgentRowMapper();

    public AgentConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AgentConfig> findById(Long id) {
        String sql = "SELECT * FROM agent_configs WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public List<AgentConfig> findAll() {
        String sql = "SELECT * FROM agent_configs ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public AgentConfig save(AgentConfig agent) {
        if (agent.getId() == null) {
            return insert(agent);
        } else {
            return update(agent);
        }
    }

    private AgentConfig insert(AgentConfig agent) {
        String sql = "INSERT INTO agent_configs (name, avatar, system_prompt, enabled_tools, enabled_mcp_tools, default_llm_id, workspace_folder, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                agent.getName(),
                agent.getAvatar(),
                agent.getSystemPrompt(),
                agent.getEnabledTools(),
                agent.getEnabledMcpTools(),
                agent.getDefaultLlmId(),
                agent.getWorkspaceFolder(),
                agent.getCreatedAt(),
                agent.getUpdatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        agent.setId(id);
        return agent;
    }

    private AgentConfig update(AgentConfig agent) {
        String sql = "UPDATE agent_configs SET name = ?, avatar = ?, system_prompt = ?, enabled_tools = ?, enabled_mcp_tools = ?, default_llm_id = ?, workspace_folder = ?, created_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                agent.getName(),
                agent.getAvatar(),
                agent.getSystemPrompt(),
                agent.getEnabledTools(),
                agent.getEnabledMcpTools(),
                agent.getDefaultLlmId(),
                agent.getWorkspaceFolder(),
                agent.getCreatedAt(),
                agent.getUpdatedAt(),
                agent.getId());
        return agent;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM agent_configs WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static class AgentRowMapper implements RowMapper<AgentConfig> {
        @Override
        public AgentConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentConfig agent = new AgentConfig();
            agent.setId(rs.getLong("id"));
            agent.setName(rs.getString("name"));
            agent.setAvatar(rs.getString("avatar"));
            agent.setSystemPrompt(rs.getString("system_prompt"));
            agent.setEnabledTools(rs.getString("enabled_tools"));
            agent.setEnabledMcpTools(rs.getString("enabled_mcp_tools"));
            agent.setDefaultLlmId(rs.getLong("default_llm_id"));
            agent.setWorkspaceFolder(rs.getString("workspace_folder"));
            agent.setCreatedAt(rs.getLong("created_at"));
            agent.setUpdatedAt(rs.getLong("updated_at"));
            return agent;
        }
    }
}
