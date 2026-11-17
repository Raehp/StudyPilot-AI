<template>
  <div class="chat">
    <PageHeader
      title="AI 智能问答"
      subtitle="基于知识库的 RAG 多轮问答，回答附带引用文档与原文片段"
    />
    <div class="chat-grid">
      <div class="sessions glass">
        <div class="panel-head">
          <span>会话列表</span>
          <el-button type="primary" size="small" :icon="Plus" @click="openCreateDialog">新会话</el-button>
        </div>
        <div v-loading="loadingSessions" class="session-list">
          <EmptyState
            v-if="!loadingSessions && sessions.length === 0"
            :icon="ChatDotRound"
            title="暂无会话"
            description="点击「新会话」选择知识库后开始提问"
            compact
          />
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === activeId }"
            @click="selectSession(session.id)"
          >
            <div class="session-title">{{ session.title }}</div>
            <div class="session-meta">
              <span class="session-kb">{{ session.kbName }}</span>
              <el-button
                class="session-delete"
                link
                size="small"
                :icon="Delete"
                @click.stop="removeSession(session)"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="chat-main glass">
        <div class="chat-toolbar">
          <template v-if="activeSession">
            <el-tag size="small" effect="plain">{{ activeSession.kbName }}</el-tag>
            <span class="toolbar-title">{{ activeSession.title }}</span>
          </template>
          <span v-else class="toolbar-title muted">未选择会话</span>
          <span v-if="sending" class="toolbar-status">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在检索并生成回答…
          </span>
        </div>

        <div ref="messagesEl" class="messages" :class="{ centered: messages.length === 0 }">
          <EmptyState
            v-if="messages.length === 0 && !loadingMessages"
            :icon="ChatDotRound"
            title="开始一段基于文献的对话"
            :description="activeId
              ? '在下方输入问题，AI 将检索该知识库的相关切片并流式作答，每条回答附带引用文档、页码与原文片段。'
              : '请先在左侧创建或选择一个会话。'"
          />
          <div v-for="message in messages" :key="message.id" class="message" :class="message.role">
            <div class="bubble" :class="{ failed: message.role === 'assistant' && message.status === 'FAILED' }">
              <template v-if="message.role === 'user'">{{ message.content }}</template>
              <template v-else>
                <div
                  v-if="message.content"
                  class="markdown"
                  v-html="renderMarkdown(message.content)"
                ></div>
                <span v-if="message.status === 'GENERATING'" class="cursor">▍</span>
                <div v-if="message.status === 'FAILED'" class="msg-error">
                  <el-icon><WarningFilled /></el-icon>
                  {{ message.errorMsg || '生成失败' }}
                  <el-button size="small" type="primary" link @click="retry(message)">重试</el-button>
                </div>
                <div v-if="message.status === 'STOPPED'" class="msg-stopped">
                  已停止生成{{ message.content ? '，以上为部分内容' : '' }}
                </div>
                <div v-if="message.citations.length > 0" class="citations">
                  <div class="citations-head">引用来源（{{ message.citations.length }}）</div>
                  <div
                    v-for="(citation, index) in message.citations"
                    :key="citation.chunkId"
                    class="citation"
                    :class="{ expanded: isExpanded(message.id, index) }"
                    @click="toggleCitation(message, index)"
                  >
                    <div class="citation-row">
                      <span class="citation-index">[{{ index + 1 }}]</span>
                      <span class="citation-doc">《{{ citation.documentName }}》</span>
                      <span class="citation-page">第{{ citation.pageNum ?? '?' }}页</span>
                      <span class="citation-score">相似度 {{ (citation.similarityScore * 100).toFixed(1) }}%</span>
                      <el-icon class="citation-arrow"><ArrowDown /></el-icon>
                    </div>
                    <div v-if="isExpanded(message.id, index)" class="citation-body" @click.stop>
                      <div v-if="deletedDocs.has(citation.documentId)" class="citation-deleted">
                        原文档已删除，以下为问答时的检索快照
                      </div>
                      <div class="citation-original">{{ citation.originalContent }}</div>
                      <div class="citation-foot">chunkId={{ citation.chunkId }} · 点击收起</div>
                    </div>
                  </div>
                </div>
                <div v-if="message.status === 'SUCCESS' && message.tokenCount > 0" class="msg-tokens">
                  消耗 {{ message.tokenCount }} tokens
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="composer">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            :disabled="!activeId || sending"
            :placeholder="activeId ? '输入问题，Enter 发送，Shift+Enter 换行' : '请先创建或选择会话'"
            resize="none"
            @keydown.enter.exact.prevent="send"
          />
          <el-button
            v-if="sending"
            type="danger"
            plain
            :icon="VideoPause"
            @click="stopGeneration"
          >停止</el-button>
          <el-button
            v-else
            type="primary"
            :icon="Promotion"
            :disabled="!activeId || !draft.trim()"
            @click="send"
          >发送</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新建问答会话" width="440px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="知识库" required>
          <el-select v-model="createForm.kbId" placeholder="选择问答范围" style="width: 100%">
            <el-option label="未分配文档" :value="0" />
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="`${kb.name}（${kb.docCount} 篇文档）`"
              :value="kb.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="createForm.title" maxlength="64" placeholder="可选，默认以首个问题命名" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">问答仅检索所选知识库中已完成向量化的文档切片。</div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createSession">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown, ChatDotRound, Delete, Loading, Plus, Promotion, VideoPause, WarningFilled
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import {
  cancelGeneration as apiCancelGeneration,
  createSession as apiCreateSession,
  deleteSession as apiDeleteSession,
  getSessionMessages,
  listSessions,
  sendMessageStream
} from '@/api/chat'
import { listKnowledgeBases } from '@/api/knowledge'
import { getDocumentContent } from '@/api/document'
import type { ChatMessageVO, CitationVO, ConversationVO, KnowledgeBaseVO } from '@/types/api'

