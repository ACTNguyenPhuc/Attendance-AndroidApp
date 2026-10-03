"""Agent chạy trên Gemini API (Google). Bật bằng LLM_PROVIDER=gemini.

Agent KHÔNG biết gì về HTTP: nó nhận chuỗi, trả về object. Nhờ vậy dùng được
cho cả CLI lẫn server web.
"""
import re
import time

from google import genai
from google.genai import types

from config import MODELS, GOOGLE_API_KEY, check_api_key
from prompt import MAX_TOOL_STEPS, Reply, build_system_prompt
from tools.registry import TOOL_SPECS, call_tool


def _gemini_schema(s: dict) -> dict:
    """JSON Schema chuẩn (type viết thường) -> dạng Gemini (type viết hoa)."""
    out = {k: v for k, v in s.items() if k not in ("type", "properties")}
    if "type" in s:
        out["type"] = s["type"].upper()
    if "properties" in s:
        out["properties"] = {k: _gemini_schema(v) for k, v in s["properties"].items()}
    return out


GEMINI_DECLARATIONS = [
    types.FunctionDeclaration(name=t["name"], description=t["description"],
                              parameters=_gemini_schema(t["parameters"]))
    for t in TOOL_SPECS
]


def _to_contents(turns):
    """[{'role','text'}] -> định dạng lịch sử của Gemini.

    Chỉ file này biết tới types.Content, nên conversations.py trả về dict thuần.
    """
    if not turns:
        return None
    return [types.Content(role=t["role"], parts=[types.Part(text=t["text"])])
            for t in turns]


