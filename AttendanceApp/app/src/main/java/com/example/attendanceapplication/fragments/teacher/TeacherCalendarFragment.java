package com.example.attendanceapplication.fragments.teacher;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.example.attendanceapplication.R;

public class TeacherCalendarFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }
}
