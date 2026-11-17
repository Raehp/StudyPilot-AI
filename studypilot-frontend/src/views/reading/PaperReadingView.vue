<template>
  <div class="reading">
    <PageHeader
      title="论文阅读"
      subtitle="上传 PDF 论文，AI 自动生成摘要与结构化分析（研究问题 / 方法 / 创新点 / 结论）"
      milestone="M2-M3"
    />
    <div class="reading-grid">
      <div class="doc-list glass">
        <div class="panel-head">
          <span>我的文档</span>
          <el-button type="primary" size="small" disabled>上传 PDF</el-button>
        </div>
        <EmptyState
          :icon="Document"
          title="暂无文档"
          description="文档上传与 PDFBox 解析将在 M2 开放"
          compact
        />
      </div>
      <div class="reader glass">
        <EmptyState
          :icon="Reading"
          title="论文阅读器待启用"
          description="上传论文后，此处展示原文阅读视图、AI 摘要卡片与结构化分析面板。当前为页面结构预览，不包含任何模拟论文数据。"
        >
          <el-tag effect="plain" round type="info">M2 上传解析 · M3 AI 分析</el-tag>
        </EmptyState>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Document, Reading } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
</script>

<style scoped>
.reading-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 14px;
  min-height: 480px;
}

.doc-list {
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.reader {
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 900px) {
  .reading-grid {
    grid-template-columns: 1fr;
  }
}
</style>