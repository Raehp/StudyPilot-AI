<template>
  <div class="settings">
    <PageHeader title="个人设置" subtitle="账号资料、液态玻璃外观与安全设置" />

    <div class="settings-grid">
      <!-- 个人信息 -->
      <div class="panel glass">
        <div class="panel-head">
          <el-icon><User /></el-icon>个人信息
        </div>
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
        <div class="panel-note">资料来自 /users/me 接口的真实数据。</div>
      </div>

      <!-- 账号安全：修改密码 -->
      <div class="panel glass">
        <div class="panel-head">
          <el-icon><Lock /></el-icon>账号安全
        </div>
        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="pwdRules"
          label-position="top"
          size="large"
          @keyup.enter="onChangePassword"
        >
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="pwdForm.oldPassword"
              type="password"
              placeholder="请输入当前密码"
              show-password
              :prefix-icon="Lock"
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="pwdForm.newPassword"
              type="password"
              placeholder="6-32位，与注册密码要求一致"
              show-password
              :prefix-icon="Key"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              show-password
              :prefix-icon="Key"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="pwdLoading"
            @click="onChangePassword"
          >
            修改密码
          </el-button>
        </el-form>
        <div class="panel-note">
          <el-icon><WarningFilled /></el-icon>
          修改成功后将自动退出登录，需使用新密码重新登录。
        </div>
      </div>

      <!-- 外观：液态玻璃实时调节 -->
      <div class="panel glass appearance-panel">
        <div class="panel-head">
          <el-icon><MagicStick /></el-icon>外观 · Liquid Glass
          <el-button class="reset-btn" size="small" round @click="onResetGlass">
            <el-icon><RefreshLeft /></el-icon>恢复默认
          </el-button>
        </div>

        <div class="appearance-body">
          <div class="theme-col">
            <div class="sub-head">主题模式</div>
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

            <div class="sub-head switch-head">材质效果</div>
            <div class="switch-row">
              <span class="switch-label">氛围光流动</span>
              <el-switch
                :model-value="appStore.glass.aurora"
                @change="(v: any) => appStore.setGlass({ aurora: !!v })"
              />
            </div>
            <div class="switch-row">
              <span class="switch-label">颗粒质感</span>
              <el-switch
                :model-value="appStore.glass.grain"
                @change="(v: any) => appStore.setGlass({ grain: !!v })"
              />
            </div>
            <div class="switch-row">
              <span class="switch-label">照片底图</span>
              <el-switch
                :model-value="appStore.glass.photo"
                @change="(v: any) => appStore.setGlass({ photo: !!v })"
              />
            </div>

            <div class="glass-preview">
              <div class="preview-tile glass" data-lens-blur-scale="0.05">
                <div class="card-art art-line" aria-hidden="true"></div>
                <span class="preview-title">实时预览</span>
                <span class="preview-sub">玻璃材质随参数即时变化</span>
              </div>
            </div>
          </div>

          <div class="sliders-col">
            <div class="slider-row">
              <div class="slider-label">
                <span>卡片透明度</span>
                <span class="slider-value">{{ Math.round(appStore.glass.alpha * 100) }}%</span>
              </div>
              <el-slider
                :model-value="appStore.glass.alpha"
                :min="0.08"
                :max="0.92"
                :step="0.01"
                :show-tooltip="false"
                @input="(v: any) => appStore.setGlass({ alpha: v as number })"
              />
            </div>
            <div class="slider-row">
              <div class="slider-label">
                <span>背景模糊</span>
                <span class="slider-value">{{ appStore.glass.blur }}px</span>
              </div>
              <el-slider
                :model-value="appStore.glass.blur"
                :min="4"
                :max="40"
                :step="1"
                :show-tooltip="false"
                @input="(v: any) => appStore.setGlass({ blur: v as number })"
              />
            </div>
            <div class="slider-row">
              <div class="slider-label">
                <span>镜面高光</span>
                <span class="slider-value">{{ Math.round(appStore.glass.specular * 100) }}%</span>
              </div>
              <el-slider
                :model-value="appStore.glass.specular"
                :min="0"
                :max="1.5"
                :step="0.05"
                :show-tooltip="false"
                @input="(v: any) => appStore.setGlass({ specular: v as number })"
              />
            </div>
            <div class="slider-row">
              <div class="slider-label">
                <span>色彩饱和</span>
                <span class="slider-value">{{ appStore.glass.saturation }}%</span>
              </div>
              <el-slider
                :model-value="appStore.glass.saturation"
                :min="100"
                :max="260"
                :step="5"
                :show-tooltip="false"
                @input="(v: any) => appStore.setGlass({ saturation: v as number })"
              />
            </div>
            <div class="slider-row">
              <div class="slider-label">
                <span>氛围色相</span>
                <span class="slider-value">{{ appStore.glass.hue }}°</span>
              </div>
              <el-slider
                :model-value="appStore.glass.hue"
                :min="0"
                :max="360"
                :step="5"
                :show-tooltip="false"
                @input="(v: any) => appStore.setGlass({ hue: v as number })"
              />
            </div>
            <div class="slider-row">
              <div class="slider-label">
                <span>边缘折射</span>
                <span class="slider-value">{{ appStore.glass.refraction }}</span>
              </div>
              <el-slider
                :model-value="appStore.glass.refraction"
                :min="0"
                :max="120"
                :step="1"
                :show-tooltip="false"
                @input="(v: any) => appStore.setGlass({ refraction: v as number })"
              />
            </div>
            <div class="slider-row">
              <div class="slider-label">
                <span>折射边带</span>
                <span class="slider-value">{{ appStore.glass.lensEdge }}</span>
              </div>
              <el-slider
                :model-value="appStore.glass.lensEdge"
                :min="10"
                :max="50"
                :step="1"
                :show-tooltip="false"
                @input="(v: any) => appStore.setGlass({ lensEdge: v as number })"
              />
            </div>
            <div class="panel-note">所有参数保存在本机，拖动即时生效，无需刷新。</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Key, MagicStick, RefreshLeft, WarningFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { changePassword } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const pwdFormRef = ref<FormInstance>()
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为6-32位', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && value === pwdForm.oldPassword) {
          callback(new Error('新密码不能与原密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function onChangePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await ElMessageBox.confirm(
      '修改密码成功后将自动退出登录，需要使用新密码重新登录。确认修改？',
      '修改密码',
      { type: 'warning', confirmButtonText: '确认修改', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，正在退出登录…')
    userStore.clearSession()
    router.replace('/login')
  } catch {
    // 错误提示已由拦截器统一处理
  } finally {
    pwdLoading.value = false
  }
}

function onResetGlass() {
  appStore.resetGlass()
  ElMessage.success('已恢复默认玻璃材质')
}
</script>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-items: start;
}

