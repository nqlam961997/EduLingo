import { useAuthStore } from '../stores/auth'

export interface Topic {
  id: string
  name: string
  description: string
  icon: string
  characterName: string
  characterRole: string
  characterAvatar: string
}

export interface ScenarioResponse {
  scenario: string
  openingMessage: string
  characterName: string
  characterRole: string
  characterAvatar: string
  /** "ON" | "OFF" | "HINT" — drives whether the UI shows the suggestion chip strip */
  suggestPolicy?: 'ON' | 'OFF' | 'HINT'
  /** Populated in Phase 2 once the backend wires chat_session persistence; may be null today */
  sessionId?: string | null
}

export interface CorrectionError {
  type: string
  original: string
  fixed: string
  explain_vi: string
}

export interface CorrectionResponse {
  corrected: string
  errors: CorrectionError[]
  score: number
  tips: string[]
}

export interface GeneratedImage {
  imageId: string
  base64Data: string
  mimeType: string
  topicId: string
}

interface MessageItem {
  role: 'user' | 'assistant'
  content: string
}

function headers(json = true): Record<string, string> {
  const auth = useAuthStore()
  const h: Record<string, string> = {}
  if (auth.token) h['Authorization'] = `Bearer ${auth.token}`
  if (json) h['Content-Type'] = 'application/json'
  return h
}

export async function fetchTopics(): Promise<Topic[]> {
  const res = await fetch('/api/topics', { headers: headers() })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function startScenario(topicId: string): Promise<ScenarioResponse> {
  const res = await fetch('/api/chat/start', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ topicId })
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function* streamReply(
  topicId: string,
  scenario: string,
  history: MessageItem[],
  message: string,
  sessionId?: string | null
): AsyncGenerator<string> {
  const body: Record<string, unknown> = { topicId, scenario, history, message }
  if (sessionId) body.sessionId = sessionId
  const res = await fetch('/api/chat/reply', {
    method: 'POST',
    headers: { ...headers(), Accept: 'text/event-stream' },
    body: JSON.stringify(body)
  })
  if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const lines = buf.split('\n')
    buf = lines.pop() ?? ''
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const raw = line.slice(5)
        const chunk = raw.startsWith(' ') ? raw.slice(1) : raw
        if (chunk) yield chunk
      }
    }
  }
}

export async function generatePicture(topicId: string): Promise<GeneratedImage> {
  const res = await fetch('/api/picture/generate', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ topicId })
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function describePicture(imageId: string, description: string): Promise<CorrectionResponse> {
  const res = await fetch('/api/picture/describe', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ imageId, description })
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export interface QuestionListResponse {
  imageId: string
  topicId: string
  scene: string
  questions: string[]
}

export interface AnswerFeedbackResponse {
  feedback: string
  corrected: string
  score: number
  isCorrect: boolean
}

export interface ErrorSummary {
  type: string
  count: number
  example: string
}

export interface SessionSummary {
  sessionType: string
  topicName: string
  score: number | null
  date: string
}

export interface DashboardData {
  cefrLevel: string
  cefrIndex: number
  learningGoal: string
  memberSince: string
  topErrors: ErrorSummary[]
  totalErrorTypes: number
  focusTip: string
  totalSessions: number
  chatSessions: number
  pictureSessions: number
  averageScore: number
  topicsStudied: number
  recentSessions: SessionSummary[]
}

export async function getDashboard(): Promise<DashboardData> {
  const res = await fetch('/api/dashboard', { headers: headers() })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function getPictureQuestions(imageId: string): Promise<QuestionListResponse> {
  const res = await fetch(`/api/picture/${imageId}/questions`, { headers: headers() })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function submitPictureAnswer(
  imageId: string,
  questionIndex: number,
  answer: string
): Promise<AnswerFeedbackResponse> {
  const res = await fetch('/api/picture/answer', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ imageId, questionIndex, answer })
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export interface AssessmentQuestion {
  id: number
  question: string
  options: string[]
  correct: number
  explanation: string
}

export interface AssessmentStartResponse {
  currentLevel: string
  targetLevel: string
  description: string
  questions: AssessmentQuestion[]
}

export interface QuestionResult {
  questionId: number
  question: string
  chosen: number
  correct: number
  isCorrect: boolean
  explanation: string
}

export interface AssessmentResultResponse {
  correctCount: number
  totalCount: number
  score: number
  previousLevel: string
  newLevel: string
  leveledUp: boolean
  message: string
  results: QuestionResult[]
}

export async function startAssessment(): Promise<AssessmentStartResponse> {
  const res = await fetch('/api/assessment/start', { headers: headers() })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function submitAssessment(answers: number[]): Promise<AssessmentResultResponse> {
  const res = await fetch('/api/assessment/submit', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ answers })
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

// ── Vocabulary ────────────────────────────────────────────────────────────

export interface VocabularyLevelStats {
  level: string
  setCount: number
  totalWords: number
  mastered: number
  difficulty: number
}

export interface VocabularySetSummary {
  id: string
  name: string
  description: string
  cefrLevel: string
  difficulty: number
  totalWords: number
  mastered: number
}

export interface VocabularyWord {
  id: string
  word: string
  pronunciation: string
  meaning: string
  wordType: string
  example: string
  exampleMeaning: string
  mastered: boolean
}

export interface VocabularySetDetail {
  id: string
  name: string
  description: string
  cefrLevel: string
  difficulty: number
  words: VocabularyWord[]
}

export async function getVocabularyLevels(): Promise<VocabularyLevelStats[]> {
  const res = await fetch('/api/vocabulary/levels', { headers: headers() })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function getVocabularySets(level: string): Promise<VocabularySetSummary[]> {
  const res = await fetch(`/api/vocabulary/levels/${level}/sets`, { headers: headers() })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function getVocabularySet(setId: string): Promise<VocabularySetDetail> {
  const res = await fetch(`/api/vocabulary/sets/${setId}`, { headers: headers() })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function toggleWordMastered(wordId: string): Promise<{ wordId: string; mastered: boolean }> {
  const res = await fetch(`/api/vocabulary/words/${wordId}/toggle`, {
    method: 'POST',
    headers: headers()
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

// ── Sessions ──────────────────────────────────────────────────────────────

export async function recordSession(
  sessionType: 'CHAT' | 'PICTURE',
  topicId: string,
  topicName: string,
  score?: number
): Promise<void> {
  try {
    await fetch('/api/dashboard/sessions', {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({ sessionType, topicId, topicName, score })
    })
  } catch {
    // non-critical — silently ignore
  }
}
