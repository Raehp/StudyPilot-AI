<template>
  <div class="sidenav">
    <div class="logo">
      <div class="logo-icon">
        <el-icon :size="20"><Compass /></el-icon>
      </div>
      <span class="logo-text">StudyPilot AI</span>
    </div>

    <nav class="nav">
      <router-link
        v-for="item in items"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ active: route.path === item.path }"
        @click="emit('navigate')"
      >
        <el-icon :size="17"><component :is="item.icon" /></el-icon>
        <span class="nav-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="sidenav-footer">
      <span class="dot"></span>
      后端服务已连接 · v0.2
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import {
  Odometer,
  Reading,
  Collection,
  ChatDotRound,
  Calendar,
  EditPen,
  Setting
} from '@element-plus/icons-vue'

const route = useRoute()
const emit = defineEmits<{ navigate: [] }>()

const items: { path: string; label: string; icon: Component }[] = [
  { path: '/', label: '工作台', icon: Odometer },
  { path: '/reading', label: '论文阅读', icon: Reading },
  { path: '/knowledge', label: '科研知识库', icon: Collection },
  { path: '/chat', label: 'AI 智能问答', icon: ChatDotRound },
  { path: '/plan', label: '学习计划', icon: Calendar },
  { path: '/quiz', label: '智能出题', icon: EditPen },
  { path: '/settings', label: '个人设置', icon: Setting }
]
</script>

<style scoped>
.sidenav {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 18px 14px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 18px;
}

.logo-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--sp-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(10, 132, 255, 0.4);
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.2px;
}

.nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  color: var(--sp-text-2);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s ease, color 0.2s ease;
}

.nav-item:hover {
  background: var(--sp-surface);
  color: var(--sp-text);
}

.nav-item.active {
  background: var(--sp-grad);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(10, 132, 255, 0.35);
}

.nav-label {
  flex: 1;
}

.sidenav-footer {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 12px 8px 0;
  font-size: 11px;
  color: var(--sp-text-2);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #30d158;
  box-shadow: 0 0 8px rgba(48, 209, 88, 0.8);
}
</style>