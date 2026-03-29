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
import java.util.ArrayList;
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

    public List<Conversation> findAll() {
        String sql = "SELECT * FROM conversations ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<Conversation> findAll(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM conversations ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, pageSize, offset);
    }

    public List<Conversation> findByFilters(Long agentConfigId, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        StringBuilder sql = new StringBuilder("SELECT DISTINCT c.* FROM conversations c");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" JOIN messages m ON c.id = m.conversation_id");
        }
        sql.append(" WHERE 1=1");

        if (agentConfigId != null) {
            sql.append(" AND c.agent_config_id = ?");
            params.add(agentConfigId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND m.content LIKE ?");
            params.add("%" + keyword + "%");
        }

        sql.append(" ORDER BY c.updated_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    public long countByFilters(Long agentConfigId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT c.id) FROM conversations c");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" JOIN messages m ON c.id = m.conversation_id");
        }
        sql.append(" WHERE 1=1");

        if (agentConfigId != null) {
            sql.append(" AND c.agent_config_id = ?");
            params.add(agentConfigId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND m.content LIKE ?");
            params.add("%" + keyword + "%");
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
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
