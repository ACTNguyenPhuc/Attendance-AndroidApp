"""Khai báo tool + bảng tra tên -> hàm Python.

TOOL_SPECS viết bằng JSON Schema chuẩn, không phụ thuộc nhà cung cấp nào.
Mỗi agent tự đổi sang định dạng của mình (agent_ollama.py dùng thẳng,
agent_gemini.py đổi type sang chữ hoa). Logic trong các file tool giữ nguyên.
"""
from .attendance import get_my_attendance_summary
from .classes import get_my_classes
from .history import get_attendance_history
from .schedule import get_my_schedule
from .teacher import get_shift_attendance, get_students_at_risk

# tên tool -> hàm thật. Mọi hàm đều nhận ctx làm tham số đầu tiên.
REGISTRY = {
    "get_my_classes": get_my_classes,
    "get_my_schedule": get_my_schedule,
    "get_my_attendance_summary": get_my_attendance_summary,
    "get_attendance_history": get_attendance_history,
    "get_students_at_risk": get_students_at_risk,
    "get_shift_attendance": get_shift_attendance,
}

TOOL_SPECS = [
    dict(
        name="get_my_classes",
        description=(
            "Liệt kê các lớp của người dùng hiện tại. Sinh viên thì ra các lớp đã tham gia, "
            "giảng viên thì ra các lớp mình dạy. Kèm mã lớp, tên lớp, phòng, lịch học, sĩ số. "
            "Gọi tool này trước khi cần mã lớp cho các tool khác."
        ),
        parameters={"type": "object", "properties": {}},
    ),
    dict(
        name="get_my_schedule",
        description=(
            "Lịch học của người dùng trong một khoảng ngày. Dùng cho câu hỏi kiểu "
            "'tuần này học gì', 'mai có tiết không', 'tháng 6 có bao nhiêu buổi'."
        ),
        parameters={
            "type": "object",
            "properties": {
                "from_date": {"type": "string", "description": "Ngày bắt đầu, dạng yyyy-MM-dd. Bỏ trống = hôm nay."},
                "to_date": {"type": "string", "description": "Ngày kết thúc, dạng yyyy-MM-dd. Bỏ trống = 7 ngày sau from_date."},
                "class_id": {"type": "string", "description": "Lọc theo một mã lớp. Bỏ trống = tất cả các lớp."},
            },
        },
    ),
    dict(
        name="get_my_attendance_summary",
        description=(
            "Thống kê điểm danh. Với sinh viên: số buổi có mặt / đi muộn / vắng và tỷ lệ vắng "
            "từng lớp. Với giảng viên: sĩ số, số buổi đã điểm danh và tỷ lệ chuyên cần từng lớp. "
            "Dùng cho câu hỏi kiểu 'tôi vắng mấy buổi', 'lớp nào tôi vắng nhiều nhất', "
            "'tỷ lệ chuyên cần thế nào'."
        ),
        parameters={
            "type": "object",
            "properties": {
                "class_id": {"type": "string", "description": "Chỉ thống kê một lớp. Bỏ trống = tất cả các lớp."},
            },
        },
    ),
    dict(
        name="get_attendance_history",
        description=(
            "CHỈ CHO SINH VIÊN. Lịch sử điểm danh chi tiết từng buổi: ngày, giờ học, "
            "có mặt lúc mấy giờ, đúng giờ hay đi muộn, hay vắng. Dùng cho câu hỏi kiểu "
            "'tôi vắng những buổi nào', 'hôm nào tôi đi muộn', 'lần điểm danh gần nhất'. "
            "Khác get_my_attendance_summary ở chỗ tool kia chỉ ra con số tổng."
        ),
        parameters={
            "type": "object",
            "properties": {
                "class_id": {"type": "string", "description": "Lọc theo một mã lớp. Bỏ trống = tất cả."},
                "limit": {"type": "integer", "description": "Số buổi gần nhất cần xem, mặc định 20."},
            },
        },
    ),
    dict(
        name="get_students_at_risk",
        description=(
            "CHỈ CHO GIẢNG VIÊN. Danh sách sinh viên có tỷ lệ vắng vượt ngưỡng, kèm họ tên, "
            "mã sinh viên, số buổi vắng. Dùng cho câu hỏi kiểu 'sinh viên nào vắng nhiều', "
            "'ai vắng quá 20%', 'em nào có nguy cơ bị cấm thi'."
        ),
        parameters={
            "type": "object",
            "properties": {
                "class_id": {"type": "string", "description": "Một mã lớp. Bỏ trống = mọi lớp mình dạy."},
                "threshold_percent": {"type": "number", "description": "Ngưỡng tỷ lệ vắng tính theo %, mặc định 20."},
            },
        },
    ),
    dict(
        name="get_shift_attendance",
        description=(
            "CHỈ CHO GIẢNG VIÊN. Chi tiết một buổi học cụ thể: ai có mặt lúc mấy giờ, ai đi muộn, "
            "ai vắng. Dùng cho câu hỏi kiểu 'buổi ngày 16/09 lớp LTACB_L01 ai vắng', "
            "'hôm đó có bao nhiêu em đi muộn'."
        ),
        parameters={
            "type": "object",
            "properties": {
                "class_id": {"type": "string", "description": "Mã lớp, bắt buộc."},
                "date": {"type": "string", "description": "Ngày học dạng yyyy-MM-dd, bắt buộc."},
            },
            "required": ["class_id", "date"],
        },
    ),
]


_STUDENT_ONLY = {"get_attendance_history"}
_TEACHER_ONLY = {"get_students_at_risk", "get_shift_attendance"}


def specs_for(ctx) -> list[dict]:
    """Chỉ những tool người đang hỏi được dùng.

    Model nhỏ dễ rối khi thấy tool "CHỈ CHO GIẢNG VIÊN" trong danh sách (có lần tự
    kết luận giảng viên KHÔNG được dùng). Bớt tool thừa thì chọn đúng hơn.
    Đây không phải lớp bảo mật — từng tool vẫn tự kiểm tra quyền theo ctx.
    """
    hidden = _STUDENT_ONLY if ctx.is_teacher else _TEACHER_ONLY
    return [t for t in TOOL_SPECS if t["name"] not in hidden]


def call_tool(ctx, name: str, args: dict):
    """Thực thi tool. ctx do MÁY CHỦ cấp, không phải do model sinh ra — đây là ranh giới an toàn."""
    fn = REGISTRY.get(name)
    if fn is None:
        return {"error": "tool_khong_ton_tai", "message": f"Không có tool tên {name}."}
    try:
        return fn(ctx, **(args or {}))
    except TypeError as e:
        return {"error": "tham_so_sai", "message": str(e)}
    except Exception as e:  # noqa: BLE001 - trả lỗi về cho model thay vì làm sập chat
        return {"error": "loi_thuc_thi", "message": f"{type(e).__name__}: {e}"}
