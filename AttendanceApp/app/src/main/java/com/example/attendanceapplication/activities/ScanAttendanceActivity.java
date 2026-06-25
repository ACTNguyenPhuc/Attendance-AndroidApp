package com.example.attendanceapplication.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Session;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.json.JSONObject;

public class ScanAttendanceActivity extends AppCompatActivity {

    private static final int PERM_CAMERA   = 100;
    private static final int PERM_LOCATION = 101;

    private DecoratedBarcodeView barcodeView;
    private TextView tvGpsStatus, tvStep1, tvStep2, tvStep3;
    private View processingOverlay, scannerContainer;

    private FusedLocationProviderClient locationClient;
    private boolean isProcessing = false;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_attendance);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        initViews();
        checkPermissions();
    }

    private void initViews() {
        barcodeView      = findViewById(R.id.barcode_view);
        tvGpsStatus      = findViewById(R.id.tv_gps_status);
        tvStep1          = findViewById(R.id.tv_step1);
        tvStep2          = findViewById(R.id.tv_step2);
        tvStep3          = findViewById(R.id.tv_step3);
        processingOverlay = findViewById(R.id.processing_overlay);
        scannerContainer  = findViewById(R.id.scanner_container);
    }

    private void checkPermissions() {
        boolean cameraOk   = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean locationOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!cameraOk || !locationOk) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERM_CAMERA);
        } else {
            startScanner();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_CAMERA) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) startScanner();
            else Toast.makeText(this, "Cần quyền Camera và GPS để điểm danh", Toast.LENGTH_LONG).show();
        }
    }

    private void startScanner() {
        getGpsAndUpdateStatus();
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (!isProcessing && result.getText() != null) {
                    isProcessing = true;
                    processQrResult(result.getText());
                }
            }
        });
    }

    private void getGpsAndUpdateStatus() {
        tvGpsStatus.setText("📍 Đang lấy vị trí...");
        tvGpsStatus.setTextColor(getColor(R.color.warning_yellow));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        tvGpsStatus.setText(String.format("✅ Vị trí sẵn sàng (±%.0fm)", location.getAccuracy()));
                        tvGpsStatus.setTextColor(getColor(R.color.accent_green));
                    } else {
                        tvGpsStatus.setText("⚠️ Không lấy được vị trí");
                    }
                });
    }

    private void processQrResult(String qrContent) {
        showProcessingStep(1, "✓ Đọc mã QR", true);
        showProcessingStep(2, "⟳ Đang lấy vị trí GPS...", false);

        try {
            JSONObject qrJson = new JSONObject(qrContent);
            String sessionId = qrJson.getString("sessionId");
            String token     = qrJson.getString("token");

            // Step 1: Validate session
            repo.getSession(sessionId,
                    session -> {
                        if (!session.isActive()) {
                            showError("Mã QR đã hết hiệu lực, liên hệ giảng viên");
                            return;
                        }
                        if (!token.equals(session.getToken())) {
                            showError("Mã QR không hợp lệ");
                            return;
                        }

                        String studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        // Chặn điểm danh trùng theo BUỔI HỌC (gồm cả phiên bù), không chỉ theo phiên.
                        repo.checkStudentAttendedShift(studentId, session.getShiftId(), alreadyDone -> {
                            if (alreadyDone) {
                                showError("Bạn đã điểm danh buổi học này rồi");
                                return;
                            }
                            // Anti-cheat: mỗi thiết bị chỉ điểm danh được 1 lần trong mỗi buổi học,
                            // chống việc dùng chung 1 máy để điểm danh hộ nhiều người.
                            String deviceId = Settings.Secure.getString(getContentResolver(),
                                    Settings.Secure.ANDROID_ID);
                            repo.checkDeviceUsedInShift(session.getShiftId(), deviceId, studentId,
                                    deviceUsed -> {
                                        if (deviceUsed) {
                                            showError("Thiết bị này đã được dùng để điểm danh "
                                                    + "cho buổi học này");
                                            return;
                                        }
                                        // Step 2: Get GPS
                                        getLocationAndVerify(session, studentId);
                                    });
                        });
                    },
                    e -> showError("Không tìm thấy phiên điểm danh")
            );
        } catch (Exception e) {
            showError("Mã QR không đúng định dạng");
        }
    }

    private void getLocationAndVerify(Session session, String studentId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        showError("Không lấy được vị trí GPS, thử lại");
                        return;
                    }
                    showProcessingStep(2, "✓ Vị trí GPS lấy thành công", true);
                    showProcessingStep(3, "⟳ Đang xác minh vị trí...", false);

                    double distance = AttendanceUtils.calculateDistance(
                            location.getLatitude(), location.getLongitude(),
                            session.getLatitude(), session.getLongitude()
                    );

                    if (!AttendanceUtils.isWithinRadius(distance, session.getRadius())) {
                        showError(String.format(
                                "Bạn không ở gần lớp học (cách %s)",
                                AttendanceUtils.formatDistance(distance)));
                        return;
                    }

                    showProcessingStep(3, "✓ Vị trí hợp lệ", true);
                    saveAttendance(session, studentId, location.getLatitude(),
                            location.getLongitude(), distance);
                })
                .addOnFailureListener(e -> showError("Lỗi lấy GPS: " + e.getMessage()));
    }

    private void saveAttendance(Session session, String studentId,
                                double lat, double lng, double distance) {
        String deviceId = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID);

        // Plan A: on-time if checked in within `lateAfterMinutes` of when the
        // teacher opened the session; otherwise marked late.
        boolean isLate = false;
        if (session.getStartTime() != null) {
            long elapsedMin = (System.currentTimeMillis()
                    - session.getStartTime().toDate().getTime()) / 60000L;
            isLate = elapsedMin > session.getLateAfterMinutes();
        }

        Attendance attendance = new Attendance();
        attendance.setStudentId(studentId);
        attendance.setSessionId(session.getSessionId());
        attendance.setShiftId(session.getShiftId());
        attendance.setClassId(session.getClassId());
        attendance.setLatitude(lat);
        attendance.setLongitude(lng);
        attendance.setDistance(distance);
        attendance.setStatus(isLate ? Attendance.STATUS_LATE : Attendance.STATUS_PRESENT);
        attendance.setDeviceId(deviceId);
        attendance.setFaceVerified(false);

        final boolean finalIsLate = isLate;
        // Gắn tên + mã sinh viên vào bản ghi để danh sách điểm danh hiển thị đúng.
        repo.getUserProfile(studentId,
                user -> {
                    if (user != null) {
                        attendance.setStudentName(user.getName());
                        attendance.setStudentCode(user.getStudentCode());
                    }
                    persistAttendance(attendance, distance, finalIsLate);
                },
                e -> persistAttendance(attendance, distance, finalIsLate)
        );
    }

    private void persistAttendance(Attendance attendance, double distance, boolean isLate) {
        repo.saveAttendance(attendance,
                aVoid -> {
                    // Navigate to success screen
                    Intent intent = new Intent(this, AttendanceResultActivity.class);
                    intent.putExtra(AttendanceResultActivity.EXTRA_SUCCESS, true);
                    intent.putExtra(AttendanceResultActivity.EXTRA_DISTANCE, (float) distance);
                    intent.putExtra(AttendanceResultActivity.EXTRA_LATE, isLate);
                    startActivity(intent);
                    finish();
                },
                e -> showError("Lỗi lưu điểm danh: " + e.getMessage())
        );
    }

    private void showProcessingStep(int step, String text, boolean done) {
        runOnUiThread(() -> {
            processingOverlay.setVisibility(View.VISIBLE);
            scannerContainer.setVisibility(View.GONE);
            TextView tvStep = step == 1 ? tvStep1 : (step == 2 ? tvStep2 : tvStep3);
            tvStep.setText(text);
            tvStep.setTextColor(done ? getColor(R.color.accent_green) : getColor(R.color.text_secondary));
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            isProcessing = false;
            processingOverlay.setVisibility(View.GONE);
            scannerContainer.setVisibility(View.VISIBLE);
            Snackbar.make(barcodeView, message, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
