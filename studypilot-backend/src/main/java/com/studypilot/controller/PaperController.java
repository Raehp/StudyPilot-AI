package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.dto.AnalyzeRequestDTO;
import com.studypilot.service.PaperAnalysisTaskService;
import com.studypilot.vo.AnalysisStatusVO;
import com.studypilot.vo.PaperAnalysisVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperAnalysisTaskService paperAnalysisTaskService;

    @PostMapping("/{id}/analyze")
    public Result<AnalysisStatusVO> analyze(@PathVariable Long id,
                                            @RequestBody(required = false) AnalyzeRequestDTO dto) {
        boolean reanalyze = dto != null && Boolean.TRUE.equals(dto.getReanalyze());
        return Result.success(paperAnalysisTaskService.submitAnalysis(id, reanalyze));
    }

    @GetMapping("/{id}/analysis")
    public Result<PaperAnalysisVO> analysis(@PathVariable Long id) {
        return Result.success(paperAnalysisTaskService.latestAnalysis(id));
    }

    @GetMapping("/{id}/analysis-status")
    public Result<AnalysisStatusVO> analysisStatus(@PathVariable Long id) {
        return Result.success(paperAnalysisTaskService.analysisStatus(id));
    }
}