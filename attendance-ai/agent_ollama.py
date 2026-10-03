"""Agent chạy trên Ollama — mô hình nằm trên máy chủ của mình, không gọi ra ngoài.

Dùng API gốc của Ollama (/api/chat) thay vì lớp tương thích OpenAI (/v1), vì
chỉ API gốc mới cho đặt num_ctx và tắt chế độ suy nghĩ (think) theo từng request.

Agent KHÔNG biết gì về HTTP phía client: nó nhận chuỗi, trả về object, nên dùng
được cho cả CLI lẫn server web.
"""
import json
import re

import httpx

from config import (OLLAMA_KEEP_ALIVE, OLLAMA_MODEL, OLLAMA_NUM_CTX, OLLAMA_TIMEOUT,
                    OLLAMA_URL)
from prompt import MAX_TOOL_STEPS, Reply, build_local_prompt
from tools.registry import call_tool, specs_for

# Một client cho cả tiến trình: giữ kết nối, an toàn khi nhiều luồng dùng chung.
_http = httpx.Client(base_url=OLLAMA_URL, timeout=httpx.Timeout(OLLAMA_TIMEOUT, connect=5))

# Model không có chế độ suy nghĩ sẽ từ chối tham số think -> nhớ lại để lần sau khỏi gửi.
_think_unsupported: set[str] = set()

_THINK_RE = re.compile(r"<think>.*?</think>\s*", re.S)


class OllamaError(RuntimeError):
    """Lỗi đã được dịch sang câu dễ hiểu, hiện thẳng cho người dùng được."""


def _explain(e: Exception) -> OllamaError:
    if isinstance(e, OllamaError):
        return e
    if isinstance(e, httpx.ConnectError):
        return OllamaError(f"Không kết nối được Ollama tại {OLLAMA_URL}. "
                           "Máy chủ mô hình đang tắt?")
    if isinstance(e, httpx.TimeoutException):
        return OllamaError("Mô hình trả lời quá lâu, bạn thử lại sau nhé.")
    return OllamaError(f"Lỗi mô hình: {type(e).__name__}")


def _args(raw) -> dict:
    """Ollama thường trả arguments là dict, nhưng vài model trả chuỗi JSON."""
    if isinstance(raw, dict):
        return raw
    try:
        v = json.loads(raw or "{}")
        return v if isinstance(v, dict) else {}
    except (TypeError, ValueError):
        return {}


def _tool_message(name: str, result) -> dict:
    return {"role": "tool", "tool_name": name,
            "content": json.dumps({"result": result}, ensure_ascii=False, default=str)}


class _ThinkFilter:
    """Lọc khối <think>…</think> khỏi luồng chữ, kể cả khi thẻ bị cắt ngang giữa hai mẩu.

    Bình thường think=false đã tắt rồi; đây là lưới an toàn cho model không tôn trọng cờ đó.
    """

    def __init__(self):
        self.buf = ""
        self.inside = False

    def feed(self, s: str) -> str:
        self.buf += s
        out = []
        while self.buf:
            if self.inside:
                j = self.buf.find("</think>")
                if j < 0:
                    self.buf = self.buf[-len("</think>"):]   # giữ đuôi phòng thẻ đóng bị cắt
                    break
                self.buf = self.buf[j + len("</think>"):].lstrip()
                self.inside = False
            else:
                j = self.buf.find("<think>")
                if j >= 0:
                    out.append(self.buf[:j])
                    self.buf = self.buf[j + len("<think>"):]
                    self.inside = True
                    continue
                # Có thể "<thi" đang chờ phần còn lại ở mẩu sau -> giữ lại
                k = self.buf.rfind("<")
                if k >= 0 and "<think>".startswith(self.buf[k:]):
                    out.append(self.buf[:k])
                    self.buf = self.buf[k:]
                else:
                    out.append(self.buf)
                    self.buf = ""
                break
        return "".join(out)

    def flush(self) -> str:
        rest = "" if self.inside else self.buf
        self.buf = ""
        return rest


