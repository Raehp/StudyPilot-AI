import http from '@/utils/request'
import type { ChangePasswordDTO, LoginDTO, LoginVO, RegisterDTO, UserVO } from '@/types/api'

export function login(data: LoginDTO): Promise<LoginVO> {
  return http.post<LoginVO>('/auth/login', data)
}

export function register(data: RegisterDTO): Promise<LoginVO> {
  return http.post<LoginVO>('/auth/register', data)
}

export function logout(): Promise<void> {
  return http.post<void>('/auth/logout')
}

export function changePassword(data: ChangePasswordDTO): Promise<void> {
  return http.post<void>('/users/me/password', data)
}

export function fetchMe(): Promise<UserVO> {
  return http.get<UserVO>('/users/me')
}