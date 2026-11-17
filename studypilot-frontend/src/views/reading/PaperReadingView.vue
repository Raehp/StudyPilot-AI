<template>
  <div class="reading">
    <PageHeader
      title="论文阅读"
      subtitle="选择已解析成功的论文，AI 自动生成摘要与 12 项结构化分析（通义千问）"
    />
    <div class="reading-grid">
      <!-- 左：论文列表 -->
      <div class="doc-list sp-card">
        <div class="panel-head">
          <span>我的论文</span>
          <el-tag effect="plain" round size="small" type="info">{{ docs.length }} 篇</el-tag>
        </div>
        <div v-loading="docLoading" class="doc-items">
          <div
            v-for="doc in docs"
            :key="doc.id"
            class="doc-item"
            :class="{ active: selectedId === doc.id }"
            @click="selectDoc(doc)"
          >
            <div class="doc-title">{{ doc.title }}</div>
            <div class="doc-meta">
              <span class="status-dot" :class="doc.status.toLowerCase()" />
              <span>{{ parseLabel(doc.status) }}</span>
              <span v-if="doc.status === 'SUCCESS'">{{ doc.pageCount }} 页</span>
              <el-tag v-if="doc.needOcr" size="small" type="warning" effect="plain" round>需OCR</el-tag>
            </div>
          </div>
          <EmptyState
            v-if="!docLoading && docs.length === 0"
            :icon="Document"
            title="暂无论文"
            description="请先在「科研知识库」上传 PDF 并完成解析"
            compact
          />
        </div>
      </div>

      <!-- 右：阅读与分析 -->
      <div class="reader sp-card">
        <EmptyState
          v-if="!selectedDoc"
          :icon="Reading"
          title="选择一篇论文开始阅读"
          description="左侧列表展示你的全部文档。解析成功且含有效文本的论文可发起 AI 分析；扫描版 PDF 需 OCR，暂不支持分析。"
        />
        <template v-else>
          <div class="reader-head">
            <div class="reader-title">
              <h3>{{ selectedDoc.title }}</h3>
              <div class="reader-meta">
                <el-tag effect="plain" round size="small">{{ selectedDoc.pageCount }} 页</el-tag>
                <el-tag effect="plain" round size="small">{{ selectedDoc.charCount.toLocaleString() }} 字符</el-tag>
                <el-tag effect="plain" round size="small">{{ formatSize(selectedDoc.fileSize) }}</el-tag>
                <el-tag effect="plain" round size="small" type="info">{{ selectedDoc.createTime }}</el-tag>
              </div>
            </div>
            <div class="reader-actions">
              <el-button
                v-if="analysisStatus !== 'PENDING' && analysisStatus !== 'PROCESSING'"
                type="primary"
                :icon="MagicStick"
                :disabled="!canAnalyze"
                :loading="submitting"
                @click="startAnalysis"
              >
                {{ analysisStatus === 'SUCCESS' ? '重新分析' : 'AI 分析' }}
              </el-button>
              <el-tooltip v-if="!canAnalyze" :content="analyzeBlockReason" placement="top">
                <el-icon class="block-hint"><Warning /></el-icon>
              </el-tooltip>
            </div>
          </div>

          <!-- 分析进度 -->
          <div v-if="analysisStatus === 'PENDING' || analysisStatus === 'PROCESSING'" class="progress-card">
            <el-icon class="is-loading progress-icon"><Loading /></el-icon>
            <div>
              <div class="progress-title">{{ analysisStatus === 'PENDING' ? '分析任务排队中…' : 'AI 正在分析论文…' }}</div>
              <div class="progress-desc">
                超长论文将分段提取后归纳（模型 {{ statusModel || 'qwen-plus' }}），请稍候，页面会自动刷新结果
              </div>
            </div>
          </div>

          <!-- 分析失败 -->
          <el-alert
            v-else-if="analysisStatus === 'FAILED'"
            type="error"
            :closable="false"
            show-icon
            class="fail-alert"
          >
            <template #title>分析失败：{{ analysisErrorMsg || '请稍后重试' }}</template>
            <el-button size="small" type="primary" plain :loading="submitting" @click="retryAnalysis">
              重试分析
            </el-button>
          </el-alert>

          <!-- 分析结果 -->
          <div v-else-if="analysisStatus === 'SUCCESS' && analysis" class="analysis-body">
            <div class="summary-card">
              <div class="card-label"><el-icon><Notebook /></el-icon> 摘要</div>
              <p>{{ analysis.result.abstract }}</p>
            </div>
            <div class="field-grid">
              <div v-for="field in fieldDefs" :key="field.key" class="field-card" :class="{ highlight: field.highlight }">
                <div class="card-label"><el-icon><component :is="field.icon" /></el-icon> {{ field.label }}</div>
                <p :class="{ 'not-stated': analysis.result[field.key] === '未明确说明' }">
                  {{ analysis.result[field.key] }}
                </p>
              </div>
            </div>
            <div class="analysis-footer">
              <span>模型 {{ analysis.model }}</span>
              <span>耗时 {{ (analysis.durationMs / 1000).toFixed(1) }}s</span>
              <span>{{ analysis.segmentCount === 0 ? '单段直析' : `分段采样 ${analysis.segmentCount} 段` }}</span>
              <span>分析于 {{ analysis.updateTime }}</span>
              <span class="overwrite-note">重新分析将覆盖当前结果</span>
            </div>
          </div>

          <!-- 未分析 -->
          <EmptyState
            v-else-if="!statusLoading"
            :icon="MagicStick"
            title="尚未发起 AI 分析"
            :description="canAnalyze
              ? '点击右上角「AI 分析」，通义千问将提取摘要、研究方法、创新点、实验与结论等 12 项结构化信息；原文未提及的项会如实标记为「未明确说明」。'
              : analyzeBlockReason"
            compact
          />

          <!-- 原文阅读 -->
          <el-collapse v-if="selectedDoc.status === 'SUCCESS'" class="raw-collapse" @change="onCollapseChange">
            <el-collapse-item title="原文阅读（PDFBox 逐页提取文本）" name="raw">
              <div v-loading="contentLoading">
                <template v-if="content">
                  <el-alert
                    v-if="content.needOcr"
                    type="warning"
                    :closable="false"
                    show-icon
                    title="扫描版 PDF：未提取到文本，需 OCR 后才能阅读与分析"
                    class="ocr-alert"
                  />
                  <div v-for="page in content.pages" :key="page.pageNum" class="raw-page">
                    <div class="raw-page-num">第 {{ page.pageNum }} 页</div>
                    <pre>{{ page.text || '（本页无可提取文本）' }}</pre>
                  </div>
                </template>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document, Reading, MagicStick, Warning, Loading, Notebook,
  Aim, SetUp, Trophy, Histogram, TrendCharts, Compass, Flag, ChatLineSquare
} from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { pageDocuments, getDocumentContent } from '@/api/document'
import { analyzePaper, getPaperAnalysis, getAnalysisStatus } from '@/api/paper'
import type {
  DocumentVO, DocumentContentVO, AnalysisStatus, AnalysisStatusVO, PaperAnalysisVO, PaperAnalysisResult
} from '@/types/api'

