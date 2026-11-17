<template>
  <div class="quiz">
    <PageHeader
      title="智能出题"
      subtitle="基于论文内容 AI 生成单选 / 多选 / 判断 / 简答题，自动判分并给出原文解析"
      milestone="M6"
    >
      <template #actions>
        <el-button type="primary" disabled :icon="EditPen">开始出题</el-button>
      </template>
    </PageHeader>
    <ModuleEmpty
      :icon="EditPen"
      title="暂无测验"
      description="选择文档或知识库、难度与题数，AI 将围绕原文内容出题并标注出题依据切片；提交后客观题自动判分、简答题 AI 评判。功能将在 M6 开放。"
      action-label="生成第一份测验"
      milestone="M6"
    />
  </div>
</template>

<script setup lang="ts">
import { EditPen } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ModuleEmpty from '@/components/business/ModuleEmpty.vue'
</script>