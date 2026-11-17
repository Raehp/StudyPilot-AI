package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.DocumentStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.entity.Document;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.DocumentContentVO;
import com.studypilot.vo.ParseStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfParseTaskService {

    private static final String LOCK_PREFIX = "studypilot:parse:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final DocumentMapper documentMapper;
    private final PdfParseService pdfParseService;
    private final DocumentStorageService documentStorageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 用户手动触发解析（含重复/并发防护） */
    public void submitParse(Long documentId) {
        Document doc = getOwnedDocument(documentId);
        DocumentStatus status = DocumentStatus.fromCode(doc.getStatus());
        if (status == DocumentStatus.READY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档已解析成功，无需重复解析");
        }
        if (status == DocumentStatus.PARSING
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(documentId)))) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "文档正在解析中，请勿重复提交");
        }
        trigger(doc, status);
    }

    /** 上传成功后自动触发（事务提交后调用，不做用户交互校验） */
    public void submitAfterUpload(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        try {
            trigger(doc, DocumentStatus.fromCode(doc.getStatus()));
        } catch (BusinessException e) {
            log.warn("上传后自动解析未启动: docId={}, reason={}", documentId, e.getMessage());
        }
    }

    private void trigger(Document doc, DocumentStatus current) {
        Long documentId = doc.getId();
        String lockKey = lockKey(documentId);
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "文档正在解析中，请勿重复提交");
        }
        // 状态 CAS：仅当前状态可流转为 PARSING，防并发重复解析
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .eq(Document::getStatus, current.getCode())
                .set(Document::getStatus, DocumentStatus.PARSING.getCode())
                .set(Document::getErrorMsg, ""));
        if (updated == 0) {
            stringRedisTemplate.delete(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "文档状态已变化，请刷新后重试");
        }
        try {
            pdfParseService.parseAsync(documentId, lockKey);
        } catch (TaskRejectedException e) {
            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, documentId)
                    .set(Document::getStatus, current.getCode()));
            stringRedisTemplate.delete(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "解析任务繁忙，请稍后重试");
        }
    }

    public ParseStatusVO parseStatus(Long documentId) {
        Document doc = getOwnedDocument(documentId);
        ParseStatusVO vo = new ParseStatusVO();
        vo.setDocumentId(doc.getId());
        vo.setStatus(DocumentStatus.fromCode(doc.getStatus()).getExternalName());
        vo.setPageCount(doc.getPageCount());
        vo.setCharCount(doc.getCharCount());
        vo.setNeedOcr(doc.getNeedOcr() != null && doc.getNeedOcr() == 1);
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setUpdateTime(doc.getUpdateTime());
        return vo;
    }

    public DocumentContentVO content(Long documentId) {
        Document doc = getOwnedDocument(documentId);
        if (DocumentStatus.fromCode(doc.getStatus()) != DocumentStatus.READY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档尚未解析成功，暂无可预览文本");
        }
        String json = documentStorageService.readParsedText(documentId);
        if (json == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "解析结果文件不存在");
        }
        try {
            return objectMapper.readValue(json, DocumentContentVO.class);
        } catch (Exception e) {
            log.error("解析结果反序列化失败: docId={}", documentId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析结果读取失败");
        }
    }

    private Document getOwnedDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private String lockKey(Long documentId) {
        return LOCK_PREFIX + documentId;
    }
}