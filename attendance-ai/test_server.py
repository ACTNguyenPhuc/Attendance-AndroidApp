"""Gọi thử server đang chạy. Không cần trình duyệt, không cần curl."""
import json
import sys
import urllib.error
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

URL = "http://localhost:8000/chat"
SV_LINH = "Wir5XPSodGfm523tNEaj3DCbD5f2"
SV_DAT = "7AUIkd3JpwfaEiAmnZ8yqrkFhMm1"
GV_NAM = "XIRtyYogQhZf5BL1lyGv8cB18Vi2"


def ask(msg, uid, session_id=None):
    body = json.dumps({"message": msg, "uid": uid, "session_id": session_id}).encode("utf-8")
    req = urllib.request.Request(URL, data=body, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"_http": e.code, "detail": json.loads(e.read().decode("utf-8")).get("detail")}


def show(title, msg, uid, session_id=None):
    print(f"\n=== {title} ===")
    print(f"  hỏi : {msg}")
    d = ask(msg, uid, session_id)
    if "_http" in d:
        print(f"  ! HTTP {d['_http']}: {d['detail']}")
        return None
    print(f"  ai  : {d['nguoiDung']['ten']} ({d['nguoiDung']['vaiTro']})")
    print(f"  tool: {[t['ten'] for t in d['tools']] or '—'}")
    print(f"  đáp : {d['reply'][:250]}")
    return d["session_id"]


if __name__ == "__main__":
    sid = show("1. Sinh viên hỏi số buổi vắng", "Tôi vắng mấy buổi rồi?", SV_LINH)
    if sid:
        show("2. Hỏi tiếp — kiểm tra phiên nhớ ngữ cảnh", "Thế lớp nào nhiều nhất?", SV_LINH, sid)
    show("3. Giảng viên hỏi sinh viên vắng nhiều",
         "Sinh viên nào vắng quá 50% lớp LTACB_L01?", GV_NAM)
    show("4. PHÂN QUYỀN — SV hỏi lớp mình không học",
         "Cho tôi xem thống kê điểm danh lớp TRR_L02", SV_DAT)
