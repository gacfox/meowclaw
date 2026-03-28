package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class MessageRepository {
    private final JdbcTemplate jdbcTemplate;
    private final MessageRowMapper rowMapper = new MessageRowMapper();

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Message> findById(Long id) {
        String sql = "SELECT * FROM messages WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public List<Message> findByConversationId(Long conversationId) {
        String sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    public List<Message> findRecentByConversationId(Long conversationId, int limit) {
        String sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, conversationId, limit);
    }

    public Message save(Message record) {
        if (record.getId() == null) {
            return insert(record);
        } else {
            return update(record);
        }
    }

    private Message insert(Message record) {
        String sql = "INSERT INTO messages (conversation_id, role, content, embedding, created_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                record.getConversationId(),
                record.getRole(),
                record.getContent(),
                record.getEmbedding(),
                record.getCreatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        record.setId(id);
        return record;
    }

    private Message update(Message record) {
        String sql = "UPDATE messages SET conversation_id = ?, role = ?, content = ?, embedding = ?, created_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                record.getConversationId(),
                record.getRole(),
                record.getContent(),
                record.getEmbedding(),
                record.getCreatedAt(),
                record.getId());
        return record;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM messages WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void deleteByConversationId(Long conversationId) {
        String sql = "DELETE FROM messages WHERE conversation_id = ?";
        jdbcTemplate.update(sql, conversationId);
    }

    public void save(Long conversationId, String role, String content, Long createdAt) {
        String sql = "INSERT INTO messages (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, conversationId, role, content, createdAt);
    }

    private static class MessageRowMapper implements RowMapper<Message> {
        @Override
        public Message mapRow(ResultSet rs, int rowNum) throws SQLException {
            Message record = new Message();
            record.setId(rs.getLong("id"));
            record.setConversationId(rs.getLong("conversation_id"));
            record.setRole(rs.getString("role"));
            record.setContent(rs.getString("content"));
            record.setEmbedding(rs.getBytes("embedding"));
            record.setCreatedAt(rs.getLong("created_at"));
            return record;
        }
    }
}
