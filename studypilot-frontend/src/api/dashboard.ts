import http from '@/utils/request'
import type {
  DashboardOverviewVO,
  DocumentStatisticsVO,
  LearningStatisticsVO,
  QuizStatisticsVO
} from '@/types/api'

export function getOverview() {
  return http.get<DashboardOverviewVO>('/dashboard/overview')
}

export function getDocumentStatistics() {
  return http.get<DocumentStatisticsVO>('/dashboard/document-statistics')
}

export function getLearningStatistics() {
  return http.get<LearningStatisticsVO>('/dashboard/learning-statistics')
}

export function getQuizStatistics() {
  return http.get<QuizStatisticsVO>('/dashboard/quiz-statistics')
}