<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { PIXI } from '../lib/setup-pixi'   // phải import trước Live2DModel
import { Live2DModel } from 'pixi-live2d-display/cubism4'

interface Props {
  modelPath: string        // e.g. "/models/haru/haru_greeter_t03.model3.json"
  mouthOpenness: number   // 0–1, driven by audio amplitude
  isSpeaking: boolean
  isThinking: boolean
  isListening: boolean
}

const props = withDefaults(defineProps<Props>(), {
  mouthOpenness: 0,
  isSpeaking: false,
  isThinking: false,
  isListening: false,
})

const canvasRef = ref<HTMLCanvasElement | null>(null)
const isLoaded = ref(false)
const loadError = ref('')

let pixiApp: PIXI.Application | null = null
let live2dModel: any = null
let tickerCallback: (() => void) | null = null

// Tên parameter — Haru model dùng chuẩn Cubism 4
const PARAM_MOUTH_OPEN = 'ParamMouthOpenY'
const PARAM_BREATH     = 'ParamBreath'

onMounted(async () => {
  if (!canvasRef.value) return

  Live2DModel.registerTicker(PIXI.Ticker)

  // Lấy kích thước thực của container
  const container = canvasRef.value.parentElement!
  const w = container.clientWidth  || 280
  const h = container.clientHeight || 380

  pixiApp = new PIXI.Application({
    view: canvasRef.value,
    width: w,
    height: h,
    backgroundAlpha: 0,
    antialias: true,
    resolution: window.devicePixelRatio || 1,
    autoDensity: true,
  })

  try {
    live2dModel = await Live2DModel.from(props.modelPath, { autoInteract: false })
    pixiApp.stage.addChild(live2dModel)
    fitModelToCanvas()
    isLoaded.value = true
    startLipSync()
  } catch (e: any) {
    loadError.value = e?.message ?? 'Failed to load model'
    console.error('[Live2D]', e)
  }
})

function fitModelToCanvas() {
  if (!live2dModel || !pixiApp) return

  const W = pixiApp.renderer.width  / (pixiApp.renderer.resolution || 1)
  const H = pixiApp.renderer.height / (pixiApp.renderer.resolution || 1)

  // Tính kích thước gốc của model (trước khi scale)
  const modelW = live2dModel.internalModel?.originalWidth  ?? live2dModel.width
  const modelH = live2dModel.internalModel?.originalHeight ?? live2dModel.height

  // Scale sao cho model vừa 80% canvas, giữ tỉ lệ
  const scale = Math.min((W / modelW) * 0.85, (H / modelH) * 0.85)
  live2dModel.scale.set(scale)

  // Căn giữa ngang, anchor phần thân ở giữa canvas
  live2dModel.x = (W - modelW * scale) / 2
  live2dModel.y = (H - modelH * scale) / 2
}

function startLipSync() {
  /**
   * Dùng PIXI.Ticker với UPDATE_PRIORITY.LOW (chạy SAU motion system).
   * Nếu dùng requestAnimationFrame, motion system chạy sau và overwrite giá trị.
   * LOW priority = số nhỏ hơn NORMAL (−25) → chạy sau tất cả update khác.
   */
  tickerCallback = () => {
    if (!live2dModel) return
    const core = live2dModel.internalModel?.coreModel
    if (!core) return

    // Đặt mouth AFTER motion system đã chạy
    const mouth = props.mouthOpenness
    core.setParameterValueById(PARAM_MOUTH_OPEN, mouth)

    // Khi đang nói, tăng thêm breath để trông tự nhiên
    if (props.isSpeaking) {
      const t = performance.now() / 1000
      core.setParameterValueById(PARAM_BREATH, 0.5 + Math.sin(t * 2) * 0.3)
    }
  }

  // PIXI v7: UPDATE_PRIORITY.LOW = -25, đảm bảo callback chạy sau Live2D model update
  PIXI.Ticker.shared.add(tickerCallback, null, (PIXI as any).UPDATE_PRIORITY?.LOW ?? -25)
}

// Resize canvas khi container thay đổi
const resizeObserver = new ResizeObserver(() => {
  if (!pixiApp || !canvasRef.value) return
  const { clientWidth, clientHeight } = canvasRef.value.parentElement!
  pixiApp.renderer.resize(clientWidth, clientHeight)
  fitModelToCanvas()
})

onMounted(() => {
  if (canvasRef.value?.parentElement) {
    resizeObserver.observe(canvasRef.value.parentElement)
  }
})

onUnmounted(() => {
  resizeObserver.disconnect()
  if (tickerCallback) {
    PIXI.Ticker.shared.remove(tickerCallback)
    tickerCallback = null
  }
  pixiApp?.destroy(false, { children: true })
  pixiApp = null
  live2dModel = null
})
</script>

<template>
  <div class="live2d-wrapper">
    <canvas ref="canvasRef" class="live2d-canvas" />

    <!-- Loading overlay -->
    <div v-if="!isLoaded && !loadError" class="live2d-overlay">
      <div class="l2d-spinner" />
      <span>Loading avatar...</span>
    </div>

    <!-- Error: model not found -->
    <div v-if="loadError" class="live2d-overlay live2d-error">
      <div class="l2d-error-icon">🤖</div>
      <p>Live2D model not found.</p>
      <p class="l2d-hint">
        Place your model in<br/>
        <code>public/models/</code>
      </p>
    </div>

    <!-- State badge -->
    <div v-if="isLoaded" class="state-badge"
         :class="{ speaking: isSpeaking, thinking: isThinking, listening: isListening }">
      <span v-if="isSpeaking">Speaking</span>
      <span v-else-if="isThinking">Thinking...</span>
      <span v-else-if="isListening">Listening</span>
    </div>
  </div>
</template>

<style scoped>
.live2d-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: inherit;
}

.live2d-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.live2d-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 13px;
  color: #64748b;
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(4px);
}

.live2d-error { color: #94a3b8; }
.l2d-error-icon { font-size: 48px; }
.l2d-hint { font-size: 12px; text-align: center; color: #94a3b8; }
.l2d-hint code { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; }

.l2d-spinner {
  width: 32px; height: 32px;
  border: 3px solid #e2e8f0;
  border-top-color: #22c55e;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.state-badge {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 14px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(255,255,255,0.85);
  color: #94a3b8;
  backdrop-filter: blur(4px);
  transition: all 0.3s;
  pointer-events: none;
  opacity: 0;
}
.state-badge.speaking { opacity: 1; background: #dcfce7; color: #16a34a; }
.state-badge.thinking { opacity: 1; background: #dbeafe; color: #2563eb; }
.state-badge.listening { opacity: 1; background: #fce7f3; color: #db2777; }
</style>
