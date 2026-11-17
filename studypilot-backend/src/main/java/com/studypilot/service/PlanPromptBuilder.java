package com.studypilot.service;

import com.studypilot.config.PlanProperties;
import com.studypilot.entity.StudyPlan;
import com.studypilot.vo.VectorSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学习计划生成 Prompt 构造。返回 [system, user]。
 * 可选携带知识库真实检索片段（编号），模型据此推荐真实文档，不得伪造来源。
 */
@Service
@RequiredArgsConstructor
public class PlanPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是高校学习规划助手，根据用户的学习目标生成可执行的逐日学习计划。必须遵守：
            1. 仅输出一个 JSON 对象，包含且仅包含两个字段：
               aiAdvice（字符串，总体学习建议）、tasks（数组，逐日学习任务）。
            2. tasks 中每个元素包含字段：stage（学习阶段名）、taskDate（yyyy-MM-dd 日期）、
               title（任务名，不超过40字）、content（任务详情）、focus（学习重点）、
               recommendedContent（推荐学习内容）、durationMin（预计分钟数，整数）、stageQuiz（布尔，是否阶段性测验）。
            3. taskDate 必须落在用户给定的开始日期与结束日期之间（含端点），按日期升序排列；
               每天任务总时长尽量贴近用户给定的每日学习时长。
            4. 每个学习阶段结束时安排一个 stageQuiz=true 的阶段性测验任务。
            5. 若提供了【知识库检索内容】，recommendedContent 应优先引用其中真实出现的文档主题，
               不得伪造文档标题或内容；未提供检索内容时基于目标给出通用但具体的学习建议。
            6. 任务数量应合理覆盖整个周期，不要超过 60 条。除 JSON 外不要输出任何其他内容。""";

    private final PlanProperties planProperties;

    public String[] build(StudyPlan plan, List<VectorSearchResultVO> chunks) {
        StringBuilder user = new StringBuilder();
        user.append("【学习目标】\n");
        user.append("计划名称：").append(nz(plan.getTitle())).append('\n');
        user.append("学习主题：").append(nz(plan.getTopic())).append('\n');
        user.append("目标描述：").append(nz(plan.getGoal())).append('\n');
        user.append("当前知识水平：").append(nz(plan.getCurrentLevel())).append('\n');
        user.append("预期学习成果：").append(nz(plan.getExpectedOutcome())).append('\n');
        user.append("开始日期：").append(plan.getStartDate() == null ? "未指定" : plan.getStartDate()).append('\n');
        user.append("结束日期：").append(plan.getEndDate() == null ? "未指定" : plan.getEndDate()).append('\n');
        user.append("每日学习时长（分钟）：").append(plan.getDailyMinutes() == null ? 60 : plan.getDailyMinutes()).append('\n');
        if (chunks != null && !chunks.isEmpty()) {
            user.append("\n【知识库检索内容】\n");
            int budget = planProperties.getMaxContextChars();
            for (int i = 0; i < chunks.size(); i++) {
                VectorSearchResultVO c = chunks.get(i);
                String header = "[来源" + i + "] 文档:《" + nz(c.getDocumentTitle()) + "》\n";
                String content = nz(c.getContent());
                if (header.length() + content.length() > budget) {
                    int keep = Math.max(0, budget - header.length() - 12);
                    if (keep <= 0) {
                        break;
                    }
                    content = content.substring(0, keep) + "…(截断)";
                }
                user.append(header).append(content).append("\n\n");
                budget -= header.length() + content.length() + 2;
            }
        }
        user.append("\n请生成逐日学习计划的 JSON。");
        return new String[]{SYSTEM_PROMPT, user.toString()};
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}