class OllamaAgent:
    def __init__(self, ctx, history=None):
        """history: các lượt chữ đã lưu [{'role','text'}], dùng khi mở lại một hội thoại cũ."""
        self.ctx = ctx
        self.model = OLLAMA_MODEL
        self.tools = [{"type": "function", "function": t} for t in specs_for(ctx)]
        self.messages: list[dict] = [{"role": "system", "content": build_local_prompt(ctx)}]
        for t in history or []:
            role = "user" if t["role"] == "user" else "assistant"
            self.messages.append({"role": role, "content": t["text"]})

    # ─────────────────────── gọi Ollama ───────────────────────
    def _body(self, stream: bool, messages: list | None = None) -> dict:
        body = {
            "model": self.model,
            "messages": self.messages if messages is None else messages,
            "tools": self.tools,
            "stream": stream,
            "keep_alive": OLLAMA_KEEP_ALIVE,
            # Nhiệt độ thấp -> gọi tool và điền tham số ổn định hơn.
            "options": {"num_ctx": OLLAMA_NUM_CTX, "temperature": 0.2},
        }
        if self.model not in _think_unsupported:
            # Suy nghĩ dài dòng làm chậm gấp nhiều lần trên GPU nhỏ mà không cần cho tra cứu.
            body["think"] = False
        return body

    def _check(self, r: httpx.Response):
        if r.status_code < 400:
            return
        try:
            msg = r.json().get("error", r.text)
        except ValueError:
            msg = r.text
        if "think" in msg and self.model not in _think_unsupported:
            _think_unsupported.add(self.model)
            raise _RetryWithoutThink()
        if r.status_code == 404 or "not found" in msg:
            raise OllamaError(f"Chưa có model '{self.model}' trên máy chủ. "
                              f"Chạy: ollama pull {self.model}")
        raise OllamaError(f"Ollama báo lỗi: {msg[:200]}")

    def _chat(self, messages: list | None = None) -> dict:
        for _ in range(2):
            try:
                r = _http.post("/api/chat", json=self._body(stream=False, messages=messages))
                self._check(r)
                return r.json()
            except _RetryWithoutThink:
                continue
            except Exception as e:  # noqa: BLE001
                raise _explain(e) from e
        raise OllamaError("Ollama từ chối request.")

    def _chat_stream(self, messages: list | None = None):
        """Sinh ra từng dòng JSON của Ollama."""
        for _ in range(2):
            try:
                with _http.stream("POST", "/api/chat", json=self._body(stream=True, messages=messages)) as r:
                    if r.status_code >= 400:
                        r.read()
                        self._check(r)
                    for line in r.iter_lines():
                        if not line:
                            continue
                        d = json.loads(line)
                        if "error" in d:
                            raise OllamaError(f"Ollama báo lỗi: {d['error'][:200]}")
                        yield d
                return
            except _RetryWithoutThink:
                continue
            except Exception as e:  # noqa: BLE001
                raise _explain(e) from e

    # ─────────────────────── lịch sử ───────────────────────
    def export_history(self) -> list[dict]:
        """Lịch sử dạng trung lập [{'role': 'user'|'model', 'text'}] để chuyển sang provider khác."""
        return [{"role": "user" if m["role"] == "user" else "model",
                 "text": _THINK_RE.sub("", m["content"]).strip()}
                for m in self.messages[1:]
                if m["role"] in ("user", "assistant") and m.get("content")
                and m["content"] != _NUDGE and _THINK_RE.sub("", m["content"]).strip()]

    def _prune_history(self):
        """Bỏ phần gọi tool khỏi lịch sử sau mỗi lượt, chỉ giữ lại chữ.

        Kết quả tool cũ còn trong ngữ cảnh thì lượt sau model đọc lại con số cũ thay
        vì tra mới (hỏi lại sau khi vừa tạo lớp vẫn ra "không có lớp nào"). Dọn đi
        thì buộc phải gọi tool lại — và cũng giống hệt phiên dựng lại từ Firestore.
        Với mô hình local, việc này còn giữ ngữ cảnh gọn trong num_ctx.
        """
        kept = [self.messages[0]]
        for m in self.messages[1:]:
            if m["role"] not in ("user", "assistant"):
                continue
            text = _THINK_RE.sub("", m.get("content") or "").strip()
            if not text:
                continue  # lượt chỉ có tool_calls
            if kept[-1]["role"] == m["role"]:
                kept[-1] = {"role": m["role"], "content": kept[-1]["content"] + "\n" + text}
            else:
                kept.append({"role": m["role"], "content": text})
        self.messages = kept

    def _retry_prompt(self, text: str) -> str:
        before = [m["content"] for m in self.messages[1:-1] if m["role"] == "user"][-3:]
        ctx = ""
        if before:
            ctx = ("Các câu người dùng hỏi trước đó (ĐÃ trả lời xong, chỉ để hiểu ngữ cảnh):\n"
                   + "\n".join(f"- {q}" for q in before) + "\n\n")
        return f"{ctx}Câu hỏi cần xử lý bây giờ: {text}\n\n{_NUDGE}"

    # ─────────────────────── vòng hỏi–đáp ───────────────────────
    def _turn(self, text: str, stream: bool):
        """Lõi chung của ask() và ask_stream(). Sinh sự kiện; sự kiện tool có kèm "result".

        Chống bịa số: model nhỏ hay trả lời luôn mà không tra — nhất là chép lại câu
        trả lời ở lượt trước ("hôm nay dạy mấy lớp" ngay sau "mai dạy mấy lớp").
        Ollama không có tool_choice="required", nên nếu lượt ĐẦU không gọi tool:
          - hỏi lại MỘT lần bằng một tin nhắn duy nhất: vài câu hỏi trước (chỉ để hiểu
            "thế tuần sau thì sao?") + câu cần trả lời + lời nhắc. Không kèm câu trả lời
            cũ nên không có gì để chép. Không để các câu hỏi cũ thành lượt riêng — model
            sẽ tưởng chúng chưa được trả lời và đi trả lời nhầm câu;
          - lần này gọi tool -> đi tiếp bình thường, ghi vào lịch sử đầy đủ;
          - vẫn không gọi -> câu này đúng là không cần dữ liệu (chào hỏi, yêu cầu
            sửa dữ liệu) -> dùng câu trả lời ĐẦU TIÊN. Câu của lần hỏi lại chỉ là
            "KHONG_CAN", không bao giờ tới tay người dùng.
        Lưới an toàn cuối: câu trả lời không qua tool mà có chữ số thì gần như chắc
        là số bịa -> thay bằng lời đề nghị hỏi rõ hơn (xem _NO_DATA).

        Vì vậy chữ của lượt đầu phải giữ lại, chưa phát ra, cho đến khi biết nó
        có bị bỏ hay không. Từ lượt sau (đã có kết quả tool) thì phát ra ngay.
        """
        self.messages.append({"role": "user", "content": text})
        tokens, called_any = 0, False
        retry = None          # lịch sử rút gọn cho lần hỏi lại; None = dùng lịch sử thật
        first = None          # (chữ đã lọc, chữ gốc) của câu trả lời đầu, khi nó chưa gọi tool
        try:
            for _ in range(MAX_TOOL_STEPS):
                hold = not called_any
                calls, content, held, filt = [], [], [], _ThinkFilter()
                for d in (self._chat_stream(retry) if stream else [self._chat(retry)]):
                    msg = d.get("message") or {}
                    if msg.get("tool_calls"):
                        calls += msg["tool_calls"]
                    if msg.get("content"):
                        content.append(msg["content"])
                        delta = filt.feed(msg["content"])
                        if delta:
                            if hold:
                                held.append(delta)
                            else:
                                yield {"type": "text", "delta": delta}
                    if d.get("done"):
                        tokens += (d.get("prompt_eval_count") or 0) + (d.get("eval_count") or 0)
                tail = filt.flush()
                if tail and hold:
                    held.append(tail)
                elif tail:
                    yield {"type": "text", "delta": tail}

                if not calls and hold and first is None:
                    first = ("".join(held), "".join(content))
                    retry = [self.messages[0], {"role": "user", "content": self._retry_prompt(text)}]
                    continue
                retry = None
                if not calls and hold and first is not None:   # hỏi lại vẫn KHONG_CAN
                    held, content = [first[0]], [first[1]]
                    if _invented_numbers(first[0], text):
                        held, content = [_NO_DATA], [_NO_DATA]
                if held:
                    yield {"type": "text", "delta": "".join(held)}

                self.messages.append({"role": "assistant", "content": "".join(content),
                                      **({"tool_calls": calls} if calls else {})})
                if not calls:
                    yield {"type": "done", "tokens": tokens}
                    return

                called_any = True
                for c in calls:
                    fn = c.get("function") or {}
                    name, args = fn.get("name", ""), _args(fn.get("arguments"))
                    result = call_tool(self.ctx, name, args)   # ctx do máy chủ cấp
                    self.messages.append(_tool_message(name, result))
                    yield {"type": "tool", "name": name, "args": args, "result": result}

            yield {"type": "text", "delta": _TOO_MANY_STEPS}
            yield {"type": "done", "tokens": tokens}
        finally:
            # Kể cả khi lỗi hay client ngắt giữa chừng -> không để lịch sử dang dở
            self._prune_history()

    # ─────────────────────── API công khai ───────────────────────
    def ask(self, text: str, on_tool=None, on_wait=None) -> Reply:  # noqa: ARG002 — on_wait chỉ Gemini cần
        parts, used, tokens = [], [], 0
        for ev in self._turn(text, stream=False):
            if ev["type"] == "text":
                parts.append(ev["delta"])
            elif ev["type"] == "tool":
                if on_tool:
                    on_tool(ev["name"], ev["args"])
                used.append((ev["name"], ev["args"], ev["result"]))
            elif ev["type"] == "done":
                tokens = ev["tokens"]
        answer = "".join(parts).strip()
        return Reply(answer or "(không có nội dung trả lời)", used, tokens)

    def ask_stream(self, text: str):
        """Sinh ra từng sự kiện một, để server đẩy dần về client.

        Sự kiện:
          {"type":"tool", "name":..., "args":{...}}   đang tra cứu dữ liệu
          {"type":"text", "delta":"..."}              một mẩu câu trả lời
          {"type":"done", "tokens":N}                 kết thúc
          {"type":"error","message":"..."}            lỗi
        """
        try:
            for ev in self._turn(text, stream=True):
                if ev["type"] == "tool":
                    ev = {k: v for k, v in ev.items() if k != "result"}   # dữ liệu thô không gửi về app
                yield ev
        except OllamaError as e:
            yield {"type": "error", "message": str(e)}


