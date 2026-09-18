"""Cấu hình chung: đường dẫn, kết nối Firestore, múi giờ."""
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

# Terminal Windows mặc định cp1252 -> vỡ khi in tiếng Việt. Phải đặt TRƯỚC mọi lần print.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from dotenv import load_dotenv

HERE = Path(__file__).resolve().parent
REPO = HERE.parent

load_dotenv(REPO / ".env")

GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
SERVICE_ACCOUNT = Path(
    os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    or REPO / "AttendanceApp" / "serviceAccountKey.json"
)

# Gói miễn phí giới hạn 20 request/NGÀY cho MỖI model (và 5 request/phút).
# Vì quota tính riêng từng model nên khi cạn một model có thể chuyển sang model khác.
# agent.py tự làm việc này, xem Agent._switch_model().
MODEL = os.getenv("GEMINI_MODEL", "gemini-3-flash-preview")
MODEL_FALLBACKS = [m.strip() for m in os.getenv(
    "GEMINI_FALLBACKS",
    "gemini-flash-latest,gemini-flash-lite-latest,gemini-3.6-flash",
).split(",") if m.strip()]
MODELS = [MODEL] + [m for m in MODEL_FALLBACKS if m != MODEL]

# Việt Nam là UTC+7 quanh năm, không có giờ mùa hè.
# Dùng offset cố định để khỏi phụ thuộc gói tzdata (Windows không có sẵn CSDL múi giờ IANA).
VN_TZ = timezone(timedelta(hours=7))

_db = None


def get_db():
    """Trả về Firestore client, khởi tạo một lần duy nhất."""
    global _db
    if _db is None:
        import firebase_admin
        from firebase_admin import credentials, firestore

        if not SERVICE_ACCOUNT.exists():
            raise SystemExit(
                f"\n❌ Không tìm thấy service account key tại:\n   {SERVICE_ACCOUNT}\n\n"
                "   Tải tại: Firebase Console > Project settings > Service accounts\n"
                "   > Generate new private key\n"
            )
        if not firebase_admin._apps:
            firebase_admin.initialize_app(credentials.Certificate(str(SERVICE_ACCOUNT)))
        _db = firestore.client()
    return _db


def check_api_key():
    if not GOOGLE_API_KEY:
        raise SystemExit(
            f"\n❌ Thiếu GOOGLE_API_KEY.\n   Tạo file {REPO / '.env'} với nội dung:\n"
            "   GOOGLE_API_KEY=...\n\n   Lấy key tại https://aistudio.google.com/apikey\n"
        )


def now_vn() -> datetime:
    return datetime.now(VN_TZ)


def today_str() -> str:
    """Hôm nay theo giờ Việt Nam, dạng yyyy-MM-dd — khớp với shifts.date."""
    return now_vn().strftime("%Y-%m-%d")


def to_vn_time(ts) -> str:
    """Timestamp Firestore (UTC) -> giờ Việt Nam dạng HH:MM. Trả '' nếu rỗng."""
    if not ts:
        return ""
    return ts.astimezone(VN_TZ).strftime("%H:%M")


def to_vn_datetime(ts) -> str:
    if not ts:
        return ""
    return ts.astimezone(VN_TZ).strftime("%Y-%m-%d %H:%M")
