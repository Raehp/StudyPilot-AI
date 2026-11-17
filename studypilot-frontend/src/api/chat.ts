import http from '@/utils/request'
import { getToken, clearToken } from '@/utils/auth'
import type { ChatMessageVO, ChatStreamMeta, ConversationVO } from '@/types/api'

export function listSessions(): Promise<ConversationVO[]> {
  return http.get<ConversationVO[]>('/chat/sessions')
}

export function createSession(kbId: number, title?: string): Promise<ConversationVO> {
  return http.post<ConversationVO>('/chat/sessions', { kbId, title: title || undefined })
}

export function getSessionMessages(id: number): Promise<ChatMessageVO[]> {
  return http.get<ChatMessageVO[]>(`/chat/sessions/${id}/messages`)
}

export function deleteSession(id: number): Promise<void> {
  return http.delete<void>(`/chat/sessions/${id}`)
}

export function cancelGeneration(id: number): Promise<void> {
  return http.post<void>(`/chat/sessions/${id}/cancel`)
}

export interface StreamHandlers {
  onMeta: (meta: ChatStreamMeta) => void
  onDelta: (text: string) => void
  onDone: (message: ChatMessageVO) => void
  onError: (message: string) => void
}

/**
 * fetch + ReadableStream 消费 SSE（JWT 走 Authorization 头，绝不放入 URL）。
 * 业务错误（非 SSE 响应）以 Error 抛出；中断由调用方 AbortController 控制。
 */
export async function sendMessageStream(
  sessionId: number,
  body: { content: string; topK?: number; minScore?: number },
  handlers: StreamHandlers,
  signal: AbortSignal
): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  const resp = await fetch(`${base}/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      Accept: 'text/event-stream, application/json'
    },
    body: JSON.stringify(body),
    signal
  })
  if (resp.status === 401) {
    clearToken()
    window.location.href = '/login'
    throw new Error('登录已过期，请重新登录')
  }
  const contentType = resp.headers.get('content-type') || ''
  if (!resp.ok || !contentType.includes('text/event-stream')) {
    let message = '请求失败，请稍后重试'
    try {
      const data = await resp.json()
      if (data && typeof data.message === 'string' && data.message) {
        message = data.message
      }
    } catch {
      // 非 JSON 错误体
    }
    throw new Error(message)
  }
  if (!resp.body) {
    throw new Error('当前浏览器不支持流式响应')
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    let index: number
    while ((index = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, index).replace(/\r$/, '')
      buffer = buffer.slice(index + 1)
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const payload = line.slice(5).trim()
        if (!payload) {
          continue
        }
        let data: unknown
        try {
          data = JSON.parse(payload)
        } catch {
          continue
        }
        if (eventName === 'meta') {
          handlers.onMeta(data as ChatStreamMeta)
        } else if (eventName === 'delta') {
          handlers.onDelta((data as { content?: string }).content ?? '')
        } else if (eventName === 'done') {
          handlers.onDone(data as ChatMessageVO)
        } else if (eventName === 'error') {
          handlers.onError((data as { message?: string }).message ?? 'AI 服务异常，请稍后重试')
        }
      } else if (line === '') {
        eventName = 'message'
      }
    }
  }
}