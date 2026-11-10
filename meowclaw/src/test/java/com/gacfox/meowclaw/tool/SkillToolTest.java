package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillToolTest {
    @TempDir
    Path workspace;

    private final SkillTool skillTool = new SkillTool();

    @Test
    void returnsAbsoluteSkillPathAndContentWhenCwdIsProjectDirectory() throws Exception {
        Path skillFile = workspace.resolve(".skill/demo/SKILL.md");
        Files.createDirectories(skillFile.getParent());
        Files.writeString(skillFile, "使用 scripts/run.sh 执行任务");
        Path projectCwd = workspace.resolve("projects/1");
        Files.createDirectories(projectCwd);
        AgentContext context = AgentContext.builder()
                .variables(Map.of(
                        "workspacePath", workspace.toString(),
                        "cwd", projectCwd.toString()))
                .build();

        String result = skillTool.skill(new SkillParam("demo"), context);

        assertThat(result).startsWith("Skill: demo\nSKILL.md: "
                + skillFile.toAbsolutePath().normalize() + "\n\n");
        assertThat(result).endsWith("使用 scripts/run.sh 执行任务");
    }
}
