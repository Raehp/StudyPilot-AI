<template>
  <div class="quiz">
    <PageHeader
      title="智能出题"
      subtitle="基于知识库真实内容，通义千问生成单选/多选/判断/简答题，在线练习与 AI 判分"
    >
      <template #actions>
        <el-button type="primary" :icon="MagicStick" @click="openGenerate">AI 出题</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="quiz-tabs" @tab-change="onTabChange">
      <el-tab-pane label="我的测验" name="list">
        <div v-loading="listLoading" class="quiz-list">
          <div v-for="q in quizzes" :key="q.id" class="quiz-card sp-card">
            <div class="quiz-card-head">
              <div class="quiz-card-title">{{ q.title }}</div>
              <el-tag size="small" effect="plain" round :type="q.status === 1 ? 'success' : 'info'">
                {{ q.status === 1 ? '已提交' : '未作答' }}
              </el-tag>
            </div>
            <div class="quiz-card-meta">
              <el-tag size="small" effect="plain" round>{{ q.kbName }}</el-tag>
              <el-tag size="small" effect="plain" round type="info">{{ difficultyLabel(q.difficulty) }}</el-tag>
              <el-tag size="small" effect="plain" round type="info">{{ q.questionCount }} 题</el-tag>
              <span class="gen-text" :class="q.generateStatus.toLowerCase()">{{ genLabel(q.generateStatus) }}</span>
            </div>
            <div class="quiz-card-foot">
              <span v-if="q.status === 1" class="score-badge">得分 {{ q.score }}</span>
              <span class="quiz-time">{{ q.createTime }}</span>
              <div class="quiz-card-actions">
                <el-button
                  v-if="q.generateStatus === 'SUCCESS' && q.status === 0"
                  size="small"
                  type="primary"
                  @click="openPractice(q.id)"
                >开始练习</el-button>
                <el-button
                  v-if="q.status === 1"
                  size="small"
                  @click="openResult(q.id)"
                >查看成绩</el-button>
                <el-button
                  v-if="q.generateStatus === 'FAILED'"
                  size="small"
                  type="warning"
                  plain
                  @click="openPractice(q.id)"
                >查看失败原因</el-button>
                <el-button size="small" type="danger" plain :icon="Delete" @click="removeQuiz(q)" />
              </div>
            </div>
          </div>
          <EmptyState
            v-if="!listLoading && quizzes.length === 0"
            :icon="EditPen"
            title="暂无测验"
            description="点击右上角「AI 出题」，选择知识库、难度、题型与数量，基于真实文档内容生成测验"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="错题回顾" name="wrong">
        <div v-loading="wrongLoading" class="wrong-list">
          <div v-for="w in wrongAnswers" :key="w.questionId" class="wrong-card sp-card">
            <div class="wrong-head">
              <el-tag size="small" effect="dark" round :type="typeTag(w.type)">{{ typeLabel(w.type) }}</el-tag>
              <span class="wrong-quiz">{{ w.quizTitle }}</span>
              <span class="wrong-score">本题 {{ w.score }} 分</span>
            </div>
            <div class="wrong-stem">{{ w.stem }}</div>
            <div v-if="w.options.length" class="wrong-options">
              <div v-for="(opt, i) in w.options" :key="i">{{ letter(i) }}. {{ opt }}</div>
            </div>
            <div class="wrong-answer-row"><b>你的答案：</b>{{ displayUserAnswer(w.type, w.userAnswer, w.options) || '未作答' }}</div>
            <div class="wrong-answer-row correct"><b>正确答案：</b>{{ displayAnswer(w.type, w.answer, w.options) }}</div>
            <div v-if="w.analysis" class="wrong-analysis"><b>解析：</b>{{ w.analysis }}</div>
            <div v-if="w.scoreBasis" class="wrong-basis"><b>评分依据：</b>{{ w.scoreBasis }}</div>
            <div v-if="w.sourceDocTitle" class="wrong-source">
              来源：《{{ w.sourceDocTitle }}》<span v-if="w.knowledgePoint"> · {{ w.knowledgePoint }}</span>
            </div>
          </div>
          <EmptyState
            v-if="!wrongLoading && wrongAnswers.length === 0"
            :icon="Trophy"
            title="暂无错题"
            description="完成测验后，答错的客观题与得分偏低的简答题会自动汇总到这里"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 出题配置 -->
    <el-dialog v-model="generateDialog" title="AI 智能出题" width="540px">
      <el-form :model="genForm" label-width="96px">
        <el-form-item label="知识库" required>
          <el-select v-model="genForm.kbId" placeholder="选择知识库" style="width: 100%">
            <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识主题">
          <el-input v-model="genForm.topic" maxlength="128" placeholder="留空则按知识库综合出题" />
        </el-form-item>
        <el-form-item label="测验名称">
          <el-input v-model="genForm.title" maxlength="64" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="题目数量">
          <el-input-number v-model="genForm.questionCount" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="genForm.difficulty">
            <el-radio-button :value="1">基础</el-radio-button>
            <el-radio-button :value="2">进阶</el-radio-button>
            <el-radio-button :value="3">挑战</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题型" required>
          <el-checkbox-group v-model="genForm.questionTypes">
            <el-checkbox :value="1">单选</el-checkbox>
            <el-checkbox :value="2">多选</el-checkbox>
            <el-checkbox :value="3">判断</el-checkbox>
            <el-checkbox :value="4">简答</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="submitGenerate">生成测验</el-button>
      </template>
    </el-dialog>

    <!-- 练习 / 结果 -->
    <el-dialog v-model="practiceDialog" :title="practiceTitle" width="820px" top="6vh" :close-on-click-modal="false">
      <!-- 生成中 -->
      <div v-if="mode === 'generating'" class="generating-card">
        <el-icon class="is-loading progress-icon"><Loading /></el-icon>
        <div>
          <div class="progress-title">AI 正在基于知识库出题…</div>
          <div class="progress-desc">通义千问生成题目并校验答案与来源，完成后自动进入练习</div>
        </div>
      </div>

      <!-- 生成失败 -->
      <el-alert v-else-if="mode === 'failed'" type="error" :closable="false" show-icon :title="failedMsg" />

      <!-- 练习答题 -->
      <div v-else-if="mode === 'practice' && detail" class="practice-body">
        <div class="practice-meta">
          <el-tag effect="plain" round size="small">{{ detail.kbName }}</el-tag>
          <el-tag effect="plain" round size="small" type="info">{{ difficultyLabel(detail.difficulty) }}</el-tag>
          <el-tag effect="plain" round size="small" type="info">{{ detail.questions.length }} 题</el-tag>
        </div>
        <div v-for="(q, idx) in detail.questions" :key="q.id" class="question-block">
          <div class="question-stem">
            <span class="question-no">{{ idx + 1 }}</span>
            <el-tag size="small" effect="dark" round :type="typeTag(q.type)">{{ typeLabel(q.type) }}</el-tag>
            <span class="stem-text">{{ q.stem }}</span>
          </div>
          <!-- 单选 / 判断 -->
          <el-radio-group
            v-if="q.type === 1 || q.type === 3"
            v-model="singleAnswers[q.id]"
            class="option-group"
          >
            <el-radio v-for="(opt, i) in q.options" :key="i" :value="letter(i)" class="option-radio">
              {{ letter(i) }}. {{ opt }}
            </el-radio>
          </el-radio-group>
          <!-- 多选 -->
          <el-checkbox-group v-else-if="q.type === 2" v-model="multiAnswers[q.id]" class="option-group">
            <el-checkbox v-for="(opt, i) in q.options" :key="i" :value="letter(i)" class="option-radio">
              {{ letter(i) }}. {{ opt }}
            </el-checkbox>
          </el-checkbox-group>
          <!-- 简答 -->
          <el-input
            v-else
            v-model="shortAnswers[q.id]"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="输入你的答案（简答题由 AI 辅助评分并给出依据）"
          />
          <div v-if="q.knowledgePoint" class="question-kp">知识点：{{ q.knowledgePoint }}</div>
        </div>
      </div>

      <!-- 结果 -->
      <div v-else-if="mode === 'result' && result" class="result-body">
        <div class="result-summary">
          <el-progress type="dashboard" :percentage="result.score" :width="120" :color="scoreColor" />
          <div class="result-summary-text">
            <div class="result-title">{{ result.title }}</div>
            <div class="result-stats">
              答对 {{ result.correctCount }} / {{ result.questionCount }} 题 · 综合得分 {{ result.score }}
            </div>
          </div>
        </div>
        <div v-for="(q, idx) in result.questions" :key="q.id" class="question-block result-question">
          <div class="question-stem">
            <span class="question-no">{{ idx + 1 }}</span>
            <el-tag size="small" effect="dark" round :type="typeTag(q.type)">{{ typeLabel(q.type) }}</el-tag>
            <el-tag size="small" effect="plain" round :type="q.isCorrect === 1 ? 'success' : 'danger'">
              {{ q.isCorrect === 1 ? '正确' : '错误' }} · {{ q.score }} 分
            </el-tag>
            <span class="stem-text">{{ q.stem }}</span>
          </div>
          <div v-if="q.options.length" class="result-options">
            <div
              v-for="(opt, i) in q.options"
              :key="i"
              class="result-option"
              :class="{
                'is-answer': isCorrectOption(q, letter(i)),
                'is-wrong-pick': isWrongPick(q, letter(i))
              }"
            >
              {{ letter(i) }}. {{ opt }}
              <el-icon v-if="isCorrectOption(q, letter(i))" class="opt-mark"><Check /></el-icon>
            </div>
          </div>
          <div class="result-answer-row"><b>你的答案：</b>{{ displayUserAnswer(q.type, q.userAnswer || '', q.options) || '未作答' }}</div>
          <div class="result-answer-row correct"><b>正确答案：</b>{{ displayAnswer(q.type, q.answer || '', q.options) }}</div>
          <div v-if="q.type === 4 && q.scoreBasis" class="result-basis"><b>AI 评分依据：</b>{{ q.scoreBasis }}</div>
          <div v-if="q.analysis" class="result-analysis"><b>解析：</b>{{ q.analysis }}</div>
          <div v-if="q.sourceDocTitle" class="result-source">
            来源：《{{ q.sourceDocTitle }}》
            <el-popover v-if="q.sourceContent" placement="top" width="360" trigger="click">
              <template #reference>
                <el-button link type="primary" size="small">查看原文片段</el-button>
              </template>
              <div class="source-pop">{{ q.sourceContent }}</div>
            </el-popover>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="practiceDialog = false">关闭</el-button>
        <el-button
          v-if="mode === 'practice'"
          type="primary"
          :loading="submitting"
          @click="submitPractice"
        >交卷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Delete, EditPen, Trophy, Loading, Check } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  generateQuiz, listQuizzes, getQuiz, getQuizGenerateStatus,
  submitQuiz, getQuizResult, listWrongAnswers, deleteQuiz
} from '@/api/quiz'
import { listKnowledgeBases } from '@/api/knowledge'
import type {
  GenerateStatus, KnowledgeBaseVO, QuizDetailVO, QuizListItemVO, QuizResultVO, WrongAnswerVO
} from '@/types/api'

