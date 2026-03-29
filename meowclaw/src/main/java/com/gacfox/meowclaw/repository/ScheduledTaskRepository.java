package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.ScheduledTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ScheduledTaskRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ScheduledTaskRowMapper rowMapper = new ScheduledTaskRowMapper();

    public ScheduledTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ScheduledTask> findById(Long id) {
        String sql = "SELECT * FROM scheduled_tasks WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public List<ScheduledTask> findAll() {
        String sql = "SELECT * FROM scheduled_tasks ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<ScheduledTask> findByEnabled(boolean enabled) {
        String sql = "SELECT * FROM scheduled_tasks WHERE enabled = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, enabled ? 1 : 0);
    }

    public ScheduledTask save(ScheduledTask task) {
        if (task.getId() == null) {
            return insert(task);
        } else {
            return update(task);
        }
    }

    private ScheduledTask insert(ScheduledTask task) {
        String sql = "INSERT INTO scheduled_tasks (name, agent_config_id, user_prompt, cron_expression, new_session_each, bound_conversation_id, enabled, last_executed_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                task.getName(),
                task.getAgentConfigId(),
                task.getUserPrompt(),
                task.getCronExpression(),
                task.isNewSessionEach() ? 1 : 0,
                task.getBoundConversationId(),
                task.isEnabled() ? 1 : 0,
                task.getLastExecutedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        task.setId(id);
        return task;
    }

    private ScheduledTask update(ScheduledTask task) {
        String sql = "UPDATE scheduled_tasks SET name = ?, agent_config_id = ?, user_prompt = ?, cron_expression = ?, new_session_each = ?, bound_conversation_id = ?, enabled = ?, last_executed_at = ?, created_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                task.getName(),
                task.getAgentConfigId(),
                task.getUserPrompt(),
                task.getCronExpression(),
                task.isNewSessionEach() ? 1 : 0,
                task.getBoundConversationId(),
                task.isEnabled() ? 1 : 0,
                task.getLastExecutedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getId());
        return task;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM scheduled_tasks WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void updateLastExecutedAt(Long id, Long lastExecutedAt) {
        String sql = "UPDATE scheduled_tasks SET last_executed_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, lastExecutedAt, lastExecutedAt, id);
    }

    public void updateBoundConversationId(Long id, Long conversationId) {
        String sql = "UPDATE scheduled_tasks SET bound_conversation_id = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, conversationId, Instant.now().toEpochMilli(), id);
    }

    private static class ScheduledTaskRowMapper implements RowMapper<ScheduledTask> {
        @Override
        public ScheduledTask mapRow(ResultSet rs, int rowNum) throws SQLException {
            ScheduledTask task = new ScheduledTask();
            task.setId(rs.getLong("id"));
            task.setName(rs.getString("name"));
            task.setAgentConfigId(rs.getLong("agent_config_id"));
            task.setUserPrompt(rs.getString("user_prompt"));
            task.setCronExpression(rs.getString("cron_expression"));
            task.setNewSessionEach(rs.getInt("new_session_each") == 1);
            Long boundConversationId = rs.getLong("bound_conversation_id");
            task.setBoundConversationId(rs.wasNull() ? null : boundConversationId);
            task.setEnabled(rs.getInt("enabled") == 1);
            Long lastExecutedAt = rs.getLong("last_executed_at");
            task.setLastExecutedAt(rs.wasNull() ? null : lastExecutedAt);
            task.setCreatedAt(rs.getLong("created_at"));
            task.setUpdatedAt(rs.getLong("updated_at"));
            return task;
        }
    }
}
