<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchTopics, type Topic } from '../api/edulingo'

const emit = defineEmits<{ select: [topic: Topic] }>()

const topics = ref<Topic[]>([])
const loading = ref(true)

onMounted(async () => {
  try { topics.value = await fetchTopics() }
  finally { loading.value = false }
})
</script>

<template>
  <div class="max-w-3xl mx-auto p-4">
    <h2 class="text-lg font-semibold text-slate-700 mb-4">Choose a topic</h2>
    <div v-if="loading" class="text-slate-400">Loading topics...</div>
    <div v-else class="grid grid-cols-2 sm:grid-cols-3 gap-3">
      <button v-for="t in topics" :key="t.id" @click="emit('select', t)"
        class="border rounded-xl p-4 bg-white hover:border-blue-400 hover:shadow-sm transition text-left">
        <div class="text-2xl mb-1">{{ t.icon }}</div>
        <div class="font-medium text-sm text-slate-800">{{ t.name }}</div>
        <div class="text-xs text-slate-500 mt-1">{{ t.description }}</div>
      </button>
    </div>
  </div>
</template>
