<template>
  <div class="knowledge">
    <PageHeader
      title="科研知识库"
      subtitle="按课题组织文献，文档自动切片并向量化，支撑 RAG 检索问答"
      milestone="M2-M4"
    >
      <template #actions>
        <el-input
          placeholder="搜索知识库（M2 开放）"
          disabled
          style="width: 220px"
          :prefix-icon="Search"
        />
        <el-button type="primary" disabled>创建知识库</el-button>
      </template>
    </PageHeader>
    <ModuleEmpty
      :icon="Collection"
      title="还没有知识库"
      description="创建知识库后即可上传论文、管理文献切片与向量索引。知识库 CRUD 与文档管理将在 M2 开放，切片与 Embedding 检索在 M4 开放。"
      action-label="创建第一个知识库"
      milestone="M2"
    />
  </div>
</template>

<script setup lang="ts">
import { Collection, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ModuleEmpty from '@/components/business/ModuleEmpty.vue'
</script>