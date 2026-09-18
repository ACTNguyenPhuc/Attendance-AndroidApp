"""Tool: xem các lớp của mình."""
from ._common import load_classes, my_class_ids


def get_my_classes(ctx) -> dict:
    ids = my_class_ids(ctx)
    if not ids:
        return {
            "soLop": 0,
            "danhSach": [],
            "ghiChu": "Bạn chưa tham gia lớp nào." if ctx.is_student else "Bạn chưa tạo lớp nào.",
        }
    info = load_classes(ids)
    return {
        "vaiTro": "giảng viên" if ctx.is_teacher else "sinh viên",
        "soLop": len(info),
        "danhSach": [info[i] for i in ids if i in info],
    }
