<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login, register } from '../api/auth'

const auth = useAuthStore()
const router = useRouter()

const isRegister = ref(false)
const email = ref('')
const fullName = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  busy.value = true
  error.value = ''
  try {
    const resp = isRegister.value
      ? await register(email.value, fullName.value, password.value)
      : await login(email.value, password.value)
    auth.setUser(resp)
    router.push('/dashboard')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="min-h-full flex items-center justify-center bg-slate-50 p-4">
    <div class="bg-white rounded-xl shadow-sm border p-8 w-full max-w-md">
      <h2 class="text-xl font-semibold text-slate-800 mb-6 text-center">
        {{ isRegister ? 'Create Account' : 'Login to EduLingo' }}
      </h2>
      <form class="space-y-4" @submit.prevent="submit">
        <input v-model="email" type="email" required
          class="w-full border rounded px-3 py-2 text-sm" placeholder="Email" />
        <input v-if="isRegister" v-model="fullName" required
          class="w-full border rounded px-3 py-2 text-sm" placeholder="Full name" />
        <input v-model="password" type="password" required minlength="6"
          class="w-full border rounded px-3 py-2 text-sm" placeholder="Password" />
        <button :disabled="busy"
          class="w-full bg-blue-600 text-white rounded py-2 disabled:opacity-50">
          {{ busy ? 'Please wait...' : isRegister ? 'Register' : 'Login' }}
        </button>
      </form>
      <p v-if="error" class="mt-3 text-red-600 text-sm text-center">{{ error }}</p>
      <p class="mt-4 text-sm text-center text-slate-500">
        {{ isRegister ? 'Already have an account?' : 'Don\'t have an account?' }}
        <button class="text-blue-600 underline ml-1" @click="isRegister = !isRegister">
          {{ isRegister ? 'Login' : 'Register' }}
        </button>
      </p>
    </div>
  </div>
</template>
