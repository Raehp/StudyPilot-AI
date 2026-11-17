package com.studypilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studypilot.entity.Quiz;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuizMapper extends BaseMapper<Quiz> {
}