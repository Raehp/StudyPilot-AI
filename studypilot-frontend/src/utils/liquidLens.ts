/**
 * 静态透镜引擎 —— 参考 shuding/liquid-glass（https://github.com/shuding/liquid-glass）
 *
 * 与逐帧重算的方案不同：位移贴图只在「尺寸 / 圆角 / 折射参数」变化时用 Canvas 生成一次，
 * 编码为 R/G 通道后经 feImage 静态引用 + 单个 feDisplacementMap 完成边缘折射。
 * 静止与背景不变时浏览器缓存滤镜结果，无逐帧 JS、无 WebGL、无三通道色散链。
 */

export interface LensOptions {
  enabled: boolean
  /** 折射强度 0–100 */
  strength: number
  /** 折射边带宽度（相对短边归一化，0.1–0.5） */
  edge: number
  /** 磨砂半径 px */
  blur: number
  /** 背景饱和 % */
  saturation: number
}

export interface LensEngine {
  sync(opts: LensOptions): void
  destroy(): void
}

const PHOTO_CLASSES = ['auth-card', 'welcome', 'preview-tile']
const PHOTO_BLUR_FACTOR = 0.15
const MAP_MAX_SIDE = 192
const SELECTOR = '.glass, .sp-card'

function smoothStep(a: number, b: number, t: number): number {
  const x = Math.max(0, Math.min(1, (t - a) / (b - a)))
  return x * x * (3 - 2 * x)
}

function roundedRectSDF(x: number, y: number, halfW: number, halfH: number, r: number): number {
  const qx = Math.abs(x) - halfW + r
  const qy = Math.abs(y) - halfH + r
  const inner = Math.min(Math.max(qx, qy), 0)
  const ox = Math.max(qx, 0)
  const oy = Math.max(qy, 0)
  return inner + Math.sqrt(ox * ox + oy * oy) - r
}

interface LensMap {
  dataUrl: string
  scale: number
}

/** 生成圆角透镜位移贴图：中心恒等采样，边缘带把背景向中心牵引形成弯折 */
function generateLensMap(w: number, h: number, radius: number, strength: number, edge: number): LensMap {
  const resScale = Math.min(1, MAP_MAX_SIDE / Math.max(w, h))
  const mw = Math.max(2, Math.round(w * resScale))
  const mh = Math.max(2, Math.round(h * resScale))
  const data = new Uint8ClampedArray(mw * mh * 4)

  const m = Math.min(w, h)
  const halfW = w / (2 * m)
  const halfH = h / (2 * m)
  const r = Math.min(radius / m, halfW, halfH)
  const band = Math.max(0.02, edge)
  const pullMax = (strength / 100) * 0.6

  const dxs = new Float32Array(mw * mh)
  const dys = new Float32Array(mw * mh)
  let max = 0

  for (let y = 0; y < mh; y++) {
    const iy = (y + 0.5) / mh - 0.5
    for (let x = 0; x < mw; x++) {
      const ix = (x + 0.5) / mw - 0.5
      const d = roundedRectSDF((ix * w) / m, (iy * h) / m, halfW, halfH, r)
      const rim = smoothStep(-band, 0, d)
      const pull = pullMax * rim
      const i = y * mw + x
      // 位移按短边缩放：若按各自边长缩放，宽横幅的边缘横向偏移会达数百 px，把背景拉成条纹
      const dx = -ix * pull * m
      const dy = -iy * pull * m
      dxs[i] = dx
      dys[i] = dy
      const ax = Math.abs(dx)
      const ay = Math.abs(dy)
      if (ax > max) max = ax
      if (ay > max) max = ay
    }
  }

  if (max < 0.5) return { dataUrl: '', scale: 0 }

  for (let i = 0; i < mw * mh; i++) {
    const p = i * 4
    data[p] = (dxs[i] / max) * 127.5 + 127.5
    data[p + 1] = (dys[i] / max) * 127.5 + 127.5
    data[p + 2] = 0
    data[p + 3] = 255
  }

  const canvas = document.createElement('canvas')
  canvas.width = mw
  canvas.height = mh
  const ctx = canvas.getContext('2d')
  if (!ctx) return { dataUrl: '', scale: 0 }
  ctx.putImageData(new ImageData(data, mw, mh), 0, 0)
  return { dataUrl: canvas.toDataURL('image/png'), scale: max }
}

function detectSupport(): boolean {
  const ua = navigator.userAgent.toLowerCase()
  const isFirefox = ua.includes('firefox')
  const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent)
  return (
    !isFirefox &&
    !isSafari &&
    typeof CSS !== 'undefined' &&
    CSS.supports('backdrop-filter', 'url(#a)')
  )
}

interface FilterEntry {
  id: string
  node: SVGFilterElement
  uses: number
}

