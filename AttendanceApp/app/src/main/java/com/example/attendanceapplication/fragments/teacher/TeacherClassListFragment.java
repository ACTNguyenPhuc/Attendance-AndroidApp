package com.example.attendanceapplication.fragments.teacher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.ClassDetailTeacherActivity;
import com.example.attendanceapplication.activities.CreateClassActivity;
import com.example.attendanceapplication.adapters.TeacherClassCardAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.ClassQRDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeacherClassListFragment extends Fragment {

    private RecyclerView rvClasses;
    private EditText etSearch;
    private FloatingActionButton fabAdd;
    private TeacherClassCardAdapter adapter;
    private List<ClassModel> allClasses = new ArrayList<>();
    private List<ClassModel> filteredClasses = new ArrayList<>();
    private LiveData<List<ClassModel>> classLiveData;
    private ActivityResultLauncher<Intent> createClassLauncher;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teacher_class_list, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createClassLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                    addCreatedClass(result.getData()
                            .getStringExtra(CreateClassActivity.EXTRA_CREATED_CLASS_ID));
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvClasses = view.findViewById(R.id.rv_classes);
        etSearch  = view.findViewById(R.id.et_search);
        fabAdd    = view.findViewById(R.id.fab_add);

        adapter = new TeacherClassCardAdapter(
                filteredClasses,
                this::openClassDetail,
                new TeacherClassCardAdapter.OnClassMenuListener() {
                    @Override
                    public void onViewDetail(ClassModel classModel) {
                        openClassDetail(classModel);
                    }

                    @Override
                    public void onShowQr(ClassModel classModel) {
                        ClassQRDialog.show(requireContext(),
                                classModel.getClassId(), classModel.getClassName());
                    }

                    @Override
                    public void onDelete(ClassModel classModel) {
                        confirmDeleteClass(classModel);
                    }
                }
        );
        rvClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvClasses.setAdapter(adapter);

        fabAdd.setOnClickListener(v ->
                createClassLauncher.launch(new Intent(requireContext(), CreateClassActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) { filterClasses(s.toString()); }
        });

        loadClasses();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!allClasses.isEmpty()) {
            loadTodayShifts();
        }
    }

    private void loadClasses() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (classLiveData == null) {
            classLiveData = repo.getTeacherClasses(uid);
        }
        classLiveData.removeObservers(getViewLifecycleOwner());
        classLiveData.observe(getViewLifecycleOwner(), classes -> {
            allClasses.clear();
            allClasses.addAll(classes);
            filterClasses(etSearch.getText().toString());
            loadTodayShifts();
            loadStudentCounts();
        });
    }

    private void loadStudentCounts() {
        for (ClassModel c : allClasses) {
            if (c.getClassId() == null || c.getClassId().isEmpty()) continue;
            repo.getClassStudents(c.getClassId(),
                    students -> {
                        if (!isAdded() || getActivity() == null) return;
                        requireActivity().runOnUiThread(() -> {
                            c.setStudentCount(students.size());
                            notifyClassUpdated(c.getClassId());
                        });
                    },
                    e -> {}
            );
        }
    }

    private void addCreatedClass(String classId) {
        if (classId == null || classId.isEmpty()) return;
        repo.getClassById(classId,
                createdClass -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        for (int i = allClasses.size() - 1; i >= 0; i--) {
                            if (classId.equals(allClasses.get(i).getClassId())) {
                                allClasses.remove(i);
                            }
                        }
                        allClasses.add(0, createdClass);
                        filterClasses(etSearch.getText().toString());
                        loadTodayShifts();
                    });
                },
                e -> {
                    if (isAdded()) loadClasses();
                }
        );
    }

    private void loadTodayShifts() {
        List<String> classIds = new ArrayList<>();
        for (ClassModel c : allClasses) {
            if (c.getClassId() != null && !c.getClassId().isEmpty()) {
                classIds.add(c.getClassId());
            }
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
        repo.getShiftsByDate(classIds, today, shifts -> {
            if (getActivity() == null) return;
            Map<String, Shift> map = new HashMap<>();
            for (Shift s : shifts) {
                Shift existing = map.get(s.getClassId());
                if (existing == null ||
                        getStatusPriority(s.getStatus()) < getStatusPriority(existing.getStatus())) {
                    map.put(s.getClassId(), s);
                }
            }
            requireActivity().runOnUiThread(() -> adapter.setTodayShiftMap(map));
        });
    }

    private int getStatusPriority(String status) {
        if (Shift.STATUS_ONGOING.equals(status)) return 0;
        if (Shift.STATUS_UPCOMING.equals(status)) return 1;
        if (Shift.STATUS_COMPLETED.equals(status)) return 2;
        if (Shift.STATUS_CANCELLED.equals(status)) return 3;
        return 4;
    }

    private void filterClasses(String query) {
        filteredClasses.clear();
        if (query.isEmpty()) {
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
    }

    private void notifyClassUpdated(String classId) {
        for (int i = 0; i < filteredClasses.size(); i++) {
            ClassModel c = filteredClasses.get(i);
            if (classId.equals(c.getClassId())) {
                adapter.notifyItemChanged(i);
                return;
            }
        }
    }

    private void openClassDetail(ClassModel classModel) {
        Intent i = new Intent(requireContext(), ClassDetailTeacherActivity.class);
        i.putExtra("classId", classModel.getClassId());
        i.putExtra("className", classModel.getClassName());
        startActivity(i);
    }

    private void confirmDeleteClass(ClassModel classModel) {
        // Không cho xóa lớp đã phát sinh phiên điểm danh (đã có bản ghi).
        repo.classHasSessions(classModel.getClassId(), hasSessions -> {
            if (!isAdded()) return;
            if (hasSessions) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Không thể xóa lớp")
                        .setMessage("Lớp " + classModel.getClassName()
                                + " đã phát sinh bản ghi điểm danh nên không thể xóa.")
                        .setPositiveButton("Đã hiểu", null)
                        .show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Xóa lớp")
                    .setMessage("Bạn có chắc muốn xóa lớp " + classModel.getClassName() + "?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Xóa", (d, which) -> deleteClass(classModel))
                    .show();
        });
    }

    private void deleteClass(ClassModel classModel) {
        repo.deleteClass(classModel.getClassId(),
                r -> {
                    removeLocalClass(classModel.getClassId());
                    Toast.makeText(requireContext(),
                            "Đã xóa lớp", Toast.LENGTH_SHORT).show();
                },
                e -> Toast.makeText(requireContext(),
                        "Xóa thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void removeLocalClass(String classId) {
        if (classId == null) return;
        for (int i = allClasses.size() - 1; i >= 0; i--) {
            if (classId.equals(allClasses.get(i).getClassId())) {
                allClasses.remove(i);
            }
        }
        for (int i = filteredClasses.size() - 1; i >= 0; i--) {
            if (classId.equals(filteredClasses.get(i).getClassId())) {
                filteredClasses.remove(i);
                adapter.notifyItemRemoved(i);
            }
        }
    }
}
