<template>
  <div class="chat">
    <PageHeader
      title="AI 智能问答"
      subtitle="基于知识库的 RAG 多轮问答，回答附带引用文档与原文片段"
      milestone="M5"
    />
    <div class="chat-grid">
      <div class="sessions glass">
        <div class="panel-head">
          <span>会话列表</span>
          <el-button type="primary" size="small" disabled>新会话</el-button>
        </div>
        <EmptyState
          :icon="ChatDotRound"
          title="暂无会话"
          description="M5 开放"
          compact
        />
      </div>

      <div class="chat-main glass">
        <div class="messages">
          <EmptyState
            :icon="ChatDotRound"
            title="开始一段基于文献的对话"
            description="选择知识库后提问，AI 将检索相关切片并流式作答，每条回答附带引用文档、页码与原文片段。当前为界面结构预览，问答能力将在 M5 接入。"
          />
        </div>
        <div class="composer">
          <el-input
            placeholder="问答功能将在 M5 开放，当前输入不可用"
            disabled
            :prefix-icon="ChatDotRound"
          />
          <el-button type="primary" disabled :icon="Promotion">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ChatDotRound, Promotion } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
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
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.chat-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messages {
  flex: 1;
  overflow-y: auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.composer {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid var(--sp-border);
}

@media (max-width: 900px) {
  .chat-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .sessions {
    display: none;
  }

  .chat-main {
    min-height: 420px;
  }
}
</style>