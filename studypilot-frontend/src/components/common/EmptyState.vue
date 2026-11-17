<template>
  <div class="empty-state" :class="{ compact }">
    <div class="empty-icon">
      <el-icon :size="compact ? 26 : 34"><component :is="icon" /></el-icon>
    </div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="description" class="empty-desc">{{ description }}</div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { FolderOpened } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    icon?: Component
    title: string
    description?: string
    compact?: boolean
  }>(),
  { icon: FolderOpened, compact: false }
)
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 56px 24px;
  gap: 10px;
}

.empty-state.compact {
  padding: 28px 16px;
  gap: 6px;
}

.empty-icon {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sp-grad);
  color: #fff;
  opacity: 0.9;
  box-shadow: 0 8px 24px rgba(10, 132, 255, 0.35);
  margin-bottom: 6px;
}

.compact .empty-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--sp-text);
}

.compact .empty-title {
  font-size: 14px;
}

.empty-desc {
  font-size: 13px;
  color: var(--sp-text-2);
  max-width: 380px;
  line-height: 1.7;
}
</style>