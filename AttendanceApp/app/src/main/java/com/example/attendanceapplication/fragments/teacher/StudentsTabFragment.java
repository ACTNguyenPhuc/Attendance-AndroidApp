package com.example.attendanceapplication.fragments.teacher;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
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
        adapter = new StudentListAdapter(new ArrayList<>());
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

        new AlertDialog.Builder(requireContext())
                .setTitle("Thêm sinh viên thủ công")
                .setMessage("Nhập mã sinh viên hoặc email đã đăng ký tài khoản.")
                .setView(content)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Thêm", (d, which) -> {
                    String q = et != null && et.getText() != null ? et.getText().toString().trim() : "";
                    if (q.isEmpty()) {
                        Snackbar.make(requireView(), "Vui lòng nhập mã SV hoặc email", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    addStudentByQuery(q);
                })
                .show();
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

    // Simple inner adapter for student list
    static class StudentListAdapter extends RecyclerView.Adapter<StudentListAdapter.VH> {
        private final List<User> users;
        StudentListAdapter(List<User> users) { this.users = users; }

        void setData(List<User> newData) {
            users.clear();
            users.addAll(newData);
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
            // Avatar initial
            if (u.getName() != null && !u.getName().isEmpty()) {
                holder.tvAvatar.setText(String.valueOf(u.getName().charAt(0)).toUpperCase());
            }
        }

        @Override public int getItemCount() { return users.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCode, tvAvatar;
            VH(@NonNull View v) {
                super(v);
                tvName   = v.findViewById(R.id.tv_name);
                tvCode   = v.findViewById(R.id.tv_code);
                tvAvatar = v.findViewById(R.id.tv_avatar);
            }
        }
    }
}
