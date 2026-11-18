<template>
  <div class="dashboard">
    <div class="welcome glass" data-lens-blur-scale="0.05">
      <div class="card-art art-line" aria-hidden="true"></div>
      <div class="welcome-text">
        <h2>你好，{{ userStore.userInfo?.nickname }} 👋</h2>
        <p>
          欢迎使用 StudyPilot AI。下方统计与图表均来自你的真实学习数据（文档、知识库、问答、计划与测验），
          无记录时显示 0 或诚实空状态，不使用任何模拟数据。
        </p>
      </div>
      <div class="welcome-badge">
        <span class="dot"></span>
        系统运行正常
      </div>
    </div>

    <div class="stat-grid">
      <StatCard label="知识库" :value="overview?.knowledgeBaseTotal ?? null" :icon="Collection" :hint="statHint" />
      <StatCard label="论文文档" :value="overview?.documentTotal ?? null" :icon="Document" :hint="statHint" />
      <StatCard label="已解析文档" :value="overview?.parsedDocumentTotal ?? null" :icon="Files" :hint="statHint" />
      <StatCard label="已向量化" :value="overview?.vectorizedDocumentTotal ?? null" :icon="DataLine" :hint="statHint" />
      <StatCard label="问答会话" :value="overview?.conversationTotal ?? null" :icon="ChatDotRound" :hint="statHint" />
      <StatCard label="学习计划" :value="overview?.studyPlanTotal ?? null" :icon="Calendar" :hint="statHint" />
      <StatCard label="已完成任务" :value="overview?.completedTaskTotal ?? null" :icon="Checked" :hint="statHint" />
      <StatCard label="练习次数" :value="overview?.quizPracticeTotal ?? null" :icon="EditPen" :hint="statHint" />
    </div>

    <div class="progress-panel glass">
      <div class="progress-head">
        <span class="progress-title">学习进度</span>
        <span class="progress-sub">
          已完成任务 {{ overview?.completedTaskTotal ?? 0 }} / {{ overview?.totalTaskCount ?? 0 }}
          · 计划平均进度 {{ overview?.learningProgress ?? 0 }}%
        </span>
      </div>
      <el-progress
        :percentage="overview?.learningProgress ?? 0"
        :stroke-width="12"
        :color="progressColors"
        striped
        striped-flow
        :duration="6"
      />
    </div>

    <ChartCard
      title="近 7 日学习活跃趋势"
      :empty="!hasActivity"
      :empty-text="chartEmptyText"
      :option="activityOption"
    />

    <div class="chart-grid">
      <ChartCard
        title="文档解析状态分布"
        :empty="!hasDocs"
        :empty-text="chartEmptyText"
        :option="parseOption"
      />
      <ChartCard
        title="测验成绩分布"
        :empty="!hasQuizScores"
        :empty-text="chartEmptyText"
        :option="scoreOption"
      />
    </div>

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
import { computed, onMounted, ref } from 'vue'
import type { Component } from 'vue'
import type { EChartsOption } from 'echarts'
import {
  Collection,
  Document,
  Files,
  DataLine,
  ChatDotRound,
  Calendar,
  Checked,
  EditPen,
  Reading,
  Setting,
  ArrowRight
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import StatCard from '@/components/common/StatCard.vue'
import ChartCard from '@/components/common/ChartCard.vue'
import {
  getOverview,
  getDocumentStatistics,
  getLearningStatistics,
  getQuizStatistics
} from '@/api/dashboard'
import type {
  DashboardOverviewVO,
  DocumentStatisticsVO,
  LearningStatisticsVO,
  QuizStatisticsVO
} from '@/types/api'

const userStore = useUserStore()

const loading = ref(true)
const loadFailed = ref(false)
const overview = ref<DashboardOverviewVO | null>(null)
const docStats = ref<DocumentStatisticsVO | null>(null)
const learnStats = ref<LearningStatisticsVO | null>(null)
const quizStats = ref<QuizStatisticsVO | null>(null)

const statHint = computed(() => (loading.value ? '加载中…' : loadFailed.value ? '统计加载失败' : '暂无数据'))
const chartEmptyText = computed(() =>
  loading.value ? '正在加载真实统计…' : loadFailed.value ? '统计加载失败，请稍后重试' : '暂无真实数据 · 不展示任何模拟数据'
)

const progressColors = [
  { color: '#ff9f0a', percentage: 40 },
  { color: '#0a84ff', percentage: 80 },
  { color: '#30d158', percentage: 100 }
]

const hasActivity = computed(() => {
  const a = learnStats.value?.activityTrend
  if (!a) return false
  const sum = [...a.documents, ...a.tasks, ...a.quizzes, ...a.conversations].reduce((s, v) => s + v, 0)
  return sum > 0
})
const hasDocs = computed(() => (docStats.value?.total ?? 0) > 0)
const hasQuizScores = computed(() => (quizStats.value?.practicedTotal ?? 0) > 0)

const axisLabel = { color: '#8e8e93' }
const splitLine = { lineStyle: { color: 'rgba(142,142,147,0.15)' } }

const activityOption = computed<EChartsOption | null>(() => {
  const a = learnStats.value?.activityTrend
  if (!a) return null
  return {
    color: ['#0a84ff', '#30d158', '#ff9f0a', '#5e5ce6'],
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['文档上传', '任务完成', '测验练习', '问答会话'],
      textStyle: { color: '#8e8e93' },
      bottom: 0
    },
    grid: { left: 40, right: 20, top: 20, bottom: 44 },
    xAxis: { type: 'category', boundaryGap: false, data: a.dates, axisLabel, axisLine: { lineStyle: { color: '#d1d1d6' } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel, splitLine },
    series: [
      { name: '文档上传', type: 'line', smooth: true, data: a.documents },
      { name: '任务完成', type: 'line', smooth: true, data: a.tasks },
      { name: '测验练习', type: 'line', smooth: true, data: a.quizzes },
      { name: '问答会话', type: 'line', smooth: true, data: a.conversations }
    ]
  }
})

const parseOption = computed<EChartsOption | null>(() => {
  const d = docStats.value?.parseStatusDistribution
  if (!d) return null
  return {
    color: ['#0a84ff'],
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: d.map((i) => i.label), axisLabel: { ...axisLabel, interval: 0, rotate: 24 }, axisLine: { lineStyle: { color: '#d1d1d6' } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel, splitLine },
    series: [{ type: 'bar', data: d.map((i) => i.value), barWidth: '46%', itemStyle: { borderRadius: [6, 6, 0, 0] } }]
  }
})

const scoreOption = computed<EChartsOption | null>(() => {
  const d = quizStats.value?.scoreDistribution
  if (!d) return null
  return {
    color: ['#5e5ce6'],
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 34 },
    xAxis: { type: 'category', data: d.map((i) => i.label), axisLabel, axisLine: { lineStyle: { color: '#d1d1d6' } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel, splitLine },
    series: [{ type: 'bar', data: d.map((i) => i.value), barWidth: '46%', itemStyle: { borderRadius: [6, 6, 0, 0] } }]
  }
})

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const [o, d, l, q] = await Promise.all([
      getOverview(),
      getDocumentStatistics(),
      getLearningStatistics(),
      getQuizStatistics()
    ])
    overview.value = o
    docStats.value = d
    learnStats.value = l
    quizStats.value = q
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

const modules: { path: string; name: string; desc: string; icon: Component; stage: string }[] = [
  { path: '/reading', name: '论文阅读', desc: 'PDF 解析 · AI 摘要与结构化分析', icon: Reading, stage: '已开放' },
  { path: '/knowledge', name: '科研知识库', desc: '个人文献库 · 切片与向量检索', icon: Collection, stage: '已开放' },
  { path: '/chat', name: 'AI 智能问答', desc: 'RAG 多轮对话 · 原文引用溯源', icon: ChatDotRound, stage: '已开放' },
  { path: '/plan', name: '学习计划', desc: 'AI 生成计划 · 每日任务打卡', icon: Calendar, stage: '已开放' },
  { path: '/quiz', name: '智能出题', desc: 'AI 出题 · 自动判分与解析', icon: EditPen, stage: '已开放' },
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

.progress-panel {
  padding: 20px 22px;
}

.progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.progress-title {
  font-size: 15px;
  font-weight: 600;
}

.progress-sub {
  font-size: 12px;
  color: var(--sp-text-2);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
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
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .stat-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>