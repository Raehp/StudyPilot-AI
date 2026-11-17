<template>
  <div class="plan">
    <PageHeader
      title="学习计划"
      subtitle="设定学习目标与周期，通义千问生成逐日任务清单，支持打卡与进度追踪"
      milestone="M6"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建计划</el-button>
      </template>
    </PageHeader>

    <div class="plan-grid">
      <!-- 左：计划列表 -->
      <div class="plan-list sp-card">
        <div class="panel-head">
          <span>我的计划</span>
          <el-tag effect="plain" round size="small" type="info">{{ plans.length }} 个</el-tag>
        </div>
        <div v-loading="planLoading" class="plan-items">
          <div
            v-for="p in plans"
            :key="p.id"
            class="plan-item"
            :class="{ active: selectedId === p.id }"
            @click="selectPlan(p)"
          >
            <div class="plan-title">{{ p.title }}</div>
            <div class="plan-meta">
              <el-tag size="small" effect="plain" round :type="planStatusType(p.status)">
                {{ planStatusLabel(p.status) }}
              </el-tag>
              <span class="gen-dot" :class="p.generateStatus.toLowerCase()" />
              <span>{{ genLabel(p.generateStatus) }}</span>
            </div>
            <el-progress
              v-if="p.taskCount > 0"
              :percentage="p.progress"
              :stroke-width="5"
              :show-text="false"
              class="plan-progress"
            />
          </div>
          <EmptyState
            v-if="!planLoading && plans.length === 0"
            :icon="Calendar"
            title="暂无学习计划"
            description="点击右上角「新建计划」，设定目标后由 AI 拆解为逐日任务"
            compact
          />
        </div>
      </div>

      <!-- 右：计划详情 -->
      <div class="plan-detail sp-card">
        <EmptyState
          v-if="!selectedPlan"
          :icon="Calendar"
          title="选择或创建一个学习计划"
          description="设定学习主题、目标截止时间、每日学习时长与当前水平，AI 将生成学习阶段、每日任务、学习重点、推荐内容与阶段性测验。可关联知识库，结合真实文档内容生成计划。"
        />
        <template v-else>
          <div class="detail-head">
            <div class="detail-title">
              <h3>{{ selectedPlan.title }}</h3>
              <div class="detail-meta">
                <el-tag effect="plain" round size="small" :type="planStatusType(selectedPlan.status)">
                  {{ planStatusLabel(selectedPlan.status) }}
                </el-tag>
                <el-tag v-if="selectedPlan.topic" effect="plain" round size="small">{{ selectedPlan.topic }}</el-tag>
                <el-tag v-if="selectedPlan.kbName" effect="plain" round size="small" type="success">
                  知识库：{{ selectedPlan.kbName }}
                </el-tag>
                <el-tag effect="plain" round size="small" type="info">每日 {{ selectedPlan.dailyMinutes }} 分钟</el-tag>
                <el-tag v-if="selectedPlan.currentLevel" effect="plain" round size="small" type="info">
                  {{ selectedPlan.currentLevel }}
                </el-tag>
              </div>
            </div>
            <div class="detail-actions">
              <el-button
                type="primary"
                :icon="MagicStick"
                :loading="generating"
                :disabled="selectedPlan.generateStatus === 'GENERATING'"
                @click="doGenerate"
              >
                {{ selectedPlan.generateStatus === 'SUCCESS' ? '重新生成' : 'AI 生成计划' }}
              </el-button>
              <el-button :icon="Edit" @click="openEdit(selectedPlan)">编辑</el-button>
              <el-button :icon="Delete" type="danger" plain @click="removePlan(selectedPlan)">删除</el-button>
            </div>
          </div>

          <div class="goal-card">
            <div class="goal-row"><span class="goal-label">学习目标</span>{{ selectedPlan.goal || '—' }}</div>
            <div class="goal-row"><span class="goal-label">预期成果</span>{{ selectedPlan.expectedOutcome || '—' }}</div>
            <div class="goal-row">
              <span class="goal-label">周期</span>
              {{ selectedPlan.startDate || '未指定' }} ~ {{ selectedPlan.endDate || '未指定' }}
            </div>
          </div>

          <!-- 进度 -->
          <div v-if="selectedPlan.taskCount > 0" class="progress-card">
            <div class="progress-info">
              <span class="progress-title">完成进度</span>
              <span class="progress-count">{{ selectedPlan.completedCount }} / {{ selectedPlan.taskCount }} 项任务</span>
            </div>
            <el-progress :percentage="selectedPlan.progress" :stroke-width="10" />
          </div>

          <!-- 生成中 -->
          <div v-if="selectedPlan.generateStatus === 'GENERATING'" class="generating-card">
            <el-icon class="is-loading progress-icon"><Loading /></el-icon>
            <div>
              <div class="progress-title">AI 正在生成学习计划…</div>
              <div class="progress-desc">通义千问拆解逐日任务，页面会自动刷新结果</div>
            </div>
          </div>

          <!-- 生成失败 -->
          <el-alert
            v-else-if="selectedPlan.generateStatus === 'FAILED'"
            type="error"
            :closable="false"
            show-icon
            class="fail-alert"
          >
            <template #title>生成失败：{{ selectedPlan.generateError || '请稍后重试' }}</template>
            <el-button size="small" type="primary" plain :loading="generating" @click="doGenerate">重试生成</el-button>
          </el-alert>

          <!-- AI 建议 -->
          <div v-if="selectedPlan.aiAdvice" class="advice-card">
            <div class="card-label"><el-icon><Notebook /></el-icon> AI 总体建议</div>
            <p>{{ selectedPlan.aiAdvice }}</p>
          </div>

          <!-- 任务列表 -->
          <div v-loading="tasksLoading" class="task-section">
            <template v-if="tasks.length > 0">
              <div v-for="group in taskGroups" :key="group.date" class="task-group">
                <div class="group-date">
                  <el-icon><Calendar /></el-icon> {{ group.date }}
                  <el-tag size="small" effect="plain" round type="info">{{ group.items.length }} 项</el-tag>
                </div>
                <div
                  v-for="task in group.items"
                  :key="task.id"
                  class="task-item"
                  :class="{ done: task.status === 1 }"
                >
                  <el-checkbox
                    :model-value="task.status === 1"
                    :loading="togglingId === task.id"
                    @change="(val: string | number | boolean) => toggleTask(task, Boolean(val))"
                  />
                  <div class="task-body">
                    <div class="task-title-row">
                      <span class="task-title">{{ task.title }}</span>
                      <el-tag v-if="task.stage" size="small" effect="plain" round>{{ task.stage }}</el-tag>
                      <el-tag v-if="task.stageQuiz" size="small" effect="dark" round type="warning">阶段测验</el-tag>
                      <span class="task-duration">{{ task.durationMin }} 分钟</span>
                    </div>
                    <div v-if="task.content" class="task-content">{{ task.content }}</div>
                    <div v-if="task.focus" class="task-focus"><b>重点：</b>{{ task.focus }}</div>
                    <div v-if="task.recommendedContent" class="task-recommend">
                      <b>推荐：</b>{{ task.recommendedContent }}
                    </div>
                  </div>
                  <el-button :icon="Edit" link type="primary" @click="openEditTask(task)">编辑</el-button>
                </div>
              </div>
            </template>
            <EmptyState
              v-else-if="!tasksLoading && selectedPlan.generateStatus !== 'GENERATING'"
              :icon="MagicStick"
              title="尚未生成学习任务"
              description="点击「AI 生成计划」，通义千问将按周期拆解逐日任务并持久化保存"
              compact
            />
          </div>
        </template>
      </div>
    </div>

    <!-- 新建/编辑计划 -->
    <el-dialog v-model="planDialog" :title="editingId ? '编辑学习计划' : '新建学习计划'" width="560px">
      <el-form :model="planForm" label-width="96px" label-position="right">
        <el-form-item label="计划名称" required>
          <el-input v-model="planForm.title" maxlength="64" placeholder="如：两周读完 5 篇 Transformer 论文" />
        </el-form-item>
        <el-form-item label="学习主题">
          <el-input v-model="planForm.topic" maxlength="128" placeholder="如：Transformer 注意力机制" />
        </el-form-item>
        <el-form-item label="学习目标">
          <el-input v-model="planForm.goal" type="textarea" :rows="2" maxlength="500" placeholder="描述你的学习目标" />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select v-model="planForm.kbId" placeholder="不结合文档" clearable style="width: 100%">
            <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="planForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="planForm.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每日时长">
          <el-input-number v-model="planForm.dailyMinutes" :min="5" :max="1440" :step="15" />
          <span class="form-unit">分钟</span>
        </el-form-item>
        <el-form-item label="当前水平">
          <el-select v-model="planForm.currentLevel" placeholder="选择知识水平" clearable style="width: 100%">
            <el-option label="入门" value="入门" />
            <el-option label="进阶" value="进阶" />
            <el-option label="熟练" value="熟练" />
          </el-select>
        </el-form-item>
        <el-form-item label="预期成果">
          <el-input v-model="planForm.expectedOutcome" type="textarea" :rows="2" maxlength="500" placeholder="如：能复现注意力机制并讲解原理" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingPlan" @click="submitPlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑任务 -->
    <el-dialog v-model="taskDialog" title="编辑学习任务" width="520px">
      <el-form :model="taskForm" label-width="96px">
        <el-form-item label="任务名称">
          <el-input v-model="taskForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="学习阶段">
          <el-input v-model="taskForm.stage" maxlength="64" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="taskForm.taskDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="任务详情">
          <el-input v-model="taskForm.content" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item label="学习重点">
          <el-input v-model="taskForm.focus" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="推荐内容">
          <el-input v-model="taskForm.recommendedContent" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item label="预计时长">
          <el-input-number v-model="taskForm.durationMin" :min="0" :max="1440" :step="15" />
          <span class="form-unit">分钟</span>
        </el-form-item>
        <el-form-item label="阶段测验">
          <el-switch v-model="taskForm.stageQuiz" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingTask" @click="submitTask">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, MagicStick, Plus, Edit, Delete, Loading, Notebook } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  createPlan, listPlans, getPlan, updatePlan, deletePlan,
  generatePlan, getPlanGenerateStatus, listPlanTasks, updateTaskStatus, updateTask
} from '@/api/plan'
import { listKnowledgeBases } from '@/api/knowledge'
import type { GenerateStatus, KnowledgeBaseVO, StudyPlanVO, StudyTaskVO } from '@/types/api'

