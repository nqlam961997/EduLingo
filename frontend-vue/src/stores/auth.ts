import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

interface AuthUser {
  email: string
  fullName: string
  role: string
  token: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(restore())
  const isLoggedIn = computed(() => !!user.value)
  const token = computed(() => user.value?.token ?? '')

  function setUser(u: AuthUser) {
    user.value = u
    localStorage.setItem('edulingo.auth', JSON.stringify(u))
  }

  function logout() {
    user.value = null
    localStorage.removeItem('edulingo.auth')
  }

  function restore(): AuthUser | null {
    try {
      const raw = localStorage.getItem('edulingo.auth')
      return raw ? JSON.parse(raw) : null
    } catch { return null }
  }

  return { user, isLoggedIn, token, setUser, logout }
})
