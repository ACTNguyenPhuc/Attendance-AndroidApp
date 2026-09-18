"""Truy vấn dùng chung + xử lý các bẫy trong dữ liệu thật.

Bốn bẫy đã kiểm chứng bằng diagnostics/probe_schema_traps.py:
  1. classes.studentCount = 0 ở CẢ 6 lớp     -> phải đếm enrollments
  2. shifts.teacherName chứa UID, shifts.teacher mới là tên
     (riêng classes.teacherName thì đúng là tên — khác nhau giữa 2 collection)
  3. 75/94 ca đã qua nhưng status vẫn 'upcoming' -> so date, không tin status
  4. startAt/endAt là chuỗi giờ VN; checkinTime/scheduledEndTime là UTC -> quy đổi +7
Thêm: KHÔNG có bản ghi status='absent' -> số buổi vắng phải suy ra.
"""
from google.cloud.firestore_v1.base_query import FieldFilter

from config import get_db, today_str


def _chunks(seq, n=30):
    """Firestore giới hạn số phần tử trong truy vấn 'in'."""
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


def my_class_ids(ctx) -> list[str]:
    """Danh sách lớp người dùng được phép xem — ĐÂY LÀ RANH GIỚI PHÂN QUYỀN.

    Sinh viên: các lớp đã tham gia. Giảng viên: các lớp mình dạy.
    Mọi tool đều phải đi qua đây, không bao giờ tin class_id do model đưa ra.
    """
    db = get_db()
    if ctx.is_teacher:
        q = db.collection("classes").where(filter=FieldFilter("teacherId", "==", ctx.uid))
        return sorted(d.id for d in q.stream())
    q = db.collection("enrollments").where(filter=FieldFilter("studentId", "==", ctx.uid))
    return sorted(
        (d.to_dict() or {}).get("classId")
        for d in q.stream()
        if (d.to_dict() or {}).get("status", "active") == "active"
        and (d.to_dict() or {}).get("classId")
    )


def authorize(ctx, class_id: str):
    """Chặn truy cập lớp không thuộc quyền. Trả None nếu hợp lệ, hoặc dict lỗi."""
    allowed = my_class_ids(ctx)
    if class_id not in allowed:
        return {
            "error": "khong_co_quyen",
            "message": f"Bạn không có quyền xem lớp {class_id}.",
            "cac_lop_duoc_phep": allowed,
        }
    return None


def load_classes(class_ids: list[str]) -> dict:
    """classId -> dict thông tin lớp, kèm soSinhVien đếm THẬT từ enrollments."""
    if not class_ids:
        return {}
    db = get_db()
    out = {}
    for doc in (db.collection("classes").document(cid).get() for cid in class_ids):
        if not doc.exists:
            continue
        d = doc.to_dict() or {}
        out[doc.id] = {
            "maLop": doc.id,
            "tenLop": d.get("className") or "",
            "phong": d.get("room") or "",
            # classes.teacherName ĐÚNG là tên (khác với shifts — xem bẫy #2)
            "giangVien": d.get("teacherName") or "",
            "lichHoc": d.get("scheduleTimeDisplay") or d.get("scheduleDisplay") or "",
            "batDau": d.get("startDate") or "",
            "ketThuc": d.get("endDate") or "",
            # BẪY #1: KHÔNG dùng d["studentCount"] — bằng 0 ở mọi lớp
            "soSinhVien": count_students(doc.id),
        }
    return out


def count_students(class_id: str) -> int:
    db = get_db()
    q = db.collection("enrollments").where(filter=FieldFilter("classId", "==", class_id))
    return sum(1 for d in q.stream() if (d.to_dict() or {}).get("status", "active") == "active")