class GeminiAgent:
    def __init__(self, ctx, history=None):
        """history: các lượt chữ đã lưu, dùng khi mở lại một hội thoại cũ."""
        check_api_key()
        self.ctx = ctx
        self.client = genai.Client(api_key=GOOGLE_API_KEY)
        self.model = MODELS[0]
        self._cfg = types.GenerateContentConfig(
            system_instruction=build_system_prompt(ctx),
            tools=[types.Tool(function_declarations=GEMINI_DECLARATIONS)],
            # Tự thực thi tool bằng tay, vì tool cần ctx — thứ model không được phép đặt.
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
        )
        self.chat = self.client.chats.create(
            model=self.model, config=self._cfg, history=_to_contents(history))

    def _switch_model(self) -> bool:
        """Cạn quota ngày của model hiện tại -> chuyển sang model kế tiếp, GIỮ nguyên hội thoại."""
        i = MODELS.index(self.model)
        if i + 1 >= len(MODELS):
            return False
        history = self.chat.get_history()
        self.model = MODELS[i + 1]
        self.chat = self.client.chats.create(model=self.model, config=self._cfg, history=history)
        return True

    def export_history(self) -> list[dict]:
        """Lịch sử dạng trung lập [{'role': 'user'|'model', 'text'}] để chuyển sang provider khác."""
        out = []
        for c in self.chat.get_history():
            text = "".join(p.text for p in (c.parts or []) if getattr(p, "text", None)).strip()
            if text:
                out.append({"role": "user" if c.role == "user" else "model", "text": text})
        return out

    def _prune_history(self):
        """Bỏ phần gọi tool khỏi lịch sử sau mỗi lượt, chỉ giữ lại chữ.

        Kết quả tool nằm trong ngữ cảnh dưới dạng dữ liệu vừa tra được, nên lượt
        sau model đọc thẳng con số cũ thay vì gọi lại tool: hỏi "hôm nay dạy mấy
        lớp?", được trả lời "không có lớp nào", tạo lớp mới rồi hỏi lại đúng câu
        đó vẫn ra "không có lớp nào".

        Dọn đi thì model không còn gì để đọc lại, buộc phải tra mới. Đây cũng là
        cách phiên được dựng lại từ Firestore vẫn hoạt động (xem conversations.py),
        nên hai đường đi cho ra hành vi giống nhau.
        """
        try:
            kept: list[types.Content] = []
            for c in self.chat.get_history():
                text = "".join(p.text for p in (c.parts or []) if getattr(p, "text", None))
                text = text.strip()
                if not text:
                    continue  # lượt chỉ có functionCall / functionResponse
                # Model có thể vừa nói vừa gọi tool -> gộp hai lượt cùng vai liền nhau
                if kept and kept[-1].role == c.role:
                    text = kept[-1].parts[0].text + "\n" + text
                    kept[-1] = types.Content(role=c.role, parts=[types.Part(text=text)])
                else:
                    kept.append(types.Content(role=c.role, parts=[types.Part(text=text)]))

            self.chat = self.client.chats.create(
                model=self.model, config=self._cfg, history=kept)
        except Exception:  # noqa: BLE001 — dọn hụt thì giữ nguyên, đừng làm hỏng cả lượt
            pass

    def _send(self, payload, on_wait=None, tries: int = 4):
        """Gửi 1 lượt, tự chờ và thử lại khi bị giới hạn tốc độ.

        Gói miễn phí của Gemini giới hạn 5 request/phút cho mỗi model. Một câu hỏi
        cần gọi tool sẽ tốn 2 request, nên rất dễ chạm trần khi hỏi liên tiếp.
        """
        for i in range(tries):
            try:
                return self.chat.send_message(payload)
            except Exception as e:  # noqa: BLE001
                msg = str(e)
                if ("RESOURCE_EXHAUSTED" not in msg and "429" not in msg) or i == tries - 1:
                    raise
                # Hết quota NGÀY -> chờ bao lâu cũng vô ích, phải đổi model.
                # Hết quota PHÚT -> chờ vài chục giây là chạy lại được.
                if "PerDay" in msg:
                    if not self._switch_model():
                        raise
                    if on_wait:
                        on_wait(0, f"đã cạn quota ngày, chuyển sang {self.model}")
                    continue
                m = re.search(r"retryDelay['\"]?:\s*['\"]?(\d+)", msg)
                delay = int(m.group(1)) + 1 if m else 15 * (i + 1)
                if on_wait:
                    on_wait(delay)
                time.sleep(delay)
        raise RuntimeError("không gửi được sau nhiều lần thử")

    def ask(self, text: str, on_tool=None, on_wait=None) -> Reply:
        resp = self._send(text, on_wait)
        used, tokens = [], 0

        for _ in range(MAX_TOOL_STEPS):
            tokens += getattr(resp.usage_metadata, "total_token_count", 0) or 0
            calls = [p.function_call for p in (resp.candidates[0].content.parts or []) if p.function_call]
            if not calls:
                break

            parts = []
            for fc in calls:
                args = dict(fc.args or {})
                if on_tool:
                    on_tool(fc.name, args)
                result = call_tool(self.ctx, fc.name, args)   # ctx do máy chủ cấp
                used.append((fc.name, args, result))
                parts.append(types.Part.from_function_response(
                    name=fc.name, response={"result": result}))

            resp = self._send(parts, on_wait)
        else:
            self._prune_history()
            return Reply("Xin lỗi, câu hỏi này cần quá nhiều bước tra cứu. "
                         "Bạn thử hỏi cụ thể hơn được không?", used, tokens)

        self._prune_history()
        return Reply((resp.text or "").strip() or "(không có nội dung trả lời)", used, tokens)

    # ─────────────────────── Streaming (SSE) ───────────────────────
    def _send_stream(self, payload, on_wait=None, tries: int = 4):
        """Như _send nhưng trả về iterator các mẩu trả lời.

        Việc thử lại chỉ an toàn TRƯỚC khi phát ra chữ đầu tiên. Nếu lỗi xảy ra
        giữa chừng thì không thể phát lại từ đầu, phải báo lỗi cho người dùng.
        """
        for i in range(tries):
            try:
                return self.chat.send_message_stream(payload)
            except Exception as e:  # noqa: BLE001
                msg = str(e)
                if ("RESOURCE_EXHAUSTED" not in msg and "429" not in msg) or i == tries - 1:
                    raise
                if "PerDay" in msg:
                    if not self._switch_model():
                        raise
                    if on_wait:
                        on_wait(0, f"đã cạn quota ngày, chuyển sang {self.model}")
                    continue
                m = re.search(r"retryDelay['\"]?:\s*['\"]?(\d+)", msg)
                delay = int(m.group(1)) + 1 if m else 15 * (i + 1)
                if on_wait:
                    on_wait(delay)
                time.sleep(delay)
        raise RuntimeError("không gửi được sau nhiều lần thử")

    def ask_stream(self, text: str):
        """Sinh ra từng sự kiện một, để server đẩy dần về client.

        Sự kiện:
          {"type":"tool", "name":..., "args":{...}}   đang tra cứu dữ liệu
          {"type":"text", "delta":"..."}              một mẩu câu trả lời
          {"type":"done", "tokens":N}                 kết thúc
          {"type":"error","message":"..."}            lỗi
        """
        payload = text
        tokens = 0

        for _ in range(MAX_TOOL_STEPS):
            calls = []
            try:
                stream = self._send_stream(payload)
                for chunk in stream:
                    tokens += getattr(chunk.usage_metadata, "total_token_count", 0) or 0
                    cands = chunk.candidates or []
                    if not cands or not cands[0].content:
                        continue
                    for p in (cands[0].content.parts or []):
                        if p.function_call:
                            calls.append(p.function_call)
                        elif p.text:
                            yield {"type": "text", "delta": p.text}
            except Exception as e:  # noqa: BLE001
                m = str(e)
                if "RESOURCE_EXHAUSTED" in m or "429" in m:
                    yield {"type": "error",
                           "message": "Đã hết lượt dùng của gói miễn phí. Bạn thử lại sau nhé."}
                else:
                    yield {"type": "error", "message": f"Lỗi xử lý: {type(e).__name__}"}
                # Lỗi giữa vòng gọi tool để lại lịch sử dang dở -> dọn luôn
                self._prune_history()
                return

            if not calls:
                self._prune_history()
                yield {"type": "done", "tokens": tokens}
                return

            parts = []
            for fc in calls:
                args = dict(fc.args or {})
                yield {"type": "tool", "name": fc.name, "args": args}
                result = call_tool(self.ctx, fc.name, args)   # ctx do máy chủ cấp
                parts.append(types.Part.from_function_response(
                    name=fc.name, response={"result": result}))
            payload = parts

        self._prune_history()
        yield {"type": "text", "delta": "\n\nCâu hỏi này cần quá nhiều bước tra cứu, "
                                        "bạn thử hỏi cụ thể hơn nhé."}
        yield {"type": "done", "tokens": tokens}
