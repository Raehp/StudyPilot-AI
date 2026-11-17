package com.studypilot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KbDocumentAddDTO {

    @NotNull(message = "文档ID不能为空")
    private Long documentId;
}