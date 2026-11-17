import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, logout as logoutApi, fetchMe } from '@/api/auth'
import { getToken, setToken, clearToken } from '@/utils/auth'
import type { LoginDTO, RegisterDTO, UserVO } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<UserVO | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(dto: LoginDTO) {
    const data = await loginApi(dto)
    token.value = data.token
    userInfo.value = data.user
    setToken(data.token)
  }

  async function register(dto: RegisterDTO) {
    const data = await registerApi(dto)
    token.value = data.token
    userInfo.value = data.user
    setToken(data.token)
  }

  async function loadUser() {
    userInfo.value = await fetchMe()
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      token.value = ''
      userInfo.value = null
      clearToken()
    }
  }

  return { token, userInfo, isLoggedIn, login, register, loadUser, logout }
})