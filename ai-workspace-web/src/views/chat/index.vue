<template>
  <div class="chat-page">
    <!-- 会话列表侧边栏 -->
    <div class="session-sidebar">
      <div class="session-sidebar-header">
        <el-button type="primary" :icon="Plus" @click="handleCreateSession" style="width: 100%">
          新建对话
        </el-button>
      </div>

      <div class="session-list" v-loading="sessionsLoading">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: currentSession?.id === session.id }"
          @click="selectSession(session)"
        >
          <el-icon size="14" class="session-icon"><ChatDotRound /></el-icon>
          <span class="session-title">{{ session.title }}</span>
          <el-tooltip content="删除对话" placement="right">
            <el-button
              link
              :icon="Delete"
              size="small"
              class="session-del-btn"
              @click.stop="handleDeleteSession(session.id)"
            />
          </el-tooltip>
        </div>

        <el-empty v-if="!sessionsLoading && sessions.length === 0" description="暂无对话" :image-size="60" />
      </div>
    </div>

    <!-- 对话主区域 -->
    <div class="chat-main">
      <!-- 未选择对话时的空态 -->
      <div v-if="!currentSession" class="chat-empty-state">
        <el-icon size="64" color="#c0c4cc"><ChatDotRound /></el-icon>
        <p>选择左侧对话，或点击「新建对话」开始</p>
      </div>

      <template v-else>
        <!-- 对话标题栏 -->
        <div class="chat-header">
          <span class="chat-title">{{ currentSession.title }}</span>
          <div class="chat-header-actions">
            <el-tooltip content="清空消息" placement="bottom">
              <el-button link :icon="Delete" @click="clearMessages">清空</el-button>
            </el-tooltip>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesRef" class="messages-area">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="message-row"
            :class="msg.role"
          >
            <!-- AI 消息 -->
            <template v-if="msg.role === 'assistant'">
              <el-avatar class="msg-avatar ai-avatar" :icon="Cpu" :size="36" />
              <div class="msg-bubble ai-bubble">
                <div class="markdown-body" v-html="renderMd(msg.content)" />
              </div>
            </template>
            <!-- 用户消息 -->
            <template v-else>
              <div class="msg-bubble user-bubble">
                <span>{{ msg.content }}</span>
              </div>
              <el-avatar class="msg-avatar user-avatar" :icon="UserFilled" :size="36" />
            </template>
          </div>

          <!-- 流式输出中 -->
          <div v-if="streaming" class="message-row assistant">
            <el-avatar class="msg-avatar ai-avatar" :icon="Cpu" :size="36" />
            <div class="msg-bubble ai-bubble">
              <div class="markdown-body" v-html="renderMd(streamingContent)" />
              <span class="cursor-blink">▋</span>
            </div>
          </div>

          <div v-if="messages.length === 0 && !streaming" class="messages-empty">
            <el-icon size="40" color="#dcdfe6"><ChatDotRound /></el-icon>
            <p>发送消息开始对话</p>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="chat-input-area">
          <div class="input-toolbar">
            <el-select
              v-model="selectedModel"
              placeholder="选择模型"
              size="small"
              style="width: 160px"
            >
              <el-option
                v-for="m in models"
                :key="m.id"
                :label="m.modelName"
                :value="m.modelName"
              />
            </el-select>
            <span class="input-tip">Ctrl + Enter 发送</span>
          </div>
          <div class="input-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              placeholder="输入消息..."
              resize="none"
              @keydown.ctrl.enter.prevent="handleSend"
            />
            <el-button
              v-if="streaming"
              type="danger"
              :icon="CircleClose"
              class="send-btn"
              @click="handleStop"
            >
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :icon="Promotion"
              :disabled="!inputText.trim()"
              class="send-btn"
              @click="handleSend"
            >
              发送
            </el-button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { Plus, Delete, Cpu, UserFilled, Promotion, CircleClose } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import type { ChatSession, ChatMessage, ChatModel } from '@/types'
import { listModels, listSessions, createSession, deleteSession, listMessages, sendMessageStream } from '@/api/chat'

const md = new MarkdownIt({ html: false, linkify: true, typographer: true })
const renderMd = (content: string) => md.render(content || '')