export function createLensEngine(host: HTMLElement): LensEngine {
  const supported = detectSupport()
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  svg.setAttribute('width', '0')
  svg.setAttribute('height', '0')
  svg.setAttribute('aria-hidden', 'true')
  svg.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden;pointer-events:none'
  const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs')
  svg.appendChild(defs)
  host.appendChild(svg)

  const filters = new Map<string, FilterEntry>()
  const bound = new Map<HTMLElement, string>()
  let opts: LensOptions = { enabled: false, strength: 0, edge: 0.22, blur: 20, saturation: 180 }
  let seq = 0
  let scanRaf = 0
  let destroyed = false

  const resizeObserver = new ResizeObserver(() => scheduleScan())
  const mutationObserver = new MutationObserver(() => scheduleScan())

  function ensureFilter(w: number, h: number, radius: number, key: string): FilterEntry | null {
    const cached = filters.get(key)
    if (cached) return cached
    const map = generateLensMap(w, h, radius, opts.strength, opts.edge)
    if (!map.dataUrl) return null

    const id = `sp-lens-${++seq}`
    const filter = document.createElementNS('http://www.w3.org/2000/svg', 'filter')
    filter.setAttribute('id', id)
    filter.setAttribute('filterUnits', 'userSpaceOnUse')
    filter.setAttribute('color-interpolation-filters', 'sRGB')
    filter.setAttribute('x', '0')
    filter.setAttribute('y', '0')
    filter.setAttribute('width', String(w))
    filter.setAttribute('height', String(h))

    const feImage = document.createElementNS('http://www.w3.org/2000/svg', 'feImage')
    feImage.setAttribute('result', 'lensMap')
    feImage.setAttribute('width', String(w))
    feImage.setAttribute('height', String(h))
    feImage.setAttribute('preserveAspectRatio', 'none')
    feImage.setAttribute('href', map.dataUrl)
    feImage.setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', map.dataUrl)

    const feDisplacement = document.createElementNS('http://www.w3.org/2000/svg', 'feDisplacementMap')
    feDisplacement.setAttribute('in', 'SourceGraphic')
    feDisplacement.setAttribute('in2', 'lensMap')
    feDisplacement.setAttribute('xChannelSelector', 'R')
    feDisplacement.setAttribute('yChannelSelector', 'G')
    feDisplacement.setAttribute('scale', map.scale.toFixed(2))

    filter.appendChild(feImage)
    filter.appendChild(feDisplacement)
    defs.appendChild(filter)

    const entry: FilterEntry = { id, node: filter, uses: 0 }
    filters.set(key, entry)
    return entry
  }

  function unbind(el: HTMLElement) {
    if (bound.has(el)) {
      bound.delete(el)
      el.style.removeProperty('--lg-lens-backdrop')
      resizeObserver.unobserve(el)
    }
  }

  function scan() {
    scanRaf = 0
    if (destroyed) return
    const active = supported && opts.enabled && opts.strength > 0
    document.documentElement.classList.toggle('lg-lens-on', active)

    for (const entry of filters.values()) entry.uses = 0
    const els = Array.from(document.querySelectorAll<HTMLElement>(SELECTOR))
    const writes: { el: HTMLElement; value: string | null; key: string }[] = []

    for (const el of els) {
      if (!active) {
        unbind(el)
        continue
      }
      const w = el.offsetWidth
      const h = el.offsetHeight
      if (!w || !h) continue
      const radius = parseFloat(getComputedStyle(el).borderTopLeftRadius) || 0
      const sizeKey = `${w}x${h}x${Math.round(radius)}`
      const key = `${sizeKey}|${opts.strength}|${opts.edge.toFixed(3)}`
      const entry = ensureFilter(w, h, radius, key)
      if (!entry) continue
      entry.uses++
      const isPhoto = PHOTO_CLASSES.some((c) => el.classList.contains(c))
      const blurScale = parseFloat(el.dataset.lensBlurScale || '') || (isPhoto ? PHOTO_BLUR_FACTOR : 1)
      const blur = opts.blur * blurScale
      const value = `url(#${entry.id}) saturate(${opts.saturation}%) blur(${blur.toFixed(2)}px)`
      writes.push({ el, value, key })
    }

    for (const { el, value, key } of writes) {
      if (bound.get(el) !== key) {
        bound.set(el, key)
        resizeObserver.observe(el)
      }
      if (value) el.style.setProperty('--lg-lens-backdrop', value)
    }
    for (const el of Array.from(bound.keys())) {
      if (!els.includes(el)) unbind(el)
    }
    for (const [key, entry] of Array.from(filters.entries())) {
      if (entry.uses === 0) {
        entry.node.remove()
        filters.delete(key)
      }
    }
  }

  function scheduleScan() {
    if (!scanRaf && !destroyed) scanRaf = requestAnimationFrame(scan)
  }

  mutationObserver.observe(document.body, { childList: true, subtree: true })

  return {
    sync(next: LensOptions) {
      const shapeChanged = next.strength !== opts.strength || next.edge !== opts.edge
      opts = { ...next }
      if (shapeChanged) {
        for (const entry of filters.values()) entry.node.remove()
        filters.clear()
        bound.clear()
      }
      scheduleScan()
    },
    destroy() {
      destroyed = true
      if (scanRaf) cancelAnimationFrame(scanRaf)
      mutationObserver.disconnect()
      resizeObserver.disconnect()
      for (const el of bound.keys()) el.style.removeProperty('--lg-lens-backdrop')
      bound.clear()
      filters.clear()
      svg.remove()
      document.documentElement.classList.remove('lg-lens-on')
    }
  }
}