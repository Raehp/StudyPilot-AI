<template>
  <span class="squish-switch-root">
    <button
      ref="btnRef"
      type="button"
      role="switch"
      :aria-checked="on"
      :aria-disabled="disabled || undefined"
      :aria-label="ariaLabel"
      class="squish-switch"
      :data-on="on ? '' : undefined"
      :data-held="dragging ? '' : undefined"
      :style="vars"
      @pointerdown="down"
      @pointermove="move"
      @pointerup="up($event, false)"
      @pointercancel="up($event, true)"
      @pointerenter="enter"
      @pointerleave="swellTarget = 1"
      @keydown.escape="cancelGrip"
      @click="click"
    >
      <span ref="trackRef" class="squish-switch__track">
        <span ref="thumbRef" class="squish-switch__thumb" aria-hidden="true">
          <slot />
        </span>
      </span>
    </button>
  </span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    checked: boolean
    disabled?: boolean
    trackColor?: string
    trackOnColor?: string
    thumbColor?: string
    thumbOnColor?: string
    width?: number
    height?: number
    radius?: number
    speed?: number
    stretch?: number
    hoverScale?: number
    colorDuration?: number
    ariaLabel?: string
  }>(),
  {
    disabled: false,
    trackColor: '#27272a',
    trackOnColor: '#f5f5f5',
    thumbColor: '',
    thumbOnColor: '',
    width: 76,
    height: 38,
    radius: 19,
    speed: 50,
    stretch: 36,
    hoverScale: 1.035,
    colorDuration: 320,
    ariaLabel: undefined
  }
)
const emit = defineEmits<{ (e: 'change', next: boolean): void }>()

const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v))

const inset = computed(() => Math.max(3, Math.round(props.height * 0.11)))
const thumb = computed(() => props.height - inset.value * 2)
const min = computed(() => inset.value)
const max = computed(() => props.width - inset.value - thumb.value)
const mid = computed(() => (min.value + max.value) / 2)
const trackRadius = computed(() => Math.min(props.radius, props.height / 2))
const thumbRadius = computed(() => Math.max(2, trackRadius.value - inset.value))
const on = computed(() => props.checked)

const vars = computed(() => ({
  '--ss-w': `${props.width}px`,
  '--ss-h': `${props.height}px`,
  '--ss-inset': `${inset.value}px`,
  '--ss-thumb': `${thumb.value}px`,
  '--ss-r': `${trackRadius.value}px`,
  '--ss-thumb-r': `${thumbRadius.value}px`,
  '--ss-track': props.trackColor,
  '--ss-track-on': props.trackOnColor,
  '--ss-thumb-color': props.thumbColor || `color-mix(in srgb, ${props.trackOnColor} 19%, ${props.trackColor})`,
  '--ss-thumb-on': props.thumbOnColor || props.trackColor,
  '--ss-fade': `${props.colorDuration}ms`
}))

const btnRef = ref<HTMLButtonElement>()
const trackRef = ref<HTMLSpanElement>()
const thumbRef = ref<HTMLSpanElement>()
const dragging = ref(false)
const swellTarget = ref(1)

const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
const gain = reduce ? 0 : clamp(props.stretch, 0, 100) / 100

interface Spring {
  v: number
  vel: number
}
const x: Spring = { v: on.value ? max.value : min.value, vel: 0 }
const flow: Spring = { v: 0, vel: 0 }
const swell: Spring = { v: 1, vel: 0 }
let prevX = x.v
let raf = 0
let last = 0

function step(s: Spring, target: number, k: number, d: number, m: number, dt: number) {
  const a = (k * (target - s.v) - d * s.vel) / m
  s.vel += a * dt
  s.v += s.vel * dt
}
const stretchOf = (v: number) => 1 + Math.min(0.4, Math.abs(v) / 600) * gain

function render() {
  const el = thumbRef.value
  if (!el) return
  const s = stretchOf(flow.v) * swell.v
  el.style.transform = `translateX(${x.v}px) scale(${s}, ${swell.v / stretchOf(flow.v)})`
}

