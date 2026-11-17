<template>
  <div class="stat-card glass">
    <div class="stat-icon">
      <el-icon :size="20"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value" :class="{ pending: value === null }">
      {{ value === null ? '—' : value }}
    </div>
    <div v-if="value === null" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    label: string
    value: number | string | null
    icon: Component
    hint?: string
  }>(),
  { hint: '待接入真实统计' }
)
</script>

<style scoped>
.stat-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sp-grad);
  color: #fff;
  margin-bottom: 6px;
}

.stat-label {
  font-size: 13px;
  color: var(--sp-text-2);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.stat-value.pending {
  color: var(--sp-text-3);
}

.stat-hint {
  font-size: 12px;
  color: var(--sp-text-3);
}
</style>