const docs = ref<DocumentVO[]>([])
const docLoading = ref(false)
const selectedId = ref<number | null>(null)
const selectedDoc = computed(() => docs.value.find((d) => d.id === selectedId.value) ?? null)

const statusLoading = ref(false)
const analysisStatus = ref<AnalysisStatus | null>(null)
const analysisErrorMsg = ref('')
const statusModel = ref('')
const analysis = ref<PaperAnalysisVO | null>(null)
const submitting = ref(false)

const content = ref<DocumentContentVO | null>(null)
const contentLoading = ref(false)
const contentLoaded = ref(false)

let pollTimer: number | null = null

const fieldDefs: {
  key: keyof PaperAnalysisResult
  label: string
  icon: unknown
  highlight?: boolean
}[] = [
  { key: 'background', label: '研究背景', icon: Compass },
  { key: 'researchQuestion', label: '研究问题', icon: Aim },
  { key: 'methods', label: '研究方法', icon: SetUp, highlight: true },
  { key: 'technicalRoute', label: '技术路线', icon: TrendCharts },
  { key: 'innovations', label: '核心创新点', icon: Trophy, highlight: true },
  { key: 'experimentDesign', label: '实验设计', icon: Histogram },
  { key: 'experimentResults', label: '实验结果', icon: Histogram, highlight: true },
  { key: 'conclusion', label: '研究结论', icon: Flag, highlight: true },
  { key: 'limitations', label: '研究局限性', icon: Warning },
  { key: 'futureWork', label: '未来研究方向', icon: ChatLineSquare }
]