const plans = ref<StudyPlanVO[]>([])
const planLoading = ref(false)
const selectedId = ref<number | null>(null)
const selectedPlan = computed(() => plans.value.find((p) => p.id === selectedId.value) ?? null)
const tasks = ref<StudyTaskVO[]>([])
const tasksLoading = ref(false)
const kbs = ref<KnowledgeBaseVO[]>([])
const generating = ref(false)
const togglingId = ref<number | null>(null)
let pollTimer: number | null = null

const planDialog = ref(false)
const editingId = ref<number | null>(null)
const savingPlan = ref(false)
const planForm = reactive({
  title: '', topic: '', goal: '', kbId: undefined as number | undefined,
  startDate: null as string | null, endDate: null as string | null,
  dailyMinutes: 60, currentLevel: '', expectedOutcome: ''
})

const taskDialog = ref(false)
const savingTask = ref(false)
const editingTaskId = ref<number | null>(null)
const taskForm = reactive({
  title: '', stage: '', taskDate: null as string | null, content: '',
  focus: '', recommendedContent: '', durationMin: 0, stageQuiz: false
})

const taskGroups = computed(() => {
  const map = new Map<string, StudyTaskVO[]>()
  for (const t of tasks.value) {
    const key = t.taskDate || '未排期'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(t)
  }
  return Array.from(map.entries()).map(([date, items]) => ({ date, items }))
})

