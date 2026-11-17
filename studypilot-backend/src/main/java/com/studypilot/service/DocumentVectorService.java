package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studypilot.common.DocumentStatus;
import com.studypilot.common.ResultCode;
import com.studypilot.common.VectorMath;
import com.studypilot.common.VectorStatus;
import com.studypilot.entity.Document;
import com.studypilot.entity.DocumentChunk;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.DocumentChunkMapper;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.VectorSearchResultVO;
import com.studypilot.vo.VectorStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档向量化任务：切片 → 批量 Embedding → MySQL JSON 入库；
 * Redis 锁 + 状态 CAS 防重复并发；失败清理派生数据支持重试；
 * 检索执行用户/知识库权限过滤与模型/版本/维度一致性校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVectorService {

    private static final String LOCK_PREFIX = "studypilot:vector:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentVectorExecutor documentVectorExecutor;
    private final DashScopeEmbeddingClient embeddingClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public VectorStatusVO submitVectorize(Long documentId, boolean reprocess) {
        Document doc = getOwnedDocument(documentId);
        if (DocumentStatus.fromCode(doc.getStatus()) != DocumentStatus.READY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档尚未解析成功，无法进行向量化");
        }
        if ((doc.getNeedOcr() != null && doc.getNeedOcr() == 1)
                || doc.getCharCount() == null || doc.getCharCount() == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档无有效文本（扫描版需 OCR），无法进行向量化");
        }
        VectorStatus current = VectorStatus.fromCode(doc.getVectorStatus());
        if (current == VectorStatus.PROCESSING
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(documentId)))) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "文档正在向量化中，请勿重复提交");
        }
        if (current == VectorStatus.SUCCESS && !reprocess) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文档已完成向量化，重新处理将覆盖当前切片与向量，请确认后重试");
        }
        String lockKey = lockKey(documentId);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "文档正在向量化中，请勿重复提交");
        }
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .eq(Document::getVectorStatus, current.getCode())
                .set(Document::getVectorStatus, VectorStatus.PROCESSING.getCode())
                .set(Document::getChunkCount, 0)
                .set(Document::getEmbeddingCount, 0)
                .set(Document::getVectorErrorMsg, ""));
        if (updated == 0) {
            stringRedisTemplate.delete(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "文档状态已变化，请刷新后重试");
        }
        try {
            documentVectorExecutor.vectorizeAsync(documentId, doc.getUserId(), doc.getKbId(), lockKey);
        } catch (TaskRejectedException e) {
            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, documentId)
                    .set(Document::getVectorStatus, current.getCode()));
            stringRedisTemplate.delete(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "向量化任务繁忙，请稍后重试");
        }
        return vectorStatus(documentId);
    }

    public VectorStatusVO vectorStatus(Long documentId) {
        Document doc = getOwnedDocument(documentId);
        VectorStatusVO vo = new VectorStatusVO();
        vo.setDocumentId(doc.getId());
        VectorStatus status = VectorStatus.fromCode(doc.getVectorStatus());
        boolean neverProcessed = status == VectorStatus.PENDING
                && (doc.getChunkCount() == null || doc.getChunkCount() == 0)
                && (doc.getVectorErrorMsg() == null || doc.getVectorErrorMsg().isEmpty());
        vo.setStatus(neverProcessed ? null : status.getExternalName());
        int chunkCount = doc.getChunkCount() == null ? 0 : doc.getChunkCount();
        int embeddingCount = doc.getEmbeddingCount() == null ? 0 : doc.getEmbeddingCount();
        vo.setChunkCount(chunkCount);
        vo.setEmbeddingCount(embeddingCount);
        vo.setProgress(chunkCount > 0 ? Math.min(100, embeddingCount * 100 / chunkCount) : 0);
        vo.setErrorMsg(doc.getVectorErrorMsg());
        vo.setUpdateTime(doc.getUpdateTime());
        return vo;
    }

    /** 余弦 TopK 检索：权限前置过滤 + 模型/版本/维度一致性 + 非法向量跳过 */
    public List<VectorSearchResultVO> search(Long kbId, String query, Integer topK) {
        Long userId = UserContext.getUserId();
        long targetKbId = kbId == null ? 0L : kbId;
        if (targetKbId != 0) {
            knowledgeBaseService.getOwnedKb(targetKbId);
        }
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "检索内容不能为空");
        }
        if (q.length() > 2000) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "检索内容过长（上限 2000 字符）");
        }
        int limit = topK == null ? 5 : Math.max(1, Math.min(topK, 20));

        float[] queryVec = embeddingClient.embedBatch(userId, List.of(q)).get(0);
        // SQL 层即过滤：仅本人 + 指定知识库 + 已有向量 + 模型/版本/维度三元组一致
        List<DocumentChunk> candidates = documentChunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getUserId, userId)
                        .eq(DocumentChunk::getKbId, targetKbId)
                        .isNotNull(DocumentChunk::getEmbedding)
                        .eq(DocumentChunk::getEmbeddingModel, embeddingClient.getModel())
                        .eq(DocumentChunk::getEmbeddingVersion, embeddingClient.getVersion())
                        .eq(DocumentChunk::getEmbeddingDimension, embeddingClient.getDimension()));
        List<VectorSearchResultVO> scored = new ArrayList<>();
        int skipped = 0;
        for (DocumentChunk chunk : candidates) {
            float[] vec;
            try {
                vec = objectMapper.readValue(chunk.getEmbedding(), float[].class);
            } catch (Exception e) {
                skipped++;
                continue;
            }
            double score;
            try {
                score = VectorMath.cosine(queryVec, vec);
            } catch (IllegalArgumentException e) {
                skipped++;
                continue;
            }
            VectorSearchResultVO vo = new VectorSearchResultVO();
            vo.setChunkId(chunk.getId());
            vo.setChunkUid(chunk.getChunkUid());
            vo.setDocumentId(chunk.getDocId());
            vo.setContent(chunk.getContent());
            vo.setPageNum(chunk.getPageNum());
            vo.setSourcePosition(chunk.getSourcePosition());
            vo.setScore(score);
            scored.add(vo);
        }
        if (skipped > 0) {
            log.warn("检索跳过非法/不可反序列化向量 {} 条 kbId={}", skipped, targetKbId);
        }
        scored.sort(Comparator.comparingDouble(VectorSearchResultVO::getScore).reversed());
        List<VectorSearchResultVO> top = scored.size() > limit ? scored.subList(0, limit) : scored;
        if (!top.isEmpty()) {
            List<Long> docIds = top.stream().map(VectorSearchResultVO::getDocumentId).distinct().toList();
            Map<Long, Document> docs = documentMapper.selectBatchIds(docIds).stream()
                    .collect(Collectors.toMap(Document::getId, Function.identity()));
            top.forEach(vo -> {
                Document d = docs.get(vo.getDocumentId());
                vo.setDocumentTitle(d == null ? "" : d.getTitle());
            });
        }
        return top;
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