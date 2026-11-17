<template>
  <div class="chart-card glass">
    <div class="chart-head">
      <span class="chart-title">{{ title }}</span>
      <slot name="extra" />
    </div>
    <div class="chart-body">
      <div v-show="!empty" ref="chartRef" class="chart"></div>
      <EmptyState
        v-if="empty"
        :icon="TrendCharts"
        title="暂无数据"
        :description="emptyText"
        compact
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'
import { TrendCharts } from '@element-plus/icons-vue'
import EmptyState from './EmptyState.vue'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = withDefaults(
  defineProps<{
    title: string
    empty?: boolean
    emptyText?: string
    option?: EChartsOption | null
  }>(),
  { empty: true, emptyText: '暂无数据', option: null }
)

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function render() {
  if (props.empty || !props.option || !chartRef.value) return
  // v-show 切换可见性后 DOM 才有尺寸；尺寸为 0 时延到下一帧，避免 ECharts 0 宽高告警
  if (chartRef.value.clientWidth === 0 || chartRef.value.clientHeight === 0) {
    requestAnimationFrame(render)
    return
  }
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption(props.option)
  chart.resize()
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})

watch(() => [props.empty, props.option], render, { deep: true, flush: 'post' })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-card {
  padding: 20px;
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
}

.chart-body {
  position: relative;
  min-height: 240px;
}

.chart {
  width: 100%;
  height: 260px;
}
</style>