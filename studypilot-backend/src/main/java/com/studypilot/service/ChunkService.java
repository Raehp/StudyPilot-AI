package com.studypilot.service;

import com.studypilot.common.ResultCode;
import com.studypilot.config.ChunkProperties;
import com.studypilot.entity.DocumentChunk;
import com.studypilot.exception.BusinessException;
import com.studypilot.vo.DocumentContentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本切片服务：清洗空白 → 保留段落/章节边界 → 按配置长度切片（带重叠）
 * → 保留页码与原文位置 → 生成稳定 Chunk UID。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkService {

    private static final Pattern HEADING = Pattern.compile(
            "^(\\d+(\\.\\d+)*[.、．]?\\s+\\S.*|第[一二三四五六七八九十百\\d]+[章节篇].*|"
                    + "(Abstract|Introduction|Background|Method(s|ology)?|Experiments?|Results?|"
                    + "Discussion|Conclusion(s)?|References|Related Work|摘要|目录|引言|结论|参考文献)\\b.*)$",
            Pattern.CASE_INSENSITIVE);

    private final ChunkProperties props;

    public List<DocumentChunk> chunk(Long docId, Long userId, Long kbId, DocumentContentVO content) {
        StringBuilder full = new StringBuilder();
        List<int[]> pageRanges = new ArrayList<>();
        if (content.getPages() != null) {
            for (DocumentContentVO.PageText page : content.getPages()) {
                String cleaned = clean(page.getText());
                if (cleaned.isEmpty()) {
                    continue;
                }
                int start = full.length();
                full.append(cleaned).append('\n');
                pageRanges.add(new int[]{start, full.length(), page.getPageNum() == null ? 0 : page.getPageNum()});
            }
        }
        String text = full.toString();
        if (text.isBlank()) {
            return List.of();
        }

        List<Line> lines = splitLines(text, pageRanges);
        List<DocumentChunk> chunks = new ArrayList<>();
        List<Line> buffer = new ArrayList<>();
        int bufferLen = 0;
        // 仅当缓冲区含新内容时才产出切片，避免重叠种子被重复输出
        boolean hasNewContent = false;
        int seedSize = 0;

        for (Line line : lines) {
            if (line.text.isEmpty()) {
                if (hasNewContent && bufferLen >= props.getMinChunkChars()) {
                    buffer = finalizeChunk(chunks, buffer, docId, userId, kbId, true);
                    bufferLen = overlapLength(buffer);
                    seedSize = buffer.size();
                    hasNewContent = false;
                }
                continue;
            }
            if (line.text.length() > props.getTargetChars()) {
                if (hasNewContent && bufferLen >= props.getMinChunkChars()) {
                    buffer = finalizeChunk(chunks, buffer, docId, userId, kbId, true);
                    bufferLen = overlapLength(buffer);
                    seedSize = buffer.size();
                    hasNewContent = false;
                }
                for (Line piece : hardSplit(line)) {
                    buffer.add(piece);
                    bufferLen += piece.text.length() + 1;
                    hasNewContent = true;
                    if (bufferLen >= props.getTargetChars()) {
                        buffer = finalizeChunk(chunks, buffer, docId, userId, kbId, true);
                        bufferLen = overlapLength(buffer);
                        seedSize = buffer.size();
                        hasNewContent = false;
                    }
                }
                continue;
            }
            boolean heading = HEADING.matcher(line.text).matches() && line.text.length() <= 80;
            if (hasNewContent && bufferLen >= props.getMinChunkChars()
                    && (heading || bufferLen + line.text.length() + 1 > props.getTargetChars())) {
                // 章节标题是硬边界：不跨标题重叠
                buffer = finalizeChunk(chunks, buffer, docId, userId, kbId, !heading);
                bufferLen = overlapLength(buffer);
                seedSize = buffer.size();
                hasNewContent = false;
            }
            buffer.add(line);
            bufferLen += line.text.length() + 1;
            hasNewContent = true;
        }
        if (hasNewContent && !buffer.isEmpty()) {
            if (bufferLen < props.getMinChunkChars() && !chunks.isEmpty()) {
                DocumentChunk last = chunks.get(chunks.size() - 1);
                List<Line> newLines = buffer.subList(seedSize, buffer.size());
                String merged = last.getContent() + "\n" + joinLines(newLines);
                last.setContent(merged);
                last.setCharCount(merged.length());
                String pos = last.getSourcePosition();
                last.setSourcePosition(pos.substring(0, pos.indexOf('-')) + "-" + endOffset(buffer));
                last.setChunkUid(chunkUid(docId, last.getChunkIndex(), merged));
            } else {
                finalizeChunk(chunks, buffer, docId, userId, kbId, true);
            }
        }
        if (chunks.size() > props.getMaxChunksPerDoc()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文档切片数量 " + chunks.size() + " 超出上限 " + props.getMaxChunksPerDoc() + "，请拆分文档");
        }
        log.info("切片完成 docId={} chunks={} targetChars={} overlapChars={}",
                docId, chunks.size(), props.getTargetChars(), props.getOverlapChars());
        return chunks;
    }

    /** 清洗无意义空白：统一换行、压缩空格/制表符、去除控制字符、保留段落空行 */
    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("[\\x00-\\x08\\x0E-\\x1F]", "");
        s = s.replaceAll("[\\t\\x0B\\f]+", " ");
        s = s.replaceAll(" {2,}", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        String[] parts = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(part.strip()).append('\n');
        }
        return sb.toString().strip();
    }

    private List<Line> splitLines(String text, List<int[]> pageRanges) {
        List<Line> lines = new ArrayList<>();
        int offset = 0;
        int rangeIdx = 0;
        for (String raw : text.split("\n", -1)) {
            int pageNum = 0;
            while (rangeIdx < pageRanges.size() && offset >= pageRanges.get(rangeIdx)[1]) {
                rangeIdx++;
            }
            if (rangeIdx < pageRanges.size() && offset >= pageRanges.get(rangeIdx)[0]) {
                pageNum = pageRanges.get(rangeIdx)[2];
            }
            lines.add(new Line(raw.strip(), offset, pageNum));
            offset += raw.length() + 1;
        }
        return lines;
    }

    private List<Line> hardSplit(Line line) {
        List<Line> pieces = new ArrayList<>();
        int target = props.getTargetChars();
        for (int i = 0; i < line.text.length(); i += target) {
            int end = Math.min(i + target, line.text.length());
            pieces.add(new Line(line.text.substring(i, end), line.offset + i, line.pageNum));
        }
        return pieces;
    }

    /** 产出当前切片；withOverlap=true 时按行边界返回重叠尾部作为下一切片种子 */
    private List<Line> finalizeChunk(List<DocumentChunk> chunks, List<Line> buffer,
                                     Long docId, Long userId, Long kbId, boolean withOverlap) {
        String content = joinLines(buffer);
        DocumentChunk chunk = new DocumentChunk();
        chunk.setUserId(userId);
        chunk.setDocId(docId);
        chunk.setKbId(kbId == null ? 0L : kbId);
        chunk.setChunkIndex(chunks.size());
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setPageNum(buffer.get(0).pageNum);
        chunk.setSourcePosition(position(buffer.get(0).pageNum, buffer.get(0).offset, endOffset(buffer)));
        chunk.setChunkUid(chunkUid(docId, chunks.size(), content));
        chunk.setEmbeddingModel("");
        chunk.setEmbeddingDimension(0);
        chunk.setEmbeddingVersion("");
        chunks.add(chunk);

        List<Line> overlap = new ArrayList<>();
        if (!withOverlap) {
            return overlap;
        }
        int len = 0;
        for (int i = buffer.size() - 1; i >= 0; i--) {
            Line l = buffer.get(i);
            if (l.text.isEmpty() || len + l.text.length() + 1 > props.getOverlapChars()) {
                break;
            }
            overlap.add(0, l);
            len += l.text.length() + 1;
        }
        return overlap;
    }

    private int overlapLength(List<Line> overlap) {
        int len = 0;
        for (Line l : overlap) {
            len += l.text.length() + 1;
        }
        return len;
    }

    private int endOffset(List<Line> buffer) {
        Line last = buffer.get(buffer.size() - 1);
        return last.offset + last.text.length();
    }

    private String position(int pageNum, int start, int end) {
        return "p" + pageNum + ":" + start + "-" + end;
    }

    private String joinLines(List<Line> lines) {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(l.text);
        }
        return sb.toString();
    }

    private String chunkUid(Long docId, int index, String content) {
        return sha256(docId + ":" + index + ":" + sha256(content));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private record Line(String text, int offset, int pageNum) {
    }
}