"""Đọc lại lịch sử chat mà ứng dụng đã lưu trong Firestore.

Phiên hội thoại của server chỉ sống trong RAM và tự hết hạn sau 30 phút, nên
người dùng mở lại một hội thoại cũ thì model không còn nhớ gì. Module này đọc
vài lượt gần nhất để dựng lại mạch.

Trả về list dict thuần {"role": ..., "text": ...} — KHÔNG đụng tới thư viện của
Gemini, để agent.py vẫn là file duy nhất phụ thuộc nhà cung cấp mô hình.

Đường dẫn khớp với ChatHistoryRepository bên Android:
    users/{uid}/conversations/{convId}/messages
"""
from config import get_db

# Khớp với ChatMessage.ROLE_USER / ROLE_AI bên Android.
_ROLE_USER = 0

# Mỗi lượt là một cặp hỏi–đáp. Nạp nhiều hơn thì mỗi request đội token và
# chạm trần quota nhanh hơn, mà phần đầu hội thoại thường cũng không còn liên quan.
DEFAULT_MAX_TURNS = 10


def load_history(uid: str, conversation_id: str | None,
                 max_turns: int = DEFAULT_MAX_TURNS) -> list[dict]:
    """Các lượt chữ gần nhất của một hội thoại, theo đúng thứ tự gửi.

    CHỈ lấy phần chữ, không dựng lại kết quả tool cũ. Số liệu điểm danh hôm qua
    có thể đã khác hôm nay — để model gọi tool lại là đúng, và cũng nhẹ token hơn.
    """
    if not conversation_id:
        return []

    from firebase_admin import firestore

    col = (get_db().collection("users").document(uid)
           .collection("conversations").document(conversation_id)
           .collection("messages"))

    try:
        docs = list(col.order_by("seq", direction=firestore.Query.DESCENDING)
                    .limit(max_turns * 2).stream())
    except Exception:  # noqa: BLE001 — hội thoại không đọc được thì coi như chưa có
        return []

    turns = []
    for d in reversed(docs):
        m = d.to_dict() or {}
        text = (m.get("text") or "").strip()
        if not text:
            continue
        role = "user" if m.get("role") == _ROLE_USER else "model"
        turns.append({"role": role, "text": text})

    # Gemini đòi lịch sử bắt đầu bằng lượt của người dùng và các lượt xen kẽ nhau.
    # Cắt phần đầu nếu lát cắt rơi vào giữa một cặp hỏi–đáp.
    while turns and turns[0]["role"] != "user":
        turns.pop(0)

    return turns
