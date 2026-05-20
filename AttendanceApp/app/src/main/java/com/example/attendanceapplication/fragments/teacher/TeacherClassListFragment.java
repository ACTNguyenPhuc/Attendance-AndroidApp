package com.example.attendanceapplication.fragments.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.ClassDetailTeacherActivity;
import com.example.attendanceapplication.activities.CreateClassActivity;
import com.example.attendanceapplication.adapters.ClassCardAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TeacherClassListFragment extends Fragment {

    private RecyclerView rvClasses;
    private EditText etSearch;
    private FloatingActionButton fabAdd;
    private ClassCardAdapter adapter;
    private List<ClassModel> allClasses = new ArrayList<>();
    private List<ClassModel> filteredClasses = new ArrayList<>();

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teacher_class_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvClasses = view.findViewById(R.id.rv_classes);
        etSearch  = view.findViewById(R.id.et_search);
        fabAdd    = view.findViewById(R.id.fab_add);

        adapter = new ClassCardAdapter(filteredClasses, cls -> {
            Intent i = new Intent(requireContext(), ClassDetailTeacherActivity.class);
            i.putExtra("classId", cls.getClassId());
            i.putExtra("className", cls.getClassName());
            startActivity(i);
        });
        rvClasses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvClasses.setAdapter(adapter);

        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CreateClassActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) { filterClasses(s.toString()); }
        });

        loadClasses();
    }

    private void loadClasses() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getTeacherClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            allClasses.clear();
            allClasses.addAll(classes);
            filterClasses(etSearch.getText().toString());
        });
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
}
