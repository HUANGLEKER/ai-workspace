<template>
  <div class="rag-page">
    <!-- 顶部：知识库选择 -->
    <div class="rag-header">
      <div class="rag-title">
        <el-icon size="18" color="#409EFF"><Search /></el-icon>
        <span>知识库问答</span>
      </div>
      <el-select
        v-model="selectedKbId"
        placeholder="选择知识库"
        size="default"
        style="width: 240px"
        :loading="kbLoading"
        @change="resetConversation"
      >
        <el-option
          v-for="kb in knowledgeBases"
          :key="kb.id"
          :label="kb.kbName"
          :value="kb.id"
        />
      </el-select>
      <el-tooltip content="清空问答" placement="bottom">
        <el-button link :icon="Delete" :disabled="streaming" @click="resetConversation">清空</el-button>
      </el-tooltip>
    </div>

    <!-- 问答区 -->
    <div ref="answersRef" class="answers-area">
      <div v-if="!selectedKbId" class="rag-empty">
        <el-icon size="56" color="#c0c4cc"><Reading /></el-icon>
        <p>请先在上方选择一个知识库</p>
      </div>

      <div v-else-if="turns.length === 0 && !streaming" class="rag-empty">
        <el-icon size="56" color="#dcdfe6"><Search /></el-icon>
        <p>基于「{{ selectedKbName }}」提问，回答将引用文档内容</p>
      </div>

      <div v-for="(turn, idx) in turns" :key="idx" class="qa-turn">
        <!-- 问题 -->
        <div class="qa-question">
          <el-avatar class="qa-avatar user-avatar" :icon="UserFilled" :size="32" />
          <div class="qa-q-bubble">{{ turn.question }}</div>
        </div>

        <!-- 回答 -->
        <div class="qa-answer">
          <el-avatar class="qa-avatar ai-avatar" :icon="Cpu" :size="32" />
          <div class="qa-a-bubble">
            <div class="markdown-body" v-html="renderMd(turn.answer)" />
            <span v-if="streaming && idx === turns.length - 1 && !turn.answer" class="cursor-blink">▋</span>

            <!-- 引用来源 -->
            <div v-if="turn.sources.length" class="qa-sources">
              <div class="qa-sources-title">
                <el-icon size="13"><Document /></el-icon>
                引用来源（{{ turn.sources.length }}）
              </div>
              <el-collapse>
                <el-collapse-item
                  v-for="(src, sIdx) in turn.sources"
                  :key="sIdx"
                  :name="sIdx"
                >
                  <template #title>
                    <span class="src-name">{{ src.file_name || '未知文档' }}</span>
                    <el-tag size="small" type="info" effect="plain" class="src-score">
                      相关度 {{ (src.score * 100).toFixed(0) }}%
                    </el-tag>
                  </template>
                  <div class="src-content">{{ src.content }}</div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="rag-input-area">
      <el-input
        v-model="question"
        type="textarea"
        :rows="2"
        :disabled="!selectedKbId"
        placeholder="输入你的问题，Ctrl + Enter 提问"
        resize="none"
        @keydown.ctrl.enter.prevent="handleAsk"
      />
      <el-button
        v-if="streaming"
        type="danger"
        :icon="CircleClose"
        class="ask-btn"
        @click="handleStop"
      >
        停止
      </el-button>
      <el-button
        v-else
        type="primary"
        :icon="Promotion"
        :disabled="!question.trim() || !selectedKbId"
        class="ask-btn"
        @click="handleAsk"
      >
        提问
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { Search, Reading, Document, Delete, Cpu, UserFilled, Promotion, CircleClose } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import type { KnowledgeBase, RagSource } from '@/types'
import { listKnowledgeBases, ragChatStream } from '@/api/kb'

interface QaTurn {
  question: string
  answer: string
  sources: RagSource[]
}

const md = new MarkdownIt({ html: false, linkify: true, typographer: true })
const renderMd = (content: string) => md.render(content || '')

const kbLoading = ref(false)
const knowledgeBases = ref<KnowledgeBase[]>([])
const selectedKbId = ref<number>()
const question = ref('')
const streaming = ref(false)
const turns = ref<QaTurn[]>([])
const answersRef = ref<HTMLElement>()
let streamController: AbortController | null = null

const selectedKbName = computed(
  () => knowledgeBases.value.find((kb) => kb.id === selectedKbId.value)?.kbName ?? ''
)

onMounted(loadKbs)

async function loadKbs() {
  kbLoading.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  } finally {
    kbLoading.value = false
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (answersRef.value) {
    answersRef.value.scrollTop = answersRef.value.scrollHeight
  }
}

function resetConversation() {
  if (streaming.value) return
  turns.value = []
  question.value = ''
}

function handleAsk() {
  const q = question.value.trim()
  if (!q || streaming.value || !selectedKbId.value) return

  question.value = ''
  const turn: QaTurn = { question: q, answer: '', sources: [] }
  turns.value.push(turn)
  scrollToBottom()

  streaming.value = true
  streamController = new AbortController()

  ragChatStream(
    { kbId: selectedKbId.value, question: q, sessionId: `rag-${selectedKbId.value}` },
    (text) => {
      turn.answer += text
      scrollToBottom()
    },
    (sources) => {
      turn.sources = sources
    },
    () => {
      streaming.value = false
      streamController = null
      scrollToBottom()
    },
    (err) => {
      streaming.value = false
      streamController = null
      if (!turn.answer) turns.value.pop()
      ElMessage.error('问答失败：' + err)
    },
    streamController.signal
  )
}

function handleStop() {
  streamController?.abort()
}
</script>

<style scoped>
.rag-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.rag-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  border-bottom: 1px solid #ebeef5;
}

.rag-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-right: auto;
}

.answers-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.rag-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #c0c4cc;
  font-size: 14px;
}

.qa-turn {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.qa-question,
.qa-answer {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.qa-question {
  flex-direction: row-reverse;
}

.qa-avatar {
  flex-shrink: 0;
}

.ai-avatar {
  background: #409EFF;
}

.user-avatar {
  background: #67C23A;
}

.qa-q-bubble {
  max-width: 72%;
  padding: 10px 14px;
  background: #409EFF;
  color: #fff;
  border-radius: 12px 2px 12px 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.qa-a-bubble {
  max-width: 80%;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 2px 12px 12px 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
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

.qa-sources {
  margin-top: 12px;
  border-top: 1px dashed #dcdfe6;
  padding-top: 8px;
}

.qa-sources-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.src-name {
  font-size: 13px;
  color: #303133;
  margin-right: 10px;
}

.src-score {
  margin-left: auto;
}

.src-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
}

.rag-input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
}

.rag-input-area :deep(.el-textarea__inner) {
  border-radius: 8px;
  font-size: 14px;
}

.ask-btn {
  height: 56px;
  padding: 0 22px;
}
</style>