const activeTab = ref('list')
const quizzes = ref<QuizListItemVO[]>([])
const listLoading = ref(false)
const wrongAnswers = ref<WrongAnswerVO[]>([])
const wrongLoading = ref(false)
const kbs = ref<KnowledgeBaseVO[]>([])

const generateDialog = ref(false)
const generating = ref(false)
const genForm = reactive({
  kbId: undefined as number | undefined,
  topic: '', title: '', questionCount: 5, difficulty: 1,
  questionTypes: [1, 3] as number[]
})

const practiceDialog = ref(false)
const mode = ref<'generating' | 'failed' | 'practice' | 'result'>('generating')
const detail = ref<QuizDetailVO | null>(null)
const result = ref<QuizResultVO | null>(null)
const failedMsg = ref('')
const submitting = ref(false)
const currentQuizId = ref<number | null>(null)
const singleAnswers = reactive<Record<number, string>>({})
const multiAnswers = reactive<Record<number, string[]>>({})
const shortAnswers = reactive<Record<number, string>>({})
let pollTimer: number | null = null

const practiceTitle = computed(() => {
  if (mode.value === 'result') return '测验成绩'
  if (mode.value === 'practice') return detail.value?.title || '在线练习'
  return 'AI 出题'
})

const scoreColor = (percentage: number) => {
  if (percentage >= 80) return '#34c759'
  if (percentage >= 60) return '#ff9f0a'
  return '#ff3b30'
}

