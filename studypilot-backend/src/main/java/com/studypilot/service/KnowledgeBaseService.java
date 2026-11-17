package com.studypilot.service;

import com.studypilot.dto.KnowledgeBaseCreateDTO;
import com.studypilot.dto.KnowledgeBaseUpdateDTO;
import com.studypilot.entity.KnowledgeBase;
import com.studypilot.vo.KnowledgeBaseVO;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseVO create(KnowledgeBaseCreateDTO dto);

    List<KnowledgeBaseVO> list();

    KnowledgeBaseVO detail(Long id);

    KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateDTO dto);

    void delete(Long id);

    void addDocument(Long kbId, Long documentId);

    void removeDocument(Long kbId, Long documentId);

    /** 校验归属并返回实体，不存在或非本人一律 404 */
    KnowledgeBase getOwnedKb(Long id);
}