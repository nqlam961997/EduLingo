# syntax=docker/dockerfile:1.7
FROM python:3.11-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# System deps — curl is for the compose healthcheck.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Install Python deps first for layer caching.
COPY requirements.txt ./
RUN pip install -r requirements.txt

# Drop privileges before downloading model + copying app code.
RUN useradd --create-home --shell /bin/sh app \
 && chown -R app:app /app
USER app

# Pre-download the Whisper weights into the app user's cache so the first
# /stt request has no cold-start. tiny.en is ~75MB and is English-optimized;
# override at runtime via the WHISPER_MODEL env var (e.g. base.en, small.en).
ENV WHISPER_MODEL=tiny.en
RUN python -c "from faster_whisper import WhisperModel; WhisperModel('${WHISPER_MODEL}', device='cpu', compute_type='int8')"

COPY --chown=app:app main.py ./

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
