"""Tool: lịch sử điểm danh chi tiết của sinh viên (từng buổi một)."""
from config import to_vn_time, today_str

from ._common import (
    authorize,
    held_shifts,
    load_classes,
    load_shifts,
    my_attendances,
    my_class_ids,
)


def get_attendance_history(ctx, class_id: str = "", limit: int = 20) -> dict:
    """Từng buổi đã diễn ra: có mặt lúc mấy giờ, đúng giờ hay muộn, hay vắng."""
    if ctx.is_teacher:
        return {
            "error": "danh_cho_sinh_vien",
            "message": "Tool này xem lịch sử của chính sinh viên. "
                       "Giảng viên hãy dùng get_shift_attendance để xem một buổi cụ thể.",
        }

    ids = my_class_ids(ctx)
    if class_id:
        err = authorize(ctx, class_id)
        if err:
            return err
        ids = [class_id]
    if not ids:
        return {"danhSach": [], "ghiChu": "Bạn chưa tham gia lớp nào."}

    today = today_str()
    shifts = load_shifts(ids)
    info = load_classes(ids)
    atts = {a["maCa"]: a for a in my_attendances(ctx, ids)}

    rows = []
    for cid in ids:
        for s in held_shifts(shifts, cid, today):
            a = atts.get(s["maCa"])
            rows.append({
                "ngay": s["ngay"],
                "thu": s["thu"],
                "gioHoc": f"{s['batDau']}-{s['ketThuc']}",
                "maLop": cid,
                "tenLop": info.get(cid, {}).get("tenLop", ""),
                "phong": s["phong"],
                "hocBu": s["hocBu"],
                "ketQua": ("đi muộn" if a["trangThai"] == "late" else "đúng giờ") if a else "VẮNG",
                "gioDiemDanh": to_vn_time(a["thoiDiem"]) if a else None,
                "khoangCachMet": round(a["khoangCach"] or 0) if a else None,
            })

    rows.sort(key=lambda r: (r["ngay"], r["gioHoc"]), reverse=True)
    limit = max(1, min(int(limit or 20), 60))
    shown = rows[:limit]
    return {
        "tongSoBuoi": len(rows),
        "soBuoiHienThi": len(shown),
        "soBuoiVang": sum(1 for r in rows if r["ketQua"] == "VẮNG"),
        "danhSach": shown,
        "ghiChu": (f"Chỉ hiện {len(shown)} buổi gần nhất trong tổng {len(rows)} buổi."
                   if len(shown) < len(rows) else None),
    }
