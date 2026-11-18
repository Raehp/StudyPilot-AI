<template>
  <div class="showcase">
    <div class="orb orb-a" aria-hidden="true"></div>
    <div class="orb orb-b" aria-hidden="true"></div>
    <div class="orb orb-c" aria-hidden="true"></div>

    <header class="head rise" style="--d: 0ms">
      <div class="head-copy">
        <p class="eyebrow">参考 shuding/liquid-glass · 本地静态透镜引擎 liquidLens.ts</p>
        <h1>Liquid Glass<span class="h1-sub">一次生成 · 单 pass 折射 · 可拖拽透镜</span></h1>
        <p class="lede">
          位移贴图由 Canvas 按圆角矩形 SDF 一次性生成并缓存，经 feImage + 单个
          feDisplacementMap 弯折背景边缘——无逐帧重算、无 WebGL。彩色渐变与高频纹理是折射的
          「被弯折物」：拖动下方透镜扫过条纹与文字，边缘液化清晰可见。
        </p>
      </div>
      <router-link to="/" class="back">← 返回应用</router-link>
    </header>

    <!-- 实时控制台：与设置页共享同一份玻璃参数，拖动即时生效 -->
    <section class="controls rise" style="--d: 60ms">
      <div class="controls-head">
        <span class="controls-title">实时控制台</span>
        <span class="controls-sub">与「个人设置」同步 · 保存在本机</span>
        <div class="controls-actions">
          <button type="button" class="ctl-btn" @click="randomize">随机</button>
          <button type="button" class="ctl-btn" @click="copyParams">{{ copyState }}</button>
          <button type="button" class="ctl-btn ctl-btn-warn" @click="appStore.resetGlass()">重置</button>
        </div>
      </div>
      <div class="controls-grid">
        <!-- refraction 折射强度：边缘位移幅度，0 = 关闭折射 -->
        <label class="ctl">
          <span class="ctl-label">折射强度<b>{{ appStore.glass.refraction }}</b></span>
          <input type="range" min="0" max="120" step="1" :value="appStore.glass.refraction"
            @input="appStore.setGlass({ refraction: num($event) })" />
        </label>
        <!-- lensEdge 折射边带：发生弯折的外圈厚度 -->
        <label class="ctl">
          <span class="ctl-label">折射边带<b>{{ appStore.glass.lensEdge }}</b></span>
          <input type="range" min="10" max="50" step="1" :value="appStore.glass.lensEdge"
            @input="appStore.setGlass({ lensEdge: num($event) })" />
        </label>
        <!-- blur 磨砂度：0 = 清玻璃，越大越朦胧 -->
        <label class="ctl">
          <span class="ctl-label">磨砂度<b>{{ appStore.glass.blur }}px</b></span>
          <input type="range" min="0" max="40" step="1" :value="appStore.glass.blur"
            @input="appStore.setGlass({ blur: num($event) })" />
        </label>
        <label class="ctl">
          <span class="ctl-label">背景饱和<b>{{ appStore.glass.saturation }}%</b></span>
          <input type="range" min="100" max="300" step="5" :value="appStore.glass.saturation"
            @input="appStore.setGlass({ saturation: num($event) })" />
        </label>
        <label class="ctl">
          <span class="ctl-label">玻璃通透<b>{{ appStore.glass.alpha.toFixed(2) }}</b></span>
          <input type="range" min="0.35" max="0.92" step="0.01" :value="appStore.glass.alpha"
            @input="appStore.setGlass({ alpha: num($event) })" />
        </label>
        <label class="ctl">
          <span class="ctl-label">镜面高光<b>{{ appStore.glass.specular.toFixed(2) }}</b></span>
          <input type="range" min="0" max="1.5" step="0.05" :value="appStore.glass.specular"
            @input="appStore.setGlass({ specular: num($event) })" />
        </label>
        <label class="ctl">
          <span class="ctl-label">氛围色相<b>{{ appStore.glass.hue }}°</b></span>
          <input type="range" min="0" max="360" step="5" :value="appStore.glass.hue"
            @input="appStore.setGlass({ hue: num($event) })" />
        </label>
        <div class="ctl ctl-switches">
          <label class="switch">
            <input type="checkbox" :checked="appStore.glass.photo"
              @change="appStore.setGlass({ photo: ($event.target as HTMLInputElement).checked })" />
            <span class="switch-track" aria-hidden="true"></span>
            <span>照片底图</span>
          </label>
          <label class="switch">
            <input type="checkbox" :checked="appStore.glass.aurora"
              @change="appStore.setGlass({ aurora: ($event.target as HTMLInputElement).checked })" />
            <span class="switch-track" aria-hidden="true"></span>
            <span>氛围光</span>
          </label>
        </div>
      </div>
    </section>

    <!-- 用法一：胶囊按钮 -->
    <section class="demo rise" style="--d: 140ms">
      <div class="demo-head">
        <span class="demo-index">01</span>
        <div>
          <h2>胶囊按钮</h2>
          <p>圆角 999px 的透镜 SDF 自动收敛为胶囊形；点击验证事件，悬浮观察边缘弯折条纹。</p>
        </div>
      </div>
      <div class="stage stage-pill">
        <div class="deco stripes" aria-hidden="true"></div>
        <div class="deco dots" aria-hidden="true"></div>
        <span class="watermark" aria-hidden="true">REFRACT</span>
        <div class="glass pill" data-lens-blur-scale="0.12" @click="onGlassClick('胶囊按钮', $event)">
          <span class="pill-dot" aria-hidden="true"></span>
          点我 · 事件透传
        </div>
      </div>
    </section>

    <!-- 用法二：信息卡片 -->
    <section class="demo rise" style="--d: 220ms">
      <div class="demo-head">
        <span class="demo-index">02</span>
        <div>
          <h2>信息卡片</h2>
          <p>圆角 28px · 内边距 24px 32px。中心内容保持锐利，仅外圈边带折射背景。</p>
        </div>
      </div>
      <div class="stage stage-card">
        <div class="deco mesh" aria-hidden="true"></div>
        <div class="deco dots" aria-hidden="true"></div>
        <span class="watermark watermark-cn" aria-hidden="true">折射</span>
        <div class="glass card" data-lens-blur-scale="0.12" @click="onGlassClick('标准卡片', $event)">
          <span class="card-dot" aria-hidden="true"></span>
          <span class="card-copy">
            <strong>科研知识库</strong>
            <small>静态位移贴图 · 按尺寸缓存 · 点击查看日志</small>
          </span>
        </div>
      </div>
    </section>

    <!-- 用法三：可拖拽透镜（shuding 演示的签名交互） -->
    <section class="demo rise" style="--d: 300ms">
      <div class="demo-head">
        <span class="demo-index">03</span>
        <div>
          <h2>可拖拽透镜</h2>
          <p>按住透镜在区域内拖动——扫过同心圆、网格与文字时，边缘折射实时可见；松手即定格。</p>
        </div>
      </div>
      <div ref="lensStage" class="stage stage-float">
        <div class="deco rings" aria-hidden="true"></div>
        <div class="deco grid-lines" aria-hidden="true"></div>
        <span class="watermark watermark-drag" aria-hidden="true">DRAG</span>
        <span class="zone-hint">按住玻璃拖动 ↘ 扫过纹理观察边缘液化</span>
        <div
          ref="lens"
          class="glass drag-lens"
          data-lens-blur-scale="0.12"
          :class="{ dragging }"
          :style="{ left: `${lensX}px`, top: `${lensY}px` }"
          @pointerdown="onLensDown"
          @pointermove="onLensMove"
          @pointerup="onLensUp"
          @pointercancel="onLensUp"
        >
          <strong>拖我</strong>
          <small>液态透镜 · 240×150</small>
        </div>
      </div>
    </section>

    <!-- 事件日志：点击透传的可观测证据 -->
    <section class="log-panel rise" style="--d: 380ms">
      <div class="log-head">
        <span class="log-title">事件日志 · 点击透传验证</span>
        <span class="log-count">{{ logs.length }} 条</span>
        <button class="log-clear" type="button" :disabled="logs.length === 0" @click="logs = []">清空</button>
      </div>
      <ul v-if="logs.length" class="log-list">
        <li v-for="(item, i) in logs" :key="item.id" class="log-item" :class="{ fresh: i === 0 }">
          <span class="log-time">{{ item.time }}</span>
          <span class="log-src">{{ item.source }}</span>
          <span class="log-detail">click · clientX {{ item.x }} / clientY {{ item.y }}</span>
        </li>
      </ul>
      <p v-else class="log-empty">尚未点击任何玻璃——点击胶囊、卡片或透镜（拖动小于 4px 记为点击），这里与控制台同时记录真实 MouseEvent。</p>
    </section>

    <footer class="compat rise" style="--d: 440ms">
      <strong>兼容性说明</strong>
      <p>
        Chrome / Edge：完整边缘折射（静态 feImage 位移贴图 + 单 pass feDisplacementMap，GPU 合成缓存）。
        Safari / Firefox：backdrop-filter 不支持 url() 滤镜引用，引擎自动降级为磨砂 + 描边，不报错、不空白。
        贴图只在尺寸 / 圆角 / 折射参数变化时生成一次（≤192px 分辨率），无逐帧 JS、无 WebGL worker。
      </p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

