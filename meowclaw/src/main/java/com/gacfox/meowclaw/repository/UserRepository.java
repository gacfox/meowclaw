package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;
    private final UserRowMapper rowMapper = new UserRowMapper();

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        return jdbcTemplate.query(sql, rowMapper, username).stream().findFirst();
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public User save(User user) {
        if (user.getId() == null) {
            return insert(user);
        } else {
            return update(user);
        }
    }

    private User insert(User user) {
        String sql = "INSERT INTO users (username, password, display_username, avatar_url, created_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                user.getUsername(),
                user.getPassword(),
                user.getDisplayUsername(),
                user.getAvatarUrl(),
                user.getCreatedAt());

        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        user.setId(id);
        return user;
    }

    private User update(User user) {
        String sql = "UPDATE users SET username = ?, password = ?, display_username = ?, avatar_url = ?, created_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                user.getUsername(),
                user.getPassword(),
                user.getDisplayUsername(),
                user.getAvatarUrl(),
                user.getCreatedAt(),
                user.getId());
        return user;
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setPassword(rs.getString("password"));
            user.setDisplayUsername(rs.getString("display_username"));
            user.setAvatarUrl(rs.getString("avatar_url"));
            user.setCreatedAt(rs.getLong("created_at"));
            return user;
        }
    }
}
