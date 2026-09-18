"""Chạy bộ kiểm thử chất lượng chatbot.

    venv/Scripts/python.exe evals/run.py              # chạy hết 20 ca
    venv/Scripts/python.exe evals/run.py --only sv01  # chạy 1 ca
    venv/Scripts/python.exe evals/run.py --limit 5    # chạy 5 ca đầu

Chấm điểm bằng luật cố định (không dùng AI chấm AI): kiểm tra đã gọi đúng tool chưa,
và câu trả lời có chứa đúng con số / đúng từ khoá không. Đáp án lấy từ script dò
dữ liệu độc lập, nên nếu tầng tool tính sai thì bài test sẽ bắt được.
"""
import argparse
import json
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: F401,E402  — sửa mã hoá stdout
from agent import Agent  # noqa: E402
from tools.context import load_ctx  # noqa: E402

HERE = Path(__file__).resolve().parent
DIM, BOLD, RED, GREEN, YELLOW, RESET = "\033[2m", "\033[1m", "\033[31m", "\033[32m", "\033[33m", "\033[0m"


def has_number(text: str, n) -> bool:
    return re.search(rf"(?<![\d]){re.escape(str(n))}(?![\d])", text) is not None


def grade(case: dict, answer: str, called: list[str]) -> list[str]:
    """Trả về danh sách lỗi. Rỗng = đạt."""
    fails = []
    low = answer.lower()

    want = case.get("tools") or []
    if want:
        if case.get("tools_any"):
            if not any(t in called for t in want):
                fails.append(f"không gọi tool nào trong {want} (đã gọi: {called or 'không có'})")
        else:
            for t in want:
                if t not in called:
                    fails.append(f"thiếu gọi tool {t} (đã gọi: {called or 'không có'})")

    for t in case.get("forbid_tools") or []:
        if t in called:
            fails.append(f"không được gọi tool {t}")

    for n in case.get("numbers") or []:
        if not has_number(answer, n):
            fails.append(f"thiếu số {n}")

    for s in case.get("contains") or []:
        if s.lower() not in low:
            fails.append(f"thiếu cụm {s!r}")

    for s in case.get("not_contains") or []:
        if s.lower() in low:
            fails.append(f"không được chứa {s!r}")

    return fails


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="chỉ chạy ca có id này")
    ap.add_argument("--limit", type=int, help="chỉ chạy N ca đầu")
    ap.add_argument("--delay", type=float, default=13.0,
                    help="giây nghỉ giữa các ca, tránh chạm 5 request/phút của gói miễn phí")
    args = ap.parse_args()

    data = json.loads((HERE / "questions.json").read_text(encoding="utf-8"))
    accounts, cases = data["accounts"], data["cases"]
    if args.only:
        cases = [c for c in cases if c["id"] == args.only]
    if args.limit:
        cases = cases[: args.limit]

    ctx_cache = {}
    results, passed = [], 0

    print(f"\n{BOLD}BỘ KIỂM THỬ CHATBOT — {len(cases)} ca{RESET}", flush=True)
    print(f"{DIM}nghỉ {args.delay}s giữa các ca để không chạm giới hạn gói miễn phí{RESET}\n")

    for i, case in enumerate(cases, 1):
        uid = accounts[case["acc"]]
        if uid not in ctx_cache:
            ctx_cache[uid] = load_ctx(uid)
        ctx = ctx_cache[uid]

        # Agent MỚI cho mỗi ca -> các ca độc lập, không ăn theo lịch sử của nhau
        agent = Agent(ctx)
        try:
            reply = agent.ask(case["q"], on_wait=lambda s: print(f"{DIM}   (chờ {s}s do giới hạn tốc độ){RESET}"))
            answer, called = reply.text, [t[0] for t in reply.tool_calls]
            err = None
        except Exception as e:  # noqa: BLE001
            answer, called, err = "", [], f"{type(e).__name__}: {e}"

        fails = [err] if err else grade(case, answer, called)
        ok = not fails
        passed += ok

        mark = f"{GREEN}ĐẠT {RESET}" if ok else f"{RED}HỎNG{RESET}"
        print(f"[{i:2}/{len(cases)}] {mark} {BOLD}{case['id']}{RESET} ({case['acc']})  {case['q']}", flush=True)
        print(f"        {DIM}tool: {', '.join(called) or '—'}{RESET}")
        if not ok:
            for f in fails:
                print(f"        {YELLOW}✗ {f}{RESET}", flush=True)
            print(f"        {DIM}trả lời: {answer[:220]}{RESET}")

        results.append({**case, "answer": answer, "called": called, "fails": fails, "ok": ok})
        if i < len(cases):
            time.sleep(args.delay)

    pct = round(passed * 100 / len(cases), 1) if cases else 0
    color = GREEN if pct >= 90 else (YELLOW if pct >= 70 else RED)
    print(f"\n{BOLD}KẾT QUẢ: {color}{passed}/{len(cases)} đạt ({pct}%){RESET}\n", flush=True)

    out = HERE / "last_run.json"
    out.write_text(json.dumps({"passed": passed, "total": len(cases), "percent": pct,
                               "results": results}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{DIM}chi tiết lưu ở {out}{RESET}", flush=True)
    return 0 if passed == len(cases) else 1


if __name__ == "__main__":
    sys.exit(main())
