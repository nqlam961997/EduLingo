<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getVocabularySet, toggleWordMastered, type VocabularySetDetail, type VocabularyWord } from '../api/edulingo'

const router = useRouter()
const route  = useRoute()
const setId  = route.params.setId as string

const setDetail  = ref<VocabularySetDetail | null>(null)
const loading    = ref(true)
const error      = ref('')
const toggling   = ref<Set<string>>(new Set())

const masteredCount = computed(() =>
  setDetail.value?.words.filter(w => w.mastered).length ?? 0
)

const progressPct = computed(() => {
  const total = setDetail.value?.words.length ?? 0
  return total ? Math.round((masteredCount.value / total) * 100) : 0
})

async function onToggle(word: VocabularyWord) {
  if (toggling.value.has(word.id)) return
  toggling.value = new Set([...toggling.value, word.id])

  const prev = word.mastered
  word.mastered = !prev            // optimistic update

  try {
    const res = await toggleWordMastered(word.id)
    word.mastered = res.mastered   // confirm from server
  } catch {
    word.mastered = prev           // revert on error
  } finally {
    const next = new Set(toggling.value)
    next.delete(word.id)
    toggling.value = next
  }
}

function speak(text: string) {
  if (!('speechSynthesis' in window)) return
  window.speechSynthesis.cancel()
  const utt = new SpeechSynthesisUtterance(text)
  utt.lang = 'en-US'
  utt.rate = 0.85
  window.speechSynthesis.speak(utt)
}

