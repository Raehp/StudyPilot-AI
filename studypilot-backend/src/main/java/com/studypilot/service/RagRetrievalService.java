package com.studypilot.service;

import com.studypilot.config.RagProperties;
import com.studypilot.vo.VectorSearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 检索模块：复用 M4 余弦 TopK 检索（用户/知识库权限 + 模型/版本/维度一致性过滤），
 * 叠加最低相似度阈值；阈值过滤后为空即"无相关内容"，由编排层明确提示，绝不伪造来源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final DocumentVectorService documentVectorService;
    private final RagProperties ragProperties;

    public List<VectorSearchResultVO> retrieve(Long kbId, String question, Integer topK, Double minScore) {
        int k = topK == null ? ragProperties.getTopK() : Math.max(1, Math.min(topK, 20));
        double threshold = minScore == null ? ragProperties.getMinScore()
                : Math.max(0.0, Math.min(minScore, 1.0));
        List<VectorSearchResultVO> hits = documentVectorService.search(kbId, question, k);
        List<VectorSearchResultVO> filtered = hits.stream()
                .filter(h -> h.getScore() != null && h.getScore() >= threshold)
                .toList();
        log.info("RAG 检索 kbId={} topK={} minScore={} 命中={} 阈值过滤后={}",
                kbId, k, threshold, hits.size(), filtered.size());
        return filtered;
    }
}