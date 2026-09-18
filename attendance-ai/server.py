"""Server HTTP cho trợ lý AI.

    venv/Scripts/python.exe -m uvicorn server:app --host 0.0.0.0 --port 8000 --reload

Rồi mở http://localhost:8000 để chat thử trong trình duyệt.
Máy ảo Android gọi vào http://10.0.2.2:8000 (xem README).

TẦNG NÀY CHỈ LO GIAO TIẾP. Không có một dòng logic nghiệp vụ nào ở đây —
toàn bộ nằm trong tools/ và agent.py, y như khi chạy CLI.
"""
import json
import os
import threading
import time
import uuid
from dataclasses import dataclass

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

import config
from agent import Agent
from config import HERE, get_db
from tools.context import load_ctx

# Cho phép gửi thẳng uid thay vì Firebase ID token — CHỈ để test local.
# Khi deploy thật BẮT BUỘC đặt DEV_MODE=false, nếu không ai cũng đóng vai được người khác.
DEV_MODE = os.getenv("DEV_MODE", "true").lower() in {"1", "true", "yes"}

SESSION_TTL = 30 * 60       # phiên hội thoại tự hết hạn sau 30 phút không dùng
MAX_SESSIONS = 200          # chặn phình RAM
RATE_LIMIT_PER_HOUR = 40    # mỗi người mỗi giờ, bảo vệ quota dùng chung

app = FastAPI(title="AttendanceApp AI")


# ─────────────────────────── Lưu phiên trong RAM ───────────────────────────
@dataclass
class Session:
    agent: Agent
    uid: str
    last: float


_sessions: dict[str, Session] = {}
_hits: dict[str, list[float]] = {}
_lock = threading.Lock()


def _sweep(now: float):
    """Dọn phiên hết hạn. Gọi khi đang giữ _lock."""
    for k in [k for k, v in _sessions.items() if now - v.last > SESSION_TTL]:
        del _sessions[k]


def get_session(session_id: str | None, ctx):
    """Lấy phiên cũ hoặc tạo mới. Trả (session_id, Session)."""
    now = time.time()
    with _lock:
        _sweep(now)
        s = _sessions.get(session_id) if session_id else None
        # Phiên phải thuộc đúng người gửi — chặn việc mượn session_id của người khác
        # để đọc hội thoại không phải của mình.
        if s and s.uid == ctx.uid:
            s.last = now
            return session_id, s

        if len(_sessions) >= MAX_SESSIONS:
            del _sessions[min(_sessions, key=lambda k: _sessions[k].last)]

        sid = uuid.uuid4().hex
        _sessions[sid] = Session(Agent(ctx), ctx.uid, now)
        return sid, _sessions[sid]


def check_rate(uid: str):
    now = time.time()
    with _lock:
        xs = [t for t in _hits.get(uid, []) if now - t < 3600]
        if len(xs) >= RATE_LIMIT_PER_HOUR:
            raise HTTPException(429, f"Bạn đã dùng {RATE_LIMIT_PER_HOUR} lượt trong 1 giờ. "
                                     "Vui lòng thử lại sau.")
        xs.append(now)
        _hits[uid] = xs


# ─────────────────────────── Xác thực ───────────────────────────
def resolve_ctx(authorization: str | None, dev_uid: str | None):
    """Dựng ctx từ Firebase ID token. ĐÂY là chỗ ctx chuyển từ giả sang thật.

    Vì mọi tool đã nhận ctx làm tham số đầu tiên ngay từ chặng 1,
    tầng tools/ không phải sửa một dòng nào khi lên server.
    """
    if authorization and authorization.lower().startswith("bearer "):
        from firebase_admin import auth

        get_db()  # đảm bảo firebase_admin đã khởi tạo
        try:
            decoded = auth.verify_id_token(authorization.split(None, 1)[1].strip())
        except Exception as e:  # noqa: BLE001
            raise HTTPException(401, f"Token không hợp lệ: {type(e).__name__}") from e
        return load_ctx(decoded["uid"])

    if DEV_MODE and dev_uid:
        return load_ctx(dev_uid)

    raise HTTPException(401, "Thiếu Firebase ID token trong header Authorization."
                             + ("" if DEV_MODE else " (DEV_MODE đang tắt)"))


