package com.college.smartattendance;

public class AttendanceModel {

    private String docId;

    private String studentId;
    private String studentName;
    private String time;
    private String deviceId;
    private String status;

    public AttendanceModel() {}

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
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