.panel {
  padding: 22px;
}

.appearance-panel {
  grid-column: 1 / -1;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 14px;
}

.panel-head .el-icon {
  color: var(--sp-primary);
}

.reset-btn {
  margin-left: auto;
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
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--sp-text-3);
  line-height: 1.6;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

/* 外观面板 */
.appearance-body {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 28px;
}

.sub-head {
  font-size: 13px;
  font-weight: 600;
  color: var(--sp-text-2);
  margin-bottom: 10px;
}

.switch-head {
  margin-top: 20px;
}

.theme-row {
  display: flex;
  gap: 12px;
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
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.theme-option:hover {
  transform: translateY(-2px);
}

.theme-option.active {
  border-color: var(--sp-primary);
}

.theme-preview {
  width: 100%;
  height: 60px;
  border-radius: 10px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.light-preview {
  background: #e9edf5;
}

.dark-preview {
  background: #05060b;
}

.theme-preview span {
  height: 8px;
  border-radius: 4px;
}

.light-preview span:nth-child(1),
.dark-preview span:nth-child(1) {
  width: 40%;
  background: linear-gradient(90deg, #0a84ff, #af52de);
}

.light-preview span:nth-child(2) {
  width: 80%;
  background: #ffffff;
}

.light-preview span:nth-child(3) {
  width: 60%;
  background: #ffffff;
}

.dark-preview span:nth-child(2) {
  width: 80%;
  background: #2c2c32;
}

.dark-preview span:nth-child(3) {
  width: 60%;
  background: #2c2c32;
}

.theme-name {
  font-size: 13px;
  font-weight: 600;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 2px;
}

.switch-label {
  font-size: 14px;
}

.glass-preview {
  margin-top: 20px;
}

.preview-tile {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-title {
  font-size: 14px;
  font-weight: 700;
}

.preview-sub {
  font-size: 12px;
  color: var(--sp-text-2);
}

.sliders-col {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.slider-row {
  padding: 4px 0;
}

.slider-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 2px;
}

.slider-value {
  color: var(--sp-primary);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .appearance-body {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}
</style>