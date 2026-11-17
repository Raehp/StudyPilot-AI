package com.studypilot.controller;

import com.studypilot.common.PageResult;
import com.studypilot.common.Result;
import com.studypilot.service.DocumentService;
import com.studypilot.service.PdfParseTaskService;
import com.studypilot.vo.DocumentContentVO;
import com.studypilot.vo.DocumentVO;
import com.studypilot.vo.ParseStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final PdfParseTaskService pdfParseTaskService;

    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "kbId", required = false) Long kbId) {
        return Result.success(documentService.upload(file, kbId));
    }

    @GetMapping
    public Result<PageResult<DocumentVO>> page(@RequestParam(value = "kbId", required = false) Long kbId,
                                               @RequestParam(value = "page", defaultValue = "1") long page,
                                               @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.success(documentService.page(kbId, page, size));
    }

    @GetMapping("/{id}")
    public Result<DocumentVO> detail(@PathVariable Long id) {
        return Result.success(documentService.detail(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/parse-status")
    public Result<ParseStatusVO> parseStatus(@PathVariable Long id) {
        return Result.success(pdfParseTaskService.parseStatus(id));
    }

    @PostMapping("/{id}/parse")
    public Result<Void> parse(@PathVariable Long id) {
        pdfParseTaskService.submitParse(id);
        return Result.success();
    }

    @GetMapping("/{id}/content")
    public Result<DocumentContentVO> content(@PathVariable Long id) {
        return Result.success(pdfParseTaskService.content(id));
    }
}