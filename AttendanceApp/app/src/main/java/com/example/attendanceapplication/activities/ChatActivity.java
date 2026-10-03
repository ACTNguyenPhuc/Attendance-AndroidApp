package com.example.attendanceapplication.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.adapters.ChatMessageAdapter;
import com.example.attendanceapplication.adapters.ConversationAdapter;
import com.example.attendanceapplication.models.ChatMessage;
import com.example.attendanceapplication.models.Conversation;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.ChatHistoryRepository;
import com.example.attendanceapplication.repositories.ChatRepository;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Màn hình chat với trợ lý AI.
 *
 * Câu trả lời hiện dần từng chữ (streaming) thay vì chờ xong mới hiện một cục.
 * Gợi ý câu hỏi khác nhau theo vai trò: sinh viên hỏi về bản thân, giảng viên
 * hỏi về lớp và sinh viên của mình.
 *
 * Hội thoại được lưu lại qua {@link ChatHistoryRepository} và hiện ở mục
 * "Cuộc trò chuyện gần đây" ngay trong màn hình trống.
 */
public class ChatActivity extends AppCompatActivity implements ConversationAdapter.Listener {

    /** Tên tool bên server -> câu mô tả cho người dùng thấy lúc đang chờ. */
    private static final Map<String, String> TOOL_LABELS = new HashMap<>();

    static {
        TOOL_LABELS.put("get_my_classes", "Đang xem danh sách lớp…");
        TOOL_LABELS.put("get_my_schedule", "Đang tra lịch học…");
        TOOL_LABELS.put("get_my_attendance_summary", "Đang tính tỷ lệ điểm danh…");
        TOOL_LABELS.put("get_attendance_history", "Đang xem lịch sử điểm danh…");
        TOOL_LABELS.put("get_students_at_risk", "Đang lọc sinh viên vắng nhiều…");
        TOOL_LABELS.put("get_shift_attendance", "Đang xem chi tiết buổi học…");
    }

    // ── Gợi ý theo vai trò ──────────────────────────────────────────
    private static final String[] SUGGEST_STUDENT = {
            "Tôi vắng mấy buổi rồi?",
            "Lớp nào tôi vắng nhiều nhất?",
            "Tuần này tôi học những buổi nào?",
    };
    private static final String[] SUGGEST_TEACHER = {
            "Hôm nay tôi dạy mấy lớp?",
            "Sinh viên nào vắng quá 20%?",
            "Tỷ lệ chuyên cần các lớp tôi dạy thế nào?",
    };

    private static final String INTRO_STUDENT =
            "Tôi có thể tra giúp bạn lịch học, số buổi đã vắng và tỷ lệ chuyên cần "
                    + "từng lớp. Hãy thử hỏi tôi nhé.";
    private static final String INTRO_TEACHER =
            "Tôi có thể tra giúp anh/chị lịch dạy, tỷ lệ chuyên cần từng lớp và "
                    + "danh sách sinh viên vắng nhiều. Hãy thử hỏi tôi nhé.";

    private RecyclerView rvChat, rvHistory;
    private EditText etInput;
    private ImageButton btnSend;
    private View layoutEmpty, layoutChips, layoutHistory;
    private TextView tvIntro, tvSubtitle;
    private final TextView[] sugBlocks = new TextView[3];
    private final TextView[] chips = new TextView[3];

    private ChatMessageAdapter adapter;
    private ConversationAdapter historyAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<Conversation> recent = new ArrayList<>();
    private final ChatRepository chatRepo = ChatRepository.getInstance();
    private final ChatHistoryRepository historyRepo = ChatHistoryRepository.getInstance();
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    private boolean waiting;

    /** Hội thoại đang mở trong Firestore. null khi chưa gửi câu nào. */
    private String convId;
    /** Thứ tự tin nhắn trong hội thoại, cũng là tổng số tin đã lưu. */
    private int msgSeq;
    /** Tool được gọi gần nhất, lưu kèm hội thoại để chọn biểu tượng. */
    private String lastTool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvChat      = findViewById(R.id.rv_chat);
        etInput     = findViewById(R.id.et_chat_input);
        btnSend     = findViewById(R.id.btn_chat_send);
        layoutEmpty = findViewById(R.id.layout_chat_empty);
        layoutChips = findViewById(R.id.layout_chat_chips);
        layoutHistory = findViewById(R.id.layout_chat_history);
        rvHistory   = findViewById(R.id.rv_chat_history);
        tvIntro     = findViewById(R.id.tv_chat_intro);
        tvSubtitle  = findViewById(R.id.tv_chat_subtitle);

