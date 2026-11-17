import http from '@/utils/request'
import type { KnowledgeBaseDTO, KnowledgeBaseVO } from '@/types/api'

export function listKnowledgeBases(): Promise<KnowledgeBaseVO[]> {
  return http.get<KnowledgeBaseVO[]>('/knowledge-bases')
}

export function getKnowledgeBase(id: number): Promise<KnowledgeBaseVO> {
  return http.get<KnowledgeBaseVO>(`/knowledge-bases/${id}`)
}

export function createKnowledgeBase(data: KnowledgeBaseDTO): Promise<KnowledgeBaseVO> {
  return http.post<KnowledgeBaseVO>('/knowledge-bases', data)
}

export function updateKnowledgeBase(id: number, data: KnowledgeBaseDTO): Promise<KnowledgeBaseVO> {
  return http.put<KnowledgeBaseVO>(`/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBase(id: number): Promise<void> {
  return http.delete<void>(`/knowledge-bases/${id}`)
}

export function addDocumentToKb(kbId: number, documentId: number): Promise<void> {
  return http.post<void>(`/knowledge-bases/${kbId}/documents`, { documentId })
}

export function removeDocumentFromKb(kbId: number, documentId: number): Promise<void> {
  return http.delete<void>(`/knowledge-bases/${kbId}/documents/${documentId}`)
}