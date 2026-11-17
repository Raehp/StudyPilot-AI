<template>
  <div class="knowledge">
    <PageHeader
      title="科研知识库"
      subtitle="按课题组织文献，上传 PDF 自动解析全文；切片与向量检索将在 M4 开放"
      milestone="M2"
    >
      <template #actions>
        <el-input
          v-model="kbSearch"
          placeholder="搜索知识库"
          clearable
          style="width: 200px"
          :prefix-icon="Search"
        />
        <el-button type="primary" :icon="Plus" @click="openKbForm()">创建知识库</el-button>
      </template>
    </PageHeader>

    <div class="kb-layout">
      <!-- 左侧：知识库列表 -->
      <div class="sp-card kb-panel">
        <div class="panel-title">我的知识库</div>
        <div v-loading="kbLoading" class="kb-list">
          <div
            v-for="kb in filteredKbs"
            :key="kb.id"
            class="kb-item"
            :class="{ active: selectedKbId === kb.id }"
            @click="selectKb(kb.id)"
          >
            <el-icon class="kb-icon"><Collection /></el-icon>
            <div class="kb-meta">
              <div class="kb-name">{{ kb.name }}</div>
              <div class="kb-sub">{{ kb.docCount }} 篇文档</div>
            </div>
            <div class="kb-ops" @click.stop>
              <el-icon title="编辑" @click="openKbForm(kb)"><Edit /></el-icon>
              <el-icon title="删除" @click="confirmDeleteKb(kb)"><Delete /></el-icon>
            </div>
          </div>
          <EmptyState
            v-if="!kbLoading && filteredKbs.length === 0"
            compact
            :icon="Collection"
            :title="kbSearch ? '没有匹配的知识库' : '还没有知识库'"
            :description="kbSearch ? '换个关键词试试' : '点击右上角「创建知识库」开始组织文献'"
          />
        </div>
        <div
          class="kb-item pool-item"
          :class="{ active: selectedKbId === 0 }"
          @click="selectKb(0)"
        >
          <el-icon class="kb-icon"><FolderOpened /></el-icon>
          <div class="kb-meta">
            <div class="kb-name">未归属文档池</div>
            <div class="kb-sub">{{ poolCount }} 篇文档</div>
          </div>
        </div>
      </div>

      <!-- 右侧：文档管理 -->
      <div class="sp-card doc-panel">
        <template v-if="selectedKbId !== null">
          <div class="doc-header">
            <div>
              <div class="doc-title">{{ selectedKbName }}</div>
              <div v-if="selectedKbDesc" class="doc-desc">{{ selectedKbDesc }}</div>
            </div>
            <el-tag v-if="selectedKbId === 0" type="info" effect="light" round>
              移除知识库的文档会进入此处，可重新分配
            </el-tag>
          </div>

          <!-- 上传区域 -->
          <el-upload
            drag
            accept=".pdf"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="customUpload"
            :disabled="uploading"
            class="upload-zone"
          >
            <div v-if="!uploading" class="upload-inner">
              <el-icon :size="30"><UploadFilled /></el-icon>
              <div class="upload-text">将 PDF 拖到此处，或<em>点击上传</em></div>
              <div class="upload-hint">仅支持 PDF，单文件 ≤ 30MB；上传后自动异步解析全文</div>
            </div>
            <div v-else class="upload-inner">
              <el-progress type="circle" :percentage="uploadPercent" :width="72" />
              <div class="upload-text">正在上传 {{ uploadingName }}…</div>
            </div>
          </el-upload>

          <!-- 文档表格 -->
          <el-table
            v-loading="docLoading"
            :data="docs"
            class="doc-table"
            empty-text=" "
          >
            <el-table-column label="文档" min-width="220">
              <template #default="{ row }">
                <div class="doc-cell-title">{{ row.title }}</div>
                <div class="doc-cell-file">{{ row.fileName }}</div>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="90">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="页数" width="70" prop="pageCount" />
            <el-table-column label="字符数" width="100">
              <template #default="{ row }">{{ row.charCount.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="解析状态" width="150">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" effect="light" size="small">
                  <el-icon v-if="row.status === 'PROCESSING'" class="is-loading"><Loading /></el-icon>
                  {{ statusLabel(row.status) }}
                </el-tag>
                <el-tag v-if="row.needOcr" type="warning" effect="plain" size="small" class="ocr-tag">
                  扫描版·需OCR
                </el-tag>
                <div v-if="row.status === 'FAILED' && row.errorMsg" class="fail-reason">
                  {{ row.errorMsg }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="160" prop="createTime" />
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  size="small"
                  :disabled="row.status !== 'SUCCESS'"
                  @click="openPreview(row)"
                >
                  预览
                </el-button>
                <el-button
                  v-if="row.status === 'FAILED'"
                  link
                  type="warning"
                  size="small"
                  @click="retryParse(row)"
                >
                  重试解析
                </el-button>
                <el-button
                  v-if="selectedKbId === 0"
                  link
                  type="primary"
                  size="small"
                  @click="openMoveDialog(row)"
                >
                  移入知识库
                </el-button>
                <el-button
                  v-else
                  link
                  type="info"
                  size="small"
                  @click="removeFromKb(row)"
                >
                  移除
                </el-button>
                <el-button link type="danger" size="small" @click="confirmDeleteDoc(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState
                compact
                :icon="Document"
                title="暂无文档"
                description="上传第一篇 PDF，解析完成后即可预览全文"
              />
            </template>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadDocs"
              @size-change="onSizeChange"
            />
          </div>
        </template>

        <EmptyState
          v-else
          :icon="Collection"
          title="开始构建你的科研知识库"
          description="创建一个知识库，上传论文 PDF，系统会自动解析全文并保留页码。切片向量化与 RAG 问答将在 M4/M5 开放。"
        >
          <el-button type="primary" :icon="Plus" @click="openKbForm()">创建第一个知识库</el-button>
        </EmptyState>
      </div>
    </div>

    <!-- 创建/编辑知识库弹窗 -->
    <el-dialog
      v-model="kbFormVisible"
      :title="editingKb ? '编辑知识库' : '创建知识库'"
      width="440px"
      destroy-on-close
    >
      <el-form ref="kbFormRef" :model="kbForm" :rules="kbRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="kbForm.name" maxlength="64" show-word-limit placeholder="例如：深度学习论文库" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="kbForm.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="课题方向、文献范围等（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="kbSaving" @click="saveKb">保存</el-button>
      </template>
    </el-dialog>

    <!-- 移入知识库弹窗 -->
    <el-dialog v-model="moveVisible" title="移入知识库" width="400px" destroy-on-close>
      <el-select v-model="moveTargetKbId" placeholder="选择目标知识库" style="width: 100%">
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!moveTargetKbId" @click="moveDoc">确定</el-button>
      </template>
    </el-dialog>

    <!-- 文档全文预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewContent?.title || '文档预览'"
      width="720px"
      top="6vh"
      destroy-on-close
    >
      <div v-loading="previewLoading" class="preview-body">
        <template v-if="previewContent">
          <div class="preview-meta">
            <el-tag effect="light" round size="small">{{ previewContent.pageCount }} 页</el-tag>
            <el-tag effect="light" round size="small">
              {{ previewContent.charCount.toLocaleString() }} 字符
            </el-tag>
            <el-tag v-if="previewContent.needOcr" type="warning" effect="light" round size="small">
              扫描版 PDF · 无可提取文本 · 需 OCR（暂未接入）
            </el-tag>
          </div>
          <el-alert
            v-if="previewContent.needOcr"
            type="warning"
            :closable="false"
            title="该 PDF 未提取到任何文本，可能为扫描版。系统已如实标记为「需 OCR」，不会伪造识别结果。"
            class="ocr-alert"
          />
          <div v-for="p in previewContent.pages" :key="p.pageNum" class="preview-page">
            <div class="page-num">第 {{ p.pageNum }} 页</div>
            <pre class="page-text">{{ p.text || '（本页无可提取文本）' }}</pre>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
import {
  Collection,
  Delete,
  Document,
  Edit,
  FolderOpened,
  Loading,
  Plus,
  Search,
  UploadFilled
} from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  addDocumentToKb,
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  removeDocumentFromKb,
  updateKnowledgeBase
} from '@/api/knowledge'
import {
  deleteDocument,
  getDocumentContent,
  getParseStatus,
  pageDocuments,
  triggerParse,
  uploadDocument
} from '@/api/document'
import type { DocumentContentVO, DocumentStatus, DocumentVO, KnowledgeBaseVO } from '@/types/api'

