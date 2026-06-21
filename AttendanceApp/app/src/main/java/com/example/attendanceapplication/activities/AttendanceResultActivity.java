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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_result);

        boolean success  = getIntent().getBooleanExtra(EXTRA_SUCCESS, false);
        float distance   = getIntent().getFloatExtra(EXTRA_DISTANCE, 0f);
        String message   = getIntent().getStringExtra(EXTRA_MESSAGE);
        boolean isLate   = getIntent().getBooleanExtra(EXTRA_LATE, false);

        LottieAnimationView lottieView = findViewById(R.id.lottie_result);
        TextView tvTitle    = findViewById(R.id.tv_result_title);
        TextView tvTime     = findViewById(R.id.tv_result_time);
        TextView tvDistance = findViewById(R.id.tv_result_distance);
        TextView tvMessage  = findViewById(R.id.tv_result_message);

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
        } else {
            lottieView.setAnimation("error_cross.json");
            tvTitle.setText("ĐIỂM DANH THẤT BẠI");
            tvTitle.setTextColor(getColor(R.color.error_red));
            tvMessage.setText(message != null ? message : "Vui lòng thử lại");
        }

        lottieView.playAnimation();

        // Auto-close after 3 seconds
        new Handler().postDelayed(this::finish, 3000);
    }
}
