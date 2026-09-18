package com.example.attendanceapplication.repositories;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * Gọi server AI (FastAPI) và đọc câu trả lời theo kiểu streaming (SSE).
 *
 * Tách riêng khỏi FirebaseRepository: chatbot là phần cộng thêm,
 * không được làm ảnh hưởng luồng điểm danh đang chạy.
 *
 * ĐỊA CHỈ SERVER: 10.0.2.2 là bí danh trỏ về máy tính thật khi chạy trên
 * Android Emulator. Máy thật cùng Wi-Fi thì đổi thành IP của máy tính, hoặc
 * chạy "adb reverse tcp:8000 tcp:8000" rồi dùng localhost.
 */
public class ChatRepository {

    public static final String BASE_URL = "http://10.0.2.2:8000";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static ChatRepository instance;

    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Giữ mạch hội thoại giữa các lượt hỏi. Server tự hết hạn sau 30 phút. */
    private String sessionId;
    private Call current;

    private ChatRepository() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                // Streaming giữ kết nối mở suốt lúc AI trả lời -> không đặt hạn đọc
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public static synchronized ChatRepository getInstance() {
        if (instance == null) instance = new ChatRepository();
        return instance;
    }

    /** Các sự kiện phát ra trong lúc AI trả lời. Mọi phương thức chạy trên luồng UI. */
    public interface StreamCallback {
        /** AI đang gọi một công cụ tra cứu dữ liệu. */
        void onTool(String toolName);

        /** Một mẩu chữ mới của câu trả lời. */
        void onDelta(String delta);

        /** Trả lời xong. */
        void onDone();

        void onError(String message);
    }

    public void newConversation() {
        sessionId = null;
        cancel();
    }

    public void cancel() {
        if (current != null) {
            current.cancel();
            current = null;
        }
    }

    public void send(String message, StreamCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            main.post(() -> cb.onError("Bạn cần đăng nhập lại."));
            return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(res -> doSend(res.getToken(), message, cb))
                .addOnFailureListener(e ->
                        main.post(() -> cb.onError("Không lấy được phiên đăng nhập.")));
    }

    private void doSend(String idToken, String message, StreamCallback cb) {
        String body;
        try {
            JSONObject o = new JSONObject();
            o.put("message", message);
            if (sessionId != null) o.put("session_id", sessionId);
            body = o.toString();
        } catch (Exception e) {
            main.post(() -> cb.onError("Lỗi tạo yêu cầu."));
            return;
        }

        Request req = new Request.Builder()
                .url(BASE_URL + "/chat/stream")
                .addHeader("Authorization", "Bearer " + idToken)
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(body, JSON))
                .build();

        cancel();
        current = client.newCall(req);
        current.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                main.post(() -> cb.onError("Không kết nối được máy chủ AI.\n"
                        + "Kiểm tra server đã chạy chưa và địa chỉ " + BASE_URL));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody rb = response.body()) {
                    if (!response.isSuccessful() || rb == null) {
                        String msg = describeError(response.code());
                        main.post(() -> cb.onError(msg));
                        return;
                    }
                    readSse(rb.source(), call, cb);
                } catch (IOException e) {
                    if (!call.isCanceled()) {
                        main.post(() -> cb.onError("Mất kết nối giữa chừng."));
                    }
                }
            }
        });
    }

    /** Đọc từng dòng "data: {...}" của luồng SSE và phát sự kiện tương ứng. */
    private void readSse(BufferedSource source, Call call, StreamCallback cb) throws IOException {
        while (!source.exhausted()) {
            if (call.isCanceled()) return;

            String line = source.readUtf8LineStrict();
            if (line == null || !line.startsWith("data:")) continue;

            String payload = line.substring(5).trim();
            if (payload.isEmpty()) continue;

            try {
                JSONObject ev = new JSONObject(payload);
                String type = ev.optString("type");

                if ("start".equals(type)) {
                    sessionId = ev.optString("session_id", sessionId);
                } else if ("tool".equals(type)) {
                    final String name = ev.optString("name");
                    main.post(() -> cb.onTool(name));
                } else if ("text".equals(type)) {
                    final String delta = ev.optString("delta");
                    if (!delta.isEmpty()) main.post(() -> cb.onDelta(delta));
                } else if ("error".equals(type)) {
                    final String m = ev.optString("message", "Đã có lỗi xảy ra.");
                    main.post(() -> cb.onError(m));
                    return;
                } else if ("done".equals(type)) {
                    main.post(cb::onDone);
                    return;
                }
            } catch (Exception ignored) {
                // một dòng hỏng không nên làm đứt cả luồng
            }
        }
        main.post(cb::onDone);
    }

    private static String describeError(int code) {
        switch (code) {
            case 401:
                return "Phiên đăng nhập không hợp lệ. Bạn thử đăng nhập lại nhé.";
            case 429:
                return "Bạn đã hỏi khá nhiều. Nghỉ một chút rồi hỏi tiếp nhé.";
            case 500:
                return "Máy chủ AI gặp lỗi. Thử lại sau ít phút.";
            default:
                return "Máy chủ trả về lỗi " + code + ".";
        }
    }
}
