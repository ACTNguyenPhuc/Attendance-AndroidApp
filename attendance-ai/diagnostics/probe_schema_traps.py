import sys, os
from datetime import datetime, timezone, timedelta
if hasattr(sys.stdout, "reconfigure"): sys.stdout.reconfigure(encoding="utf-8", errors="replace")
import firebase_admin
from firebase_admin import credentials, firestore
firebase_admin.initialize_app(credentials.Certificate(
    os.path.join(os.path.dirname(__file__),"..","AttendanceApp","serviceAccountKey.json")))
db = firestore.client()
TODAY = "2026-09-17"

print("=== 1. KHOẢNG NGÀY CỦA CÁC CA HỌC ===")
shifts=[d.to_dict() for d in db.collection("shifts").stream()]
dates=sorted(s["date"] for s in shifts)
print(f"  sớm nhất: {dates[0]}   muộn nhất: {dates[-1]}   (hôm nay: {TODAY})")
future=[s for s in shifts if s["date"]>TODAY]
past_upcoming=[s for s in shifts if s["date"]<TODAY and s.get("status")=="upcoming"]
print(f"  ca ở TƯƠNG LAI       : {len(future)}")
print(f"  ca ĐÃ QUA nhưng status vẫn 'upcoming': {len(past_upcoming)}  <-- status không tự cập nhật")

print("\n=== 2. teacher vs teacherName (tài liệu ghi ngược?) ===")
s=shifts[0]
print(f"  shifts.teacher     = {s.get('teacher')!r}")
print(f"  shifts.teacherName = {s.get('teacherName')!r}  (dài {len(str(s.get('teacherName')))})")
u=db.collection("users").document(str(s.get("teacherName"))).get()
print(f"  -> tra users/{{teacherName}}: {'TỒN TẠI, role='+u.to_dict().get('role') if u.exists else 'không tồn tại'}")

print("\n=== 3. studentCount có đáng tin không? ===")
for c in db.collection("classes").stream():
    cd=c.to_dict()
    n=len(list(db.collection("enrollments").where(filter=firestore.FieldFilter("classId","==",c.id)).stream()))
    flag = "  <-- LỆCH" if cd.get("studentCount")!=n else ""
    print(f"  {c.id:14} studentCount={cd.get('studentCount'):<3} nhưng enrollments thật={n}{flag}")

print("\n=== 4. MÚI GIỜ: startAt (chuỗi) vs startTime (UTC) ===")
ses=db.collection("sessions").document("session_KTPMN_L03_KTPMN_L03_2026-06-02_1783146242769").get().to_dict()
sh=db.collection("shifts").document("KTPMN_L03_2026-06-02").get().to_dict()
print(f"  shift.startAt/endAt   = {sh['startAt']} - {sh['endAt']}  (giờ Việt Nam, dạng chuỗi)")
print(f"  session.scheduledEndTime = {ses['scheduledEndTime']}  (UTC)")
print(f"  -> +7h = {ses['scheduledEndTime'].astimezone(timezone(timedelta(hours=7))).strftime('%H:%M')} khớp endAt={sh['endAt']}")

print("\n=== 5. SV test: Wir5XPSodGfm523tNEaj3DCbD5f2 ===")
uid="Wir5XPSodGfm523tNEaj3DCbD5f2"
ud=db.collection("users").document(uid).get().to_dict()
print(f"  role={ud.get('role')}  studentCode={ud.get('studentCode')}")
enr=[e.to_dict()["classId"] for e in db.collection("enrollments").where(filter=firestore.FieldFilter("studentId","==",uid)).stream()]
print(f"  tham gia {len(enr)} lớp: {enr}")
att=[a.to_dict() for a in db.collection("attendances").where(filter=firestore.FieldFilter("studentId","==",uid)).stream()]
print(f"  có {len(att)} lượt điểm danh: {[a['status'] for a in att]}")
for cid in enr:
    done=[s for s in shifts if s["classId"]==cid and s["date"]<=TODAY and s.get("attendanceSessionId")]
    mine=[a for a in att if a["classId"]==cid]
    print(f"    lớp {cid:14}: {len(done)} buổi đã mở điểm danh, SV có mặt {len(mine)} -> vắng {len(done)-len(mine)}")

print("\n=== 6. GIẢNG VIÊN ===")
for t in db.collection("users").where(filter=firestore.FieldFilter("role","==","teacher")).stream():
    td=t.to_dict()
    cls=[c.id for c in db.collection("classes").where(filter=firestore.FieldFilter("teacherId","==",t.id)).stream()]
    print(f"  uid={t.id}  code={td.get('studentCode')}  dạy {len(cls)} lớp: {cls}")
