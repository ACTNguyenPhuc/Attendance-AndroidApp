package com.example.attendanceapplication.fragments.teacher;

import android.app.AlertDialog;
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
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.models.User;
import com.example.attendanceapplication.repositories.FirebaseRepository;
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
    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    private final List<User> allStudents = new ArrayList<>();
    private StudentListAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c,
                             @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_students_tab, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) classId = getArguments().getString("classId");

        rvStudents = view.findViewById(R.id.rv_students);
        tvEmpty    = view.findViewById(R.id.tv_empty);
        tvCount    = view.findViewById(R.id.tv_student_count);
        tilSearch  = view.findViewById(R.id.til_search);
        etSearch   = view.findViewById(R.id.et_search);
        btnAddStudent = view.findViewById(R.id.btn_add_student);

        rvStudents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StudentListAdapter(new ArrayList<>(), this::confirmRemoveStudent);
        rvStudents.setAdapter(adapter);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterStudents(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnAddStudent != null) {
            btnAddStudent.setOnClickListener(v -> showAddStudentDialog());
        }

        loadStudents();
        loadAttendanceStats();
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
                        Map<String, Integer> attendedByStudent = new HashMap<>();
                        for (Map.Entry<String, Set<String>> e : attendedShiftsByStudent.entrySet()) {
                            attendedByStudent.put(e.getKey(), e.getValue().size());
                        }
                        requireActivity().runOnUiThread(() ->
                                adapter.setAttendanceStats(totalPast, attendedByStudent));
                    },
                    e -> {}
            );
        });
    }

    private void loadStudents() {
        if (classId == null) return;
        repo.getClassStudents(classId,
                students -> requireActivity().runOnUiThread(() -> {
                    allStudents.clear();
                    allStudents.addAll(students);
                    tvCount.setText("Tổng: " + allStudents.size() + " sinh viên");
                    tvEmpty.setVisibility(allStudents.isEmpty() ? View.VISIBLE : View.GONE);
                    filterStudents(etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "");
                }),
                e -> {}
        );
    }

    private void filterStudents(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<User> filtered = new ArrayList<>();
        if (q.isEmpty()) {
            filtered.addAll(allStudents);
        } else {
            for (User u : allStudents) {
                String name = u.getName() == null ? "" : u.getName();
                String code = u.getStudentCode() == null ? "" : u.getStudentCode();
                if (name.toLowerCase(Locale.ROOT).contains(q) ||
                        code.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(u);
                }
            }
        }

        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.setData(filtered);
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
                users -> requireActivity().runOnUiThread(() -> {
                    availableStudents.clear();
                    availableStudents.addAll(users);
                    String query = queryInput != null && queryInput.getText() != null
                            ? queryInput.getText().toString() : "";
                    performStudentSearch(query, availableStudents, adapter, tvEmpty);
                }),
                e -> requireActivity().runOnUiThread(() -> {
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
                requireActivity().runOnUiThread(() ->
                        Snackbar.make(requireView(), "Sinh viên đã có trong lớp", Snackbar.LENGTH_LONG).show());
                return;
            }
            repo.enrollStudent(user.getUid(), classId,
                    aVoid -> requireActivity().runOnUiThread(() -> {
                        Snackbar.make(requireView(), "Đã thêm " + user.getName(), Snackbar.LENGTH_LONG).show();
                        if (onSuccess != null) onSuccess.run();
                    }),
                    e -> requireActivity().runOnUiThread(() ->
                            Snackbar.make(requireView(), "Thêm thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
            );
        });
    }

    private void addStudentByQuery(String query) {
        repo.findStudentByCodeOrEmail(query,
                user -> {
                    if (user == null) {
                        requireActivity().runOnUiThread(() ->
                                Snackbar.make(requireView(), "Không tìm thấy sinh viên", Snackbar.LENGTH_LONG).show());
                        return;
                    }
                    if (!user.isStudent()) {
                        requireActivity().runOnUiThread(() ->
                                Snackbar.make(requireView(), "Tài khoản này không phải sinh viên", Snackbar.LENGTH_LONG).show());
                        return;
                    }

                    repo.checkEnrollment(user.getUid(), classId, exists -> {
                        if (exists) {
                            requireActivity().runOnUiThread(() ->
                                    Snackbar.make(requireView(), "Sinh viên đã có trong lớp", Snackbar.LENGTH_LONG).show());
                            return;
                        }
                        repo.enrollStudent(user.getUid(), classId,
                                aVoid -> requireActivity().runOnUiThread(() -> {
                                    Snackbar.make(requireView(), "Đã thêm " + user.getName(), Snackbar.LENGTH_LONG).show();
                                    loadStudents();
                                }),
                                e -> requireActivity().runOnUiThread(() ->
                                        Snackbar.make(requireView(), "Thêm thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
                        );
                    });
                },
                e -> requireActivity().runOnUiThread(() ->
                        Snackbar.make(requireView(), "Không tìm thấy sinh viên: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
        );
    }

    private void confirmRemoveStudent(User user) {
        if (user == null) return;
        if (classId == null || user.getUid() == null || user.getUid().isEmpty()) {
            Snackbar.make(requireView(), "Không tìm thấy mã sinh viên", Snackbar.LENGTH_LONG).show();
            return;
        }

        repo.getStudentAttendanceCount(classId, user.getUid(), count -> requireActivity().runOnUiThread(() -> {
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
        }), e -> requireActivity().runOnUiThread(() ->
                Snackbar.make(requireView(), "Không kiểm tra được điểm danh: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
        );
    }

    private void removeStudentFromClass(User user, boolean deleteAttendances) {
        if (classId == null || user.getUid() == null || user.getUid().isEmpty()) {
            Snackbar.make(requireView(), "Không tìm thấy mã sinh viên", Snackbar.LENGTH_LONG).show();
            return;
        }

        Runnable deleteEnrollment = () -> repo.removeEnrollment(user.getUid(), classId,
                aVoid -> requireActivity().runOnUiThread(() -> {
                    removeFromList(user.getUid());
                    Snackbar.make(requireView(), "Đã xóa sinh viên", Snackbar.LENGTH_LONG).show();
                }),
                e -> requireActivity().runOnUiThread(() ->
                        Snackbar.make(requireView(), "Xóa thất bại: " + e.getMessage(), Snackbar.LENGTH_LONG).show())
        );

        if (deleteAttendances) {
            repo.deleteStudentAttendancesForClass(user.getUid(), classId,
                    aVoid -> deleteEnrollment.run(),
                    e -> requireActivity().runOnUiThread(() ->
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
        filterStudents(etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "");
    }

    // Simple inner adapter for student list
    static class StudentListAdapter extends RecyclerView.Adapter<StudentListAdapter.VH> {
        interface OnRemoveClickListener { void onRemove(User user); }

        private final List<User> users;
        private final OnRemoveClickListener onRemoveClickListener;
        private int totalPastShifts = 0;
        private final Map<String, Integer> attendedByStudent = new HashMap<>();

        StudentListAdapter(List<User> users, OnRemoveClickListener onRemoveClickListener) {
            this.users = users;
            this.onRemoveClickListener = onRemoveClickListener;
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
