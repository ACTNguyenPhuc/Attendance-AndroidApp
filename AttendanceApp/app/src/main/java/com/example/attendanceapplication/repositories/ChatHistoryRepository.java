package com.example.attendanceapplication.repositories;

import android.util.Log;

import com.example.attendanceapplication.models.ChatMessage;
import com.example.attendanceapplication.models.Conversation;
import com.example.attendanceapplication.utils.MarkdownRenderer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lưu và đọc lại lịch sử chat với trợ lý AI.
 *
 * Dữ liệu nằm dưới users/{uid}/conversations/{convId}/messages — riêng tư theo
 * tài khoản, nên luật bảo mật chỉ cần so uid. Firestore bật sẵn bộ nhớ đệm
 * ngoại tuyến, vì vậy không cần thêm CSDL cục bộ chỉ để xem lại lịch sử.
 *
 * KHÔNG ghi theo từng mẩu chữ của luồng streaming — một câu trả lời sẽ thành
 * hàng trăm lượt ghi. Chỉ ghi khi người dùng gửi, và một lần nữa lúc AI trả xong.
 */
public class ChatHistoryRepository {

    private static final String TAG = "ChatHistoryRepo";

    private static final String COL_USERS         = "users";
    private static final String COL_CONVERSATIONS = "conversations";
    private static final String COL_MESSAGES      = "messages";

    /** Số hội thoại hiện trong mục "Cuộc trò chuyện gần đây". */
    public static final int RECENT_LIMIT = 5;

    private static final int TITLE_MAX   = 60;
    private static final int PREVIEW_MAX = 80;

    private static ChatHistoryRepository instance;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ChatHistoryRepository() { }

    public static synchronized ChatHistoryRepository getInstance() {
        if (instance == null) instance = new ChatHistoryRepository();
        return instance;
    }

