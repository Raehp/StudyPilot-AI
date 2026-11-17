package com.studypilot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuizSubmitDTO {

    @NotEmpty(message = "答案不能为空")
    @Valid
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {

        @NotNull(message = "题目ID不能为空")
        private Long questionId;

        @Size(max = 500, message = "答案不能超过500个字符")
        private String answer;
    }
}