const canAnalyze = computed(() => {
  const doc = selectedDoc.value
  return !!doc && doc.status === 'SUCCESS' && !doc.needOcr && doc.charCount > 0
})

const analyzeBlockReason = computed(() => {
  const doc = selectedDoc.value
  if (!doc) return ''
  if (doc.status !== 'SUCCESS') return '文档尚未解析成功，请先在知识库完成解析'
  if (doc.needOcr || doc.charCount === 0) return '扫描版 PDF 无有效文本，需 OCR 后才能分析（本阶段不伪造识别结果）'
  return ''
})

onMounted(async () => {
  await loadDocs()
  const first = docs.value.find((d) => d.status === 'SUCCESS') ?? docs.value[0]
  if (first) selectDoc(first)
})

onBeforeUnmount(() => stopPolling())

async function loadDocs() {
  docLoading.value = true
  try {
    const result = await pageDocuments(null, 1, 100)
    docs.value = result.records
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    docLoading.value = false
  }
}

function selectDoc(doc: DocumentVO) {
  if (selectedId.value === doc.id) return
  selectedId.value = doc.id
  analysis.value = null
  analysisStatus.value = null
  analysisErrorMsg.value = ''
  content.value = null
  contentLoaded.value = false
  stopPolling()
  loadAnalysisState(doc.id)
}

async function loadAnalysisState(docId: number) {
  statusLoading.value = true
  try {
    const status = await getAnalysisStatus(docId)
    if (selectedId.value !== docId) return
    applyStatus(status)
    if (status.status === 'SUCCESS') {
      await loadAnalysis(docId)
    } else if (status.status === 'PENDING' || status.status === 'PROCESSING') {
      schedulePolling()
    }
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    statusLoading.value = false
  }
}

function applyStatus(status: AnalysisStatusVO) {
  analysisStatus.value = status.status
  analysisErrorMsg.value = status.errorMsg ?? ''
  statusModel.value = status.model ?? ''
}

async function loadAnalysis(docId: number) {
  try {
    const result = await getPaperAnalysis(docId)
    if (selectedId.value !== docId) return
    analysis.value = result
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  }
}

