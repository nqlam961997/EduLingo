<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard, type DashboardData } from '../api/edulingo'
import { useAuthStore } from '../stores/auth'

const router  = useRouter()
const auth    = useAuthStore()
const data    = ref<DashboardData | null>(null)
const loading = ref(true)
const error   = ref('')

// ── constants ────────────────────────────────────────────────────────────────
const CEFR_LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']
const CEFR_FULL: Record<string, string> = {
  A1: 'Beginner', A2: 'Elementary', B1: 'Intermediate',
  B2: 'Upper-Intermediate', C1: 'Advanced', C2: 'Proficient'
}
const CEFR_BG: Record<string, string> = {
  A1: 'from-slate-500 to-slate-600',
  A2: 'from-blue-500 to-blue-600',
  B1: 'from-cyan-500 to-teal-600',
  B2: 'from-green-500 to-emerald-600',
  C1: 'from-violet-500 to-purple-600',
  C2: 'from-amber-500 to-orange-500',
}
const ERROR_COLOR: Record<string, string> = {
  Grammar: 'bg-red-400', Vocabulary: 'bg-blue-400',
  Spelling: 'bg-orange-400', Article: 'bg-purple-400',
  Tense: 'bg-pink-400', Structure: 'bg-teal-400',
}
const ERROR_BADGE: Record<string, string> = {
  Grammar: 'bg-red-50 text-red-700 border-red-200',
  Vocabulary: 'bg-blue-50 text-blue-700 border-blue-200',
  Spelling: 'bg-orange-50 text-orange-700 border-orange-200',
  Article: 'bg-purple-50 text-purple-700 border-purple-200',
  Tense: 'bg-pink-50 text-pink-700 border-pink-200',
  Structure: 'bg-teal-50 text-teal-700 border-teal-200',
}
const ERROR_ICON: Record<string, string> = {
  Grammar: '📝', Vocabulary: '📚', Spelling: '🔡',
  Article: '🔍', Tense: '⏰', Structure: '🏗️',
}
const SESSION_ICON: Record<string, string> = { CHAT: '💬', PICTURE: '🖼️' }
const SESSION_LABEL: Record<string, string> = { CHAT: 'Chat', PICTURE: 'Mô tả hình' }

// ── computed ─────────────────────────────────────────────────────────────────
const levelBg     = computed(() => data.value ? (CEFR_BG[data.value.cefrLevel] ?? 'from-blue-500 to-blue-600') : '')
const progressPct = computed(() => data.value ? Math.round(((data.value.cefrIndex + 1) / 6) * 100) : 0)
const nextLevel   = computed(() => data.value && data.value.cefrIndex < 5 ? CEFR_LEVELS[data.value.cefrIndex + 1] : null)
const maxErr      = computed(() => data.value?.topErrors.reduce((m, e) => Math.max(m, e.count), 1) ?? 1)

function scoreColor(s: number) {
  return s >= 70 ? 'text-green-600' : s >= 40 ? 'text-amber-500' : 'text-red-500'
}
function scoreBarColor(s: number) {
  return s >= 70 ? 'bg-green-500' : s >= 40 ? 'bg-amber-400' : 'bg-red-400'
}

