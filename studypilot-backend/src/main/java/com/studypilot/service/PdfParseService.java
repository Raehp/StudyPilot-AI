package com.studypilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.DocumentStatus;
import com.studypilot.entity.Document;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.vo.DocumentContentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfParseService {

    private final DocumentMapper documentMapper;
    private final DocumentStorageService documentStorageService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Async("parseExecutor")
    public void parseAsync(Long documentId, String lockKey) {
        try {
            doParse(documentId);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void doParse(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        try {
            Path pdfPath = documentStorageService.resolve(doc.getFilePath());
            try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile())) {
                int pageCount = pdf.getNumberOfPages();
                if (pageCount == 0) {
                    markFailed(documentId, "PDF 为空文档（0 页），无法解析");
                    return;
                }
                PDFTextStripper stripper = new PDFTextStripper();
                List<DocumentContentVO.PageText> pages = new ArrayList<>();
                int charCount = 0;
                for (int i = 1; i <= pageCount; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String text = stripper.getText(pdf).strip();
                    charCount += text.length();
                    DocumentContentVO.PageText page = new DocumentContentVO.PageText();
                    page.setPageNum(i);
                    page.setText(text);
                    pages.add(page);
                }
                boolean needOcr = charCount == 0;

                DocumentContentVO content = new DocumentContentVO();
                content.setDocumentId(documentId);
                content.setTitle(doc.getTitle());
                content.setPageCount(pageCount);
                content.setCharCount(charCount);
                content.setNeedOcr(needOcr);
                content.setPages(pages);
                documentStorageService.saveParsedText(documentId, objectMapper.writeValueAsString(content));

                Document update = new Document();
                update.setId(documentId);
                update.setStatus(DocumentStatus.READY.getCode());
                update.setPageCount(pageCount);
                update.setCharCount(charCount);
                update.setNeedOcr(needOcr ? 1 : 0);
                update.setErrorMsg("");
                documentMapper.updateById(update);
                log.info("PDF 解析完成: docId={}, pages={}, chars={}, needOcr={}",
                        documentId, pageCount, charCount, needOcr);
            }
        } catch (InvalidPasswordException e) {
            log.warn("PDF 已加密: docId={}", documentId);
            markFailed(documentId, "文档已加密，无法提取文本");
        } catch (Exception e) {
            // 完整堆栈仅记录在服务端日志，前端只看到业务化描述
            log.error("PDF 解析失败: docId={}", documentId, e);
            markFailed(documentId, "PDF 文件损坏或格式异常，无法解析");
        }
    }

    private void markFailed(Long documentId, String message) {
        Document update = new Document();
        update.setId(documentId);
        update.setStatus(DocumentStatus.FAILED.getCode());
        update.setErrorMsg(message);
        documentMapper.updateById(update);
    }
}