<script setup lang="ts">
import { ref, nextTick, onUnmounted, defineAsyncComponent } from 'vue'
import TopicPicker from '../components/TopicPicker.vue'
import { startScenario, streamReply, recordSession, type Topic } from '../api/edulingo'
import { LOCAL_AI_URL, LIVE2D_MODEL } from '../config/env'

// Lazy load — lỗi Live2D không crash app chính
const Live2DAvatar = defineAsyncComponent({
  loader: () => import('../components/Live2DAvatar.vue'),
  errorComponent: { template: '<div class="l2d-fallback">🤖</div>' },
  delay: 200,
})

interface Msg { role: 'user' | 'assistant'; text: string; suggestions?: string[] }

const selectedTopic = ref<Topic | null>(null)
const scenario = ref('')
const characterName = ref('')
const characterRole = ref('')
const characterAvatar = ref('')
const messages = ref<Msg[]>([])
const input = ref('')
const busy = ref(false)
const sessionRecorded = ref(false)
const scroller = ref<HTMLElement | null>(null)
const showKeyboard = ref(false)
const isListening = ref(false)
const isSpeaking = ref(false)
const showHint = ref(false)

const micError = ref('')
const interimText = ref('')
const mouthOpenness = ref(0)    // 0–1, drives Live2D mouth parameter

let recognition: any = null
let currentUtterance: SpeechSynthesisUtterance | null = null
let retryCount = 0
const MAX_RETRIES = 2
let micStream: MediaStream | null = null

// Audio context cho TTS lip sync
let audioCtx: AudioContext | null = null
let audioSource: AudioBufferSourceNode | null = null
let lipSyncRaf = 0

async function requestMicPermission(): Promise<boolean> {
  try {
    micStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    return true
  } catch {
    micError.value = 'Microphone access denied. Please allow microphone permission in your browser settings.'
    setTimeout(() => { micError.value = '' }, 5000)
    return false
  }
}

function createRecognition() {
  const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  if (!SR) return null
  const r = new SR()
  r.lang = 'en-US'
  r.continuous = false
  r.interimResults = true
  r.maxAlternatives = 1

  r.onresult = (e: any) => {
    let interim = ''
    let final = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const transcript = e.results[i][0].transcript
      if (e.results[i].isFinal) {
        final += transcript
      } else {
        interim += transcript
      }
    }
    if (final) {
      input.value += final
      interimText.value = ''
      retryCount = 0
    } else {
      interimText.value = interim
    }
  }

  r.onend = () => {
    isListening.value = false
    interimText.value = ''
    if (input.value.trim()) {
      showKeyboard.value = true
    }
  }

  r.onerror = (e: any) => {
    isListening.value = false
    interimText.value = ''

    if (e.error === 'network' && retryCount < MAX_RETRIES) {
      retryCount++
      micError.value = `Connection issue, retrying... (${retryCount}/${MAX_RETRIES})`
      recognition = null
      setTimeout(() => {
        micError.value = ''
        startListening()
      }, 1000)
      return
    }

    if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
      micError.value = 'Microphone blocked. Check browser permissions (click lock icon in address bar).'
    } else if (e.error === 'no-speech') {
      micError.value = 'No speech detected. Tap the mic and try again.'
    } else if (e.error === 'network') {
      micError.value = 'Cannot connect to speech service. Try: 1) Check internet connection 2) Use Chrome 3) Use keyboard input instead.'
      showKeyboard.value = true
    } else if (e.error === 'aborted') {
      return
    } else {
      micError.value = 'Speech error: ' + e.error
    }
    setTimeout(() => { micError.value = '' }, 6000)
  }

  return r
}

