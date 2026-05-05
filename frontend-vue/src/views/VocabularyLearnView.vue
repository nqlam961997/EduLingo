<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getVocabularySet, toggleWordMastered, type VocabularyWord } from '../api/edulingo'

const router = useRouter()
const route  = useRoute()
const setId  = route.params.setId as string
const mode   = route.params.mode as 'flashcard' | 'listening'

const words     = ref<VocabularyWord[]>([])
const setName   = ref('')
const loading   = ref(true)
const error     = ref('')

// ── shared state ─────────────────────────────────────────────────────────
const currentIdx = ref(0)
const masteredMap = ref<Record<string, boolean>>({})
const finished    = ref(false)

const currentWord = computed(() => words.value[currentIdx.value] ?? null)
const isMastered  = (w: VocabularyWord) => masteredMap.value[w.id] ?? w.mastered
const masteredCount = computed(() => words.value.filter(w => isMastered(w)).length)

async function markMastered(word: VocabularyWord, val: boolean) {
  const current = isMastered(word)
  if (current === val) return
  try {
    const res = await toggleWordMastered(word.id)
    masteredMap.value[word.id] = res.mastered
  } catch {}
}

async function next() {
  if (currentIdx.value < words.value.length - 1) {
    currentIdx.value++
    if (mode === 'flashcard') {
      flipped.value = false
    } else if (mode === 'listening') {
      await resetListening()
    }
  } else {
    finished.value = true
  }
}

async function prev() {
  if (currentIdx.value > 0) {
    currentIdx.value--
    if (mode === 'flashcard') {
      flipped.value = false
    } else if (mode === 'listening') {
      await resetListening()
    }
  }
}

// ── FLASHCARD mode ────────────────────────────────────────────────────────
const flipped = ref(false)

// Track current HTMLAudioElement so we can stop it before playing next
let currentAudio: HTMLAudioElement | null = null

/**
 * Primary: Python edge-tts backend → high-quality MP3 via Audio API (reliable).
 * Fallback: browser speechSynthesis (unreliable on Chrome/macOS but better than nothing).
 */
async function speakWord(word: string) {
  // Stop any currently playing audio
  if (currentAudio) { currentAudio.pause(); currentAudio = null }

  try {
    const res = await fetch('http://localhost:8000/tts', {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ text: word, voice: 'en-US-JennyNeural' }),
      signal:  AbortSignal.timeout(5000)
    })
    if (!res.ok) throw new Error(`TTS ${res.status}`)
    const blob = await res.blob()
    const url  = URL.createObjectURL(blob)
    const audio = new Audio(url)
    currentAudio = audio
    audio.onended = () => { URL.revokeObjectURL(url); if (currentAudio === audio) currentAudio = null }
    await audio.play()
  } catch {
    // Fallback to Web Speech API
    if (!('speechSynthesis' in window)) return
    const syn = window.speechSynthesis
    if (syn.speaking || syn.pending) syn.cancel()
    const utt = new SpeechSynthesisUtterance(word)
    utt.lang = 'en-US'
    utt.rate = 0.85
    syn.speak(utt)
  }
}

async function flipCard() {
  flipped.value = !flipped.value
  if (flipped.value && currentWord.value) await speakWord(currentWord.value.word)
}

// ── LISTENING mode ────────────────────────────────────────────────────────
const listenInput     = ref('')
const listenSubmitted = ref(false)
const listenCorrect   = ref(false)
const listenTimer     = ref(30)
const listenInterval  = ref<ReturnType<typeof setInterval> | null>(null)
const inputRef        = ref<HTMLInputElement | null>(null)

function startListeningTimer() {
  listenInterval.value && clearInterval(listenInterval.value)
  listenTimer.value = 30
  listenInterval.value = setInterval(() => {
    listenTimer.value--
    if (listenTimer.value <= 0) {
      clearInterval(listenInterval.value!)
      if (!listenSubmitted.value) submitListenAnswer()
    }
  }, 1000)
}

async function playCurrentWord() {
  if (!currentWord.value) return
  await speakWord(currentWord.value.word)
}

function submitListenAnswer() {
  if (!currentWord.value) return
  listenInterval.value && clearInterval(listenInterval.value)
  listenSubmitted.value = true
  const answer = listenInput.value.trim().toLowerCase()
  const correct = currentWord.value.word.toLowerCase()
  listenCorrect.value = answer === correct
  if (listenCorrect.value) markMastered(currentWord.value, true)
}

// Returns a Promise so callers can await it (keeps user-gesture context via microtask, not setTimeout)
async function resetListening() {
  listenSubmitted.value = false
  listenCorrect.value   = false
  listenInput.value     = ''
  listenTimer.value     = 30
  listenInterval.value && clearInterval(listenInterval.value)
  // Wait for Vue to flush DOM updates (microtask — preserves user-gesture context in Chrome)
  await nextTick()
  playCurrentWord()
  startListeningTimer()
  await nextTick()
  inputRef.value?.focus()
}

