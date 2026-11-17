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
      <header class="header glass">
        <el-button class="menu-btn" text circle @click="appStore.mobileNavOpen = true">
          <el-icon :size="20"><Operation /></el-icon>
        </el-button>
        <div class="header-title">{{ route.meta.title }}</div>
        <div class="header-right">
          <el-button text circle class="theme-btn" @click="appStore.toggleTheme()">
            <el-icon :size="18">
              <Moon v-if="appStore.theme === 'light'" />
              <Sunny v-else />
            </el-icon>
          </el-button>
          <el-dropdown trigger="click" @command="onCommand">
            <div class="user-chip">
              <el-avatar :size="30" class="avatar">{{ avatarText }}</el-avatar>
              <span class="nickname">{{ userStore.userInfo?.nickname || '…' }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ userStore.userInfo?.username }}</el-dropdown-item>
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
      </header>

      <main class="content">
        <router-view />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import SideNav from './SideNav.vue'

const route = useRoute()
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
  background: var(--sp-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.header {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
}

.menu-btn {
  display: none;
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.theme-btn {
  color: var(--sp-text-2);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  background: var(--sp-surface);
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
  padding: 2px;
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }

  .menu-btn {
    display: inline-flex;
  }

  .layout {
    padding: 10px;
    gap: 10px;
  }
}
</style>