const sessions = ref<ConversationVO[]>([])
const activeId = ref<number | null>(null)
const messages = ref<ChatMessageVO[]>([])
const knowledgeBases = ref<KnowledgeBaseVO[]>([])
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const creating = ref(false)
const createVisible = ref(false)
const createForm = reactive({ kbId: 0, title: '' })
const draft = ref('')
const messagesEl = ref<HTMLElement | null>(null)
const expanded = ref<Set<string>>(new Set())
const deletedDocs = ref<Set<number>>(new Set())
const checkedDocs = ref<Set<number>>(new Set())
let abortController: AbortController | null = null
let reconcileTimer: number | null = null

const activeSession = computed(() => sessions.value.find((s) => s.id === activeId.value) ?? null)

function renderMarkdown(text: string): string {
  return DOMPurify.sanitize(marked.parse(text, { async: false }) as string)
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesEl.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = await listSessions()
  } finally {
    loadingSessions.value = false
  }
}

async function loadKnowledgeBases() {
  knowledgeBases.value = await listKnowledgeBases()
}

async function selectSession(id: number) {
  if (id === activeId.value) {
    return
  }
  if (sending.value) {
    ElMessage.warning('回答生成中，请先停止或等待完成后再切换会话')
    return
  }
  activeId.value = id
  expanded.value = new Set()
  await loadMessages()
}

async function loadMessages() {
  if (activeId.value == null) {
    messages.value = []
    return
  }
  loadingMessages.value = true
  try {
    messages.value = await getSessionMessages(activeId.value)
    scrollToBottom()
  } finally {
    loadingMessages.value = false
  }
}

function openCreateDialog() {
  createForm.kbId = knowledgeBases.value.length > 0 ? knowledgeBases.value[0].id : 0
  createForm.title = ''
  createVisible.value = true
}

async function createSession() {
  creating.value = true
  try {
    const session = await apiCreateSession(createForm.kbId, createForm.title.trim() || undefined)
    createVisible.value = false
    await loadSessions()
    activeId.value = session.id
    messages.value = []
    draft.value = ''
  } finally {
    creating.value = false
  }
}

