<template>
  <div class="auth-page">
    <div class="auth-bg" aria-hidden="true"></div>
    <div class="auth-card glass">
      <div class="auth-logo">
        <div class="logo-icon">
          <el-icon :size="26"><Compass /></el-icon>
        </div>
        <h1>StudyPilot AI</h1>
        <p>高校学习科研智能工作台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="onSubmit"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <SpecularButton
          class="submit-btn"
          size="md"
          :radius="14"
          :tint="appStore.theme === 'dark' ? '#0a84ff' : '#ffffff'"
          :tint-opacity="appStore.theme === 'dark' ? 0.16 : 0.55"
          :text-color="appStore.theme === 'dark' ? '#f5f5f7' : '#1c1c1e'"
          :line-color="appStore.theme === 'dark' ? '#ffffff' : '#0a84ff'"
          :base-color="appStore.theme === 'dark' ? '#525252' : '#9a9aa2'"
          :idle-intensity="0.55"
          :disabled="loading"
          @click="onSubmit"
        >
          {{ loading ? '登录中…' : '登 录' }}
        </SpecularButton>
      </el-form>

      <div class="auth-switch">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import SpecularButton from '@/components/common/SpecularButton.vue'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 400px;
  max-width: 100%;
  padding: 40px 36px 32px;
}

.auth-logo {
  text-align: center;
  margin-bottom: 28px;
}

.logo-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 16px;
  background: var(--sp-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(10, 132, 255, 0.4);
}

.auth-logo h1 {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.auth-logo p {
  margin-top: 6px;
  font-size: 13px;
  color: var(--sp-text-2);
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.auth-switch {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--sp-text-2);
}

.auth-switch a {
  color: var(--sp-primary);
  font-weight: 600;
  text-decoration: none;
}
</style>