function schedulePolling() {
  if (pollTimer === null) {
    pollTimer = window.setInterval(pollStatus, 3000)
  }
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollStatus() {
  const docId = selectedId.value
  if (docId === null) {
    stopPolling()
    return
  }
  try {
    const status = await getAnalysisStatus(docId)
    if (selectedId.value !== docId) return
    applyStatus(status)
    if (status.status === 'SUCCESS') {
      stopPolling()
      await loadAnalysis(docId)
      await loadDocs()
    } else if (status.status === 'FAILED') {
      stopPolling()
    }
  } catch {
    // 单次轮询失败忽略，下一轮重试
  }
}

async function startAnalysis() {
  const doc = selectedDoc.value
  if (!doc || !canAnalyze.value) return
  if (analysisStatus.value === 'SUCCESS') {
    try {
      await ElMessageBox.confirm(
        '重新分析将覆盖当前展示的分析结果（历史任务记录保留）。确定继续？',
        '重新分析',
        { type: 'warning', confirmButtonText: '覆盖并重新分析', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    await doAnalyze(doc.id, true)
  } else {
    await doAnalyze(doc.id, false)
  }
}

async function retryAnalysis() {
  const doc = selectedDoc.value
  if (!doc) return
  await doAnalyze(doc.id, false)
}

async function doAnalyze(docId: number, reanalyze: boolean) {
  submitting.value = true
  try {
    const status = await analyzePaper(docId, reanalyze)
    applyStatus(status)
    analysis.value = null
    ElMessage.success('已加入 AI 分析队列')
    schedulePolling()
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function onCollapseChange(names: string[]) {
  const doc = selectedDoc.value
  if (!doc || !names.includes('raw') || contentLoaded.value) return
  contentLoading.value = true
  try {
    content.value = await getDocumentContent(doc.id)
    contentLoaded.value = true
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    contentLoading.value = false
  }
}

function parseLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待解析', PROCESSING: '解析中', SUCCESS: '已解析', FAILED: '解析失败'
  }
  return map[status] ?? status
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
</script>

<style scoped>
.reading-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  align-items: start;
}

.doc-list {
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

.doc-items {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-item {
  padding: 10px 12px;
  border-radius: var(--sp-radius-sm);
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.doc-item:hover {
  background: rgba(10, 132, 255, 0.06);
}

.doc-item.active {
  background: rgba(10, 132, 255, 0.1);
  border-color: rgba(10, 132, 255, 0.3);
}

.doc-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--sp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
  font-size: 12px;
  color: var(--sp-text-2);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--sp-text-3);
}

.status-dot.success { background: #34c759; }
.status-dot.failed { background: #ff3b30; }
.status-dot.processing, .status-dot.pending { background: #ff9f0a; }

.reader {
  padding: 20px;
  min-height: 480px;
}

.reader-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.reader-title h3 {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.2px;
}

.reader-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.reader-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.block-hint {
  color: #ff9f0a;
  font-size: 18px;
}

.progress-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-radius: var(--sp-radius-sm);
  background: rgba(10, 132, 255, 0.07);
  border: 1px solid rgba(10, 132, 255, 0.18);
  margin-bottom: 16px;
}

.progress-icon {
  font-size: 26px;
  color: var(--sp-primary);
}

.progress-title {
  font-size: 15px;
  font-weight: 600;
}

.progress-desc {
  font-size: 12px;
  color: var(--sp-text-2);
  margin-top: 4px;
}

.fail-alert {
  margin-bottom: 16px;
  border-radius: var(--sp-radius-sm);
}

.fail-alert .el-button {
  margin-top: 8px;
}

.analysis-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.summary-card {
  padding: 18px;
  border-radius: var(--sp-radius-sm);
  background: var(--sp-grad);
  color: #fff;
  box-shadow: 0 8px 24px rgba(10, 132, 255, 0.3);
}

.summary-card .card-label {
  color: rgba(255, 255, 255, 0.9);
}

.summary-card p {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.8;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.field-card {
  padding: 14px 16px;
  border-radius: var(--sp-radius-sm);
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
}

.field-card.highlight {
  border-color: rgba(10, 132, 255, 0.25);
  background: rgba(10, 132, 255, 0.05);
}

.card-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sp-text-2);
}

.field-card p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--sp-text);
  white-space: pre-wrap;
}

.field-card p.not-stated {
  color: var(--sp-text-3);
  font-style: italic;
}

.analysis-footer {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--sp-text-2);
  padding: 4px 2px;
}

.overwrite-note {
  color: #ff9f0a;
}

.raw-collapse {
  margin-top: 18px;
  border-radius: var(--sp-radius-sm);
}

.ocr-alert {
  margin-bottom: 10px;
}

.raw-page {
  margin-bottom: 14px;
}

.raw-page-num {
  font-size: 12px;
  font-weight: 600;
  color: var(--sp-primary);
  margin-bottom: 6px;
}

.raw-page pre {
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  color: var(--sp-text);
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 10px;
  padding: 12px 14px;
}

html.dark .raw-page pre {
  background: rgba(255, 255, 255, 0.05);
}

@media (max-width: 900px) {
  .reading-grid {
    grid-template-columns: 1fr;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>