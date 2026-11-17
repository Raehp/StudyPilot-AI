package com.studypilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studypilot.common.ResultCode;
import com.studypilot.dto.KnowledgeBaseCreateDTO;
import com.studypilot.dto.KnowledgeBaseUpdateDTO;
import com.studypilot.entity.Document;
import com.studypilot.entity.KnowledgeBase;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.mapper.KnowledgeBaseMapper;
import com.studypilot.security.UserContext;
import com.studypilot.service.DocumentStorageService;
import com.studypilot.service.KnowledgeBaseService;
import com.studypilot.vo.KnowledgeBaseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentStorageService documentStorageService;

    @Override
    public KnowledgeBaseVO create(KnowledgeBaseCreateDTO dto) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(UserContext.getUserId());
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription() == null ? "" : dto.getDescription());
        knowledgeBaseMapper.insert(kb);
        kb = knowledgeBaseMapper.selectById(kb.getId());
        return toVO(kb);
    }

    @Override
    public List<KnowledgeBaseVO> list() {
        List<KnowledgeBase> list = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, UserContext.getUserId())
                        .orderByDesc(KnowledgeBase::getCreateTime));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public KnowledgeBaseVO detail(Long id) {
        return toVO(getOwnedKb(id));
    }

    @Override
    public KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateDTO dto) {
        KnowledgeBase kb = getOwnedKb(id);
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription() == null ? "" : dto.getDescription());
        knowledgeBaseMapper.updateById(kb);
        return toVO(knowledgeBaseMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBase kb = getOwnedKb(id);
        Long userId = UserContext.getUserId();
        // 级联逻辑删除本人名下、该知识库中的文档（双重条件防误删他人文档）
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, id)
                        .eq(Document::getUserId, userId));
        if (!docs.isEmpty()) {
            documentMapper.delete(new LambdaQueryWrapper<Document>()
                    .eq(Document::getKbId, id)
                    .eq(Document::getUserId, userId));
        }
        knowledgeBaseMapper.deleteById(kb.getId());
        // 事务提交成功后再清理物理文件，回滚时文件不受影响
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                docs.forEach(doc -> documentStorageService.deleteDocumentFiles(doc.getFilePath(), doc.getId()));
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDocument(Long kbId, Long documentId) {
        getOwnedKb(kbId);
        Document doc = getOwnedDocument(documentId);
        if (kbId.equals(doc.getKbId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档已在该知识库中");
        }
        Long oldKbId = doc.getKbId();
        if (oldKbId != null && oldKbId != 0) {
            adjustDocCount(oldKbId, -1);
        }
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .eq(Document::getUserId, UserContext.getUserId())
                .set(Document::getKbId, kbId));
        adjustDocCount(kbId, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDocument(Long kbId, Long documentId) {
        getOwnedKb(kbId);
        Document doc = getOwnedDocument(documentId);
        if (!kbId.equals(doc.getKbId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档不在该知识库中");
        }
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .eq(Document::getUserId, UserContext.getUserId())
                .set(Document::getKbId, 0L));
        adjustDocCount(kbId, -1);
    }

    @Override
    public KnowledgeBase getOwnedKb(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null || !kb.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }

    private Document getOwnedDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private void adjustDocCount(Long kbId, int delta) {
        String sql = delta > 0
                ? "doc_count = doc_count + " + delta
                : "doc_count = GREATEST(doc_count - " + (-delta) + ", 0)";
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .setSql(sql));
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(kb, vo);
        return vo;
    }
}