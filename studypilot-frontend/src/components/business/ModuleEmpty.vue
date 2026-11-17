<template>
  <div class="module-empty glass">
    <EmptyState :icon="icon" :title="title" :description="description">
      <div class="module-action">
        <el-button type="primary" size="large" disabled>
          {{ actionLabel }}
        </el-button>
        <el-tag effect="plain" round type="info">{{ milestone }} 开放</el-tag>
      </div>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'

defineProps<{
  icon: Component
  title: string
  description: string
  actionLabel: string
  milestone: string
}>()
</script>

<style scoped>
.module-empty {
  min-height: 380px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.module-action {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>