onMounted(async () => {
  try { data.value = await getDashboard() }
  catch (e) { error.value = (e as Error).message }
  finally { loading.value = false }
})
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50">

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center h-64">
      <div class="w-8 h-8 border-4 border-blue-300 border-t-blue-600 rounded-full animate-spin" />
    </div>

    <div v-else-if="data" class="max-w-5xl mx-auto px-4 py-6 space-y-5">

      <!-- ══════════════════════════════════════════════════════════════════ -->
      <!-- 1. HERO — Level + Lộ trình cá nhân                               -->
      <!-- ══════════════════════════════════════════════════════════════════ -->
      <div :class="`bg-gradient-to-r ${levelBg} rounded-2xl p-6 text-white shadow-lg`">
        <div class="flex items-start justify-between flex-wrap gap-4">
          <div>
            <p class="text-white/70 text-sm">Xin chào 👋</p>
            <h1 class="text-2xl font-bold mt-0.5">{{ auth.user?.fullName ?? 'Learner' }}</h1>
            <p class="text-white/80 text-sm mt-1">🎯 {{ data.learningGoal }}</p>
            <p class="text-white/60 text-xs mt-1">Thành viên từ {{ data.memberSince }}</p>
          </div>
          <!-- CEFR badge -->
          <div class="text-center bg-white/20 rounded-2xl px-6 py-3">
            <p class="text-4xl font-black tracking-wide">{{ data.cefrLevel }}</p>
            <p class="text-sm text-white/80 font-medium">{{ CEFR_FULL[data.cefrLevel] }}</p>
          </div>
        </div>

        <!-- A1 → C2 progress bar -->
        <div class="mt-5">
          <div class="flex justify-between text-xs text-white/70 mb-1.5 px-0.5">
            <span v-for="l in CEFR_LEVELS" :key="l"
              :class="['transition', l === data.cefrLevel ? 'text-white font-bold scale-110 origin-bottom' : '']">
              {{ l }}
            </span>
          </div>
          <div class="relative h-2.5 bg-white/25 rounded-full overflow-hidden">
            <div class="h-full bg-white rounded-full transition-all duration-700"
              :style="{ width: progressPct + '%' }" />
          </div>
          <div class="flex justify-between mt-1.5 text-xs text-white/60">
            <span>{{ progressPct }}% hoàn thành lộ trình</span>
            <span v-if="nextLevel">Tiếp theo: <b class="text-white">{{ nextLevel }}</b></span>
            <span v-else>🏆 Trình độ cao nhất!</span>
          </div>
        </div>
      </div>

      <!-- ══════════════════════════════════════════════════════════════════ -->
      <!-- 2. STATS ROW — số buổi, điểm TB, chủ đề                         -->
      <!-- ══════════════════════════════════════════════════════════════════ -->
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div class="bg-white rounded-xl border shadow-sm p-4 text-center">
          <p class="text-3xl font-bold text-blue-600">{{ data.totalSessions }}</p>
          <p class="text-xs text-slate-500 mt-1">Buổi học</p>
        </div>
        <div class="bg-white rounded-xl border shadow-sm p-4 text-center">
          <p class="text-3xl font-bold" :class="scoreColor(data.averageScore)">
            {{ data.totalSessions > 0 ? data.averageScore : '—' }}
          </p>
          <p class="text-xs text-slate-500 mt-1">Điểm TB</p>
        </div>
        <div class="bg-white rounded-xl border shadow-sm p-4 text-center">
          <p class="text-3xl font-bold text-green-600">{{ data.topicsStudied }}</p>
          <p class="text-xs text-slate-500 mt-1">Chủ đề đã học</p>
        </div>
        <div class="bg-white rounded-xl border shadow-sm p-4 text-center">
          <p class="text-3xl font-bold text-violet-600">{{ data.totalErrorTypes }}</p>
          <p class="text-xs text-slate-500 mt-1">Loại lỗi ghi nhận</p>
        </div>
      </div>

      <!-- ══════════════════════════════════════════════════════════════════ -->
      <!-- 3. FOCUS TIP — cá nhân hóa theo lỗi                              -->
      <!-- ══════════════════════════════════════════════════════════════════ -->
      <div class="bg-amber-50 border border-amber-200 rounded-xl px-5 py-4 flex gap-3 items-start">
        <span class="text-2xl">💡</span>
        <div>
          <p class="font-semibold text-amber-800 text-sm">Gợi ý cá nhân cho bạn hôm nay</p>
          <p class="text-amber-700 text-sm mt-0.5">{{ data.focusTip }}</p>
        </div>
      </div>

      <!-- ══════════════════════════════════════════════════════════════════ -->
      <!-- 4. TWO-COLUMN: Lỗi hay gặp ←→ Lịch sử học                       -->
      <!-- ══════════════════════════════════════════════════════════════════ -->
      <div class="grid md:grid-cols-2 gap-5">

        <!-- ── Báo cáo lỗi cá nhân ────────────────────────────────────── -->
        <div class="bg-white rounded-2xl border shadow-sm p-5">
          <div class="flex items-center justify-between mb-4">
            <h2 class="font-semibold text-slate-800">📊 Báo cáo lỗi cá nhân</h2>
            <span class="text-xs text-slate-400 bg-slate-50 border rounded-full px-2 py-0.5">
              {{ data.totalErrorTypes }} loại
            </span>
          </div>

          <div v-if="data.topErrors.length === 0"
            class="flex flex-col items-center justify-center py-8 text-slate-400 text-center">
            <p class="text-4xl mb-2">🌱</p>
            <p class="text-sm">Chưa có dữ liệu</p>
            <p class="text-xs mt-1">Hãy bắt đầu luyện tập!</p>
          </div>

          <div v-else class="space-y-3.5">
            <div v-for="(err, i) in data.topErrors" :key="err.type">
              <div class="flex items-center justify-between mb-1">
                <span class="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                  <span>{{ ERROR_ICON[err.type] ?? '⚠️' }}</span>
                  <span>{{ err.type }}</span>
                  <span v-if="i === 0"
                    class="text-xs bg-red-100 text-red-600 px-1.5 py-0.5 rounded-full">Hay mắc nhất</span>
                </span>
                <span :class="`text-xs px-2 py-0.5 rounded-full border font-semibold ${ERROR_BADGE[err.type] ?? 'bg-slate-50 text-slate-600 border-slate-200'}`">
                  {{ err.count }}×
                </span>
              </div>
              <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
                <div :class="`h-full rounded-full transition-all duration-500 ${ERROR_COLOR[err.type] ?? 'bg-slate-400'}`"
                  :style="{ width: (err.count / maxErr * 100) + '%' }" />
              </div>
              <p v-if="err.example" class="text-xs text-slate-400 truncate pl-5 mt-0.5 italic">
                "{{ err.example }}"
              </p>
            </div>
          </div>
        </div>

        <!-- ── Lịch sử học ─────────────────────────────────────────────── -->
        <div class="bg-white rounded-2xl border shadow-sm p-5">
          <div class="flex items-center justify-between mb-4">
            <h2 class="font-semibold text-slate-800">📅 Lịch sử học</h2>
            <div class="flex gap-2 text-xs text-slate-500">
              <span class="flex items-center gap-1">💬 {{ data.chatSessions }} Chat</span>
              <span class="flex items-center gap-1">🖼️ {{ data.pictureSessions }} Hình</span>
            </div>
          </div>

          <div v-if="data.recentSessions.length === 0"
            class="flex flex-col items-center justify-center py-8 text-slate-400 text-center">
            <p class="text-4xl mb-2">📖</p>
            <p class="text-sm">Chưa có buổi học nào</p>
            <p class="text-xs mt-1">Bắt đầu một buổi chat hoặc mô tả hình!</p>
          </div>

          <div v-else class="space-y-2">
            <div v-for="(s, i) in data.recentSessions" :key="i"
              class="flex items-center gap-3 p-2.5 rounded-xl bg-slate-50 hover:bg-slate-100 transition">
              <span class="text-xl w-8 text-center">{{ SESSION_ICON[s.sessionType] ?? '📖' }}</span>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-slate-700 truncate">{{ s.topicName }}</p>
                <p class="text-xs text-slate-400">{{ SESSION_LABEL[s.sessionType] }} · {{ s.date }}</p>
              </div>
              <div v-if="s.score !== null && s.score !== undefined" class="text-right">
                <p class="text-sm font-bold" :class="scoreColor(s.score)">{{ s.score }}</p>
                <div class="w-12 h-1 bg-slate-200 rounded-full mt-0.5 overflow-hidden">
                  <div :class="`h-full rounded-full ${scoreBarColor(s.score)}`"
                    :style="{ width: s.score + '%' }" />
                </div>
              </div>
              <span v-else class="text-xs text-slate-400">—</span>
            </div>
          </div>
        </div>

      </div>

      <!-- ══════════════════════════════════════════════════════════════════ -->
      <!-- 5. QUICK ACTIONS                                                  -->
      <!-- ══════════════════════════════════════════════════════════════════ -->
      <div class="bg-white rounded-2xl border shadow-sm p-5">
        <h2 class="font-semibold text-slate-800 mb-3">🚀 Luyện tập ngay</h2>
        <div class="grid sm:grid-cols-2 gap-3">
          <button @click="router.push('/chat')"
            class="flex items-center gap-4 bg-blue-50 hover:bg-blue-100 border border-blue-200 rounded-xl px-5 py-4 transition text-left">
            <span class="text-3xl">💬</span>
            <div>
              <p class="font-semibold text-blue-800">Chat với AI</p>
              <p class="text-xs text-blue-500 mt-0.5">Hội thoại theo tình huống thực tế</p>
            </div>
            <span class="ml-auto text-blue-400 text-lg">→</span>
          </button>
          <button @click="router.push('/picture')"
            class="flex items-center gap-4 bg-green-50 hover:bg-green-100 border border-green-200 rounded-xl px-5 py-4 transition text-left">
            <span class="text-3xl">🖼️</span>
            <div>
              <p class="font-semibold text-green-800">Mô tả hình ảnh</p>
              <p class="text-xs text-green-500 mt-0.5">Luyện từ vựng qua quan sát</p>
            </div>
            <span class="ml-auto text-green-400 text-lg">→</span>
          </button>
          <button @click="router.push('/assessment')"
            class="flex items-center gap-4 bg-violet-50 hover:bg-violet-100 border border-violet-200 rounded-xl px-5 py-4 transition text-left sm:col-span-2">
            <span class="text-3xl">🎓</span>
            <div>
              <p class="font-semibold text-violet-800">Bài kiểm tra lên cấp</p>
              <p class="text-xs text-violet-500 mt-0.5">Làm bài thi để nâng CEFR level của bạn</p>
            </div>
            <div class="ml-auto flex items-center gap-2">
              <span class="text-xs bg-violet-200 text-violet-700 px-2 py-0.5 rounded-full font-medium">
                {{ data.cefrLevel }} → ?
              </span>
              <span class="text-violet-400 text-lg">→</span>
            </div>
          </button>
        </div>
      </div>

    </div>

    <p v-if="error" class="text-red-600 text-sm p-4 text-center">{{ error }}</p>
  </div>
</template>
