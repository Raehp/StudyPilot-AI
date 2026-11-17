package com.studypilot.service;

import com.studypilot.config.QuizProperties;
import com.studypilot.vo.VectorSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能出题 Prompt 构造。返回 [system, user]。
 * 基于知识库真实检索片段出题；sourceIndex 引用片段序号（0 起），由后端映射真实来源，杜绝伪造。
 */
@Service
@RequiredArgsConstructor
public class QuizPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是高校课程命题助手，基于用户知识库中检索到的真实文献片段命题。必须遵守：
            1. 仅输出一个 JSON 对象，包含且仅包含字段 questions（数组）。
            2. questions 中每个元素包含字段：type（整数题型）、difficulty（整数难度）、stem（题干）、
               options（选项文本数组，不含字母前缀）、answer（正确答案）、analysis（题目解析）、
               knowledgePoint（对应知识点）、sourceIndex（出题依据的检索片段序号，0 起整数）。
            3. 题型与答案编码规则：
               - type=1 单选：options 为 4 个选项文本；answer 为单个大写字母（A/B/C/D），表示正确选项序号。
               - type=2 多选：options 为 4 个选项文本；answer 为逗号分隔的大写字母（如 "A,C"），至少 2 个。
               - type=3 判断：options 固定为 ["正确","错误"]；answer 为 "A"（正确）或 "B"（错误）。
               - type=4 简答：options 为空数组 []；answer 为参考答案文本。
            4. 题目必须依据【检索内容】中的真实知识命题，不得编造文献中不存在的事实；
               sourceIndex 必须是【检索内容】中实际给出的序号，不得创造不存在的序号。
            5. 每道题的 analysis 需给出清晰的解题依据；简答题 answer 需是可评分的参考答案要点。
            6. 严格生成指定数量与题型的题目。除 JSON 外不要输出任何其他内容。""";

    private final QuizProperties quizProperties;

    public String[] build(String topic, int questionCount, int difficulty,
                          List<Integer> questionTypes, List<VectorSearchResultVO> chunks) {
        StringBuilder user = new StringBuilder();
        user.append("【出题配置】\n");
        user.append("知识主题：").append(topic == null || topic.isBlank() ? "综合" : topic).append('\n');
        user.append("题目数量：").append(questionCount).append('\n');
        user.append("难度：").append(difficultyLabel(difficulty)).append('\n');
        user.append("题型：").append(questionTypes.stream().map(QuizPromptBuilder::typeLabel)
                .collect(Collectors.joining("、"))).append('\n');
        user.append("请按题型均匀分配题目数量。\n");
        if (chunks != null && !chunks.isEmpty()) {
            user.append("\n【检索内容】\n");
            int budget = quizProperties.getMaxContextChars();
            for (int i = 0; i < chunks.size(); i++) {
                VectorSearchResultVO c = chunks.get(i);
                String header = "[来源" + i + "] 文档:《" + nz(c.getDocumentTitle())
                        + "》 页码:第" + (c.getPageNum() == null ? "?" : c.getPageNum()) + "页\n";
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
        user.append("\n请生成 ").append(questionCount).append(" 道题目的 JSON。");
        return new String[]{SYSTEM_PROMPT, user.toString()};
    }

    private static String difficultyLabel(int d) {
        return switch (d) {
            case 2 -> "进阶";
            case 3 -> "挑战";
            default -> "基础";
        };
    }

    private static String typeLabel(int t) {
        return switch (t) {
            case 2 -> "多选";
            case 3 -> "判断";
            case 4 -> "简答";
            default -> "单选";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}