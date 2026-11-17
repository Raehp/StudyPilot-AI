package com.studypilot.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 论文结构化分析结果（12 字段，缺失项统一为「未明确说明」） */
@Data
public class PaperAnalysisResult {

    public static final String NOT_STATED = "未明确说明";

    private String title;

    @JsonProperty("abstract")
    private String abstractText;

    private String background;

    private String researchQuestion;

    private String methods;

    private String technicalRoute;

    private String innovations;

    private String experimentDesign;

    private String experimentResults;

    private String conclusion;

    private String limitations;

    private String futureWork;

    public void normalize() {
        title = blankToNotStated(title);
        abstractText = blankToNotStated(abstractText);
        background = blankToNotStated(background);
        researchQuestion = blankToNotStated(researchQuestion);
        methods = blankToNotStated(methods);
        technicalRoute = blankToNotStated(technicalRoute);
        innovations = blankToNotStated(innovations);
        experimentDesign = blankToNotStated(experimentDesign);
        experimentResults = blankToNotStated(experimentResults);
        conclusion = blankToNotStated(conclusion);
        limitations = blankToNotStated(limitations);
        futureWork = blankToNotStated(futureWork);
    }

    private String blankToNotStated(String value) {
        return value == null || value.isBlank() ? NOT_STATED : value.trim();
    }
}