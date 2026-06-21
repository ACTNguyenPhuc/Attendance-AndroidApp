package com.example.attendanceapplication.repositories;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.attendanceapplication.models.*;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class FirebaseRepository {
    private static final String TAG = "FirebaseRepository";

    // Collection names
    private static final String COL_USERS       = "users";
    private static final String COL_CLASSES     = "classes";
    private static final String COL_SHIFTS      = "shifts";
    private static final String COL_SESSIONS    = "sessions";
    private static final String COL_ATTENDANCES = "attendances";
    private static final String COL_ENROLLMENTS = "enrollments";

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private static FirebaseRepository instance;

    private FirebaseRepository() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized FirebaseRepository getInstance() {
        if (instance == null) instance = new FirebaseRepository();
        return instance;
    }

    // ==================== AUTH ====================

    public FirebaseUser getCurrentUser() { return mAuth.getCurrentUser(); }

    public void login(String email, String password,
                      OnSuccessListener<FirebaseUser> onSuccess,
                      OnFailureListener onFailure) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> onSuccess.onSuccess(result.getUser()))
                .addOnFailureListener(onFailure::onFailure);
    }

    public void register(String email, String password, User userInfo,
                         OnSuccessListener<String> onSuccess,
                         OnFailureListener onFailure) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();
                    userInfo.setUid(uid);
                    userInfo.setCreatedAt(Timestamp.now());
                    db.collection(COL_USERS).document(uid).set(userInfo)
                            .addOnSuccessListener(aVoid -> onSuccess.onSuccess(uid))
                            .addOnFailureListener(onFailure::onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void getUserProfile(String uid,
                               OnSuccessListener<User> onSuccess,
                               OnFailureListener onFailure) {
        db.collection(COL_USERS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        User user = doc.toObject(User.class);
                        onSuccess.onSuccess(user);
                    } else {
                        onFailure.onFailure(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void signOut() { mAuth.signOut(); }

    // ==================== CLASSES ====================

    public void createClass(ClassModel classModel,
                            OnSuccessListener<String> onSuccess,
                            OnFailureListener onFailure) {
        classModel.setCreatedAt(Timestamp.now());
        db.collection(COL_CLASSES).document(classModel.getClassId()).set(classModel)
                .addOnSuccessListener(aVoid -> {
                    // Auto-generate shifts after class creation
                    generateShifts(classModel,
                            () -> onSuccess.onSuccess(classModel.getClassId()),
                            onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void updateClassInfo(String classId, String className, String room,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        if (classId == null || classId.isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Invalid classId"));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("className", className);
        updates.put("room", room);

        db.collection(COL_CLASSES).document(classId).update(updates)
                .addOnSuccessListener(aVoid -> updateShiftsForClass(classId, updates, onSuccess, onFailure))
                .addOnFailureListener(onFailure::onFailure);
    }

        public void deleteClass(String classId,
                    OnSuccessListener<Void> onSuccess,
                    OnFailureListener onFailure) {
        if (classId == null || classId.isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Invalid classId"));
            return;
        }

        deleteByQuery(db.collection(COL_ATTENDANCES).whereEqualTo("classId", classId), () ->
            deleteByQuery(db.collection(COL_SESSIONS).whereEqualTo("classId", classId), () ->
                deleteByQuery(db.collection(COL_SHIFTS).whereEqualTo("classId", classId), () ->
                    deleteByQuery(db.collection(COL_ENROLLMENTS).whereEqualTo("classId", classId), () ->
                        db.collection(COL_CLASSES).document(classId).delete()
                            .addOnSuccessListener(onSuccess::onSuccess)
                            .addOnFailureListener(onFailure::onFailure)
                    , onFailure)
                , onFailure)
            , onFailure)
        , onFailure);
        }

    /**
     * Auto-generate shift documents for every scheduled day in the semester.
     */
    private void generateShifts(ClassModel classModel,
                                 Runnable onComplete,
                                 OnFailureListener onFailure) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(classModel.getStartDate()));
            Date endDate = sdf.parse(classModel.getEndDate());

            WriteBatch batch = db.batch();
            int batchCount = 0;
            List<List<Object>> batchGroups = new ArrayList<>();
            List<Object> currentGroup = new ArrayList<>();

            while (!cal.getTime().after(endDate)) {
                // Calendar: 1=Sun,2=Mon,...,7=Sat → VN: 2=Mon,...,8=Sun
                int calDow = cal.get(Calendar.DAY_OF_WEEK);
                int vnDow = AttendanceUtils.getDayOfWeekVN(calDow);

                if (classModel.getSchedule() != null &&
                        classModel.getSchedule().contains(vnDow)) {
                    String dateStr = sdf.format(cal.getTime());
                    String shiftId = classModel.getClassId() + "_" + dateStr;

                    Shift shift = new Shift();
                    shift.setShiftId(shiftId);
                    shift.setClassId(classModel.getClassId());
                    shift.setClassName(classModel.getClassName());
                    shift.setTitle("Buổi học ngày " + dateStr);
                    shift.setDate(dateStr);
                    shift.setDayOfWeek(vnDow);
                    shift.setStartAt(classModel.getStartAt());
                    shift.setEndAt(classModel.getEndAt());
                    shift.setRoom(classModel.getRoom() != null ? classModel.getRoom() : "");
                    shift.setStatus(Shift.STATUS_UPCOMING);
                    shift.setAttendanceOpened(false);
                    shift.setCreatedAt(Timestamp.now());

                    DocumentReference docRef = db.collection(COL_SHIFTS).document(shiftId);
                    batch.set(docRef, shift);
                    batchCount++;

                    // Firestore batch limit is 500
                    if (batchCount == 499) {
                        batch.commit();
                        batch = db.batch();
                        batchCount = 0;
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }

            batch.commit()
                    .addOnSuccessListener(aVoid -> onComplete.run())
                    .addOnFailureListener(onFailure::onFailure);
        } catch (Exception e) {
            onFailure.onFailure(e);
        }
    }

    public LiveData<List<ClassModel>> getTeacherClasses(String teacherId) {
        MutableLiveData<List<ClassModel>> liveData = new MutableLiveData<>();
        db.collection(COL_CLASSES)
                .whereEqualTo("teacherId", teacherId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) { Log.e(TAG, "getTeacherClasses error", error); return; }
                    List<ClassModel> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ClassModel c = doc.toObject(ClassModel.class);
                            if (c != null) list.add(c);
                        }
                    }
                    liveData.postValue(list);
                });
        return liveData;
    }

    public LiveData<List<ClassModel>> getStudentClasses(String studentId) {
        MutableLiveData<List<ClassModel>> liveData = new MutableLiveData<>();
        // First get enrollments, then fetch classes
        db.collection(COL_ENROLLMENTS)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    List<String> classIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Enrollment e = doc.toObject(Enrollment.class);
                        if (e != null) classIds.add(e.getClassId());
                    }
                    if (classIds.isEmpty()) { liveData.postValue(new ArrayList<>()); return; }

                    // Fetch class details
                    db.collection(COL_CLASSES)
                            .whereIn(FieldPath.documentId(), classIds)
                            .get()
                            .addOnSuccessListener(classDocs -> {
                                List<ClassModel> classList = new ArrayList<>();
                                for (DocumentSnapshot doc : classDocs.getDocuments()) {
                                    ClassModel c = doc.toObject(ClassModel.class);
                                    if (c != null) classList.add(c);
                                }
                                liveData.postValue(classList);
                            });
                });
        return liveData;
    }

    public void getClassById(String classId,
                             OnSuccessListener<ClassModel> onSuccess,
                             OnFailureListener onFailure) {
        db.collection(COL_CLASSES).document(classId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) onSuccess.onSuccess(doc.toObject(ClassModel.class));
                    else onFailure.onFailure(new Exception("Class not found"));
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    // ==================== ENROLLMENTS ====================

    public void enrollStudent(String studentId, String classId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        String docId = studentId + "_" + classId;
        Enrollment enrollment = new Enrollment(studentId, classId);
        enrollment.setJoinedAt(Timestamp.now());
        db.collection(COL_ENROLLMENTS).document(docId).set(enrollment)
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onFailure::onFailure);
    }

    public void checkEnrollment(String studentId, String classId,
                                OnSuccessListener<Boolean> onSuccess) {
        String docId = studentId + "_" + classId;
        db.collection(COL_ENROLLMENTS).document(docId).get()
                .addOnSuccessListener(doc -> onSuccess.onSuccess(doc.exists()));
    }

        public void removeEnrollment(String studentId, String classId,
                     OnSuccessListener<Void> onSuccess,
                     OnFailureListener onFailure) {
        String docId = studentId + "_" + classId;
        db.collection(COL_ENROLLMENTS).document(docId).delete()
            .addOnSuccessListener(onSuccess::onSuccess)
            .addOnFailureListener(onFailure::onFailure);
        }

    public void getClassStudents(String classId,
                                 OnSuccessListener<List<User>> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection(COL_ENROLLMENTS)
                .whereEqualTo("classId", classId)
                .whereEqualTo("status", "active")
                .get()
                .addOnSuccessListener(enrollmentDocs -> {
                    List<String> studentIds = new ArrayList<>();
                    for (DocumentSnapshot doc : enrollmentDocs.getDocuments()) {
                        Enrollment e = doc.toObject(Enrollment.class);
                        if (e != null) studentIds.add(e.getStudentId());
                    }
                    if (studentIds.isEmpty()) { onSuccess.onSuccess(new ArrayList<>()); return; }

                    db.collection(COL_USERS)
                            .whereIn(FieldPath.documentId(), studentIds)
                            .get()
                            .addOnSuccessListener(userDocs -> {
                                List<User> users = new ArrayList<>();
                                for (DocumentSnapshot doc : userDocs.getDocuments()) {
                                    User u = doc.toObject(User.class);
                                    if (u != null) users.add(u);
                                }
                                onSuccess.onSuccess(users);
                            })
                            .addOnFailureListener(onFailure::onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    /**
     * Returns student accounts that do not have an active enrollment in the class.
     */
    public void getUnenrolledStudents(String classId,
                                      OnSuccessListener<List<User>> onSuccess,
                                      OnFailureListener onFailure) {
        db.collection(COL_ENROLLMENTS)
                .whereEqualTo("classId", classId)
                .whereEqualTo("status", "active")
                .get()
                .addOnSuccessListener(enrollmentDocs -> {
                    Set<String> enrolledStudentIds = new HashSet<>();
                    for (DocumentSnapshot doc : enrollmentDocs.getDocuments()) {
                        Enrollment enrollment = doc.toObject(Enrollment.class);
                        if (enrollment != null && enrollment.getStudentId() != null) {
                            enrolledStudentIds.add(enrollment.getStudentId());
                        }
                    }

                    db.collection(COL_USERS)
                            .whereEqualTo("role", User.ROLE_STUDENT)
                            .get()
                            .addOnSuccessListener(userDocs -> {
                                List<User> students = new ArrayList<>();
                                for (DocumentSnapshot doc : userDocs.getDocuments()) {
                                    User student = doc.toObject(User.class);
                                    if (student != null && (student.getUid() == null ||
                                            student.getUid().isEmpty())) {
                                        student.setUid(doc.getId());
                                    }
                                    if (student != null && student.getUid() != null &&
                                            !enrolledStudentIds.contains(student.getUid())) {
                                        students.add(student);
                                    }
                                }
                                Collections.sort(students, (first, second) -> {
                                    String firstName = first.getName() == null ? "" : first.getName();
                                    String secondName = second.getName() == null ? "" : second.getName();
                                    return firstName.compareToIgnoreCase(secondName);
                                });
                                onSuccess.onSuccess(students);
                            })
                            .addOnFailureListener(onFailure::onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void findStudentByCodeOrEmail(String query,
                                         OnSuccessListener<User> onSuccess,
                                         OnFailureListener onFailure) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Empty query"));
            return;
        }

        // Heuristic: email contains '@'
        if (q.contains("@")) {
            db.collection(COL_USERS)
                    .whereEqualTo("email", q)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap.isEmpty()) { onSuccess.onSuccess(null); return; }
                        DocumentSnapshot doc = snap.getDocuments().get(0);
                        User u = doc.toObject(User.class);
                        if (u != null && (u.getUid() == null || u.getUid().isEmpty())) {
                            u.setUid(doc.getId());
                        }
                        onSuccess.onSuccess(u);
                    })
                    .addOnFailureListener(onFailure::onFailure);
        } else {
            db.collection(COL_USERS)
                    .whereEqualTo("studentCode", q)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap.isEmpty()) { onSuccess.onSuccess(null); return; }
                        DocumentSnapshot doc = snap.getDocuments().get(0);
                        User u = doc.toObject(User.class);
                        if (u != null && (u.getUid() == null || u.getUid().isEmpty())) {
                            u.setUid(doc.getId());
                        }
                        onSuccess.onSuccess(u);
                    })
                    .addOnFailureListener(onFailure::onFailure);
        }
    }

    public void findStudentsByQuery(String query, int limit,
                                    OnSuccessListener<List<User>> onSuccess,
                                    OnFailureListener onFailure) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }

        int safeLimit = Math.max(5, Math.min(limit, 50));
        List<Query> queries = new ArrayList<>();

        if (q.contains("@")) {
            queries.add(db.collection(COL_USERS)
                    .orderBy("email")
                    .startAt(q)
                    .endAt(q + "\uf8ff")
                    .limit(safeLimit));
        } else {
            queries.add(db.collection(COL_USERS)
                    .orderBy("studentCode")
                    .startAt(q)
                    .endAt(q + "\uf8ff")
                    .limit(safeLimit));
            queries.add(db.collection(COL_USERS)
                    .orderBy("name")
                    .startAt(q)
                    .endAt(q + "\uf8ff")
                    .limit(safeLimit));
            queries.add(db.collection(COL_USERS)
                    .orderBy("email")
                    .startAt(q)
                    .endAt(q + "\uf8ff")
                    .limit(safeLimit));
        }

        Map<String, User> merged = new LinkedHashMap<>();
        final int[] pending = {queries.size()};
        final boolean[] failed = {false};

        for (Query qry : queries) {
            qry.get()
                    .addOnSuccessListener(snap -> {
                        if (failed[0]) return;
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            User u = doc.toObject(User.class);
                            if (u != null) {
                                if (u.getUid() == null || u.getUid().isEmpty()) {
                                    u.setUid(doc.getId());
                                }
                                merged.put(u.getUid(), u);
                            }
                        }
                        pending[0]--;
                        if (pending[0] == 0) {
                            onSuccess.onSuccess(new ArrayList<>(merged.values()));
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (failed[0]) return;
                        failed[0] = true;
                        onFailure.onFailure(e);
                    });
        }
    }

    // ==================== SHIFTS ====================

    public LiveData<List<Shift>> getClassShifts(String classId) {
        MutableLiveData<List<Shift>> liveData = new MutableLiveData<>();
        db.collection(COL_SHIFTS)
                .whereEqualTo("classId", classId)
                .orderBy("date")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "getClassShifts listener error for class " + classId, error);
                        return;
                    }
                    List<Shift> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Shift s = doc.toObject(Shift.class);
                            if (s != null) list.add(s);
                        }
                    }
                    liveData.postValue(list);
                });
        return liveData;
    }

    public void getShiftsByDate(List<String> classIds, String date,
                                OnSuccessListener<List<Shift>> onSuccess) {
        if (classIds.isEmpty()) { onSuccess.onSuccess(new ArrayList<>()); return; }
        db.collection(COL_SHIFTS)
                .whereIn("classId", classIds)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(docs -> {
                    List<Shift> list = new ArrayList<>();
                    for (DocumentSnapshot doc : docs.getDocuments()) {
                        Shift s = doc.toObject(Shift.class);
                        if (s != null) list.add(s);
                    }
                    onSuccess.onSuccess(list);
                });
    }

    public void getShiftsForClasses(List<String> classIds,
                                    OnSuccessListener<List<Shift>> onSuccess) {
        if (classIds.isEmpty()) { onSuccess.onSuccess(new ArrayList<>()); return; }
        List<Shift> result = new ArrayList<>();
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < classIds.size(); i += 10) {
            chunks.add(classIds.subList(i, Math.min(i + 10, classIds.size())));
        }
        final int[] pending = {chunks.size()};
        for (List<String> chunk : chunks) {
            db.collection(COL_SHIFTS)
                    .whereIn("classId", chunk)
                    .get()
                    .addOnSuccessListener(docs -> {
                        for (DocumentSnapshot doc : docs.getDocuments()) {
                            Shift s = doc.toObject(Shift.class);
                            if (s != null) result.add(s);
                        }
                        pending[0]--;
                        if (pending[0] == 0) onSuccess.onSuccess(result);
                    })
                    .addOnFailureListener(e -> {
                        pending[0]--;
                        if (pending[0] == 0) onSuccess.onSuccess(result);
                    });
        }
    }

    public void updateShiftStatus(String shiftId, String status, boolean attendanceOpened,
                                  String sessionId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("attendanceOpened", attendanceOpened);
        if (sessionId != null) updates.put("attendanceSessionId", sessionId);
        db.collection(COL_SHIFTS).document(shiftId).update(updates)
                .addOnFailureListener(e ->
                        Log.e(TAG, "updateShiftStatus failed for shift " + shiftId, e));
    }

    // ==================== SESSIONS ====================

    public void createSession(Session session,
                              OnSuccessListener<String> onSuccess,
                              OnFailureListener onFailure) {
        session.setStartTime(Timestamp.now());
        session.setActive(true);

        // Write the session AND flip the linked shift to "ongoing" in one atomic
        // batch. Both writes apply to the local cache immediately (latency
        // compensation), so the shift list's real-time listener reflects the new
        // status right away instead of waiting for the session write's server ack.
        Map<String, Object> shiftUpdates = new HashMap<>();
        shiftUpdates.put("status", Shift.STATUS_ONGOING);
        shiftUpdates.put("attendanceOpened", true);
        shiftUpdates.put("attendanceSessionId", session.getSessionId());

        WriteBatch batch = db.batch();
        batch.set(db.collection(COL_SESSIONS).document(session.getSessionId()), session);
        batch.update(db.collection(COL_SHIFTS).document(session.getShiftId()), shiftUpdates);
        batch.commit()
                .addOnSuccessListener(unused -> onSuccess.onSuccess(session.getSessionId()))
                .addOnFailureListener(onFailure::onFailure);
    }

    public void getSession(String sessionId,
                           OnSuccessListener<Session> onSuccess,
                           OnFailureListener onFailure) {
        db.collection(COL_SESSIONS).document(sessionId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) onSuccess.onSuccess(doc.toObject(Session.class));
                    else onFailure.onFailure(new Exception("Session not found"));
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void closeSession(String sessionId, String shiftId,
                             OnSuccessListener<Void> onSuccess,
                             OnFailureListener onFailure) {
        Map<String, Object> sessionUpdates = new HashMap<>();
        sessionUpdates.put("isActive", false);
        sessionUpdates.put("endTime", Timestamp.now());

        Map<String, Object> shiftUpdates = new HashMap<>();
        shiftUpdates.put("status", Shift.STATUS_COMPLETED);
        shiftUpdates.put("attendanceOpened", false);

        WriteBatch batch = db.batch();
        batch.update(db.collection(COL_SESSIONS).document(sessionId), sessionUpdates);
        batch.update(db.collection(COL_SHIFTS).document(shiftId), shiftUpdates);
        batch.commit()
                .addOnSuccessListener(unused -> onSuccess.onSuccess(null))
                .addOnFailureListener(onFailure::onFailure);
    }

    // ==================== ATTENDANCE ====================

    public void saveAttendance(Attendance attendance,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        attendance.setCheckinTime(Timestamp.now());
        if (attendance.getAttendanceId() == null || attendance.getAttendanceId().isEmpty()) {
            attendance.setAttendanceId(db.collection(COL_ATTENDANCES).document().getId());
        }
        db.collection(COL_ATTENDANCES)
                .document(attendance.getAttendanceId())
                .set(attendance)
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onFailure::onFailure);
    }

    public void getClassAttendances(String classId,
                                    OnSuccessListener<List<Attendance>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection(COL_ATTENDANCES)
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Attendance> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Attendance a = doc.toObject(Attendance.class);
                        if (a != null) list.add(a);
                    }
                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void getShiftAttendances(String shiftId,
                                    OnSuccessListener<List<Attendance>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection(COL_ATTENDANCES)
                .whereEqualTo("shiftId", shiftId)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Attendance> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Attendance a = doc.toObject(Attendance.class);
                        if (a != null) list.add(a);
                    }
                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

            public void getStudentAttendanceCount(String classId, String studentId,
                              OnSuccessListener<Integer> onSuccess,
                              OnFailureListener onFailure) {
            db.collection(COL_ATTENDANCES)
                .whereEqualTo("classId", classId)
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnSuccessListener(snap -> onSuccess.onSuccess(snap.size()))
                .addOnFailureListener(onFailure::onFailure);
            }

            public void deleteStudentAttendancesForClass(String studentId, String classId,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
            Query query = db.collection(COL_ATTENDANCES)
                .whereEqualTo("classId", classId)
                .whereEqualTo("studentId", studentId);
            deleteByQuery(query, () -> onSuccess.onSuccess(null), onFailure);
            }

    private void updateShiftsForClass(String classId, Map<String, Object> updates,
                                      OnSuccessListener<Void> onSuccess,
                                      OnFailureListener onFailure) {
        db.collection(COL_SHIFTS)
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(snap -> {
                    List<DocumentSnapshot> docs = snap.getDocuments();
                    if (docs.isEmpty()) { onSuccess.onSuccess(null); return; }
                    updateDocsInBatches(docs, 0, updates, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    private void updateDocsInBatches(List<DocumentSnapshot> docs, int start,
                                     Map<String, Object> updates,
                                     OnSuccessListener<Void> onSuccess,
                                     OnFailureListener onFailure) {
        int end = Math.min(start + 450, docs.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            batch.update(docs.get(i).getReference(), updates);
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    if (end >= docs.size()) onSuccess.onSuccess(null);
                    else updateDocsInBatches(docs, end, updates, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    private void deleteByQuery(Query query, Runnable onSuccess, OnFailureListener onFailure) {
        query.get()
                .addOnSuccessListener(snap -> {
                    List<DocumentSnapshot> docs = snap.getDocuments();
                    if (docs.isEmpty()) { onSuccess.run(); return; }
                    deleteDocsInBatches(docs, 0, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    private void deleteDocsInBatches(List<DocumentSnapshot> docs, int start,
                                     Runnable onSuccess, OnFailureListener onFailure) {
        int end = Math.min(start + 450, docs.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            batch.delete(docs.get(i).getReference());
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    if (end >= docs.size()) onSuccess.run();
                    else deleteDocsInBatches(docs, end, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void checkAlreadyAttended(String studentId, String sessionId,
                                     OnSuccessListener<Boolean> onSuccess) {
        db.collection(COL_ATTENDANCES)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("sessionId", sessionId)
                .get()
                .addOnSuccessListener(docs -> onSuccess.onSuccess(!docs.isEmpty()));
    }

    /**
     * Anti-cheat: mỗi buổi học (shift) chỉ chấp nhận một deviceId duy nhất cho mỗi
     * bản ghi điểm danh. Trả về true nếu thiết bị này đã được dùng để điểm danh
     * buổi đó bởi một sinh viên KHÁC (chống điểm danh hộ trên cùng một máy).
     * Lưu ý: chỉ kiểm tra phía client; chỉ chặn được người dùng app bình thường.
     */
    public void checkDeviceUsedInShift(String shiftId, String deviceId, String excludeStudentId,
                                       OnSuccessListener<Boolean> onSuccess) {
        if (deviceId == null || deviceId.isEmpty()) { onSuccess.onSuccess(false); return; }
        db.collection(COL_ATTENDANCES)
                .whereEqualTo("shiftId", shiftId)
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(snap -> {
                    boolean used = false;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Attendance a = doc.toObject(Attendance.class);
                        if (a == null) continue;
                        if (excludeStudentId == null || !excludeStudentId.equals(a.getStudentId())) {
                            used = true;
                            break;
                        }
                    }
                    onSuccess.onSuccess(used);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "checkDeviceUsedInShift failed: " + e.getMessage());
                    onSuccess.onSuccess(false); // fail-open để tránh chặn nhầm khi lỗi mạng
                });
    }

    /**
     * Real-time listener for teacher to monitor attendance in a session.
     */
    public ListenerRegistration listenToSessionAttendance(String sessionId,
                                                          OnSuccessListener<List<Attendance>> onUpdate) {
        return db.collection(COL_ATTENDANCES)
                .whereEqualTo("sessionId", sessionId)
                .orderBy("checkinTime")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    List<Attendance> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Attendance a = doc.toObject(Attendance.class);
                        if (a != null) list.add(a);
                    }
                    onUpdate.onSuccess(list);
                });
    }

    public void getStudentAttendanceHistory(String studentId, String classId,
                                            OnSuccessListener<List<Attendance>> onSuccess,
                                            OnFailureListener onFailure) {
        Query query = db.collection(COL_ATTENDANCES)
                .whereEqualTo("studentId", studentId);
        if (classId != null && !classId.isEmpty()) {
            query = query.whereEqualTo("classId", classId);
        }
        query.orderBy("checkinTime", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(docs -> {
                    List<Attendance> list = new ArrayList<>();
                    for (DocumentSnapshot doc : docs.getDocuments()) {
                        Attendance a = doc.toObject(Attendance.class);
                        if (a != null) list.add(a);
                    }
                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    // ==================== HELPER INTERFACES ====================

    public interface OnSuccessListener<T> {
        void onSuccess(T result);
    }

    public interface OnFailureListener {
        void onFailure(Exception e);
    }
}
