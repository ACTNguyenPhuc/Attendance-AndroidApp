package com.example.attendanceapplication.fragments.teacher;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.StudentAttendanceDetailActivity;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.*;

public class StudentsTabFragment extends Fragment {

    private RecyclerView rvStudents;
    private TextView tvEmpty, tvCount;
    private TextInputLayout tilSearch;
    private TextInputEditText etSearch;
    private View btnAddStudent;
    private String classId;
    private String className;
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    private final List<User> allStudents = new ArrayList<>();
    private StudentListAdapter adapter;

    // --- Bộ lọc theo tỷ lệ điểm danh / vắng ---
    private View llFilterHeader, llFilterPanel;
    private ImageView ivFilterArrow;
    private TextView tvFilterSummary;
    private ChipGroup chipGroupFilter;
    private Chip chipAttendanceRate, chipAbsenceRate;
    private TextInputEditText etFilterPercent;
    private View btnClearFilter;
    private boolean filterExpanded = false;

    // Bản sao thống kê điểm danh để lọc ở phía fragment (đồng bộ với adapter).
    private int totalPastShifts = 0;
    private final Map<String, Integer> attendedByStudent = new HashMap<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c,
                             @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_students_tab, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            classId = getArguments().getString("classId");
            className = getArguments().getString("className");
        }

        rvStudents = view.findViewById(R.id.rv_students);
        tvEmpty    = view.findViewById(R.id.tv_empty);
        tvCount    = view.findViewById(R.id.tv_student_count);
        tilSearch  = view.findViewById(R.id.til_search);
        etSearch   = view.findViewById(R.id.et_search);
        btnAddStudent = view.findViewById(R.id.btn_add_student);

        llFilterHeader   = view.findViewById(R.id.ll_filter_header);
        llFilterPanel    = view.findViewById(R.id.ll_filter_panel);
        ivFilterArrow    = view.findViewById(R.id.iv_filter_arrow);
        tvFilterSummary  = view.findViewById(R.id.tv_filter_summary);
        chipGroupFilter  = view.findViewById(R.id.chip_group_filter);
        chipAttendanceRate = view.findViewById(R.id.chip_attendance_rate);
        chipAbsenceRate    = view.findViewById(R.id.chip_absence_rate);
        etFilterPercent  = view.findViewById(R.id.et_filter_percent);
        btnClearFilter   = view.findViewById(R.id.btn_clear_filter);

        rvStudents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StudentListAdapter(new ArrayList<>(), this::confirmRemoveStudent, this::openStudentDetail);
        rvStudents.setAdapter(adapter);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilters();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnAddStudent != null) {
            btnAddStudent.setOnClickListener(v -> showAddStudentDialog());
        }

        setupRateFilter();

        loadStudents();
        loadAttendanceStats();
    }

    /** Cài đặt bộ lọc tỷ lệ: đóng/mở dạng dropdown, đổi chip, nhập % và xóa lọc. */
    private void setupRateFilter() {
        if (llFilterHeader != null) {
            llFilterHeader.setOnClickListener(v -> toggleFilterPanel());
        }
        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        }
        if (etFilterPercent != null) {
            etFilterPercent.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilters();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        if (btnClearFilter != null) {
            btnClearFilter.setOnClickListener(v -> {
                if (etFilterPercent != null) etFilterPercent.setText("");
                if (chipAttendanceRate != null) chipAttendanceRate.setChecked(true);
                applyFilters();
            });
        }
    }

    private void toggleFilterPanel() {
        filterExpanded = !filterExpanded;
        if (llFilterPanel != null) {
            llFilterPanel.setVisibility(filterExpanded ? View.VISIBLE : View.GONE);
        }
        if (ivFilterArrow != null) {
            ivFilterArrow.animate().rotation(filterExpanded ? 180f : 0f).setDuration(150).start();
        }
    }

    /**
     * Tính số buổi tham gia của từng sinh viên:
     *  - Mẫu số = số buổi ĐÃ QUA (status = completed), giống tab Thống kê.
     *  - Tử số = số buổi đã qua mà SV có mặt/đi muộn (present/late).
     * Quan sát shifts realtime nên khi đóng phiên (buổi -> completed) sẽ tự tính lại.
     */
    private void loadAttendanceStats() {
        if (classId == null) return;
        repo.getClassShifts(classId).observe(getViewLifecycleOwner(), shifts -> {
            Set<String> completedShiftIds = new HashSet<>();
            for (Shift s : shifts) {
                if (Shift.STATUS_COMPLETED.equals(s.getStatus()) && s.getShiftId() != null) {
                    completedShiftIds.add(s.getShiftId());
                }
            }
            final int totalPast = completedShiftIds.size();

            repo.getClassAttendances(classId,
                    attendances -> {
                        if (!isAdded()) return;
                        Map<String, Set<String>> attendedShiftsByStudent = new HashMap<>();
                        for (Attendance a : attendances) {
                            if (a == null || a.getStudentId() == null || a.getShiftId() == null) continue;
                            if (!completedShiftIds.contains(a.getShiftId())) continue;
                            String st = a.getStatus();
                            if (!Attendance.STATUS_PRESENT.equals(st)
                                    && !Attendance.STATUS_LATE.equals(st)) continue;
                            attendedShiftsByStudent
                                    .computeIfAbsent(a.getStudentId(), k -> new HashSet<>())
                                    .add(a.getShiftId());
                        }
                        Map<String, Integer> attendedMap = new HashMap<>();
                        for (Map.Entry<String, Set<String>> e : attendedShiftsByStudent.entrySet()) {
                            attendedMap.put(e.getKey(), e.getValue().size());
                        }
                        runOnUi(() -> {
                            totalPastShifts = totalPast;
                            attendedByStudent.clear();
                            attendedByStudent.putAll(attendedMap);
                            adapter.setAttendanceStats(totalPast, attendedMap);
                            // Thống kê thay đổi (vd buổi học chuyển completed) -> lọc lại.
                            applyFilters();
                        });
                    },
                    e -> {}
            );
        });
    }

    private void loadStudents() {
        if (classId == null) return;
        repo.getClassStudents(classId,
                students -> runOnUi(() -> {
                    allStudents.clear();
                    allStudents.addAll(students);
                    tvCount.setText("Tổng: " + allStudents.size() + " sinh viên");
                    tvEmpty.setVisibility(allStudents.isEmpty() ? View.VISIBLE : View.GONE);
                    applyFilters();
                }),
                e -> {}
        );
    }

    /**
     * Lọc kết hợp: từ khóa tìm kiếm (tên/mã SV) và tỷ lệ tối thiểu.
     * Bộ lọc tỷ lệ giữ lại các SV có tỷ lệ (điểm danh hoặc vắng tùy chip) >= số % nhập vào.
     * Ô % để trống -> không lọc theo tỷ lệ.
     */
    private void applyFilters() {
        if (adapter == null) return;

        String q = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        Integer threshold = parseFilterThreshold();
        boolean filterByAbsence = chipAbsenceRate != null && chipAbsenceRate.isChecked();

        List<User> filtered = new ArrayList<>();
        for (User u : allStudents) {
            // Khớp từ khóa tìm kiếm
            if (!q.isEmpty()) {
                String name = u.getName() == null ? "" : u.getName().toLowerCase(Locale.ROOT);
                String code = u.getStudentCode() == null ? "" : u.getStudentCode().toLowerCase(Locale.ROOT);
                if (!name.contains(q) && !code.contains(q)) continue;
            }
            // Khớp tỷ lệ tối thiểu
            if (threshold != null) {
                int rate = filterByAbsence ? absenceRateOf(u) : attendanceRateOf(u);
                if (rate < threshold) continue;
            }
            filtered.add(u);
        }

        updateFilterSummary(threshold, filterByAbsence);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.setData(filtered);
    }

    /** Đọc số % từ ô nhập; trả về null nếu trống/không hợp lệ (kẹp về 0..100). */
    private Integer parseFilterThreshold() {
        if (etFilterPercent == null || etFilterPercent.getText() == null) return null;
        String raw = etFilterPercent.getText().toString().trim();
        if (raw.isEmpty()) return null;
        try {
            int value = Integer.parseInt(raw);
            return Math.max(0, Math.min(100, value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int attendanceRateOf(User u) {
        if (totalPastShifts <= 0) return 0;
        int attended = u.getUid() == null ? 0 : attendedByStudent.getOrDefault(u.getUid(), 0);
        return attended * 100 / totalPastShifts;
    }

    private int absenceRateOf(User u) {
        if (totalPastShifts <= 0) return 0;
        int attended = u.getUid() == null ? 0 : attendedByStudent.getOrDefault(u.getUid(), 0);
        int missed = Math.max(0, totalPastShifts - attended);
        return missed * 100 / totalPastShifts;
    }

    private void updateFilterSummary(Integer threshold, boolean filterByAbsence) {
        if (tvFilterSummary == null) return;
        if (threshold == null) {
            tvFilterSummary.setText("Lọc theo tỷ lệ");
        } else {
            String label = filterByAbsence ? "Tỷ lệ vắng" : "Tỷ lệ điểm danh";
            tvFilterSummary.setText(label + " ≥ " + threshold + "%");
        }
    }

    /** Mở trang chi tiết các bản ghi điểm danh của sinh viên trong môn học này. */
    private void openStudentDetail(User user) {
        if (user == null || classId == null) return;
        if (user.getUid() == null || user.getUid().isEmpty()) {
            Snackbar.make(requireView(), "Không tìm thấy mã sinh viên", Snackbar.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(requireContext(), StudentAttendanceDetailActivity.class);
        intent.putExtra(StudentAttendanceDetailActivity.EXTRA_STUDENT_ID, user.getUid());
        intent.putExtra(StudentAttendanceDetailActivity.EXTRA_STUDENT_NAME, user.getName());
        intent.putExtra(StudentAttendanceDetailActivity.EXTRA_STUDENT_CODE, user.getStudentCode());
        intent.putExtra(StudentAttendanceDetailActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(StudentAttendanceDetailActivity.EXTRA_CLASS_NAME, className);
        startActivity(intent);
    }

    /**
     * Chạy {@code action} trên UI thread, nhưng chỉ khi fragment còn gắn với activity.
     * Tránh crash "Fragment not attached to an activity" khi callback Firestore trả về
     * sau lúc người dùng đã rời màn hình.
     */
    private void runOnUi(Runnable action) {
        if (isAdded() && getActivity() != null) requireActivity().runOnUiThread(action);
    }

    private void showAddStudentDialog() {
        if (classId == null) return;

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_student, null, false);
        TextInputLayout til = content.findViewById(R.id.til_student_query);
        TextInputEditText et = content.findViewById(R.id.et_student_query);
        TextView tvEmpty = content.findViewById(R.id.tv_search_empty);
        RecyclerView rvResults = content.findViewById(R.id.rv_search_results);
        List<User> availableStudents = new ArrayList<>();

        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        StudentSearchAdapter searchAdapter = new StudentSearchAdapter(new ArrayList<>(), null);
        searchAdapter.setOnAddClickListener(user ->
                addStudentFromSearch(user, () -> {
                    Iterator<User> iterator = availableStudents.iterator();
                    while (iterator.hasNext()) {
                        if (Objects.equals(user.getUid(), iterator.next().getUid())) {
                            iterator.remove();
                            break;
                        }
                    }
                    String query = et != null && et.getText() != null ? et.getText().toString() : "";
                    performStudentSearch(query, availableStudents, searchAdapter, tvEmpty);
                    loadStudents();
                }));
        rvResults.setAdapter(searchAdapter);

        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] pending = new Runnable[1];

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Thêm sinh viên thủ công")
            .setMessage("Nhập mã sinh viên, email hoặc họ tên đã đăng ký tài khoản.")
                .setView(content)
                .setNegativeButton("Đóng", null)
                .create();
        dialog.setOnDismissListener(d -> handler.removeCallbacksAndMessages(null));
        dialog.show();

        if (et != null) {
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (pending[0] != null) handler.removeCallbacks(pending[0]);
                    String q = s == null ? "" : s.toString();
                    pending[0] = () -> performStudentSearch(q, availableStudents, searchAdapter, tvEmpty);
                    handler.postDelayed(pending[0], 1000);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        tvEmpty.setText("Đang tải danh sách sinh viên...");
        tvEmpty.setVisibility(View.VISIBLE);
        loadUnenrolledStudents(availableStudents, et, searchAdapter, tvEmpty);
    }

    private void loadUnenrolledStudents(List<User> availableStudents, TextInputEditText queryInput,
                                        StudentSearchAdapter adapter, TextView tvEmpty) {
        repo.getUnenrolledStudents(classId,
                users -> runOnUi(() -> {
                    availableStudents.clear();
                    availableStudents.addAll(users);
                    String query = queryInput != null && queryInput.getText() != null
                            ? queryInput.getText().toString() : "";
                    performStudentSearch(query, availableStudents, adapter, tvEmpty);
                }),
                e -> runOnUi(() -> {
                    tvEmpty.setText("Không tải được danh sách: " + e.getMessage());
                    tvEmpty.setVisibility(View.VISIBLE);
                })
        );
    }

    private void performStudentSearch(String query, List<User> availableStudents,
                                      StudentSearchAdapter adapter, TextView tvEmpty) {
        String q = query == null ? "" : query.trim();
        String lowerQuery = q.toLowerCase(Locale.ROOT);
        List<User> filtered = new ArrayList<>();
        for (User user : availableStudents) {
            String name = user.getName() == null ? "" : user.getName();
            String code = user.getStudentCode() == null ? "" : user.getStudentCode();
            String email = user.getEmail() == null ? "" : user.getEmail();
            if (lowerQuery.isEmpty() || name.toLowerCase(Locale.ROOT).contains(lowerQuery) ||
                    code.toLowerCase(Locale.ROOT).contains(lowerQuery) ||
                    email.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                filtered.add(user);
            }
        }
        adapter.setData(filtered);
        if (filtered.isEmpty()) {
            tvEmpty.setText(availableStudents.isEmpty()
                    ? "Tất cả sinh viên đã tham gia lớp"
                    : "Không tìm thấy sinh viên phù hợp");
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void addStudentFromSearch(User user, Runnable onSuccess) {
        if (classId == null || user == null) return;
        if (!user.isStudent()) {
            Snackbar.make(requireView(), "Tài khoản này không phải sinh viên", Snackbar.LENGTH_LONG).show();
            return;
        }
        if (user.getUid() == null || user.getUid().isEmpty()) {
            Snackbar.make(requireView(), "Thiếu mã định danh sinh viên", Snackbar.LENGTH_LONG).show();
            return;
        }

        repo.checkEnrollment(user.getUid(), classId, exists -> {
            if (exists) {
                runOnUi(() ->
                        Snackbar.make(requireView(), "Sinh viên đã có trong lớp", Snackbar.LENGTH_LONG).show());
                return;
            }
            repo.enrollStudent(user.getUid(), classId,
                    aVoid -> runOnUi(() -> {
                        Snackbar.make(requireView(), "Đã thêm " + user.getName(), Snackbar.LENGTH_LONG).show();
                        if (onSuccess != null) onSuccess.run();
                    }),
                    e -> runOnUi(() ->
                            Snackbar.make(requireView(), "Thêm thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
            );
        });
    }

    private void addStudentByQuery(String query) {
        repo.findStudentByCodeOrEmail(query,
                user -> {
                    if (user == null) {
                        runOnUi(() ->
                                Snackbar.make(requireView(), "Không tìm thấy sinh viên", Snackbar.LENGTH_LONG).show());
                        return;
                    }
                    if (!user.isStudent()) {
                        runOnUi(() ->
                                Snackbar.make(requireView(), "Tài khoản này không phải sinh viên", Snackbar.LENGTH_LONG).show());
                        return;
                    }

                    repo.checkEnrollment(user.getUid(), classId, exists -> {
                        if (exists) {
                            runOnUi(() ->
                                    Snackbar.make(requireView(), "Sinh viên đã có trong lớp", Snackbar.LENGTH_LONG).show());
                            return;
                        }
                        repo.enrollStudent(user.getUid(), classId,
                                aVoid -> runOnUi(() -> {
                                    Snackbar.make(requireView(), "Đã thêm " + user.getName(), Snackbar.LENGTH_LONG).show();
                                    loadStudents();
                                }),
                                e -> runOnUi(() ->
                                        Snackbar.make(requireView(), "Thêm thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
                        );
                    });
                },
                e -> runOnUi(() ->
                        Snackbar.make(requireView(), "Không tìm thấy sinh viên: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
        );
    }

    private void confirmRemoveStudent(User user) {
        if (user == null) return;
        if (classId == null || user.getUid() == null || user.getUid().isEmpty()) {
            Snackbar.make(requireView(), "Không tìm thấy mã sinh viên", Snackbar.LENGTH_LONG).show();
            return;
        }

        repo.getStudentAttendanceCount(classId, user.getUid(), count -> runOnUi(() -> {
            String name = user.getName() == null ? "" : user.getName();
            String base = "Xóa " + (name.isEmpty() ? "sinh viên này" : name) + " khỏi lớp?";
            String msg = count > 0
                    ? base + "\nSinh viên đã có " + count + " phiếu điểm danh. Xóa sẽ xóa luôn các bản ghi điểm danh của môn học này."
                    : base;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Xóa sinh viên")
                    .setMessage(msg)
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Xóa", (d, which) -> removeStudentFromClass(user, count > 0))
                    .show();
        }), e -> runOnUi(() ->
                Snackbar.make(requireView(), "Không kiểm tra được điểm danh: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
        );
    }

    private void removeStudentFromClass(User user, boolean deleteAttendances) {
        if (classId == null || user.getUid() == null || user.getUid().isEmpty()) {
            Snackbar.make(requireView(), "Không tìm thấy mã sinh viên", Snackbar.LENGTH_LONG).show();
            return;
        }

        Runnable deleteEnrollment = () -> repo.removeEnrollment(user.getUid(), classId,
                aVoid -> runOnUi(() -> {
                    removeFromList(user.getUid());
                    Snackbar.make(requireView(), "Đã xóa sinh viên", Snackbar.LENGTH_LONG).show();
                }),
                e -> runOnUi(() ->
                        Snackbar.make(requireView(), "Xóa thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
        );

        if (deleteAttendances) {
            repo.deleteStudentAttendancesForClass(user.getUid(), classId,
                    aVoid -> deleteEnrollment.run(),
                    e -> runOnUi(() ->
                            Snackbar.make(requireView(), "Xóa điểm danh thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
            );
        } else {
            deleteEnrollment.run();
        }
    }

    private void removeFromList(String studentId) {
        if (studentId == null) return;
        Iterator<User> it = allStudents.iterator();
        while (it.hasNext()) {
            User u = it.next();
            if (studentId.equals(u.getUid())) {
                it.remove();
                break;
            }
        }
        tvCount.setText("Tổng: " + allStudents.size() + " sinh viên");
        applyFilters();
    }

    // Simple inner adapter for student list
    static class StudentListAdapter extends RecyclerView.Adapter<StudentListAdapter.VH> {
        interface OnRemoveClickListener { void onRemove(User user); }
        interface OnStudentClickListener { void onClick(User user); }

        private final List<User> users;
        private final OnRemoveClickListener onRemoveClickListener;
        private final OnStudentClickListener onStudentClickListener;
        private int totalPastShifts = 0;
        private final Map<String, Integer> attendedByStudent = new HashMap<>();

        StudentListAdapter(List<User> users, OnRemoveClickListener onRemoveClickListener,
                           OnStudentClickListener onStudentClickListener) {
            this.users = users;
            this.onRemoveClickListener = onRemoveClickListener;
            this.onStudentClickListener = onStudentClickListener;
        }

        void setData(List<User> newData) {
            users.clear();
            users.addAll(newData);
            notifyDataSetChanged();
        }

        void setAttendanceStats(int totalPastShifts, Map<String, Integer> attendedByStudent) {
            this.totalPastShifts = totalPastShifts;
            this.attendedByStudent.clear();
            if (attendedByStudent != null) this.attendedByStudent.putAll(attendedByStudent);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            User u = users.get(position);
            holder.tvName.setText(u.getName());
            holder.tvCode.setText(u.getStudentCode());

            int attended = u.getUid() == null ? 0
                    : attendedByStudent.getOrDefault(u.getUid(), 0);
            int percent = totalPastShifts > 0 ? (attended * 100 / totalPastShifts) : 0;
            holder.tvAttendanceSummary.setText(
                    attended + "/" + totalPastShifts + " buổi (" + percent + "%)");
            holder.progressAttendance.setProgress(percent);
            // Avatar initial
            if (u.getName() != null && !u.getName().isEmpty()) {
                holder.tvAvatar.setText(String.valueOf(u.getName().charAt(0)).toUpperCase());
            }
            holder.btnRemove.setOnClickListener(v -> {
                if (onRemoveClickListener != null) onRemoveClickListener.onRemove(u);
            });
            holder.itemView.setOnClickListener(v -> {
                if (onStudentClickListener != null) onStudentClickListener.onClick(u);
            });
        }

        @Override public int getItemCount() { return users.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCode, tvAvatar, tvAttendanceSummary;
            ProgressBar progressAttendance;
            View btnRemove;
            VH(@NonNull View v) {
                super(v);
                tvName   = v.findViewById(R.id.tv_name);
                tvCode   = v.findViewById(R.id.tv_code);
                tvAvatar = v.findViewById(R.id.tv_avatar);
                tvAttendanceSummary = v.findViewById(R.id.tv_attendance_summary);
                progressAttendance = v.findViewById(R.id.progress_attendance);
                btnRemove = v.findViewById(R.id.btn_remove);
            }
        }
    }

    static class StudentSearchAdapter extends RecyclerView.Adapter<StudentSearchAdapter.VH> {
        interface OnAddClickListener { void onAdd(User user); }

        private final List<User> users;
        private OnAddClickListener onAddClickListener;

        StudentSearchAdapter(List<User> users, OnAddClickListener onAddClickListener) {
            this.users = users;
            this.onAddClickListener = onAddClickListener;
        }

        void setOnAddClickListener(OnAddClickListener onAddClickListener) {
            this.onAddClickListener = onAddClickListener;
        }

        void setData(List<User> newData) {
            users.clear();
            users.addAll(newData);
            notifyDataSetChanged();
        }

        void removeUser(User user) {
            int index = users.indexOf(user);
            if (index >= 0) {
                users.remove(index);
                notifyItemRemoved(index);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student_search, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            User u = users.get(position);
            String name = u.getName() == null ? "" : u.getName();
            String code = u.getStudentCode() == null ? "" : u.getStudentCode();
            String email = u.getEmail() == null ? "" : u.getEmail();
            String line = code;
            if (!email.isEmpty()) {
                line = code.isEmpty() ? email : code + " · " + email;
            }

            holder.tvName.setText(name.isEmpty() ? "(Không tên)" : name);
            holder.tvCodeEmail.setText(line);
            holder.btnAdd.setOnClickListener(v -> {
                if (onAddClickListener != null) onAddClickListener.onAdd(u);
            });
        }

        @Override public int getItemCount() { return users.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCodeEmail;
            View btnAdd;
            VH(@NonNull View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_name);
                tvCodeEmail = v.findViewById(R.id.tv_code_email);
                btnAdd = v.findViewById(R.id.btn_add);
            }
        }
    }

    public void requestAddStudentDialog() {
        if (isAdded()) {
            showAddStudentDialog();
        }
    }
}
