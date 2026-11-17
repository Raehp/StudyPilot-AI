package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.AnalysisStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.config.DashScopeProperties;
import com.studypilot.entity.Document;
import com.studypilot.entity.PaperAnalysis;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.mapper.PaperAnalysisMapper;
import com.studypilot.vo.DocumentContentVO;
import com.studypilot.vo.PaperAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 论文分析异步执行器：分段 map-reduce + 12 字段结构化输出。
 * 由 PaperAnalysisTaskService 跨 Bean 调用以保证 @Async 代理生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperAnalysisService {

    private static final String SYSTEM_PROMPT = """
            你是高校科研论文分析助手。仅依据用户提供的论文原文提取信息，严禁编造。
            输出一个 JSON 对象，包含且仅包含以下 12 个字段（值均为字符串）：
            title（论文标题，保留原文语言）、abstract（论文摘要，中文归纳）、
            background（研究背景）、researchQuestion（研究问题）、methods（研究方法）、
            technicalRoute（技术路线）、innovations（核心创新点）、experimentDesign（实验设计）、
            experimentResults（实验结果）、conclusion（研究结论）、limitations（研究局限性）、
            futureWork（未来研究方向）。
            任何一项无法从原文中找到依据时，该项值必须为"未明确说明"。
            除 JSON 外不要输出任何其他内容。""";

    private final PaperAnalysisMapper paperAnalysisMapper;
    private final DocumentMapper documentMapper;
    private final DocumentStorageService documentStorageService;
    private final DashScopeClient dashScopeClient;
    private final DashScopeProperties props;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Async("analysisExecutor")
    public void analyzeAsync(Long analysisId, Long documentId, Long userId, String lockKey) {
        long start = System.currentTimeMillis();
        try {
            int updated = paperAnalysisMapper.update(null, new LambdaUpdateWrapper<PaperAnalysis>()
                    .eq(PaperAnalysis::getId, analysisId)
                    .eq(PaperAnalysis::getStatus, AnalysisStatus.PENDING.getCode())
                    .set(PaperAnalysis::getStatus, AnalysisStatus.PROCESSING.getCode()));
            if (updated == 0) {
                log.warn("分析任务状态已变化，跳过: analysisId={}", analysisId);
                return;
            }
            DocumentContentVO content = readParsedContent(documentId);
            String fullText = buildFullText(content);
            if (fullText.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "文档无有效文本，无法分析");
            }
            int segmentChars = Math.max(1000, props.getSegmentChars());
            List<String> segments = split(fullText, segmentChars);
            String finalJson;
            int segmentCount;
            if (segments.size() == 1) {
                segmentCount = 0;
                finalJson = dashScopeClient.chatJson(userId, SYSTEM_PROMPT,
                        "分析以下论文全文，输出 12 字段 JSON。\n\n论文全文：\n" + segments.get(0));
            } else {
                List<String> picked = sample(segments, Math.max(2, props.getMaxSegments()));
                segmentCount = picked.size();
                List<String> partials = new ArrayList<>();
                for (int i = 0; i < picked.size(); i++) {
                    String partial = dashScopeClient.chatJson(userId, SYSTEM_PROMPT,
                            "以下是同一篇论文的第 " + (i + 1) + "/" + picked.size()
                                    + " 段原文（超长论文均匀采样，可能不完整）。"
                                    + "仅提取该段中可确定的信息，输出 12 字段 JSON，"
                                    + "无法确定的字段填\"未明确说明\"。\n\n原文：\n" + picked.get(i));
                    if (isJsonObject(partial)) {
                        partials.add(partial);
                    } else {
                        log.warn("分段提取结果非 JSON，跳过: analysisId={} segment={}", analysisId, i + 1);
                    }
                }
                if (partials.isEmpty()) {
                    throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                            "AI 分段提取结果格式异常，请重试");
                }
                finalJson = dashScopeClient.chatJson(userId, SYSTEM_PROMPT,
                        "以下是同一篇论文各段的要点提取结果（JSON 数组）。"
                                + "合并归纳为最终的 12 字段 JSON：摘要需综合全部段落；"
                                + "信息冲突时以证据更充分者为准；仍无法确定的字段填\"未明确说明\"。\n\n"
                                + "各段结果：\n[" + String.join(",", partials) + "]");
            }
            PaperAnalysisResult result = parseResult(finalJson);
            long cost = System.currentTimeMillis() - start;
            String resultJson = objectMapper.writeValueAsString(result);
            paperAnalysisMapper.update(null, new LambdaUpdateWrapper<PaperAnalysis>()
                    .eq(PaperAnalysis::getId, analysisId)
                    .set(PaperAnalysis::getStatus, AnalysisStatus.SUCCESS.getCode())
                    .set(PaperAnalysis::getResult, resultJson)
                    .set(PaperAnalysis::getSummary, result.getAbstractText())
                    .set(PaperAnalysis::getErrorMsg, "")
                    .set(PaperAnalysis::getDurationMs, cost)
                    .set(PaperAnalysis::getSegmentCount, segmentCount));
            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, documentId)
                    .set(Document::getAnalyzed, 1)
                    .set(Document::getSummary, result.getAbstractText()));
            log.info("论文分析成功 analysisId={} docId={} segments={} latencyMs={}",
                    analysisId, documentId, segmentCount, cost);
        } catch (BusinessException e) {
            markFailed(analysisId, e.getMessage(), start);
        } catch (Exception e) {
            log.error("论文分析异常 analysisId={} docId={}", analysisId, documentId, e);
            markFailed(analysisId, "AI 分析失败，请稍后重试", start);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void markFailed(Long analysisId, String message, long start) {
        try {
            paperAnalysisMapper.update(null, new LambdaUpdateWrapper<PaperAnalysis>()
                    .eq(PaperAnalysis::getId, analysisId)
                    .set(PaperAnalysis::getStatus, AnalysisStatus.FAILED.getCode())
                    .set(PaperAnalysis::getErrorMsg, message)
                    .set(PaperAnalysis::getDurationMs, System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.error("分析失败状态写入异常 analysisId={}", analysisId, e);
        }
    }

    private DocumentContentVO readParsedContent(Long documentId) {
        String json = documentStorageService.readParsedText(documentId);
        if (json == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "解析结果文件不存在，请重新解析文档");
        }
        try {
            return objectMapper.readValue(json, DocumentContentVO.class);
        } catch (Exception e) {
            log.error("解析结果反序列化失败: docId={}", documentId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析结果读取失败");
        }
    }

    private String buildFullText(DocumentContentVO content) {
        StringBuilder sb = new StringBuilder();
        if (content.getPages() != null) {
            for (DocumentContentVO.PageText page : content.getPages()) {
                if (page.getText() != null && !page.getText().isBlank()) {
                    sb.append(page.getText()).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private List<String> split(String text, int segmentChars) {
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < text.length(); i += segmentChars) {
            segments.add(text.substring(i, Math.min(text.length(), i + segmentChars)));
        }
        return segments;
    }

    /** 均匀采样（必含首尾段），控制超长论文的调用成本 */
    private List<String> sample(List<String> segments, int maxSegments) {
        if (segments.size() <= maxSegments) {
            return segments;
        }
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < maxSegments; i++) {
            int idx = (int) Math.round((double) i * (segments.size() - 1) / (maxSegments - 1));
            picked.add(segments.get(idx));
        }
        return picked;
    }

    private boolean isJsonObject(String text) {
        try {
            JsonNode node = objectMapper.readTree(stripFences(text));
            return node != null && node.isObject();
        } catch (Exception e) {
            return false;
        }
    }

    private PaperAnalysisResult parseResult(String json) {
        try {
            PaperAnalysisResult result = objectMapper.readValue(stripFences(json), PaperAnalysisResult.class);
            result.normalize();
            return result;
        } catch (Exception e) {
            log.warn("分析结果 JSON 解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                    "AI 返回结果格式异常，请重试");
        }
    }

    private String stripFences(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}