const MAX_SIZE_MB = 30

const kbList = ref<KnowledgeBaseVO[]>([])
const kbLoading = ref(false)
const kbSearch = ref('')
const selectedKbId = ref<number | null>(null)
const poolCount = ref(0)

const docs = ref<DocumentVO[]>([])
const docLoading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const uploading = ref(false)
const uploadPercent = ref(0)
const uploadingName = ref('')

const kbFormVisible = ref(false)
const editingKb = ref<KnowledgeBaseVO | null>(null)
const kbSaving = ref(false)
const kbFormRef = ref<FormInstance>()
const kbForm = reactive({ name: '', description: '' })
const kbRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' }
  ],
  description: [{ max: 255, message: '描述不能超过 255 个字符', trigger: 'blur' }]
}

const moveVisible = ref(false)
const moveTargetKbId = ref<number | null>(null)
const movingDoc = ref<DocumentVO | null>(null)

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewContent = ref<DocumentContentVO | null>(null)

let pollTimer: number | null = null

const filteredKbs = computed(() => {
  const keyword = kbSearch.value.trim().toLowerCase()
  if (!keyword) return kbList.value
  return kbList.value.filter(
    (kb) =>
      kb.name.toLowerCase().includes(keyword) || kb.description.toLowerCase().includes(keyword)
  )
})