        sugBlocks[0] = findViewById(R.id.sug_1);
        sugBlocks[1] = findViewById(R.id.sug_2);
        sugBlocks[2] = findViewById(R.id.sug_3);
        chips[0] = findViewById(R.id.chip_1);
        chips[1] = findViewById(R.id.chip_2);
        chips[2] = findViewById(R.id.chip_3);

        findViewById(R.id.btn_chat_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_chat_new).setOnClickListener(v -> resetConversation());

        adapter = new ChatMessageAdapter(messages);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvChat.setLayoutManager(lm);
        rvChat.setAdapter(adapter);

        historyAdapter = new ConversationAdapter(recent, this);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(historyAdapter);

        btnSend.setOnClickListener(v -> send());
        btnSend.setEnabled(false);
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                btnSend.setEnabled(!waiting && !TextUtils.isEmpty(s.toString().trim()));
            }
        });

        // Hiện gợi ý của sinh viên trước, rồi đổi sang giảng viên nếu đúng vai trò.
        // Làm vậy để màn hình không trống trong lúc chờ đọc hồ sơ từ Firestore.
        applyRole(false);
        loadRole();
        updateEmptyState();

        // Activity bị dựng lại (xoay màn hình, thoát rồi vào lại) nhưng repository
        // là singleton nên vẫn nhớ hội thoại đang dở — nạp lại đúng chỗ đó.
        String open = chatRepo.getConversationId();
        if (open != null) {
            convId = open;
            loadMessages(open);
        }
        loadRecent();
    }

    private void loadRole() {
        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) return;
        repo.getUserProfile(fu.getUid(),
                (User u) -> {
                    if (isFinishing() || isDestroyed() || u == null) return;
                    applyRole(u.isTeacher());
                },
                e -> { /* giữ nguyên gợi ý mặc định */ });
    }

    private void applyRole(boolean teacher) {
        String[] qs = teacher ? SUGGEST_TEACHER : SUGGEST_STUDENT;
        tvIntro.setText(teacher ? INTRO_TEACHER : INTRO_STUDENT);
        tvSubtitle.setText(teacher
                ? "Hỏi về lịch dạy và chuyên cần lớp"
                : "Hỏi về lịch học và tỷ lệ chuyên cần");

        for (int i = 0; i < 3; i++) {
            final String q = qs[i];
            sugBlocks[i].setText(q);
            sugBlocks[i].setOnClickListener(v -> ask(q));
            chips[i].setText(q);
            chips[i].setOnClickListener(v -> ask(q));
        }
    }

    private void ask(String question) {
        if (waiting) return;
        etInput.setText(question);
        send();
    }

    // ── Lịch sử hội thoại ───────────────────────────────────────────

    private void loadRecent() {
        historyRepo.loadRecent(ChatHistoryRepository.RECENT_LIMIT,
                (List<Conversation> list) -> {
                    if (isFinishing() || isDestroyed()) return;
                    recent.clear();
                    recent.addAll(list);
                    historyAdapter.notifyDataSetChanged();
                    updateEmptyState();
                },
                e -> { /* không đọc được lịch sử thì màn hình vẫn dùng bình thường */ });
    }

    /** Đổ lại toàn bộ tin nhắn của một hội thoại vào khung chat. */
    private void loadMessages(String id) {
        historyRepo.loadMessages(id,
                (List<ChatMessage> list) -> {
                    if (isFinishing() || isDestroyed()) return;
                    messages.clear();
                    if (!list.isEmpty()) {
                        messages.add(ChatMessage.dateSeparator(list.get(0).getCreatedAt()));
                        messages.addAll(list);
                    }
                    // Tin mới phải nối tiếp thứ tự cũ, không được ghi đè
                    msgSeq = list.size();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    scrollToBottom();
                },
                e -> { /* giữ nguyên khung trống */ });
    }

    @Override
    public void onOpen(Conversation c) {
        if (waiting) return;
        convId = c.getId();
        lastTool = c.getLastTool();
        // Phiên server có thể đã hết hạn; server sẽ tự dựng lại mạch từ Firestore.
        chatRepo.openConversation(c.getId(), c.getServerSessionId());
        loadMessages(c.getId());
    }

    @Override
    public void onDelete(Conversation c) {
        new AlertDialog.Builder(this)
                .setTitle("Xoá cuộc trò chuyện?")
                .setMessage(c.getTitle())
                .setNegativeButton("Huỷ", null)
                .setPositiveButton("Xoá", (d, w) -> historyRepo.deleteConversation(c.getId(),
                        () -> {
                            if (isFinishing() || isDestroyed()) return;
                            // Đang mở đúng hội thoại vừa xoá -> về màn hình trống
                            if (c.getId().equals(convId)) resetConversation();
                            else loadRecent();
                        }))
                .show();
    }

    /** Chốt hội thoại hiện tại và mở một hội thoại mới. Không xoá gì cả. */
    private void resetConversation() {
        chatRepo.newConversation();
        convId = null;
        msgSeq = 0;
        lastTool = null;
        messages.clear();
        adapter.notifyDataSetChanged();
        setWaiting(false);
        updateEmptyState();
        loadRecent();
    }

    private void updateEmptyState() {
        boolean empty = messages.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        layoutChips.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutHistory.setVisibility(empty && !recent.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setWaiting(boolean w) {
        waiting = w;
        etInput.setEnabled(!w);
        btnSend.setEnabled(!w && !TextUtils.isEmpty(etInput.getText().toString().trim()));
    }

    private void send() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty() || waiting) return;

        etInput.setText("");
        setWaiting(true);

        // Hội thoại chỉ sinh ra khi thực sự có câu hỏi đầu tiên — mở màn hình
        // rồi thoát ngay sẽ không để lại bản ghi rỗng nào.
        if (convId == null) {
            convId = historyRepo.createConversation(text);
            chatRepo.bindConversation(convId);
        }
        final int userSeq = msgSeq++;
        final int aiSeq   = msgSeq++;
        historyRepo.appendUserMessage(convId, text, userSeq);

        int inserted = 0;
        if (messages.isEmpty()) {
            messages.add(ChatMessage.dateSeparator());
            inserted++;
        }
        messages.add(ChatMessage.user(text));
        final ChatMessage pending = ChatMessage.aiPending();
        messages.add(pending);
        inserted += 2;

        adapter.notifyItemRangeInserted(messages.size() - inserted, inserted);
        updateEmptyState();
        scrollToBottom();

        final int pendingPos = messages.size() - 1;

        chatRepo.send(text, new ChatRepository.StreamCallback() {
            @Override
            public void onTool(String toolName) {
                lastTool = toolName;
                String label = TOOL_LABELS.get(toolName);
                pending.setToolNote(label != null ? label : "Đang tra cứu dữ liệu…");
                adapter.notifyItemChanged(pendingPos);
                scrollToBottom();
            }

            @Override
            public void onDelta(String delta) {
                pending.append(delta);
                adapter.notifyItemChanged(pendingPos);
                scrollToBottom();
            }

            @Override
            public void onDone() {
                pending.setStreaming(false);
                pending.setToolNote(null);

                String answer = pending.getText();
                if (answer.isEmpty()) {
                    pending.setText("(không có nội dung trả lời)");
                } else {
                    // Ghi một lần lúc xong, không ghi theo từng mẩu chữ của luồng
                    historyRepo.appendAiMessage(convId, answer, aiSeq,
                            chatRepo.getSessionId(), lastTool, msgSeq);
                }

                adapter.notifyItemChanged(pendingPos);
                setWaiting(false);
                scrollToBottom();
            }

            @Override
            public void onError(String message) {
                pending.setStreaming(false);
                pending.setToolNote(null);
                pending.setText(message);
                adapter.notifyItemChanged(pendingPos);
                setWaiting(false);
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        rvChat.post(() -> {
            if (!messages.isEmpty()) rvChat.scrollToPosition(messages.size() - 1);
        });
    }

    @Override
    protected void onDestroy() {
        // Huỷ kết nối streaming đang mở, tránh rò rỉ khi rời màn hình giữa chừng
        chatRepo.cancel();
        super.onDestroy();
    }
}
