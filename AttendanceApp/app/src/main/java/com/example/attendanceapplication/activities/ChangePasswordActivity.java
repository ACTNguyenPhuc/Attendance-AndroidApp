package com.example.attendanceapplication.activities;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextInputLayout tilOldPassword, tilNewPassword, tilConfirmPassword;
    private TextInputEditText etOldPassword, etNewPassword, etConfirmPassword;
    private MaterialButton btnChangePassword;
    private View overlay;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Đổi mật khẩu");
        }
        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon != null) {
            navIcon.setTint(ContextCompat.getColor(this, R.color.white));
        }

        tilOldPassword     = findViewById(R.id.til_old_password);
        tilNewPassword     = findViewById(R.id.til_new_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);
        etOldPassword      = findViewById(R.id.et_old_password);
        etNewPassword      = findViewById(R.id.et_new_password);
        etConfirmPassword  = findViewById(R.id.et_confirm_password);
        btnChangePassword  = findViewById(R.id.btn_change_password);
        overlay            = findViewById(R.id.loading_overlay);

        btnChangePassword.setOnClickListener(v -> attemptChangePassword());
    }

    private void attemptChangePassword() {
        String oldPassword     = etOldPassword.getText() != null ? etOldPassword.getText().toString().trim() : "";
        String newPassword     = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        tilOldPassword.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);

        if (oldPassword.isEmpty()) {
            tilOldPassword.setError("Vui lòng nhập mật khẩu hiện tại");
            return;
        }
        if (newPassword.length() < 6) {
            tilNewPassword.setError(getString(R.string.error_password_short));
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            tilConfirmPassword.setError(getString(R.string.error_password_mismatch));
            return;
        }

        showLoading(true);

        repo.changePassword(oldPassword, newPassword,
                aVoid -> {
                    showLoading(false);
                    Snackbar.make(findViewById(android.R.id.content),
                            "Đổi mật khẩu thành công", Snackbar.LENGTH_LONG).show();
                    finish();
                },
                e -> {
                    showLoading(false);
                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        tilOldPassword.setError("Mật khẩu hiện tại không đúng");
                    } else if (e instanceof FirebaseAuthWeakPasswordException) {
                        tilNewPassword.setError(getString(R.string.error_password_short));
                    } else {
                        showError("Đổi mật khẩu thất bại: " + e.getMessage());
                    }
                }
        );
    }

    private void showLoading(boolean show) {
        overlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnChangePassword.setEnabled(!show);
    }

    private void showError(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