const selectedKbName = computed(() => {
  if (selectedKbId.value === 0) return '未归属文档池'
  return kbList.value.find((kb) => kb.id === selectedKbId.value)?.name ?? '知识库'
})

const selectedKbDesc = computed(() => {
  if (selectedKbId.value === 0) return ''
  return kbList.value.find((kb) => kb.id === selectedKbId.value)?.description ?? ''
})

onMounted(async () => {
  await loadKbs()
  if (kbList.value.length > 0) {
    selectKb(kbList.value[0].id)
  } else {
    selectKb(0)
  }
})

onBeforeUnmount(() => stopPolling())

async function loadKbs() {
  kbLoading.value = true
  try {
    kbList.value = await listKnowledgeBases()
    const pool = await pageDocuments(0, 1, 1)
    poolCount.value = pool.total
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    kbLoading.value = false
  }
}

function selectKb(id: number) {
  selectedKbId.value = id
  page.value = 1
  loadDocs()
}

async function loadDocs() {
  if (selectedKbId.value === null) return
  docLoading.value = true
  try {
    const result = await pageDocuments(selectedKbId.value, page.value, size.value)
    docs.value = result.records
    total.value = result.total
    schedulePolling()
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    docLoading.value = false
  }
}

function onSizeChange() {
  page.value = 1
  loadDocs()
}

