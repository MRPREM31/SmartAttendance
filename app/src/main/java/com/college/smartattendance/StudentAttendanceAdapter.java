package com.college.smartattendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StudentAttendanceAdapter
        extends RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder> {

    private final List<StudentAttendanceModel> list;

    public StudentAttendanceAdapter(List<StudentAttendanceModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_student_attendance, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        StudentAttendanceModel model = list.get(position);

        holder.txtSubject.setText("Subject: " + model.subject);
        holder.txtTeacher.setText("Teacher: " + model.teacherName);
        holder.txtTimeSlot.setText("Time Slot: " + model.timeSlot);
        holder.txtStatus.setText("Status: " + model.status);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtSubject, txtTeacher, txtTimeSlot, txtStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtSubject = itemView.findViewById(R.id.txtSubject);
            txtTeacher = itemView.findViewById(R.id.txtTeacher);
            txtTimeSlot = itemView.findViewById(R.id.txtTimeSlot);
            txtStatus = itemView.findViewById(R.id.txtStatus);
        }
    }
}
