"""Assertion primitives used by golden-conversation rubrics."""
from __future__ import annotations

import json
import re
from typing import Any, Iterable


# ── Deterministic primitives ────────────────────────────────────────────────


def valid_json(text: str) -> bool:
    """True if *text* is a valid JSON object after stripping code fences."""
    try:
        _parse_json_relaxed(text)
        return True
    except Exception:
        return False


def has_fields(text: str, required: Iterable[str]) -> bool:
    try:
        node = _parse_json_relaxed(text)
        return all(k in node for k in required)
    except Exception:
        return False


def field_missing(text: str, field: str) -> bool:
    """True if the JSON object does NOT contain *field*."""
    try:
        node = _parse_json_relaxed(text)
        return field not in node
    except Exception:
        return False


def length_in_range(text: str, low: int, high: int) -> bool:
    return low <= len(text) <= high


def sentence_count_in_range(text: str, low: int, high: int) -> bool:
    n = len([s for s in re.split(r"[.!?]+\s*", text.strip()) if s.strip()])
    return low <= n <= high


def array_size(text: str, field: str, expected: int) -> bool:
    try:
        node = _parse_json_relaxed(text)
        arr = node.get(field)
        return isinstance(arr, list) and len(arr) == expected
    except Exception:
        return False


def wordcount_per_item(text: str, field: str, max_words: int) -> bool:
    try:
        node = _parse_json_relaxed(text)
        arr = node.get(field, [])
        return all(isinstance(s, str) and len(s.split()) <= max_words for s in arr)
    except Exception:
        return False


def regex_absent(text: str, patterns: Iterable[str]) -> bool:
    for p in patterns:
        if re.search(p, text, re.IGNORECASE):
            return False
    return True


def regex_present(text: str, patterns: Iterable[str]) -> bool:
    for p in patterns:
        if not re.search(p, text, re.IGNORECASE):
            return False
    return True


def contains_any(text: str, needles: Iterable[str]) -> bool:
    low = text.lower()
    return any(n.lower() in low for n in needles)


# ── Scratchpad assertions ───────────────────────────────────────────────────


def scratchpad_field_equals(scratch: dict[str, Any] | None, field: str, expected: Any) -> bool:
    if not scratch:
        return False
    return scratch.get(field) == expected


def scratchpad_field_contains(scratch: dict[str, Any] | None, field: str, needle: str) -> bool:
    if not scratch:
        return False
    v = scratch.get(field)
    if isinstance(v, str):
        return needle.lower() in v.lower()
    if isinstance(v, list):
        return any(needle.lower() in str(x).lower() for x in v)
    return False


def scratchpad_has_field(scratch: dict[str, Any] | None, field: str) -> bool:
    return bool(scratch) and field in scratch


# ── helpers ─────────────────────────────────────────────────────────────────


def _parse_json_relaxed(text: str) -> dict:
    """Parse JSON after stripping markdown fences and surrounding prose."""
    t = text.strip()
    t = re.sub(r"^```(?:json)?\s*", "", t)
    t = re.sub(r"```\s*$", "", t)
    start = t.find("{")
    end = t.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("no JSON object found")
    return json.loads(t[start : end + 1])
