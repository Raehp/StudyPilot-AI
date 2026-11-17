import http from '@/utils/request'
import type { VectorSearchResultVO, VectorStatusVO } from '@/types/api'

export function vectorizeDocument(id: number, reprocess = false): Promise<VectorStatusVO> {
  return http.post<VectorStatusVO>(`/documents/${id}/vectorize`, { reprocess })
}

export function getVectorStatus(id: number): Promise<VectorStatusVO> {
  return http.get<VectorStatusVO>(`/documents/${id}/vector-status`)
}

export function searchKnowledgeBase(
  kbId: number,
  query: string,
  topK = 5
): Promise<VectorSearchResultVO[]> {
  return http.post<VectorSearchResultVO[]>(`/knowledge-bases/${kbId}/search`, { query, topK })
}