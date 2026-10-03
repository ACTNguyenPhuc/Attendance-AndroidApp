"""Kiểm tra máy chủ Ollama: có chạy không, có model chưa, model có gọi tool được không.

    venv/Scripts/python.exe diagnostics/probe_ollama.py
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import httpx  # noqa: E402

from config import OLLAMA_MODEL, OLLAMA_NUM_CTX, OLLAMA_URL  # noqa: E402

print(f"Ollama: {OLLAMA_URL}   model: {OLLAMA_MODEL}   num_ctx: {OLLAMA_NUM_CTX}\n")

try:
    ver = httpx.get(f"{OLLAMA_URL}/api/version", timeout=5).json()["version"]
    print(f"  ✅ Ollama đang chạy, phiên bản {ver}")
except Exception as e:  # noqa: BLE001
    sys.exit(f"  ❌ Không kết nối được: {type(e).__name__}. Đã bật Ollama chưa?")

names = [m["name"] for m in httpx.get(f"{OLLAMA_URL}/api/tags", timeout=5).json()["models"]]
if OLLAMA_MODEL not in names and f"{OLLAMA_MODEL}:latest" not in names:
    sys.exit(f"  ❌ Chưa có model {OLLAMA_MODEL}. Có: {names or 'không có'}\n"
             f"     Chạy: ollama pull {OLLAMA_MODEL}")
print(f"  ✅ Đã có model {OLLAMA_MODEL}")

tool = {"type": "function", "function": {
    "name": "get_attendance",
    "description": "Lấy số buổi vắng của sinh viên trong một lớp",
    "parameters": {"type": "object", "properties": {"class_id": {"type": "string", "description": "Mã lớp"}},
                   "required": ["class_id"]},
}}
body = {"model": OLLAMA_MODEL, "stream": False, "think": False, "tools": [tool],
        "options": {"num_ctx": OLLAMA_NUM_CTX, "temperature": 0.2},
        "messages": [{"role": "user", "content": "Tôi vắng mấy buổi lớp KTPMN_L03?"}]}

t = time.time()
r = httpx.post(f"{OLLAMA_URL}/api/chat", json=body, timeout=300)
if r.status_code >= 400 and "think" in r.text:
    body.pop("think")
    r = httpx.post(f"{OLLAMA_URL}/api/chat", json=body, timeout=300)
r.raise_for_status()
d = r.json()
calls = (d.get("message") or {}).get("tool_calls") or []
print(f"  {'✅' if calls else '❌'} Gọi tool: "
      f"{[(c['function']['name'], c['function']['arguments']) for c in calls] or 'KHÔNG gọi'}"
      f"   ({time.time() - t:.1f}s, gồm cả thời gian nạp model lần đầu)")

ps = httpx.get(f"{OLLAMA_URL}/api/ps", timeout=5).json().get("models", [])
for m in ps:
    total, vram = m.get("size", 0), m.get("size_vram", 0)
    pct = 100 * vram / total if total else 0
    print(f"  ℹ  {m['name']}: {total / 2**30:.1f} GB, {pct:.0f}% nằm trên GPU"
          + ("" if pct > 99 else "  ← phần còn lại chạy CPU, sẽ chậm hơn"))
