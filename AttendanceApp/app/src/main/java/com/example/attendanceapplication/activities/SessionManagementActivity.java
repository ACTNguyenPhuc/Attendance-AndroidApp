package com.example.attendanceapplication.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.adapters.RealtimeAttendanceAdapter;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Session;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.example.attendanceapplication.utils.LocationService;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionManagementActivity extends AppCompatActivity {

    public static final String EXTRA_SHIFT_ID  = "shiftId";
    public static final String EXTRA_CLASS_ID  = "classId";
    public static final String EXTRA_CLASS_NAME = "className";
    /** Mở phiên điểm danh BÙ (cho sinh viên đi muộn) thay vì phiên thường. */
    public static final String EXTRA_MAKEUP = "makeup";

    private static final int PERM_LOCATION = 201;

    private ImageView ivQrCode;
    private TextView tvShiftInfo, tvAttendanceCount, tvSessionStatus;
    private Button btnRefreshQr, btnCloseSession, btnApplyLate;
    private TextInputLayout tilLateMinutes;
    private TextInputEditText etLateMinutes;
    private RecyclerView rvAttendance;

    private String shiftId, classId, className;
    private boolean makeupMode;
    private int makeupLateMinutes = Session.DEFAULT_LATE_AFTER_MINUTES;
    private Shift currentShift;
    private Session currentSession;
    private RealtimeAttendanceAdapter adapter;
    private ListenerRegistration attendanceListener;
    private List<Attendance> attendanceList = new ArrayList<>();
    private int totalStudents = 0;
    /** true khi đang chờ người dùng cấp quyền vị trí để tạo phiên mới. */
    private boolean awaitingLocationPermission = false;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private final ExecutorService geocodingExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_management);

        shiftId    = getIntent().getStringExtra(EXTRA_SHIFT_ID);
        classId    = getIntent().getStringExtra(EXTRA_CLASS_ID);
        className  = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        makeupMode = getIntent().getBooleanExtra(EXTRA_MAKEUP, false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle((makeupMode ? "Điểm danh bù - " : "Điểm danh - ") + className);
        }
        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon != null) {
            navIcon.setTint(ContextCompat.getColor(this, R.color.white));
        }
        initViews();
        setupRecyclerView();
        loadTotalStudents();
        createOrLoadSession();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Người dùng vừa cấp quyền vị trí (qua hộp thoại hệ thống hoặc Cài đặt)
        // và phiên chưa được tạo → tiếp tục tạo phiên. Cờ đảm bảo chỉ chạy một lần.
        if (awaitingLocationPermission && currentSession == null && hasLocationPermission()) {
            awaitingLocationPermission = false;
            fetchLocationAndCreateSession();
        }
    }

    private void initViews() {
        ivQrCode          = findViewById(R.id.iv_qr_code);
        tvShiftInfo       = findViewById(R.id.tv_shift_info);
        tvAttendanceCount = findViewById(R.id.tv_attendance_count);
        tvSessionStatus   = findViewById(R.id.tv_session_status);
        btnRefreshQr      = findViewById(R.id.btn_refresh_qr);
        btnCloseSession   = findViewById(R.id.btn_close_session);
        tilLateMinutes    = findViewById(R.id.til_late_minutes);
        etLateMinutes     = findViewById(R.id.et_late_minutes);
        btnApplyLate      = findViewById(R.id.btn_apply_late);
        rvAttendance      = findViewById(R.id.rv_attendance);

        btnRefreshQr.setOnClickListener(v -> refreshQrCode());
        btnCloseSession.setOnClickListener(v -> {
            if (makeupMode) confirmCloseMakeup(); else confirmClose();
        });
        btnApplyLate.setOnClickListener(v -> applyLateMinutes());

        // Phiên bù cố định mốc muộn theo phiên gốc → ẩn ô chỉnh "cho phép muộn"
        // để tránh giáo viên đổi giá trị làm sai mục đích "tất cả đều muộn".
        if (makeupMode) {
            View cardLate = findViewById(R.id.card_late_minutes);
            if (cardLate != null) cardLate.setVisibility(View.GONE);
        }

        // Nút "Áp dụng" chỉ hiện khi ô số phút có giá trị.
        etLateMinutes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                tilLateMinutes.setError(null);
                btnApplyLate.setVisibility(
                        s.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    /** Đổ giá trị "cho phép muộn" hiện tại của phiên vào ô nhập. */
    private void bindLateMinutes() {
        if (currentSession == null) return;
        etLateMinutes.setText(String.valueOf(currentSession.getLateAfterMinutes()));
    }

    /** Lưu số phút cho phép vào muộn cho phiên hiện tại (0 < phút ≤ 30). */
    private void applyLateMinutes() {
        if (currentSession == null) return;
        String s = etLateMinutes.getText() == null ? "" : etLateMinutes.getText().toString().trim();
        if (s.isEmpty()) return;

        int minutes;
        try {
            minutes = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            tilLateMinutes.setError("Số phút không hợp lệ");
            return;
        }
        if (minutes <= 0) { tilLateMinutes.setError("Số phút phải lớn hơn 0"); return; }
        if (minutes > 30) { tilLateMinutes.setError("Tối đa 30 phút"); return; }

        tilLateMinutes.setError(null);
        btnApplyLate.setEnabled(false);
        repo.updateSessionLateMinutes(currentSession.getSessionId(), minutes,
                unused -> runOnUiThread(() -> {
                    currentSession.setLateAfterMinutes(minutes);
                    btnApplyLate.setEnabled(true);
                    Toast.makeText(this, "Đã lưu: cho phép muộn " + minutes + " phút",
                            Toast.LENGTH_SHORT).show();
                }),
                e -> runOnUiThread(() -> {
                    btnApplyLate.setEnabled(true);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                })
        );
    }

    private void setupRecyclerView() {
        adapter = new RealtimeAttendanceAdapter(attendanceList);
        rvAttendance.setLayoutManager(new LinearLayoutManager(this));
        rvAttendance.setAdapter(adapter);
    }

    private void loadTotalStudents() {
        repo.getClassStudents(classId,
                students -> {
                    totalStudents = students.size();
                    updateAttendanceCount();
                },
                e -> Toast.makeText(this, "Không thể tải sĩ số lớp: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    private void createOrLoadSession() {
        tvSessionStatus.setText("Đang tải phiên điểm danh...");
        if (makeupMode) {
            prepareMakeupSession();
            return;
        }
        // Nếu buổi học đã mở điểm danh từ trước (đang diễn ra), nạp lại đúng phiên
        // đó để thấy danh sách sinh viên đã điểm danh — thay vì tạo phiên mới (rỗng).
        repo.getShiftById(shiftId,
                shift -> {
                    currentShift = shift;
                    if (shift != null && shift.isAttendanceOpened()
                            && shift.getAttendanceSessionId() != null
                            && !shift.getAttendanceSessionId().isEmpty()) {
                        loadExistingSession(shift.getAttendanceSessionId());
                    } else {
                        startNewSession();
                    }
                },
                e -> startNewSession()
        );
    }

    /**
     * Phiên bù: luôn tạo phiên MỚI cho buổi đã kết thúc, nhưng kế thừa mốc
     * "cho phép muộn" của phiên gốc để giữ đúng ngưỡng đã cấu hình.
     */
    private void prepareMakeupSession() {
        repo.getShiftById(shiftId,
                shift -> {
                    currentShift = shift;
                    String prevSid = shift != null ? shift.getAttendanceSessionId() : null;
                    if (prevSid != null && !prevSid.isEmpty()) {
                        repo.getSession(prevSid,
                                prev -> {
                                    if (prev != null) makeupLateMinutes = prev.getLateAfterMinutes();
                                    startNewSession();
                                },
                                e -> startNewSession());
                    } else {
                        startNewSession();
                    }
                },
                e -> startNewSession()
        );
    }

    private void loadExistingSession(String sessionId) {
        repo.getSession(sessionId,
                session -> {
                    currentSession = session;
                    displayQrCode(session);
                    startRealtimeListener(session.getSessionId());
                    tvSessionStatus.setText("ĐANG MỞ ĐIỂM DANH");
                    displayLocation(session.getLatitude(), session.getLongitude());
                    bindLateMinutes();
                },
                // Phiên cũ không đọc được → tạo phiên mới
                e -> startNewSession()
        );
    }

    private void startNewSession() {
        // Phiên điểm danh cần vị trí GPS làm tâm bán kính chống gian lận.
        // Kiểm tra quyền định vị trước; nếu chưa được cấp thì yêu cầu cấp.
        if (!hasLocationPermission()) {
            awaitingLocationPermission = true;
            tvSessionStatus.setText("Cần quyền vị trí để mở phiên điểm danh");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_LOCATION);
            return;
        }
        fetchLocationAndCreateSession();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void fetchLocationAndCreateSession() {
        tvSessionStatus.setText("Đang lấy vị trí GPS...");
        LocationService locationService = new LocationService(this);
        locationService.getCurrentLocation(new LocationService.LocationCallback2() {
            @Override
            public void onLocationReady(android.location.Location loc) {
                runOnUiThread(() -> buildAndCreateSession(loc.getLatitude(), loc.getLongitude()));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    // Fallback: use 0,0 with a warning (teacher not moving)
                    Toast.makeText(SessionManagementActivity.this,
                            "⚠️ Không lấy GPS: " + message + "\nDùng vị trí mặc định", Toast.LENGTH_LONG).show();
                    buildAndCreateSession(0, 0);
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERM_LOCATION) return;

        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            // onResume có thể đã xử lý trước; dùng cờ để chỉ tạo phiên một lần.
            if (awaitingLocationPermission && currentSession == null) {
                awaitingLocationPermission = false;
                fetchLocationAndCreateSession();
            }
            return;
        }
        awaitingLocationPermission = false;

        // Bị từ chối: nếu còn có thể hỏi lại thì giải thích và mời cấp lại,
        // ngược lại (đã chọn "không hỏi lại") thì hướng dẫn mở Cài đặt.
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Cần quyền vị trí")
                    .setMessage("Mở phiên điểm danh cần quyền truy cập vị trí để xác định "
                            + "khu vực điểm danh (chống gian lận). Vui lòng cấp quyền.")
                    .setPositiveButton("Cấp quyền", (d, w) -> ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_LOCATION))
                    .setNegativeButton("Hủy", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Cần quyền vị trí")
                    .setMessage("Bạn đã từ chối quyền vị trí. Hãy bật quyền trong "
                            + "Cài đặt > Ứng dụng để mở phiên điểm danh.")
                    .setPositiveButton("Mở cài đặt", (d, w) -> openAppSettings())
                    .setNegativeButton("Hủy", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void buildAndCreateSession(double lat, double lng) {
        String sessionId = AttendanceUtils.generateSessionId(classId, shiftId);
        String token = AttendanceUtils.generateToken();

        Session session = new Session();
        session.setSessionId(sessionId);
        session.setClassId(classId);
        session.setShiftId(shiftId);
        session.setToken(token);
        session.setLatitude(lat);
        session.setLongitude(lng);
        session.setRadius(100); // 100 metres

        FirebaseRepository.OnSuccessListener<String> onCreated = id -> {
            currentSession = session;
            currentSession.setSessionId(id);
            displayQrCode(session);
            startRealtimeListener(id);
            tvSessionStatus.setText(makeupMode ? "ĐANG MỞ ĐIỂM DANH BÙ" : "ĐANG MỞ ĐIỂM DANH");
            displayLocation(lat, lng);
            bindLateMinutes();
        };
        FirebaseRepository.OnFailureListener onError = e ->
                Toast.makeText(this, "Lỗi tạo phiên: " + e.getMessage(), Toast.LENGTH_SHORT).show();

        if (makeupMode) {
            // startTime = now - lateAfterMinutes => mọi lượt điểm danh đều tính MUỘN.
            session.setLateAfterMinutes(makeupLateMinutes);
            repo.createMakeupSession(session, onCreated, onError);
        } else {
            repo.createSession(session, onCreated, onError);
        }
    }

    @SuppressWarnings("deprecation")
    private void displayLocation(double latitude, double longitude) {
        String coordinates = String.format(Locale.getDefault(),
                "Vị trí: %.5f, %.5f  (bán kính 100m)", latitude, longitude);
        tvShiftInfo.setText(coordinates);

        // A fallback GPS location (0,0) cannot be meaningfully reverse-geocoded.
        if ((latitude == 0 && longitude == 0) || !Geocoder.isPresent()) return;

        geocodingExecutor.execute(() -> {
            try {
                List<Address> addresses = new Geocoder(this, Locale.getDefault())
                        .getFromLocation(latitude, longitude, 1);
                if (addresses == null || addresses.isEmpty()) return;

                Address address = addresses.get(0);
                String city = address.getLocality();
                if (city == null || city.isEmpty()) city = address.getSubAdminArea();
                if (city == null || city.isEmpty()) city = address.getAdminArea();
                String country = address.getCountryName();

                if ((city == null || city.isEmpty()) && (country == null || country.isEmpty())) {
                    return;
                }
                String place = city == null || city.isEmpty() ? country
                        : country == null || country.isEmpty() ? city
                        : city + ", " + country;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        tvShiftInfo.setText(coordinates + "\n" + place);
                    }
                });
            } catch (IOException ignored) {
                // Keep showing coordinates when the device has no geocoding service or network.
            }
        });
    }

    private void displayQrCode(Session session) {
        try {
            JSONObject qrData = new JSONObject();
            qrData.put("sessionId", session.getSessionId());
            qrData.put("token", session.getToken());
            qrData.put("classId", classId);

            Bitmap qrBitmap = AttendanceUtils.generateQRCode(qrData.toString(), 600);
            if (qrBitmap != null) ivQrCode.setImageBitmap(qrBitmap);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void refreshQrCode() {
        if (currentSession == null) return;
        String newToken = AttendanceUtils.generateToken();
        btnRefreshQr.setEnabled(false);
        repo.updateSessionToken(currentSession.getSessionId(), newToken,
                unused -> runOnUiThread(() -> {
                    currentSession.setToken(newToken);
                    displayQrCode(currentSession);
                    btnRefreshQr.setEnabled(true);
                    Toast.makeText(this, "Mã QR đã được làm mới", Toast.LENGTH_SHORT).show();
                }),
                e -> runOnUiThread(() -> {
                    btnRefreshQr.setEnabled(true);
                    Toast.makeText(this, "Lỗi làm mới QR: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                })
        );
    }

    private void startRealtimeListener(String sessionId) {
        if (attendanceListener != null) attendanceListener.remove();
        attendanceListener = repo.listenToSessionAttendance(sessionId,
                list -> {
                    attendanceList.clear();
                    attendanceList.addAll(list);
                    adapter.notifyDataSetChanged();
                    updateAttendanceCount();
                },
                e -> Toast.makeText(this, "Không thể cập nhật điểm danh: " + e.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    private void updateAttendanceCount() {
        tvAttendanceCount.setText(String.format(Locale.getDefault(),
                "Đã điểm danh: %d/%d sinh viên", attendanceList.size(), totalStudents));
    }

    private void confirmClose() {
        // Giáo viên bắt buộc nhập nội dung buổi học trước khi đóng phiên.
        final EditText input = new EditText(this);
        input.setHint("Nội dung buổi học, vd: Buổi 8 - Giao thức TCP/IP");
        input.setSingleLine(true);
        if (currentSession != null && currentSession.getContent() != null) {
            input.setText(currentSession.getContent());
        }

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Đóng phiên điểm danh")
                .setMessage("Nhập nội dung buổi học trước khi đóng phiên:")
                .setView(container)
                .setPositiveButton("Đóng phiên", null)
                .setNegativeButton("Hủy", null)
                .create();

        // Tự kiểm tra rỗng mà không đóng dialog (override sau khi show).
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String content = input.getText().toString().trim();
                if (content.isEmpty()) {
                    input.setError("Vui lòng nhập nội dung buổi học");
                    return;
                }
                dialog.dismiss();
                closeSession(content);
            });
        });
        dialog.show();
    }

    /** Xác nhận đóng phiên bù — không yêu cầu nội dung, giữ nguyên nội dung buổi gốc. */
    private void confirmCloseMakeup() {
        new AlertDialog.Builder(this)
                .setTitle("Đóng phiên điểm danh bù")
                .setMessage("Đóng phiên điểm danh bù này?")
                .setPositiveButton("Đóng phiên", (d, which) -> closeSession(null))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void closeSession(String content) {
        if (currentSession == null) {
            finish();
            return;
        }
        if (content != null) currentSession.setContent(content);
        btnCloseSession.setEnabled(false);
        btnRefreshQr.setEnabled(false);
        tvSessionStatus.setText("ĐANG ĐÓNG PHIÊN...");
        repo.closeSession(currentSession.getSessionId(), shiftId, content,
                unused -> {
                    if (attendanceListener != null) { attendanceListener.remove(); attendanceListener = null; }
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_SHIFT_ID, shiftId));
                    Toast.makeText(this, "Đã đóng phiên điểm danh", Toast.LENGTH_SHORT).show();
                    // Phiên bù mở từ màn danh sách điểm danh → chỉ cần đóng để quay lại
                    // (màn đó tự làm mới ở onResume); phiên thường thì mở màn kết quả.
                    if (!makeupMode) openAttendanceResult();
                    finish();
                },
                e -> {
                    btnCloseSession.setEnabled(true);
                    btnRefreshQr.setEnabled(true);
                    tvSessionStatus.setText("ĐÓNG PHIÊN THẤT BẠI");
                    Toast.makeText(this, "Lỗi đóng phiên: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
        );
    }

    // Sau khi đóng phiên, mở luôn màn kết quả điểm danh của buổi học này
    // (danh sách đã/chưa điểm danh) thay vì thoát về màn buổi học.
    private void openAttendanceResult() {
        Intent intent = new Intent(this, ShiftAttendanceListActivity.class);
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_ID, shiftId);
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_NAME, className);
        if (currentShift != null) {
            String title = currentShift.getTitle() != null && !currentShift.getTitle().isEmpty()
                    ? currentShift.getTitle() : className;
            intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TITLE, title);
            intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TIME,
                    currentShift.getDayOfWeekDisplay() + "  "
                            + currentShift.getStartAt() + " - " + currentShift.getEndAt());
        }
        if (currentSession != null && currentSession.getContent() != null) {
            intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_CONTENT,
                    currentSession.getContent());
        }
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (attendanceListener != null) attendanceListener.remove();
        geocodingExecutor.shutdownNow();
    }
}
