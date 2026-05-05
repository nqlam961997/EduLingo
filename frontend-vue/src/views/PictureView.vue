<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import TopicPicker from '../components/TopicPicker.vue'
import {
  generatePicture, getPictureQuestions, submitPictureAnswer, recordSession,
  type Topic, type GeneratedImage, type QuestionListResponse, type AnswerFeedbackResponse
} from '../api/edulingo'

// ── state ─────────────────────────────────────────────────────────────────────
const selectedTopic   = ref<Topic | null>(null)
const image           = ref<GeneratedImage | null>(null)
const questionData    = ref<QuestionListResponse | null>(null)
const currentIndex    = ref(0)
const answer          = ref('')
const feedback        = ref<AnswerFeedbackResponse | null>(null)
const scores          = ref<number[]>([])
const generating      = ref(false)
const submitting      = ref(false)
const error           = ref('')
const showSummary     = ref(false)

// ── computed ──────────────────────────────────────────────────────────────────
const totalQuestions  = computed(() => questionData.value?.questions.length ?? 0)
const currentQuestion = computed(() => questionData.value?.questions[currentIndex.value] ?? '')
const progressPct     = computed(() => totalQuestions.value ? ((currentIndex.value) / totalQuestions.value) * 100 : 0)
const averageScore    = computed(() => scores.value.length
  ? Math.round(scores.value.reduce((a, b) => a + b, 0) / scores.value.length)
  : 0)

const scoreColor = (s: number) =>
  s >= 70 ? 'text-green-600' : s >= 40 ? 'text-amber-500' : 'text-red-500'

// ── actions ───────────────────────────────────────────────────────────────────
async function onTopic(topic: Topic) {
  selectedTopic.value = topic
  generating.value = true
  error.value = ''
  try {
    image.value = await generatePicture(topic.id)
    questionData.value = await getPictureQuestions(image.value.imageId)
    currentIndex.value = 0
    scores.value = []
    feedback.value = null
    showSummary.value = false
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    generating.value = false
  }
}

async function submitAnswer() {
  if (!image.value || !answer.value.trim() || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const fb = await submitPictureAnswer(image.value.imageId, currentIndex.value, answer.value.trim())
    feedback.value = fb
    scores.value.push(fb.score)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    submitting.value = false
  }
}

function nextQuestion() {
  const next = currentIndex.value + 1
  if (next >= totalQuestions.value) {
    showSummary.value = true
    if (selectedTopic.value) {
      recordSession('PICTURE', selectedTopic.value.id, selectedTopic.value.name, averageScore.value)
    }
  } else {
    currentIndex.value = next
    feedback.value = null
    answer.value = ''
  }
}

function reset() {
  selectedTopic.value = null
  image.value = null
  questionData.value = null
  currentIndex.value = 0
  answer.value = ''
  feedback.value = null
  scores.value = []
  showSummary.value = false
  error.value = ''
}

async function newRound() {
  if (selectedTopic.value) onTopic(selectedTopic.value)
}

// ── Voice input ───────────────────────────────────────────────────────────────
const isRecording  = ref(false)
const micError     = ref('')
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let recognition: any = null

function toggleVoiceInput() {
  micError.value = ''

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  if (!SR) {
    micError.value = 'Trình duyệt không hỗ trợ nhận giọng nói'
    return
  }

  if (isRecording.value) {
    recognition?.stop()
    return
  }

  recognition = new SR()
  recognition.lang = 'en-US'
  recognition.continuous = false
  recognition.interimResults = true
  recognition.maxAlternatives = 1

  recognition.onstart = () => { isRecording.value = true }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  recognition.onresult = (event: any) => {
    const transcript = Array.from(event.results as any[])
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .map((r: any) => r[0].transcript)
      .join('')
    answer.value = transcript
  }

  recognition.onend = () => { isRecording.value = false; recognition = null }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  recognition.onerror = (event: any) => {
    isRecording.value = false
    recognition = null
    if (event.error !== 'aborted') micError.value = `Lỗi mic: ${event.error}`
  }

  recognition.start()
}

