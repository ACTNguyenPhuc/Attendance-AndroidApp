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

public class ShiftDetailActivity extends AppCompatActivity {

    public static final String EXTRA_SHIFT_ID   = "shiftId";
    public static final String EXTRA_CLASS_ID   = "classId";
    public static final String EXTRA_CLASS_NAME = "className";

    private TextView tvDate, tvDayTime, tvRoom, tvAttStatus, tvInfoText;
    private View infoBanner; // LinearLayout container
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
        if (shift.isAttendanceOpened() && shift.getAttendanceSessionId() != null) {
            String studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            repo.checkAlreadyAttended(studentId, shift.getAttendanceSessionId(),
                    attended -> runOnUiThread(() -> {
                        if (attended) {
                            showAlreadyAttended();
                        } else {
                            showCanAttend(shift.getAttendanceSessionId());
                        }
                    }));
        } else {
            showSessionNotOpen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentShift != null) evaluateAttendance(currentShift);
    }

    private void showAlreadyAttended() {
        tvAttStatus.setText("✅ ĐÃ ĐIỂM DANH");
        tvAttStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
        btnAttend.setEnabled(false);
        btnAttend.setText("✓ Đã điểm danh");
        infoBanner.setVisibility(View.GONE);
    }

    private void showCanAttend(String sessionId) {
        tvAttStatus.setText("⏳ CHƯA ĐIỂM DANH");
        tvAttStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow));
        btnAttend.setEnabled(true);
        btnAttend.setText("📷 Điểm danh ngay");
        infoBanner.setVisibility(View.GONE);
        btnAttend.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanAttendanceActivity.class);
            intent.putExtra("classId", classId);
            intent.putExtra("shiftId", shiftId);
            startActivity(intent);
        });
    }

    private void showSessionNotOpen() {
        tvAttStatus.setText("🔒 Chưa mở điểm danh");
        tvAttStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        btnAttend.setEnabled(false);
        btnAttend.setText("Chờ giảng viên mở");
        infoBanner.setVisibility(View.VISIBLE);
        tvInfoText.setText("Giảng viên chưa mở phiên điểm danh");
    }
}
