package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.Message;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Slf4j
@Service
public class VectorSearchService {
    private final JdbcTemplate jdbcTemplate;
    private static final int VECTOR_DIMENSIONS = 1536;
    private boolean vecExtensionLoaded = false;

    public VectorSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        initializeVecTables();
    }

    private void initializeVecTables() {
        try {
            jdbcTemplate.execute("SELECT vec_version()");
            vecExtensionLoaded = true;
            String createVirtualTableSql = String.format(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS vec_message_embeddings USING vec0(embedding float[%d])",
                    VECTOR_DIMENSIONS
            );
            jdbcTemplate.execute(createVirtualTableSql);
        } catch (Exception e) {
            vecExtensionLoaded = false;
        }
    }

    public boolean isVecEnabled() {
        return vecExtensionLoaded;
    }

    public void saveMessageWithEmbedding(Long conversationId, String role, String content, float[] embedding) {
        if (!isVecEnabled()) {
            String sql = "INSERT INTO messages (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(sql, conversationId, role, content, System.currentTimeMillis());
            return;
        }

        byte[] embeddingBytes = floatArrayToBytes(embedding);

        String insertMessageSql = "INSERT INTO messages (conversation_id, role, content, embedding, created_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(insertMessageSql, conversationId, role, content, embeddingBytes, System.currentTimeMillis());

        String insertVecSql = "INSERT INTO vec_message_embeddings (rowid, embedding) VALUES (last_insert_rowid(), vec_normalize(?))";
        jdbcTemplate.update(insertVecSql, (Object) embeddingBytes);
    }

    public List<Message> searchSimilarMessages(float[] queryEmbedding, int limit) {
        if (!isVecEnabled()) {
            log.warn("Vector search not available");
            return List.of();
        }

        byte[] queryBytes = floatArrayToBytes(queryEmbedding);

        String sql = """
                SELECT m.*, distance
                FROM vec_message_embeddings vme
                JOIN messages m ON vme.rowid = m.id
                WHERE vme.embedding MATCH vec_normalize(?)
                ORDER BY distance
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, new MessageRowMapper(), queryBytes, limit);
    }

    public List<Message> searchSimilarMessagesInConversation(Long conversationId, float[] queryEmbedding, int limit) {
        if (!isVecEnabled()) {
            log.warn("Vector search not available");
            return List.of();
        }

        byte[] queryBytes = floatArrayToBytes(queryEmbedding);

        String sql = """
                SELECT m.*, distance
                FROM vec_message_embeddings vme
                JOIN messages m ON vme.rowid = m.id
                WHERE vme.embedding MATCH vec_normalize(?)
                AND m.conversation_id = ?
                ORDER BY distance
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, new MessageRowMapper(), queryBytes, conversationId, limit);
    }

    public void deleteMessagesByConversationId(Long conversationId) {
        if (isVecEnabled()) {
            String deleteVecSql = """
                    DELETE FROM vec_message_embeddings
                    WHERE rowid IN (
                        SELECT id FROM messages WHERE conversation_id = ?
                    )
                    """;
            jdbcTemplate.update(deleteVecSql, conversationId);
        }

        String deleteMsgSql = "DELETE FROM messages WHERE conversation_id = ?";
        jdbcTemplate.update(deleteMsgSql, conversationId);
    }

    private byte[] floatArrayToBytes(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i * 4] = (byte) (bits & 0xff);
            bytes[i * 4 + 1] = (byte) ((bits >> 8) & 0xff);
            bytes[i * 4 + 2] = (byte) ((bits >> 16) & 0xff);
            bytes[i * 4 + 3] = (byte) ((bits >> 24) & 0xff);
        }
        return bytes;
    }

    private float[] bytesToFloatArray(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = (bytes[i * 4] & 0xff) |
                    ((bytes[i * 4 + 1] & 0xff) << 8) |
                    ((bytes[i * 4 + 2] & 0xff) << 16) |
                    ((bytes[i * 4 + 3] & 0xff) << 24);
            floats[i] = Float.intBitsToFloat(bits);
        }
        return floats;
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
