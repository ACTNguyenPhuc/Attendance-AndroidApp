"""Điểm vào duy nhất cho phần còn lại của dự án: `from agent import Agent`.

Agent ở đây là lớp bọc, bên trong là một trong hai agent thật:
  ollama  -> agent_ollama.py  (mô hình chạy trên máy chủ của mình)
  gemini  -> agent_gemini.py  (API Google)

Chuyển đổi linh hoạt theo 3 mức:
  1. Mặc định cho phiên mới: LLM_PROVIDER trong .env, hoặc set_default_provider() lúc chạy.
  2. Từng phiên: Agent(ctx, provider="gemini"), hoặc agent.switch("ollama") giữa chừng —
     lịch sử hội thoại được mang sang provider mới.
  3. Tự động: LLM_FALLBACK=gemini -> provider đang dùng lỗi thì chuyển sang provider dự phòng
     và hỏi lại. CHỈ chuyển khi chưa gọi tool nào và chưa phát chữ nào, để không chạy lại
     một thao tác ghi dữ liệu hay trả về hai câu trả lời chồng nhau.

Agent bọc có cùng giao diện với agent thật: Agent(ctx, history), .ask(), .ask_stream(),
nên server.py, chat.py, evals/ không cần biết đang dùng bên nào.
"""
import importlib

from config import LLM_FALLBACK, LLM_PROVIDER, PROVIDERS, model_of
from prompt import MAX_TOOL_STEPS, Reply, build_system_prompt  # noqa: F401

# Import lười: dùng Ollama thì không cần cài google-genai, và ngược lại.
_IMPLS = {"ollama": ("agent_ollama", "OllamaAgent"),
          "gemini": ("agent_gemini", "GeminiAgent")}


class ProviderUnavailable(RuntimeError):
    """Không dựng được agent cho provider này (thiếu API key, thiếu thư viện...)."""


def _check(provider: str | None) -> str:
    p = (provider or "").strip().lower()
    if p not in _IMPLS:
        raise ValueError(f"provider '{provider}' không hợp lệ. Chọn: {', '.join(PROVIDERS)}")
    return p


try:
    _default = _check(LLM_PROVIDER)
    for _p in LLM_FALLBACK:
        _check(_p)
except ValueError as e:
    raise SystemExit(f"\n❌ .env: {e}\n") from e


def default_provider() -> str:
    return _default


def set_default_provider(provider: str) -> str:
    """Đổi provider cho các phiên MỚI. Phiên đang mở giữ nguyên provider của nó."""
    global _default
    _default = _check(provider)
    return _default


def _build(provider: str, ctx, history):
    mod, cls = _IMPLS[provider]
    try:
        return getattr(importlib.import_module(mod), cls)(ctx, history=history or None)
    except SystemExit as e:   # check_api_key() báo thiếu key bằng SystemExit
        raise ProviderUnavailable(f"{provider}: {str(e).strip()}") from e
    except ImportError as e:
        raise ProviderUnavailable(f"{provider}: thiếu thư viện ({e.name})") from e


class Agent:
    def __init__(self, ctx, history=None, provider: str | None = None):
        """history: các lượt chữ đã lưu [{'role','text'}], dùng khi mở lại một hội thoại cũ."""
        self.ctx = ctx
        first = _check(provider or _default)
        err = None
        for p in [first] + [p for p in LLM_FALLBACK if p != first]:
            try:
                self._impl = _build(p, ctx, history)
                self.provider = p
                return
            except ProviderUnavailable as e:
                err = e
        raise err

    @property
    def model(self) -> str:
        return getattr(self._impl, "model", model_of(self.provider))

    def _handover(self) -> list[dict]:
        """Lịch sử để trao cho provider mới: bắt đầu bằng user, kết thúc bằng model.

        Lượt user cuối chưa có trả lời là câu vừa hỏi hỏng — sẽ được hỏi lại.
        """
        h = self._impl.export_history()
        while h and h[0]["role"] != "user":
            h.pop(0)
        while h and h[-1]["role"] == "user":
            h.pop()
        return h

    def switch(self, provider: str):
        """Đổi provider cho phiên này, giữ nguyên mạch hội thoại."""
        p = _check(provider)
        if p != self.provider:
            self._impl = _build(p, self.ctx, self._handover())
            self.provider = p

    def _fallback(self, tried: set, reason: str, notify=None) -> bool:
        for p in LLM_FALLBACK:
            if p in tried:
                continue
            tried.add(p)
            old = self.provider
            try:
                self.switch(p)
            except ProviderUnavailable:
                continue
            print(f"[agent] {old} lỗi ({reason[:120]}) -> chuyển sang {p}", flush=True)
            if notify:
                notify(0, f"{old} lỗi, chuyển sang {p}")
            return True
        return False

    def ask(self, text: str, on_tool=None, on_wait=None) -> Reply:
        acted = False

        def _on_tool(name, args):
            nonlocal acted
            acted = True
            if on_tool:
                on_tool(name, args)

        tried = {self.provider}
        while True:
            try:
                return self._impl.ask(text, on_tool=_on_tool, on_wait=on_wait)
            except Exception as e:  # noqa: BLE001
                if acted or not self._fallback(tried, f"{type(e).__name__}: {e}", on_wait):
                    raise

    def ask_stream(self, text: str):
        """Như agent thật; sự kiện "done" có thêm provider/model đã trả lời."""
        tried = {self.provider}
        while True:
            started, failure = False, None
            gen = self._impl.ask_stream(text)
            try:
                for ev in gen:
                    if ev["type"] == "error" and not started:
                        failure = ev
                        break
                    if ev["type"] in ("text", "tool"):
                        started = True
                    elif ev["type"] == "done":
                        ev = {**ev, "provider": self.provider, "model": self.model}
                    yield ev
                else:
                    return
            except Exception as e:  # noqa: BLE001
                if started:
                    raise
                failure = {"type": "error", "message": f"Lỗi xử lý: {type(e).__name__}"}
            finally:
                gen.close()
            if not self._fallback(tried, failure["message"]):
                yield failure
                return


__all__ = ["Agent", "ProviderUnavailable", "Reply", "build_system_prompt",
           "default_provider", "set_default_provider", "PROVIDERS"]
