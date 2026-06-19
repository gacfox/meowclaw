package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentRequest;
import com.gacfox.meowclaw.service.AgentService;
import com.gacfox.proarc.common.model.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;

    @Autowired
    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public ApiResult<List<AgentDTO>> list() {
        return ApiResult.success(agentService.list());
    }

    @PostMapping
    public ApiResult<AgentDTO> create(@RequestBody @Valid CreateAgentRequest req) {
        return ApiResult.success(agentService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<AgentDTO> update(@PathVariable Long id, @RequestBody @Valid UpdateAgentRequest req) {
        return ApiResult.success(agentService.update(id, req));
    }

    @PostMapping("/{id}/avatar")
    public ApiResult<String> uploadAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResult.success("success", agentService.updateAvatar(id, file));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        agentService.delete(id);
        return ApiResult.success();
    }
}
