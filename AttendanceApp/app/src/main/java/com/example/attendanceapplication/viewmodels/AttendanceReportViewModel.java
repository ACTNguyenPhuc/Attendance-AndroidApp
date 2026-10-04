package com.example.attendanceapplication.viewmodels;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.attendanceapplication.models.AttendanceReport;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.report.AttendanceReportBuilder;
import com.example.attendanceapplication.utils.report.AttendanceReportHtml;
import com.example.attendanceapplication.utils.report.AttendanceReportXlsxExporter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Giữ {@link AttendanceReport} của màn xem trước qua thay đổi cấu hình (xoay màn hình)
 * để không phải query lại; cùng một instance được dùng cho xem trước và xuất file.
 */
public class AttendanceReportViewModel extends AndroidViewModel {

    public enum Status { LOADING, READY, EMPTY, ERROR, FORBIDDEN }

    /** Trạng thái màn hình; {@code report}/{@code html} chỉ có khi READY hoặc EMPTY. */
    public static final class State {
        public final Status status;
        public final AttendanceReport report;
        public final String html;
        public final String error;

        State(Status status, AttendanceReport report, String html, String error) {
            this.status = status;
            this.report = report;
            this.html = html;
            this.error = error;
        }
    }

    /** Kết quả một lần xuất file — chỉ được xử lý một lần (không phát lại khi xoay màn hình). */
    public static final class ExportResult {
        public final boolean forShare;
        public final Uri savedUri;      // khi lưu qua SAF
        public final File sharedFile;   // khi chia sẻ (cacheDir/reports)
        public final String fileName;
        public final Exception error;
        private boolean handled;

        ExportResult(boolean forShare, Uri savedUri, File sharedFile, String fileName, Exception error) {
            this.forShare = forShare;
            this.savedUri = savedUri;
            this.sharedFile = sharedFile;
            this.fileName = fileName;
            this.error = error;
        }