onMounted(async () => {
  try {
    setDetail.value = await getVocabularySet(setId)
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="max-w-4xl mx-auto px-4 py-8">
    <!-- Back -->
    <button @click="router.back()" class="flex items-center gap-2 text-slate-500 hover:text-slate-700 mb-6 text-sm transition">
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
      </svg>
      Quay lại
    </button>

    <div v-if="loading" class="flex justify-center py-16">
      <div class="w-10 h-10 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
    </div>

    <div v-else-if="error" class="text-center text-red-500 py-8">{{ error }}</div>

    <template v-else-if="setDetail">
      <!-- Header card -->
      <div class="bg-white border border-slate-200 rounded-2xl p-6 mb-6 shadow-sm">
        <div class="flex items-start justify-between mb-4">
          <div>
            <span class="text-xs font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full">{{ setDetail.cefrLevel }}</span>
            <h1 class="text-2xl font-bold text-slate-800 mt-2">{{ setDetail.name }}</h1>
            <p v-if="setDetail.description" class="text-slate-500 text-sm mt-1">{{ setDetail.description }}</p>
          </div>
          <div class="text-right text-sm text-slate-500">
            <p class="text-2xl font-bold text-slate-700">{{ setDetail.words.length }}</p>
            <p>từ vựng</p>
          </div>
        </div>

        <!-- Progress -->
        <div class="mb-4">
          <div class="flex justify-between text-sm text-slate-500 mb-1.5">
            <span>Đã thuộc: {{ masteredCount }}/{{ setDetail.words.length }}</span>
            <span :class="progressPct === 100 ? 'text-green-600 font-semibold' : ''">{{ progressPct }}%</span>
          </div>
          <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
            <div
              :class="progressPct === 100 ? 'bg-green-500' : 'bg-blue-500'"
              class="h-full rounded-full transition-all duration-500"
              :style="`width:${progressPct}%`"
            ></div>
          </div>
        </div>

        <!-- Mode selection -->
        <div>
          <p class="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-3">Chọn chế độ học</p>
          <div class="flex gap-3">
            <!-- Flashcard -->
            <button
              @click="router.push(`/vocabulary/set/${setId}/learn/flashcard`)"
              class="flex-1 flex flex-col items-center gap-2 py-4 px-3 rounded-xl bg-gradient-to-b from-indigo-500 to-indigo-600 text-white hover:from-indigo-400 hover:to-indigo-500 transition shadow-sm hover:shadow-md"
            >
              <span class="text-2xl">🃏</span>
              <span class="font-semibold text-sm">Flashcard</span>
              <span class="text-xs text-indigo-200">Lật thẻ học từ vựng</span>
            </button>

            <!-- Listening -->
            <button
              @click="router.push(`/vocabulary/set/${setId}/learn/listening`)"
              class="flex-1 flex flex-col items-center gap-2 py-4 px-3 rounded-xl bg-gradient-to-b from-cyan-500 to-cyan-600 text-white hover:from-cyan-400 hover:to-cyan-500 transition shadow-sm hover:shadow-md"
            >
              <span class="text-2xl">🎧</span>
              <span class="font-semibold text-sm">Listening</span>
              <span class="text-xs text-cyan-200">Nghe và gõ lại từ</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Word list -->
      <div class="bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden">
        <div class="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <h2 class="font-semibold text-slate-700">Danh sách từ vựng</h2>
          <span class="text-sm text-slate-400">{{ setDetail.words.length }} từ</span>
        </div>

        <!-- Column headers -->
        <div class="grid grid-cols-12 gap-2 px-6 py-2 bg-slate-50 text-xs font-semibold text-slate-400 uppercase tracking-wide border-b border-slate-100">
          <span class="col-span-3">Từ vựng</span>
          <span class="col-span-3">Nghĩa</span>
          <span class="col-span-2">Loại từ</span>
          <span class="col-span-3">Ví dụ</span>
          <span class="col-span-1 text-center">Thuộc</span>
        </div>

        <div class="divide-y divide-slate-50">
          <div
            v-for="word in setDetail.words"
            :key="word.id"
            class="grid grid-cols-12 gap-2 px-6 py-4 items-center hover:bg-slate-50 transition"
          >
            <!-- Word + pronunciation -->
            <div class="col-span-3 flex items-center gap-2">
              <button
                @click="speak(word.word)"
                class="w-7 h-7 rounded-full bg-blue-50 text-blue-500 hover:bg-blue-100 flex items-center justify-center flex-shrink-0 transition"
                title="Phát âm"
              >
                <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/>
                </svg>
              </button>
              <div>
                <p class="font-semibold text-slate-800 text-sm">{{ word.word }}</p>
                <p v-if="word.pronunciation" class="text-xs text-slate-400">{{ word.pronunciation }}</p>
              </div>
            </div>

            <!-- Meaning -->
            <div class="col-span-3">
              <p class="text-sm text-slate-700">{{ word.meaning }}</p>
            </div>

            <!-- Word type badge -->
            <div class="col-span-2">
              <span v-if="word.wordType"
                class="text-xs bg-amber-50 text-amber-700 border border-amber-200 px-1.5 py-0.5 rounded font-medium">
                {{ word.wordType }}
              </span>
            </div>

            <!-- Example -->
            <div class="col-span-3">
              <p v-if="word.example" class="text-xs text-slate-500 italic">{{ word.example }}</p>
              <p v-if="word.exampleMeaning" class="text-xs text-slate-400 mt-0.5">{{ word.exampleMeaning }}</p>
              <span v-if="!word.example" class="text-xs text-slate-300">—</span>
            </div>

            <!-- Mastered toggle -->
            <div class="col-span-1 flex justify-center">
              <button
                @click="onToggle(word)"
                :disabled="toggling.has(word.id)"
                :class="[
                  word.mastered
                    ? 'bg-green-500 border-green-500 text-white shadow-sm'
                    : 'bg-white border-slate-300 text-slate-400 hover:border-green-400 hover:text-green-500',
                  toggling.has(word.id) ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer',
                  'w-7 h-7 rounded-full border-2 flex items-center justify-center transition-all text-xs font-bold'
                ]"
                :title="word.mastered ? 'Đã thuộc — click để bỏ' : 'Đánh dấu đã thuộc'"
              >
                <span v-if="toggling.has(word.id)" class="w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin"></span>
                <span v-else>✓</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
