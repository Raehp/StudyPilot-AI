package com.studypilot.service;

import com.studypilot.config.ChunkProperties;
import com.studypilot.entity.DocumentChunk;
import com.studypilot.vo.DocumentContentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 切片服务单元测试（非 AI 调用）：清洗/边界/重叠/页码/稳定UID */
class ChunkServiceTest {

    private ChunkService chunkService;

    @BeforeEach
    void setup() {
        ChunkProperties props = new ChunkProperties();
        props.setTargetChars(200);
        props.setOverlapChars(40);
        props.setMinChunkChars(20);
        props.setMaxChunksPerDoc(100);
        chunkService = new ChunkService(props);
    }

    private DocumentContentVO content(String... pages) {
        DocumentContentVO vo = new DocumentContentVO();
        vo.setPages(new java.util.ArrayList<>());
        for (int i = 0; i < pages.length; i++) {
            DocumentContentVO.PageText p = new DocumentContentVO.PageText();
            p.setPageNum(i + 1);
            p.setText(pages[i]);
            vo.getPages().add(p);
        }
        return vo;
    }

    @Test
    void cleansMeaninglessWhitespace() {
        String cleaned = chunkService.clean("  hello   world  \r\n\r\n\r\n\r\n  next\tpart  ");
        assertEquals("hello world\n\nnext part", cleaned);
    }

    @Test
    void shortTextSingleChunkWithPageAndPosition() {
        List<DocumentChunk> chunks = chunkService.chunk(1L, 1L, 1L, content("Short paragraph text here."));
        assertEquals(1, chunks.size());
        DocumentChunk c = chunks.get(0);
        assertEquals(1, c.getPageNum());
        assertTrue(c.getSourcePosition().startsWith("p1:0-"));
        assertEquals(64, c.getChunkUid().length());
        assertEquals("Short paragraph text here.", c.getContent());
    }

    @Test
    void longTextSplitsWithOverlapAndStableUid() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("Line number ").append(i).append(" with some words.\n");
        }
        DocumentContentVO vo = content(sb.toString());
        List<DocumentChunk> a = chunkService.chunk(7L, 1L, 2L, vo);
        List<DocumentChunk> b = chunkService.chunk(7L, 1L, 2L, vo);
        assertTrue(a.size() > 1, "应产生多个切片");
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).getChunkUid(), b.get(i).getChunkUid(), "UID 必须稳定");
            assertTrue(a.get(i).getContent().length() <= 260, "切片不应显著超过目标长度");
        }
        // 重叠：后一切片开头应包含前一切片尾部内容
        String prev = a.get(0).getContent();
        String next = a.get(1).getContent();
        String prevTail = prev.substring(prev.length() - 30);
        assertTrue(next.contains(prevTail.substring(prevTail.indexOf('\n') + 1)), "应存在重叠");
    }

    @Test
    void differentContentDifferentUid() {
        List<DocumentChunk> a = chunkService.chunk(1L, 1L, 0L, content("alpha beta gamma delta epsilon zeta eta theta"));
        List<DocumentChunk> b = chunkService.chunk(1L, 1L, 0L, content("alpha beta gamma delta epsilon zeta eta iota"));
        assertNotEquals(a.get(0).getChunkUid(), b.get(0).getChunkUid());
    }

    @Test
    void headingStartsNewChunk() {
        String text = "Some introduction sentence one.\nSome introduction sentence two.\n"
                + "2. Method\nWe describe the method in detail here with enough words to pass minimum.";
        List<DocumentChunk> chunks = chunkService.chunk(1L, 1L, 0L, content(text));
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(chunks.size() - 1).getContent().startsWith("2. Method"));
    }

    @Test
    void pageNumbersTrackedAcrossPages() {
        StringBuilder p1 = new StringBuilder();
        StringBuilder p2 = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            p1.append("Page one line ").append(i).append(" some text.\n");
            p2.append("Page two line ").append(i).append(" some text.\n");
        }
        List<DocumentChunk> chunks = chunkService.chunk(1L, 1L, 0L, content(p1.toString(), p2.toString()));
        assertTrue(chunks.stream().anyMatch(c -> c.getPageNum() == 1));
        assertTrue(chunks.stream().anyMatch(c -> c.getPageNum() == 2));
    }

    @Test
    void emptyContentProducesNoChunks() {
        assertTrue(chunkService.chunk(1L, 1L, 0L, content("   ", "\n\n")).isEmpty());
    }

    @Test
    void tailBelowMinimumMergesIntoPrevious() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append("Line number ").append(i).append(" with some words.\n");
        }
        sb.append("tiny tail");
        List<DocumentChunk> chunks = chunkService.chunk(1L, 1L, 0L, content(sb.toString()));
        DocumentChunk last = chunks.get(chunks.size() - 1);
        assertTrue(last.getContent().endsWith("tiny tail"));
        assertFalse(chunks.size() > 1 && chunks.get(chunks.size() - 1).getContent().equals("tiny tail"));
    }
}