import http from '@/utils/request'
import type {
  GenerateStatusVO,
  StudyPlanCreateDTO,
  StudyPlanProgressVO,
  StudyPlanUpdateDTO,
  StudyPlanVO,
  StudyTaskUpdateDTO,
  StudyTaskVO
} from '@/types/api'

export function createPlan(dto: StudyPlanCreateDTO) {
  return http.post<StudyPlanVO>('/study-plans', dto)
}

export function listPlans() {
  return http.get<StudyPlanVO[]>('/study-plans')
}

export function getPlan(id: number) {
  return http.get<StudyPlanVO>(`/study-plans/${id}`)
}

export function updatePlan(id: number, dto: StudyPlanUpdateDTO) {
  return http.put<StudyPlanVO>(`/study-plans/${id}`, dto)
}

export function deletePlan(id: number) {
  return http.delete<void>(`/study-plans/${id}`)
}

export function generatePlan(id: number) {
  return http.post<GenerateStatusVO>(`/study-plans/${id}/generate`)
}

export function getPlanGenerateStatus(id: number) {
  return http.get<GenerateStatusVO>(`/study-plans/${id}/generate-status`)
}

export function listPlanTasks(id: number) {
  return http.get<StudyTaskVO[]>(`/study-plans/${id}/tasks`)
}

export function getPlanProgress(id: number) {
  return http.get<StudyPlanProgressVO>(`/study-plans/${id}/progress`)
}

export function updateTaskStatus(id: number, status: number) {
  return http.put<StudyTaskVO>(`/study-tasks/${id}/status`, { status })
}

export function updateTask(id: number, dto: StudyTaskUpdateDTO) {
  return http.put<StudyTaskVO>(`/study-tasks/${id}`, dto)
}