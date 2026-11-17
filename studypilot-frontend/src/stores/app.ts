import { defineStore } from 'pinia'
import { ref } from 'vue'

export type Theme = 'light' | 'dark'

const THEME_KEY = 'studypilot_theme'

export const useAppStore = defineStore('app', () => {
  const stored = localStorage.getItem(THEME_KEY)
  const theme = ref<Theme>(stored === 'dark' ? 'dark' : 'light')
  const mobileNavOpen = ref(false)

  function applyTheme() {
    document.documentElement.dataset.theme = theme.value
    document.documentElement.classList.toggle('dark', theme.value === 'dark')
  }

  function setTheme(next: Theme) {
    theme.value = next
    localStorage.setItem(THEME_KEY, next)
    applyTheme()
  }

  function toggleTheme() {
    setTheme(theme.value === 'light' ? 'dark' : 'light')
  }

  applyTheme()

  return { theme, mobileNavOpen, setTheme, toggleTheme }
})