function num(e: Event): number {
  return Number((e.target as HTMLInputElement).value)
}

/* ---------- 实时控制台动作 ---------- */
const copyState = ref('复制参数')
let copyTimer = 0

function randomize() {
  const ri = (min: number, max: number) => Math.round(min + Math.random() * (max - min))
  const rf = (min: number, max: number, p = 2) => Number((min + Math.random() * (max - min)).toFixed(p))
  appStore.setGlass({
    refraction: ri(30, 110),
    lensEdge: ri(12, 45),
    blur: ri(2, 30),
    saturation: ri(120, 260),
    alpha: rf(0.45, 0.85),
    specular: rf(0.4, 1.4),
    hue: ri(0, 360)
  })
}

async function copyParams() {
  try {
    await navigator.clipboard.writeText(JSON.stringify(appStore.glass, null, 2))
    copyState.value = '已复制 ✓'
  } catch {
    copyState.value = '复制失败'
  }
  clearTimeout(copyTimer)
  copyTimer = window.setTimeout(() => (copyState.value = '复制参数'), 1600)
}

/* ---------- 点击日志 ---------- */
interface ClickLog {
  id: number
  time: string
  source: string
  x: number
  y: number
}
const logs = ref<ClickLog[]>([])
let logSeq = 0

function pushLog(source: string, x: number, y: number) {
  console.log('[LiquidGlass] click 透传成功:', source, { clientX: x, clientY: y })
  logs.value.unshift({
    id: ++logSeq,
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    source,
    x: Math.round(x),
    y: Math.round(y)
  })
  if (logs.value.length > 30) logs.value.pop()
}

