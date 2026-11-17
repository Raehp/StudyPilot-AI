<template>
  <div class="settings">
    <PageHeader title="个人设置" subtitle="账号资料、外观偏好与安全设置" />

    <div class="settings-grid">
      <div class="panel glass">
        <div class="panel-head">个人信息</div>
        <div class="info-row">
          <span class="info-label">用户名</span>
          <span class="info-value">{{ userStore.userInfo?.username }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">昵称</span>
          <span class="info-value">{{ userStore.userInfo?.nickname }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">邮箱</span>
          <span class="info-value">{{ userStore.userInfo?.email || '未设置' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">专业</span>
          <span class="info-value">{{ userStore.userInfo?.major || '未设置' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">注册时间</span>
          <span class="info-value">{{ userStore.userInfo?.createTime }}</span>
        </div>
        <div class="panel-note">
          资料修改接口将在后续里程碑开放，当前展示来自 /users/me 的真实数据。
        </div>
      </div>

      <div class="panel glass">
        <div class="panel-head">外观</div>
        <div class="theme-row">
          <div
            class="theme-option"
            :class="{ active: appStore.theme === 'light' }"
            @click="appStore.setTheme('light')"
          >
            <div class="theme-preview light-preview">
              <span></span><span></span><span></span>
            </div>
            <span class="theme-name">浅色</span>
          </div>
          <div
            class="theme-option"
            :class="{ active: appStore.theme === 'dark' }"
            @click="appStore.setTheme('dark')"
          >
            <div class="theme-preview dark-preview">
              <span></span><span></span><span></span>
            </div>
            <span class="theme-name">深色</span>
          </div>
        </div>
        <div class="panel-note">主题偏好保存在本机，立即生效。</div>

        <div class="panel-head security-head">账号安全</div>
        <div class="info-row">
          <span class="info-label">修改密码</span>
          <el-tag size="small" effect="plain" round type="info">后续开放</el-tag>
        </div>
        <div class="info-row">
          <span class="info-label">登录设备管理</span>
          <el-tag size="small" effect="plain" round type="info">后续开放</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const userStore = useUserStore()
const appStore = useAppStore()
</script>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.panel {
  padding: 22px;
}

.panel-head {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.security-head {
  margin-top: 24px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 2px;
  border-bottom: 1px solid var(--sp-border);
  font-size: 14px;
}

.info-row:last-of-type {
  border-bottom: none;
}

.info-label {
  color: var(--sp-text-2);
}

.info-value {
  font-weight: 500;
}

.panel-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--sp-text-3);
  line-height: 1.6;
}

.theme-row {
  display: flex;
  gap: 14px;
}

.theme-option {
  flex: 1;
  cursor: pointer;
  border: 2px solid var(--sp-border);
  border-radius: 14px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  transition: border-color 0.2s ease;
}

.theme-option.active {
  border-color: var(--sp-primary);
}

.theme-preview {
  width: 100%;
  height: 64px;
  border-radius: 10px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.light-preview {
  background: #f2f2f7;
}

.dark-preview {
  background: #1c1c1e;
}

.theme-preview span {
  height: 8px;
  border-radius: 4px;
}

.light-preview span:nth-child(1) {
  width: 40%;
  background: linear-gradient(90deg, #0a84ff, #5e5ce6);
}

.light-preview span:nth-child(2) {
  width: 80%;
  background: #ffffff;
}

.light-preview span:nth-child(3) {
  width: 60%;
  background: #ffffff;
}

.dark-preview span:nth-child(1) {
  width: 40%;
  background: linear-gradient(90deg, #0a84ff, #5e5ce6);
}

.dark-preview span:nth-child(2) {
  width: 80%;
  background: #2c2c2e;
}

.dark-preview span:nth-child(3) {
  width: 60%;
  background: #2c2c2e;
}

.theme-name {
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>