package com.studypilot.service;

import com.studypilot.common.PageResult;
import com.studypilot.entity.Document;
import com.studypilot.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    DocumentVO upload(MultipartFile file, Long kbId);

    PageResult<DocumentVO> page(Long kbId, long page, long size);

    DocumentVO detail(Long id);

    void delete(Long id);

    /** 校验归属并返回实体，不存在或非本人一律 404 */
    Document getOwnedDocument(Long id);

    DocumentVO toVO(Document doc);
}