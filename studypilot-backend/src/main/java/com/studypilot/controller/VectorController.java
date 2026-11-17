package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.dto.VectorSearchDTO;
import com.studypilot.dto.VectorizeRequestDTO;
import com.studypilot.service.DocumentVectorService;
import com.studypilot.vo.VectorSearchResultVO;
import com.studypilot.vo.VectorStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VectorController {

    private final DocumentVectorService documentVectorService;

    @PostMapping("/api/documents/{id}/vectorize")
    public Result<VectorStatusVO> vectorize(@PathVariable Long id,
                                            @RequestBody(required = false) VectorizeRequestDTO dto) {
        boolean reprocess = dto != null && Boolean.TRUE.equals(dto.getReprocess());
        return Result.success(documentVectorService.submitVectorize(id, reprocess));
    }

    @GetMapping("/api/documents/{id}/vector-status")
    public Result<VectorStatusVO> vectorStatus(@PathVariable Long id) {
        return Result.success(documentVectorService.vectorStatus(id));
    }

    /** M4 向量检索（余弦 TopK，权限过滤）；M5 RAG 问答复用此能力，本阶段不做聊天界面 */
    @PostMapping("/api/knowledge-bases/{kbId}/search")
    public Result<List<VectorSearchResultVO>> search(@PathVariable Long kbId,
                                                     @Valid @RequestBody VectorSearchDTO dto) {
        return Result.success(documentVectorService.search(kbId, dto.getQuery(), dto.getTopK()));
    }
}