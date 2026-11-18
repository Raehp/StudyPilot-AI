<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { createLensEngine, type LensEngine } from '@/utils/liquidLens'

const appStore = useAppStore()
const host = ref<HTMLElement | null>(null)
let engine: LensEngine | null = null

function syncLens() {
  engine?.sync({
    enabled: appStore.glass.refraction > 0,
    strength: appStore.glass.refraction,
    edge: appStore.glass.lensEdge / 100,
    blur: appStore.glass.blur,
    saturation: appStore.glass.saturation
  })
}

onMounted(() => {
  if (host.value) engine = createLensEngine(host.value)
  syncLens()
  startSheen()
  document.addEventListener('transitionrun', onFlightStart)
  document.addEventListener('transitionend', onFlightEnd)
  document.addEventListener('transitioncancel', onFlightEnd)
})

watch(
  () => [
    appStore.glass.refraction,
    appStore.glass.lensEdge,
    appStore.glass.blur,
    appStore.glass.saturation
  ],
  syncLens
)

/* 指针追光：镜面反射斑随指针阻尼流动，仅精确指针且非降低动效偏好时启用 */
let sheenCard: HTMLElement | null = null
let hovering = false
let tx = 50
let ty = 35
let cx = 50
let cy = 35
let raf = 0
let sheenSkip = false

function sheenTick() {
  raf = 0
  if (!sheenCard) return
  if (sheenCard.classList.contains('lg-flight')) return
  cx += (tx - cx) * 0.18
  cy += (ty - cy) * 0.18
  const settled = Math.abs(tx - cx) < 0.1 && Math.abs(ty - cy) < 0.1
  if (settled && !hovering) {
    sheenCard.style.removeProperty('--lg-mx')
    sheenCard.style.removeProperty('--lg-my')
    sheenCard = null
    return
  }
  sheenSkip = !sheenSkip
  if (!sheenSkip) {
    sheenCard.style.setProperty('--lg-mx', `${cx.toFixed(2)}%`)
    sheenCard.style.setProperty('--lg-my', `${cy.toFixed(2)}%`)
  }
  if (!settled) raf = requestAnimationFrame(sheenTick)
}

function onPointerMove(e: PointerEvent) {
  const target = e.target as Element | null
  const card = (target?.closest?.('.glass, .sp-card') as HTMLElement | null) ?? null
  if (card !== sheenCard) {
    if (sheenCard && card) {
      sheenCard.style.removeProperty('--lg-mx')
      sheenCard.style.removeProperty('--lg-my')
    }
    sheenCard = card
    cx = 50
    cy = 35
  }
  hovering = !!card
  if (card) {
    const r = card.getBoundingClientRect()
    tx = ((e.clientX - r.left) / r.width) * 100
    ty = ((e.clientY - r.top) / r.height) * 100
  } else {
    tx = 50
    ty = 35
  }
  if (sheenCard && !raf && !sheenCard.classList.contains('lg-flight')) {
    raf = requestAnimationFrame(sheenTick)
  }
}

function startSheen() {
  if (!matchMedia('(pointer: fine)').matches) return
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
  document.addEventListener('pointermove', onPointerMove, { passive: true })
}

function stopSheen() {
  document.removeEventListener('pointermove', onPointerMove)
  if (raf) cancelAnimationFrame(raf)
  raf = 0
  if (sheenCard) {
    sheenCard.style.removeProperty('--lg-mx')
    sheenCard.style.removeProperty('--lg-my')
    sheenCard = null
  }
}

/* 位移动画期间标记 lg-flight：折射层暂卸为等效磨砂，避免运动期逐帧重算 backdrop */
function flightCard(e: TransitionEvent): HTMLElement | null {
  if (e.propertyName !== 'transform') return null
  const el = e.target as HTMLElement
  if (!el.classList?.contains('glass') && !el.classList?.contains('sp-card')) return null
  return el
}
function onFlightStart(e: TransitionEvent) {
  flightCard(e)?.classList.add('lg-flight')
}
function onFlightEnd(e: TransitionEvent) {
  flightCard(e)?.classList.remove('lg-flight')
}

onBeforeUnmount(() => {
  engine?.destroy()
  engine = null
  document.removeEventListener('transitionrun', onFlightStart)
  document.removeEventListener('transitionend', onFlightEnd)
  document.removeEventListener('transitioncancel', onFlightEnd)
  stopSheen()
})
</script>

<template>
  <div ref="host" class="lg-defs" aria-hidden="true"></div>
</template>

<style scoped>
.lg-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}
</style>