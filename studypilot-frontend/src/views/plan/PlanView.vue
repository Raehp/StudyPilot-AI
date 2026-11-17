<template>
  <div class="plan">
    <PageHeader
      title="学习计划"
      subtitle="输入学习目标与周期，AI 生成逐日任务清单，支持打卡与进度追踪"
      milestone="M6"
    >
      <template #actions>
        <el-button type="primary" disabled :icon="MagicStick">AI 生成计划</el-button>
      </template>
    </PageHeader>
    <ModuleEmpty
      :icon="Calendar"
      title="暂无学习计划"
      description="设定目标（如「两周读完 5 篇 Transformer 论文」）、周期与每日可投入时长，AI 将拆解为可执行的逐日任务。计划生成与打卡功能将在 M6 开放。"
      action-label="创建学习计划"
      milestone="M6"
    />
  </div>
</template>

<script setup lang="ts">
import { Calendar, MagicStick } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ModuleEmpty from '@/components/business/ModuleEmpty.vue'
</script>