<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { getDashboard } from './api/edulingo'

const auth   = useAuthStore()
const router = useRouter()
const route  = useRoute()
const cefrLevel = ref<string | null>(null)

const CEFR_BADGE: Record<string, string> = {
  A1: 'bg-slate-100 text-slate-600',
  A2: 'bg-blue-100 text-blue-700',
  B1: 'bg-teal-100 text-teal-700',
  B2: 'bg-green-100 text-green-700',
  C1: 'bg-violet-100 text-violet-700',
  C2: 'bg-amber-100 text-amber-700',
}

async function fetchLevel() {
  if (!auth.isLoggedIn) return
  try { cefrLevel.value = (await getDashboard()).cefrLevel } catch {}
}

onMounted(fetchLevel)
watch(() => auth.isLoggedIn, (v) => { if (v) fetchLevel() })

function logout() {
  auth.logout()
  cefrLevel.value = null
  router.push('/login')
}
</script>

<template>
  <div class="h-full flex flex-col bg-slate-50">
    <header v-if="auth.isLoggedIn" class="border-b bg-white px-6 py-3 flex items-center justify-between shadow-sm">
      <div class="flex items-center gap-6">
        <RouterLink to="/dashboard" class="font-bold text-slate-800 text-lg tracking-tight hover:text-blue-600 transition">
          EduLingo
        </RouterLink>
        <nav class="flex gap-1 text-sm">
          <RouterLink to="/dashboard"
            class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition"
            active-class="bg-blue-50 text-blue-700 font-medium">
            🏠 Trang chủ
          </RouterLink>
          <RouterLink to="/chat"
            class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition"
            active-class="bg-blue-50 text-blue-700 font-medium">
            💬 Chat
          </RouterLink>
          <RouterLink to="/picture"
            class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition"
            active-class="bg-blue-50 text-blue-700 font-medium">
            🖼️ Mô tả hình
          </RouterLink>
          <RouterLink to="/vocabulary"
            class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition"
            active-class="bg-blue-50 text-blue-700 font-medium">
            📚 Từ vựng
          </RouterLink>
          <RouterLink to="/assessment"
            class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition"
            active-class="bg-blue-50 text-blue-700 font-medium">
            🎓 Kiểm tra
          </RouterLink>
        </nav>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span v-if="cefrLevel"
          :class="`text-xs font-bold px-2.5 py-1 rounded-full ${CEFR_BADGE[cefrLevel] ?? 'bg-slate-100 text-slate-600'}`">
          {{ cefrLevel }}
        </span>
        <span class="text-slate-600">{{ auth.user?.fullName }}</span>
        <button @click="logout" class="text-sm text-red-500 hover:text-red-700 transition">Logout</button>
      </div>
    </header>
    <main class="flex-1 overflow-y-auto">
      <RouterView />
    </main>
  </div>
</template>