const sessionsLoading = ref(false)
const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const streamingContent = ref('')
const models = ref<ChatModel[]>([])
const selectedModel = ref('')
const messagesRef = ref<HTMLElement>()
let streamController: AbortController | null = null

async function loadModels() {
  try {
    models.value = await listModels()
    if (models.value.length && !selectedModel.value) {
      selectedModel.value = models.value[0].modelName
    }
  } catch {
    models.value = []
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

onMounted(() => {
  loadModels()
  loadSessions()
})

async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = await listSessions()
  } catch {
    sessions.value = []
  } finally {
    sessionsLoading.value = false
  }
}

async function selectSession(session: ChatSession) {
  currentSession.value = session
  messages.value = []
  try {
    messages.value = await listMessages(session.id)
  } catch {
    messages.value = []
  }
  scrollToBottom()
}

async function handleCreateSession() {
  try {
    const session = await createSession({ title: '新对话', modelName: selectedModel.value })
    sessions.value.unshift(session)
    selectSession(session)
  } catch {
    ElMessage.error('创建对话失败')
  }
}

async function handleDeleteSession(id: number) {
  await ElMessageBox.confirm('确定删除该对话吗？', '删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).catch(() => { throw new Error('cancel') })

  try {
    await deleteSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSession.value?.id === id) {
      currentSession.value = null
      messages.value = []
    }
    ElMessage.success('删除成功')
  } catch (e) {
    if ((e as Error).message !== 'cancel') ElMessage.error('删除失败')
  }
}

function clearMessages() {
  ElMessageBox.confirm('确定清空当前对话记录吗？', '清空确认', {
    confirmButtonText: '确定清空',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    messages.value = []
  }).catch(() => {})
}

async function handleSend() {
  const content = inputText.value.trim()
  if (!content || streaming.value || !currentSession.value) return

  inputText.value = ''
  messages.value.push({ role: 'user', content })
  await scrollToBottom()

  streaming.value = true
  streamingContent.value = ''
  streamController = new AbortController()

  sendMessageStream(
    currentSession.value.id,
    content,
    (text) => {
      streamingContent.value += text
      scrollToBottom()
    },
    () => {
      if (streamingContent.value) {
        messages.value.push({ role: 'assistant', content: streamingContent.value })
      }
      streaming.value = false
      streamingContent.value = ''
      streamController = null
      scrollToBottom()
    },
    (err) => {
      streaming.value = false
      streamingContent.value = ''
      streamController = null
      ElMessage.error('发送失败：' + err)
    },
    streamController.signal
  )
}

function handleStop() {
  streamController?.abort()
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 100px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

/* 会话侧边栏 */
.session-sidebar {
  width: 240px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
}

.session-sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: #606266;
  font-size: 13px;
  transition: background 0.2s;
  position: relative;
}

.session-item:hover {
  background: #ecf5ff;
  color: #409EFF;
}

.session-item.active {
  background: #ecf5ff;
  color: #409EFF;
  font-weight: 500;
}

.session-icon {
  flex-shrink: 0;
}

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-del-btn {
  opacity: 0;
  flex-shrink: 0;
}

.session-item:hover .session-del-btn {
  opacity: 1;
}

/* 对话主区域 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #c0c4cc;
  font-size: 14px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #ebeef5;
}

.chat-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

/* 消息列表 */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.messages-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #c0c4cc;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  flex-shrink: 0;
}

.ai-avatar {
  background: #409EFF;
}

.user-avatar {
  background: #67C23A;
}

.msg-bubble {
  max-width: 72%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.ai-bubble {
  background: #f5f7fa;
  border-radius: 2px 12px 12px 12px;
}

.user-bubble {
  background: #409EFF;
  color: #fff;
  border-radius: 12px 2px 12px 12px;
}

.cursor-blink {
  display: inline-block;
  animation: blink 0.8s step-end infinite;
  color: #409EFF;
  font-weight: bold;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* 输入区域 */
.chat-input-area {
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.input-tip {
  font-size: 12px;
  color: #c0c4cc;
}

.input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: 8px;
  font-size: 14px;
}

.send-btn {
  height: 78px;
  padding: 0 20px;
}
</style>
