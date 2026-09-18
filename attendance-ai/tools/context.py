"""Ctx — "ai đang hỏi".

QUY TẮC QUAN TRỌNG: mọi tool nhận ctx làm tham số ĐẦU TIÊN.
- Chạy CLI      : ctx dựng từ --uid trên dòng lệnh
- Chạy trong app: ctx dựng từ uid Firebase đã xác thực
Nhờ vậy tầng tools không cần sửa một dòng nào khi tích hợp vào Android.
"""
from dataclasses import dataclass

from config import get_db


@dataclass(frozen=True)
class Ctx:
    uid: str
    role: str          # 'student' | 'teacher'
    name: str
    student_code: str

    @property
    def is_teacher(self) -> bool:
        return self.role == "teacher"

    @property
    def is_student(self) -> bool:
        return self.role == "student"


def load_ctx(uid: str) -> Ctx:
    """Đọc hồ sơ người dùng từ Firestore. Ném lỗi nếu uid không tồn tại."""
    doc = get_db().collection("users").document(uid).get()
    if not doc.exists:
        raise SystemExit(f"\n❌ Không có người dùng nào với uid = {uid}\n")
    d = doc.to_dict() or {}
    return Ctx(
        uid=uid,
        role=d.get("role") or "student",
        name=d.get("name") or "(chưa có tên)",
        student_code=d.get("studentCode") or "",
    )
