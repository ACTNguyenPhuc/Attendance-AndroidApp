"""Kiểm tra server KHÔNG tốn request Gemini nào.

Mọi ca ở đây đều bị server chặn lại TRƯỚC khi gọi tới AI, nên chạy thoải mái.
"""
import json, sys, urllib.error, urllib.request
if hasattr(sys.stdout, "reconfigure"): sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://localhost:8000"

def call(path, body=None, headers=None):
    if body is None:
        req = urllib.request.Request(BASE + path)
    else:
        req = urllib.request.Request(BASE + path, data=json.dumps(body).encode("utf-8"),
                                     headers={"Content-Type": "application/json", **(headers or {})})
    def parse(raw):
        try: return json.loads(raw.decode("utf-8"))
        except Exception: return {}
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, parse(r.read())
    except urllib.error.HTTPError as e:
        return e.code, parse(e.read())

def check(name, got, want):
    ok = got == want
    print(f"  {'✅' if ok else '❌'} {name:44} HTTP {got} (mong đợi {want})")
    return ok

print("KIỂM TRA SERVER — 0 REQUEST GEMINI\n")
ok = []
s, d = call("/health"); ok.append(check("GET /health", s, 200))
print(f"      model={d.get('model')}  dev_mode={d.get('dev_mode')}  phiên={d.get('phien_dang_mo')}")

s, _ = call("/"); ok.append(check("GET / (trang test)", s, 200))
s, d = call("/chat", {"message": "xin chào"}); ok.append(check("POST /chat KHÔNG uid, KHÔNG token → chặn", s, 401))
print(f"      lý do: {d.get('detail','')[:70]}")
s, _ = call("/chat", {"message": "", "uid": "Wir5XPSodGfm523tNEaj3DCbD5f2"}); ok.append(check("POST /chat message rỗng → chặn", s, 400))
s, d = call("/chat", {"message": "hi"}, {"Authorization": "Bearer token_gia_mao"})
ok.append(check("POST /chat token giả mạo → chặn", s, 401))
print(f"      lý do: {d.get('detail','')[:70]}")
s, d = call("/chat", {"message": "hi", "uid": "uid_khong_ton_tai_12345"})
ok.append(check("POST /chat uid không tồn tại → chặn", s != 200, True))

print(f"\n{sum(ok)}/{len(ok)} đạt — không tiêu tốn quota nào.")
