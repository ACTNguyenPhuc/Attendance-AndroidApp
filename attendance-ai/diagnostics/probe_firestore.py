"""Dò cấu trúc dữ liệu thật trong Firestore trước khi viết tool.

Chạy:  venv/Scripts/python.exe probe_firestore.py
Script chỉ ĐỌC, không ghi gì. Tên và email được che bớt.
"""
import sys, os
# Terminal Windows mặc định cp1252 -> vỡ khi in tiếng Việt
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import firebase_admin
from firebase_admin import credentials, firestore

KEY = os.path.join(os.path.dirname(__file__), "..", "AttendanceApp", "serviceAccountKey.json")
COLLECTIONS = ["users", "classes", "shifts", "sessions", "attendances", "enrollments"]
SENSITIVE = {"name", "email", "studentName", "teacherName", "teacher", "avatarUrl", "deviceId"}


def mask(key, val):
    """Che dữ liệu cá nhân, giữ nguyên thứ cần biết về định dạng."""
    if key in SENSITIVE and isinstance(val, str) and val:
        return f"<{len(val)} ký tự>"
    if isinstance(val, str) and len(val) > 60:
        return val[:57] + "..."
    return val


def main():
    firebase_admin.initialize_app(credentials.Certificate(KEY))
    db = firestore.client()

    print("=" * 68)
    print("DÒ DỮ LIỆU FIRESTORE — project attendance-297e6")
    print("=" * 68)

    for col in COLLECTIONS:
        docs = list(db.collection(col).limit(200).stream())
        print(f"\n┌─ {col}  —  {len(docs)} document (lấy tối đa 200)")
        if not docs:
            print("│  (TRỐNG)")
            continue

        # Thống kê field xuất hiện ở bao nhiêu doc -> phát hiện field thiếu/null
        seen = {}
        for d in docs:
            for k, v in (d.to_dict() or {}).items():
                seen.setdefault(k, {"count": 0, "types": set(), "nulls": 0})
                seen[k]["count"] += 1
                seen[k]["types"].add(type(v).__name__)
                if v is None:
                    seen[k]["nulls"] += 1

        print(f"│  doc đầu tiên: id = {docs[0].id}")
        for k in sorted(seen):
            info = seen[k]
            cover = f"{info['count']}/{len(docs)}"
            types = "|".join(sorted(info["types"]))
            null = f"  null×{info['nulls']}" if info["nulls"] else ""
            sample = mask(k, (docs[0].to_dict() or {}).get(k))
            print(f"│    {k:22} {types:26} có ở {cover:>8}{null}   vd: {sample!r}")

    # Vài con số để biết dữ liệu có đủ để chatbot trả lời không
    print("\n" + "=" * 68)
    print("KIỂM TRA ĐỘ SẴN SÀNG")
    print("=" * 68)
    users = list(db.collection("users").limit(200).stream())
    roles = {}
    for u in users:
        roles[(u.to_dict() or {}).get("role", "?")] = roles.get((u.to_dict() or {}).get("role", "?"), 0) + 1
    print(f"  vai trò người dùng      : {roles}")

    shifts = list(db.collection("shifts").limit(500).stream())
    st = {}
    for s in shifts:
        st[(s.to_dict() or {}).get("status", "?")] = st.get((s.to_dict() or {}).get("status", "?"), 0) + 1
    print(f"  trạng thái ca học       : {st}")

    atts = list(db.collection("attendances").limit(500).stream())
    ast = {}
    for a in atts:
        ast[(a.to_dict() or {}).get("status", "?")] = ast.get((a.to_dict() or {}).get("status", "?"), 0) + 1
    print(f"  trạng thái điểm danh    : {ast}")

    # Tìm một sinh viên có dữ liệu phong phú nhất để làm tài khoản test
    per_student = {}
    for a in atts:
        sid = (a.to_dict() or {}).get("studentId")
        if sid:
            per_student[sid] = per_student.get(sid, 0) + 1
    if per_student:
        best = max(per_student.items(), key=lambda x: x[1])
        print(f"  SV có nhiều bản ghi nhất: uid={best[0]}  ({best[1]} lượt điểm danh)")
    else:
        print("  ⚠️  CHƯA CÓ bản ghi điểm danh nào -> chatbot sẽ không có gì để trả lời")

    enr = list(db.collection("enrollments").limit(300).stream())
    print(f"  số lượt tham gia lớp    : {len(enr)}")


if __name__ == "__main__":
    main()
