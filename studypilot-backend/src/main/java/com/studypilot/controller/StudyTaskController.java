package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.dto.StudyTaskStatusDTO;
import com.studypilot.dto.StudyTaskUpdateDTO;
import com.studypilot.service.StudyTaskService;
import com.studypilot.vo.StudyTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-tasks")
@RequiredArgsConstructor
public class StudyTaskController {

    private final StudyTaskService studyTaskService;

    @PutMapping("/{id}/status")
    public Result<StudyTaskVO> updateStatus(@PathVariable Long id, @Valid @RequestBody StudyTaskStatusDTO dto) {
        return Result.success(studyTaskService.updateStatus(id, dto.getStatus()));
    }

    @PutMapping("/{id}")
    public Result<StudyTaskVO> update(@PathVariable Long id, @Valid @RequestBody StudyTaskUpdateDTO dto) {
        return Result.success(studyTaskService.update(id, dto));
    }
}