    private String uid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? null : u.getUid();
    }

    private CollectionReference conversations() {
        String uid = uid();
        if (uid == null) return null;
        return db.collection(COL_USERS).document(uid).collection(COL_CONVERSATIONS);
    }

    private CollectionReference messages(String convId) {
        CollectionReference c = conversations();
        // convId null nghĩa là chưa mở được hội thoại (mất phiên đăng nhập).
        // Trả null để các hàm gọi bỏ qua, thay vì để Firestore ném lỗi.
        return (c == null || convId == null) ? null : c.document(convId).collection(COL_MESSAGES);
    }

    // ==================== GHI ====================

    /**
     * Mở một hội thoại mới và trả về id ngay lập tức.
     *
     * Id sinh phía client nên dùng được luôn, không phải chờ mạng. Gọi lúc người
     * dùng gửi câu đầu tiên chứ không phải lúc mở màn hình — nếu không, mỗi lần
     * bấm nhầm vào nút chat sẽ đẻ ra một hội thoại rỗng.
     *
     * Tiêu đề lấy thẳng từ câu hỏi đầu tiên. Nhờ AI đặt tên sẽ tốn thêm một
     * request Gemini cho mỗi hội thoại, quá đắt so với hạn mức của gói miễn phí.
     */
    public String createConversation(String firstQuestion) {
        CollectionReference col = conversations();
        if (col == null) return null;

        DocumentReference doc = col.document();
        long now = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("title", cut(firstQuestion, TITLE_MAX));
        data.put("preview", "");
        data.put("createdAt", now);
        data.put("updatedAt", now);
        data.put("messageCount", 0);
        data.put("serverSessionId", null);
        data.put("lastTool", null);

        doc.set(data).addOnFailureListener(e -> Log.w(TAG, "createConversation", e));
        return doc.getId();
    }

    /** Ghi tin nhắn của người dùng ngay khi bấm gửi, để không mất nếu app tắt giữa chừng. */
    public void appendUserMessage(String convId, String text, int seq) {
        appendMessage(convId, ChatMessage.ROLE_USER, text, seq);
    }

    /**
     * Ghi câu trả lời của AI và cập nhật phần tóm tắt của hội thoại.
     *
     * @param sessionId mạch hội thoại server vừa dùng, lưu lại để lần sau nối tiếp
     * @param lastTool  tool được gọi gần nhất, dùng chọn biểu tượng; có thể null
     */
    public void appendAiMessage(String convId, String text, int seq,
                                String sessionId, String lastTool, int messageCount) {
        appendMessage(convId, ChatMessage.ROLE_AI, text, seq);

        CollectionReference col = conversations();
        if (col == null || convId == null) return;

        Map<String, Object> update = new HashMap<>();
        // Gỡ Markdown TRƯỚC khi cắt, nếu không lát cắt có thể rơi vào giữa cặp **
        update.put("preview", cut(MarkdownRenderer.plain(text), PREVIEW_MAX));
        update.put("updatedAt", System.currentTimeMillis());
        update.put("messageCount", messageCount);
        if (sessionId != null) update.put("serverSessionId", sessionId);
        if (lastTool != null) update.put("lastTool", lastTool);

        col.document(convId).update(update)
                .addOnFailureListener(e -> Log.w(TAG, "appendAiMessage/update", e));
    }

    private void appendMessage(String convId, int role, String text, int seq) {
        CollectionReference col = messages(convId);
        if (col == null || text == null || text.trim().isEmpty()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("role", role);
        data.put("text", text);
        data.put("createdAt", System.currentTimeMillis());
        // Hai tin nhắn có thể rơi vào cùng một mili giây; seq mới là thứ tự đáng tin.
        data.put("seq", seq);

        col.add(data).addOnFailureListener(e -> Log.w(TAG, "appendMessage", e));
    }

    // ==================== ĐỌC ====================

    /** Vài hội thoại gần nhất, mới nhất lên đầu. */
    public void loadRecent(int limit,
                           FirebaseRepository.OnSuccessListener<List<Conversation>> onSuccess,
                           FirebaseRepository.OnFailureListener onFailure) {
        CollectionReference col = conversations();
        if (col == null) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }
        col.orderBy("updatedAt", Query.Direction.DESCENDING).limit(limit).get()
                .addOnSuccessListener(snap -> {
                    List<Conversation> out = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Conversation c = d.toObject(Conversation.class);
                        c.setId(d.getId());
                        // Hội thoại chưa có câu trả lời nào thì chưa đáng hiện
                        if (c.getMessageCount() > 0) out.add(c);
                    }
                    onSuccess.onSuccess(out);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "loadRecent", e);
                    onFailure.onFailure(e);
                });
    }

    /** Toàn bộ tin nhắn của một hội thoại, theo đúng thứ tự gửi. */
    public void loadMessages(String convId,
                             FirebaseRepository.OnSuccessListener<List<ChatMessage>> onSuccess,
                             FirebaseRepository.OnFailureListener onFailure) {
        CollectionReference col = messages(convId);
        if (col == null) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }
        col.orderBy("seq").get()
                .addOnSuccessListener(snap -> {
                    List<ChatMessage> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Long role = d.getLong("role");
                        Long at   = d.getLong("createdAt");
                        ChatMessage m = new ChatMessage(
                                role == null ? ChatMessage.ROLE_AI : role.intValue(),
                                d.getString("text"));
                        if (at != null) m.setCreatedAt(at);
                        out.add(m);
                    }
                    onSuccess.onSuccess(out);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "loadMessages", e);
                    onFailure.onFailure(e);
                });
    }

    // ==================== XOÁ ====================

    /**
     * Xoá một hội thoại cùng toàn bộ tin nhắn của nó.
     *
     * Firestore không xoá subcollection theo document cha, phải xoá tay từng tin.
     */
    public void deleteConversation(String convId, Runnable onDone) {
        CollectionReference msgs = messages(convId);
        CollectionReference col  = conversations();
        if (msgs == null || col == null || convId == null) return;

        msgs.get().addOnSuccessListener(snap -> {
            WriteBatch batch = db.batch();
            for (DocumentSnapshot d : snap.getDocuments()) batch.delete(d.getReference());
            batch.delete(col.document(convId));
            batch.commit()
                    .addOnSuccessListener(v -> { if (onDone != null) onDone.run(); })
                    .addOnFailureListener(e -> Log.w(TAG, "deleteConversation", e));
        });
    }

    /** Cắt bớt cho vừa một dòng trong danh sách. */
    private static String cut(String s, int max) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= max ? one : one.substring(0, max).trim() + "…";
    }
}
