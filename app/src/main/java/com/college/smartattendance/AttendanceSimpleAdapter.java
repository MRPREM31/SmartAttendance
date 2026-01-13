package com.college.smartattendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AttendanceSimpleAdapter
        extends RecyclerView.Adapter<AttendanceSimpleAdapter.VH> {

    private final List<AttendanceModel> list;

    public AttendanceSimpleAdapter(List<AttendanceModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_attendance, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {

        AttendanceModel model = list.get(position);

        // ✅ STUDENT NAME (IMPORTANT FIX)
        String name = model.getStudentName();
        String studentId = model.getStudentId();

        if (name == null || name.isEmpty()) {
            h.txtStudentId.setText("Unknown Student");
        } else {
            h.txtStudentId.setText(name + " (" + studentId + ")");
        }

        // ✅ TIME
        h.txtTime.setText("Time: " + model.getTime());

        // ✅ DEVICE
        h.txtDevice.setText("Device: " + model.getDeviceId());

        // ✅ FORCE TEXT COLOR (SAFETY)
        h.txtStudentId.setTextColor(0xFF000000); // BLACK
        h.txtTime.setTextColor(0xFF333333);
        h.txtDevice.setTextColor(0xFF666666);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView txtStudentId, txtTime, txtDevice;

        public VH(@NonNull View itemView) {
            super(itemView);
            txtStudentId = itemView.findViewById(R.id.txtStudentId);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtDevice = itemView.findViewById(R.id.txtDevice);
        }
    }
}
