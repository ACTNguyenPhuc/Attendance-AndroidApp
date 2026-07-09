package com.example.attendanceapplication.fragments.teacher;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.activities.SessionManagementActivity;
import com.example.attendanceapplication.adapters.ShiftListAdapter;
import com.example.attendanceapplication.adapters.ShiftSwipeCallback;
import com.example.attendanceapplication.models.Shift;
import com.example.attendanceapplication.repositories.FirebaseRepository;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.example.attendanceapplication.utils.RequiredFieldUtils;
import com.example.attendanceapplication.utils.StudyShiftOptions;
import com.example.attendanceapplication.activities.ShiftAttendanceListActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.*;

public class ShiftsTabFragment extends Fragment {

    private RecyclerView rvShifts;
    private TextView tvEmpty;
    private ShiftListAdapter adapter;
    private List<Shift> shiftList = new ArrayList<>();
    private String classId, className;

    private final FirebaseRepository repo = FirebaseRepository.getInstance();

    // After opening/closing an attendance session, reload the whole
    // ClassDetailTeacherActivity so every tab reflects the latest state.
    private ActivityResultLauncher<Intent> sessionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isAdded()) requireActivity().recreate();
                });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shifts_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            classId   = getArguments().getString("classId");
            className = getArguments().getString("className");
        }

        rvShifts = view.findViewById(R.id.rv_shifts);
        tvEmpty  = view.findViewById(R.id.tv_empty);

        adapter = new ShiftListAdapter(shiftList, classId, className,
                this::openSession,
                shift -> {
                    if (Shift.STATUS_ONGOING.equals(shift.getStatus()) || shift.isAttendanceOpened()) {
                        openSession(shift);
                    } else if (Shift.STATUS_COMPLETED.equals(shift.getStatus())) {
                        Intent intent = new Intent(requireContext(), ShiftAttendanceListActivity.class);
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_ID, shift.getShiftId());
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_ID, classId);
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_CLASS_NAME, className);
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TITLE,
                                shift.getTitle() != null ? shift.getTitle() : shift.getClassName());
                        intent.putExtra(ShiftAttendanceListActivity.EXTRA_SHIFT_TIME,
                                shift.getDayOfWeekDisplay() + "  " + shift.getStartAt() + " - " + shift.getEndAt());
                        startActivity(intent);
                    }
                },
                this::showRescheduleDialog,
                this::showDeleteConfirmation);

        rvShifts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvShifts.setAdapter(adapter);

        // Swipe sang trái trên thẻ ca học để lộ hai lựa chọn "Xóa" và "Dời ca".
        new ItemTouchHelper(new ShiftSwipeCallback(adapter)).attachToRecyclerView(rvShifts);

        loadShifts();
    }

    private void openSession(Shift shift) {
        Intent intent = new Intent(requireContext(), SessionManagementActivity.class);
        intent.putExtra(SessionManagementActivity.EXTRA_SHIFT_ID, shift.getShiftId());
        intent.putExtra(SessionManagementActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(SessionManagementActivity.EXTRA_CLASS_NAME, className);
        SessionManagementActivity.putShiftExtras(intent, shift);
        sessionLauncher.launch(intent);
    }

    // The list updates itself in real time: getClassShifts() is backed by a Firestore
    // snapshot listener, so opening/closing a session (which rewrites the shift's
    // status/attendanceOpened) is reflected here immediately — no manual refresh needed.
    private void loadShifts() {
        if (classId == null) return;
        repo.getClassShifts(classId).observe(getViewLifecycleOwner(), shifts -> {
            List<Shift> sortedShifts = new ArrayList<>(shifts);
            AttendanceUtils.sortShiftsActiveFirst(sortedShifts);
            shiftList.clear();
            shiftList.addAll(sortedShifts);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(sortedShifts.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void showDeleteConfirmation(Shift shift) {
        if (shift == null) return;
        if (!ShiftListAdapter.isDeletable(shift)) {
            Toast.makeText(requireContext(),
                    "Chỉ có thể xóa ca học đang ở trạng thái sắp diễn ra",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String shiftTime = shift.getDate() + "  " + shift.getStartAt() + " - " + shift.getEndAt();
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa ca học?")
                .setMessage("Ca " + shiftTime + " sẽ bị xóa vĩnh viễn.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deleteShift(shift))
                .show();
    }

    private void deleteShift(Shift shift) {
        repo.deleteUpcomingShift(shift.getShiftId(),
                unused -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Đã xóa ca học", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    if (!isAdded()) return;
                    String message = error.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = "Không thể xóa ca học";
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Dialog dời ca học: chọn ngày/ca mới và phòng học bắt buộc rồi cập nhật ca.
     * Sau khi dời, danh sách tự cập nhật nhờ snapshot listener của getClassShifts().
     */
    private void showRescheduleDialog(Shift shift) {
        if (shift == null) return;
        // Bảo vệ thêm ở UI: ca đã mở điểm danh / đã kết thúc thì không cho dời.
        if (!ShiftListAdapter.isReschedulable(shift)) {
            Toast.makeText(requireContext(),
                    "Ca học đã mở điểm danh, không thể dời", Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_reschedule_shift, null, false);
        TextView tvCurrent = content.findViewById(R.id.tv_current_info);
        TextView tvDate    = content.findViewById(R.id.tv_date);
        TextView tvDay     = content.findViewById(R.id.tv_day_of_week);
        MaterialAutoCompleteTextView shiftDropdown = content.findViewById(R.id.dropdown_shift);
        TextInputEditText etRoom = content.findViewById(R.id.et_room);
        TextInputLayout tilShift = content.findViewById(R.id.til_shift);
        TextInputLayout tilRoom = content.findViewById(R.id.til_room);

        RequiredFieldUtils.markRequired(requireContext(),
                (TextView) content.findViewById(R.id.tv_date_label));
        RequiredFieldUtils.markRequired(requireContext(), tilShift);
        RequiredFieldUtils.markRequired(requireContext(), tilRoom);

        tvCurrent.setText("Ca hiện tại: " + shift.getDayOfWeekDisplay() + " " + shift.getDate()
                + "  " + shift.getStartAt() + " - " + shift.getEndAt());

        // picked = { date, startAt, endAt }. Chỉ giữ giờ hiện tại nếu thuộc 5 ca cố định.
        final String[] picked = {"", "", ""};
        shiftDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, StudyShiftOptions.getLabels()));
        StudyShiftOptions.Option currentOption = StudyShiftOptions.findByTimes(
                shift.getStartAt(), shift.getEndAt());
        if (currentOption != null) {
            picked[1] = currentOption.getStartAt();
            picked[2] = currentOption.getEndAt();
            shiftDropdown.setText(currentOption.getLabel(), false);
        }
        shiftDropdown.setOnItemClickListener((parent, view, position, id) -> {
            StudyShiftOptions.Option option = StudyShiftOptions.get(position);
            picked[1] = option.getStartAt();
            picked[2] = option.getEndAt();
        });
        if (shift.getRoom() != null) etRoom.setText(shift.getRoom());

        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(requireContext(), (view, y, m, d) -> {
                picked[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                tvDate.setText(picked[0]);
                // Tính thứ từ ngày đã chọn để hiển thị (repo cũng tính lại dayOfWeek khi lưu).
                Calendar pc = Calendar.getInstance();
                pc.set(y, m, d);
                tvDay.setVisibility(View.VISIBLE);
                tvDay.setText("Thứ: " + weekdayNameVN(pc.get(Calendar.DAY_OF_WEEK)));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            // Ngày dời phải lớn hơn hôm nay → chỉ cho chọn từ ngày mai.
            Calendar min = Calendar.getInstance();
            min.add(Calendar.DAY_OF_MONTH, 1);
            dp.getDatePicker().setMinDate(min.getTimeInMillis());
            dp.show();
        });
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Dời ca học")
                .setView(content)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Dời ca", null)
                .create();
        dialog.show();

        // Validate trước khi đóng dialog (không tự dismiss nếu dữ liệu chưa hợp lệ).
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String date = picked[0], start = picked[1], end = picked[2];
            if (date.isEmpty() || !StudyShiftOptions.isValid(start, end)) {
                Toast.makeText(requireContext(), "Vui lòng chọn ngày và ca học", Toast.LENGTH_SHORT).show();
                return;
            }
            if (date.compareTo(todayString()) <= 0) {
                Toast.makeText(requireContext(), "Ngày dời phải lớn hơn ngày hiện tại", Toast.LENGTH_SHORT).show();
                return;
            }
            String room = etRoom.getText() == null ? "" : etRoom.getText().toString().trim();
            tilRoom.setError(null);
            if (room.isEmpty()) {
                tilRoom.setError("Vui lòng nhập phòng học");
                etRoom.requestFocus();
                return;
            }

            repo.rescheduleShift(shift.getShiftId(), classId, date, start, end, room,
                    r -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Đã dời ca học", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    },
                    e -> {
                        if (!isAdded()) return;
                        if (e instanceof FirebaseRepository.TeacherScheduleConflictException) {
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Xung đột lịch giảng")
                                    .setMessage(e.getMessage())
                                    .setPositiveButton("Đã hiểu", null)
                                    .show();
                            return;
                        }
                        if (e instanceof FirebaseRepository.RoomConflictException) {
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Xung đột phòng học")
                                    .setMessage(e.getMessage())
                                    .setPositiveButton("Đã hiểu", null)
                                    .show();
                            return;
                        }
                        String msg = (e instanceof FirebaseRepository.ShiftConflictException)
                                ? e.getMessage() : "Lỗi: " + e.getMessage();
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private String todayString() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private String weekdayNameVN(int calendarDayOfWeek) {
        String[] days = {"Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
        int idx = calendarDayOfWeek - 1;
        if (idx >= 0 && idx < days.length) return days[idx];
        return "";
    }
}