function onGlassClick(source: string, e: MouseEvent) {
  pushLog(source, e.clientX, e.clientY)
}

/* ---------- 可拖拽透镜 ---------- */
const LENS_W = 240
const LENS_H = 150
const lensStage = ref<HTMLElement | null>(null)
const lens = ref<HTMLElement | null>(null)
const lensX = ref(0)
const lensY = ref(0)
const dragging = ref(false)
let startX = 0
let startY = 0
let originX = 0
let originY = 0
let moved = 0

function clampLens() {
  const stage = lensStage.value
  if (!stage) return
  const maxX = Math.max(0, stage.clientWidth - LENS_W)
  const maxY = Math.max(0, stage.clientHeight - LENS_H)
  lensX.value = Math.max(0, Math.min(maxX, lensX.value))
  lensY.value = Math.max(0, Math.min(maxY, lensY.value))
}

function centerLens() {
  const stage = lensStage.value
  if (!stage) return
  lensX.value = Math.max(0, (stage.clientWidth - LENS_W) / 2)
  lensY.value = Math.max(0, (stage.clientHeight - LENS_H) / 2)
}

function onLensDown(e: PointerEvent) {
  lens.value?.setPointerCapture(e.pointerId)
  dragging.value = true
  moved = 0
  startX = e.clientX
  startY = e.clientY
  originX = lensX.value
  originY = lensY.value
}

