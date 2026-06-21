package com.example.attendanceapplication.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    public static final String ROLE_TEACHER = "teacher";
    public static final String ROLE_STUDENT = "student";

    private String uid;
    private String studentCode;
    private String name;
    private String email;
    private String role;
    private String avatarUrl;
    private Timestamp createdAt;

    public User() {}

    public User(String uid, String studentCode, String name, String email, String role) {
        this.uid = uid;
        this.studentCode = studentCode;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    // Getters & Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Exclude
    public boolean isTeacher() { return ROLE_TEACHER.equals(role); }

    @Exclude
    public boolean isStudent() { return ROLE_STUDENT.equals(role); }
}
