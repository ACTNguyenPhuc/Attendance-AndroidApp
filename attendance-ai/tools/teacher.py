"""Tool dành riêng cho giảng viên.

Mọi hàm ở đây đều chặn sinh viên ngay dòng đầu — đây là ranh giới phân quyền
theo VAI TRÒ, bổ sung cho ranh giới theo LỚP ở _common.authorize().
"""
from config import to_vn_time, today_str

from ._common import (
    authorize,
    class_attendances,
    class_roster,
    held_shifts,
    load_classes,
    load_shifts,
    load_users,
    my_class_ids,
)

_CHI_GIANG_VIEN = {
    "error": "chi_danh_cho_giang_vien",
    "message": "Chức năng này chỉ dành cho giảng viên.",
}


def _pct(part, whole):
    return round(part * 100.0 / whole, 1) if whole else 0.0


def get_students_at_risk(ctx, class_id: str = "", threshold_percent: float = 20.0) -> dict:
    """Danh sách sinh viên có tỷ lệ vắng vượt ngưỡng."""
    if not ctx.is_teacher:
        return _CHI_GIANG_VIEN

    ids = my_class_ids(ctx)
    if class_id:
        err = authorize(ctx, class_id)
        if err:
            return err
        ids = [class_id]
    if not ids:
        return {"danhSach": [], "ghiChu": "Bạn chưa dạy lớp nào."}

    today = today_str()
    shifts = load_shifts(ids)
    info = load_classes(ids)
    out = []

    for cid in ids:
        held = held_shifts(shifts, cid, today)
        held_ids = {s["maCa"] for s in held}
        if not held_ids:
            continue

        roster = class_roster(cid)
        names = load_users(roster)
        # uid -> tập ca đã có mặt
        co_mat = {}
        for a in class_attendances(cid):
            if a.get("shiftId") in held_ids and a.get("studentId"):
                co_mat.setdefault(a["studentId"], set()).add(a["shiftId"])

        for uid in roster:
            vang = len(held_ids) - len(co_mat.get(uid, set()))
            ty_le = _pct(vang, len(held_ids))
            if ty_le >= threshold_percent:
                out.append({
                    "maSV": names.get(uid, {}).get("maSV", ""),
                    "hoTen": names.get(uid, {}).get("ten", ""),
                    "maLop": cid,
                    "tenLop": info.get(cid, {}).get("tenLop", ""),
                    "soBuoiDaDiemDanh": len(held_ids),
                    "soBuoiVang": vang,
                    "tyLeVangPhanTram": ty_le,
                })

    out.sort(key=lambda r: (-r["tyLeVangPhanTram"], -r["soBuoiVang"]))
    return {
        "nguong": threshold_percent,
        "soSinhVienVuotNguong": len(out),
        "danhSach": out,
        "ghiChu": None if out else f"Không có sinh viên nào vắng từ {threshold_percent}% trở lên.",
    }


def get_shift_attendance(ctx, class_id: str, date: str) -> dict:
    """Ai có mặt, ai vắng trong một buổi học cụ thể."""
    if not ctx.is_teacher:
        return _CHI_GIANG_VIEN
    err = authorize(ctx, class_id)
    if err:
        return err

    shifts = [s for s in load_shifts([class_id]) if s["ngay"] == date]
    if not shifts:
        có = sorted({s["ngay"] for s in load_shifts([class_id])})
        return {
            "error": "khong_co_buoi_hoc",
            "message": f"Lớp {class_id} không có buổi nào ngày {date}.",
            "cacNgayCoBuoiHoc": có[-10:],
        }

    shift = shifts[0]
    if not shift["_daMoDiemDanh"]:
        return {
            "maLop": class_id, "ngay": date,
            "ghiChu": "Buổi này chưa từng mở điểm danh nên không có dữ liệu.",
        }

    roster = class_roster(class_id)
    names = load_users(roster)
    recs = {a["studentId"]: a for a in class_attendances(class_id)
            if a.get("shiftId") == shift["maCa"] and a.get("studentId")}

    co_mat, vang = [], []
    for uid in roster:
        who = {"maSV": names.get(uid, {}).get("maSV", ""), "hoTen": names.get(uid, {}).get("ten", "")}
        r = recs.get(uid)
        if r:
            co_mat.append({**who,
                           "trangThai": "đi muộn" if r.get("status") == "late" else "đúng giờ",
                           "gioDiemDanh": to_vn_time(r.get("checkinTime")),
                           "khoangCachMet": round(r.get("distance") or 0)})
        else:
            vang.append(who)

    co_mat.sort(key=lambda x: x["gioDiemDanh"])
    return {
        "maLop": class_id,
        "tenLop": shift["tenLop"],
        "ngay": date,
        "thu": shift["thu"],
        "gio": f"{shift['batDau']}-{shift['ketThuc']}",
        "phong": shift["phong"],
        "hocBu": shift["hocBu"],
        "siSo": len(roster),
        "soCoMat": len(co_mat),
        "soVang": len(vang),
        "tyLeCoMatPhanTram": _pct(len(co_mat), len(roster)),
        "danhSachCoMat": co_mat,
        "danhSachVang": vang,
    }
