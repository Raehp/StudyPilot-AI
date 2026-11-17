import http from '@/utils/request'
import type {
  GenerateStatusVO,
  QuizDetailVO,
  QuizGenerateDTO,
  QuizListItemVO,
  QuizResultVO,
  QuizSubmitDTO,
  WrongAnswerVO
} from '@/types/api'

export function generateQuiz(dto: QuizGenerateDTO) {
  return http.post<GenerateStatusVO>('/quizzes/generate', dto)
}

export function listQuizzes() {
  return http.get<QuizListItemVO[]>('/quizzes')
}

export function getQuiz(id: number) {
  return http.get<QuizDetailVO>(`/quizzes/${id}`)
}

export function getQuizGenerateStatus(id: number) {
  return http.get<GenerateStatusVO>(`/quizzes/${id}/generate-status`)
}

export function submitQuiz(id: number, dto: QuizSubmitDTO) {
  return http.post<QuizResultVO>(`/quizzes/${id}/submit`, dto)
}

export function getQuizResult(id: number) {
  return http.get<QuizResultVO>(`/quizzes/${id}/result`)
}

export function listWrongAnswers() {
  return http.get<WrongAnswerVO[]>('/quizzes/wrong-answers')
}

export function deleteQuiz(id: number) {
  return http.delete<void>(`/quizzes/${id}`)
}