package com.studypilot.vo;

import lombok.Data;

import java.util.List;

@Data
public class DocumentContentVO {

    private Long documentId;

    private String title;

    private Integer pageCount;

    private Integer charCount;

    private Boolean needOcr;

    private List<PageText> pages;

    @Data
    public static class PageText {

        private Integer pageNum;

        private String text;
    }
}