function onLensMove(e: PointerEvent) {
  if (!dragging.value) return
  const dx = e.clientX - startX
  const dy = e.clientY - startY
  moved = Math.max(moved, Math.abs(dx) + Math.abs(dy))
  lensX.value = originX + dx
  lensY.value = originY + dy
  clampLens()
}

function onLensUp(e: PointerEvent) {
  if (!dragging.value) return
  dragging.value = false
  if (moved < 4) pushLog('拖拽透镜', e.clientX, e.clientY)
  lens.value?.releasePointerCapture(e.pointerId)
}

let stageObserver: ResizeObserver | null = null

onMounted(() => {
  centerLens()
  stageObserver = new ResizeObserver(() => {
    if (!dragging.value) clampLens()
  })
  if (lensStage.value) stageObserver.observe(lensStage.value)
})

onBeforeUnmount(() => {
  stageObserver?.disconnect()
  clearTimeout(copyTimer)
})
</script>

<style scoped>
.showcase {
  position: relative;
  min-height: 100vh;
  padding: 48px clamp(16px, 5vw, 64px) 64px;
  overflow: hidden;
  color: #f5f6fa;
  font-family: system-ui, -apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background:
    radial-gradient(1100px 700px at 12% -8%, rgba(10, 132, 255, 0.5), transparent 60%),
    radial-gradient(900px 650px at 88% 10%, rgba(191, 90, 242, 0.45), transparent 62%),
    radial-gradient(1000px 800px at 72% 96%, rgba(255, 159, 10, 0.36), transparent 60%),
    radial-gradient(760px 620px at 14% 88%, rgba(48, 209, 88, 0.28), transparent 58%),
    linear-gradient(180deg, rgba(8, 10, 20, 0.62), rgba(8, 10, 20, 0.72)),
    url('/images/glass-cat.jpg') center / cover no-repeat;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  pointer-events: none;
  z-index: 0;
}
.orb-a {
  width: 420px;
  height: 420px;
  top: -120px;
  right: -80px;
  background: radial-gradient(circle, rgba(94, 92, 230, 0.55), transparent 70%);
  animation: orbDrift 26s ease-in-out infinite alternate;
}
.orb-b {
  width: 360px;
  height: 360px;
  bottom: 6%;
  left: -100px;
  background: radial-gradient(circle, rgba(255, 55, 95, 0.4), transparent 70%);
  animation: orbDrift 32s ease-in-out infinite alternate-reverse;
}
.orb-c {
  width: 300px;
  height: 300px;
  top: 42%;
  left: 52%;
  background: radial-gradient(circle, rgba(100, 210, 255, 0.35), transparent 70%);
  animation: orbDrift 38s ease-in-out infinite alternate;
}
@keyframes orbDrift {
  from { transform: translate3d(0, 0, 0) scale(1); }
  to { transform: translate3d(60px, -40px, 0) scale(1.15); }
}

.head,
.controls,
.demo,
.log-panel,
.compat {
  position: relative;
  z-index: 1;
  max-width: 980px;
  margin: 0 auto;
}

.rise {
  animation: riseIn 0.6s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: var(--d, 0ms);
}
@keyframes riseIn {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 28px;
}
.eyebrow {
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #9fb4ff;
  margin-bottom: 10px;
}
h1 {
  font-size: clamp(30px, 4.6vw, 46px);
  font-weight: 800;
  letter-spacing: -0.5px;
  line-height: 1.15;
  background: linear-gradient(100deg, #ffffff 20%, #8ec9ff 55%, #e2a6ff 90%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.h1-sub {
  display: block;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  margin-top: 8px;
  background: none;
  -webkit-text-fill-color: #c8cde0;
  color: #c8cde0;
}
.lede {
  margin-top: 14px;
  max-width: 660px;
  font-size: 13.5px;
  line-height: 1.8;
  color: #aab0c6;
}
.back {
  flex-shrink: 0;
  padding: 9px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.08);
  color: #e8ebf5;
  font-size: 13px;
  text-decoration: none;
  transition: background 0.2s ease, transform 0.2s ease;
}
.back:hover {
  background: rgba(255, 255, 255, 0.16);
  transform: translateY(-1px);
}

