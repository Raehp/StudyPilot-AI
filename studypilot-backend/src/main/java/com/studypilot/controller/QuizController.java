package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.dto.QuizGenerateDTO;
import com.studypilot.dto.QuizSubmitDTO;
import com.studypilot.service.QuizService;
import com.studypilot.vo.GenerateStatusVO;
import com.studypilot.vo.QuizDetailVO;
import com.studypilot.vo.QuizListItemVO;
import com.studypilot.vo.QuizResultVO;
import com.studypilot.vo.WrongAnswerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/generate")
    public Result<GenerateStatusVO> generate(@Valid @RequestBody QuizGenerateDTO dto) {
        return Result.success(quizService.generate(dto));
    }

    @GetMapping
    public Result<List<QuizListItemVO>> list() {
        return Result.success(quizService.list());
    }

    @GetMapping("/wrong-answers")
    public Result<List<WrongAnswerVO>> wrongAnswers() {
        return Result.success(quizService.wrongAnswers());
    }

    @GetMapping("/{id}")
    public Result<QuizDetailVO> detail(@PathVariable Long id) {
        return Result.success(quizService.detail(id));
    }

    @GetMapping("/{id}/generate-status")
    public Result<GenerateStatusVO> generateStatus(@PathVariable Long id) {
        return Result.success(quizService.generateStatus(id));
    }

    @PostMapping("/{id}/submit")
    public Result<QuizResultVO> submit(@PathVariable Long id, @Valid @RequestBody QuizSubmitDTO dto) {
        return Result.success(quizService.submit(id, dto));
    }

    @GetMapping("/{id}/result")
    public Result<QuizResultVO> result(@PathVariable Long id) {
        return Result.success(quizService.result(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        quizService.delete(id);
        return Result.success();
    }
}