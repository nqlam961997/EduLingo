"""Layer 3 live-model eval runner. See README for usage."""
from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import httpx
import yaml

import rubrics


HERE = Path(__file__).parent
GOLDENS_DIR = HERE / "goldens"
REPORTS_DIR = HERE / "reports"

BACKEND_URL = os.getenv("EDULINGO_BACKEND_URL", "http://localhost:8080")
OLLAMA_URL  = os.getenv("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gemma2:9b")
DEFAULT_TRIALS = int(os.getenv("EVAL_TRIALS", "3"))

# Pass thresholds per chat-eval-harness spec.
THRESH_DETERMINISTIC = 0.95
THRESH_BEHAVIORAL    = 0.80
THRESH_SCRATCHPAD    = 0.95


# ── data classes ────────────────────────────────────────────────────────────


@dataclass
class AssertionResult:
    id: str
    kind: str          # "deterministic" | "behavioral" | "scratchpad"
    turn: int | None
    passed: bool


@dataclass
class GoldenResult:
    golden_id: str
    trials: int
    assertions: list[AssertionResult] = field(default_factory=list)


# ── golden loader ───────────────────────────────────────────────────────────


def load_golden(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def find_goldens(name: str | None) -> list[Path]:
    if name:
        return [GOLDENS_DIR / f"{name}.yml"]
    return sorted(GOLDENS_DIR.glob("*.yml"))


# ── backend + ollama clients ────────────────────────────────────────────────


def assemble_prompt(topic_id: str, cefr: str,
                    history: list[dict], newest: str,
                    scratchpad: str | None) -> dict:
    payload = {
        "topicId": topic_id,
        "learnerCefr": cefr,
        "history": history,
        "newestMessage": newest,
        "scratchpad": scratchpad,
    }
    resp = httpx.post(f"{BACKEND_URL}/api/internal/debug/assemble-prompt",
                      json=payload, timeout=30)
    resp.raise_for_status()
    return resp.json()


def call_ollama(system_prompt: str, messages: list[dict]) -> str:
    ollama_messages = [{"role": "system", "content": system_prompt}]
    for m in messages:
        role = m.get("role")
        role_str = "assistant" if str(role).upper() == "ASSISTANT" else "user"
        ollama_messages.append({"role": role_str, "content": m.get("content", "")})
    payload = {
        "model": OLLAMA_MODEL,
        "messages": ollama_messages,
        "stream": False,
    }
    resp = httpx.post(f"{OLLAMA_URL}/api/chat", json=payload, timeout=120)
    resp.raise_for_status()
    return resp.json().get("message", {}).get("content", "")


# ── assertion dispatch ──────────────────────────────────────────────────────


def parse_assistant_payload(raw: str) -> dict | None:
    try:
        return rubrics._parse_json_relaxed(raw)
    except Exception:
        return None


def eval_per_turn(spec: dict, raw_reply: str, turn_idx: int) -> bool:
    fn = spec["fn"]
    args = spec.get("args", {}) or {}
    payload = parse_assistant_payload(raw_reply) or {}

    if fn == "valid_json":
        return rubrics.valid_json(raw_reply)
    if fn == "has_fields":
        return rubrics.has_fields(raw_reply, args["required"])
    if fn == "field_missing":
        return rubrics.field_missing(raw_reply, args["field"])
    if fn == "array_size":
        return rubrics.array_size(raw_reply, args["field"], args["expected"])
    if fn == "wordcount_per_item":
        return rubrics.wordcount_per_item(raw_reply, args["field"], args["max_words"])
    if fn == "regex_absent":
        return rubrics.regex_absent(raw_reply, args["patterns"])
    if fn == "regex_present":
        return rubrics.regex_present(raw_reply, args["patterns"])
    if fn == "reply_sentence_count_in_range":
        return rubrics.sentence_count_in_range(
                str(payload.get("reply", "")), args["low"], args["high"])
    if fn == "contains_any_in_field":
        arr = payload.get(args["field"], [])
        items = arr if isinstance(arr, list) else []
        items_txt = " ".join(json.dumps(x) for x in items)
        return rubrics.contains_any(items_txt, args["needles"])
    if fn == "contains_any_in_reply":
        return rubrics.contains_any(str(payload.get("reply", "")), args["needles"])
    if fn == "contains_any_in_reply_or_suggestions":
        haystack = str(payload.get("reply", ""))
        sug = payload.get("suggestions", [])
        if isinstance(sug, list):
            haystack += " " + " ".join(str(s) for s in sug)
        return rubrics.contains_any(haystack, args["needles"])
    raise ValueError(f"unknown per-turn fn: {fn}")


def eval_scratchpad(spec: dict, scratch: dict | None) -> bool:
    fn = spec["fn"]
    args = spec.get("args", {}) or {}
    if fn == "scratchpad_field_equals":
        return rubrics.scratchpad_field_equals(scratch, args["field"], args["expected"])
    if fn == "scratchpad_field_contains":
        return rubrics.scratchpad_field_contains(scratch, args["field"], args["needle"])
    if fn == "scratchpad_has_field":
        return rubrics.scratchpad_has_field(scratch, args["field"])
    raise ValueError(f"unknown scratchpad fn: {fn}")


# ── single golden run ───────────────────────────────────────────────────────


def run_golden(golden: dict, trials: int) -> GoldenResult:
    gid = golden["id"]
    topic = golden["topic"]
    cefr  = golden.get("cefr", "A2")
    inputs = golden["learner_inputs"]
    per_turn_specs   = golden.get("per_turn", []) or []
    scratchpad_specs = golden.get("scratchpad", []) or []

    result = GoldenResult(golden_id=gid, trials=trials)

    for trial in range(trials):
        history: list[dict] = []  # accumulates {role, content} as the dialogue grows
        last_scratchpad: dict | None = None

        for turn_idx, user_msg in enumerate(inputs, start=1):
            # Render the same prompt the backend would assemble
            scratch_str = json.dumps(last_scratchpad) if last_scratchpad else None
            assembled = assemble_prompt(topic, cefr, history, user_msg, scratch_str)
            raw_reply = call_ollama(assembled["systemPrompt"], assembled["messages"])

            # Per-turn assertions
            for spec in per_turn_specs:
                only_turn = spec.get("only_turn")
                if only_turn is not None and only_turn != turn_idx:
                    continue
                ok = False
                try:
                    ok = eval_per_turn(spec, raw_reply, turn_idx)
                except Exception:
                    ok = False
                result.assertions.append(AssertionResult(
                        id=f"{spec['id']}#trial{trial+1}_T{turn_idx}",
                        kind=spec.get("kind", "deterministic"),
                        turn=turn_idx,
                        passed=ok))

            # Extend history: assistant message uses parsed reply when possible
            payload = parse_assistant_payload(raw_reply) or {}
            reply_text = payload.get("reply") or raw_reply
            history.append({"role": "user",      "content": user_msg})
            history.append({"role": "assistant", "content": reply_text})

            # Best-effort: parse a "state" field if the model emitted one
            # (some goldens may rely on this). Real scratchpad extraction is
            # backend-side; for evals we accept any "state" field on the reply
            # as the trial's scratchpad proxy.
            if isinstance(payload.get("state"), dict):
                last_scratchpad = payload["state"]

        # End-of-chat scratchpad assertions (one set per trial)
        for spec in scratchpad_specs:
            ok = False
            try:
                ok = eval_scratchpad(spec, last_scratchpad)
            except Exception:
                ok = False
            result.assertions.append(AssertionResult(
                    id=f"{spec['id']}#trial{trial+1}",
                    kind="scratchpad",
                    turn=None,
                    passed=ok))

    return result


# ── reporting ───────────────────────────────────────────────────────────────


def category_pass_rate(results: list[GoldenResult], kind: str) -> tuple[int, int]:
    total = 0
    passed = 0
    for r in results:
        for a in r.assertions:
            if a.kind == kind:
                total += 1
                if a.passed:
                    passed += 1
    return passed, total


def write_report(results: list[GoldenResult],
                 thresholds: dict[str, float],
                 path: Path) -> None:
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    lines.append(f"# EduLingo eval run — {dt.datetime.now().isoformat(timespec='seconds')}")
    lines.append(f"Model: `{OLLAMA_MODEL}`  Trials: {results[0].trials if results else 0}\n")

    for r in results:
        lines.append(f"## GOLDEN `{r.golden_id}`\n")
        by_id: dict[str, list[AssertionResult]] = {}
        for a in r.assertions:
            stem = re.sub(r"#.*$", "", a.id)
            by_id.setdefault(stem, []).append(a)
        lines.append("| assertion | kind | passed / total |")
        lines.append("| --- | --- | --- |")
        for stem, runs in by_id.items():
            p = sum(1 for x in runs if x.passed)
            t = len(runs)
            lines.append(f"| `{stem}` | {runs[0].kind} | {p}/{t} |")
        lines.append("")

    # Overall totals
    lines.append("## Overall\n")
    for kind, label in (("deterministic", "Deterministic"),
                         ("behavioral", "Behavioral"),
                         ("scratchpad", "Scratchpad")):
        p, t = category_pass_rate(results, kind)
        rate = (p / t) if t else 1.0
        threshold = thresholds[kind]
        marker = "✓" if rate >= threshold else "✗"
        lines.append(f"- {label}: **{p}/{t}** ({rate:.0%}) — threshold {threshold:.0%} {marker}")
    lines.append("")

    # Regression diff vs latest previous report
    diff = regression_diff(results)
    if diff:
        lines.append("## Regression from previous run\n")
        for line in diff:
            lines.append(f"- {line}")
        lines.append("")

    path.write_text("\n".join(lines), encoding="utf-8")


def regression_diff(current: list[GoldenResult]) -> list[str]:
    """Compare current pass counts against the most recent prior report."""
    prior_reports = sorted(REPORTS_DIR.glob("*.md"))
    if len(prior_reports) < 1:
        return []
    last = prior_reports[-1].read_text(encoding="utf-8")
    # Naive regex parse of the previous report's table rows
    prev_counts: dict[str, tuple[int, int]] = {}
    for m in re.finditer(r"\|\s*`([^`]+)`\s*\|\s*\w+\s*\|\s*(\d+)/(\d+)\s*\|", last):
        prev_counts[m.group(1)] = (int(m.group(2)), int(m.group(3)))

    diffs: list[str] = []
    for r in current:
        by_id: dict[str, tuple[int, int]] = {}
        for a in r.assertions:
            stem = re.sub(r"#.*$", "", a.id)
            cur = by_id.get(stem, (0, 0))
            by_id[stem] = (cur[0] + (1 if a.passed else 0), cur[1] + 1)
        for stem, (p, t) in by_id.items():
            if stem in prev_counts:
                pp, pt = prev_counts[stem]
                if p < pp:
                    diffs.append(f"`{stem}`: {pp}/{pt} → **{p}/{t}**")
    return diffs


def overall_pass(results: list[GoldenResult]) -> bool:
    for kind, threshold in (("deterministic", THRESH_DETERMINISTIC),
                             ("behavioral",    THRESH_BEHAVIORAL),
                             ("scratchpad",    THRESH_SCRATCHPAD)):
        p, t = category_pass_rate(results, kind)
        rate = (p / t) if t else 1.0
        if rate < threshold:
            return False
    return True


# ── main ────────────────────────────────────────────────────────────────────


def main() -> int:
    ap = argparse.ArgumentParser(description="EduLingo Layer 3 eval runner")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--all", action="store_true")
    g.add_argument("--golden", help="Golden id (filename minus .yml)")
    ap.add_argument("--trials", type=int, default=DEFAULT_TRIALS)
    ap.add_argument("--no-regression", action="store_true",
                    help="Skip regression diff in the report")
    args = ap.parse_args()

    paths = find_goldens(None if args.all else args.golden)
    if not paths:
        print(f"No goldens matched", file=sys.stderr)
        return 2

    results: list[GoldenResult] = []
    for p in paths:
        golden = load_golden(p)
        print(f"== running golden: {golden['id']} ({args.trials} trial(s)) ==")
        results.append(run_golden(golden, args.trials))

    ts = dt.datetime.now().strftime("%Y-%m-%dT%H-%M-%S")
    report_path = REPORTS_DIR / f"{ts}.md"
    write_report(results, {
        "deterministic": THRESH_DETERMINISTIC,
        "behavioral":    THRESH_BEHAVIORAL,
        "scratchpad":    THRESH_SCRATCHPAD,
    }, report_path)
    print(f"Report written to {report_path}")

    return 0 if overall_pass(results) else 1


if __name__ == "__main__":
    sys.exit(main())
