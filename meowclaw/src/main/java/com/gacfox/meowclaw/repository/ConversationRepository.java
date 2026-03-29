package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ConversationRowMapper rowMapper = new ConversationRowMapper();

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Conversation> findById(Long id) {
        String sql = "SELECT * FROM conversations WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public List<Conversation> findByAgentConfigId(Long agentConfigId) {
        String sql = "SELECT * FROM conversations WHERE agent_config_id = ? ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, rowMapper, agentConfigId);
    }

    public List<Conversation> findByAgentConfigId(Long agentConfigId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM conversations WHERE agent_config_id = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, agentConfigId, pageSize, offset);
    }

    public long countByAgentConfigId(Long agentConfigId) {
        String sql = "SELECT COUNT(*) FROM conversations WHERE agent_config_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, agentConfigId);
        return count != null ? count : 0L;
    }

    public List<Conversation> findByType(String type, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM conversations WHERE type = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, type, pageSize, offset);
    }

    public long countByType(String type) {
        String sql = "SELECT COUNT(*) FROM conversations WHERE type = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, type);
        return count != null ? count : 0L;
    }

    public List<Conversation> findAll() {
        String sql = "SELECT * FROM conversations ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Conversation> findAll(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM conversations ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, pageSize, offset);
    }

    public long countAll() {
        String sql = "SELECT COUNT(*) FROM conversations";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    public List<Conversation> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(String::valueOf).toArray(String[]::new));
        String sql = "SELECT * FROM conversations WHERE id IN (" + placeholders + ") ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Conversation save(Conversation conversation) {
        if (conversation.getId() == null) {
            return insert(conversation);
        } else {
            return update(conversation);
        }
    }

    private Conversation insert(Conversation conversation) {
        String sql = "INSERT INTO conversations (agent_config_id, title, type, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, conversation.getAgentConfigId());
            statement.setString(2, conversation.getTitle());
            statement.setString(3, conversation.getType() != null ? conversation.getType() : Conversation.TYPE_CHAT);
            statement.setLong(4, conversation.getCreatedAt());
            statement.setLong(5, conversation.getUpdatedAt());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            conversation.setId(key.longValue());
        }
        return conversation;
    }

    private Conversation update(Conversation conversation) {
        String sql = "UPDATE conversations SET agent_config_id = ?, title = ?, type = ?, created_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                conversation.getAgentConfigId(),
                conversation.getTitle(),
                conversation.getType(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getId());
        return conversation;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM conversations WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static class ConversationRowMapper implements RowMapper<Conversation> {
        @Override
        public Conversation mapRow(ResultSet rs, int rowNum) throws SQLException {
            Conversation conversation = new Conversation();
            conversation.setId(rs.getLong("id"));
            conversation.setAgentConfigId(rs.getLong("agent_config_id"));
            conversation.setTitle(rs.getString("title"));
            conversation.setType(rs.getString("type"));
            conversation.setCreatedAt(rs.getLong("created_at"));
            conversation.setUpdatedAt(rs.getLong("updated_at"));
            return conversation;
        }
    }
}
