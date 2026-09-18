"""Tool: xem lịch học trong một khoảng ngày."""
from datetime import timedelta

from config import now_vn, today_str

from ._common import authorize, effective_status, load_shifts, my_class_ids


def get_my_schedule(ctx, from_date: str = "", to_date: str = "", class_id: str = "") -> dict:
    """Lịch học từ from_date đến to_date (yyyy-MM-dd). Bỏ trống = 7 ngày tới."""
    today = today_str()
    if not from_date:
        from_date = today
    if not to_date:
        to_date = (now_vn() + timedelta(days=7)).strftime("%Y-%m-%d")
    if from_date > to_date:
        from_date, to_date = to_date, from_date

    ids = my_class_ids(ctx)
    if class_id:
        err = authorize(ctx, class_id)
        if err:
            return err
        ids = [class_id]

    rows = [s for s in load_shifts(ids) if from_date <= s["ngay"] <= to_date]
    out = []
    for s in rows:
        out.append({
            "ngay": s["ngay"],
            "thu": s["thu"],
            "gio": f"{s['batDau']}-{s['ketThuc']}",
            "maLop": s["maLop"],
            "tenLop": s["tenLop"],
            "phong": s["phong"],
            "giangVien": s["giangVien"],
            "trangThai": effective_status(s, today),
            "hocBu": s["hocBu"],
            "noiDung": s["noiDung"],
        })

    res = {"tuNgay": from_date, "denNgay": to_date, "homNay": today, "soBuoi": len(out), "danhSach": out}
    if not out:
        # Toàn bộ 6 lớp trong CSDL kết thúc ngày 2026-09-16 — nói rõ để model
        # không bịa ra lịch, và gợi ý khoảng ngày còn dữ liệu.
        res["ghiChu"] = (
            "Không có buổi học nào trong khoảng này. "
            "Dữ liệu hiện có trải từ 2026-06-01 đến 2026-09-16; "
            "hãy thử hỏi về một khoảng ngày trong phạm vi đó."
        )
    return res
