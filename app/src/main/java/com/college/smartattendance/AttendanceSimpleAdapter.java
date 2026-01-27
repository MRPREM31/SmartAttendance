package com.college.smartattendance;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

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

        // ✅ STUDENT NAME
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

        // ✅ COLORS
        h.txtStudentId.setTextColor(0xFF000000);
        h.txtTime.setTextColor(0xFF333333);
        h.txtDevice.setTextColor(0xFF666666);

        // 🔴 LONG PRESS TO DELETE (ANTI-PROXY)
        h.itemView.setOnLongClickListener(v -> {

            new AlertDialog.Builder(v.getContext())
                    .setTitle("Remove Attendance")
                    .setMessage("Delete attendance for:\n\n" +
                            model.getStudentName())
                    .setPositiveButton("Delete", (dialog, which) -> {

                        FirebaseFirestore.getInstance()
                                .collection("attendance_records")
                                .document(model.getDocId()) // MUST exist
                                .delete()
                                .addOnSuccessListener(aVoid -> {

                                    list.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, list.size());

                                    Toast.makeText(v.getContext(),
                                            "Attendance deleted",
                                            Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            return true;
        });
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
