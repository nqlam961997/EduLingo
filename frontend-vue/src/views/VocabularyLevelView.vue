<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getVocabularySets, type VocabularySetSummary } from '../api/edulingo'

const router = useRouter()
const route  = useRoute()
const level  = (route.params.level as string).toUpperCase()

const sets    = ref<VocabularySetSummary[]>([])
const loading = ref(true)
const error   = ref('')

const LEVEL_COLOR: Record<string, string> = {
  A1: 'text-slate-700',
  A2: 'text-blue-700',
  B1: 'text-teal-700',
  B2: 'text-green-700',
  C1: 'text-violet-700',
  C2: 'text-amber-700',
}

const LEVEL_BG: Record<string, string> = {
  A1: 'from-slate-500 to-slate-700',
  A2: 'from-blue-500 to-blue-700',
  B1: 'from-teal-500 to-teal-700',
  B2: 'from-green-500 to-green-700',
  C1: 'from-violet-500 to-violet-700',
  C2: 'from-amber-500 to-amber-700',
}

function progressPct(s: VocabularySetSummary) {
  if (!s.totalWords) return 0
  return Math.round((s.mastered / s.totalWords) * 100)
}

const totalWords  = ref(0)
const totalMaster = ref(0)

onMounted(async () => {
  try {
    sets.value = await getVocabularySets(level)
    totalWords.value  = sets.value.reduce((a, s) => a + s.totalWords, 0)
    totalMaster.value = sets.value.reduce((a, s) => a + s.mastered, 0)
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <!-- Back -->
    <button @click="router.back()" class="flex items-center gap-2 text-slate-500 hover:text-slate-700 mb-6 text-sm transition">
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
      </svg>
      Quay lại
    </button>

    <!-- Hero banner -->
    <div :class="`bg-gradient-to-r ${LEVEL_BG[level] ?? 'from-blue-500 to-blue-700'} rounded-2xl p-6 text-white mb-8`">
      <div class="flex items-start justify-between">
        <div>
          <h1 class="text-3xl font-bold mb-1">Cấp độ {{ level }}</h1>
          <div class="flex gap-4 text-sm text-white/80 mt-3">
            <span>📚 {{ sets.length }} bộ từ</span>
            <span>💬 {{ totalWords }} từ vựng</span>
            <span>✅ {{ Math.round(totalMaster / (totalWords || 1) * 100) }}% hoàn thành</span>
          </div>
        </div>
        <span class="text-5xl opacity-80">🎯</span>
      </div>
      <!-- Overall progress bar -->
      <div class="mt-4">
        <div class="text-xs text-white/70 mb-1">Tiến độ: {{ totalMaster }}/{{ totalWords }} từ</div>
        <div class="h-2 bg-white/30 rounded-full overflow-hidden">
          <div class="h-full bg-white rounded-full transition-all"
               :style="`width:${Math.round(totalMaster/(totalWords||1)*100)}%`"></div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="flex justify-center py-16">
      <div class="w-10 h-10 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
    </div>

    <div v-else-if="error" class="text-center text-red-500 py-8">{{ error }}</div>

    <div v-else>
      <h2 class="text-lg font-semibold text-slate-700 mb-4">Các bộ từ</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <button
          v-for="(s, idx) in sets"
          :key="s.id"
          @click="router.push(`/vocabulary/set/${s.id}`)"
          class="bg-white border border-slate-200 rounded-xl p-4 text-left hover:shadow-md hover:border-blue-300 transition-all duration-200 hover:-translate-y-0.5 group"
        >
          <!-- Number + name -->
          <div class="flex items-center gap-3 mb-3">
            <div class="w-8 h-8 rounded-lg bg-blue-100 text-blue-600 text-sm font-bold flex items-center justify-center flex-shrink-0">
              {{ idx + 1 }}
            </div>
            <p class="font-semibold text-slate-800 text-sm leading-tight">{{ s.name }}</p>
          </div>

          <!-- Word count + progress text -->
          <div class="flex justify-between items-center text-xs text-slate-500 mb-2">
            <span>{{ s.mastered }}/{{ s.totalWords }} từ</span>
            <span :class="progressPct(s) === 100 ? 'text-green-600 font-semibold' : 'text-slate-400'">
              {{ progressPct(s) }}%
            </span>
          </div>

          <!-- Progress bar -->
          <div class="h-1.5 bg-slate-100 rounded-full overflow-hidden mb-3">
            <div
              :class="progressPct(s) === 100 ? 'bg-green-500' : 'bg-blue-400'"
              class="h-full rounded-full transition-all"
              :style="`width:${progressPct(s)}%`"
            ></div>
          </div>

          <!-- Start button hint -->
          <div class="flex items-center justify-between">
            <span v-if="progressPct(s) === 100"
              class="text-xs text-green-600 font-medium flex items-center gap-1">
              ✅ Hoàn thành
            </span>
            <span v-else class="text-xs text-slate-400">{{ s.description }}</span>
            <svg class="w-4 h-4 text-slate-300 group-hover:text-blue-400 transition" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
            </svg>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>
