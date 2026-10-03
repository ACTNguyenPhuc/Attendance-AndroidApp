"""Tool: thống kê điểm danh / vắng mặt.

KHÔNG có bản ghi status='absent' trong Firestore. Số buổi vắng suy ra bằng:
    vắng = (số buổi đã mở điểm danh và đã qua) − (số buổi mình có mặt)
"""
from config import today_str

from ._common import (
    authorize,
    held_shifts,
    load_classes,
    load_shifts,
    my_attendances,
    my_class_ids,
)


def _pct(part: int, whole: int) -> float:
    return round(part * 100.0 / whole, 1) if whole else 0.0


def _top(rows: list[dict], key: str, highest: bool) -> list[dict]:
    """Các lớp đứng đầu theo một chỉ số (nhiều lớp nếu bằng nhau).

    Kết luận sẵn ở tầng tool để model khỏi tự so sánh: model nhỏ đọc danh sách dài
    hay chọn nhầm dòng, hoặc lẫn "nhiều buổi nhất" với "tỷ lệ cao nhất".
    Bỏ qua lớp chưa có buổi nào — tỷ lệ 0% ở đó là "chưa có dữ liệu", không phải thấp nhất.
    """
    rows = [r for r in rows if r["soBuoiDaDiemDanh"] > 0]
    if not rows:
        return []
    best = (max if highest else min)(r[key] for r in rows)
    return [{"maLop": r["maLop"], "tenLop": r["tenLop"], key: r[key]}
            for r in rows if r[key] == best]


def get_my_attendance_summary(ctx, class_id: str = "") -> dict:
    ids = my_class_ids(ctx)
    if class_id:
        err = authorize(ctx, class_id)
        if err:
            return err
        ids = [class_id]
    if not ids:
        return {"soLop": 0, "danhSach": [], "ghiChu": "Không có lớp nào để thống kê."}

    today = today_str()
    shifts = load_shifts(ids)
    info = load_classes(ids)

    if ctx.is_teacher:
        return _teacher_view(ids, shifts, info, today)
    return _student_view(ctx, ids, shifts, info, today)


def _student_view(ctx, ids, shifts, info, today) -> dict:
    atts = my_attendances(ctx, ids)
    rows = []
    tong_hoc, tong_vang = 0, 0

    for cid in ids:
        held = held_shifts(shifts, cid, today)
        held_ids = {s["maCa"] for s in held}
        mine = [a for a in atts if a["maLop"] == cid and a["maCa"] in held_ids]
        co_mat_ids = {a["maCa"] for a in mine}

        dung_gio = sum(1 for a in mine if a["trangThai"] == "present")
        di_muon = sum(1 for a in mine if a["trangThai"] == "late")
        vang = len(held_ids - co_mat_ids)

        tong_hoc += len(held_ids)
        tong_vang += vang

        rows.append({
            "maLop": cid,
            "tenLop": info.get(cid, {}).get("tenLop", ""),
            "soBuoiDaDiemDanh": len(held_ids),
            "coMat": len(co_mat_ids),
            "dungGio": dung_gio,
            "diMuon": di_muon,
            "vang": vang,
            "tyLeVangPhanTram": _pct(vang, len(held_ids)),
            "tyLeChuyenCanPhanTram": _pct(len(co_mat_ids), len(held_ids)),
        })

    rows.sort(key=lambda r: (-r["vang"], -r["tyLeVangPhanTram"]))
    return {
        "vaiTro": "sinh viên",
        "tinhDenNgay": today,
        "tongSoBuoiDaDiemDanh": tong_hoc,
        "tongSoBuoiVang": tong_vang,
        "tyLeVangChungPhanTram": _pct(tong_vang, tong_hoc),
        "lopVangNhieuBuoiNhat": _top(rows, "vang", highest=True) if tong_vang else [],
        "lopTyLeVangCaoNhat": _top(rows, "tyLeVangPhanTram", highest=True) if tong_vang else [],
        "danhSach": rows,  # xếp theo số buổi vắng giảm dần
        "giaiThich": "Buổi vắng = buổi đã mở điểm danh và đã qua, nhưng không có bản ghi của bạn.",
    }


def _teacher_view(ids, shifts, info, today) -> dict:
    from google.cloud.firestore_v1.base_query import FieldFilter

    from config import get_db

    db = get_db()
    rows = []
    for cid in ids:
        held = held_shifts(shifts, cid, today)
        held_ids = {s["maCa"] for s in held}
        si_so = info.get(cid, {}).get("soSinhVien", 0)

        q = db.collection("attendances").where(filter=FieldFilter("classId", "==", cid))
        recs = [d.to_dict() or {} for d in q.stream()]
        recs = [r for r in recs if r.get("shiftId") in held_ids]

        luot_toi_da = len(held_ids) * si_so
        rows.append({
            "maLop": cid,
            "tenLop": info.get(cid, {}).get("tenLop", ""),
            "siSo": si_so,
            "soBuoiDaDiemDanh": len(held_ids),
            "tongLuotCoMat": len(recs),
            "dungGio": sum(1 for r in recs if r.get("status") == "present"),
            "diMuon": sum(1 for r in recs if r.get("status") == "late"),
            "tongLuotVang": max(luot_toi_da - len(recs), 0),
            "tyLeChuyenCanPhanTram": _pct(len(recs), luot_toi_da),
        })

    # Lớp chưa điểm danh buổi nào xuống cuối, đừng để 0% của nó đứng đầu danh sách
    rows.sort(key=lambda r: (r["soBuoiDaDiemDanh"] == 0, r["tyLeChuyenCanPhanTram"]))
    return {
        "vaiTro": "giảng viên",
        "tinhDenNgay": today,
        "soLop": len(rows),
        "lopChuyenCanThapNhat": _top(rows, "tyLeChuyenCanPhanTram", highest=False),
        "lopChuyenCanCaoNhat": _top(rows, "tyLeChuyenCanPhanTram", highest=True),
        "danhSach": rows,  # xếp theo tỷ lệ chuyên cần tăng dần
        "giaiThich": "Lượt vắng = (số buổi đã điểm danh × sĩ số) − tổng lượt có mặt.",
    }
