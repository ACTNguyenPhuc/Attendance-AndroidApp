package com.example.attendanceapplication.fragments.student;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.ClassDetailStudentActivity;
import com.example.attendanceapplication.activities.JoinClassActivity;
import com.example.attendanceapplication.adapters.StudentClassCardAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.*;

public class StudentClassListFragment extends Fragment {

    private RecyclerView rvClasses;
    private EditText etSearch;
    private TextView tvEmpty;
    private FloatingActionButton fabJoin;
    private StudentClassCardAdapter adapter;
    private final List<ClassModel> allClasses = new ArrayList<>();
    private final List<ClassModel> filteredClasses = new ArrayList<>();
    // Cache tên giáo viên theo teacherId để tránh fetch lại trên mỗi snapshot.
    private final Map<String, String> teacherNameCache = new HashMap<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_class_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvClasses = view.findViewById(R.id.rv_classes);
        etSearch  = view.findViewById(R.id.et_search);
        tvEmpty   = view.findViewById(R.id.tv_empty);
        fabJoin   = view.findViewById(R.id.fab_join);

        adapter = new StudentClassCardAdapter(filteredClasses, cls -> {
            Intent i = new Intent(requireContext(), ClassDetailStudentActivity.class);
            i.putExtra("classId", cls.getClassId());
            i.putExtra("className", cls.getClassName());
            startActivity(i);
        });
        rvClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvClasses.setAdapter(adapter);

        if (fabJoin != null)
            fabJoin.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), JoinClassActivity.class)));

        if (etSearch != null)
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    filterClasses(s.toString());
                }
            });

        loadClasses();
    }

    private void loadClasses() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            if (!isAdded()) return;
            allClasses.clear();
            allClasses.addAll(classes);
            filterClasses(etSearch != null ? etSearch.getText().toString() : "");
            loadTeacherNames();
            loadRates(uid);
        });
    }

    /** Liên kết với collection users để lấy tên giáo viên cho từng lớp. */
    private void loadTeacherNames() {
        for (ClassModel c : allClasses) {
            final String teacherId = c.getTeacherId();
            if (teacherId == null || teacherId.isEmpty()) continue;

            String cached = teacherNameCache.get(teacherId);
            if (cached != null) {
                c.setTeacherName(cached);
                continue;
            }
            repo.getUserProfile(teacherId,
                    user -> {
                        if (!isAdded() || user == null) return;
                        String name = user.getName() != null ? user.getName() : "";
                        teacherNameCache.put(teacherId, name);
                        for (ClassModel cls : allClasses) {
                            if (teacherId.equals(cls.getTeacherId())) cls.setTeacherName(name);
                        }
                        adapter.notifyDataSetChanged();
                    },
                    e -> {});
        }
        adapter.notifyDataSetChanged();
    }

    /** Tính tỉ lệ điểm danh từng lớp = số buổi đã điểm danh / số buổi đã kết thúc. */
    private void loadRates(String uid) {
        List<String> classIds = new ArrayList<>();
        for (ClassModel c : allClasses) {
            if (c.getClassId() != null && !c.getClassId().isEmpty()) classIds.add(c.getClassId());
        }
        if (classIds.isEmpty()) return;

        repo.getShiftsForClasses(classIds, allShifts -> {
            if (!isAdded()) return;
            final Map<String, Integer> completedByClass = new HashMap<>();
            for (Shift s : allShifts) {
                if (Shift.STATUS_COMPLETED.equals(s.getStatus())) {
                    Integer cur = completedByClass.get(s.getClassId());
                    completedByClass.put(s.getClassId(), (cur == null ? 0 : cur) + 1);
                }
            }
            for (ClassModel c : allClasses) {
                final String classId = c.getClassId();
                final int completed = completedByClass.containsKey(classId)
                        ? completedByClass.get(classId) : 0;
                repo.getStudentAttendanceCount(classId, uid,
                        count -> {
                            if (!isAdded()) return;
                            int attended = Math.min(count, completed);
                            int rate = completed == 0 ? -1
                                    : Math.round(attended * 100f / completed);
                            adapter.setRate(classId, rate);
                        },
                        e -> {});
            }
        });
    }

    private void filterClasses(String query) {
        filteredClasses.clear();
        if (query == null || query.isEmpty()) {
            filteredClasses.addAll(allClasses);
        } else {
            String lower = query.toLowerCase();
            for (ClassModel c : allClasses) {
                if (c.getClassName().toLowerCase().contains(lower) ||
                        c.getClassId().toLowerCase().contains(lower)) {
                    filteredClasses.add(c);
                }
            }
        }
        adapter.notifyDataSetChanged();
        if (tvEmpty != null)
            tvEmpty.setVisibility(filteredClasses.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
