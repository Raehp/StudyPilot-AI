package com.studypilot.service;

import com.studypilot.config.RagProperties;
import com.studypilot.entity.ChatMessage;
import com.studypilot.vo.CitationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG Prompt 构造：防伪造系统提示词 + 编号检索上下文 + 预算内多轮历史。
 * 引用编号与后端组装的 CitationVO 顺序一一对应，模型只负责在回答中标注 [n]。
 */
@Service
@RequiredArgsConstructor
public class RagPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是 StudyPilot 高校学习科研助手，基于用户知识库中检索到的文献片段回答问题。必须遵守：
            1. 优先依据【检索内容】回答，并在引用处用 [n] 标注对应来源编号（n 为来源序号）。
            2. 不得把无依据的猜测说成事实；检索内容不足以回答时，明确说明"知识库中未找到相关信息"，可补充常识性建议但必须标明这不是来自知识库。
            3. 对知识库缺少的信息明确说明缺失，不要编造。
            4. 不得伪造论文标题、作者、页码和引用；只能引用【检索内容】中实际给出的来源，不得自行创造来源编号或文献信息。
            5. 若多个来源存在冲突，明确指出冲突及各自来源，不要擅自取舍。
            回答使用简体中文 Markdown 格式，条理清晰、简洁准确。""";

    private final RagProperties ragProperties;

    public List<Map<String, String>> build(String question, List<CitationVO> citations,
                                           List<ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (ChatMessage m : history) {
            String role = m.getRole() != null && m.getRole() == 1 ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", m.getContent() == null ? "" : m.getContent()));
        }
        StringBuilder user = new StringBuilder();
        user.append("【检索内容】\n");
        int budget = ragProperties.getMaxContextChars();
        for (int i = 0; i < citations.size(); i++) {
            CitationVO c = citations.get(i);
            String header = "[来源" + (i + 1) + "] 文档:《" + c.getDocumentName() + "》 页码:第"
                    + (c.getPageNum() == null ? "?" : c.getPageNum()) + "页 chunkId=" + c.getChunkId() + "\n";
            String content = c.getOriginalContent() == null ? "" : c.getOriginalContent();
            if (header.length() + content.length() > budget) {
                int keep = Math.max(0, budget - header.length() - 12);
                if (keep <= 0) {
                    break;
                }
                content = content.substring(0, keep) + "…(内容截断)";
            }
            user.append(header).append(content).append("\n\n");
            budget -= header.length() + content.length() + 2;
        }
        user.append("【用户问题】\n").append(question);
        messages.add(Map.of("role", "user", "content", user.toString()));
        return messages;
    }
}