onMounted(async () => {
  await Promise.all([loadQuizzes(), loadKbs()])
})

onBeforeUnmount(() => stopPolling())

async function loadQuizzes() {
  listLoading.value = true
  try {
    quizzes.value = await listQuizzes()
  } catch {
    // 拦截器统一提示
  } finally {
    listLoading.value = false
  }
}

async function loadKbs() {
  try {
    kbs.value = await listKnowledgeBases()
  } catch {
    // 拦截器统一提示
  }
}

async function loadWrong() {
  wrongLoading.value = true
  try {
    wrongAnswers.value = await listWrongAnswers()
  } catch {
    // 拦截器统一提示
  } finally {
    wrongLoading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'wrong') loadWrong()
}

function openGenerate() {
  generateDialog.value = true
}

async function submitGenerate() {
  if (!genForm.kbId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (genForm.questionTypes.length === 0) {
    ElMessage.warning('请至少选择一种题型')
    return
  }
  generating.value = true
  try {
    const status = await generateQuiz({
      kbId: genForm.kbId,
      topic: genForm.topic,
      title: genForm.title,
      questionCount: genForm.questionCount,
      difficulty: genForm.difficulty,
      questionTypes: genForm.questionTypes
    })
    generateDialog.value = false
    ElMessage.success('已加入 AI 出题队列')
    await loadQuizzes()
    currentQuizId.value = status.id
    resetAnswers()
    mode.value = 'generating'
    detail.value = null
    result.value = null
    practiceDialog.value = true
    schedulePolling()
  } catch {
    // 拦截器统一提示
  } finally {
    generating.value = false
  }
}

async function openPractice(quizId: number) {
  currentQuizId.value = quizId
  resetAnswers()
  result.value = null
  practiceDialog.value = true
  try {
    const d = await getQuiz(quizId)
    detail.value = d
    if (d.generateStatus === 'SUCCESS') {
      mode.value = 'practice'
      for (const q of d.questions) {
        if (q.type === 2) multiAnswers[q.id] = []
        else if (q.type === 4) shortAnswers[q.id] = ''
        else singleAnswers[q.id] = ''
      }
    } else if (d.generateStatus === 'FAILED') {
      mode.value = 'failed'
      failedMsg.value = '出题失败：' + (d.generateError || '请稍后重试')
    } else {
      mode.value = 'generating'
      schedulePolling()
    }
  } catch {
    practiceDialog.value = false
  }
}

async function openResult(quizId: number) {
  currentQuizId.value = quizId
  practiceDialog.value = true
  mode.value = 'result'
  try {
    result.value = await getQuizResult(quizId)
  } catch {
    practiceDialog.value = false
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
  const quizId = currentQuizId.value
  if (quizId === null) {
    stopPolling()
    return
  }
  try {
    const status = await getQuizGenerateStatus(quizId)
    if (currentQuizId.value !== quizId) return
    if (status.status === 'SUCCESS') {
      stopPolling()
      await loadQuizzes()
      await openPractice(quizId)
      ElMessage.success('测验生成完成，开始练习')
    } else if (status.status === 'FAILED') {
      stopPolling()
      mode.value = 'failed'
      failedMsg.value = '出题失败：' + (status.errorMsg || '请稍后重试')
      await loadQuizzes()
    }
  } catch {
    // 单次轮询失败忽略
  }
}

async function submitPractice() {
  const quizId = currentQuizId.value
  if (quizId === null || !detail.value) return
  const answers = detail.value.questions.map((q) => {
    let answer = ''
    if (q.type === 2) answer = [...(multiAnswers[q.id] || [])].sort().join(',')
    else if (q.type === 4) answer = shortAnswers[q.id] || ''
    else answer = singleAnswers[q.id] || ''
    return { questionId: q.id, answer }
  })
  const unanswered = answers.filter((a) => !a.answer).length
  if (unanswered > 0) {
    try {
      await ElMessageBox.confirm(`还有 ${unanswered} 题未作答，确定交卷？`, '交卷', {
        type: 'warning', confirmButtonText: '确定交卷', cancelButtonText: '继续答题'
      })
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    result.value = await submitQuiz(quizId, { answers })
    mode.value = 'result'
    ElMessage.success('交卷成功，已完成判分')
    await loadQuizzes()
  } catch {
    // 拦截器统一提示
  } finally {
    submitting.value = false
  }
}

async function removeQuiz(q: QuizListItemVO) {
  try {
    await ElMessageBox.confirm(`确定删除测验「${q.title}」及其题目与作答记录？`, '删除测验', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteQuiz(q.id)
    ElMessage.success('测验已删除')
    await loadQuizzes()
  } catch {
    // 拦截器统一提示
  }
}

function resetAnswers() {
  Object.keys(singleAnswers).forEach((k) => delete singleAnswers[Number(k)])
  Object.keys(multiAnswers).forEach((k) => delete multiAnswers[Number(k)])
  Object.keys(shortAnswers).forEach((k) => delete shortAnswers[Number(k)])
}

function letter(i: number) {
  return String.fromCharCode(65 + i)
}

function isCorrectOption(q: { type: number; answer: string | null }, optLetter: string) {
  if (q.type === 4 || !q.answer) return false
  return q.answer.toUpperCase().split(',').map((s) => s.trim()).includes(optLetter)
}

function isWrongPick(q: { type: number; answer: string | null; userAnswer: string | null }, optLetter: string) {
  if (q.type === 4 || !q.userAnswer) return false
  const picked = q.userAnswer.toUpperCase().split(',').map((s) => s.trim()).includes(optLetter)
  return picked && !isCorrectOption(q, optLetter)
}

function displayAnswer(type: number, answer: string, options: string[]) {
  if (type === 4) return answer
  if (!answer) return ''
  return answer.toUpperCase().split(',').map((s) => {
    const idx = s.trim().charCodeAt(0) - 65
    return options[idx] ? `${s.trim()}. ${options[idx]}` : s.trim()
  }).join('；')
}

function displayUserAnswer(type: number, answer: string, options: string[]) {
  return displayAnswer(type, answer, options)
}

function typeLabel(type: number) {
  return ['', '单选', '多选', '判断', '简答'][type] ?? '题目'
}

function typeTag(type: number) {
  return (['', 'primary', 'warning', 'success', 'info'] as const)[type] ?? 'info'
}

function difficultyLabel(d: number) {
  return ['', '基础', '进阶', '挑战'][d] ?? '基础'
}

function genLabel(status: GenerateStatus) {
  const map: Record<GenerateStatus, string> = {
    NONE: '未生成', GENERATING: '生成中', SUCCESS: '已生成', FAILED: '生成失败'
  }
  return map[status]
}
</script>

<style scoped>
.quiz-tabs { margin-top: 4px; }

.quiz-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.quiz-card { padding: 16px 18px; display: flex; flex-direction: column; gap: 10px; }
.quiz-card-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.quiz-card-title { font-size: 15px; font-weight: 600; color: var(--sp-text); }
.quiz-card-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.gen-text { font-size: 12px; color: var(--sp-text-2); }
.gen-text.success { color: #34c759; }
.gen-text.failed { color: #ff3b30; }
.gen-text.generating { color: #ff9f0a; }
.quiz-card-foot { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.score-badge { font-size: 14px; font-weight: 700; color: var(--sp-primary); }
.quiz-time { font-size: 12px; color: var(--sp-text-3); }
.quiz-card-actions { display: flex; gap: 6px; margin-left: auto; }

.wrong-list { display: flex; flex-direction: column; gap: 12px; }
.wrong-card { padding: 16px 18px; display: flex; flex-direction: column; gap: 8px; }
.wrong-head { display: flex; align-items: center; gap: 10px; }
.wrong-quiz { font-size: 13px; font-weight: 600; color: var(--sp-text-2); }
.wrong-score { margin-left: auto; font-size: 13px; font-weight: 700; color: #ff3b30; }
.wrong-stem { font-size: 14px; font-weight: 600; line-height: 1.7; color: var(--sp-text); }
.wrong-options { font-size: 13px; line-height: 1.8; color: var(--sp-text-2); }
.wrong-answer-row { font-size: 13px; line-height: 1.7; color: var(--sp-text); }
.wrong-answer-row.correct { color: #34c759; }
.wrong-analysis, .wrong-basis { font-size: 13px; line-height: 1.7; color: var(--sp-text-2); }
.wrong-source { font-size: 12px; color: var(--sp-text-3); }

.generating-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 24px;
  border-radius: var(--sp-radius-sm);
  background: rgba(10, 132, 255, 0.07);
  border: 1px solid rgba(10, 132, 255, 0.18);
}

.progress-icon { font-size: 28px; color: var(--sp-primary); }
.progress-title { font-size: 15px; font-weight: 600; }
.progress-desc { font-size: 12px; color: var(--sp-text-2); margin-top: 4px; }

.practice-body, .result-body { display: flex; flex-direction: column; gap: 18px; max-height: 64vh; overflow-y: auto; }
.practice-meta { display: flex; gap: 6px; flex-wrap: wrap; }

.question-block {
  padding: 14px 16px;
  border-radius: var(--sp-radius-sm);
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-stem { display: flex; align-items: flex-start; gap: 8px; flex-wrap: wrap; }
.question-no {
  width: 22px; height: 22px;
  border-radius: 50%;
  background: var(--sp-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.stem-text { font-size: 14px; font-weight: 600; line-height: 1.7; color: var(--sp-text); flex: 1; min-width: 200px; }
.option-group { display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.option-radio { margin-right: 0; height: auto; white-space: normal; }
.question-kp { font-size: 12px; color: var(--sp-text-3); }

.result-summary { display: flex; align-items: center; gap: 20px; }
.result-title { font-size: 17px; font-weight: 700; }
.result-stats { font-size: 13px; color: var(--sp-text-2); margin-top: 6px; }
.result-options { display: flex; flex-direction: column; gap: 6px; }
.result-option {
  font-size: 13px;
  line-height: 1.7;
  padding: 6px 10px;
  border-radius: 8px;
  color: var(--sp-text);
  display: flex; align-items: center; gap: 6px;
}
.result-option.is-answer { background: rgba(52, 199, 89, 0.12); color: #1f8f3d; font-weight: 600; }
.result-option.is-wrong-pick { background: rgba(255, 59, 48, 0.1); color: #d70015; }
.opt-mark { color: #34c759; }
.result-answer-row { font-size: 13px; line-height: 1.7; color: var(--sp-text); }
.result-answer-row.correct { color: #34c759; }
.result-basis { font-size: 13px; line-height: 1.7; color: var(--sp-text-2); background: rgba(10,132,255,0.05); padding: 8px 10px; border-radius: 8px; }
.result-analysis { font-size: 13px; line-height: 1.7; color: var(--sp-text-2); }
.result-source { font-size: 12px; color: var(--sp-text-3); display: flex; align-items: center; gap: 6px; }
.source-pop { font-size: 13px; line-height: 1.8; white-space: pre-wrap; max-height: 300px; overflow-y: auto; }

@media (max-width: 900px) {
  .quiz-list { grid-template-columns: 1fr; }
}
</style>