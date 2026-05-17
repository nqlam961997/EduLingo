"""
Tests for the /stt speech-to-text endpoint. The test injects a fake Whisper
model via FastAPI's dependency_overrides so neither the model weights nor a
real audio sample are required to exercise the endpoint contract.
"""
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from main import app, get_whisper_model


class FakeWhisper:
    def transcribe(self, audio, **kwargs):
        return (
            iter([SimpleNamespace(text=" hello world")]),
            SimpleNamespace(language="en", language_probability=0.99, duration=1.23),
        )


@pytest.fixture
def client():
    app.dependency_overrides[get_whisper_model] = lambda: FakeWhisper()
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_stt_returns_transcription(client):
    response = client.post(
        "/stt",
        files={"audio": ("clip.webm", b"fake-but-non-empty-audio", "audio/webm")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["text"] == "hello world"
    assert body["language"] == "en"


def test_stt_rejects_empty_audio(client):
    response = client.post(
        "/stt",
        files={"audio": ("clip.webm", b"", "audio/webm")},
    )
    assert response.status_code == 400


def test_stt_requires_audio_field(client):
    response = client.post("/stt")
    assert response.status_code == 422
