package com.studypilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studypilot.common.DocumentStatus;
import com.studypilot.common.PageResult;
import com.studypilot.common.ResultCode;
import com.studypilot.entity.Document;
import com.studypilot.entity.KnowledgeBase;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.mapper.KnowledgeBaseMapper;
import com.studypilot.security.UserContext;
import com.studypilot.service.DocumentService;
import com.studypilot.service.DocumentStorageService;
import com.studypilot.service.KnowledgeBaseService;
import com.studypilot.service.PdfParseTaskService;
import com.studypilot.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentStorageService documentStorageService;
    private final PdfParseTaskService pdfParseTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO upload(MultipartFile file, Long kbId) {
        Long userId = UserContext.getUserId();
        long targetKbId = 0L;
        if (kbId != null && kbId != 0) {
            knowledgeBaseService.getOwnedKb(kbId);
            targetKbId = kbId;
        }
        String relativePath = documentStorageService.storePdf(file);
        try {
            Document doc = new Document();
            doc.setKbId(targetKbId);
            doc.setUserId(userId);
            doc.setTitle(buildTitle(file.getOriginalFilename()));
            doc.setFileName(truncate(file.getOriginalFilename(), 255));
            doc.setFilePath(relativePath);
            doc.setFileSize(file.getSize());
            doc.setPageCount(0);
            doc.setCharCount(0);
            doc.setNeedOcr(0);
            doc.setStatus(DocumentStatus.UPLOADED.getCode());
            doc.setErrorMsg("");
            doc.setAnalyzed(0);
            documentMapper.insert(doc);
            if (targetKbId != 0) {
                adjustDocCount(targetKbId, 1);
            }
            Document saved = documentMapper.selectById(doc.getId());
            // 事务提交后再投递异步解析，避免 worker 读不到未提交数据
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pdfParseTaskService.submitAfterUpload(saved.getId());
                }
            });
            return toVO(saved);
        } catch (RuntimeException e) {
            documentStorageService.deleteDocumentFiles(relativePath, null);
            throw e;
        }
    }

    @Override
    public PageResult<DocumentVO> page(Long kbId, long page, long size) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, UserContext.getUserId());
        if (kbId != null) {
            if (kbId != 0) {
                knowledgeBaseService.getOwnedKb(kbId);
            }
            wrapper.eq(Document::getKbId, kbId);
        }
        wrapper.orderByDesc(Document::getCreateTime);
        Page<Document> result = documentMapper.selectPage(new Page<>(page, size), wrapper);
        List<DocumentVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public DocumentVO detail(Long id) {
        return toVO(getOwnedDocument(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Document doc = getOwnedDocument(id);
        if (DocumentStatus.fromCode(doc.getStatus()) == DocumentStatus.PARSING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档正在解析中，请稍后再删除");
        }
        documentMapper.deleteById(id);
        if (doc.getKbId() != null && doc.getKbId() != 0) {
            adjustDocCount(doc.getKbId(), -1);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentStorageService.deleteDocumentFiles(doc.getFilePath(), doc.getId());
            }
        });
    }

    @Override
    public Document getOwnedDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    @Override
    public DocumentVO toVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setTitle(doc.getTitle());
        vo.setFileName(doc.getFileName());
        vo.setFileSize(doc.getFileSize());
        vo.setPageCount(doc.getPageCount());
        vo.setCharCount(doc.getCharCount());
        vo.setNeedOcr(doc.getNeedOcr() != null && doc.getNeedOcr() == 1);
        vo.setStatus(DocumentStatus.fromCode(doc.getStatus()).getExternalName());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreateTime(doc.getCreateTime());
        vo.setUpdateTime(doc.getUpdateTime());
        return vo;
    }

    private void adjustDocCount(Long kbId, int delta) {
        String sql = delta > 0
                ? "doc_count = doc_count + " + delta
                : "doc_count = GREATEST(doc_count - " + (-delta) + ", 0)";
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .setSql(sql));
    }

    private String buildTitle(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "未命名文档";
        }
        String name = originalFilename;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return truncate(name.isBlank() ? "未命名文档" : name, 128);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}