# ─────────────────────────── API ───────────────────────────
class ChatIn(BaseModel):
    message: str
    session_id: str | None = None
    uid: str | None = None   # chỉ có tác dụng khi DEV_MODE bật


@app.get("/health")
def health():
    with _lock:
        n = len(_sessions)
    return {"ok": True, "dev_mode": DEV_MODE, "phien_dang_mo": n, "model": config.MODEL}


# Dùng def (không phải async def) để FastAPI chạy trong threadpool —
# lời gọi Gemini là đồng bộ, nếu để async sẽ chặn cả event loop.
@app.post("/chat")
def chat(body: ChatIn, authorization: str | None = Header(default=None)):
    if not (body.message or "").strip():
        raise HTTPException(400, "message rỗng")

    ctx = resolve_ctx(authorization, body.uid)
    check_rate(ctx.uid)
    sid, sess = get_session(body.session_id, ctx)

    try:
        reply = sess.agent.ask(body.message)
    except Exception as e:  # noqa: BLE001
        msg = str(e)
        if "RESOURCE_EXHAUSTED" in msg or "429" in msg:
            raise HTTPException(429, "Hệ thống đang quá tải (giới hạn gói miễn phí). "
                                     "Bạn thử lại sau ít giây nhé.") from e
        raise HTTPException(500, f"Lỗi xử lý: {type(e).__name__}") from e

    return {
        "session_id": sid,
        "reply": reply.text,
        "tools": [{"ten": t[0], "thamSo": t[1]} for t in reply.tool_calls],
        "tokens": reply.tokens,
        "nguoiDung": {"ten": ctx.name, "vaiTro": ctx.role},
    }


def sse(ev: dict) -> str:
    """Đóng gói một sự kiện theo chuẩn Server-Sent Events."""
    return "data: " + json.dumps(ev, ensure_ascii=False) + "\n\n"


@app.post("/chat/stream")
def chat_stream(body: ChatIn, authorization: str | None = Header(default=None)):
    """Giống /chat nhưng đẩy dần từng mẩu về client theo chuẩn SSE.

    Mỗi sự kiện là một dòng `data: {json}` rồi một dòng trống.
    Client đọc từng dòng, không phải đợi cả câu trả lời.
    """
    if not (body.message or "").strip():
        raise HTTPException(400, "message rỗng")

    ctx = resolve_ctx(authorization, body.uid)
    check_rate(ctx.uid)
    sid, sess = get_session(body.session_id, ctx)

    def gen():
        # Gửi session_id ngay để client lưu lại cho lượt sau
        yield sse({'type': 'start', 'session_id': sid})
        try:
            for ev in sess.agent.ask_stream(body.message):
                yield sse(ev)
        except Exception as e:  # noqa: BLE001
            yield sse({'type': 'error', 'message': f'Lỗi xử lý: {type(e).__name__}'})

    return StreamingResponse(gen(), media_type="text/event-stream", headers={
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",   # tắt đệm nếu có proxy đứng trước
    })


STATIC = HERE / "static"
if STATIC.exists():
    app.mount("/static", StaticFiles(directory=str(STATIC)), name="static")


@app.get("/")
def index():
    f = STATIC / "test.html"
    if not f.exists():
        return {"message": "Server đang chạy. Chưa có static/test.html."}
    return FileResponse(str(f))


@app.on_event("startup")
def _warn():
    if DEV_MODE:
        print("\n" + "!" * 68)
        print("  DEV_MODE đang BẬT — cho phép gửi uid trực tiếp, KHÔNG cần đăng nhập.")
        print("  Chỉ dùng khi chạy local. Khi deploy thật phải đặt DEV_MODE=false.")
        print("!" * 68 + "\n")
