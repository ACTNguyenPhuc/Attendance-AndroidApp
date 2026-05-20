package com.example.attendanceapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.attendanceapplication.R;
import com.example.attendanceapplication.models.Attendance;
import com.example.attendanceapplication.utils.AttendanceUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RealtimeAttendanceAdapter extends RecyclerView.Adapter<RealtimeAttendanceAdapter.ViewHolder> {

    private final List<Attendance> attendanceList;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public RealtimeAttendanceAdapter(List<Attendance> attendanceList) {
        this.attendanceList = attendanceList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Attendance att = attendanceList.get(position);
        holder.tvName.setText(att.getStudentName() != null ? att.getStudentName() : att.getStudentId());
        holder.tvCode.setText(att.getStudentCode() != null ? att.getStudentCode() : "");
        if (att.getCheckinTime() != null) {
            holder.tvTime.setText(timeFormat.format(att.getCheckinTime().toDate()));
        }
        holder.tvDistance.setText(AttendanceUtils.formatDistance(att.getDistance()));
    }

    @Override
    public int getItemCount() { return attendanceList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCode, tvTime, tvDistance;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName     = itemView.findViewById(R.id.tv_student_name);
            tvCode     = itemView.findViewById(R.id.tv_student_code);
            tvTime     = itemView.findViewById(R.id.tv_checkin_time);
            tvDistance = itemView.findViewById(R.id.tv_distance);
        }
    }
}
