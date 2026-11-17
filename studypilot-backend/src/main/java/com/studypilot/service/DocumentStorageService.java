package com.studypilot.service;

import com.studypilot.common.ResultCode;
import com.studypilot.config.StorageProperties;
import com.studypilot.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private static final String PDF_DIR = "pdf";
    private static final String PARSED_DIR = "parsed";
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final StorageProperties storageProperties;

    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve(PDF_DIR));
            Files.createDirectories(root.resolve(PARSED_DIR));
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件存储目录", e);
        }
        log.info("文件存储根目录: {}", root);
    }

    public void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        long maxBytes = (long) storageProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文件大小超出限制（最大 " + storageProperties.getMaxSizeMb() + "MB）");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 PDF 文件（扩展名不符）");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 PDF 文件（MIME 类型不符）");
        }
        if (!hasPdfMagic(file)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件内容不是有效的 PDF（文件头校验失败）");
        }
    }

    private boolean hasPdfMagic(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(1024);
            outer:
            for (int i = 0; i <= head.length - PDF_MAGIC.length; i++) {
                for (int j = 0; j < PDF_MAGIC.length; j++) {
                    if (head[i + j] != PDF_MAGIC[j]) {
                        continue outer;
                    }
                }
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件读取失败");
        }
    }

    /** 校验并落盘，返回相对路径；文件名由服务端生成，不信任用户文件名 */
    public String storePdf(MultipartFile file) {
        validatePdf(file);
        String relativePath = PDF_DIR + "/" + UUID.randomUUID().toString().replace("-", "") + ".pdf";
        Path target = resolve(relativePath);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件落盘失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件保存失败，请重试");
        }
        return relativePath;
    }

    /** 相对路径解析为绝对路径，防目录穿越 */
    public Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法文件路径");
        }
        return path;
    }

    public String parsedRelativePath(Long documentId) {
        return PARSED_DIR + "/" + documentId + ".json";
    }

    public void saveParsedText(Long documentId, String json) {
        try {
            Files.writeString(resolve(parsedRelativePath(documentId)), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("解析结果保存失败: docId={}", documentId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析结果保存失败");
        }
    }

    public String readParsedText(Long documentId) {
        Path path = resolve(parsedRelativePath(documentId));
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("解析结果读取失败: docId={}", documentId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析结果读取失败");
        }
    }

    /** 删除文档关联的物理文件（PDF + 解析结果），失败仅记录日志 */
    public void deleteDocumentFiles(String filePath, Long documentId) {
        deleteQuietly(filePath);
        deleteQuietly(parsedRelativePath(documentId));
    }

    private void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | BusinessException e) {
            log.warn("物理文件删除失败: {}", relativePath);
        }
    }
}