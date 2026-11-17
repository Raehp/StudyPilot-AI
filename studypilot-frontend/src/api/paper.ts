import http from '@/utils/request'
import type { AnalysisStatusVO, PaperAnalysisVO } from '@/types/api'

export function analyzePaper(documentId: number, reanalyze = false) {
  return http.post<AnalysisStatusVO>(`/papers/${documentId}/analyze`, { reanalyze })
}

export function getPaperAnalysis(documentId: number) {
  return http.get<PaperAnalysisVO>(`/papers/${documentId}/analysis`)
}

export function getAnalysisStatus(documentId: number) {
  return http.get<AnalysisStatusVO>(`/papers/${documentId}/analysis-status`)
}