package com.gacfox.meowclaw.service.memory;

import com.gacfox.meowclaw.entity.MemoryNodeEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemoryNodeEntityWithVector {
    private final MemoryNodeEntity relation;
    private final float[] descriptionVector;
}
