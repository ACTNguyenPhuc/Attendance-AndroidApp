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
import com.example.attendanceapplication.adapters.ClassCardAdapter;
import com.example.attendanceapplication.models.ClassModel;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.*;

public class StudentClassListFragment extends Fragment {

    private RecyclerView rvClasses;
    private EditText etSearch;
    private TextView tvEmpty;
    private FloatingActionButton fabJoin;
    private ClassCardAdapter adapter;
    private List<ClassModel> allClasses = new ArrayList<>();
    private List<ClassModel> filteredClasses = new ArrayList<>();

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

        adapter = new ClassCardAdapter(filteredClasses, cls -> {
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
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        repo.getStudentClasses(uid).observe(getViewLifecycleOwner(), classes -> {
            allClasses.clear();
            allClasses.addAll(classes);
            filterClasses("");
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
        if (tvEmpty != null)
            tvEmpty.setVisibility(filteredClasses.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
