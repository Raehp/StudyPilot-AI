package com.studypilot.dto;

import lombok.Data;

@Data
public class VectorizeRequestDTO {

    /** true=重新处理（物理清除旧切片与向量后重建） */
    private Boolean reprocess;
}