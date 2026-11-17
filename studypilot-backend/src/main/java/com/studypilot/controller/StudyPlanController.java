package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.dto.StudyPlanCreateDTO;
import com.studypilot.dto.StudyPlanUpdateDTO;
import com.studypilot.service.StudyPlanGenerator;
import com.studypilot.service.StudyPlanService;
import com.studypilot.service.StudyTaskService;
import com.studypilot.vo.GenerateStatusVO;
import com.studypilot.vo.StudyPlanProgressVO;
import com.studypilot.vo.StudyPlanVO;
import com.studypilot.vo.StudyTaskVO;
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
@RequestMapping("/api/study-plans")
@RequiredArgsConstructor
public class StudyPlanController {

    private final StudyPlanService studyPlanService;
    private final StudyPlanGenerator studyPlanGenerator;
    private final StudyTaskService studyTaskService;

    @PostMapping
    public Result<StudyPlanVO> create(@Valid @RequestBody StudyPlanCreateDTO dto) {
        return Result.success(studyPlanService.create(dto));
    }

    @GetMapping
    public Result<List<StudyPlanVO>> list() {
        return Result.success(studyPlanService.list());
    }

    @GetMapping("/{id}")
    public Result<StudyPlanVO> get(@PathVariable Long id) {
        return Result.success(studyPlanService.get(id));
    }

    @PutMapping("/{id}")
    public Result<StudyPlanVO> update(@PathVariable Long id, @Valid @RequestBody StudyPlanUpdateDTO dto) {
        return Result.success(studyPlanService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studyPlanService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/generate")
    public Result<GenerateStatusVO> generate(@PathVariable Long id) {
        return Result.success(studyPlanGenerator.submit(id));
    }

    @GetMapping("/{id}/generate-status")
    public Result<GenerateStatusVO> generateStatus(@PathVariable Long id) {
        return Result.success(studyPlanGenerator.status(id));
    }

    @GetMapping("/{id}/tasks")
    public Result<List<StudyTaskVO>> tasks(@PathVariable Long id) {
        return Result.success(studyTaskService.listByPlan(id));
    }

    @GetMapping("/{id}/progress")
    public Result<StudyPlanProgressVO> progress(@PathVariable Long id) {
        return Result.success(studyPlanService.progress(id));
    }
}