def load_shifts(class_ids: list[str]) -> list[dict]:
    """Toàn bộ ca học của các lớp, đã chuẩn hoá. Sắp theo ngày rồi giờ."""
    if not class_ids:
        return []
    db = get_db()
    rows = []
    for chunk in _chunks(class_ids):
        q = db.collection("shifts").where(filter=FieldFilter("classId", "in", chunk))
        for doc in q.stream():
            d = doc.to_dict() or {}
            rows.append({
                "maCa": doc.id,
                "maLop": d.get("classId") or "",
                "tenLop": d.get("className") or "",
                "ngay": d.get("date") or "",
                "thu": d.get("dayOfWeekDisplay") or "",
                "batDau": d.get("startAt") or "",   # đã là giờ VN dạng chuỗi
                "ketThuc": d.get("endAt") or "",
                "phong": d.get("room") or "",
                # BẪY #2: dùng 'teacher' (tên), KHÔNG dùng 'teacherName' (là UID)
                "giangVien": d.get("teacher") or "",
                "tieuDe": d.get("title") or "",
                "noiDung": d.get("content"),
                "hocBu": bool(d.get("makeup")),
                "_status_goc": d.get("status") or "",
                "_daMoDiemDanh": bool(d.get("attendanceSessionId")),
                "_dangMo": bool(d.get("attendanceOpened")),
            })
    rows.sort(key=lambda r: (r["ngay"], r["batDau"]))
    return rows


def effective_status(shift: dict, today: str | None = None) -> str:
    """BẪY #3: shifts.status không tự cập nhật (75/94 ca đã qua vẫn 'upcoming').

    Trạng thái thật phải suy từ ngày + việc đã mở điểm danh hay chưa.
    """
    today = today or today_str()
    # Ngày xét TRƯỚC cờ attendanceOpened: có những ca đã qua nhưng giảng viên
    # quên đóng phiên, cờ vẫn còn true. Nói "đang mở" cho ca hôm qua là sai.
    if shift["ngay"] > today:
        return "sắp tới"
    if shift["ngay"] == today:
        return "đang mở điểm danh" if shift["_dangMo"] else "hôm nay"
    if shift["_dangMo"]:
        return "đã học (phiên điểm danh chưa được đóng)"
    return "đã học" if shift["_daMoDiemDanh"] else "đã qua (không điểm danh)"


def held_shifts(shifts: list[dict], class_id: str, today: str | None = None) -> list[dict]:
    """Các buổi THỰC SỰ đã diễn ra và có mở điểm danh — mẫu số để tính tỷ lệ vắng."""
    today = today or today_str()
    return [
        s for s in shifts
        if s["maLop"] == class_id and s["ngay"] <= today and s["_daMoDiemDanh"]
    ]


def my_attendances(ctx, class_ids: list[str] | None = None) -> list[dict]:
    """Các lượt điểm danh của chính người dùng (chỉ dùng cho sinh viên)."""
    db = get_db()
    q = db.collection("attendances").where(filter=FieldFilter("studentId", "==", ctx.uid))
    rows = []
    for doc in q.stream():
        d = doc.to_dict() or {}
        if class_ids and d.get("classId") not in class_ids:
            continue
        rows.append({
            "maLop": d.get("classId") or "",
            "maCa": d.get("shiftId") or "",
            "trangThai": d.get("status") or "",   # chỉ có 'present' | 'late'
            "thoiDiem": d.get("checkinTime"),     # UTC — quy đổi khi hiển thị
            "khoangCach": d.get("distance"),
        })
    return rows


def load_users(uids: list[str]) -> dict:
    """uid -> {ten, maSV}. Nạp một lần cho cả danh sách thay vì gọi lẻ từng người."""
    if not uids:
        return {}
    db = get_db()
    refs = [db.collection("users").document(u) for u in set(uids)]
    out = {}
    for doc in db.get_all(refs):
        if doc.exists:
            d = doc.to_dict() or {}
            out[doc.id] = {"ten": d.get("name") or "", "maSV": d.get("studentCode") or ""}
    return out


def class_roster(class_id: str) -> list[str]:
    """uid các sinh viên đang học lớp này."""
    db = get_db()
    q = db.collection("enrollments").where(filter=FieldFilter("classId", "==", class_id))
    return sorted(
        (d.to_dict() or {}).get("studentId")
        for d in q.stream()
        if (d.to_dict() or {}).get("status", "active") == "active"
        and (d.to_dict() or {}).get("studentId")
    )


def class_attendances(class_id: str) -> list[dict]:
    """Mọi lượt điểm danh của một lớp (dùng cho giảng viên)."""
    db = get_db()
    q = get_db().collection("attendances").where(filter=FieldFilter("classId", "==", class_id))
    return [d.to_dict() or {} for d in q.stream()]
