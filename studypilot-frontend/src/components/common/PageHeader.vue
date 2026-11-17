<template>
  <div class="page-header">
    <div class="header-text">
      <h2>{{ title }}</h2>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div class="header-actions">
      <el-tag v-if="milestone" effect="light" round type="primary">{{ milestone }}</el-tag>
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  milestone?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.header-text h2 {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.header-text p {
  margin-top: 6px;
  font-size: 13px;
  color: var(--sp-text-2);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>