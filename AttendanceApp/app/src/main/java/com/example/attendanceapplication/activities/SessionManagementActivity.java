package com.example.attendanceapplication.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

    private ImageView ivQrCode;
    private TextView tvShiftInfo, tvAttendanceCount, tvSessionStatus;
    private Button btnRefreshQr, btnCloseSession;
    private RecyclerView rvAttendance;

    private String shiftId, classId, className;
    private Session currentSession;
    private RealtimeAttendanceAdapter adapter;
    private ListenerRegistration attendanceListener;
    private List<Attendance> attendanceList = new ArrayList<>();
    private int totalStudents = 0;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    private final ExecutorService geocodingExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_management);

        shiftId   = getIntent().getStringExtra(EXTRA_SHIFT_ID);
        classId   = getIntent().getStringExtra(EXTRA_CLASS_ID);
        className = getIntent().getStringExtra(EXTRA_CLASS_NAME);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Điểm danh - " + className);
        }
        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon != null) {
            navIcon.setTint(ContextCompat.getColor(this, R.color.white));
        }
        initViews();
        setupRecyclerView();
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

    private void initViews() {
        ivQrCode          = findViewById(R.id.iv_qr_code);
        tvShiftInfo       = findViewById(R.id.tv_shift_info);
        tvAttendanceCount = findViewById(R.id.tv_attendance_count);
        tvSessionStatus   = findViewById(R.id.tv_session_status);
        btnRefreshQr      = findViewById(R.id.btn_refresh_qr);
        btnCloseSession   = findViewById(R.id.btn_close_session);
        rvAttendance      = findViewById(R.id.rv_attendance);

        btnRefreshQr.setOnClickListener(v -> refreshQrCode());
        btnCloseSession.setOnClickListener(v -> confirmClose());
    }

    private void setupRecyclerView() {
        adapter = new RealtimeAttendanceAdapter(attendanceList);
        rvAttendance.setLayoutManager(new LinearLayoutManager(this));
        rvAttendance.setAdapter(adapter);
    }

    private void createOrLoadSession() {
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

        repo.createSession(session,
                id -> {
                    currentSession = session;
                    currentSession.setSessionId(id);
                    displayQrCode(session);
                    startRealtimeListener(id);
                    tvSessionStatus.setText("ĐANG MỞ ĐIỂM DANH");
                    displayLocation(lat, lng);
                },
                e -> Toast.makeText(this, "Lỗi tạo phiên: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
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
        currentSession.setToken(AttendanceUtils.generateToken());
        displayQrCode(currentSession);
        Toast.makeText(this, "Mã QR đã được làm mới", Toast.LENGTH_SHORT).show();
    }

    private void startRealtimeListener(String sessionId) {
        attendanceListener = repo.listenToSessionAttendance(sessionId,
                list -> {
                    attendanceList.clear();
                    attendanceList.addAll(list);
                    adapter.notifyDataSetChanged();
                    tvAttendanceCount.setText(String.format("Đã điểm danh: %d/%d sinh viên",
                            list.size(), totalStudents));
                }
        );
    }

    private void confirmClose() {
        new AlertDialog.Builder(this)
                .setTitle("Đóng phiên điểm danh")
                .setMessage("Bạn có chắc muốn đóng phiên điểm danh này?")
                .setPositiveButton("Đóng phiên", (d, w) -> closeSession())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void closeSession() {
        if (currentSession == null) {
            finish();
            return;
        }
        btnCloseSession.setEnabled(false);
        btnRefreshQr.setEnabled(false);
        tvSessionStatus.setText("ĐANG ĐÓNG PHIÊN...");
        repo.closeSession(currentSession.getSessionId(), shiftId,
                unused -> {
                    if (attendanceListener != null) attendanceListener.remove();
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_SHIFT_ID, shiftId));
                    Toast.makeText(this, "Đã đóng phiên điểm danh", Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (attendanceListener != null) attendanceListener.remove();
        geocodingExecutor.shutdownNow();
    }
}
