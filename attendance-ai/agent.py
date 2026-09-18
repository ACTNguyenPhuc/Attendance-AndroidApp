"""Vòng lặp hội thoại + gọi tool.

ĐÂY LÀ FILE DUY NHẤT phụ thuộc vào Gemini. Đổi sang Claude/GPT chỉ cần sửa file này
và phần GEMINI_DECLARATIONS trong tools/registry.py — tầng tools giữ nguyên.

Agent KHÔNG biết gì về HTTP: nó nhận chuỗi, trả về object. Nhờ vậy dùng được
cho cả CLI lẫn server web sau này.
"""
import re
import time
from dataclasses import dataclass, field

from google import genai
from google.genai import types

from config import MODELS, GOOGLE_API_KEY, check_api_key, now_vn, today_str
from tools.registry import GEMINI_DECLARATIONS, call_tool

MAX_TOOL_STEPS = 6  # chặn vòng lặp vô hạn nếu model cứ gọi tool mãi

_THU_VN = {0: "Thứ Hai", 1: "Thứ Ba", 2: "Thứ Tư", 3: "Thứ Năm",
           4: "Thứ Sáu", 5: "Thứ Bảy", 6: "Chủ Nhật"}


def build_system_prompt(ctx) -> str:
    now = now_vn()
    vai_tro = "giảng viên" if ctx.is_teacher else "sinh viên"
    return f"""Bạn là trợ lý AI của ứng dụng điểm danh AttendanceApp.

NGƯỜI ĐANG HỎI
- Tên: {ctx.name}
- Vai trò: {vai_tro}
- Mã: {ctx.student_code}

HÔM NAY: {today_str()} ({_THU_VN[now.weekday()]}), giờ Việt Nam.

QUY TẮC BẮT BUỘC
1. Mọi con số phải lấy từ tool. Tuyệt đối không tự suy đoán hay bịa số liệu.
2. Nếu chưa biết mã lớp, gọi get_my_classes trước.
3. Khi gọi tool, ngày luôn ở dạng yyyy-MM-dd.
4. Nếu tool trả về trường "ghiChu" hoặc "error", hãy nói lại đúng ý đó cho người dùng,
   đừng lấp liếm bằng thông tin tự nghĩ ra.
5. Không hiển thị uid, mã phiên hay tên trường kỹ thuật. Người dùng chỉ cần thấy
   tên lớp, ngày giờ, phòng, con số.
6. Bạn chỉ có quyền ĐỌC. Nếu người dùng yêu cầu tạo lớp, mở điểm danh hay sửa dữ liệu,
   hãy nói rõ là bạn chưa làm được và hướng dẫn họ thao tác trong ứng dụng.

CÁCH TRÌNH BÀY — câu trả lời hiện trong bong bóng chat hẹp trên điện thoại
7. Câu đầu tiên phải trả lời thẳng câu hỏi. Không mở bài, không nhắc lại câu hỏi.
8. Ngắn gọn: tối đa 4 dòng nếu không phải liệt kê.
9. Khi liệt kê, mỗi mục một dòng bắt đầu bằng "- ". Tối đa 6 mục; nhiều hơn thì
   nêu vài mục đáng chú ý rồi nói tổng số.
10. Chỉ dùng **in đậm** cho con số hoặc tên lớp quan trọng. Mỗi câu nhiều nhất một lần.
11. TUYỆT ĐỐI KHÔNG dùng: tiêu đề (#), bảng (|), khối mã (```), chữ nghiêng, liên kết.
    Giao diện chat không hiển thị được những thứ đó.
12. Ngày viết dạng dd/MM, giờ dạng HH:mm. Ví dụ: "T4 16/09, 15:05-17:30, phòng 102-TA1".
13. Đừng lặp lại toàn bộ dữ liệu tool trả về — chỉ nêu phần người dùng hỏi.
"""


@dataclass
class Reply:
    text: str
    tool_calls: list = field(default_factory=list)   # [(tên, tham số, kết quả)]
    tokens: int = 0


class Agent:
    def __init__(self, ctx):
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
        self.chat = self.client.chats.create(model=self.model, config=self._cfg)

    def _switch_model(self) -> bool:
        """Cạn quota ngày của model hiện tại -> chuyển sang model kế tiếp, GIỮ nguyên hội thoại."""
        i = MODELS.index(self.model)
        if i + 1 >= len(MODELS):
            return False
        history = self.chat.get_history()
        self.model = MODELS[i + 1]
        self.chat = self.client.chats.create(model=self.model, config=self._cfg, history=history)
        return True

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
            return Reply("Xin lỗi, câu hỏi này cần quá nhiều bước tra cứu. "
                         "Bạn thử hỏi cụ thể hơn được không?", used, tokens)

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
                return

            if not calls:
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

        yield {"type": "text", "delta": "\n\nCâu hỏi này cần quá nhiều bước tra cứu, "
                                        "bạn thử hỏi cụ thể hơn nhé."}
        yield {"type": "done", "tokens": tokens}
