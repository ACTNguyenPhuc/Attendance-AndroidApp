package com.example.attendanceapplication.fragments.shared;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.ChangePasswordActivity;
import com.example.attendanceapplication.activities.LoginActivity;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileFragment extends Fragment {

    private TextView tvName, tvRole;
    private Button btnLogout;
    private User currentUser;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_profile, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvName   = view.findViewById(R.id.tv_name);
        tvRole   = view.findViewById(R.id.tv_role);
        btnLogout = view.findViewById(R.id.btn_logout);

        btnLogout.setOnClickListener(v -> showLogoutDialog());
        view.findViewById(R.id.row_personal_info).setOnClickListener(v -> showPersonalInfoDialog());
        view.findViewById(R.id.row_personal_qr).setOnClickListener(v -> showPersonalQrDialog());
        view.findViewById(R.id.row_help).setOnClickListener(v -> showHelpDialog());
        view.findViewById(R.id.row_about).setOnClickListener(v -> showAboutDialog());
        loadProfile();
    }

    private void loadProfile() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getUserProfile(uid,
                user -> {
                    if (!isAdded() || getActivity() == null) return;
                    currentUser = user;
                    requireActivity().runOnUiThread(() -> {
                        tvName.setText(user.getName());
                        tvRole.setText("teacher".equals(user.getRole()) ? "GIẢNG VIÊN" : "SINH VIÊN");
                    });
                },
                e -> {}
        );
    }

    private void showPersonalInfoDialog() {
        if (currentUser == null || !isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Thông tin cá nhân")
                .setMessage("Họ và tên: " + currentUser.getName()
                        + "\nEmail: " + currentUser.getEmail()
                        + "\nMã số: " + currentUser.getStudentCode()
                        + "\nVai trò: " + ("teacher".equals(currentUser.getRole()) ? "Giảng viên" : "Sinh viên"))
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showPersonalQrDialog() {
        if (currentUser == null || !isAdded()) return;
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_class_qr);

        TextView tvTitle = dialog.findViewById(R.id.tv_title);
        ImageView ivQr = dialog.findViewById(R.id.iv_qr);
        TextView tvLabel = dialog.findViewById(R.id.tv_label);
        TextView tvClose = dialog.findViewById(R.id.tv_close);

        if (tvTitle != null) tvTitle.setText("Mã QR cá nhân");
        if (tvLabel != null) tvLabel.setText("Mã định danh của bạn:\n" + currentUser.getStudentCode());

        Bitmap qr = AttendanceUtils.generateQRCode(currentUser.getUid(), 600);
        if (ivQr != null && qr != null) ivQr.setImageBitmap(qr);
        if (tvClose != null) tvClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showHelpDialog() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Trợ giúp")
                .setMessage("Nếu bạn gặp sự cố khi điểm danh hoặc sử dụng ứng dụng, vui lòng liên hệ giảng viên hoặc bộ phận hỗ trợ kỹ thuật của nhà trường.")
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showAboutDialog() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Về ứng dụng")
                .setMessage(getString(R.string.app_name) + "\nPhiên bản 1.0.0\n\n" + getString(R.string.app_tagline))
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất?")
                .setPositiveButton("Đăng xuất", (d, w) -> {
                    repo.signOut();
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
