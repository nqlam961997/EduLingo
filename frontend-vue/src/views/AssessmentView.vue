<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  startAssessment, submitAssessment,
  type AssessmentStartResponse, type AssessmentResultResponse
} from '../api/edulingo'

const router  = useRouter()

// ── state ─────────────────────────────────────────────────────────────────────
type Phase = 'intro' | 'quiz' | 'result'
const phase      = ref<Phase>('intro')
const assessment = ref<AssessmentStartResponse | null>(null)
const result     = ref<AssessmentResultResponse | null>(null)
const answers    = ref<number[]>([])
const current    = ref(0)
const chosen     = ref<number | null>(null)
const confirmed  = ref(false)
const loading    = ref(false)
const error      = ref('')

// ── computed ──────────────────────────────────────────────────────────────────
const question   = computed(() => assessment.value?.questions[current.value])
const total      = computed(() => assessment.value?.questions.length ?? 0)
const progressPct = computed(() => total.value ? (current.value / total.value) * 100 : 0)
const isLast     = computed(() => current.value === total.value - 1)

const CEFR_BG: Record<string, string> = {
  A1: 'from-slate-500 to-slate-600', A2: 'from-blue-500 to-blue-600',
  B1: 'from-cyan-500 to-teal-600',  B2: 'from-green-500 to-emerald-600',
  C1: 'from-violet-500 to-purple-600', C2: 'from-amber-500 to-orange-500',
}
const levelBg = computed(() => {
  const lv = result.value?.newLevel ?? assessment.value?.currentLevel ?? 'A2'
  return CEFR_BG[lv] ?? 'from-blue-500 to-blue-600'
})

// ── lifecycle ─────────────────────────────────────────────────────────────────
onMounted(async () => {
  loading.value = true
  try { assessment.value = await startAssessment() }
  catch (e) { error.value = (e as Error).message }
  finally { loading.value = false }
})

// ── actions ───────────────────────────────────────────────────────────────────
function selectOption(idx: number) {
  if (confirmed.value) return
  chosen.value = idx
}

function confirmAnswer() {
  if (chosen.value === null) return
  confirmed.value = true
  answers.value[current.value] = chosen.value
}

function nextQuestion() {
  if (isLast.value) {
    submitQuiz()
  } else {
    current.value++
    chosen.value = null
    confirmed.value = false
  }
}