async function removeSession(session: ConversationVO) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${session.title}」？聊天记录将一并删除。`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await apiDeleteSession(session.id)
  ElMessage.success('会话已删除')
  if (activeId.value === session.id) {
    activeId.value = null
    messages.value = []
  }
  await loadSessions()
}

async function send() {
  const content = draft.value.trim()
  if (!content || activeId.value == null || sending.value) {
    return
  }
  const sessionId = activeId.value
  sending.value = true
  draft.value = ''
  const optimisticUser: ChatMessageVO = {
    id: -Date.now(),
    conversationId: sessionId,
    role: 'user',
    content,
    citations: [],
    tokenCount: 0,
    status: 'SUCCESS',
    errorMsg: '',
    createTime: ''
  }
  const optimisticAssistant: ChatMessageVO = {
    id: optimisticUser.id - 1,
    conversationId: sessionId,
    role: 'assistant',
    content: '',
    citations: [],
    tokenCount: 0,
    status: 'GENERATING',
    errorMsg: '',
    createTime: ''
  }
  messages.value.push(optimisticUser, optimisticAssistant)
  scrollToBottom()
  abortController = new AbortController()
  let metaReceived = false
  try {
    await sendMessageStream(
      sessionId,
      { content },
      {
        onMeta: (meta) => {
          metaReceived = true
          optimisticUser.id = meta.userMessageId
          optimisticAssistant.id = meta.assistantMessageId
          optimisticAssistant.citations = meta.citations
        },
        onDelta: (text) => {
          optimisticAssistant.content += text
          scrollToBottom()
        },
        onDone: (message) => {
          const index = messages.value.indexOf(optimisticAssistant)
          if (index >= 0) {
            messages.value[index] = message
          }
          scrollToBottom()
        },
        onError: (message) => {
          optimisticAssistant.status = 'FAILED'
          optimisticAssistant.errorMsg = message
        }
      },
      abortController.signal
    )
    await loadSessions()
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      optimisticAssistant.status = 'STOPPED'
      await reconcileAfterStop(sessionId, optimisticAssistant)
    } else {
      if (!metaReceived) {
        // 后端未保存任何消息（校验/检索失败），移除乐观消息
        messages.value = messages.value.filter((m) => m !== optimisticUser && m !== optimisticAssistant)
      } else {
        optimisticAssistant.status = 'FAILED'
        optimisticAssistant.errorMsg = (error as Error).message || '网络异常，请重试'
      }
      ElMessage.error((error as Error).message || '发送失败，请重试')
    }
  } finally {
    sending.value = false
    abortController = null
    scrollToBottom()
  }
}

function stopGeneration() {
  abortController?.abort()
  // 显式通知后端取消（浏览器 abort 的 TCP 断开在 localhost 下可能不被即时检测），保证落 STOPPED
  if (activeId.value != null) {
    apiCancelGeneration(activeId.value).catch(() => {
      // 取消请求失败不影响客户端中断；后端生成仍受 deadline 约束
    })
  }
}

/** 停止后后端异步落终态，轮询历史直至收敛；超出窗口则本地置 STOPPED，避免光标永久卡住 */
async function reconcileAfterStop(sessionId: number, stoppedMessage: ChatMessageVO) {
  for (let i = 0; i < 15; i++) {
    await new Promise((resolve) => {
      reconcileTimer = window.setTimeout(resolve, 1000)
    })
    if (activeId.value !== sessionId) {
      return
    }
    const latest = await getSessionMessages(sessionId)
    if (!latest.some((m) => m.status === 'GENERATING')) {
      messages.value = latest
      return
    }
  }
  // 后端仍在生成（超出轮询窗口）：本地收敛为 STOPPED，DB 由后端生成 deadline 保证终态一致
  stoppedMessage.status = 'STOPPED'
}

function retry(message: ChatMessageVO) {
  const index = messages.value.indexOf(message)
  const question = index > 0 && messages.value[index - 1].role === 'user'
    ? messages.value[index - 1].content
    : ''
  if (!question) {
    ElMessage.warning('未找到对应问题，请重新输入')
    return
  }
  draft.value = question
  send()
}

function isExpanded(messageId: number, index: number): boolean {
  return expanded.value.has(`${messageId}:${index}`)
}

async function toggleCitation(message: ChatMessageVO, index: number) {
  const key = `${message.id}:${index}`
  const next = new Set(expanded.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
    const citation: CitationVO = message.citations[index]
    if (!checkedDocs.value.has(citation.documentId)) {
      checkedDocs.value.add(citation.documentId)
      try {
        await getDocumentContent(citation.documentId)
      } catch {
        deletedDocs.value.add(citation.documentId)
      }
    }
  }
  expanded.value = next
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadKnowledgeBases()])
  if (sessions.value.length > 0) {
    activeId.value = sessions.value[0].id
    await loadMessages()
  }
})

onBeforeUnmount(() => {
  abortController?.abort()
  if (reconcileTimer != null) {
    window.clearTimeout(reconcileTimer)
  }
})
</script>

<style scoped>
.chat-grid {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 14px;
  height: calc(100vh - 200px);
  min-height: 420px;
}

.sessions {
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.session-item {
  padding: 10px 12px;
  border-radius: var(--sp-radius-sm);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s ease;
}

.session-item:hover {
  background: var(--sp-surface);
}

.session-item.active {
  background: var(--sp-surface-solid);
  border-color: var(--sp-border);
  box-shadow: var(--sp-shadow);
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.session-kb {
  font-size: 11px;
  color: var(--sp-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-delete {
  color: var(--sp-text-3);
}

.chat-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--sp-border);
}

.toolbar-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-title.muted {
  color: var(--sp-text-3);
  font-weight: 400;
}

.toolbar-status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sp-text-2);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.messages.centered {
  align-items: center;
  justify-content: center;
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: var(--sp-radius-sm);
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.message.user .bubble {
  background: var(--sp-grad);
  color: #fff;
}

.message.assistant .bubble {
  background: var(--sp-surface-solid);
  border: 1px solid var(--sp-border);
  white-space: normal;
}

.bubble.failed {
  border-color: rgba(255, 59, 48, 0.35);
}

.markdown :deep(p) {
  margin: 0 0 8px;
}

.markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown :deep(pre) {
  background: var(--sp-bg);
  border-radius: 8px;
  padding: 10px;
  overflow-x: auto;
}

.markdown :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px;
}

.markdown :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.markdown :deep(th),
.markdown :deep(td) {
  border: 1px solid var(--sp-border);
  padding: 4px 8px;
}

.markdown :deep(ul),
.markdown :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}

.cursor {
  display: inline-block;
  animation: blink 1s steps(2) infinite;
  color: var(--sp-primary);
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.msg-error {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #ff3b30;
  font-size: 13px;
}

.msg-stopped {
  margin-top: 6px;
  font-size: 12px;
  color: var(--sp-text-3);
}

.msg-tokens {
  margin-top: 8px;
  font-size: 11px;
  color: var(--sp-text-3);
}

.citations {
  margin-top: 10px;
  border-top: 1px dashed var(--sp-border);
  padding-top: 8px;
}

.citations-head {
  font-size: 12px;
  font-weight: 600;
  color: var(--sp-text-2);
  margin-bottom: 6px;
}

.citation {
  border: 1px solid var(--sp-border);
  border-radius: 8px;
  padding: 6px 10px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.citation:hover {
  border-color: var(--sp-primary);
}

.citation-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.citation-index {
  color: var(--sp-primary);
  font-weight: 600;
}

.citation-doc {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 40%;
}

.citation-page {
  color: var(--sp-text-2);
}

.citation-score {
  margin-left: auto;
  color: var(--sp-text-2);
}

.citation-arrow {
  color: var(--sp-text-3);
  transition: transform 0.15s ease;
}

.citation.expanded .citation-arrow {
  transform: rotate(180deg);
}

.citation-body {
  margin-top: 8px;
  cursor: default;
}

.citation-deleted {
  font-size: 12px;
  color: #ff9500;
  margin-bottom: 6px;
}

.citation-original {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--sp-text-2);
  background: var(--sp-bg);
  border-radius: 8px;
  padding: 8px 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow-y: auto;
}

.citation-foot {
  margin-top: 6px;
  font-size: 11px;
  color: var(--sp-text-3);
}

.composer {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid var(--sp-border);
  align-items: flex-end;
}

.dialog-tip {
  font-size: 12px;
  color: var(--sp-text-2);
  margin-top: 4px;
}

@media (max-width: 900px) {
  .chat-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .sessions {
    max-height: 220px;
  }

  .chat-main {
    min-height: 420px;
  }
}
</style>