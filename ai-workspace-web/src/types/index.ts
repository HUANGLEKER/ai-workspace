/**
 * 全局 TypeScript 类型定义
 *
 * 与后端 DTO / 数据库表字段保持对应，按业务模块分组。
 * 所有列表接口均使用 PageResult<T> 包装（无分页时后端返回 total=records.length）。
 */

/** 分页返回结构，与后端 common.PageResult 对应 */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

// 认证相关
export interface LoginRequest {
  username: string
  password: string
  // 滑块验证通过后下发的一次性令牌；后端开启验证码时必填
  captchaToken?: string
}

// 滑块拼图验证：init 返回底图/拼图块（base64 data URI）与拼图块定位
export interface SlideCaptchaInit {
  captchaId: string
  masterImage: string
  tileImage: string
  tileX: number
  tileY: number
  tileWidth: number
}

export interface SlideCaptchaVerifyResult {
  success: boolean
  captchaToken?: string
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  nickname: string
  roles: string[]
}

export interface UserInfo {
  userId: number
  username: string
  nickname: string
  email?: string
  /** 后端返回时已临时签名为 1h 可访问 URL（库中存的是 MinIO 对象路径） */
  avatar: string
  roles: string[]
  isAdmin: boolean
  /** 是否需强制改密（默认密码未改）；前端据此弹出强制改密引导 */
  mustChangePwd?: boolean
}

// 对话相关
export interface ChatSession {
  id: number
  userId: number
  title: string
  modelName: string
  /** 会话级系统提示词（来自提示词中心），空表示未绑定 */
  systemPrompt?: string
  createTime: string
  updateTime: string
}

/**
 * 聊天模型配置，完全由 Spring Boot 的 ChatModelController 管理。
 * FastAPI 不读此表，选定的模型名通过请求载荷透传。
 * apiKey 在列表响应中由后端脱敏，编辑时留空则不修改原有值。
 */
export interface ChatModel {
  id?: number
  modelName: string
  provider: string
  /** 留空则使用 AI 服务 .env 中的 LLM_API_BASE */
  apiUrl?: string
  /** 前端编辑时留空表示不修改，后端永远不返回明文 */
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
  /** RAG 问答消息携带的引用来源（普通 chat 消息无此字段） */
  sources?: RagSource[]
}

/**
 * 知识库问答会话，对应后端 rag_session 表。
 * 与 ChatSession 同构，额外绑定一个知识库（kbId）。
 */
export interface RagSession {
  id: number
  userId: number
  kbId: number
  title: string
  modelName: string
  /** 会话级系统提示词（来自提示词中心），空表示未绑定 */
  systemPrompt?: string
  createTime: string
  updateTime: string
}

/** Token 用量统计（由 chat SSE 的 {"type":"usage"} 帧携带，通常在流尾到达） */
export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

// 知识库相关
export interface KnowledgeBase {
  id: number
  kbName: string
  description: string
  createBy: number
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
  /** 异步嵌入管道状态：PENDING → PROCESSING → DONE / FAILED */
  status: 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'
  createTime: string
}

// RAG 检索来源（流式问答时由 sources 帧携带）
export interface RagSource {
  document_id: string
  file_name: string
  content: string
  score: number
  /** rerank 精排分数（启用 rerank 时存在） */
  rerank_score?: number
  /** 是否被答案实际引用（命中来源高亮） */
  cited?: boolean
}

// 文件相关
export interface FileInfo {
  id: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  uploadBy: number
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
  /** 用户备注 */
  remark?: string
  status: number
  createTime?: string
}
