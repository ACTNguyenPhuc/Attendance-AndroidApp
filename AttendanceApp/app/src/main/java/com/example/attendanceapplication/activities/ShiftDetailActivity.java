package com.example.attendanceapplication.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Session;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ShiftDetailActivity extends AppCompatActivity {

    public static final String EXTRA_SHIFT_ID   = "shiftId";
    public static final String EXTRA_CLASS_ID   = "classId";
    public static final String EXTRA_CLASS_NAME = "className";

    private TextView tvDate, tvDayTime, tvRoom, tvAttStatus, tvInfoText;
    private TextView tvCheckinTime, tvPunctuality, tvDistance;
    private View infoBanner;   // LinearLayout container
    private View attDetails;   // LinearLayout holding check-in time + punctuality
    private Button btnAttend;

    private String shiftId, classId, className;
    private final FirebaseRepository repo = FirebaseRepository.getInstance();
    // Latest realtime shift, kept so onResume can re-check the attendance state
    // (e.g. right after the student checks in) without re-subscribing the LiveData.
    private Shift currentShift;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shift_detail);

        shiftId   = getIntent().getStringExtra(EXTRA_SHIFT_ID);
        classId   = getIntent().getStringExtra(EXTRA_CLASS_ID);
        className = getIntent().getStringExtra(EXTRA_CLASS_NAME);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
            getSupportActionBar().setTitle("Chi tiết buổi học");
        }

        initViews();
        checkAttendanceStatus();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        tvDate      = findViewById(R.id.tv_date);
        tvDayTime   = findViewById(R.id.tv_day_time);
        tvRoom      = findViewById(R.id.tv_room);
        tvAttStatus = findViewById(R.id.tv_att_status);
        infoBanner  = findViewById(R.id.ll_info_banner); // LinearLayout wrapper
        tvInfoText  = findViewById(R.id.tv_info_text);
        attDetails    = findViewById(R.id.ll_att_details);
        tvCheckinTime = findViewById(R.id.tv_checkin_time);
        tvPunctuality = findViewById(R.id.tv_punctuality);
        tvDistance    = findViewById(R.id.tv_distance);
        btnAttend   = findViewById(R.id.btn_attend);

        tvDate.setText(shiftId);
    }

    private void checkAttendanceStatus() {
        // Get shift to check session (realtime). The attendance check is
        // re-run separately so onResume can refresh it without re-observing.
        repo.getClassShifts(classId).observe(this, shifts -> {
            for (Shift shift : shifts) {
                if (shift.getShiftId().equals(shiftId)) {
                    currentShift = shift;
                    tvDate.setText(shift.getDate());
                    tvDayTime.setText(shift.getDayOfWeekDisplay() + "  " +
                            shift.getStartAt() + " - " + shift.getEndAt());
                    tvRoom.setText(shift.getRoom() != null ? "Phòng: " + shift.getRoom() : "");
                    evaluateAttendance(shift);
                    break;
                }
            }
        });
    }

    private void evaluateAttendance(Shift shift) {
        String sessionId = shift.getAttendanceSessionId();
        // The shift has been opened for attendance at least once iff a session exists.
        boolean hasSession = sessionId != null;

        String studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (hasSession) {
            // Attendance currently open: let the student check in if they haven't.
            repo.getStudentAttendanceForSession(studentId, sessionId,
                    att -> runOnUiThread(() -> {
                        if (att != null) showAttended(att);
                        else showCanAttend(sessionId);
                    }));
        } else if (hasSession || Shift.STATUS_COMPLETED.equals(shift.getStatus())) {
            // Attendance was opened then closed (session ended).
            repo.getStudentAttendanceForSession(studentId, sessionId,
                    att -> runOnUiThread(() -> {
                        if (att != null) showAttended(att);
                        else showSessionEnded();
                    }));
        } else {
            // Teacher hasn't opened attendance yet.
            showSessionNotOpen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentShift != null) evaluateAttendance(currentShift);
    }

    private void showAttended(Attendance att) {
        tvAttStatus.setText("✅ ĐÃ ĐIỂM DANH");
        tvAttStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
        btnAttend.setEnabled(false);
        btnAttend.setText("✓ Đã điểm danh");
        infoBanner.setVisibility(View.GONE);

        // Show check-in details + punctuality (on time / late).
        if (att.getCheckinTime() != null) {
            String time = new SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.getDefault())
                    .format(att.getCheckinTime().toDate());
            tvCheckinTime.setText(time);
        } else {
            tvCheckinTime.setText("--");
        }

        tvDistance.setText(String.format(Locale.getDefault(), "%.0f m", att.getDistance()));

        if (Attendance.STATUS_LATE.equals(att.getStatus())) {
            tvPunctuality.setText("Muộn");
            tvPunctuality.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow));
        } else {
            tvPunctuality.setText("Đúng giờ");
            tvPunctuality.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
        }
        attDetails.setVisibility(View.VISIBLE);
    }

    private void showCanAttend(String sessionId) {
        tvAttStatus.setText("⏳ CHƯA ĐIỂM DANH");
        tvAttStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow));
        btnAttend.setEnabled(true);
        btnAttend.setText("📷 Điểm danh ngay");
        infoBanner.setVisibility(View.GONE);
        attDetails.setVisibility(View.GONE);
        btnAttend.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanAttendanceActivity.class);
            intent.putExtra("classId", classId);
            intent.putExtra("shiftId", shiftId);
            startActivity(intent);
        });
    }

    private void showSessionEnded() {
        tvAttStatus.setText("⛔ PHIÊN ĐIỂM DANH ĐÃ KẾT THÚC");
        tvAttStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        btnAttend.setEnabled(false);
        btnAttend.setText("Đã kết thúc");
        attDetails.setVisibility(View.GONE);
        infoBanner.setVisibility(View.VISIBLE);
        tvInfoText.setText("Phiên điểm danh đã kết thúc. Bạn không thể điểm danh buổi học này.");
    }

    private void showSessionNotOpen() {
        tvAttStatus.setText("🔒 Chưa mở điểm danh");
        tvAttStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        btnAttend.setEnabled(false);
        btnAttend.setText("Chờ giảng viên mở");
        attDetails.setVisibility(View.GONE);
        infoBanner.setVisibility(View.VISIBLE);
        tvInfoText.setText("Giảng viên chưa mở phiên điểm danh");
    }
}
