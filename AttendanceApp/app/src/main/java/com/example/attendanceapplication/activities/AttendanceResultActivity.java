package com.example.attendanceapplication.activities;

import android.os.Bundle;
import android.os.Handler;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_result);

        boolean success  = getIntent().getBooleanExtra(EXTRA_SUCCESS, false);
        float distance   = getIntent().getFloatExtra(EXTRA_DISTANCE, 0f);
        String message   = getIntent().getStringExtra(EXTRA_MESSAGE);

        LottieAnimationView lottieView = findViewById(R.id.lottie_result);
        TextView tvTitle    = findViewById(R.id.tv_result_title);
        TextView tvTime     = findViewById(R.id.tv_result_time);
        TextView tvDistance = findViewById(R.id.tv_result_distance);
        TextView tvMessage  = findViewById(R.id.tv_result_message);

        if (success) {
            lottieView.setAnimation("success_checkmark.json");
            tvTitle.setText("ĐIỂM DANH THÀNH CÔNG");
            tvTitle.setTextColor(getColor(R.color.accent_green));
            tvTime.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
            tvDistance.setText("Bạn cách lớp " + AttendanceUtils.formatDistance(distance));
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
