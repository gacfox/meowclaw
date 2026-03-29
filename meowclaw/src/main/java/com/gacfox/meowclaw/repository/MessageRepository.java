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
        String sql = "INSERT INTO messages (conversation_id, role, content, embedding, api_url, model, input_tokens, output_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                record.getConversationId(),
                record.getRole(),
                record.getContent(),
                record.getEmbedding(),
                record.getApiUrl(),
                record.getModel(),
                record.getInputTokens(),
                record.getOutputTokens(),
                record.getCreatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        record.setId(id);
        return record;
    }

    private Message update(Message record) {
        String sql = "UPDATE messages SET conversation_id = ?, role = ?, content = ?, embedding = ?, api_url = ?, model = ?, input_tokens = ?, output_tokens = ?, created_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                record.getConversationId(),
                record.getRole(),
                record.getContent(),
                record.getEmbedding(),
                record.getApiUrl(),
                record.getModel(),
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

    public void save(Long conversationId, String role, String content, String apiUrl, String model, Long inputTokens, Long outputTokens, Long createdAt) {
        String sql = "INSERT INTO messages (conversation_id, role, content, api_url, model, input_tokens, output_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, conversationId, role, content, apiUrl, model, inputTokens, outputTokens, createdAt);
    }

    public long[] sumTokensByTimeRange(Long startTime, Long endTime) {
        String sql = "SELECT COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0) FROM messages WHERE created_at >= ? AND created_at <= ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return new long[]{rs.getLong(1), rs.getLong(2)};
            }
            return new long[]{0L, 0L};
        }, startTime, endTime);
    }

    public long countByTimeRange(Long startTime, Long endTime) {
        String sql = "SELECT COUNT(*) FROM messages WHERE created_at >= ? AND created_at <= ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, startTime, endTime);
        return count != null ? count : 0L;
    }

    public List<Long> findConversationIdsByKeyword(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT DISTINCT conversation_id FROM messages WHERE content LIKE ? ORDER BY conversation_id DESC LIMIT ? OFFSET ?";
        String pattern = "%" + keyword + "%";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("conversation_id"), pattern, pageSize, offset);
    }

    public long countConversationsByKeyword(String keyword) {
        String sql = "SELECT COUNT(DISTINCT conversation_id) FROM messages WHERE content LIKE ?";
        String pattern = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, pattern);
        return count != null ? count : 0L;
    }

    public List<String[]> findDistinctApiUrlModelPairs() {
        String sql = "SELECT DISTINCT api_url, model FROM messages WHERE model IS NOT NULL AND model != '' AND api_url IS NOT NULL AND api_url != '' ORDER BY api_url, model";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new String[]{rs.getString("api_url"), rs.getString("model")});
    }

    public long[] sumDailyTokensByApiUrlAndModel(Long dayStart, Long dayEnd, String apiUrl, String model) {
        String sql = "SELECT COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0), COUNT(*) FROM messages WHERE created_at >= ? AND created_at <= ? AND api_url = ? AND model = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)};
            }
            return new long[]{0L, 0L, 0L};
        }, dayStart, dayEnd, apiUrl, model);
    }

    public long[] sumDailyTokensAllModels(Long dayStart, Long dayEnd) {
        String sql = "SELECT COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0), COUNT(*) FROM messages WHERE created_at >= ? AND created_at <= ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)};
            }
            return new long[]{0L, 0L, 0L};
        }, dayStart, dayEnd);
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
            record.setApiUrl(rs.getString("api_url"));
            record.setModel(rs.getString("model"));
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
