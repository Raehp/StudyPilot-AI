package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.AnalysisStatus;
import com.studypilot.common.DocumentStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.entity.Document;
import com.studypilot.entity.PaperAnalysis;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.mapper.PaperAnalysisMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.AnalysisStatusVO;
import com.studypilot.vo.PaperAnalysisResult;
import com.studypilot.vo.PaperAnalysisVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperAnalysisTaskService {

    private static final String LOCK_PREFIX = "studypilot:analysis:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final DocumentMapper documentMapper;
    private final PaperAnalysisMapper paperAnalysisMapper;
    private final PaperAnalysisService paperAnalysisService;
    private final DashScopeClient dashScopeClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 发起分析（含前置校验、重复防护、重新分析覆盖语义） */
    public AnalysisStatusVO submitAnalysis(Long documentId, boolean reanalyze) {
        Document doc = getOwnedDocument(documentId);
        if (DocumentStatus.fromCode(doc.getStatus()) != DocumentStatus.READY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档尚未解析成功，无法发起 AI 分析");
        }
        if ((doc.getNeedOcr() != null && doc.getNeedOcr() == 1)
                || doc.getCharCount() == null || doc.getCharCount() == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文档无有效文本（扫描版需 OCR），无法发起 AI 分析");
        }
        Long active = paperAnalysisMapper.selectCount(new LambdaQueryWrapper<PaperAnalysis>()
                .eq(PaperAnalysis::getDocumentId, documentId)
                .in(PaperAnalysis::getStatus,
                        AnalysisStatus.PENDING.getCode(), AnalysisStatus.PROCESSING.getCode()));
        if (active > 0) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "分析正在进行中，请勿重复提交");
        }
        PaperAnalysis latest = latestTask(documentId);
        if (latest != null && AnalysisStatus.fromCode(latest.getStatus()) == AnalysisStatus.SUCCESS
                && !reanalyze) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文档已完成 AI 分析，重新分析将覆盖当前结果，请确认后重试");
        }
        String lockKey = LOCK_PREFIX + documentId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "分析正在进行中，请勿重复提交");
        }
        PaperAnalysis task = new PaperAnalysis();
        task.setDocumentId(documentId);
        task.setUserId(UserContext.getUserId());
        task.setModel(dashScopeClient.getModel());
        task.setStatus(AnalysisStatus.PENDING.getCode());
        task.setErrorMsg("");
        task.setDurationMs(0L);
        task.setSegmentCount(0);
        paperAnalysisMapper.insert(task);
        try {
            paperAnalysisService.analyzeAsync(task.getId(), documentId, UserContext.getUserId(), lockKey);
        } catch (TaskRejectedException e) {
            paperAnalysisMapper.update(null, new LambdaUpdateWrapper<PaperAnalysis>()
                    .eq(PaperAnalysis::getId, task.getId())
                    .set(PaperAnalysis::getStatus, AnalysisStatus.FAILED.getCode())
                    .set(PaperAnalysis::getErrorMsg, "分析任务繁忙，请稍后重试"));
            stringRedisTemplate.delete(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "分析任务繁忙，请稍后重试");
        }
        return toStatusVO(paperAnalysisMapper.selectById(task.getId()));
    }

    /** 最新任务状态；从未分析时 analysisId/status 为 null */
    public AnalysisStatusVO analysisStatus(Long documentId) {
        getOwnedDocument(documentId);
        PaperAnalysis latest = latestTask(documentId);
        if (latest == null) {
            AnalysisStatusVO vo = new AnalysisStatusVO();
            vo.setDocumentId(documentId);
            return vo;
        }
        return toStatusVO(latest);
    }

    /** 最新成功分析结果（重新分析成功后覆盖展示，历史任务保留） */
    public PaperAnalysisVO latestAnalysis(Long documentId) {
        Document doc = getOwnedDocument(documentId);
        PaperAnalysis latest = latestTask(documentId);
        if (latest == null || AnalysisStatus.fromCode(latest.getStatus()) != AnalysisStatus.SUCCESS) {
            throw new BusinessException(ResultCode.NOT_FOUND, "暂无分析结果");
        }
        PaperAnalysisVO vo = new PaperAnalysisVO();
        vo.setAnalysisId(latest.getId());
        vo.setDocumentId(documentId);
        vo.setDocumentTitle(doc.getTitle());
        vo.setModel(latest.getModel());
        vo.setStatus(AnalysisStatus.SUCCESS.getExternalName());
        vo.setSummary(latest.getSummary());
        vo.setDurationMs(latest.getDurationMs());
        vo.setSegmentCount(latest.getSegmentCount());
        vo.setCreateTime(latest.getCreateTime());
        vo.setUpdateTime(latest.getUpdateTime());
        try {
            PaperAnalysisResult result = objectMapper.readValue(latest.getResult(), PaperAnalysisResult.class);
            result.normalize();
            vo.setResult(result);
        } catch (Exception e) {
            log.error("分析结果反序列化失败: analysisId={}", latest.getId(), e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "分析结果读取失败");
        }
        return vo;
    }

    private PaperAnalysis latestTask(Long documentId) {
        List<PaperAnalysis> tasks = paperAnalysisMapper.selectList(new LambdaQueryWrapper<PaperAnalysis>()
                .eq(PaperAnalysis::getDocumentId, documentId)
                .orderByDesc(PaperAnalysis::getId)
                .last("LIMIT 1"));
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    private AnalysisStatusVO toStatusVO(PaperAnalysis task) {
        AnalysisStatusVO vo = new AnalysisStatusVO();
        vo.setAnalysisId(task.getId());
        vo.setDocumentId(task.getDocumentId());
        vo.setStatus(AnalysisStatus.fromCode(task.getStatus()).getExternalName());
        vo.setModel(task.getModel());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setDurationMs(task.getDurationMs());
        vo.setSegmentCount(task.getSegmentCount());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private Document getOwnedDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }
}