package com.example.attendanceapplication.activities;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.databinding.ActivityAttendanceReportPreviewBinding;
import com.example.attendanceapplication.models.AttendanceReport;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.report.AttendanceReportXlsxExporter;
import com.example.attendanceapplication.viewmodels.AttendanceReportViewModel;
import com.example.attendanceapplication.viewmodels.AttendanceReportViewModel.ExportResult;
import com.example.attendanceapplication.viewmodels.AttendanceReportViewModel.State;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;

/**
 * Xem trước bảng thống kê điểm danh của lớp (WebView) và xuất / chia sẻ file Excel.
 * Chỉ giảng viên của lớp được xem — kiểm tra trong {@link
 * com.example.attendanceapplication.utils.report.AttendanceReportBuilder} khi tải dữ liệu.
 */
public class AttendanceReportPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID = "classId";
    public static final String EXTRA_CLASS_NAME = "className";
    private static final String KEY_PENDING_FILE_NAME = "pendingFileName";

    private ActivityAttendanceReportPreviewBinding binding;
    private AttendanceReportViewModel viewModel;
    private String loadedHtml;        // HTML đang hiển thị trong WebView của instance này
    private String pendingFileName;   // tên file đang chờ người dùng chọn nơi lưu

    private final ActivityResultLauncher<String> createDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(AttendanceReportXlsxExporter.MIME_TYPE),
            uri -> {
                if (uri != null && pendingFileName != null) viewModel.exportToUri(uri, pendingFileName);
            });

    public static void start(Context context, String classId, String className) {
        Intent intent = new Intent(context, AttendanceReportPreviewActivity.class);
        intent.putExtra(EXTRA_CLASS_ID, classId);
        intent.putExtra(EXTRA_CLASS_NAME, className);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAttendanceReportPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String classId = getIntent().getStringExtra(EXTRA_CLASS_ID);
        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        FirebaseUser user = FirebaseRepository.getInstance().getCurrentUser();
        if (classId == null || user == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            pendingFileName = savedInstanceState.getString(KEY_PENDING_FILE_NAME);
        }

        setupToolbar(className);
        setupWebView();
        binding.btnRetry.setOnClickListener(v -> viewModel.reload());
        binding.btnExport.setOnClickListener(v -> onExportClicked());
        binding.btnShare.setOnClickListener(v -> viewModel.exportForShare());

        viewModel = new ViewModelProvider(this).get(AttendanceReportViewModel.class);
        viewModel.getState().observe(this, this::render);
        viewModel.isExporting().observe(this, busy -> updateButtons());
        viewModel.getExportResult().observe(this, this::onExportResult);
        viewModel.loadIfNeeded(classId, user.getUid());
    }

    private void setupToolbar(String className) {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.report_title);
            getSupportActionBar().setSubtitle(className);
        }
        Drawable navIcon = binding.toolbar.getNavigationIcon();
        if (navIcon != null) navIcon.setTint(ContextCompat.getColor(this, R.color.white));
    }

    private void setupWebView() {
        WebSettings s = binding.webView.getSettings();
        s.setJavaScriptEnabled(false);       // HTML tĩnh, không cần JS
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        // Ban đầu thu nhỏ để thấy toàn bảng, pinch-zoom để xem chi tiết
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
    }

    private void render(State state) {
        boolean loading = state.status == AttendanceReportViewModel.Status.LOADING;
        boolean ready = state.status == AttendanceReportViewModel.Status.READY;
        binding.layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.webView.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
        binding.layoutMessage.setVisibility(loading || ready ? View.GONE : View.VISIBLE);
        binding.btnRetry.setVisibility(
                state.status == AttendanceReportViewModel.Status.ERROR ? View.VISIBLE : View.GONE);

        AttendanceReport report = state.report;
        if (report != null && getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(report.getClassDisplayName());
        }
        int ongoing = report == null ? 0 : report.getOngoingShiftCount();
        binding.tvOngoingNotice.setVisibility(ongoing > 0 ? View.VISIBLE : View.GONE);
        if (ongoing > 0) binding.tvOngoingNotice.setText(getString(R.string.report_ongoing_notice, ongoing));

        switch (state.status) {
            case READY:
                if (!state.html.equals(loadedHtml)) {
                    loadedHtml = state.html;
                    binding.webView.loadDataWithBaseURL(null, state.html, "text/html", "UTF-8", null);
                }
                break;
            case EMPTY:
                binding.ivMessage.setImageResource(R.drawable.ic_calendar_empty);
                binding.tvMessage.setText(report != null && report.getColumns().isEmpty()
                        ? R.string.report_empty : R.string.report_no_students);
                break;
            case FORBIDDEN:
                binding.ivMessage.setImageResource(R.drawable.ic_lock);
                binding.tvMessage.setText(state.error);
                break;
            case ERROR:
                binding.ivMessage.setImageResource(R.drawable.ic_info_circle);
                binding.tvMessage.setText(getString(R.string.report_error,
                        state.error == null ? "" : state.error));
                break;
            default:
                break;
        }
        updateButtons();
    }

    /** Chỉ cho xuất khi bảng đã sẵn sàng và không có lần xuất nào đang chạy. */
    private void updateButtons() {
        State state = viewModel.getState().getValue();
        boolean busy = Boolean.TRUE.equals(viewModel.isExporting().getValue());
        boolean enabled = !busy && state != null
                && state.status == AttendanceReportViewModel.Status.READY;
        setButtonEnabled(binding.btnExport, enabled);
        setButtonEnabled(binding.btnShare, enabled);
        binding.btnExport.setText(busy ? R.string.report_exporting : R.string.report_export_excel);
    }

    private static void setButtonEnabled(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.5f);
    }

    private void onExportClicked() {
        String fileName = viewModel.newFileName();
        if (fileName == null) return;
        pendingFileName = fileName;
        try {
            // SAF: người dùng chọn nơi lưu (mặc định thường là Download), không cần quyền bộ nhớ
            createDocument.launch(fileName);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.report_export_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onExportResult(ExportResult result) {
        if (result == null || !result.consume()) return;
        if (result.error != null) {
            String msg = result.error.getMessage() == null
                    ? result.error.getClass().getSimpleName() : result.error.getMessage();
            Snackbar.make(binding.getRoot(), getString(R.string.report_export_failed, msg),
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        if (result.forShare) {
            shareFile(result.sharedFile, result.fileName);
        } else {
            pendingFileName = null;
            Snackbar.make(binding.getRoot(), getString(R.string.report_saved, result.fileName),
                            Snackbar.LENGTH_LONG)
                    .setAction(R.string.report_open, v -> openFile(result.savedUri))
                    .show();
        }
    }

    private void shareFile(File file, String fileName) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(AttendanceReportXlsxExporter.MIME_TYPE);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, fileName);
        send.setClipData(ClipData.newRawUri(fileName, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.report_share_chooser)));
    }

    private void openFile(Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, AttendanceReportXlsxExporter.MIME_TYPE);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(view);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.report_no_excel_app, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PENDING_FILE_NAME, pendingFileName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (binding != null) binding.webView.destroy();
        super.onDestroy();
    }
}
