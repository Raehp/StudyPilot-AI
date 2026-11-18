<template>
  <div ref="rootRef" class="liquid-glass" :data-mode="mode" :data-refract="refractOn" :style="rootStyle">
    <span v-if="filterSvg" aria-hidden="true" class="lg-defs" v-html="filterSvg"></span>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  buildFilterSvg,
  generateDisplacementBlobUrl,
  isSafariLike,
  prefersReducedTransparency,
  supportsUrlBackdropFilter
} from '@/utils/snellGlass'

const props = withDefaults(
  defineProps<{
    radius?: number
    bevelDepth?: number
    scale?: number
    aberration?: number
    frost?: number
    tint?: number
  }>(),
  {
    radius: 999,
    bevelDepth: 14,
    scale: 48,
    aberration: 4,
    frost: 0.4,
    tint: 0.12
  }
)

let uidCounter = 0
const uid = `v${++uidCounter}`

const rootRef = ref<HTMLDivElement>()
const mode = ref<'pending' | 'simple' | 'opaque'>('pending')
const size = ref({ w: 0, h: 0 })
const filterId = ref('')
const filterSvg = ref('')
let blobUrl: string | null = null
let buildToken = 0
let ro: ResizeObserver | null = null
let resizeTimer = 0

const refractOn = computed(() => mode.value === 'pending' && !!filterSvg.value)

const backdrop = computed(() => {
  if (mode.value === 'opaque') return 'none'
  if (mode.value === 'simple') return 'blur(20px) saturate(1.4)'
  return filterId.value
    ? `url(#${filterId.value}) blur(0.5px) saturate(1.85) brightness(1.02)`
    : 'blur(10px) saturate(1.6) brightness(1.04)'
})

const rootStyle = computed(() => ({
  borderRadius: `${props.radius}px`,
  backgroundColor: mode.value === 'pending' ? `rgb(255 255 255 / ${props.tint})` : undefined,
  backdropFilter: backdrop.value,
  WebkitBackdropFilter: backdrop.value
}))

async function rebuild() {
  if (mode.value !== 'pending' || size.value.w <= 1 || size.value.h <= 1) return
  const token = ++buildToken
  const { w, h } = size.value
  const mapUrl = await generateDisplacementBlobUrl({
    width: w,
    height: h,
    radius: Math.min(props.radius, w / 2, h / 2),
    bevelDepth: Math.min(props.bevelDepth, Math.min(w, h) / 2),
    dpr: Math.min(window.devicePixelRatio || 1, 2)
  })
  await new Promise<void>((resolve) => {
    const img = new Image()
    img.onload = img.onerror = () => resolve()
    img.src = mapUrl
  })
  if (token !== buildToken) {
    URL.revokeObjectURL(mapUrl)
    return
  }
  if (blobUrl) URL.revokeObjectURL(blobUrl)
  blobUrl = mapUrl
  // 每次重建换新 id：浏览器按 id 缓存滤镜输出
  const id = `lg-${uid}-${w}x${h}-${Date.now().toString(36)}`
  filterId.value = id
  filterSvg.value = buildFilterSvg({
    id,
    mapUrl,
    width: w,
    height: h,
    scale: props.scale,
    aberration: props.aberration,
    frost: props.frost
  })
}

watch(
  [size, () => props.radius, () => props.bevelDepth, () => props.scale, () => props.aberration, () => props.frost],
  () => {
    void rebuild()
  }
)

onMounted(() => {
  if (prefersReducedTransparency()) mode.value = 'opaque'
  else if (isSafariLike() || !supportsUrlBackdropFilter()) mode.value = 'simple'
  const el = rootRef.value
  if (!el || mode.value !== 'pending') return
  ro = new ResizeObserver(([entry]) => {
    if (!entry) return
    const box = entry.borderBoxSize?.[0]
    const width = box?.inlineSize ?? entry.contentRect.width
    const height = box?.blockSize ?? entry.contentRect.height
    const w = Math.max(1, Math.round(width))
    const h = Math.max(1, Math.round(height))
    if (w === size.value.w && h === size.value.h) return
    window.clearTimeout(resizeTimer)
    resizeTimer = window.setTimeout(() => {
      size.value = { w, h }
    }, 120)
  })
  ro.observe(el)
})

onBeforeUnmount(() => {
  buildToken++
  window.clearTimeout(resizeTimer)
  ro?.disconnect()
  if (blobUrl) URL.revokeObjectURL(blobUrl)
})
</script>

<style scoped>
.liquid-glass {
  position: relative;
  overflow: hidden;
}
.liquid-glass[data-mode='simple'] {
  background: rgba(255, 255, 255, 0.7);
}
html.dark .liquid-glass[data-mode='simple'] {
  background: rgba(24, 24, 28, 0.6);
}
.liquid-glass[data-mode='opaque'] {
  background: #ffffff;
}
html.dark .liquid-glass[data-mode='opaque'] {
  background: #17171c;
}
.lg-defs {
  pointer-events: none;
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}
</style>