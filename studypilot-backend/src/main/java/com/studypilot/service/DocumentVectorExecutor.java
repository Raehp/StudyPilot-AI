package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.VectorStatus;
import com.studypilot.entity.Document;
import com.studypilot.entity.DocumentChunk;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentChunkMapper;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.vo.DocumentContentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量化异步执行体。由 DocumentVectorService 跨 Bean 调用以保证 @Async 代理生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVectorExecutor {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentStorageService documentStorageService;
    private final ChunkService chunkService;
    private final DashScopeEmbeddingClient embeddingClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Async("vectorExecutor")
    public void vectorizeAsync(Long documentId, Long userId, Long kbId, String lockKey) {
        try {
            Document doc = documentMapper.selectById(documentId);
            if (doc == null || VectorStatus.fromCode(doc.getVectorStatus()) != VectorStatus.PROCESSING) {
                return;
            }
            String json = documentStorageService.readParsedText(documentId);
            if (json == null) {
                cleanupAndFail(documentId, "解析结果文件不存在，请重新解析");
                return;
            }
            DocumentContentVO content = objectMapper.readValue(json, DocumentContentVO.class);
            List<DocumentChunk> chunks = chunkService.chunk(documentId, userId, kbId, content);
            if (chunks.isEmpty()) {
                cleanupAndFail(documentId, "文档清洗后无有效文本，无法切片");
                return;
            }
            // 重建前物理清除旧切片/向量（含失败重试的残留），杜绝孤立记录
            documentChunkMapper.physicalDeleteByDocId(documentId);
            for (DocumentChunk chunk : chunks) {
                chunk.setEmbeddingModel(embeddingClient.getModel());
                chunk.setEmbeddingDimension(embeddingClient.getDimension());
                chunk.setEmbeddingVersion(embeddingClient.getVersion());
                documentChunkMapper.insert(chunk);
            }
            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, documentId)
                    .set(Document::getChunkCount, chunks.size()));

            int batchSize = embeddingClient.getBatchSize();
            int embedded = 0;
            for (int from = 0; from < chunks.size(); from += batchSize) {
                List<DocumentChunk> batch = chunks.subList(from, Math.min(from + batchSize, chunks.size()));
                List<float[]> vectors = embeddingClient.embedBatch(userId,
                        batch.stream().map(DocumentChunk::getContent).toList());
                for (int i = 0; i < batch.size(); i++) {
                    DocumentChunk chunk = batch.get(i);
                    documentChunkMapper.update(null, new LambdaUpdateWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getId, chunk.getId())
                            .set(DocumentChunk::getEmbedding, objectMapper.writeValueAsString(vectors.get(i))));
                }
                embedded += batch.size();
                documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                        .eq(Document::getId, documentId)
                        .set(Document::getEmbeddingCount, embedded));
            }
            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, documentId)
                    .set(Document::getVectorStatus, VectorStatus.SUCCESS.getCode())
                    .set(Document::getChunkCount, chunks.size())
                    .set(Document::getEmbeddingCount, embedded)
                    .set(Document::getVectorErrorMsg, ""));
            log.info("向量化完成 docId={} chunks={} embedded={} model={} dim={}",
                    documentId, chunks.size(), embedded, embeddingClient.getModel(), embeddingClient.getDimension());
        } catch (BusinessException e) {
            cleanupAndFail(documentId, e.getMessage());
        } catch (Exception e) {
            log.error("向量化失败 docId={}", documentId, e);
            cleanupAndFail(documentId, "向量化失败，请稍后重试");
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void cleanupAndFail(Long documentId, String message) {
        try {
            documentChunkMapper.physicalDeleteByDocId(documentId);
            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, documentId)
                    .set(Document::getVectorStatus, VectorStatus.FAILED.getCode())
                    .set(Document::getChunkCount, 0)
                    .set(Document::getEmbeddingCount, 0)
                    .set(Document::getVectorErrorMsg, message == null ? "向量化失败" : message));
        } catch (Exception e) {
            log.error("向量化失败后清理异常 docId={}", documentId, e);
        }
    }
}