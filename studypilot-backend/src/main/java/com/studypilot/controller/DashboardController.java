package com.studypilot.controller;

import com.studypilot.common.Result;
import com.studypilot.service.DashboardService;
import com.studypilot.vo.DashboardOverviewVO;
import com.studypilot.vo.DocumentStatisticsVO;
import com.studypilot.vo.LearningStatisticsVO;
import com.studypilot.vo.QuizStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作台真实统计接口（均需 JWT；数据按当前用户隔离，纯只读）。 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview() {
        return Result.success(dashboardService.overview());
    }

    @GetMapping("/document-statistics")
    public Result<DocumentStatisticsVO> documentStatistics() {
        return Result.success(dashboardService.documentStatistics());
    }

    @GetMapping("/learning-statistics")
    public Result<LearningStatisticsVO> learningStatistics() {
        return Result.success(dashboardService.learningStatistics());
    }

    @GetMapping("/quiz-statistics")
    public Result<QuizStatisticsVO> quizStatistics() {
        return Result.success(dashboardService.quizStatistics());
    }
}