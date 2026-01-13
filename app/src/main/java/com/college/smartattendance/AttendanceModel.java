package com.college.smartattendance;

public class AttendanceModel {

    private String studentId;
    private String date;
    private String time;
    private String deviceId;
    private String status;

    // 🔹 REQUIRED empty constructor for Firebase
    public AttendanceModel() {
    }

    // 🔹 Getters
    public String getStudentId() {
        return studentId;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getStatus() {
        return status;
    }
}