onUnmounted(() => recognition?.stop())
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50">
    <!-- Topic picker -->
    <TopicPicker v-if="!selectedTopic" @select="onTopic" />

    <template v-else>
      <!-- Header -->
      <div class="bg-white border-b px-4 py-2 flex items-center justify-between shadow-sm">
        <button @click="reset" class="flex items-center gap-1 text-slate-500 hover:text-slate-800 text-sm">
          <span class="text-lg">←</span>
          <span class="font-medium text-slate-700">{{ selectedTopic.icon }} {{ selectedTopic.name }}</span>
        </button>
        <div class="flex gap-3">
          <button @click="newRound" :disabled="generating"
            class="text-sm text-blue-600 hover:text-blue-800 disabled:opacity-40">New image</button>
          <button @click="reset" class="text-sm text-slate-400 hover:text-slate-600">Change topic</button>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="generating" class="flex flex-col items-center justify-center h-64 gap-3 text-slate-400">
        <div class="w-8 h-8 border-4 border-blue-300 border-t-blue-600 rounded-full animate-spin"></div>
        <p class="text-sm">Loading picture...</p>
      </div>

      <!-- Summary screen -->
      <div v-else-if="showSummary" class="max-w-lg mx-auto p-8 text-center">
        <div class="bg-white rounded-2xl shadow p-8 space-y-4">
          <div class="text-5xl">🎉</div>
          <h2 class="text-2xl font-bold text-slate-800">Well done!</h2>
          <p class="text-slate-500">You completed all {{ totalQuestions }} questions.</p>
          <div class="flex items-center justify-center gap-2 mt-2">
            <span class="text-5xl font-bold" :class="scoreColor(averageScore)">{{ averageScore }}</span>
            <span class="text-slate-400 text-lg">/ 100</span>
          </div>
          <div class="flex gap-1 justify-center mt-2">
            <div v-for="(s, i) in scores" :key="i"
              class="h-2 flex-1 rounded-full"
              :class="s >= 70 ? 'bg-green-400' : s >= 40 ? 'bg-amber-400' : 'bg-red-400'" />
          </div>
          <div class="flex gap-3 justify-center mt-4">
            <button @click="newRound"
              class="bg-blue-600 text-white px-6 py-2.5 rounded-xl font-medium hover:bg-blue-700">
              New image
            </button>
            <button @click="reset"
              class="border border-slate-200 text-slate-600 px-6 py-2.5 rounded-xl hover:bg-slate-50">
              Change topic
            </button>
          </div>
        </div>
      </div>

      <!-- Q&A screen -->
      <div v-else-if="image && questionData" class="h-[calc(100vh-56px)] flex flex-col">

        <!-- Progress bar -->
        <div class="px-4 pt-3 pb-1 bg-white border-b">
          <div class="flex items-center justify-between text-xs text-slate-400 mb-1">
            <span>Scene picture (detail)</span>
            <span>{{ currentIndex + 1 }}/{{ totalQuestions }}</span>
          </div>
          <div class="h-1.5 bg-gray-100 rounded-full overflow-hidden">
            <div class="h-full bg-green-500 rounded-full transition-all duration-500"
              :style="{ width: progressPct + '%' }" />
          </div>
        </div>

        <!-- Main split layout -->
        <div class="flex flex-1 min-h-0">

          <!-- Left: image -->
          <div class="flex-1 flex items-center justify-center p-4 min-w-0">
            <img
              :src="`data:${image.mimeType};base64,${image.base64Data}`"
              class="max-h-full max-w-full rounded-xl shadow-md object-contain"
              alt="Describe this picture" />
          </div>

          <!-- Right: Q&A panel -->
          <div class="w-80 flex flex-col border-l bg-white p-4 gap-3 overflow-y-auto">

            <!-- AI question bubble -->
            <div class="flex gap-2 items-start">
              <div class="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0 text-sm">🤖</div>
              <div class="bg-green-500 text-white rounded-2xl rounded-tl-none px-4 py-3 text-sm leading-relaxed shadow-sm">
                {{ currentQuestion }}
              </div>
            </div>

            <!-- Feedback bubble (after submit) -->
            <template v-if="feedback">
              <div class="flex gap-2 items-start flex-row-reverse">
                <div class="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center flex-shrink-0 text-sm">🧑</div>
                <div class="bg-blue-50 border border-blue-100 rounded-2xl rounded-tr-none px-4 py-3 text-sm text-slate-700">
                  {{ answer }}
                </div>
              </div>

              <!-- AI feedback -->
              <div class="flex gap-2 items-start">
                <div class="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0 text-sm">🤖</div>
                <div class="flex-1 space-y-2">
                  <div class="bg-white border rounded-2xl rounded-tl-none px-4 py-3 text-sm text-slate-700 shadow-sm">
                    <p>{{ feedback.feedback }}</p>
                    <div v-if="feedback.corrected" class="mt-2 p-2 bg-green-50 rounded-lg border border-green-100">
                      <span class="text-xs text-green-600 font-medium">Better:</span>
                      <p class="text-green-700 text-sm mt-0.5">{{ feedback.corrected }}</p>
                    </div>
                  </div>
                  <div class="flex items-center gap-2">
                    <div class="h-1.5 flex-1 bg-gray-100 rounded-full overflow-hidden">
                      <div class="h-full rounded-full transition-all"
                        :class="feedback.score >= 70 ? 'bg-green-500' : feedback.score >= 40 ? 'bg-amber-400' : 'bg-red-400'"
                        :style="{ width: feedback.score + '%' }" />
                    </div>
                    <span class="text-xs font-semibold" :class="scoreColor(feedback.score)">
                      {{ feedback.score }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- Next button -->
              <button @click="nextQuestion"
                class="mt-auto w-full bg-blue-600 hover:bg-blue-700 text-white rounded-xl py-2.5 font-medium text-sm transition">
                {{ currentIndex + 1 >= totalQuestions ? '🏁 Finish' : 'Next question →' }}
              </button>
            </template>

            <!-- Spacer -->
            <div class="flex-1" />

            <!-- Answer input area (when no feedback yet) -->
            <div v-if="!feedback" class="space-y-2">
              <textarea v-model="answer" rows="3"
                placeholder="Type your answer here..."
                class="w-full border border-slate-200 rounded-xl px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-300"
                @keydown.enter.ctrl="submitAnswer" />

              <!-- Mic error -->
              <p v-if="micError" class="text-xs text-red-500">{{ micError }}</p>

              <div class="flex items-center gap-2">
                <!-- Mic button -->
                <button
                  @click="toggleVoiceInput"
                  :class="[
                    isRecording
                      ? 'bg-red-500 hover:bg-red-600 animate-pulse ring-2 ring-red-300'
                      : 'bg-pink-500 hover:bg-pink-600',
                    'w-11 h-11 rounded-full text-white flex items-center justify-center shadow transition'
                  ]"
                  :title="isRecording ? 'Đang nghe — nhấn để dừng' : 'Nhấn để nói tiếng Anh'"
                >
                  {{ isRecording ? '⏹' : '🎤' }}
                </button>

                <!-- Submit button -->
                <button @click="submitAnswer"
                  :disabled="submitting || !answer.trim()"
                  class="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-40 text-white rounded-xl py-2.5 font-medium text-sm transition">
                  {{ submitting ? 'Checking...' : 'Submit' }}
                </button>
              </div>
            </div>

          </div>
        </div>

      </div>

      <p v-if="error" class="text-red-600 text-sm p-4">{{ error }}</p>
    </template>
  </div>
</template>
