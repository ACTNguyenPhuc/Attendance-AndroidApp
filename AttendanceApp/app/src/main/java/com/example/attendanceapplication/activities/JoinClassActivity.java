package com.example.attendanceapplication.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.json.JSONObject;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class JoinClassActivity extends AppCompatActivity {

    private DecoratedBarcodeView barcodeView;
    private boolean isProcessing = false;
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_class);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tham gia lớp học");
        }

        barcodeView = findViewById(R.id.barcode_view);
        startScanner();
    }

    private void startScanner() {
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

    private void processQrResult(String qrContent) {
        try {
            JSONObject qrJson = new JSONObject(qrContent);
            String classId = qrJson.getString("classId");

            repo.getClassById(classId,
                    classModel -> showJoinConfirmation(classId, classModel.getClassName()),
                    e -> {
                        isProcessing = false;
                        Snackbar.make(barcodeView, "Không tìm thấy lớp học", Snackbar.LENGTH_LONG).show();
                    }
            );
        } catch (Exception e) {
            isProcessing = false;
            Toast.makeText(this, "Mã QR không đúng định dạng", Toast.LENGTH_SHORT).show();
        }
    }

    private void showJoinConfirmation(String classId, String className) {
        runOnUiThread(() -> {
            BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
            View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_join_class, null);
            bottomSheet.setContentView(sheetView);

            TextView tvClassName = sheetView.findViewById(R.id.tv_class_name);
            Button btnJoin = sheetView.findViewById(R.id.btn_join);
            Button btnCancel = sheetView.findViewById(R.id.btn_cancel);

            tvClassName.setText(className);

            btnJoin.setOnClickListener(v -> {
                String studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                repo.checkEnrollment(studentId, classId, alreadyEnrolled -> {
                    if (alreadyEnrolled) {
                        bottomSheet.dismiss();
                        Toast.makeText(this, "Bạn đã là thành viên lớp này", Toast.LENGTH_SHORT).show();
                        isProcessing = false;
                    } else {
                        repo.enrollStudent(studentId, classId,
                                aVoid -> {
                                    bottomSheet.dismiss();
                                    Toast.makeText(this, "Tham gia lớp học thành công!", Toast.LENGTH_SHORT).show();
                                    finish();
                                },
                                e -> {
                                    bottomSheet.dismiss();
                                    Toast.makeText(this, "Lỗi tham gia: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    isProcessing = false;
                                }
                        );
                    }
                });
            });

            btnCancel.setOnClickListener(v -> {
                bottomSheet.dismiss();
                isProcessing = false;
            });

            bottomSheet.setOnDismissListener(d -> isProcessing = false);
            bottomSheet.show();
        });
    }

    @Override
    protected void onResume() { super.onResume(); barcodeView.resume(); }

    @Override
    protected void onPause() { super.onPause(); barcodeView.pause(); }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
