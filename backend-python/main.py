import json
import os
import re
from functools import lru_cache
from io import BytesIO
from typing import AsyncGenerator, List, Literal, Optional

import httpx
from fastapi import Depends, FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="EduLingo Local AI", version="1.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gemma2:9b")
TTS_VOICE = os.getenv("TTS_VOICE", "en-US-JennyNeural")


class WireMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class GenerateRequest(BaseModel):
    system_prompt: str
    # New multi-turn shape (preferred).
    messages: Optional[List[WireMessage]] = None
    # Legacy single-turn field kept for transitional compat with older clients
    # that may still POST {system_prompt, user_text}.
    user_text: Optional[str] = None


class TTSRequest(BaseModel):
    text: str
    voice: Optional[str] = None


def _build_ollama_messages(req: GenerateRequest) -> list[dict]:
    out: list[dict] = [{"role": "system", "content": req.system_prompt}]
    if req.messages:
        for m in req.messages:
            out.append({"role": m.role, "content": m.content})
    elif req.user_text is not None:
        out.append({"role": "user", "content": req.user_text})
    else:
        raise HTTPException(status_code=400, detail="Either 'messages' or 'user_text' must be provided")
    return out


@app.get("/health")
async def health():
    return {"status": "ok", "model": OLLAMA_MODEL, "tts_voice": TTS_VOICE}


@app.post("/generate")
async def generate(req: GenerateRequest):
    payload = {
        "model": OLLAMA_MODEL,
        "messages": _build_ollama_messages(req),
        "stream": False,
    }
    # Qwen3 ships with thinking mode on by default — it emits a <think>...</think>
    # block before the answer, which 2-3x latency and breaks downstream JSON parsing.
    if OLLAMA_MODEL.startswith("qwen3"):
        payload["think"] = False
    async with httpx.AsyncClient(timeout=120) as client:
        try:
            resp = await client.post(f"{OLLAMA_URL}/api/chat", json=payload)
            resp.raise_for_status()
        except httpx.HTTPStatusError as e:
            raise HTTPException(status_code=502, detail=f"Ollama error: {e.response.text}")
        except httpx.RequestError as e:
            raise HTTPException(status_code=503, detail=f"Cannot reach Ollama: {e}")

    data = resp.json()
    return {"text": data.get("message", {}).get("content", "")}


@app.post("/stream")
async def stream(req: GenerateRequest):
    payload = {
        "model": OLLAMA_MODEL,
        "messages": _build_ollama_messages(req),
        "stream": True,
    }
    if OLLAMA_MODEL.startswith("qwen3"):
        payload["think"] = False

    async def event_generator() -> AsyncGenerator[str, None]:
        async with httpx.AsyncClient(timeout=120) as client:
            try:
                async with client.stream("POST", f"{OLLAMA_URL}/api/chat", json=payload) as resp:
                    resp.raise_for_status()
                    async for line in resp.aiter_lines():
                        if not line:
                            continue
                        try:
                            chunk = json.loads(line)
                            token = chunk.get("message", {}).get("content", "")
                            if token:
                                yield token
                            if chunk.get("done"):
                                break
                        except json.JSONDecodeError:
                            continue
            except httpx.RequestError:
                yield "[ERROR: Cannot reach Ollama]"

    return StreamingResponse(event_generator(), media_type="text/plain")


@app.post("/tts")
async def text_to_speech(req: TTSRequest):
    """
    Chuyển text → MP3 audio dùng edge-tts (Microsoft Azure TTS, miễn phí).
    Frontend decode bằng AudioContext để phân tích amplitude real-time cho lip sync.
    """
    try:
        import edge_tts
    except ImportError:
        raise HTTPException(
            status_code=501,
            detail="edge-tts not installed. Run: pip install edge-tts"
        )

    voice = req.voice or TTS_VOICE
    text = req.text.strip()
    text = re.sub(r'\([^)]*\)', '', text)
    text = re.sub(r'[*_`#>]', '', text)
    text = re.sub(r'\s+', ' ', text).strip()

    if not text:
        raise HTTPException(status_code=400, detail="Empty text after cleaning")

    try:
        communicate = edge_tts.Communicate(text, voice)
        audio_chunks: list[bytes] = []
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio_chunks.append(chunk["data"])

        return Response(
            content=b"".join(audio_chunks),
            media_type="audio/mpeg",
            headers={"Cache-Control": "no-cache", "X-Text-Length": str(len(text))},
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"TTS error: {e}")


@app.get("/tts/voices")
async def list_voices():
    """Liệt kê tất cả giọng English có sẵn."""
    try:
        import edge_tts
        voices = await edge_tts.list_voices()
        en_voices = [
            {"name": v["ShortName"], "gender": v["Gender"], "locale": v["Locale"]}
            for v in voices if v["Locale"].startswith("en-")
        ]
        return {"voices": en_voices, "current": TTS_VOICE}
    except ImportError:
        raise HTTPException(status_code=501, detail="edge-tts not installed")


@lru_cache(maxsize=1)
def get_whisper_model():
    """
    Lazy-load faster-whisper. The first call downloads weights (~75MB for
    tiny.en, ~150MB for base.en) and warms CTranslate2; subsequent calls
    return the cached singleton. Override via FastAPI's dependency_overrides
    in tests so the model isn't loaded for unit tests.
    """
    from faster_whisper import WhisperModel
    model_name = os.getenv("WHISPER_MODEL", "tiny.en")
    return WhisperModel(model_name, device="cpu", compute_type="int8")


@app.post("/stt")
async def speech_to_text(
    audio: UploadFile = File(...),
    model=Depends(get_whisper_model),
):
    """
    Browser uploads a captured audio blob (any container faster-whisper can
    decode via libav: webm/opus, wav, mp3, ogg). Returns the transcription so
    the frontend doesn't depend on Chrome's cloud webkitSpeechRecognition.
    """
    audio_bytes = await audio.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Empty audio payload")
    try:
        segments, info = model.transcribe(BytesIO(audio_bytes), beam_size=1)
        text = "".join(s.text for s in segments).strip()
        return {"text": text, "language": info.language, "duration": info.duration}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"STT error: {e}")
