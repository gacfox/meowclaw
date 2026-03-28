package com.gacfox.meowclaw.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Configuration
@EnableTransactionManagement
public class DatabaseConfig {
    @Value("${sqlite.database:./data/meowclaw.db}")
    private String databasePath;

    @Bean
    public DataSource dataSource() {
        File dbFile = new File(databasePath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean ignored = parentDir.mkdirs();
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + databasePath + "?enable_load_extension=true");

        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        initializeDatabase(jdbcTemplate);
        return jdbcTemplate;
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private void initializeDatabase(JdbcTemplate jdbcTemplate) {
        loadExtensions(jdbcTemplate);
        executeSchema(jdbcTemplate);
        ensureAgentWorkspaceColumn(jdbcTemplate);
        log.info("SQLite数据库初始化完成");
    }

    private void loadExtensions(JdbcTemplate jdbcTemplate) {
        try {
            File extensionFile = loadExtensionFromClasspath();

            if (extensionFile == null || !extensionFile.exists()) {
                log.warn("sqlite-vec扩展在当前平台不被支持");
                return;
            }

            jdbcTemplate.execute((Connection conn) -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT load_extension('" + extensionFile.getAbsolutePath().replace("\\", "/") + "')");
                    log.info("sqlite-vec扩展已加载: {}", extensionFile.getAbsolutePath());
                }
                return null;
            });

        } catch (Exception e) {
            log.error("sqlite-vec扩展加载失败: {}", e.getMessage(), e);
        }
    }

    private File loadExtensionFromClasspath() throws IOException {
        String platform = detectPlatform();
        String extensionResourcePath;
        String extensionFileName;

        switch (platform) {
            case "windows-x86_64":
                extensionResourcePath = "extensions/windows-x86_64/vec0.dll";
                extensionFileName = "vec0.dll";
                break;
            case "linux-x86_64":
                extensionResourcePath = "extensions/linux-x86_64/vec0.so";
                extensionFileName = "vec0.so";
                break;
            case "linux-aarch64":
                extensionResourcePath = "extensions/linux-aarch64/vec0.so";
                extensionFileName = "vec0.so";
                break;
            default:
                log.warn("Unsupported platform: {}", platform);
                return null;
        }

        ClassPathResource resource = new ClassPathResource(extensionResourcePath);
        if (!resource.exists()) {
            log.warn("类路径中找不到sqlite-vec扩展: {}", extensionResourcePath);
            return null;
        }

        Path tempDir = Files.createTempDirectory("sqlite-extensions");
        tempDir.toFile().deleteOnExit();
        File extensionFile = new File(tempDir.toFile(), extensionFileName);
        try (InputStream is = resource.getInputStream();
             FileOutputStream fos = new FileOutputStream(extensionFile)) {
            StreamUtils.copy(is, fos);
        }
        if (!platform.startsWith("windows")) {
            boolean ignored = extensionFile.setExecutable(true);
        }
        extensionFile.deleteOnExit();
        return extensionFile;
    }

    private String detectPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        String os;
        if (osName.contains("win")) {
            os = "windows";
        } else if (osName.contains("linux")) {
            os = "linux";
        } else {
            return "unsupported";
        }

        String arch;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            arch = "x86_64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            arch = "aarch64";
        } else {
            return "unsupported";
        }

        return os + "-" + arch;
    }

    private void executeSchema(JdbcTemplate jdbcTemplate) {
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String schema = new String(bytes, StandardCharsets.UTF_8);

            String[] statements = schema.split(";");
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    String withoutComments = trimmed.replaceAll("(?m)^--.*$", "").trim();
                    if (!withoutComments.isEmpty()) {
                        jdbcTemplate.execute(trimmed);
                    }
                }
            }

            log.info("数据表初始化完成");
        } catch (IOException e) {
            log.error("读取schema.sql失败: {}", e.getMessage());
            throw new RuntimeException("数据表初始化失败", e);
        }
    }

    private void ensureAgentWorkspaceColumn(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("ALTER TABLE agent_configs ADD COLUMN workspace_folder TEXT");
        } catch (Exception e) {
            log.info("agent_configs.workspace_folder 已存在或无法添加: {}", e.getMessage());
        }
    }
}