onMounted(async () => {
  try {
    const data = await getVocabularySet(setId)
    words.value   = data.words
    setName.value = data.name
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
  // After loading, start listening session for first word
  if (mode === 'listening' && words.value.length > 0) {
    await nextTick()          // wait for template to render (loading → content)
    await resetListening()
  }
})

onUnmounted(() => {
  listenInterval.value && clearInterval(listenInterval.value)
  if (currentAudio) { currentAudio.pause(); currentAudio = null }
  window.speechSynthesis?.cancel()
})
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <!-- Top bar -->
    <div class="bg-white border-b border-slate-200 px-6 py-3 flex items-center justify-between shadow-sm">
      <button @click="router.back()" class="flex items-center gap-2 text-slate-500 hover:text-slate-700 text-sm transition">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
        </svg>
        Thoát
      </button>
      <div class="text-center">
        <p class="font-semibold text-slate-700 text-sm">{{ setName }}</p>
        <p class="text-xs text-slate-400">{{ mode === 'flashcard' ? '🃏 Flashcard' : '🎧 Listening' }}</p>
      </div>
      <div class="text-sm text-slate-500">
        {{ currentIdx + 1 }} / {{ words.length }}
      </div>
    </div>

    <!-- Progress bar -->
    <div class="h-1.5 bg-slate-100">
      <div class="h-full bg-blue-500 transition-all duration-300"
           :style="`width:${words.length ? ((currentIdx+1)/words.length*100) : 0}%`"></div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="w-12 h-12 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="flex-1 flex items-center justify-center text-red-500">{{ error }}</div>

    <!-- Finished screen -->
    <div v-else-if="finished" class="flex-1 flex items-center justify-center p-6">
      <div class="bg-white rounded-2xl shadow-lg p-8 max-w-sm w-full text-center">
        <div class="text-6xl mb-4">🎉</div>
        <h2 class="text-2xl font-bold text-slate-800 mb-2">Hoàn thành!</h2>
        <p class="text-slate-500 mb-6">Bạn đã học xong <strong>{{ words.length }}</strong> từ</p>
        <div class="bg-blue-50 rounded-xl p-4 mb-6">
          <p class="text-blue-600 font-semibold text-lg">{{ masteredCount }} / {{ words.length }}</p>
          <p class="text-blue-400 text-sm">từ đã thuộc</p>
        </div>
        <div class="flex gap-3">
          <button @click="router.back()" class="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 transition text-sm font-medium">
            Quay lại
          </button>
          <button @click="async () => { currentIdx = 0; finished = false; flipped = false; await resetListening() }"
            class="flex-1 py-2.5 rounded-xl bg-blue-500 text-white hover:bg-blue-600 transition text-sm font-medium">
            Học lại
          </button>
        </div>
      </div>
    </div>

    <!-- Main content -->
    <div v-else-if="currentWord" class="flex-1 flex flex-col items-center justify-center p-6">

      <!-- ── FLASHCARD MODE ────────────────────────────────────────── -->
      <template v-if="mode === 'flashcard'">
        <!-- Card -->
        <div class="w-full max-w-md mb-8" style="perspective: 1000px">
          <div
            class="relative w-full cursor-pointer transition-transform duration-500"
            style="transform-style: preserve-3d"
            :style="flipped ? 'transform: rotateY(180deg)' : ''"
            @click="flipCard"
          >
            <!-- Front: English word -->
            <div class="bg-white rounded-2xl shadow-lg p-10 text-center min-h-[240px] flex flex-col items-center justify-center"
                 style="backface-visibility: hidden">
              <p class="text-sm text-slate-400 mb-4">Nhấn để xem nghĩa</p>
              <p class="text-4xl font-bold text-slate-800 mb-3">{{ currentWord.word }}</p>
              <p v-if="currentWord.pronunciation" class="text-slate-400 text-lg">{{ currentWord.pronunciation }}</p>
            </div>

            <!-- Back: Vietnamese meaning -->
            <div class="absolute inset-0 bg-gradient-to-br from-indigo-500 to-indigo-700 rounded-2xl shadow-lg p-8 text-white flex flex-col items-center justify-center"
                 style="backface-visibility: hidden; transform: rotateY(180deg)">
              <p class="text-3xl font-bold mb-3 text-center">{{ currentWord.meaning }}</p>
              <p v-if="currentWord.wordType"
                class="text-xs bg-white/20 px-3 py-1 rounded-full mb-4">{{ currentWord.wordType }}</p>
              <div v-if="currentWord.example" class="text-center">
                <p class="text-indigo-200 text-sm italic">{{ currentWord.example }}</p>
                <p class="text-indigo-300 text-xs mt-1">{{ currentWord.exampleMeaning }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Mastered + Nav buttons -->
        <div class="flex items-center gap-4 mb-6">
          <button @click="prev" :disabled="currentIdx === 0"
            class="w-12 h-12 rounded-full border-2 border-slate-200 text-slate-400 hover:border-slate-300 disabled:opacity-30 transition flex items-center justify-center">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
            </svg>
          </button>

          <button
            @click="markMastered(currentWord, !isMastered(currentWord))"
            :class="[
              isMastered(currentWord)
                ? 'bg-green-500 border-green-500 text-white'
                : 'bg-white border-slate-300 text-slate-500 hover:border-green-400',
              'px-5 py-2.5 rounded-full border-2 text-sm font-semibold transition flex items-center gap-2'
            ]"
          >
            <span>{{ isMastered(currentWord) ? '✅' : '☐' }}</span>
            {{ isMastered(currentWord) ? 'Đã thuộc' : 'Đánh dấu thuộc' }}
          </button>

          <button @click="next"
            class="w-12 h-12 rounded-full border-2 border-blue-300 text-blue-500 hover:bg-blue-50 transition flex items-center justify-center">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
            </svg>
          </button>
        </div>

        <p class="text-xs text-slate-400">{{ masteredCount }} / {{ words.length }} đã thuộc</p>
      </template>

      <!-- ── LISTENING MODE ────────────────────────────────────────── -->
      <template v-else-if="mode === 'listening'">
        <div class="w-full max-w-md">
          <!-- Timer ring -->
          <div class="flex justify-center mb-6">
            <div class="relative w-20 h-20">
              <svg class="w-full h-full -rotate-90" viewBox="0 0 80 80">
                <circle cx="40" cy="40" r="34" fill="none" stroke="#e2e8f0" stroke-width="6"/>
                <circle cx="40" cy="40" r="34" fill="none"
                  :stroke="listenTimer > 10 ? '#06b6d4' : '#ef4444'"
                  stroke-width="6" stroke-linecap="round"
                  :stroke-dasharray="`${2 * Math.PI * 34}`"
                  :stroke-dashoffset="`${2 * Math.PI * 34 * (1 - listenTimer / 30)}`"
                  class="transition-all duration-1000"
                />
              </svg>
              <span class="absolute inset-0 flex items-center justify-center font-bold text-slate-700">
                {{ listenTimer }}s
              </span>
            </div>
          </div>

          <!-- Play button -->
          <div class="bg-white rounded-2xl shadow-lg p-8 text-center mb-6">
            <button @click="playCurrentWord"
              class="w-20 h-20 rounded-full bg-cyan-500 hover:bg-cyan-400 text-white flex items-center justify-center mx-auto mb-4 shadow-lg hover:shadow-xl transition-all active:scale-95">
              <svg class="w-10 h-10" fill="currentColor" viewBox="0 0 24 24">
                <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/>
              </svg>
            </button>
            <p class="text-slate-500 text-sm">Nhấn để nghe lại</p>
          </div>

          <!-- Input / result -->
          <div v-if="!listenSubmitted" class="mb-4">
            <input
              ref="inputRef"
              v-model="listenInput"
              @keyup.enter="submitListenAnswer"
              type="text"
              placeholder="Gõ từ vừa nghe được..."
              class="w-full border-2 border-slate-200 focus:border-cyan-400 rounded-xl px-4 py-3 text-lg text-center outline-none transition"
            />
            <button @click="submitListenAnswer"
              class="w-full mt-3 py-3 bg-cyan-500 hover:bg-cyan-600 text-white rounded-xl font-semibold transition">
              Kiểm tra
            </button>
          </div>

          <!-- Answer feedback -->
          <div v-else class="mb-4">
            <div :class="[
              listenCorrect ? 'bg-green-50 border-green-300' : 'bg-red-50 border-red-300',
              'border-2 rounded-xl p-5 text-center mb-4'
            ]">
              <div class="text-3xl mb-2">{{ listenCorrect ? '🎉' : '❌' }}</div>
              <p v-if="listenCorrect" class="text-green-700 font-bold text-lg">Chính xác!</p>
              <p v-else class="text-red-600 font-semibold mb-2">Chưa đúng</p>

              <div class="mt-3 pt-3 border-t" :class="listenCorrect ? 'border-green-200' : 'border-red-200'">
                <p class="text-2xl font-bold text-slate-800">{{ currentWord.word }}</p>
                <p v-if="currentWord.pronunciation" class="text-slate-400">{{ currentWord.pronunciation }}</p>
                <p class="text-slate-600 mt-1">{{ currentWord.meaning }}</p>
                <p v-if="currentWord.example" class="text-slate-400 text-sm italic mt-2">{{ currentWord.example }}</p>
              </div>
            </div>

            <button @click="next"
              class="w-full py-3 bg-cyan-500 hover:bg-cyan-600 text-white rounded-xl font-semibold transition">
              {{ currentIdx < words.length - 1 ? 'Từ tiếp theo →' : 'Kết thúc 🎉' }}
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
