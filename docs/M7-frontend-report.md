# M7-5 前端优化检查报告

> 阶段：M7 系统整合与比赛交付 · 子任务 M7-5
> 日期：2026-11-18
> 原则：保留现有 iOS 高科技 SaaS 视觉设计，仅做针对性修复，不大规模重做 UI；所有结论基于真实运行的系统（后端 :8080 + 前端 dev :5173，登录账号 demo）。

## 一、检查范围与方法

- 代码审计：`src/utils/request.ts`、`src/router/index.ts`、`src/stores/{app,user}.ts`、`src/utils/auth.ts`、`src/layouts/{MainLayout,SideNav}.vue`、各业务视图与通用组件。
- 浏览器实测：通过 browser-use 在真实登录态下逐页导航（工作台 / 论文阅读 / 科研知识库 / AI 智能问答 / 学习计划 / 智能出题 / 个人设置），读取真实 DOM 文本、控制台消息、表单校验与主题切换。
- 构建验证：`npm run typecheck`（vue-tsc --noEmit）+ `npm run build`（vite 生产构建）。

## 二、逐项检查结果

| # | 检查项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | 真实 API（无 Mock） | 通过 | 全仓 grep 无 mock/faker/Math.random 造数；工作台明示"不使用任何模拟数据"。实测工作台统计来自真实 MySQL：知识库 2 / 论文文档 15 / 已解析 13 / 已向量化 5 / 问答会话 2 / 学习计划 4 / 已完成任务 1 / 练习次数 4。 |
| 2 | 加载状态 | 通过 | 各视图请求期间显示 loading（骨架/加载态），实测导航后 loading 收敛为 false。 |
| 3 | 空状态 | 通过 | 无记录显示 0 或诚实空状态（EmptyState）；知识库空态引导"创建第一个知识库"，测验空态 4 条诚实占位。 |
| 4 | 错误状态 | 通过 | `request.ts` 统一拦截：业务错误 `ElMessage.error(res.message)`；401→clearToken+跳转 /login；超时/网络异常友好提示。测验"生成失败"态展示"查看失败原因"。 |
| 5 | 成功反馈 | 通过 | 退出登录 `ElMessage.success('已退出登录')`；各保存/生成操作有成功提示与状态刷新。 |
| 6 | 表单校验 | 通过 | 登录/注册 `FormRules` required + blur 触发 + 提交前 `validate()`。实测知识库创建对话框空名提交→显示"请输入知识库名称"，阻止提交。 |
| 7 | 路由权限 | 通过 | `router.beforeEach`：非 public 且无 token→/login；已登录访问 /login、/register→/；catch-all→/；动态 document.title。实测刷新后 401→重定向登录。 |
| 8 | 浅色/深色主题 | 通过 | `app` store 主题持久化 localStorage `studypilot_theme`，初始化即 applyTheme（dataset.theme + .dark class）。实测切换深色→刷新保持→恢复浅色，`document.documentElement.dataset.theme` 正确。 |
| 9 | 响应式 | 通过 | `@media` 断点覆盖 MainLayout/Chat/Dashboard(1100/700)/Settings/Knowledge/Quiz/Reading/Plan(900)；移动端 `mobileNavOpen` + el-drawer 抽屉导航 + menu-btn。代码层面完整实现（隐藏浏览器面板无法真实缩放视口，未做运行时移动端截图）。 |
| 10 | 控制台错误 | 通过 | 逐页导航后 `list_console_messages(error,warn)` 返回空，无报错/警告。 |
| 11 | 刷新恢复 | 通过 | token 存 localStorage `studypilot_token`；MainLayout.onMounted 在 !userInfo 时 loadUser()。实测 reload→拦截器 401→跳登录→重新登录回工作台，状态正确恢复。 |
| 12 | 429 限流提示 | 通过 | 后端限流返回 HTTP200+code429+message"请求过于频繁，请稍后重试"，前端通用错误分支弹出该 message（M7-3 已联调验证）。 |

## 三、针对性修复（开发期里程碑残留文案）

发现并修复不适合比赛交付的开发期里程碑标记，均为小范围文本/属性调整，保留 iOS 视觉结构与组件样式：

| 文件 | 修复前 | 修复后 |
|------|--------|--------|
| `layouts/SideNav.vue` | 导航项 stage 徽章 M2/M2/M5/M6/M6 | 移除 stage 字段、徽章 span 与 `.nav-stage` 样式 |
| `components/common/PageHeader.vue` | `milestone` prop + el-tag 徽章 | 移除 milestone prop 与徽章渲染 |
| `views/reading/PaperReadingView.vue` | `milestone="M3"` | 移除 |
| `views/knowledge/KnowledgeView.vue` | `milestone="M4"`；副标题/空态"RAG 问答将在 M5 开放" | 移除徽章；文案改为"并可在 AI 智能问答中基于知识库进行 RAG 问答" |
| `views/chat/ChatView.vue` | `milestone="M5"` | 移除 |
| `views/plan/PlanView.vue` | `milestone="M6"` | 移除 |
| `views/quiz/QuizView.vue` | `milestone="M6"` | 移除 |
| `views/dashboard/DashboardView.vue` | 模块快捷入口 stage M2-M3/M2-M4/M5/M6/M6 | 统一改为"已开放"（6 个模块均已实现并测试） |
| `components/common/ChartCard.vue` | 空态默认"数据接口将在后续里程碑接入" | 改为"暂无数据" |
| `views/settings/SettingsView.vue` | "资料修改接口将在后续里程碑开放" | "资料修改功能暂未开放，当前展示来自 /users/me 的真实数据" |

说明：
- `PlanView` 中 `task.stage`/`stageQuiz` 为真实业务字段（学习阶段/阶段测验），非开发里程碑，未改动。
- `components/business/ModuleEmpty.vue` 为历史空态组件，当前无任何视图引用（死代码），未渲染，未改动。
- 个人设置中"修改密码 / 登录设备管理 / 资料修改"确为尚未实现功能，保留诚实"暂未开放/后续开放"标注，未标记为完成。

修复后浏览器实测：工作台 `hasMilestone=false`、模块"已开放"计数 6、`.nav-stage` 数量 0、页头里程碑标签 0；主题已恢复浅色。

## 四、构建验证（真实命令与结果）

| 命令 | 结果 |
|------|------|
| `npm run typecheck`（vue-tsc --noEmit） | 通过，无类型错误 |
| `npm run build`（vite 生产构建） | 通过，`✓ built in 11.44s`，产物输出 dist/ |

构建提示：element-plus（1086.50 kB / gzip 342.54 kB）、echarts（500.62 kB / gzip 168.24 kB）超过 500 kB 警告阈值。二者为第三方库，已通过 manualChunks 独立分包，属预期提示，非本次改动引入的回归。业务视图分包均 < 100 kB（ChatView 94.54 kB 含 marked/dompurify）。

## 五、结论

M7-5 前端优化检查 12 项全部通过；开发期里程碑残留文案已针对性清理，iOS 高科技 SaaS 视觉设计完整保留；typecheck 与生产构建均通过，控制台无错误。尚未实现的功能（资料修改/修改密码/设备管理）保持诚实标注，未伪造完成状态。