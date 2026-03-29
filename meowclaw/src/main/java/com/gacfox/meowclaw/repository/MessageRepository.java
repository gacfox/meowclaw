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

    public Message save(Message record) {
        if (record.getId() == null) {
            return insert(record);
        } else {
            return update(record);
        }
    }

    private Message insert(Message record) {
        String sql = "INSERT INTO messages (conversation_id, role, content, embedding, input_tokens, output_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                record.getConversationId(),
                record.getRole(),
                record.getContent(),
                record.getEmbedding(),
                record.getInputTokens(),
                record.getOutputTokens(),
                record.getCreatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        record.setId(id);
        return record;
    }

    private Message update(Message record) {
        String sql = "UPDATE messages SET conversation_id = ?, role = ?, content = ?, embedding = ?, input_tokens = ?, output_tokens = ?, created_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                record.getConversationId(),
                record.getRole(),
                record.getContent(),
                record.getEmbedding(),
                record.getInputTokens(),
                record.getOutputTokens(),
                record.getCreatedAt(),
                record.getId());
        return record;
    }

    public void deleteAfterId(Long conversationId, Long messageId) {
        String sql = "DELETE FROM messages WHERE conversation_id = ? AND id >= ?";
        jdbcTemplate.update(sql, conversationId, messageId);
    }

    public void save(Long conversationId, String role, String content, Long inputTokens, Long outputTokens, Long createdAt) {
        String sql = "INSERT INTO messages (conversation_id, role, content, input_tokens, output_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, conversationId, role, content, inputTokens, outputTokens, createdAt);
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
            record.setInputTokens(rs.getLong("input_tokens"));
            if (rs.wasNull()) {
                record.setInputTokens(null);
            }
            record.setOutputTokens(rs.getLong("output_tokens"));
            if (rs.wasNull()) {
                record.setOutputTokens(null);
            }
            record.setCreatedAt(rs.getLong("created_at"));
            return record;
        }
    }
}
