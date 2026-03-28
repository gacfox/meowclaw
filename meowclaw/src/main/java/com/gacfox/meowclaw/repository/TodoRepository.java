package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.TodoItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class TodoRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TodoItemRowMapper rowMapper = new TodoItemRowMapper();

    public TodoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TodoItem> findByConversationId(Long conversationId) {
        String sql = "SELECT * FROM todo_items WHERE conversation_id = ? ORDER BY sort_order ASC, id ASC";
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    public TodoItem save(TodoItem item) {
        if (item.getId() == null) {
            return insert(item);
        } else {
            return update(item);
        }
    }

    private TodoItem insert(TodoItem item) {
        String sql = "INSERT INTO todo_items (conversation_id, text, done, sort_order, created_at, done_at) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                item.getConversationId(),
                item.getText(),
                item.isDone() ? 1 : 0,
                item.getSortOrder(),
                item.getCreatedAt(),
                item.getDoneAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        item.setId(id);
        return item;
    }

    private TodoItem update(TodoItem item) {
        String sql = "UPDATE todo_items SET conversation_id = ?, text = ?, done = ?, sort_order = ?, created_at = ?, done_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                item.getConversationId(),
                item.getText(),
                item.isDone() ? 1 : 0,
                item.getSortOrder(),
                item.getCreatedAt(),
                item.getDoneAt(),
                item.getId());
        return item;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM todo_items WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void deleteByConversationId(Long conversationId) {
        String sql = "DELETE FROM todo_items WHERE conversation_id = ?";
        jdbcTemplate.update(sql, conversationId);
    }

    public int countByConversationId(Long conversationId) {
        String sql = "SELECT COUNT(*) FROM todo_items WHERE conversation_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
        return count != null ? count : 0;
    }

    public int getMaxSortOrder(Long conversationId) {
        String sql = "SELECT MAX(sort_order) FROM todo_items WHERE conversation_id = ?";
        Integer max = jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
        return max != null ? max : 0;
    }

    private static class TodoItemRowMapper implements RowMapper<TodoItem> {
        @Override
        public TodoItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            TodoItem item = new TodoItem();
            item.setId(rs.getLong("id"));
            item.setConversationId(rs.getLong("conversation_id"));
            item.setText(rs.getString("text"));
            item.setDone(rs.getInt("done") == 1);
            item.setSortOrder(rs.getInt("sort_order"));
            item.setCreatedAt(rs.getLong("created_at"));
            long doneAt = rs.getLong("done_at");
            item.setDoneAt(rs.wasNull() ? null : doneAt);
            return item;
        }
    }
}
