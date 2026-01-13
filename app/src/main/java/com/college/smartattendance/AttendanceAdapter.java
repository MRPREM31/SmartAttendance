package com.college.smartattendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private List<AttendanceModel> list;

    public AttendanceAdapter(List<AttendanceModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        AttendanceModel model = list.get(position);

        holder.txtStudentId.setText("Student ID: " + model.getStudentId());
        holder.txtTime.setText("Time: " + model.getTime());
        holder.txtDevice.setText("Device: " + model.getDeviceId());
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtStudentId, txtTime, txtDevice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtStudentId = itemView.findViewById(R.id.txtStudentId);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtDevice = itemView.findViewById(R.id.txtDevice);
        }
    }
}
