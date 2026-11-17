package com.studypilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studypilot.entity.QuizQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuizQuestionMapper extends BaseMapper<QuizQuestion> {
}