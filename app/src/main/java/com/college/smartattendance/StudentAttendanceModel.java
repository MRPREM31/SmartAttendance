package com.college.smartattendance;

public class StudentAttendanceModel {

    public String subject, teacherName, timeSlot, status, time;

    public StudentAttendanceModel() {}

    public StudentAttendanceModel(String subject, String teacherName,
                                  String timeSlot, String status, String time) {
        this.subject = subject;
        this.teacherName = teacherName;
        this.timeSlot = timeSlot;
        this.status = status;
        this.time = time;
    }
}
