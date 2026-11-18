import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'

export type Theme = 'light' | 'dark'

export interface GlassSettings {
  /** 卡片透明度（内容卡片表面不透明度 0.08–0.92，导航栏/侧边栏/弹层不受影响） */
  alpha: number
  /** 背景模糊半径 px */
  blur: number
  /** 镜面高光强度 0–1.5 */
  specular: number
  /** 背景饱和 % */
  saturation: number
  /** 氛围光色相偏移 deg */
  hue: number
  /** 氛围光流动动画 */
  aurora: boolean
  /** 颗粒质感 */
  grain: boolean
  /** 边缘折射强度（透镜位移幅度，0 关闭折射） */
  refraction: number
  /** 折射边带宽度（10–50，相对短边归一化后 /100） */
  lensEdge: number
  /** 照片底图（折射演示承托） */
  photo: boolean
}

const THEME_KEY = 'studypilot_theme'
const GLASS_KEY = 'studypilot_glass'

export const DEFAULT_GLASS: GlassSettings = {
  alpha: 0.72,
  blur: 20,
  specular: 1,
  saturation: 180,
  hue: 0,
  aurora: true,
  grain: true,
  refraction: 70,
  lensEdge: 22,
  photo: true
}

function loadGlass(): GlassSettings {
  try {
    const raw = localStorage.getItem(GLASS_KEY)
    if (!raw) return { ...DEFAULT_GLASS }
    const parsed = JSON.parse(raw) as Partial<GlassSettings> & { aberration?: number }
    delete parsed.aberration
    return { ...DEFAULT_GLASS, ...parsed }
  } catch {
    return { ...DEFAULT_GLASS }
  }
}

export const useAppStore = defineStore('app', () => {
  const stored = localStorage.getItem(THEME_KEY)
  const theme = ref<Theme>(stored === 'dark' ? 'dark' : 'light')
  const mobileNavOpen = ref(false)
  const glass = reactive<GlassSettings>(loadGlass())

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

  function applyGlass() {
    const root = document.documentElement
    root.style.setProperty('--lg-alpha', String(glass.alpha))
    root.style.setProperty('--lg-blur', `${glass.blur}px`)
    root.style.setProperty('--lg-specular', String(glass.specular))
    root.style.setProperty('--lg-sat', `${glass.saturation}%`)
    root.style.setProperty('--lg-hue', `${glass.hue}deg`)
    root.style.setProperty('--lg-grain', glass.grain ? '0.05' : '0')
    root.style.setProperty('--lg-aurora', glass.aurora ? '1' : '0')
    root.classList.toggle('lg-still', !glass.aurora)
    root.classList.toggle('lg-photo-off', !glass.photo)
  }

  function setGlass(patch: Partial<GlassSettings>) {
    Object.assign(glass, patch)
    localStorage.setItem(GLASS_KEY, JSON.stringify(glass))
    applyGlass()
  }

  function resetGlass() {
    Object.assign(glass, DEFAULT_GLASS)
    localStorage.setItem(GLASS_KEY, JSON.stringify(glass))
    applyGlass()
  }

  applyTheme()
  applyGlass()

  return { theme, mobileNavOpen, glass, setTheme, toggleTheme, setGlass, resetGlass }
})