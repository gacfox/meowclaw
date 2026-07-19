package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingModelDTO {
    private Long id;
    private String name;
    private String endpointUrl;
    private String sk;
    private String model;
    private Integer dimensions;
    private Long createdAt;
    private Long updatedAt;
}
