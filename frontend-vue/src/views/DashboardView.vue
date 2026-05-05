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

// ── Error improvement details ────────────────────────────────────────────────
interface ErrorDetail { why: string; tips: string[]; exercise: string; route: string; routeLabel: string }
const ERROR_DETAILS: Record<string, ErrorDetail> = {
  Grammar: {
    why: 'Thường xảy ra khi chia sai động từ, thiếu chủ ngữ hoặc dùng sai cấu trúc câu.',
    tips: ['Chú ý động từ phải chia theo chủ ngữ (He/She/It → thêm -s)', 'Cấu trúc cơ bản: Subject + Verb + Object', 'Đọc to câu vừa viết — câu sai thường nghe "lạ"'],
    exercise: 'Viết 3 câu mô tả việc bạn đang làm, nhờ AI Chat sửa lỗi ngay.',
    route: '/chat', routeLabel: '💬 Luyện Grammar qua Chat'
  },
  Vocabulary: {
    why: 'Thiếu từ vựng khiến bạn dùng sai nghĩa hoặc không diễn đạt được ý muốn.',
    tips: ['Học 5 từ mới mỗi ngày theo chủ đề phù hợp trình độ', 'Dùng Flashcard: xem từ → đoán nghĩa → lật thẻ kiểm tra', 'Đặt câu với từ mới ngay sau khi học để nhớ lâu'],
    exercise: 'Học bộ từ vựng phù hợp trình độ của bạn hôm nay.',
    route: '/vocabulary', routeLabel: '📚 Học từ vựng ngay'
  },
  Spelling: {
    why: 'Thường do không quen mặt chữ, hoặc phát âm sai dẫn đến viết sai theo âm.',
    tips: ['Dùng Listening mode: nghe từ → gõ lại → xem kết quả', 'Chú ý cặp hay nhầm: their/there, your/you\'re, its/it\'s', 'Đọc to từng từ khi gõ để tự kiểm tra'],
    exercise: 'Thực hành Listening — nghe từ và gõ lại chính xác từng chữ.',
    route: '/vocabulary', routeLabel: '🎧 Luyện Listening'
  },
  Tense: {
    why: 'Nhầm lẫn giữa các thì, đặc biệt Present/Past/Present Perfect là lỗi rất phổ biến.',
    tips: ['Present Simple: thói quen (I eat rice every day)', 'Past Simple: đã xong (I ate rice yesterday)', 'Present Perfect: kinh nghiệm/vừa xong (I have eaten)'],
    exercise: 'Kể lại ngày hôm qua cho AI nghe, chỉ dùng Past Simple.',
    route: '/chat', routeLabel: '💬 Luyện Tense qua Chat'
  },
  Article: {
    why: 'Mạo từ a/an/the không có trong tiếng Việt nên người học hay bỏ qua hoặc dùng sai.',
    tips: ['"a/an" → lần đầu nhắc tới: I saw a dog', '"the" → đã biết hoặc chỉ 1: The dog was friendly', 'Không dùng "the" với danh từ số nhiều chung: Dogs are cute'],
    exercise: 'Mô tả một bức ảnh và chú ý dùng a/the đúng khi nhắc đến đồ vật.',
    route: '/picture', routeLabel: '🖼️ Luyện qua Mô tả hình'
  },
  Structure: {
    why: 'Câu thiếu chủ ngữ/vị ngữ, hoặc ghép câu quá dài không đúng cách.',
    tips: ['Mỗi câu cần: Chủ ngữ + Động từ tối thiểu (I run / She eats)', 'Nối câu bằng: because, although, so, but, when, if', 'Câu quá dài → chia thành nhiều câu ngắn rõ ràng hơn'],
    exercise: 'Viết 3 câu mô tả ngôi nhà bạn, dùng ít nhất 1 liên từ.',
    route: '/chat', routeLabel: '💬 Luyện cấu trúc câu qua Chat'
  },
}

const selectedError = ref<{ type: string; count: number; example: string } | null>(null)

const ERROR_GRADIENTS: Record<string, string> = {
  Grammar:    '#ef4444,#f43f5e',
  Vocabulary: '#3b82f6,#6366f1',
  Spelling:   '#f97316,#f59e0b',
  Article:    '#a855f7,#7c3aed',
  Tense:      '#ec4899,#f43f5e',
  Structure:  '#14b8a6,#0d9488',
}
function errorModalHeaderStyle(type: string) {
  const colors = ERROR_GRADIENTS[type] ?? '#64748b,#475569'
  const [c1, c2] = colors.split(',')
  return { background: `linear-gradient(135deg, ${c1}, ${c2})` }
}

// ── computed ─────────────────────────────────────────────────────────────────
const levelBg     = computed(() => data.value ? (CEFR_BG[data.value.cefrLevel] ?? 'from-blue-500 to-blue-600') : '')
const progressPct = computed(() => data.value ? Math.round(((data.value.cefrIndex + 1) / 6) * 100) : 0)
const nextLevel   = computed(() => data.value && data.value.cefrIndex < 5 ? CEFR_LEVELS[data.value.cefrIndex + 1] : null)
const maxErr      = computed(() => data.value?.topErrors.reduce((m, e) => Math.max(m, e.count), 1) ?? 1)