async function startListening() {
  if (busy.value) return

  if (!micStream) {
    const ok = await requestMicPermission()
    if (!ok) return
  }

  recognition = createRecognition()
  if (!recognition) {
    micError.value = 'Speech recognition not supported. Please use Chrome or Edge browser.'
    showKeyboard.value = true
    setTimeout(() => { micError.value = '' }, 5000)
    return
  }

  try {
    input.value = ''
    interimText.value = ''
    showKeyboard.value = true
    recognition.start()
    isListening.value = true
  } catch (e: any) {
    isListening.value = false
    if (e.message?.includes('already started')) {
      recognition.stop()
      setTimeout(() => startListening(), 300)
    } else {
      micError.value = 'Could not start: ' + e.message
      setTimeout(() => { micError.value = '' }, 5000)
    }
  }
}

function toggleListening() {
  micError.value = ''
  if (isListening.value) {
    recognition?.stop()
    isListening.value = false
    retryCount = 0
  } else {
    retryCount = 0
    startListening()
  }
}

/** Dừng hoàn toàn âm thanh đang phát */
function stopSpeaking() {
  // Dừng TTS AudioContext
  if (audioSource) { try { audioSource.stop() } catch { /* ignore */ }; audioSource = null }
  if (lipSyncRaf) { cancelAnimationFrame(lipSyncRaf); lipSyncRaf = 0 }
  // Dừng Web Speech API fallback
  window.speechSynthesis?.cancel()
  currentUtterance = null
  isSpeaking.value = false
  mouthOpenness.value = 0
}

/**
 * Phát TTS qua Python /tts (edge-tts) → AudioContext → AnalyserNode → lip sync realtime.
 * Fallback sang Web Speech API nếu Python không khả dụng.
 */