function frame(now: number) {
  raf = 0
  const dt = last ? Math.min(0.05, (now - last) / 1000) : 1 / 60
  last = now
  const target = on.value ? max.value : min.value
  if (reduce) {
    x.v = dragging.value ? x.v : target
    x.vel = 0
    flow.v = 0
    flow.vel = 0
    swell.v = swellTarget.value
    swell.vel = 0
  } else {
    if (!dragging.value) {
      const k = 170 - (50 - clamp(props.speed, 0, 100)) * 1.1
      step(x, target, k, 21.5, 0.9, dt)
    }
    const vx = (x.v - prevX) / dt
    step(flow, vx, 320, 40, 0.6, dt)
    step(swell, swellTarget.value, 520, 34, 0.6, dt)
  }
  prevX = x.v
  render()
  const settled =
    !dragging.value &&
    Math.abs(x.v - target) < 0.05 &&
    Math.abs(x.vel) < 0.05 &&
    Math.abs(flow.v) < 0.5 &&
    Math.abs(flow.vel) < 0.5 &&
    Math.abs(swell.v - swellTarget.value) < 0.001 &&
    Math.abs(swell.vel) < 0.001
  if (!settled) raf = requestAnimationFrame(frame)
  else last = 0
}
function wake() {
  if (!raf) raf = requestAnimationFrame(frame)
}

interface Grip {
  id: number
  grab: number | null
  moved: boolean
  startX: number
  onAtPress: boolean
  slop: number
}
let grip: Grip | null = null
let skipClick = false

function commit(next: boolean) {
  if (next === on.value) return
  emit('change', next)
}
function localX(clientX: number) {
  const el = trackRef.value
  if (!el) return 0
  const rect = el.getBoundingClientRect()
  const scale = rect.width / (el.offsetWidth || rect.width) || 1
  return (clientX - rect.left) / scale
}
function down(e: PointerEvent) {
  if (props.disabled || grip || e.button !== 0) return
  grip = {
    id: e.pointerId,
    grab: null,
    moved: false,
    startX: e.clientX,
    onAtPress: on.value,
    slop: e.pointerType === 'touch' ? 8 : 4
  }
  try {
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  } catch {
    // 合成事件无活动指针时忽略
  }
  dragging.value = true
  wake()
}
function move(e: PointerEvent) {
  const g = grip
  if (!g || g.id !== e.pointerId) return
  const lx = localX(e.clientX)
  if (g.grab === null) {
    g.grab = lx - x.v
    return
  }
  if (!g.moved && Math.abs(e.clientX - g.startX) > g.slop) g.moved = true
  if (!g.moved) return
  const nx = clamp(lx - g.grab, min.value, max.value)
  x.v = nx
  x.vel = 0
  commit(nx > mid.value)
  wake()
}
function up(e: PointerEvent, cancelled: boolean) {
  const g = grip
  if (!g || g.id !== e.pointerId) return
  grip = null
  try {
    ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
  } catch {
    // 同上
  }
  if (cancelled) commit(g.onAtPress)
  else if (!g.moved) commit(!on.value)
  skipClick = true
  setTimeout(() => {
    skipClick = false
  }, 0)
  dragging.value = false
  wake()
}
function cancelGrip(e: KeyboardEvent) {
  if (grip) up({ pointerId: grip.id, currentTarget: e.currentTarget } as unknown as PointerEvent, true)
}
function enter(e: PointerEvent) {
  if (e.pointerType === 'mouse' && !props.disabled) swellTarget.value = props.hoverScale
  wake()
}
function click() {
  if (skipClick) {
    skipClick = false
    return
  }
  if (!props.disabled) commit(!on.value)
}

watch([on, min, max, () => props.speed], wake)
onMounted(() => {
  render()
  wake()
})
onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf)
})
</script>

<style scoped>
.squish-switch-root {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.squish-switch {
  position: relative;
  display: inline-block;
  margin: 0;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  outline: none;
  touch-action: pan-y;
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
  -webkit-tap-highlight-color: transparent;
}
.squish-switch::after {
  content: '';
  position: absolute;
  inset: -8px;
}
.squish-switch[data-held] {
  cursor: grabbing;
}
.squish-switch[aria-disabled='true'] {
  cursor: not-allowed;
  opacity: 0.5;
}
.squish-switch:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--ss-track-on) 55%, transparent);
  outline-offset: 3px;
  border-radius: var(--ss-r);
}
.squish-switch__track {
  position: relative;
  display: block;
  width: var(--ss-w);
  height: var(--ss-h);
  border-radius: var(--ss-r);
  background: var(--ss-track);
  transition: background-color var(--ss-fade) ease;
}
.squish-switch[data-on] .squish-switch__track {
  background: var(--ss-track-on);
}
.squish-switch__thumb {
  position: absolute;
  top: var(--ss-inset);
  left: 0;
  width: var(--ss-thumb);
  height: var(--ss-thumb);
  border-radius: var(--ss-thumb-r);
  background: var(--ss-thumb-color);
  transition: background-color var(--ss-fade) ease;
  will-change: transform;
}
.squish-switch[data-on] .squish-switch__thumb {
  background: var(--ss-thumb-on);
}
@media (prefers-reduced-motion: reduce) {
  .squish-switch__track,
  .squish-switch__thumb {
    transition-duration: 1ms;
  }
}
</style>