package com.example.attendanceapplication.activities;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.attendanceapplication.R;
import com.example.attendanceapplication.utils.AttendanceUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AttendanceResultActivity extends AppCompatActivity {

    public static final String EXTRA_SUCCESS  = "success";
    public static final String EXTRA_DISTANCE = "distance";
    public static final String EXTRA_MESSAGE  = "message";
    public static final String EXTRA_LATE     = "late";
    public static final String EXTRA_BSSID_WARNING = "bssidWarning";
    public static final String EXTRA_BSSID_NOTE    = "bssidNote";

    private static final long AUTO_CLOSE_MS = 3000;
    // Giữ màn hình lâu hơn để sinh viên kịp đọc cảnh báo Wi-Fi.
    private static final long AUTO_CLOSE_WARNING_MS = 6000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_result);

        boolean success  = getIntent().getBooleanExtra(EXTRA_SUCCESS, false);
        float distance   = getIntent().getFloatExtra(EXTRA_DISTANCE, 0f);
        String message   = getIntent().getStringExtra(EXTRA_MESSAGE);
        boolean isLate   = getIntent().getBooleanExtra(EXTRA_LATE, false);
        boolean bssidWarning = getIntent().getBooleanExtra(EXTRA_BSSID_WARNING, false);
        String bssidNote = getIntent().getStringExtra(EXTRA_BSSID_NOTE);

        LottieAnimationView lottieView = findViewById(R.id.lottie_result);
        TextView tvTitle    = findViewById(R.id.tv_result_title);
        TextView tvTime     = findViewById(R.id.tv_result_time);
        TextView tvDistance = findViewById(R.id.tv_result_distance);
        TextView tvMessage  = findViewById(R.id.tv_result_message);
        TextView tvBssidWarning = findViewById(R.id.tv_result_bssid_warning);

        lottieView.setFailureListener(e -> {
            lottieView.cancelAnimation();
            lottieView.setVisibility(View.GONE);
        });

        if (success) {
            lottieView.setAnimation("success_checkmark.json");
            tvTime.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
            tvDistance.setText("Bạn cách lớp " + AttendanceUtils.formatDistance(distance));
            if (isLate) {
                tvTitle.setText("ĐIỂM DANH THÀNH CÔNG (ĐI MUỘN)");
                tvTitle.setTextColor(getColor(R.color.warning_yellow));
                tvMessage.setText("Bạn đã điểm danh muộn so với giờ mở lớp");
            } else {
                tvTitle.setText("ĐIỂM DANH THÀNH CÔNG");
                tvTitle.setTextColor(getColor(R.color.accent_green));
            }
            if (bssidWarning) {
                tvBssidWarning.setText("⚠️ Cảnh báo Wi-Fi: "
                        + (bssidNote != null ? bssidNote : "không trùng Wi-Fi của lớp")
                        + ".\nBản ghi vẫn được lưu nhưng bị đánh dấu để giảng viên kiểm tra.");
                tvBssidWarning.setVisibility(View.VISIBLE);
            }
        } else {
            lottieView.setAnimation("error_cross.json");
            tvTitle.setText("ĐIỂM DANH THẤT BẠI");
            tvTitle.setTextColor(getColor(R.color.error_red));
            tvMessage.setText(message != null ? message : "Vui lòng thử lại");
        }

        lottieView.playAnimation();

        // Auto-close
        new Handler().postDelayed(this::finish,
                success && bssidWarning ? AUTO_CLOSE_WARNING_MS : AUTO_CLOSE_MS);
    }
}
