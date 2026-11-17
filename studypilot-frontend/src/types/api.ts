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
  createTime: string
  updateTime: string
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