package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.dto.KbDocumentAddDTO;
import com.studypilot.dto.KnowledgeBaseCreateDTO;
import com.studypilot.dto.KnowledgeBaseUpdateDTO;
import com.studypilot.service.KnowledgeBaseService;
import com.studypilot.vo.KnowledgeBaseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateDTO dto) {
        return Result.success(knowledgeBaseService.create(dto));
    }

    @GetMapping
    public Result<List<KnowledgeBaseVO>> list() {
        return Result.success(knowledgeBaseService.list());
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.detail(id));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseVO> update(@PathVariable Long id,
                                          @Valid @RequestBody KnowledgeBaseUpdateDTO dto) {
        return Result.success(knowledgeBaseService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/documents")
    public Result<Void> addDocument(@PathVariable Long id, @Valid @RequestBody KbDocumentAddDTO dto) {
        knowledgeBaseService.addDocument(id, dto.getDocumentId());
        return Result.success();
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public Result<Void> removeDocument(@PathVariable Long id, @PathVariable Long documentId) {
        knowledgeBaseService.removeDocument(id, documentId);
        return Result.success();
    }
}