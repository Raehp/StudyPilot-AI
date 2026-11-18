<template>
  <div class="auth-page">
    <div class="auth-bg" aria-hidden="true"></div>
    <div class="auth-card glass">
      <div class="auth-logo">
        <div class="logo-icon">
          <el-icon :size="26"><Compass /></el-icon>
        </div>
        <h1>创建账号</h1>
        <p>加入 StudyPilot AI，开启智能科研之旅</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="onSubmit"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-32位字母/数字/下划线）" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称（选填）" :prefix-icon="Avatar" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（6-32位）"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
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
          {{ loading ? '注册中…' : '注册并进入' }}
        </SpecularButton>
      </el-form>

      <div class="auth-switch">
        已有账号？
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import SpecularButton from '@/components/common/SpecularButton.vue'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,32}$/, message: '3-32位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为6-32位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined
    })
    ElMessage.success('注册成功，欢迎加入 StudyPilot AI')
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
  width: 420px;
  max-width: 100%;
  padding: 36px 36px 28px;
}

.auth-logo {
  text-align: center;
  margin-bottom: 24px;
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
  font-size: 22px;
  font-weight: 700;
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
  margin-top: 18px;
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