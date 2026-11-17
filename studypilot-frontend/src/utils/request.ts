import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearToken } from './auth'
import type { Result } from '@/types/api'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const res = response.data as Result<unknown>
    if (res.code === 200) {
      return res.data as never
    }
    if (res.code === 401) {
      clearToken()
      ElMessage.warning('登录已过期，请重新登录')
      window.location.href = '/login'
      return Promise.reject(new Error(res.message))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    const message = error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请检查后端服务'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

const http = {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    return service.get(url, { params }) as Promise<T>
  },
  post<T>(url: string, data?: unknown): Promise<T> {
    return service.post(url, data) as Promise<T>
  },
  put<T>(url: string, data?: unknown): Promise<T> {
    return service.put(url, data) as Promise<T>
  },
  delete<T>(url: string): Promise<T> {
    return service.delete(url) as Promise<T>
  },
  upload<T>(url: string, formData: FormData, onProgress?: (percent: number) => void): Promise<T> {
    return service.post(url, formData, {
      timeout: 120000,
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      }
    }) as Promise<T>
  }
}

export default http