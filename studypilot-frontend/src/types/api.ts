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
