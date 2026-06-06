export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

// 认证相关
export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  email: string
  phone: string
  status: number
  createTime: string
  roles: string[]
}

// 对话相关
export interface ChatSession {
  id: number
  userId: number
  title: string
  modelName: string
  createTime: string
  updateTime: string
}

export interface ChatModel {
  id?: number
  modelName: string
  provider: string
  apiUrl?: string
  apiKey?: string
  enabled: number
  createTime?: string
  updateTime?: string
}

export interface ChatMessage {
  id?: number
  sessionId?: number
  role: 'user' | 'assistant' | 'system'
  content: string
  tokenCount?: number
  createTime?: string
}

// 知识库相关
export interface KnowledgeBase {
  id: number
  kbName: string
  description: string
  createBy: string
  createTime: string
  documentCount?: number
}

export interface KbDocument {
  id: number
  kbId: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  status: 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'
  createTime: string
}

// RAG 检索来源（流式问答时由 sources 帧携带）
export interface RagSource {
  document_id: string
  file_name: string
  content: string
  score: number
}

// 文件相关
export interface FileInfo {
  id: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  uploadBy: string
  createTime: string
}

// 系统用户
export interface SysUser {
  id?: number
  username: string
  password?: string
  nickname: string
  avatar?: string
  email: string
  phone: string
  status: number
  createTime?: string
}
