"""Chat với trợ lý AI ngay trong terminal — chưa cần Android.

    venv/Scripts/python.exe chat.py                 # hiện danh sách tài khoản để chọn
    venv/Scripts/python.exe chat.py --uid <uid>     # vào chat luôn
    venv/Scripts/python.exe chat.py --uid <uid> --quiet   # ẩn dòng gọi tool
"""
import argparse
import json
import sys

import config  # noqa: F401 — import sớm để sửa mã hoá stdout trước mọi lần print
from agent import Agent
from config import get_db
from tools.context import load_ctx

DIM, BOLD, CYAN, YELLOW, GREEN, RESET = (
    "\033[2m", "\033[1m", "\033[36m", "\033[33m", "\033[32m", "\033[0m")


def list_accounts():
    """In vài tài khoản có sẵn để chọn khi chạy lần đầu."""
    db = get_db()
    users = [(d.id, d.to_dict() or {}) for d in db.collection("users").limit(100).stream()]
    # đếm số lượt điểm danh để gợi ý tài khoản nhiều dữ liệu nhất
    counts = {}
    for d in db.collection("attendances").limit(500).stream():
        sid = (d.to_dict() or {}).get("studentId")
        if sid:
            counts[sid] = counts.get(sid, 0) + 1

    print(f"\n{BOLD}Chọn một tài khoản rồi chạy lại với --uid <uid>{RESET}\n")
    for role in ("teacher", "student"):
        rows = [(u, d) for u, d in users if d.get("role") == role]
        rows.sort(key=lambda x: -counts.get(x[0], 0))
        print(f"{BOLD}{'GIẢNG VIÊN' if role == 'teacher' else 'SINH VIÊN'}{RESET}")
        for uid, d in rows[:6]:
            n = counts.get(uid, 0)
            extra = f"  {DIM}({n} lượt điểm danh){RESET}" if n else ""
            print(f"  {uid}  {d.get('studentCode',''):12} {d.get('name','')}{extra}")
        print()


def main():
    ap = argparse.ArgumentParser(description="Chat AI với dữ liệu điểm danh thật")
    ap.add_argument("--uid", help="uid Firebase của người dùng muốn đóng vai")
    ap.add_argument("--quiet", action="store_true", help="ẩn dòng hiển thị tool được gọi")
    args = ap.parse_args()

    if not args.uid:
        list_accounts()
        return

    ctx = load_ctx(args.uid)
    agent = Agent(ctx)

    vai_tro = "Giảng viên" if ctx.is_teacher else "Sinh viên"
    print(f"\n{BOLD}Trợ lý điểm danh AttendanceApp{RESET}")
    print(f"{DIM}Đang đóng vai: {vai_tro} {ctx.name} ({ctx.student_code}){RESET}")
    print(f"{DIM}Gõ câu hỏi bằng tiếng Việt. Gõ 'thoat' hoặc Ctrl+C để kết thúc.{RESET}\n")

    def on_wait(sec, note=None):
        if note:
            print(f"  {YELLOW}↻ {note}{RESET}")
        else:
            print(f"  {YELLOW}⏳ Cham gioi han request/phut. Cho {sec}s roi thu lai...{RESET}")

    def on_tool(name, params):
        if not args.quiet:
            p = json.dumps(params, ensure_ascii=False) if params else ""
            print(f"  {DIM}↳ gọi {name}({p}){RESET}")

    while True:
        try:
            q = input(f"{CYAN}Bạn › {RESET}").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nTạm biệt.")
            return
        if not q:
            continue
        if q.lower() in {"thoat", "thoát", "exit", "quit", "q"}:
            print("Tạm biệt.")
            return

        try:
            reply = agent.ask(q, on_tool=on_tool, on_wait=on_wait)
        except Exception as e:  # noqa: BLE001
            print(f"  {YELLOW}Lỗi: {type(e).__name__}: {e}{RESET}\n")
            continue

        print(f"\n{GREEN}AI ›{RESET} {reply.text}")
        if not args.quiet and reply.tokens:
            print(f"{DIM}      [{reply.tokens} token · {len(reply.tool_calls)} lần gọi tool]{RESET}")
        print()


if __name__ == "__main__":
    sys.exit(main())
