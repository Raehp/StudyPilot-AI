<template>
  <div class="layout">
    <aside class="sidebar glass">
      <SideNav />
    </aside>

    <el-drawer
      v-model="appStore.mobileNavOpen"
      direction="ltr"
      size="270px"
      :with-header="false"
      class="nav-drawer"
    >
      <SideNav @navigate="appStore.mobileNavOpen = false" />
    </el-drawer>

    <section class="main-col">
      <LiquidGlass
        class="nav-pill"
        :tint="0.1"
        :scale="32"
        :bevel-depth="12"
      >
        <div class="nav-inner">
          <el-button class="menu-btn" text circle @click="appStore.mobileNavOpen = true">
            <el-icon :size="20"><Operation /></el-icon>
          </el-button>
          <router-link to="/" class="brand">
            <span class="logo-icon">
              <el-icon :size="18"><Compass /></el-icon>
            </span>
            <span class="brand-text">StudyPilot</span>
          </router-link>
          <div class="nav-actions">
            <SquishSwitch
              class="theme-switch"
              :checked="appStore.theme === 'dark'"
              :width="64"
              :height="32"
              :radius="16"
              track-color="#e6ebf5"
              track-on-color="#23232b"
              thumb-color="#ffffff"
              thumb-on-color="#3b3b46"
              aria-label="切换深色模式"
              @change="(v: boolean) => appStore.setTheme(v ? 'dark' : 'light')"
            >
              <el-icon class="ss-icon ss-icon-off" :size="14"><Moon /></el-icon>
              <el-icon class="ss-icon ss-icon-on" :size="14"><Sunny /></el-icon>
            </SquishSwitch>
            <el-dropdown trigger="click" @command="onCommand">
              <div class="user-chip">
                <el-avatar :size="30" class="avatar">{{ avatarText }}</el-avatar>
                <span class="nickname">{{ userStore.userInfo?.nickname || '…' }}</span>
                <el-icon :size="12"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <li class="user-menu-head">{{ userStore.userInfo?.username }}</li>
                  <el-dropdown-item command="settings" divided>
                    <el-icon><Setting /></el-icon>个人设置
                  </el-dropdown-item>
                  <el-dropdown-item command="logout">
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </LiquidGlass>

      <main class="content">
        <router-view />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import SideNav from './SideNav.vue'
import SquishSwitch from '@/components/common/SquishSwitch.vue'
import LiquidGlass from '@/components/common/LiquidGlass.vue'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const avatarText = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || ''
  return name.slice(0, 1).toUpperCase()
})

onMounted(async () => {
  if (!userStore.userInfo) {
    try {
      await userStore.loadUser()
    } catch {
      // 401 已由拦截器统一处理
    }
  }
})

async function onCommand(command: string) {
  if (command === 'settings') {
    router.push('/settings')
  } else if (command === 'logout') {
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
  display: flex;
  gap: 14px;
  padding: 14px;
}

.sidebar {
  width: 232px;
  flex-shrink: 0;
  background-color: var(--sp-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-col {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.nav-pill {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: min(100%, 1160px);
  z-index: 40;
  box-shadow:
    0 10px 40px rgba(0, 0, 0, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.45);
}
html.dark .nav-pill {
  box-shadow:
    0 12px 44px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.nav-inner {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 56px;
  padding: 8px 10px 8px 14px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-right: auto;
  color: var(--sp-text);
  text-decoration: none;
  font-weight: 700;
  letter-spacing: -0.2px;
  flex-shrink: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--sp-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(10, 132, 255, 0.4);
}

.brand-text {
  font-size: 15px;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.menu-btn {
  display: none;
}

.theme-switch :deep(.squish-switch__track) {
  box-shadow:
    inset 0 0 0 1px var(--lg-border),
    inset 0 2px 6px rgba(0, 0, 0, 0.06);
}
.theme-switch :deep([data-on] .squish-switch__track) {
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.09),
    inset 0 2px 8px rgba(0, 0, 0, 0.5);
}
.theme-switch .ss-icon {
  position: absolute;
  inset: 0;
  /* 覆盖 .el-icon 的 1em 尺寸，否则 inset:0 过约束会把图标钉在滑块左上角 */
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6a6a72;
  transition: opacity 0.32s ease, transform 0.32s ease;
}
.theme-switch .ss-icon-on {
  opacity: 0;
  transform: rotate(-90deg) scale(0.6);
  color: #ffd60a;
}
.theme-switch :deep([data-on]) .ss-icon-off {
  opacity: 0;
  transform: rotate(90deg) scale(0.6);
}
.theme-switch :deep([data-on]) .ss-icon-on {
  opacity: 1;
  transform: none;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  background: var(--sp-nav-surface);
  border: 1px solid var(--sp-border);
  color: var(--sp-text);
}

.avatar {
  background: var(--sp-grad);
  color: #fff;
  font-weight: 600;
}

.nickname {
  font-size: 13px;
  font-weight: 600;
}

.content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 70px 2px 2px;
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }

  .menu-btn {
    display: inline-flex;
  }

  .nickname {
    display: none;
  }

  .layout {
    padding: 10px;
    gap: 10px;
  }

  .nav-inner {
    padding: 8px;
  }
}
</style>