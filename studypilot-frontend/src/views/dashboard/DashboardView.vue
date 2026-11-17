<template>
  <div class="dashboard">
    <div class="welcome glass">
      <div class="welcome-text">
        <h2>你好，{{ userStore.userInfo?.nickname }} 👋</h2>
        <p>
          欢迎使用 StudyPilot AI。账号体系已就绪，各业务模块将按里程碑逐步开放；
          下方统计当前为明确空状态，接入真实数据后自动展示。
        </p>
      </div>
      <div class="welcome-badge">
        <span class="dot"></span>
        系统运行正常
      </div>
    </div>

    <div class="stat-grid">
      <StatCard label="知识库" :value="null" :icon="Collection" hint="M2 接入真实统计" />
      <StatCard label="论文文档" :value="null" :icon="Document" hint="M2 接入真实统计" />
      <StatCard label="问答次数" :value="null" :icon="ChatDotRound" hint="M5 接入真实统计" />
      <StatCard label="学习计划" :value="null" :icon="Calendar" hint="M6 接入真实统计" />
    </div>

    <ChartCard
      title="近 7 日学习活跃趋势"
      empty
      empty-text="暂无学习行为数据 · 统计接口将在 M7 接入，当前不展示任何模拟数据"
    />

    <div class="modules glass">
      <div class="modules-head">功能模块</div>
      <div class="module-grid">
        <router-link
          v-for="m in modules"
          :key="m.path"
          :to="m.path"
          class="module-item"
        >
          <div class="module-icon">
            <el-icon :size="18"><component :is="m.icon" /></el-icon>
          </div>
          <div class="module-meta">
            <span class="module-name">{{ m.name }}</span>
            <span class="module-desc">{{ m.desc }}</span>
          </div>
          <el-tag size="small" effect="plain" round type="info">{{ m.stage }}</el-tag>
          <el-icon class="module-arrow" :size="14"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import {
  Collection,
  Document,
  ChatDotRound,
  Calendar,
  Reading,
  EditPen,
  Setting
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import StatCard from '@/components/common/StatCard.vue'
import ChartCard from '@/components/common/ChartCard.vue'

const userStore = useUserStore()

const modules: { path: string; name: string; desc: string; icon: Component; stage: string }[] = [
  { path: '/reading', name: '论文阅读', desc: 'PDF 解析 · AI 摘要与结构化分析', icon: Reading, stage: 'M2-M3' },
  { path: '/knowledge', name: '科研知识库', desc: '个人文献库 · 切片与向量检索', icon: Collection, stage: 'M2-M4' },
  { path: '/chat', name: 'AI 智能问答', desc: 'RAG 多轮对话 · 原文引用溯源', icon: ChatDotRound, stage: 'M5' },
  { path: '/plan', name: '学习计划', desc: 'AI 生成计划 · 每日任务打卡', icon: Calendar, stage: 'M6' },
  { path: '/quiz', name: '智能出题', desc: 'AI 出题 · 自动判分与解析', icon: EditPen, stage: 'M6' },
  { path: '/settings', name: '个人设置', desc: '账号资料 · 外观主题', icon: Setting, stage: '已开放' }
]
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.welcome {
  padding: 24px 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.welcome-text h2 {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.3px;
  margin-bottom: 8px;
}

.welcome-text p {
  font-size: 13px;
  color: var(--sp-text-2);
  line-height: 1.7;
  max-width: 620px;
}

.welcome-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  font-size: 12px;
  font-weight: 600;
  color: var(--sp-text-2);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #30d158;
  box-shadow: 0 0 8px rgba(48, 209, 88, 0.8);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.modules {
  padding: 20px;
}

.modules-head {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.module-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--sp-border);
  background: var(--sp-surface);
  text-decoration: none;
  color: var(--sp-text);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.module-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--sp-shadow);
}

.module-icon {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 12px;
  background: var(--sp-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.module-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.module-name {
  font-size: 14px;
  font-weight: 600;
}

.module-desc {
  font-size: 12px;
  color: var(--sp-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.module-arrow {
  color: var(--sp-text-3);
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 700px) {
  .stat-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>