async function submitQuiz() {
  loading.value = true
  try {
    result.value = await submitAssessment(answers.value)
    phase.value = 'result'
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function optionClass(idx: number) {
  if (!confirmed.value) {
    return chosen.value === idx
      ? 'border-blue-500 bg-blue-50 text-blue-800'
      : 'border-slate-200 hover:border-blue-300 hover:bg-blue-50 cursor-pointer'
  }
  if (idx === question.value?.correct) return 'border-green-500 bg-green-50 text-green-800'
  if (idx === chosen.value)            return 'border-red-400 bg-red-50 text-red-700'
  return 'border-slate-200 text-slate-400'
}
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 flex flex-col items-center justify-start py-8 px-4">

    <!-- Loading -->
    <div v-if="loading" class="flex flex-col items-center justify-center h-64 gap-3 text-slate-400">
      <div class="w-8 h-8 border-4 border-blue-300 border-t-blue-600 rounded-full animate-spin" />
      <p class="text-sm">{{ phase === 'quiz' ? 'Đang chấm điểm...' : 'Đang tải bài kiểm tra...' }}</p>
    </div>

    <!-- ══════════════════════════════════════════════════════════════════════ -->
    <!-- INTRO                                                                 -->
    <!-- ══════════════════════════════════════════════════════════════════════ -->
    <div v-else-if="assessment && phase === 'intro'" class="max-w-lg w-full space-y-5">
      <div v-if="assessment.questions.length === 0"
        class="bg-white rounded-2xl border shadow-sm p-8 text-center space-y-4">
        <span class="text-6xl">🏆</span>
        <h2 class="text-2xl font-bold text-amber-700">Bạn đã đạt C2!</h2>
        <p class="text-slate-500">Trình độ Proficient — cấp độ cao nhất trong hệ thống.</p>
        <button @click="router.push('/dashboard')"
          class="bg-amber-500 hover:bg-amber-600 text-white rounded-xl px-6 py-2.5 font-medium">
          Về Dashboard
        </button>
      </div>

      <template v-else>
        <div :class="`bg-gradient-to-r ${CEFR_BG[assessment.currentLevel] ?? 'from-blue-500 to-blue-600'} rounded-2xl p-6 text-white shadow-lg text-center`">
          <p class="text-white/70 text-sm mb-1">Bài kiểm tra lên cấp</p>
          <div class="flex items-center justify-center gap-4 my-3">
            <span class="text-3xl font-black bg-white/20 rounded-xl px-4 py-2">{{ assessment.currentLevel }}</span>
            <span class="text-2xl">→</span>
            <span class="text-3xl font-black bg-white/20 rounded-xl px-4 py-2">{{ assessment.targetLevel }}</span>
          </div>
          <p class="text-white/80 text-sm">{{ assessment.description }}</p>
        </div>

        <div class="bg-white rounded-2xl border shadow-sm p-6 space-y-3">
          <h3 class="font-semibold text-slate-800">Hướng dẫn</h3>
          <ul class="text-sm text-slate-600 space-y-2">
            <li class="flex items-start gap-2"><span>📝</span> {{ total }} câu hỏi trắc nghiệm 4 lựa chọn</li>
            <li class="flex items-start gap-2"><span>⏱️</span> Không giới hạn thời gian</li>
            <li class="flex items-start gap-2"><span>🎯</span> Đạt ≥ 80% ({{ Math.ceil(total * 0.8) }}/{{ total }} câu) để lên cấp</li>
            <li class="flex items-start gap-2"><span>💡</span> Sau mỗi câu sẽ có giải thích chi tiết</li>
          </ul>
        </div>

        <button @click="phase = 'quiz'"
          class="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-xl py-3.5 font-semibold text-lg shadow transition">
          Bắt đầu kiểm tra 🚀
        </button>
      </template>
    </div>

    <!-- ══════════════════════════════════════════════════════════════════════ -->
    <!-- QUIZ                                                                  -->
    <!-- ══════════════════════════════════════════════════════════════════════ -->
    <div v-else-if="assessment && phase === 'quiz' && question" class="max-w-lg w-full space-y-4">

      <!-- Header -->
      <div class="flex items-center justify-between text-sm text-slate-500">
        <span class="font-medium text-slate-700">Câu {{ current + 1 }} / {{ total }}</span>
        <span class="text-xs bg-blue-50 text-blue-600 px-2 py-1 rounded-full font-medium">
          {{ assessment.currentLevel }} → {{ assessment.targetLevel }}
        </span>
      </div>

      <!-- Progress bar -->
      <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
        <div class="h-full bg-blue-500 rounded-full transition-all duration-500"
          :style="{ width: progressPct + '%' }" />
      </div>

      <!-- Question card -->
      <div class="bg-white rounded-2xl border shadow-sm p-6 space-y-5">
        <p class="text-lg font-medium text-slate-800 leading-relaxed">
          {{ question.question }}
        </p>

        <div class="space-y-3">
          <button
            v-for="(opt, idx) in question.options" :key="idx"
            @click="selectOption(idx)"
            :class="`w-full text-left flex items-center gap-3 border-2 rounded-xl px-4 py-3 transition text-sm font-medium ${optionClass(idx)}`">
            <span class="w-7 h-7 rounded-lg border-2 flex items-center justify-center flex-shrink-0 text-xs font-bold"
              :class="confirmed && idx === question.correct ? 'border-green-500 bg-green-500 text-white'
                    : confirmed && idx === chosen && idx !== question.correct ? 'border-red-400 bg-red-400 text-white'
                    : chosen === idx && !confirmed ? 'border-blue-500 bg-blue-500 text-white'
                    : 'border-current'">
              {{ ['A','B','C','D'][idx] }}
            </span>
            {{ opt }}
            <span v-if="confirmed && idx === question.correct" class="ml-auto">✅</span>
            <span v-else-if="confirmed && idx === chosen && idx !== question.correct" class="ml-auto">❌</span>
          </button>
        </div>

        <!-- Explanation (after confirm) -->
        <div v-if="confirmed" class="bg-blue-50 border border-blue-100 rounded-xl p-3">
          <p class="text-sm text-blue-800">
            <span class="font-semibold">💡 Giải thích: </span>{{ question.explanation }}
          </p>
        </div>

        <!-- Buttons -->
        <div class="flex gap-3">
          <button v-if="!confirmed"
            @click="confirmAnswer"
            :disabled="chosen === null"
            class="flex-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-40 text-white rounded-xl py-2.5 font-semibold transition">
            Xác nhận
          </button>
          <button v-else
            @click="nextQuestion"
            class="flex-1 bg-green-600 hover:bg-green-700 text-white rounded-xl py-2.5 font-semibold transition">
            {{ isLast ? '📊 Xem kết quả' : 'Câu tiếp theo →' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ══════════════════════════════════════════════════════════════════════ -->
    <!-- RESULT                                                                -->
    <!-- ══════════════════════════════════════════════════════════════════════ -->
    <div v-else-if="result && phase === 'result'" class="max-w-lg w-full space-y-5">

      <!-- Score hero -->
      <div :class="`bg-gradient-to-r ${levelBg} rounded-2xl p-6 text-white shadow-lg text-center`">
        <p class="text-5xl mb-2">{{ result.leveledUp ? '🎉' : result.score >= 60 ? '👍' : '💪' }}</p>
        <p class="text-xl font-bold mt-2">{{ result.message }}</p>

        <div class="flex items-center justify-center gap-6 mt-4">
          <div class="text-center">
            <p class="text-4xl font-black">{{ result.correctCount }}<span class="text-2xl text-white/70">/{{ result.totalCount }}</span></p>
            <p class="text-white/70 text-xs mt-1">Câu đúng</p>
          </div>
          <div class="w-px h-12 bg-white/30" />
          <div class="text-center">
            <p class="text-4xl font-black">{{ result.score }}%</p>
            <p class="text-white/70 text-xs mt-1">Điểm số</p>
          </div>
          <template v-if="result.leveledUp">
            <div class="w-px h-12 bg-white/30" />
            <div class="text-center">
              <p class="text-sm text-white/70">Level mới</p>
              <p class="text-3xl font-black">{{ result.newLevel }}</p>
            </div>
          </template>
        </div>

        <!-- level up animation -->
        <div v-if="result.leveledUp" class="mt-4 bg-white/20 rounded-xl px-4 py-2 flex items-center justify-center gap-3">
          <span class="text-xl font-bold line-through opacity-60">{{ result.previousLevel }}</span>
          <span class="text-xl">→</span>
          <span class="text-2xl font-black">{{ result.newLevel }}</span>
          <span>🆙</span>
        </div>
      </div>

      <!-- Detailed results -->
      <div class="bg-white rounded-2xl border shadow-sm p-5">
        <h3 class="font-semibold text-slate-800 mb-3">Chi tiết từng câu</h3>
        <div class="space-y-2 max-h-72 overflow-y-auto pr-1">
          <div v-for="(r, i) in result.results" :key="r.questionId"
            class="flex items-start gap-3 p-3 rounded-xl"
            :class="r.isCorrect ? 'bg-green-50' : 'bg-red-50'">
            <span class="text-lg flex-shrink-0">{{ r.isCorrect ? '✅' : '❌' }}</span>
            <div class="min-w-0 flex-1">
              <p class="text-sm font-medium text-slate-700">Câu {{ i + 1 }}: {{ r.question }}</p>
              <p v-if="!r.isCorrect" class="text-xs text-red-600 mt-0.5">
                Bạn chọn: <b>{{ assessment?.questions[i]?.options[r.chosen] ?? '—' }}</b>
                · Đúng: <b class="text-green-700">{{ assessment?.questions[i]?.options[r.correct] }}</b>
              </p>
              <p class="text-xs text-slate-500 mt-0.5 italic">{{ r.explanation }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Actions -->
      <div class="flex gap-3">
        <button @click="router.push('/dashboard')"
          class="flex-1 bg-blue-600 hover:bg-blue-700 text-white rounded-xl py-3 font-semibold transition">
          Về Dashboard
        </button>
        <button @click="router.push('/chat')"
          class="flex-1 border-2 border-blue-200 text-blue-700 hover:bg-blue-50 rounded-xl py-3 font-semibold transition">
          Luyện tập tiếp 💬
        </button>
      </div>
    </div>

    <p v-if="error" class="text-red-500 text-sm mt-4">{{ error }}</p>
  </div>
</template>