onMounted(async () => {
  await Promise.all([loadPlans(), loadKbs()])
  if (plans.value.length > 0) selectPlan(plans.value[0])
})

onBeforeUnmount(() => stopPolling())

async function loadPlans() {
  planLoading.value = true
  try {
    plans.value = await listPlans()
  } catch {
    // 拦截器统一提示
  } finally {
    planLoading.value = false
  }
}

async function loadKbs() {
  try {
    kbs.value = await listKnowledgeBases()
  } catch {
    // 拦截器统一提示
  }
}

function selectPlan(p: StudyPlanVO) {
  if (selectedId.value === p.id) return
  selectedId.value = p.id
  tasks.value = []
  stopPolling()
  loadTasks(p.id)
  if (p.generateStatus === 'GENERATING') schedulePolling()
}

async function loadTasks(planId: number) {
  tasksLoading.value = true
  try {
    const result = await listPlanTasks(planId)
    if (selectedId.value === planId) tasks.value = result
  } catch {
    // 拦截器统一提示
  } finally {
    tasksLoading.value = false
  }
}

async function refreshPlan(planId: number) {
  try {
    const fresh = await getPlan(planId)
    const idx = plans.value.findIndex((p) => p.id === planId)
    if (idx >= 0) plans.value[idx] = fresh
    return fresh
  } catch {
    return null
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(planForm, {
    title: '', topic: '', goal: '', kbId: undefined, startDate: null, endDate: null,
    dailyMinutes: 60, currentLevel: '', expectedOutcome: ''
  })
  planDialog.value = true
}

function openEdit(p: StudyPlanVO) {
  editingId.value = p.id
  Object.assign(planForm, {
    title: p.title, topic: p.topic, goal: p.goal, kbId: p.kbId || undefined,
    startDate: p.startDate, endDate: p.endDate, dailyMinutes: p.dailyMinutes,
    currentLevel: p.currentLevel, expectedOutcome: p.expectedOutcome
  })
  planDialog.value = true
}

async function submitPlan() {
  if (!planForm.title.trim()) {
    ElMessage.warning('请填写计划名称')
    return
  }
  savingPlan.value = true
  try {
    const payload = { ...planForm, kbId: planForm.kbId ?? 0 }
    if (editingId.value) {
      await updatePlan(editingId.value, payload)
      ElMessage.success('计划已更新')
    } else {
      const created = await createPlan(payload)
      ElMessage.success('计划已创建')
      await loadPlans()
      selectPlan(created)
    }
    planDialog.value = false
    if (editingId.value) await refreshPlan(editingId.value)
  } catch {
    // 拦截器统一提示
  } finally {
    savingPlan.value = false
  }
}

async function removePlan(p: StudyPlanVO) {
  try {
    await ElMessageBox.confirm(`确定删除计划「${p.title}」及其全部任务？`, '删除计划', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deletePlan(p.id)
    ElMessage.success('计划已删除')
    if (selectedId.value === p.id) {
      selectedId.value = null
      tasks.value = []
      stopPolling()
    }
    await loadPlans()
  } catch {
    // 拦截器统一提示
  }
}

async function doGenerate() {
  const p = selectedPlan.value
  if (!p) return
  if (p.generateStatus === 'SUCCESS') {
    try {
      await ElMessageBox.confirm('重新生成将覆盖当前任务清单与打卡进度。确定继续？', '重新生成', {
        type: 'warning', confirmButtonText: '覆盖并生成', cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  generating.value = true
  try {
    await generatePlan(p.id)
    ElMessage.success('已加入 AI 生成队列')
    await refreshPlan(p.id)
    schedulePolling()
  } catch {
    // 拦截器统一提示
  } finally {
    generating.value = false
  }
}

function schedulePolling() {
  if (pollTimer === null) pollTimer = window.setInterval(pollGenerate, 3000)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollGenerate() {
  const planId = selectedId.value
  if (planId === null) {
    stopPolling()
    return
  }
  try {
    const status = await getPlanGenerateStatus(planId)
    if (selectedId.value !== planId) return
    if (status.status === 'SUCCESS') {
      stopPolling()
      await refreshPlan(planId)
      await loadTasks(planId)
      ElMessage.success('学习计划生成完成')
    } else if (status.status === 'FAILED') {
      stopPolling()
      await refreshPlan(planId)
    }
  } catch {
    // 单次轮询失败忽略
  }
}

async function toggleTask(task: StudyTaskVO, val: boolean) {
  togglingId.value = task.id
  try {
    await updateTaskStatus(task.id, val ? 1 : 0)
    task.status = val ? 1 : 0
    if (selectedId.value) await refreshPlan(selectedId.value)
  } catch {
    // 拦截器统一提示
  } finally {
    togglingId.value = null
  }
}

function openEditTask(task: StudyTaskVO) {
  editingTaskId.value = task.id
  Object.assign(taskForm, {
    title: task.title, stage: task.stage, taskDate: task.taskDate, content: task.content,
    focus: task.focus, recommendedContent: task.recommendedContent,
    durationMin: task.durationMin, stageQuiz: task.stageQuiz
  })
  taskDialog.value = true
}

async function submitTask() {
  if (editingTaskId.value === null) return
  savingTask.value = true
  try {
    await updateTask(editingTaskId.value, { ...taskForm, stageQuiz: taskForm.stageQuiz ? 1 : 0 })
    ElMessage.success('任务已更新')
    taskDialog.value = false
    if (selectedId.value) await loadTasks(selectedId.value)
  } catch {
    // 拦截器统一提示
  } finally {
    savingTask.value = false
  }
}

function planStatusLabel(status: number) {
  return ['进行中', '已完成', '已放弃'][status] ?? '进行中'
}

function planStatusType(status: number) {
  return (['primary', 'success', 'info'] as const)[status] ?? 'primary'
}

function genLabel(status: GenerateStatus) {
  const map: Record<GenerateStatus, string> = {
    NONE: '未生成', GENERATING: '生成中', SUCCESS: '已生成', FAILED: '生成失败'
  }
  return map[status]
}
</script>

<style scoped>
.plan-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  align-items: start;
}

.plan-list {
  padding: 16px;
  max-height: calc(100vh - 180px);
  display: flex;
  flex-direction: column;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  padding: 0 4px;
}

.plan-items {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.plan-item {
  padding: 10px 12px;
  border-radius: var(--sp-radius-sm);
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.plan-item:hover { background: rgba(10, 132, 255, 0.06); }
.plan-item.active { background: rgba(10, 132, 255, 0.1); border-color: rgba(10, 132, 255, 0.3); }

.plan-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--sp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--sp-text-2);
}

.gen-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--sp-text-3); }
.gen-dot.success { background: #34c759; }
.gen-dot.failed { background: #ff3b30; }
.gen-dot.generating { background: #ff9f0a; }

.plan-progress { margin-top: 8px; }

.plan-detail { padding: 20px; min-height: 480px; }

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.detail-title h3 { font-size: 20px; font-weight: 700; letter-spacing: -0.2px; }
.detail-meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.detail-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

.goal-card {
  padding: 14px 16px;
  border-radius: var(--sp-radius-sm);
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.goal-row { font-size: 13px; line-height: 1.7; color: var(--sp-text); }
.goal-label {
  display: inline-block;
  min-width: 64px;
  font-weight: 600;
  color: var(--sp-text-2);
  margin-right: 8px;
}

.progress-card {
  padding: 14px 16px;
  border-radius: var(--sp-radius-sm);
  background: rgba(10, 132, 255, 0.05);
  border: 1px solid rgba(10, 132, 255, 0.18);
  margin-bottom: 14px;
}

.progress-info { display: flex; justify-content: space-between; margin-bottom: 8px; }
.progress-title { font-size: 14px; font-weight: 600; }
.progress-count { font-size: 12px; color: var(--sp-text-2); }

.generating-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-radius: var(--sp-radius-sm);
  background: rgba(10, 132, 255, 0.07);
  border: 1px solid rgba(10, 132, 255, 0.18);
  margin-bottom: 14px;
}

.progress-icon { font-size: 26px; color: var(--sp-primary); }
.progress-desc { font-size: 12px; color: var(--sp-text-2); margin-top: 4px; }

.fail-alert { margin-bottom: 14px; border-radius: var(--sp-radius-sm); }
.fail-alert .el-button { margin-top: 8px; }

.advice-card {
  padding: 16px 18px;
  border-radius: var(--sp-radius-sm);
  background: var(--sp-grad);
  color: #fff;
  box-shadow: 0 8px 24px rgba(10, 132, 255, 0.3);
  margin-bottom: 16px;
}

.advice-card .card-label { color: rgba(255, 255, 255, 0.9); }
.advice-card p { margin-top: 8px; font-size: 14px; line-height: 1.8; white-space: pre-wrap; }

.card-label { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; }

.task-section { display: flex; flex-direction: column; gap: 16px; }
.task-group { display: flex; flex-direction: column; gap: 8px; }

.group-date {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sp-primary);
}

.task-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--sp-radius-sm);
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
}

.task-item.done { opacity: 0.65; }
.task-item.done .task-title { text-decoration: line-through; }

.task-body { flex: 1; min-width: 0; }
.task-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.task-title { font-size: 14px; font-weight: 600; color: var(--sp-text); }
.task-duration { font-size: 12px; color: var(--sp-text-3); margin-left: auto; }
.task-content { font-size: 13px; line-height: 1.7; color: var(--sp-text); margin-top: 6px; }
.task-focus, .task-recommend { font-size: 12px; line-height: 1.7; color: var(--sp-text-2); margin-top: 4px; }

.form-unit { margin-left: 8px; font-size: 13px; color: var(--sp-text-2); }

@media (max-width: 900px) {
  .plan-grid { grid-template-columns: 1fr; }
}
</style>