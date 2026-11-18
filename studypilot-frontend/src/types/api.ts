export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  major: string
  createTime: string
}

export interface LoginVO {
  token: string
  user: UserVO
}

export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO {
  username: string
  password: string
  nickname?: string
}

export interface ChangePasswordDTO {
  oldPassword: string
  newPassword: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface KnowledgeBaseVO {
  id: number
  name: string
  description: string
  docCount: number
  createTime: string
  updateTime: string
}

export interface KnowledgeBaseDTO {
  name: string
  description?: string
}

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export type VectorStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export interface DocumentVO {
  id: number
  kbId: number
  title: string
  fileName: string
  fileSize: number
  pageCount: number
  charCount: number
  needOcr: boolean
  status: DocumentStatus
  errorMsg: string
  vectorStatus: VectorStatus
  chunkCount: number
  embeddingCount: number
  vectorErrorMsg: string
  createTime: string
  updateTime: string
}

export interface VectorStatusVO {
  documentId: number
  /** null = 从未处理 */
  status: VectorStatus | null
  chunkCount: number
  embeddingCount: number
  progress: number
  errorMsg: string
  updateTime: string
}

export interface VectorSearchResultVO {
  chunkId: number
  chunkUid: string
  documentId: number
  documentTitle: string
  content: string
  pageNum: number
  sourcePosition: string
  score: number
}

export interface ParseStatusVO {
  documentId: number
  status: DocumentStatus
  pageCount: number
  charCount: number
  needOcr: boolean
  errorMsg: string
  updateTime: string
}

export interface PageText {
  pageNum: number
  text: string
}

export interface DocumentContentVO {
  documentId: number
  title: string
  pageCount: number
  charCount: number
  needOcr: boolean
  pages: PageText[]
}
export type AnalysisStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export interface PaperAnalysisResult {
  title: string
  abstract: string
  background: string
  researchQuestion: string
  methods: string
  technicalRoute: string
  innovations: string
  experimentDesign: string
  experimentResults: string
  conclusion: string
  limitations: string
  futureWork: string
}

export interface AnalysisStatusVO {
  analysisId: number | null
  documentId: number
  status: AnalysisStatus | null
  model: string | null
  errorMsg: string | null
  durationMs: number | null
  segmentCount: number | null
  createTime: string | null
  updateTime: string | null
}

export interface PaperAnalysisVO {
  analysisId: number
  documentId: number
  documentTitle: string
  model: string
  status: AnalysisStatus
  result: PaperAnalysisResult
  summary: string
  durationMs: number
  segmentCount: number
  createTime: string
  updateTime: string
}

export type MessageStatus = 'GENERATING' | 'SUCCESS' | 'FAILED' | 'STOPPED'

export interface CitationVO {
  documentId: number
  documentName: string
  chunkId: number
  chunkUid: string
  pageNum: number | null
  originalContent: string
  similarityScore: number
}

export interface ConversationVO {
  id: number
  kbId: number
  kbName: string
  title: string
  createTime: string
  updateTime: string
}

export interface ChatMessageVO {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  citations: CitationVO[]
  tokenCount: number
  status: MessageStatus
  errorMsg: string
  createTime: string
}

export interface ChatStreamMeta {
  userMessageId: number
  assistantMessageId: number
  retrievalCount: number
  citations: CitationVO[]
}

export type GenerateStatus = 'NONE' | 'GENERATING' | 'SUCCESS' | 'FAILED'

export interface GenerateStatusVO {
  id: number
  status: GenerateStatus
  errorMsg: string
  updateTime: string
}

export interface StudyPlanVO {
  id: number
  title: string
  goal: string
  topic: string
  kbId: number
  kbName: string
  startDate: string | null
  endDate: string | null
  dailyMinutes: number
  currentLevel: string
  expectedOutcome: string
  status: number
  generateStatus: GenerateStatus
  generateError: string
  progress: number
  aiAdvice: string
  taskCount: number
  completedCount: number
  createTime: string
  updateTime: string
}

export interface StudyPlanCreateDTO {
  title: string
  goal?: string
  topic?: string
  kbId?: number
  startDate?: string | null
  endDate?: string | null
  dailyMinutes?: number
  currentLevel?: string
  expectedOutcome?: string
}

export interface StudyPlanUpdateDTO extends StudyPlanCreateDTO {
  status?: number
}

export interface StudyPlanProgressVO {
  planId: number
  total: number
  completed: number
  progress: number
  status: number
}

export interface StudyTaskVO {
  id: number
  planId: number
  stage: string
  taskDate: string | null
  title: string
  content: string
  focus: string
  recommendedContent: string
  durationMin: number
  stageQuiz: boolean
  status: number
  sortOrder: number
}

export interface StudyTaskUpdateDTO {
  title?: string
  content?: string
  focus?: string
  recommendedContent?: string
  stage?: string
  taskDate?: string | null
  durationMin?: number
  stageQuiz?: number
  sortOrder?: number
}

export interface QuizQuestionVO {
  id: number
  type: number
  difficulty: number
  stem: string
  options: string[]
  knowledgePoint: string
  sourceDocId: number
  sourceDocTitle: string
  sourceContent: string | null
  sortOrder: number
  answer: string | null
  analysis: string | null
  userAnswer: string | null
  isCorrect: number | null
  score: number | null
  scoreBasis: string | null
}

export interface QuizListItemVO {
  id: number
  title: string
  topic: string
  kbId: number
  kbName: string
  difficulty: number
  questionCount: number
  status: number
  generateStatus: GenerateStatus
  score: number
  createTime: string
}

export interface QuizDetailVO {
  id: number
  title: string
  topic: string
  kbId: number
  kbName: string
  difficulty: number
  questionTypes: number[]
  questionCount: number
  status: number
  generateStatus: GenerateStatus
  generateError: string
  score: number
  createTime: string
  questions: QuizQuestionVO[]
}

export interface QuizResultVO {
  quizId: number
  title: string
  score: number
  questionCount: number
  correctCount: number
  status: number
  questions: QuizQuestionVO[]
}

export interface WrongAnswerVO {
  questionId: number
  quizId: number
  quizTitle: string
  type: number
  stem: string
  options: string[]
  answer: string
  analysis: string
  userAnswer: string
  isCorrect: number
  score: number
  scoreBasis: string
  knowledgePoint: string
  sourceDocId: number
  sourceDocTitle: string
  sourceContent: string
  createTime: string
}

export interface QuizGenerateDTO {
  kbId: number
  topic?: string
  title?: string
  questionCount?: number
  difficulty?: number
  questionTypes: number[]
}

export interface QuizSubmitDTO {
  answers: { questionId: number; answer: string }[]
}

export interface StatItemVO {
  label: string
  value: number
}

export interface TrendItemVO {
  date: string
  count: number
}

export interface DashboardOverviewVO {
  documentTotal: number
  knowledgeBaseTotal: number
  parsedDocumentTotal: number
  vectorizedDocumentTotal: number
  conversationTotal: number
  studyPlanTotal: number
  completedTaskTotal: number
  totalTaskCount: number
  quizPracticeTotal: number
  learningProgress: number
}

export interface DocumentStatisticsVO {
  total: number
  parsed: number
  vectorized: number
  parseFailed: number
  pending: number
  parseStatusDistribution: StatItemVO[]
  vectorStatusDistribution: StatItemVO[]
  uploadTrend: TrendItemVO[]
}

export interface PlanProgressVO {
  planId: number
  title: string
  progress: number
  total: number
  completed: number
}

export interface ActivityTrendVO {
  dates: string[]
  documents: number[]
  tasks: number[]
  quizzes: number[]
  conversations: number[]
}

export interface LearningStatisticsVO {
  planTotal: number
  taskTotal: number
  completedTaskTotal: number
  avgProgress: number
  planProgressList: PlanProgressVO[]
  completionTrend: TrendItemVO[]
  activityTrend: ActivityTrendVO
}

export interface RecentScoreVO {
  quizId: number
  title: string
  score: number
  createTime: string
}

export interface QuizStatisticsVO {
  quizTotal: number
  practicedTotal: number
  avgScore: number
  scoreDistribution: StatItemVO[]
  questionTypeDistribution: StatItemVO[]
  practiceTrend: TrendItemVO[]
  recentScores: RecentScoreVO[]
}