_NUDGE = ("Bạn chưa tra cứu. Nếu câu hỏi cần xử lý liên quan tới lớp, lịch học/lịch dạy, "
          "điểm danh hay sinh viên thì GỌI TOOL phù hợp ngay bây giờ, không tự đoán. "
          "Nếu câu đó thật sự không cần dữ liệu (chào hỏi, chuyện phiếm, yêu cầu "
          "tạo/sửa/xóa) thì chỉ trả lời đúng một từ: KHONG_CAN")

_NO_DATA = ("Mình chưa tra được dữ liệu cho câu này. Bạn hỏi cụ thể hơn giúp mình nhé, "
            "ví dụ: \"tuần sau tôi có những buổi nào?\"")

_NUM = re.compile(r"\d+")


def _invented_numbers(answer: str, question: str) -> bool:
    """Câu trả lời có con số nào KHÔNG nằm sẵn trong câu hỏi không.

    Số lặp lại từ câu hỏi ("vắng quá 20%", "năm 2028") là bình thường; số mới xuất
    hiện mà không qua tool thì gần như chắc là bịa.
    """
    return bool(set(_NUM.findall(answer)) - set(_NUM.findall(question)))

_TOO_MANY_STEPS = "\n\nCâu hỏi này cần quá nhiều bước tra cứu, bạn thử hỏi cụ thể hơn nhé."


class _RetryWithoutThink(Exception):
    pass
