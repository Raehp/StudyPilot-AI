import http from '@/utils/request'
import type { DocumentContentVO, DocumentVO, PageResult, ParseStatusVO } from '@/types/api'

export function uploadDocument(
  file: File,
  kbId: number | null,
  onProgress?: (percent: number) => void
): Promise<DocumentVO> {
  const formData = new FormData()
  formData.append('file', file)
  if (kbId !== null && kbId !== 0) {
    formData.append('kbId', String(kbId))
  }
  return http.upload<DocumentVO>('/documents/upload', formData, onProgress)
}

export function pageDocuments(
  kbId: number | null,
  page: number,
  size: number
): Promise<PageResult<DocumentVO>> {
  const params: Record<string, unknown> = { page, size }
  if (kbId !== null) {
    params.kbId = kbId
  }
  return http.get<PageResult<DocumentVO>>('/documents', params)
}

export function getDocument(id: number): Promise<DocumentVO> {
  return http.get<DocumentVO>(`/documents/${id}`)
}

export function deleteDocument(id: number): Promise<void> {
  return http.delete<void>(`/documents/${id}`)
}

export function getParseStatus(id: number): Promise<ParseStatusVO> {
  return http.get<ParseStatusVO>(`/documents/${id}/parse-status`)
}

export function triggerParse(id: number): Promise<void> {
  return http.post<void>(`/documents/${id}/parse`)
}

export function getDocumentContent(id: number): Promise<DocumentContentVO> {
  return http.get<DocumentContentVO>(`/documents/${id}/content`)
}