interface Rec { icon: string; title: string; reason: string; route: string; badge: string; bg: string; border: string; text: string; btnBg: string }
const todayRecs = computed<Rec[]>(() => {
  if (!data.value) return []
  const d = data.value
  const lvl = d.cefrLevel
  const topErr = d.topErrors[0]?.type ?? ''
  const recs: Rec[] = []

  // ── rec 1: dựa trên lỗi hay mắc ──────────────────────────────────────────
  if (topErr === 'Vocabulary') {
    recs.push({ icon: '📚', title: `Từ vựng ${lvl}`, reason: `Bạn hay mắc lỗi Vocabulary — học thêm từ mới giúp cải thiện nhanh`, route: `/vocabulary/level/${lvl}`, badge: lvl, bg: 'bg-green-50', border: 'border-green-200', text: 'text-green-800', btnBg: 'bg-green-500 hover:bg-green-600' })
  } else if (topErr === 'Spelling') {
    recs.push({ icon: '🎧', title: `Listening ${lvl}`, reason: `Bạn hay mắc lỗi Spelling — luyện nghe & gõ lại từ sẽ giúp ích`, route: `/vocabulary/level/${lvl}`, badge: 'Listening', bg: 'bg-cyan-50', border: 'border-cyan-200', text: 'text-cyan-800', btnBg: 'bg-cyan-500 hover:bg-cyan-600' })
  } else if (topErr === 'Grammar' || topErr === 'Tense' || topErr === 'Structure') {
    recs.push({ icon: '💬', title: 'Luyện Grammar qua Chat', reason: `Bạn hay mắc lỗi ${topErr} — hội thoại với AI và nhận sửa lỗi ngay`, route: '/chat', badge: topErr, bg: 'bg-blue-50', border: 'border-blue-200', text: 'text-blue-800', btnBg: 'bg-blue-500 hover:bg-blue-600' })
  } else {
    // Chưa có lỗi hoặc lỗi khác → gợi ý từ vựng theo level
    recs.push({ icon: '📚', title: `Từ vựng ${lvl}`, reason: `Ôn tập từ vựng phù hợp trình độ ${lvl} của bạn`, route: `/vocabulary/level/${lvl}`, badge: lvl, bg: 'bg-green-50', border: 'border-green-200', text: 'text-green-800', btnBg: 'bg-green-500 hover:bg-green-600' })
  }

  // ── rec 2: xoay vòng Chat / Picture (chưa luyện hoặc ít luyện hơn) ───────
  if (d.pictureSessions <= d.chatSessions) {
    recs.push({ icon: '🖼️', title: 'Mô tả hình ảnh', reason: `Luyện diễn đạt qua quan sát thực tế — kỹ năng quan trọng ở ${lvl}`, route: '/picture', badge: 'Mới', bg: 'bg-amber-50', border: 'border-amber-200', text: 'text-amber-800', btnBg: 'bg-amber-500 hover:bg-amber-600' })
  } else {
    recs.push({ icon: '💬', title: 'Chat với AI', reason: `Hội thoại theo tình huống thực tế, AI sửa lỗi cho bạn ngay`, route: '/chat', badge: 'Thực hành', bg: 'bg-blue-50', border: 'border-blue-200', text: 'text-blue-800', btnBg: 'bg-blue-500 hover:bg-blue-600' })
  }

  // ── rec 3: kiểm tra lên cấp nếu học đủ nhiều ──────────────────────────────
  if (d.totalSessions >= 3 && lvl !== 'C2') {
    recs.push({ icon: '🎓', title: `Thi lên ${nextLevel.value ?? '?'}`, reason: `Bạn đã học ${d.totalSessions} buổi — thử sức bài kiểm tra để lên level!`, route: '/assessment', badge: `${lvl} → ${nextLevel.value ?? '?'}`, bg: 'bg-violet-50', border: 'border-violet-200', text: 'text-violet-800', btnBg: 'bg-violet-500 hover:bg-violet-600' })
  }

  return recs.slice(0, 3)
})

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
      <!-- 3. HÔM NAY HỌC GÌ — lộ trình cá nhân                            -->
      <!-- ══════════════════════════════════════════════════════════════════ -->
      <div class="bg-white rounded-2xl border shadow-sm p-5">
        <div class="flex items-center gap-2 mb-4">
          <span class="text-xl">🗺️</span>
          <h2 class="font-semibold text-slate-800">Hôm nay, bạn nên học gì?</h2>
          <span class="ml-auto text-xs text-slate-400 bg-slate-50 border rounded-full px-2 py-0.5">
            Dựa trên lộ trình cá nhân của bạn
          </span>
        </div>

        <div class="grid sm:grid-cols-3 gap-3">
          <div v-for="(rec, i) in todayRecs" :key="i"
            :class="[rec.bg, rec.border, 'border rounded-xl p-4 flex flex-col gap-3']">
            <!-- Icon + badge -->
            <div class="flex items-start justify-between">
              <span class="text-3xl">{{ rec.icon }}</span>
              <span :class="[rec.text, 'text-xs font-semibold bg-white/70 border rounded-full px-2 py-0.5', rec.border]">
                {{ rec.badge }}
              </span>
            </div>
            <!-- Text -->
            <div>
              <p :class="[rec.text, 'font-semibold text-sm']">{{ rec.title }}</p>
              <p class="text-xs text-slate-500 mt-1 leading-relaxed">{{ rec.reason }}</p>
            </div>
            <!-- CTA -->
            <button @click="router.push(rec.route)"
              :class="[rec.btnBg, 'mt-auto text-white text-xs font-semibold py-2 rounded-lg transition']">
              Bắt đầu →
            </button>
          </div>
        </div>

        <!-- Focus tip nhỏ bên dưới -->
        <div class="mt-4 flex gap-2 items-start bg-amber-50 border border-amber-100 rounded-xl px-4 py-3">
          <span class="text-lg">💡</span>
          <p class="text-amber-700 text-xs leading-relaxed">{{ data.focusTip }}</p>
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
            <div v-for="(err, i) in data.topErrors" :key="err.type"
              class="cursor-pointer rounded-xl p-2.5 hover:bg-slate-50 transition group -mx-2"
              @click="selectedError = err"
              title="Nhấn để xem gợi ý cải thiện">
              <div class="flex items-center justify-between mb-1">
                <span class="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                  <span>{{ ERROR_ICON[err.type] ?? '⚠️' }}</span>
                  <span>{{ err.type }}</span>
                  <span v-if="i === 0"
                    class="text-xs bg-red-100 text-red-600 px-1.5 py-0.5 rounded-full">Hay mắc nhất</span>
                </span>
                <div class="flex items-center gap-1.5">
                  <span class="text-xs text-slate-300 group-hover:text-slate-400">💡 xem gợi ý</span>
                  <span :class="`text-xs px-2 py-0.5 rounded-full border font-semibold ${ERROR_BADGE[err.type] ?? 'bg-slate-50 text-slate-600 border-slate-200'}`">
                    {{ err.count }}×
                  </span>
                </div>
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

    <!-- ── Error improvement modal ─────────────────────────────────────────── -->
    <Teleport to="body">
    <div v-if="selectedError"
      class="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-end sm:items-center justify-center p-4"
      @click.self="selectedError = null">
      <div class="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">

        <!-- Header — colour driven by ERROR_COLOR map -->
        <div class="p-5 text-white"
          :style="errorModalHeaderStyle(selectedError.type)">
          <div class="flex items-start justify-between">
            <div>
              <span class="text-3xl">{{ ERROR_ICON[selectedError.type] ?? '⚠️' }}</span>
              <h3 class="text-xl font-bold mt-1">Lỗi {{ selectedError.type }}</h3>
              <p class="text-white/80 text-sm mt-0.5">Đã mắc {{ selectedError.count }} lần</p>
            </div>
            <button @click="selectedError = null" class="text-white/70 hover:text-white text-2xl leading-none">×</button>
          </div>
          <div v-if="selectedError.example" class="mt-3 bg-white/20 rounded-lg px-3 py-2 text-sm italic">
            "{{ selectedError.example }}"
          </div>
        </div>

        <!-- Body -->
        <div class="p-5 space-y-4">
          <!-- Why -->
          <div>
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1">Tại sao bạn hay mắc?</p>
            <p class="text-sm text-slate-600">{{ ERROR_DETAILS[selectedError.type]?.why ?? 'Hãy chú ý kỹ hơn khi viết câu tiếng Anh.' }}</p>
          </div>

          <!-- Tips -->
          <div>
            <p class="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">Cách cải thiện</p>
            <ul class="space-y-2">
              <li v-for="(tip, idx) in (ERROR_DETAILS[selectedError.type]?.tips ?? [])"
                :key="idx"
                class="flex items-start gap-2 text-sm text-slate-700">
                <span class="w-5 h-5 rounded-full bg-green-100 text-green-600 text-xs font-bold flex items-center justify-center flex-shrink-0 mt-0.5">{{ idx + 1 }}</span>
                {{ tip }}
              </li>
            </ul>
          </div>

          <!-- Exercise -->
          <div class="bg-amber-50 border border-amber-100 rounded-xl px-4 py-3">
            <p class="text-xs font-semibold text-amber-600 mb-1">📝 Bài tập hôm nay</p>
            <p class="text-sm text-amber-800">{{ ERROR_DETAILS[selectedError.type]?.exercise }}</p>
          </div>

          <!-- CTA -->
          <button
            v-if="ERROR_DETAILS[selectedError.type]"
            @click="router.push(ERROR_DETAILS[selectedError.type].route); selectedError = null"
            class="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-semibold text-sm transition">
            {{ ERROR_DETAILS[selectedError.type].routeLabel }} →
          </button>
        </div>
      </div>
    </div>
  </Teleport>
  </div>
</template>
