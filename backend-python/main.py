import json
import os
import re
from typing import AsyncGenerator

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="EduLingo Local AI", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gemma2:9b")
TTS_VOICE = os.getenv("TTS_VOICE", "en-US-JennyNeural")


class GenerateRequest(BaseModel):
    system_prompt: str
    user_text: str


class TTSRequest(BaseModel):
    text: str
    voice: str | None = None


@app.get("/health")
async def health():
    return {"status": "ok", "model": OLLAMA_MODEL, "tts_voice": TTS_VOICE}


@app.post("/generate")
async def generate(req: GenerateRequest):
    payload = {
        "model": OLLAMA_MODEL,
        "messages": [
            {"role": "system", "content": req.system_prompt},
            {"role": "user", "content": req.user_text},
        ],
        "stream": False,
    }
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
        "messages": [
            {"role": "system", "content": req.system_prompt},
            {"role": "user", "content": req.user_text},
        ],
        "stream": True,
    }

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
    # Làm sạch text: bỏ ngoặc đơn (corrections), markdown
    text = req.text.strip()
    text = re.sub(r'\([^)]*\)', '', text)   # bỏ (corrections in parentheses)
    text = re.sub(r'[*_`#>]', '', text)     # bỏ markdown
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