        /** @return true nếu lần đầu được lấy ra xử lý. */
        public boolean consume() {
            if (handled) return false;
            handled = true;
            return true;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> exporting = new MutableLiveData<>(false);
    private final MutableLiveData<ExportResult> exportResult = new MutableLiveData<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Uri pendingUri;
    private String pendingUriFileName;
    private String classId;
    private String requesterUid;

    public AttendanceReportViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<State> getState() { return state; }
    public LiveData<Boolean> isExporting() { return exporting; }
    public LiveData<ExportResult> getExportResult() { return exportResult; }

    /** Chỉ tải lần đầu; sau khi xoay màn hình thì dùng lại dữ liệu đang giữ. */
    public void loadIfNeeded(String classId, String requesterUid) {
        if (state.getValue() != null && classId.equals(this.classId)) return;
        this.classId = classId;
        this.requesterUid = requesterUid;
        reload();
    }

    public void reload() {
        state.setValue(new State(Status.LOADING, null, null, null));
        new AttendanceReportBuilder(FirebaseRepository.getInstance(), this::runInBackground)
                .build(classId, requesterUid, new AttendanceReportBuilder.Callback() {
                    @Override
                    public void onSuccess(AttendanceReport report) {
                        if (report.isEmpty()) {
                            setState(new State(Status.EMPTY, report, null, null));
                            return;
                        }
                        // Sinh HTML ở background — lớp lớn có thể dài vài trăm KB
                        runInBackground(() -> {
                            String html = AttendanceReportHtml.build(report);
                            mainHandler.post(() -> setState(new State(Status.READY, report, html, null)));
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (e instanceof AttendanceReportBuilder.NotClassTeacherException) {
                            setState(new State(Status.FORBIDDEN, null, null, e.getMessage()));
                        } else {
                            setState(new State(Status.ERROR, null, null,
                                    e == null ? null : e.getMessage()));
                        }
                    }
                });
    }

    /**
     * Cập nhật trạng thái (main thread). Nếu đang có Uri chờ xuất — trường hợp tiến trình bị
     * hệ thống kill trong lúc người dùng ở trình chọn file — thì xuất ngay khi dữ liệu sẵn sàng,
     * hoặc xoá file rỗng vừa tạo và báo lỗi nếu không tải được.
     */
    private void setState(State newState) {
        state.setValue(newState);
        if (pendingUri == null || newState.status == Status.LOADING) return;
        Uri uri = pendingUri;
        String fileName = pendingUriFileName;
        pendingUri = null;
        pendingUriFileName = null;
        if (newState.status == Status.READY) {
            exportToUri(uri, fileName);
        } else {
            runInBackground(() -> {
                try {
                    DocumentsContract.deleteDocument(getApplication().getContentResolver(), uri);
                } catch (Exception ignored) {
                    // không xoá được thì để file rỗng
                }
            });
            exportResult.setValue(new ExportResult(false, uri, null, fileName,
                    new IOException("Không có dữ liệu để xuất")));
        }
    }

    /** Report đang hiển thị (null nếu chưa sẵn sàng hoặc rỗng). */
    private AttendanceReport readyReport() {
        State s = state.getValue();
        return s != null && s.status == Status.READY ? s.report : null;
    }

    public String newFileName() {
        AttendanceReport report = readyReport();
        return report == null ? null : AttendanceReportXlsxExporter.buildFileName(report, new Date());
    }

    /** "Xuất Excel": ghi vào Uri do người dùng chọn qua ACTION_CREATE_DOCUMENT. */
    public void exportToUri(Uri uri, String fileName) {
        State current = state.getValue();
        if (current == null || current.status == Status.LOADING) {
            // Dữ liệu đang tải lại (vd sau khi tiến trình bị kill) — xuất khi sẵn sàng
            pendingUri = uri;
            pendingUriFileName = fileName;
            return;
        }
        AttendanceReport report = readyReport();
        if (report == null || Boolean.TRUE.equals(exporting.getValue())) return;
        exporting.setValue(true);
        runInBackground(() -> {
            Exception error = null;
            try (OutputStream raw = openForWrite(uri)) {
                if (raw == null) throw new IOException("Không mở được file để ghi");
                writeXlsx(report, raw);
            } catch (Exception e) {
                error = e;
            }
            exportResult.postValue(new ExportResult(false, uri, null, fileName, error));
            exporting.postValue(false);
        });
    }

    /** "Chia sẻ": ghi file vào cacheDir/reports/ rồi activity chia sẻ qua FileProvider. */
    public void exportForShare() {
        AttendanceReport report = readyReport();
        if (report == null || Boolean.TRUE.equals(exporting.getValue())) return;
        exporting.setValue(true);
        String fileName = AttendanceReportXlsxExporter.buildFileName(report, new Date());
        runInBackground(() -> {
            Exception error = null;
            File file = null;
            try {
                File dir = new File(getApplication().getCacheDir(), "reports");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Không tạo được thư mục tạm");
                File[] old = dir.listFiles();   // dọn file chia sẻ cũ
                if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                    f.delete();
                file = new File(dir, fileName);
                try (OutputStream raw = new FileOutputStream(file)) {
                    writeXlsx(report, raw);
                }
            } catch (Exception e) {
                error = e;
            }
            exportResult.postValue(new ExportResult(true, null, file, fileName, error));
            exporting.postValue(false);
        });
    }

    /** "wt" để chắc chắn ghi đè từ đầu; vài DocumentsProvider chỉ nhận "w". */
    private OutputStream openForWrite(Uri uri) throws IOException {
        try {
            return getApplication().getContentResolver().openOutputStream(uri, "wt");
        } catch (FileNotFoundException | IllegalArgumentException e) {
            return getApplication().getContentResolver().openOutputStream(uri, "w");
        }
    }

    /** Callback Firestore có thể về sau khi ViewModel đã bị hủy — khi đó bỏ qua. */
    private void runInBackground(Runnable task) {
        try {
            if (!executor.isShutdown()) executor.execute(task);
        } catch (RejectedExecutionException ignored) {
            // ViewModel đã onCleared
        }
    }

    private static void writeXlsx(AttendanceReport report, OutputStream raw) throws IOException {
        OutputStream out = new BufferedOutputStream(raw);
        AttendanceReportXlsxExporter.write(report, out);
        out.flush();
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }
}
