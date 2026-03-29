package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Skill;
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
public class SkillRepository {
    private final JdbcTemplate jdbcTemplate;
    private final SkillRowMapper rowMapper = new SkillRowMapper();

    public SkillRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Skill> findById(Long id) {
        String sql = "SELECT * FROM skills WHERE id = ?";
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public Optional<Skill> findByName(String name) {
        String sql = "SELECT * FROM skills WHERE name = ?";
        return jdbcTemplate.query(sql, rowMapper, name).stream().findFirst();
    }

    public List<Skill> findAll() {
        String sql = "SELECT * FROM skills ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Skill save(Skill skill) {
        if (skill.getId() == null) {
            return insert(skill);
        } else {
            return update(skill);
        }
    }

    private Skill insert(Skill skill) {
        String sql = "INSERT INTO skills (name, description, package_file, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, skill.getName());
            statement.setString(2, skill.getDescription());
            statement.setString(3, skill.getPackageFile());
            statement.setLong(4, skill.getCreatedAt());
            statement.setLong(5, skill.getUpdatedAt());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            skill.setId(key.longValue());
        }
        return skill;
    }

    private Skill update(Skill skill) {
        String sql = "UPDATE skills SET name = ?, description = ?, package_file = ?, created_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                skill.getName(),
                skill.getDescription(),
                skill.getPackageFile(),
                skill.getCreatedAt(),
                skill.getUpdatedAt(),
                skill.getId());
        return skill;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM skills WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static class SkillRowMapper implements RowMapper<Skill> {
        @Override
        public Skill mapRow(ResultSet rs, int rowNum) throws SQLException {
            Skill skill = new Skill();
            skill.setId(rs.getLong("id"));
            skill.setName(rs.getString("name"));
            skill.setDescription(rs.getString("description"));
            skill.setPackageFile(rs.getString("package_file"));
            skill.setCreatedAt(rs.getLong("created_at"));
            skill.setUpdatedAt(rs.getLong("updated_at"));
            return skill;
        }
    }
}
