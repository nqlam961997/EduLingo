<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getVocabularyLevels, type VocabularyLevelStats } from '../api/edulingo'

const router = useRouter()
const levels = ref<VocabularyLevelStats[]>([])
const loading = ref(true)
const error = ref('')

const LEVEL_META: Record<string, { label: string; color: string; bg: string; bar: string; emoji: string }> = {
  A1: { label: 'A1 – Mới bắt đầu',   color: 'text-slate-700', bg: 'bg-slate-50 border-slate-200',   bar: 'bg-slate-400',   emoji: '🌱' },
  A2: { label: 'A2 – Cơ bản',         color: 'text-blue-700',  bg: 'bg-blue-50 border-blue-200',     bar: 'bg-blue-400',    emoji: '📘' },
  B1: { label: 'B1 – Trung cấp',      color: 'text-teal-700',  bg: 'bg-teal-50 border-teal-200',     bar: 'bg-teal-400',    emoji: '🚀' },
  B2: { label: 'B2 – Trên trung cấp', color: 'text-green-700', bg: 'bg-green-50 border-green-200',   bar: 'bg-green-500',   emoji: '⭐' },
  C1: { label: 'C1 – Nâng cao',       color: 'text-violet-700',bg: 'bg-violet-50 border-violet-200', bar: 'bg-violet-500',  emoji: '🏆' },
  C2: { label: 'C2 – Thành thạo',     color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200',   bar: 'bg-amber-500',   emoji: '👑' },
}

function meta(level: string) {
  return LEVEL_META[level] ?? { label: level, color: 'text-gray-700', bg: 'bg-gray-50 border-gray-200', bar: 'bg-gray-400', emoji: '📚' }
}

function progressPct(lv: VocabularyLevelStats) {
  if (!lv.totalWords) return 0
  return Math.round((lv.mastered / lv.totalWords) * 100)
}

onMounted(async () => {
  try {
    levels.value = await getVocabularyLevels()
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <!-- Header -->
    <div class="mb-8">
      <h1 class="text-3xl font-bold text-slate-800">📚 Học từ vựng</h1>
      <p class="text-slate-500 mt-1">Chọn cấp độ phù hợp với trình độ của bạn</p>
    </div>

    <div v-if="loading" class="flex justify-center py-16">
      <div class="w-10 h-10 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
    </div>

    <div v-else-if="error" class="text-center py-16 text-red-500">{{ error }}</div>

    <div v-else>
      <!-- Levels grid -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <button
          v-for="lv in levels"
          :key="lv.level"
          @click="router.push(`/vocabulary/level/${lv.level}`)"
          :class="[meta(lv.level).bg, 'border rounded-2xl p-5 text-left hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 group']"
        >
          <!-- Level badge + emoji -->
          <div class="flex items-center justify-between mb-3">
            <span :class="[meta(lv.level).color, 'text-2xl font-bold']">{{ lv.level }}</span>
            <span class="text-3xl">{{ meta(lv.level).emoji }}</span>
          </div>

          <p :class="[meta(lv.level).color, 'font-semibold text-sm mb-3']">{{ meta(lv.level).label }}</p>

          <!-- Stats row -->
          <div class="flex gap-3 text-xs text-slate-500 mb-3">
            <span class="flex items-center gap-1">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
              {{ lv.setCount }} bộ từ
            </span>
            <span class="flex items-center gap-1">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
              </svg>
              {{ lv.totalWords }} từ vựng
            </span>
          </div>

          <!-- Difficulty -->
          <div class="flex items-center gap-1.5 mb-3">
            <span class="text-xs text-slate-400">Độ khó</span>
            <div class="flex gap-0.5">
              <div v-for="i in 5" :key="i"
                :class="[i <= lv.difficulty ? meta(lv.level).bar : 'bg-slate-200', 'w-4 h-1.5 rounded-full']">
              </div>
            </div>
            <span class="text-xs text-slate-400">{{ lv.difficulty }}/5</span>
          </div>

          <!-- Progress bar -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>Đã thuộc: {{ lv.mastered }}/{{ lv.totalWords }}</span>
              <span>{{ progressPct(lv) }}%</span>
            </div>
            <div class="h-1.5 bg-slate-200 rounded-full overflow-hidden">
              <div :class="[meta(lv.level).bar, 'h-full rounded-full transition-all']"
                   :style="`width:${progressPct(lv)}%`"></div>
            </div>
          </div>
        </button>
      </div>

      <!-- Empty state -->
      <div v-if="levels.length === 0" class="text-center py-16 text-slate-400">
        <p class="text-4xl mb-3">📭</p>
        <p>Chưa có dữ liệu từ vựng</p>
      </div>
    </div>
  </div>
</template>