function schedulePolling() {
  const pending = docs.value.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING')
  if (pending && pollTimer === null) {
    pollTimer = window.setInterval(pollParseStatus, 3000)
  } else if (!pending) {
    stopPolling()
  }
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollParseStatus() {
  const targets = docs.value.filter((d) => d.status === 'PENDING' || d.status === 'PROCESSING')
  if (targets.length === 0) {
    stopPolling()
    return
  }
  let changed = false
  for (const doc of targets) {
    try {
      const status = await getParseStatus(doc.id)
      if (status.status !== doc.status) {
        changed = true
      }
      doc.status = status.status
      doc.pageCount = status.pageCount
      doc.charCount = status.charCount
      doc.needOcr = status.needOcr
      doc.errorMsg = status.errorMsg
    } catch {
      // 单次轮询失败忽略，下一轮重试
    }
  }
  if (changed) {
    schedulePolling()
    if (!docs.value.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING')) {
      loadKbs()
    }
  }
}

function beforeUpload(rawFile: File) {
  if (!rawFile.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.error('仅支持 PDF 文件')
    return false
  }
  if (rawFile.size > MAX_SIZE_MB * 1024 * 1024) {
    ElMessage.error(`文件大小超出限制（最大 ${MAX_SIZE_MB}MB）`)
    return false
  }
  return true
}

async function customUpload(options: UploadRequestOptions) {
  uploading.value = true
  uploadPercent.value = 0
  uploadingName.value = options.file.name
  try {
    await uploadDocument(options.file, selectedKbId.value, (percent) => {
      uploadPercent.value = percent
    })
    ElMessage.success('上传成功，已加入解析队列')
    await loadDocs()
    await loadKbs()
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

function openKbForm(kb?: KnowledgeBaseVO) {
  editingKb.value = kb ?? null
  kbForm.name = kb?.name ?? ''
  kbForm.description = kb?.description ?? ''
  kbFormVisible.value = true
}

async function saveKb() {
  if (!kbFormRef.value) return
  try {
    await kbFormRef.value.validate()
  } catch {
    return
  }
  kbSaving.value = true
  try {
    if (editingKb.value) {
      await updateKnowledgeBase(editingKb.value.id, { ...kbForm })
      ElMessage.success('知识库已更新')
    } else {
      const created = await createKnowledgeBase({ ...kbForm })
      ElMessage.success('知识库已创建')
      await loadKbs()
      selectKb(created.id)
    }
    kbFormVisible.value = false
    await loadKbs()
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  } finally {
    kbSaving.value = false
  }
}

async function confirmDeleteKb(kb: KnowledgeBaseVO) {
  try {
    await ElMessageBox.confirm(
      kb.docCount > 0
        ? `删除「${kb.name}」将同时删除其中 ${kb.docCount} 篇文档及其文件，且不可恢复。确定删除？`
        : `确定删除知识库「${kb.name}」？`,
      '删除知识库',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteKnowledgeBase(kb.id)
    ElMessage.success('知识库已删除')
    await loadKbs()
    if (selectedKbId.value === kb.id) {
      selectKb(kbList.value.length > 0 ? kbList.value[0].id : 0)
    }
  } catch {
    // 用户取消或请求异常（提示由拦截器处理）
  }
}

async function confirmDeleteDoc(doc: DocumentVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除文档「${doc.title}」？文件与解析结果将一并删除。`,
      '删除文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteDocument(doc.id)
    ElMessage.success('文档已删除')
    await loadDocs()
    await loadKbs()
  } catch {
    // 用户取消或请求异常（提示由拦截器处理）
  }
}

async function removeFromKb(doc: DocumentVO) {
  if (selectedKbId.value === null || selectedKbId.value === 0) return
  try {
    await ElMessageBox.confirm(
      `将「${doc.title}」移出当前知识库？文档会保留在「未归属文档池」，可重新分配。`,
      '移除文档',
      { type: 'info', confirmButtonText: '移除', cancelButtonText: '取消' }
    )
    await removeDocumentFromKb(selectedKbId.value, doc.id)
    ElMessage.success('已移入未归属文档池')
    await loadDocs()
    await loadKbs()
  } catch {
    // 用户取消或请求异常（提示由拦截器处理）
  }
}

function openMoveDialog(doc: DocumentVO) {
  if (kbList.value.length === 0) {
    ElMessage.warning('请先创建知识库')
    return
  }
  movingDoc.value = doc
  moveTargetKbId.value = null
  moveVisible.value = true
}

async function moveDoc() {
  if (!movingDoc.value || !moveTargetKbId.value) return
  try {
    await addDocumentToKb(moveTargetKbId.value, movingDoc.value.id)
    ElMessage.success('已移入知识库')
    moveVisible.value = false
    await loadDocs()
    await loadKbs()
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  }
}

async function retryParse(doc: DocumentVO) {
  try {
    await triggerParse(doc.id)
    ElMessage.success('已重新加入解析队列')
    doc.status = 'PROCESSING'
    schedulePolling()
  } catch {
    // 错误提示由 Axios 拦截器统一处理
  }
}

async function openPreview(doc: DocumentVO) {
  previewVisible.value = true
  previewLoading.value = true
  previewContent.value = null
  try {
    previewContent.value = await getDocumentContent(doc.id)
  } catch {
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

function statusLabel(status: DocumentStatus) {
  const map: Record<DocumentStatus, string> = {
    PENDING: '待解析',
    PROCESSING: '解析中',
    SUCCESS: '解析成功',
    FAILED: '解析失败'
  }
  return map[status]
}

function statusTagType(status: DocumentStatus) {
  const map: Record<DocumentStatus, 'info' | 'warning' | 'success' | 'danger'> = {
    PENDING: 'info',
    PROCESSING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger'
  }
  return map[status]
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
</script>

<style scoped>
.kb-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: start;
}

.kb-panel {
  padding: 16px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sp-text-2);
  margin-bottom: 10px;
  padding-left: 4px;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 60px;
  max-height: 52vh;
  overflow-y: auto;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--sp-radius-sm);
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.kb-item:hover {
  background: var(--sp-surface);
}

.kb-item.active {
  background: var(--sp-surface);
  border-color: var(--sp-primary);
}

.kb-icon {
  color: var(--sp-primary);
  font-size: 18px;
  flex-shrink: 0;
}

.kb-meta {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-sub {
  font-size: 12px;
  color: var(--sp-text-2);
  margin-top: 2px;
}

.kb-ops {
  display: none;
  gap: 8px;
  color: var(--sp-text-2);
}

.kb-item:hover .kb-ops {
  display: flex;
}

.kb-ops .el-icon:hover {
  color: var(--sp-primary);
}

.pool-item {
  margin-top: 10px;
  border-top: 1px dashed var(--sp-border);
  padding-top: 14px;
}

.doc-panel {
  padding: 20px;
  min-height: 480px;
}

.doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.doc-title {
  font-size: 18px;
  font-weight: 700;
}

.doc-desc {
  font-size: 13px;
  color: var(--sp-text-2);
  margin-top: 4px;
}

.upload-zone {
  margin-bottom: 16px;
}

.upload-zone :deep(.el-upload-dragger) {
  border-radius: var(--sp-radius);
  border: 1.5px dashed var(--sp-border);
  background: var(--sp-surface);
  padding: 22px;
}

.upload-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--sp-text-2);
}

.upload-inner .el-icon {
  color: var(--sp-primary);
}

.upload-text {
  font-size: 14px;
  color: var(--sp-text);
}

.upload-text em {
  color: var(--sp-primary);
  font-style: normal;
  font-weight: 600;
}

.upload-hint {
  font-size: 12px;
  color: var(--sp-text-3);
}

.doc-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-border-color: var(--sp-border);
  --el-table-text-color: var(--sp-text);
  --el-table-header-text-color: var(--sp-text-2);
}

.doc-cell-title {
  font-weight: 600;
  font-size: 13px;
}

.doc-cell-file {
  font-size: 12px;
  color: var(--sp-text-3);
  margin-top: 2px;
}

.ocr-tag {
  margin-left: 6px;
}

.fail-reason {
  font-size: 12px;
  color: var(--el-color-danger);
  margin-top: 4px;
  line-height: 1.4;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.preview-body {
  max-height: 64vh;
  overflow-y: auto;
}

.preview-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.ocr-alert {
  margin-bottom: 12px;
  border-radius: var(--sp-radius-sm);
}

.preview-page {
  margin-bottom: 14px;
}

.page-num {
  font-size: 12px;
  font-weight: 600;
  color: var(--sp-primary);
  margin-bottom: 6px;
}

.page-text {
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  border-radius: var(--sp-radius-sm);
  padding: 12px 14px;
  color: var(--sp-text);
}

@media (max-width: 900px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-list {
    max-height: 240px;
  }
}
</style>