async function speakText(text: string) {
  if (!text.trim()) return
  stopSpeaking()

  try {
    // Gọi Python TTS endpoint
    const res = await fetch(`${LOCAL_AI_URL}/tts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    })
    if (!res.ok) throw new Error(`TTS HTTP ${res.status}`)

    const arrayBuffer = await res.arrayBuffer()

    // Tạo AudioContext mới cho mỗi lần phát (tránh lỗi reuse)
    audioCtx = new AudioContext()
    const analyser = audioCtx.createAnalyser()
    analyser.fftSize = 512
    analyser.smoothingTimeConstant = 0.7

    const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer)
    audioSource = audioCtx.createBufferSource()
    audioSource.buffer = audioBuffer
    audioSource.connect(analyser)
    analyser.connect(audioCtx.destination)

    const dataArray = new Uint8Array(analyser.frequencyBinCount)

    // Lip sync loop — đọc amplitude speech frequency (200–3000 Hz)
    function lipSyncLoop() {
      lipSyncRaf = requestAnimationFrame(lipSyncLoop)
      analyser.getByteFrequencyData(dataArray)

      // Lấy bins tương ứng dải giọng nói, normalize mạnh hơn để dễ thấy
      const speechBins = Array.from(dataArray.slice(2, 40))
      const peak = Math.max(...speechBins)
      const avg  = speechBins.reduce((a, b) => a + b, 0) / speechBins.length
      // Dùng peak + avg để nhạy hơn với giọng nói
      const raw = (peak * 0.6 + avg * 0.4) / 80
      mouthOpenness.value = Math.min(Math.max(raw, 0), 1)
    }

    isSpeaking.value = true
    audioSource.onended = () => {
      isSpeaking.value = false
      mouthOpenness.value = 0
      cancelAnimationFrame(lipSyncRaf)
      lipSyncRaf = 0
      audioCtx?.close()
      audioCtx = null
    }

    audioSource.start(0)
    lipSyncLoop()

  } catch (err) {
    // Fallback: Web Speech API + miệng mô phỏng
    console.warn('[TTS] Python unavailable, falling back to Web Speech API:', err)
    _speakFallback(text)
  }
}

/** Web Speech API fallback với mouth simulation */
function _speakFallback(text: string) {
  if (!window.speechSynthesis) return
  const utt = new SpeechSynthesisUtterance(text)
  utt.lang = 'en-US'
  utt.rate = 0.9
  currentUtterance = utt
  isSpeaking.value = true

  let phase = 0
  function simulateMouth() {
    if (!isSpeaking.value) { mouthOpenness.value = 0; return }
    phase += 0.2
    // Dao động ngẫu nhiên giả lập chuyển động miệng
    mouthOpenness.value = Math.max(0, Math.sin(phase) * 0.5 + 0.3 + (Math.random() - 0.5) * 0.3)
    lipSyncRaf = requestAnimationFrame(simulateMouth)
  }
  utt.onstart = () => simulateMouth()
  utt.onend = () => {
    isSpeaking.value = false
    mouthOpenness.value = 0
    currentUtterance = null
    cancelAnimationFrame(lipSyncRaf)
    lipSyncRaf = 0
  }
  utt.onerror = utt.onend
  window.speechSynthesis.speak(utt)
}

const latestHint = ref('')
function revealHint() {
  const last = [...messages.value].reverse().find(m => m.role === 'assistant' && m.suggestions?.length)
  latestHint.value = last?.suggestions?.[0] ?? 'Try saying something simple!'
  showHint.value = true
  setTimeout(() => { showHint.value = false }, 4000)
}

async function onTopic(topic: Topic) {
  selectedTopic.value = topic
  busy.value = true
  try {
    const resp = await startScenario(topic.id)
    scenario.value = resp.scenario
    characterName.value = resp.characterName
    characterRole.value = resp.characterRole
    characterAvatar.value = resp.characterAvatar
    messages.value = [{ role: 'assistant', text: resp.openingMessage }]
    speakText(resp.openingMessage)
  } catch (e) {
    messages.value = [{ role: 'assistant', text: 'Error: ' + (e as Error).message }]
  } finally {
    busy.value = false
  }
}

async function send(text?: string) {
  const message = (text ?? input.value).trim()
  if (!message || busy.value || !selectedTopic.value) return
  if (isListening.value && recognition) { recognition.stop(); isListening.value = false }
  input.value = ''
  interimText.value = ''
  showHint.value = false

  const history = messages.value.map(m => ({ role: m.role, content: m.text }))
  messages.value.push({ role: 'user', text: message })
  const reply: Msg = { role: 'assistant', text: '' }
  messages.value.push(reply)
  busy.value = true
  await scrollToEnd()

  try {
    let raw = ''
    for await (const chunk of streamReply(
      selectedTopic.value.id, scenario.value, history, message
    )) {
      raw += chunk
      reply.text = raw
      await scrollToEnd()
    }
    const parsed = tryParseJson(raw)
    if (parsed) {
      reply.text = fixSpacing(parsed.reply ?? raw)
      reply.suggestions = parsed.suggestions ?? []
    } else {
      reply.text = fixSpacing(raw)
    }
    speakText(reply.text)
    // Record first reply as a session
    if (!sessionRecorded.value && selectedTopic.value) {
      sessionRecorded.value = true
      recordSession('CHAT', selectedTopic.value.id, selectedTopic.value.name)
    }
  } catch (e) {
    reply.text = 'Error: ' + (e as Error).message
  } finally {
    busy.value = false
  }
}

function reset() {
  stopSpeaking()
  selectedTopic.value = null
  scenario.value = ''
  characterName.value = ''
  characterRole.value = ''
  characterAvatar.value = ''
  messages.value = []
  showKeyboard.value = false
  sessionRecorded.value = false
  showHint.value = false
}

function fixSpacing(text: string): string {
  return text
    .replace(/([.!?;:,])([A-Za-z])/g, '$1 $2')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
}

function tryParseJson(raw: string): { reply?: string; suggestions?: string[] } | null {
  const cleaned = raw.trim().replace(/^```(?:json)?/i, '').replace(/```$/, '').trim()
  try { return JSON.parse(cleaned) } catch { /* not pure JSON */ }
  const idx = cleaned.indexOf('{')
  if (idx > 0) {
    try { return JSON.parse(cleaned.substring(idx)) } catch { /* ignore */ }
  }
  return null
}

async function scrollToEnd() {
  await nextTick()
  scroller.value?.scrollTo({ top: scroller.value.scrollHeight, behavior: 'smooth' })
}

onUnmounted(() => {
  stopSpeaking()
  if (audioCtx) { audioCtx.close(); audioCtx = null }
  recognition?.stop()
  micStream?.getTracks().forEach(t => t.stop())
  micStream = null
})
</script>

<template>
  <div class="chat-page">
    <TopicPicker v-if="!selectedTopic" @select="onTopic" />

    <template v-else>
      <div class="chat-layout">
        <!-- ==================== LEFT SIDE ==================== -->
        <div class="chat-main">
          <!-- Header -->
          <div class="chat-header">
            <button @click="reset" class="header-btn" title="Back">
              <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M19 12H5M12 19l-7-7 7-7"/>
              </svg>
            </button>
            <h1 class="header-title">{{ characterName }}</h1>
            <div class="header-actions">
              <button class="header-btn" @click="speakText(messages.filter(m=>m.role==='assistant').at(-1)?.text??'')" title="Replay last">
                <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>
                </svg>
              </button>
              <button class="header-btn" @click="revealHint" title="Help">
                <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Scenario Card -->
          <div class="scenario-card">
            <div class="scenario-head">
              <svg width="16" height="16" fill="none" stroke="#16a34a" stroke-width="2" viewBox="0 0 24 24">
                <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/>
              </svg>
              <span>Scenario</span>
            </div>
            <p>{{ scenario }}</p>
          </div>

          <!-- Conversation -->
          <div ref="scroller" class="conversation">
            <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
              <template v-if="m.role === 'assistant'">
                <div class="bubble ai">
                  <p>{{ m.text || '...' }}</p>
                  <div v-if="m.suggestions?.length" class="suggestions">
                    <button v-for="s in m.suggestions" :key="s" @click="send(s)" class="chip">{{ s }}</button>
                  </div>
                </div>
                <div class="msg-actions">
                  <button class="act-btn" @click="speakText(m.text)" title="Listen">
                    <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 010 7.07"/>
                    </svg>
                  </button>
                </div>
              </template>
              <template v-else>
                <div class="bubble user"><p>{{ m.text }}</p></div>
              </template>
            </div>

            <!-- Typing dots -->
            <div v-if="busy && messages.length && messages[messages.length-1].role==='assistant' && !messages[messages.length-1].text" class="msg-row assistant">
              <div class="bubble ai typing"><span/><span/><span/></div>
            </div>
          </div>

          <!-- Hint Toast -->
          <Transition name="hint">
            <div v-if="showHint" class="hint-toast">
              <svg width="16" height="16" fill="none" stroke="#eab308" stroke-width="2" viewBox="0 0 24 24">
                <path d="M9 18h6M10 22h4M12 2a7 7 0 00-4 12.7V17h8v-2.3A7 7 0 0012 2z"/>
              </svg>
              <span>{{ latestHint }}</span>
            </div>
          </Transition>

          <!-- Mic Error -->
          <Transition name="hint">
            <div v-if="micError" class="mic-error-toast">
              <svg width="16" height="16" fill="none" stroke="#ef4444" stroke-width="2" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              <span>{{ micError }}</span>
            </div>
          </Transition>

          <!-- Keyboard Input -->
          <div v-if="showKeyboard" class="keyboard-bar">
            <!-- Interim text from speech -->
            <div v-if="isListening && (interimText || input)" class="interim-row">
              <span class="interim-label">
                <span class="rec-dot"/>
                {{ isListening ? 'Listening...' : '' }}
              </span>
              <span v-if="interimText" class="interim-text">{{ interimText }}</span>
            </div>
            <form @submit.prevent="send()" class="kb-form">
              <input v-model="input" :disabled="busy"
                :placeholder="isListening ? 'Speak now...' : 'Type your answer in English...'"
                :class="{ 'listening-border': isListening }"
                class="kb-input" autofocus />
              <button :disabled="busy || !input.trim()" class="kb-send">
                <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
              </button>
            </form>
          </div>
        </div>

        <!-- ==================== RIGHT SIDE ==================== -->
        <div class="chat-sidebar">
          <!-- Live2D Avatar Panel -->
          <div class="avatar-panel">
            <div class="live2d-stage" :class="{ speaking: isSpeaking, thinking: busy && !isSpeaking, listening: isListening }">
              <Live2DAvatar
                :model-path="LIVE2D_MODEL"
                :mouth-openness="mouthOpenness"
                :is-speaking="isSpeaking"
                :is-thinking="busy && !isSpeaking"
                :is-listening="isListening"
              />
            </div>
            <div v-if="isSpeaking" class="wave-bars">
              <span v-for="n in 5" :key="n" class="bar" :style="{ animationDelay: (n * 0.1) + 's' }"/>
            </div>
            <div v-else-if="busy" class="status-text thinking-text">Thinking...</div>
            <div v-else-if="isListening" class="status-text listening-text">Listening...</div>
            <div v-else class="status-text">Ready to chat</div>
          </div>

          <!-- Controls -->
          <div class="controls">
            <button class="ctrl hint-btn" @click="revealHint" title="Hint">
              <svg width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M9 18h6M10 22h4M12 2a7 7 0 00-4 12.7V17h8v-2.3A7 7 0 0012 2z"/>
              </svg>
            </button>
            <button class="ctrl mic-btn" :class="{ active: isListening }" @click="toggleListening" title="Speak">
              <svg width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z"/>
                <path d="M19 10v2a7 7 0 01-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/>
              </svg>
            </button>
            <button class="ctrl kb-btn" :class="{ active: showKeyboard }" @click="showKeyboard = !showKeyboard" title="Keyboard">
              <svg width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <rect x="2" y="4" width="20" height="16" rx="2"/>
                <line x1="6" y1="8" x2="6.01" y2="8"/><line x1="10" y1="8" x2="10.01" y2="8"/>
                <line x1="14" y1="8" x2="14.01" y2="8"/><line x1="18" y1="8" x2="18.01" y2="8"/>
                <line x1="8" y1="12" x2="8.01" y2="12"/><line x1="12" y1="12" x2="12.01" y2="12"/>
                <line x1="16" y1="12" x2="16.01" y2="12"/>
                <line x1="7" y1="16" x2="17" y2="16"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* ===== PAGE ===== */
.chat-page { height: 100%; background: #f3f4f6; }

.chat-layout {
  display: flex; height: 100%; gap: 16px; padding: 16px;
}

/* ===== LEFT ===== */
.chat-main {
  flex: 7; display: flex; flex-direction: column; gap: 12px; min-width: 0;
}

.chat-header {
  background: #fff; border-radius: 20px; padding: 10px 16px;
  display: flex; align-items: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}
.header-btn {
  width: 38px; height: 38px; border-radius: 12px; border: none;
  background: #f1f5f9; color: #64748b; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: .2s;
}
.header-btn:hover { background: #e2e8f0; color: #334155; }
.header-title {
  flex: 1; text-align: center; font-size: 15px; font-weight: 600; color: #1e293b; margin: 0;
}
.header-actions { display: flex; gap: 6px; }

/* Scenario */
.scenario-card {
  background: #f0fdf4; border: 1.5px solid #bbf7d0; border-radius: 18px; padding: 14px 18px;
}
.scenario-head {
  display: flex; align-items: center; gap: 6px; margin-bottom: 4px;
  font-weight: 600; font-size: 13px; color: #15803d;
}
.scenario-card p { margin: 0; font-size: 13px; color: #166534; line-height: 1.5; }

/* Conversation */
.conversation {
  flex: 1; overflow-y: auto; border-radius: 20px; padding: 20px;
  background: #f8fafc; display: flex; flex-direction: column; gap: 14px;
}
.conversation::-webkit-scrollbar { width: 4px; }
.conversation::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }

.msg-row { display: flex; flex-direction: column; }
.msg-row.assistant { align-items: flex-start; }
.msg-row.user { align-items: flex-end; }

.bubble {
  max-width: 82%; padding: 14px 18px; border-radius: 20px;
  font-size: 14px; line-height: 1.65;
}
.bubble p { margin: 0; }

.bubble.ai {
  background: #dcfce7; color: #14532d; border-bottom-left-radius: 6px;
}
.bubble.user {
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  color: #fff; border-bottom-right-radius: 6px;
}

.msg-actions { display: flex; gap: 2px; margin-top: 4px; padding-left: 2px; }
.act-btn {
  width: 28px; height: 28px; border-radius: 8px; border: none;
  background: transparent; color: #94a3b8; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: .2s;
}
.act-btn:hover { background: #e2e8f0; color: #475569; }

/* Suggestions */
.suggestions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.chip {
  font-size: 12px; padding: 6px 14px; border-radius: 100px;
  border: 1.5px solid #bbf7d0; background: #fff; color: #166534;
  cursor: pointer; transition: .2s;
}
.chip:hover { background: #f0fdf4; border-color: #4ade80; transform: translateY(-1px); }

/* Typing */
.typing { display: flex; gap: 5px; padding: 14px 22px !important; }
.typing span {
  width: 8px; height: 8px; border-radius: 50%; background: #86efac;
  animation: bounce 1.4s infinite;
}
.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }
@keyframes bounce {
  0%,60%,100% { transform: translateY(0); }
  30% { transform: translateY(-8px); }
}

/* Hint toast */
.hint-toast {
  display: flex; align-items: center; gap: 8px;
  background: #fefce8; border: 1.5px solid #fde68a; border-radius: 14px;
  padding: 12px 18px; font-size: 13px; color: #854d0e;
}
.hint-enter-active, .hint-leave-active { transition: all .3s ease; }
.hint-enter-from, .hint-leave-to { opacity: 0; transform: translateY(8px); }

/* Mic error toast */
.mic-error-toast {
  display: flex; align-items: center; gap: 8px;
  background: #fef2f2; border: 1.5px solid #fecaca; border-radius: 14px;
  padding: 12px 18px; font-size: 13px; color: #991b1b;
}

/* Keyboard */
.keyboard-bar {
  background: #fff; border-radius: 18px; padding: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}
.interim-row {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 12px 8px; font-size: 13px; color: #64748b;
}
.interim-label {
  display: flex; align-items: center; gap: 6px;
  font-weight: 500; color: #f43f5e; white-space: nowrap;
}
.rec-dot {
  width: 8px; height: 8px; border-radius: 50%; background: #f43f5e;
  animation: rec-blink 1s infinite;
}
@keyframes rec-blink {
  0%,100% { opacity: 1; }
  50% { opacity: .3; }
}
.interim-text { color: #94a3b8; font-style: italic; }
.kb-input.listening-border { border-color: #f9a8d4; background: #fdf2f8; }
.kb-form { display: flex; gap: 8px; }
.kb-input {
  flex: 1; border: 1.5px solid #e2e8f0; border-radius: 14px;
  padding: 10px 16px; font-size: 14px; outline: none; transition: .2s;
  background: #f8fafc;
}
.kb-input:focus { border-color: #60a5fa; background: #fff; }
.kb-send {
  width: 42px; height: 42px; border-radius: 14px; border: none;
  background: linear-gradient(135deg, #60a5fa, #3b82f6); color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: .2s;
}
.kb-send:hover:not(:disabled) { transform: scale(1.05); }
.kb-send:disabled { opacity: .35; cursor: not-allowed; }

/* ===== RIGHT ===== */
.chat-sidebar {
  flex: 3; display: flex; flex-direction: column; gap: 14px; max-width: 320px;
}

/* Avatar panel */
.avatar-panel {
  flex: 1; background: #fff; border-radius: 24px; padding: 16px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.05); gap: 12px; overflow: hidden;
}

/* Live2D stage — khung hiển thị nhân vật */
.live2d-stage {
  width: 100%; flex: 1; min-height: 320px;
  border-radius: 18px; overflow: hidden;
  background: linear-gradient(160deg, #f0fdf4, #e0f2fe);
  transition: box-shadow .3s;
  position: relative;
}
.live2d-stage.speaking {
  box-shadow: 0 0 0 3px #4ade80, 0 0 20px rgba(34,197,94,.25);
  animation: pulse-green 1.8s infinite;
}
.live2d-stage.thinking {
  box-shadow: 0 0 0 3px #60a5fa, 0 0 20px rgba(96,165,250,.2);
  animation: pulse-blue 2s infinite;
}
.live2d-stage.listening {
  box-shadow: 0 0 0 3px #f472b6, 0 0 20px rgba(244,114,182,.2);
  animation: pulse-pink 1.6s infinite;
}
@keyframes pulse-green {
  0%,100% { box-shadow: 0 0 0 3px #4ade80, 0 0 20px rgba(34,197,94,.25); }
  50%      { box-shadow: 0 0 0 6px #4ade80, 0 0 32px rgba(34,197,94,.4); }
}
@keyframes pulse-blue {
  0%,100% { box-shadow: 0 0 0 3px #60a5fa, 0 0 20px rgba(96,165,250,.2); }
  50%     { box-shadow: 0 0 0 6px #60a5fa, 0 0 28px rgba(96,165,250,.35); }
}
@keyframes pulse-pink {
  0%,100% { box-shadow: 0 0 0 3px #f472b6, 0 0 18px rgba(244,114,182,.2); }
  50%     { box-shadow: 0 0 0 6px #f472b6, 0 0 26px rgba(244,114,182,.35); }
}

/* Wave bars */
.wave-bars {
  display: flex; gap: 3px; align-items: flex-end; height: 28px;
}
.bar {
  width: 4px; border-radius: 2px; background: #22c55e;
  animation: wave 0.9s ease-in-out infinite;
}
.bar:nth-child(1) { height: 10px; }
.bar:nth-child(2) { height: 18px; }
.bar:nth-child(3) { height: 26px; }
.bar:nth-child(4) { height: 18px; }
.bar:nth-child(5) { height: 10px; }
@keyframes wave {
  0%,100% { transform: scaleY(.45); }
  50% { transform: scaleY(1); }
}

.status-text { font-size: 13px; color: #94a3b8; }
.thinking-text { color: #60a5fa; }
.listening-text { color: #f472b6; }

/* Controls */
.controls {
  display: flex; align-items: center; justify-content: center; gap: 18px;
  padding: 18px; background: #fff; border-radius: 24px;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}

.ctrl {
  border: none; cursor: pointer; display: flex;
  align-items: center; justify-content: center; transition: .2s;
}
.hint-btn, .kb-btn {
  width: 50px; height: 50px; border-radius: 16px;
  background: #f1f5f9; color: #64748b;
}
.hint-btn:hover { background: #fef9c3; color: #a16207; }
.kb-btn:hover { background: #e0e7ff; color: #4338ca; }
.kb-btn.active { background: #dbeafe; color: #2563eb; }

.mic-btn {
  width: 68px; height: 68px; border-radius: 50%;
  background: linear-gradient(135deg, #fb7185, #f43f5e); color: #fff;
  box-shadow: 0 6px 20px rgba(244,63,94,.3);
}
.mic-btn:hover { transform: scale(1.07); box-shadow: 0 8px 25px rgba(244,63,94,.4); }
.mic-btn.active {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  animation: mic-pulse 1.4s infinite;
}
@keyframes mic-pulse {
  0% { box-shadow: 0 0 0 0 rgba(244,63,94,.45); }
  70% { box-shadow: 0 0 0 18px rgba(244,63,94,0); }
  100% { box-shadow: 0 0 0 0 rgba(244,63,94,0); }
}

/* ===== RESPONSIVE ===== */
@media (max-width: 860px) {
  .chat-layout { flex-direction: column; }
  .chat-sidebar {
    flex-direction: row; max-width: 100%; gap: 10px;
  }
  .portrait-card { display: none; }
  .avatar-panel { flex: 1; padding: 16px; }
  .avatar-ring { width: 70px; height: 70px; }
  .avatar-inner { font-size: 34px; }
  .controls { flex: 0 0 auto; padding: 12px; gap: 12px; }
  .mic-btn { width: 54px; height: 54px; }
  .hint-btn, .kb-btn { width: 42px; height: 42px; border-radius: 12px; }
}
</style>