/* ---------- 实时控制台 ---------- */
.controls {
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(10, 13, 28, 0.6);
  backdrop-filter: blur(16px) saturate(140%);
  -webkit-backdrop-filter: blur(16px) saturate(140%);
  padding: 16px 18px 18px;
  margin-bottom: 34px;
}
@media (min-width: 900px) {
  .controls {
    position: sticky;
    top: 12px;
    z-index: 6;
    box-shadow: 0 18px 50px rgba(3, 5, 15, 0.5);
  }
}
.controls-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.controls-title {
  font-size: 14px;
  font-weight: 800;
}
.controls-sub {
  font-size: 11px;
  color: #8b93ab;
}
.controls-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}
.ctl-btn {
  font-size: 12px;
  color: #e8ebf5;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  padding: 4px 13px;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.15s ease;
}
.ctl-btn:hover {
  background: rgba(142, 201, 255, 0.28);
  transform: translateY(-1px);
}
.ctl-btn:active {
  transform: scale(0.96);
}
.ctl-btn-warn:hover {
  background: rgba(255, 55, 95, 0.35);
}
.controls-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px 18px;
}
.ctl {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}
.ctl-label {
  display: flex;
  justify-content: space-between;
  font-size: 11.5px;
  color: #aab0c6;
}
.ctl-label b {
  color: #8ec9ff;
  font-variant-numeric: tabular-nums;
}
.ctl input[type='range'] {
  -webkit-appearance: none;
  appearance: none;
  width: 100%;
  height: 6px;
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(10, 132, 255, 0.55), rgba(191, 90, 242, 0.55));
  outline: none;
  cursor: pointer;
}
.ctl input[type='range']::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #ffffff, #8ec9ff 60%, #5e5ce6);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 2px 8px rgba(10, 132, 255, 0.6);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.ctl input[type='range']::-webkit-slider-thumb:hover {
  transform: scale(1.2);
  box-shadow: 0 2px 14px rgba(142, 201, 255, 0.9);
}
.ctl input[type='range']::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #ffffff, #8ec9ff 60%, #5e5ce6);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 2px 8px rgba(10, 132, 255, 0.6);
}
.ctl input[type='range']::-moz-range-track {
  height: 6px;
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(10, 132, 255, 0.55), rgba(191, 90, 242, 0.55));
}
.ctl-switches {
  flex-direction: row;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #c6cbdd;
  cursor: pointer;
  user-select: none;
}
.switch input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}
.switch-track {
  width: 34px;
  height: 20px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.24);
  position: relative;
  transition: background 0.2s ease;
  flex-shrink: 0;
}
.switch-track::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #c6cbdd;
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.2s ease;
}
.switch input:checked + .switch-track {
  background: linear-gradient(90deg, #0a84ff, #5e5ce6);
  border-color: rgba(142, 201, 255, 0.6);
}
.switch input:checked + .switch-track::after {
  transform: translateX(14px);
  background: #fff;
}
.switch input:focus-visible + .switch-track {
  outline: 2px solid #8ec9ff;
  outline-offset: 2px;
}

/* ---------- 演示区 ---------- */
.demo {
  margin-bottom: 36px;
}
.demo-head {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 12px;
}
.demo-index {
  font-size: 13px;
  font-weight: 800;
  color: #0b0d1a;
  background: linear-gradient(135deg, #8ec9ff, #e2a6ff);
  border-radius: 10px;
  padding: 5px 9px;
  line-height: 1;
  margin-top: 3px;
}
.demo-head h2 {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 5px;
}
.demo-head p {
  font-size: 12.5px;
  color: #9aa1b8;
  line-height: 1.7;
}

.stage {
  position: relative;
  height: 240px;
  border-radius: 22px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12), 0 18px 50px rgba(3, 5, 15, 0.45);
}
.stage-pill {
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(420px 260px at 22% 30%, rgba(255, 159, 10, 0.75), transparent 65%),
    radial-gradient(460px 300px at 80% 72%, rgba(255, 55, 95, 0.65), transparent 65%),
    linear-gradient(120deg, #1d2b64 0%, #4a1d6e 55%, #7a1f4d 100%);
}
.stage-card {
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(480px 300px at 76% 24%, rgba(48, 209, 88, 0.55), transparent 62%),
    radial-gradient(420px 280px at 18% 78%, rgba(10, 132, 255, 0.7), transparent 62%),
    linear-gradient(140deg, #0e2a52 0%, #123c3a 52%, #1c2461 100%);
}
.stage-float {
  height: 340px;
  background:
    radial-gradient(500px 320px at 50% 46%, rgba(100, 210, 255, 0.5), transparent 64%),
    radial-gradient(420px 300px at 86% 88%, rgba(94, 92, 230, 0.6), transparent 62%),
    linear-gradient(150deg, #101c46 0%, #232a6e 55%, #3c1a5e 100%);
}

.deco {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.stripes {
  background: repeating-linear-gradient(-45deg, rgba(255, 255, 255, 0.16) 0 10px, transparent 10px 34px);
  mask-image: radial-gradient(70% 90% at 50% 50%, #000 30%, transparent 100%);
  -webkit-mask-image: radial-gradient(70% 90% at 50% 50%, #000 30%, transparent 100%);
}
.dots {
  background-image: radial-gradient(rgba(255, 255, 255, 0.35) 1.4px, transparent 1.5px);
  background-size: 22px 22px;
  mask-image: linear-gradient(115deg, #000 10%, transparent 55%);
  -webkit-mask-image: linear-gradient(115deg, #000 10%, transparent 55%);
}
.mesh {
  background: conic-gradient(from 210deg at 62% 38%, rgba(255, 214, 10, 0.4), rgba(255, 55, 95, 0.35), rgba(10, 132, 255, 0.4), rgba(48, 209, 88, 0.35), rgba(255, 214, 10, 0.4));
  filter: blur(26px);
  opacity: 0.75;
}
.rings {
  background: repeating-radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.18) 0 2px, transparent 2px 42px);
  mask-image: radial-gradient(80% 100% at 50% 50%, #000 20%, transparent 95%);
  -webkit-mask-image: radial-gradient(80% 100% at 50% 50%, #000 20%, transparent 95%);
}
.grid-lines {
  background-image:
    linear-gradient(rgba(140, 190, 255, 0.16) 1px, transparent 1px),
    linear-gradient(90deg, rgba(140, 190, 255, 0.16) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(180deg, transparent 0%, #000 65%);
  -webkit-mask-image: linear-gradient(180deg, transparent 0%, #000 65%);
}
.watermark {
  position: absolute;
  right: 22px;
  bottom: 8px;
  font-size: 76px;
  font-weight: 900;
  letter-spacing: 0.04em;
  line-height: 1;
  color: transparent;
  -webkit-text-stroke: 1.5px rgba(255, 255, 255, 0.3);
  pointer-events: none;
  user-select: none;
}
.watermark-cn {
  font-size: 88px;
  -webkit-text-stroke: 1.5px rgba(255, 255, 255, 0.26);
}
.watermark-drag {
  left: 24px;
  right: auto;
  top: 40px;
  bottom: auto;
  font-size: 64px;
  -webkit-text-stroke: 1.5px rgba(255, 255, 255, 0.22);
}
.zone-hint {
  position: absolute;
  top: 14px;
  right: 18px;
  font-size: 12px;
  color: rgba(230, 238, 255, 0.85);
  background: rgba(8, 12, 30, 0.4);
  border: 1px solid rgba(140, 190, 255, 0.3);
  border-radius: 999px;
  padding: 5px 12px;
  pointer-events: none;
}

/* 玻璃演示件：全局 .glass 材质 + 透镜引擎自动绑定；
   演示页为深色场景，表面不透明度降为全局通透度的 22%，折射更通透 */
.pill,
.card,
.drag-lens {
  background-color: rgba(255, 255, 255, calc(var(--lg-alpha) * 0.22));
}
.pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
}
.pill-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ffd60a;
  box-shadow: 0 0 10px rgba(255, 214, 10, 0.9);
}
.card {
  display: inline-flex;
  align-items: center;
  gap: 18px;
  padding: 24px 32px;
  border-radius: 28px;
  cursor: pointer;
  user-select: none;
}
.card-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #30d158;
  box-shadow: 0 0 12px rgba(48, 209, 88, 0.9);
  flex-shrink: 0;
}
.card-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-copy strong {
  font-size: 18px;
  font-weight: 700;
}
.card-copy small {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
}
.drag-lens {
  position: absolute;
  width: 240px;
  height: 150px;
  border-radius: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.drag-lens strong {
  font-size: 20px;
  font-weight: 800;
}
.drag-lens small {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
}
.drag-lens.dragging {
  cursor: grabbing;
}

/* ---------- 事件日志 ---------- */
.log-panel {
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(10, 13, 28, 0.55);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  padding: 16px 18px;
  margin-bottom: 28px;
}
.log-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.log-title {
  font-size: 14px;
  font-weight: 700;
}
.log-count {
  font-size: 11px;
  color: #9fb4ff;
  background: rgba(10, 132, 255, 0.16);
  border-radius: 999px;
  padding: 2px 9px;
}
.log-clear {
  margin-left: auto;
  font-size: 12px;
  color: #e8ebf5;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  padding: 4px 14px;
  cursor: pointer;
  transition: background 0.2s ease;
}
.log-clear:hover:not(:disabled) {
  background: rgba(255, 55, 95, 0.35);
}
.log-clear:disabled {
  opacity: 0.4;
  cursor: default;
}
.log-list {
  list-style: none;
  max-height: 180px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  scrollbar-width: thin;
  scrollbar-color: rgba(142, 201, 255, 0.5) rgba(255, 255, 255, 0.06);
}
.log-list::-webkit-scrollbar {
  width: 8px;
}
.log-list::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.06);
  border-radius: 8px;
}
.log-list::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, #8ec9ff, #e2a6ff);
  border-radius: 8px;
}
.log-list::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(180deg, #aed8ff, #efc4ff);
}
.log-item {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  font-family: ui-monospace, 'Cascadia Code', Consolas, monospace;
  padding: 7px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.log-item.fresh {
  border-color: rgba(48, 209, 88, 0.55);
  background: rgba(48, 209, 88, 0.1);
  animation: logFlash 0.5s ease;
}
@keyframes logFlash {
  from { transform: translateY(-4px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.log-time {
  color: #8b93ab;
}
.log-src {
  color: #8ec9ff;
  font-weight: 700;
}
.log-detail {
  color: #c6cbdd;
}
.log-empty {
  font-size: 12.5px;
  color: #8b93ab;
  line-height: 1.7;
}

.compat {
  border-radius: 16px;
  border: 1px solid rgba(255, 159, 10, 0.4);
  background: rgba(255, 159, 10, 0.1);
  padding: 16px 20px;
}
.compat strong {
  display: block;
  font-size: 13.5px;
  color: #ffd60a;
  margin-bottom: 6px;
}
.compat p {
  font-size: 12.5px;
  line-height: 1.8;
  color: #e3d9c2;
}

@media (max-width: 900px) {
  .controls-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 700px) {
  .showcase {
    padding-top: 32px;
  }
  .controls-grid {
    grid-template-columns: 1fr;
  }
  .stage {
    height: 210px;
  }
  .stage-float {
    height: 300px;
  }
  .watermark {
    font-size: 52px;
  }
  .watermark-cn {
    font-size: 60px;
  }
  .watermark-drag {
    font-size: 44px;
  }
  .head {
    margin-bottom: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .orb,
  .rise,
  .log-item.fresh {
    animation: none;
  }
}
</style>