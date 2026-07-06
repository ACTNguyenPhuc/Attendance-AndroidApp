package com.example.attendanceapplication.repositories;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.attendanceapplication.models.*;
import com.example.attendanceapplication.utils.AttendanceUtils;
import com.example.attendanceapplication.utils.RoomConflictChecker;
import com.example.attendanceapplication.utils.TeacherScheduleConflictChecker;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
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

    // Firestore giới hạn số phần tử cho mỗi truy vấn whereIn/in — cắt list dài
    // thành các đoạn không vượt quá giới hạn này (xem runChunkedWhereIn).
    private static final int WHERE_IN_CHUNK_SIZE = 10;

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

    /**
     * Đổi mật khẩu người dùng hiện tại. Xác thực lại (reauthenticate) bằng mật khẩu
     * cũ trước khi cập nhật vì Firebase yêu cầu phiên đăng nhập "gần đây" cho thao
     * tác nhạy cảm này.
     */
    public void changePassword(String oldPassword, String newPassword,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            onFailure.onFailure(new Exception("Chưa đăng nhập"));
            return;
        }
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> user.updatePassword(newPassword)
                        .addOnSuccessListener(onSuccess::onSuccess)
                        .addOnFailureListener(onFailure::onFailure))
                .addOnFailureListener(onFailure::onFailure);
    }

    // ==================== CLASSES ====================

    /** Báo mã lớp đã tồn tại để màn hình tạo lớp hiển thị thông báo riêng. */
    public static class DuplicateClassIdException extends Exception {
        public DuplicateClassIdException(String classId) {
            super("Mã lớp " + classId + " đã tồn tại");
        }
    }

    /** Báo một ca của lớp mới đang chiếm phòng đã được lớp khác sử dụng. */
    public static class RoomConflictException extends Exception {
        public RoomConflictException(RoomConflictChecker.Conflict conflict) {
            super(buildRoomConflictMessage(conflict));
        }

        private static String buildRoomConflictMessage(RoomConflictChecker.Conflict conflict) {
            Shift requested = conflict.getNewShift();
            Shift existing = conflict.getExistingShift();
            String existingClassName = existing.getClassName();
            if (existingClassName == null || existingClassName.trim().isEmpty()) {
                existingClassName = existing.getClassId() == null
                        ? "Không xác định"
                        : existing.getClassId();
            } else if (existing.getClassId() != null && !existing.getClassId().trim().isEmpty()) {
                existingClassName += " (" + existing.getClassId() + ")";
            }

            return "Ngày: " + requested.getDate()
                    + "\nCa yêu cầu: " + requested.getStartAt() + " - " + requested.getEndAt()
                    + "\nCa đang sử dụng: " + existing.getStartAt() + " - " + existing.getEndAt()
                    + "\nPhòng: " + requested.getRoom()
                    + "\nLớp đang sử dụng: " + existingClassName;
        }
    }

    /** Báo giảng viên của lớp mới đã có ca giảng giao với một ca được tạo sẵn. */
    public static class TeacherScheduleConflictException extends Exception {
        public TeacherScheduleConflictException(TeacherScheduleConflictChecker.Conflict conflict,
                                                String teacherDisplayName) {
            super(buildTeacherScheduleConflictMessage(conflict, teacherDisplayName));
        }

        private static String buildTeacherScheduleConflictMessage(
                TeacherScheduleConflictChecker.Conflict conflict,
                String teacherDisplayName) {
            Shift requested = conflict.getNewShift();
            Shift existing = conflict.getExistingShift();
            String displayName = teacherDisplayName == null || teacherDisplayName.trim().isEmpty()
                    ? requested.getTeacherDisplayName()
                    : teacherDisplayName.trim();

            return "Giảng viên: " + displayName
                    + "\nNgày: " + requested.getDate()
                    + "\nCa muốn tạo: " + requested.getStartAt() + " - " + requested.getEndAt()
                    + "\nCa đang giảng: " + existing.getStartAt() + " - " + existing.getEndAt()
                    + "\nLớp đang phụ trách: " + getShiftClassDisplay(existing)
                    + "\nPhòng: " + safeDisplayValue(existing.getRoom());
        }
    }

    private static String getShiftClassDisplay(Shift shift) {
        String className = shift.getClassName();
        String classId = shift.getClassId();
        if (className == null || className.trim().isEmpty()) {
            return safeDisplayValue(classId);
        }
        if (classId == null || classId.trim().isEmpty()) return className.trim();
        return className.trim() + " (" + classId.trim() + ")";
    }

    private static String safeDisplayValue(String value) {
        return value == null || value.trim().isEmpty() ? "Không xác định" : value.trim();
    }

    /**
     * Lấy tên giảng viên đã lưu trong lớp; với dữ liệu lớp cũ, đọc lại hồ sơ theo teacherId.
     * Mọi ca mới/được dời vì thế đều có trường Firestore {@code teacher} là tên hiển thị.
     */
    private void resolveTeacherDisplayName(ClassModel classModel, String fallbackName,
                                           OnSuccessListener<String> onSuccess,
                                           OnFailureListener onFailure) {
        String classTeacherName = classModel == null ? null : classModel.getTeacherName();
        if (classTeacherName != null && !classTeacherName.trim().isEmpty()) {
            onSuccess.onSuccess(classTeacherName.trim());
            return;
        }
        if (fallbackName != null && !fallbackName.trim().isEmpty()) {
            onSuccess.onSuccess(fallbackName.trim());
            return;
        }

        String teacherId = classModel == null ? null : classModel.getTeacherId();
        if (teacherId == null || teacherId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Không xác định được giảng viên của lớp"));
            return;
        }
        getUserProfile(teacherId, teacher -> {
            String name = teacher == null ? null : teacher.getName();
            if (name == null || name.trim().isEmpty()) {
                onFailure.onFailure(new IllegalArgumentException("Hồ sơ giảng viên chưa có tên"));
                return;
            }
            onSuccess.onSuccess(name.trim());
        }, onFailure);
    }

    public void createClass(ClassModel classModel,
                            OnSuccessListener<String> onSuccess,
                            OnFailureListener onFailure) {
        if (classModel.getRoom() == null || classModel.getRoom().trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Phòng học không được để trống"));
            return;
        }
        classModel.setRoom(classModel.getRoom().trim());
        resolveTeacherDisplayName(classModel, null, teacherName -> {
            classModel.setTeacherName(teacherName);
            createClassWithTeacher(classModel, onSuccess, onFailure);
        }, onFailure);
    }

    private void createClassWithTeacher(ClassModel classModel,
                                        OnSuccessListener<String> onSuccess,
                                        OnFailureListener onFailure) {
        classModel.setCreatedAt(Timestamp.now());
        String classId = classModel.getClassId();
        DocumentReference classRef = db.collection(COL_CLASSES).document(classId);

        final List<Shift> newShifts;
        try {
            newShifts = buildShifts(classModel);
        } catch (Exception e) {
            onFailure.onFailure(e);
            return;
        }

        // Giữ thông báo mã lớp trùng rõ ràng trước khi kiểm tra lịch. Transaction
        // phía dưới vẫn kiểm tra lại để xử lý trường hợp hai thiết bị tạo đồng thời.
        classRef.get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        onFailure.onFailure(new DuplicateClassIdException(classId));
                        return;
                    }
                    checkNewClassConflictsThenPersist(
                            classModel, classRef, newShifts, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    private void checkNewClassConflictsThenPersist(ClassModel classModel,
                                                   DocumentReference classRef,
                                                   List<Shift> newShifts,
                                                   OnSuccessListener<String> onSuccess,
                                                   OnFailureListener onFailure) {
        checkTeacherAndRoomConflicts(newShifts, null, classModel.getTeacherName(),
                () -> persistClassAndShifts(
                        classModel, classRef, newShifts, onSuccess, onFailure),
                onFailure);
    }

    /**
     * Đọc toàn bộ ca một lần, ưu tiên báo trùng lịch giảng rồi mới kiểm tra phòng.
     * Khi dời lịch, {@code excludedShiftId} loại chính ca đang cập nhật.
     */
    private void checkTeacherAndRoomConflicts(List<Shift> requestedShifts,
                                              String excludedShiftId,
                                              String teacherDisplayName,
                                              Runnable onNoConflict,
                                              OnFailureListener onFailure) {
        db.collection(COL_SHIFTS).get()
                .addOnSuccessListener(snapshot -> {
                    List<Shift> existingShifts = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (excludedShiftId != null && excludedShiftId.equals(document.getId())) {
                            continue;
                        }
                        Shift shift = document.toObject(Shift.class);
                        if (shift != null) existingShifts.add(shift);
                    }

                    TeacherScheduleConflictChecker.Conflict teacherConflict =
                            TeacherScheduleConflictChecker.findFirstConflict(
                                    requestedShifts, existingShifts);
                    if (teacherConflict != null) {
                        onFailure.onFailure(new TeacherScheduleConflictException(
                                teacherConflict, teacherDisplayName));
                        return;
                    }

                    RoomConflictChecker.Conflict roomConflict =
                            RoomConflictChecker.findFirstConflict(requestedShifts, existingShifts);
                    if (roomConflict != null) {
                        onFailure.onFailure(new RoomConflictException(roomConflict));
                        return;
                    }

                    onNoConflict.run();
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    /** Giữ quy tắc cũ: một lớp không được có hai ca chồng giờ trong cùng ngày. */
    private void checkClassScheduleConflict(Shift requestedShift, String excludedShiftId,
                                            Runnable onNoConflict,
                                            OnFailureListener onFailure) {
        db.collection(COL_SHIFTS)
                .whereEqualTo("classId", requestedShift.getClassId())
                .whereEqualTo("date", requestedShift.getDate())
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (excludedShiftId != null && excludedShiftId.equals(document.getId())) {
                            continue;
                        }
                        Shift existing = document.toObject(Shift.class);
                        if (existing == null
                                || Shift.STATUS_CANCELLED.equals(existing.getStatus())) {
                            continue;
                        }
                        if (RoomConflictChecker.timesOverlap(
                                requestedShift.getStartAt(), requestedShift.getEndAt(),
                                existing.getStartAt(), existing.getEndAt())) {
                            onFailure.onFailure(new ShiftConflictException(
                                    "Đã tồn tại ca học trùng thời gian trong ngày này"));
                            return;
                        }
                    }
                    onNoConflict.run();
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    private void persistClassAndShifts(ClassModel classModel, DocumentReference classRef,
                                       List<Shift> newShifts,
                                       OnSuccessListener<String> onSuccess,
                                       OnFailureListener onFailure) {
        String classId = classModel.getClassId();

        // Transaction giúp thao tác kiểm tra và tạo là nguyên tử: hai thiết bị
        // cùng tạo một mã lớp cũng không thể ghi đè document của nhau.
        db.runTransaction(transaction -> {
                    DocumentSnapshot existingClass = transaction.get(classRef);
                    if (existingClass.exists()) {
                        throw new FirebaseFirestoreException(
                                "Class ID already exists",
                                FirebaseFirestoreException.Code.ALREADY_EXISTS);
                    }
                    transaction.set(classRef, classModel);
                    return null;
                })
                .addOnSuccessListener(aVoid -> {
                    // Auto-generate shifts after class creation
                    generateShifts(newShifts,
                            () -> onSuccess.onSuccess(classId),
                            onFailure);
                })
                .addOnFailureListener(error -> {
                    if (error instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) error).getCode()
                            == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                        onFailure.onFailure(new DuplicateClassIdException(classId));
                        return;
                    }
                    onFailure.onFailure(error);
                });
    }

    public void updateClassInfo(String classId, String className,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        if (classId == null || classId.isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Invalid classId"));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("className", className);


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
     * Kiểm tra lớp đã phát sinh phiên điểm danh (session) nào chưa. Dùng để chặn
     * việc xóa lớp khi đã có dữ liệu điểm danh phát sinh. Trả về {@code false}
     * khi lỗi truy vấn để tránh chặn nhầm.
     */
    public void classHasSessions(String classId, OnSuccessListener<Boolean> onResult) {
        if (classId == null || classId.isEmpty()) { onResult.onSuccess(false); return; }
        db.collection(COL_SESSIONS)
                .whereEqualTo("classId", classId)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> onResult.onSuccess(!snap.isEmpty()))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "classHasSessions failed: " + e.getMessage());
                    onResult.onSuccess(false);
                });
    }

    /**
     * Auto-generate shift documents for every scheduled day in the semester.
     */
    private List<Shift> buildShifts(ClassModel classModel) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.setTime(Objects.requireNonNull(sdf.parse(classModel.getStartDate())));
        Date endDate = Objects.requireNonNull(sdf.parse(classModel.getEndDate()));
        List<Shift> shifts = new ArrayList<>();

        while (!cal.getTime().after(endDate)) {
            int vnDow = AttendanceUtils.getDayOfWeekVN(cal.get(Calendar.DAY_OF_WEEK));
            if (classModel.getSchedule() != null && classModel.getSchedule().contains(vnDow)) {
                String dateStr = sdf.format(cal.getTime());
                DaySchedule daySchedule = classModel.getDayScheduleFor(vnDow);

                Shift shift = new Shift();
                shift.setShiftId(classModel.getClassId() + "_" + dateStr);
                shift.setClassId(classModel.getClassId());
                shift.setClassName(classModel.getClassName());
                shift.setTeacher(classModel.getTeacherName());
                // Theo schema Shift hiện tại, teacherName lưu UID của giáo viên.
                shift.setTeacherName(classModel.getTeacherId());
                shift.setTitle("Buổi học ngày " + dateStr);
                shift.setDate(dateStr);
                shift.setDayOfWeek(vnDow);
                shift.setStartAt(daySchedule != null
                        ? daySchedule.getStartAt() : classModel.getStartAt());
                shift.setEndAt(daySchedule != null
                        ? daySchedule.getEndAt() : classModel.getEndAt());
                shift.setRoom(classModel.getRoom() != null ? classModel.getRoom().trim() : "");
                shift.setStatus(Shift.STATUS_UPCOMING);
                shift.setAttendanceOpened(false);
                shift.setCreatedAt(Timestamp.now());
                shifts.add(shift);
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return shifts;
    }

    private void generateShifts(List<Shift> shifts, Runnable onComplete,
                                OnFailureListener onFailure) {
        writeShiftBatch(shifts, 0, onComplete, onFailure);
    }

    private void writeShiftBatch(List<Shift> shifts, int start, Runnable onComplete,
                                 OnFailureListener onFailure) {
        if (start >= shifts.size()) {
            onComplete.run();
            return;
        }

        int end = Math.min(start + 499, shifts.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            Shift shift = shifts.get(i);
            batch.set(db.collection(COL_SHIFTS).document(shift.getShiftId()), shift);
        }
        batch.commit()
                .addOnSuccessListener(unused ->
                        writeShiftBatch(shifts, end, onComplete, onFailure))
                .addOnFailureListener(onFailure::onFailure);
    }

    /** Lỗi nghiệp vụ: ca học mới trùng/chồng thời gian với một ca đã có cùng ngày. */
    public static class ShiftConflictException extends Exception {
        public ShiftConflictException(String message) { super(message); }
    }

    /**
     * Tạo một ca học bù (phát sinh ngoài lịch gốc) cho lớp. Thông tin lớp
     * (tên lớp, giáo viên) được lấy từ document lớp tương ứng.
     *
     * <p>Trước khi tạo sẽ kiểm tra xung đột phòng với toàn bộ ca đã tồn tại:
     * cùng ngày, cùng phòng và khoảng giờ giao nhau thì báo
     * {@link RoomConflictException} và KHÔNG tạo.
     *
     * @param date   ngày học "yyyy-MM-dd"
     * @param room   phòng học bắt buộc
     * @param title  tiêu đề; để rỗng sẽ tự đặt "Ca học bù ngày ..."
     */
    public void createMakeupShift(String classId, String date, String startAt, String endAt,
                                  String room, String title,
                                  OnSuccessListener<String> onSuccess,
                                  OnFailureListener onFailure) {
        if (room == null || room.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Phòng học không được để trống"));
            return;
        }
        getClassById(classId, classModel -> {
            resolveTeacherDisplayName(classModel, null, teacherName -> {
                classModel.setTeacherName(teacherName);
                try {
                    Shift newShift = buildMakeupShift(
                            classModel, date, startAt, endAt, room, title);
                    checkTeacherAndRoomConflicts(
                            Collections.singletonList(newShift), null, teacherName,
                            () -> checkClassScheduleConflict(newShift, null,
                                    () -> writeMakeupShift(newShift, onSuccess, onFailure),
                                    onFailure),
                            onFailure);
                } catch (Exception e) {
                    onFailure.onFailure(e);
                }
            }, onFailure);
        }, onFailure);
    }

    private Shift buildMakeupShift(ClassModel classModel, String date,
                                   String startAt, String endAt,
                                   String room, String title) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.setTime(Objects.requireNonNull(sdf.parse(date)));
        int vnDow = AttendanceUtils.getDayOfWeekVN(cal.get(Calendar.DAY_OF_WEEK));
        String shiftId = classModel.getClassId() + "_" + date + "_m" + System.currentTimeMillis();

        Shift shift = new Shift();
        shift.setShiftId(shiftId);
        shift.setClassId(classModel.getClassId());
        shift.setClassName(classModel.getClassName());
        shift.setTeacher(classModel.getTeacherName());
        // Giữ cùng cách lưu với các ca được sinh tự động từ lớp.
        shift.setTeacherName(classModel.getTeacherId());
        shift.setTitle(title != null && !title.isEmpty() ? title : "Ca học bù ngày " + date);
        shift.setDate(date);
        shift.setDayOfWeek(vnDow);
        shift.setStartAt(startAt);
        shift.setEndAt(endAt);
        shift.setRoom(room.trim());
        shift.setStatus(Shift.STATUS_UPCOMING);
        shift.setAttendanceOpened(false);
        shift.setMakeup(true);
        shift.setCreatedAt(Timestamp.now());
        return shift;
    }

    private void writeMakeupShift(Shift shift,
                                  OnSuccessListener<String> onSuccess,
                                  OnFailureListener onFailure) {
        db.collection(COL_SHIFTS).document(shift.getShiftId()).set(shift)
                .addOnSuccessListener(aVoid -> onSuccess.onSuccess(shift.getShiftId()))
                .addOnFailureListener(onFailure::onFailure);
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

                    // Fetch class details (chunk vì whereIn giới hạn số phần tử)
                    runChunkedWhereIn(classIds,
                            chunk -> db.collection(COL_CLASSES).whereIn(FieldPath.documentId(), chunk),
                            classDocs -> {
                                List<ClassModel> classList = new ArrayList<>();
                                for (DocumentSnapshot doc : classDocs) {
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

    /**
     * Kiểm tra sinh viên có enrollment đang hoạt động trong lớp hay không.
     * Overload này trả lỗi cho caller để luồng điểm danh không đi tiếp khi chưa
     * thể xác minh trạng thái thành viên từ Firestore.
     */
    public void checkEnrollment(String studentId, String classId,
                                OnSuccessListener<Boolean> onSuccess,
                                OnFailureListener onFailure) {
        String docId = studentId + "_" + classId;
        db.collection(COL_ENROLLMENTS).document(docId).get()
                .addOnSuccessListener(doc -> {
                    Enrollment enrollment = doc.exists()
                            ? doc.toObject(Enrollment.class)
                            : null;
                    onSuccess.onSuccess(enrollment != null
                            && "active".equals(enrollment.getStatus()));
                })
                .addOnFailureListener(onFailure::onFailure);
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

                    // Chunk vì whereIn giới hạn số phần tử cho mỗi truy vấn.
                    runChunkedWhereIn(studentIds,
                            chunk -> db.collection(COL_USERS).whereIn(FieldPath.documentId(), chunk),
                            userDocs -> {
                                List<User> users = new ArrayList<>();
                                for (DocumentSnapshot doc : userDocs) {
                                    User u = doc.toObject(User.class);
                                    if (u != null) users.add(u);
                                }
                                onSuccess.onSuccess(users);
                            });
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

    public void getShiftById(String shiftId,
                             OnSuccessListener<Shift> onSuccess,
                             OnFailureListener onFailure) {
        db.collection(COL_SHIFTS).document(shiftId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) onSuccess.onSuccess(doc.toObject(Shift.class));
                    else onFailure.onFailure(new Exception("Shift not found"));
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    public void getShiftsByDate(List<String> classIds, String date,
                                OnSuccessListener<List<Shift>> onSuccess) {
        runChunkedWhereIn(classIds,
                chunk -> db.collection(COL_SHIFTS).whereIn("classId", chunk)
                        .whereEqualTo("date", date),
                docs -> {
                    List<Shift> list = new ArrayList<>();
                    for (DocumentSnapshot doc : docs) {
                        Shift s = doc.toObject(Shift.class);
                        if (s != null) list.add(s);
                    }
                    onSuccess.onSuccess(list);
                });
    }

    public void getShiftsForClasses(List<String> classIds,
                                    OnSuccessListener<List<Shift>> onSuccess) {
        runChunkedWhereIn(classIds,
                chunk -> db.collection(COL_SHIFTS).whereIn("classId", chunk),
                docs -> {
                    List<Shift> result = new ArrayList<>();
                    for (DocumentSnapshot doc : docs) {
                        Shift s = doc.toObject(Shift.class);
                        if (s != null) result.add(s);
                    }
                    onSuccess.onSuccess(result);
                });
    }

    /**
     * Lấy các ca học của SINH VIÊN (dựa trên các lớp đang ghi danh active), dùng để lên
     * lịch thông báo cục bộ nhắc trước ca học. Trả về toàn bộ ca của các lớp; việc lọc
     * "ca chưa kết thúc / thời điểm nhắc còn trong tương lai" do bên gọi
     * ({@code NotificationScheduler}) đảm nhiệm để giữ repository đơn giản.
     */
    public void getUpcomingShiftsForStudent(String studentId,
                                            OnSuccessListener<List<Shift>> onSuccess,
                                            OnFailureListener onFailure) {
        db.collection(COL_ENROLLMENTS)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("status", "active")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<String> classIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Enrollment e = doc.toObject(Enrollment.class);
                        if (e != null && e.getClassId() != null) classIds.add(e.getClassId());
                    }
                    if (classIds.isEmpty()) { onSuccess.onSuccess(new ArrayList<>()); return; }
                    getShiftsForClasses(classIds, onSuccess::onSuccess);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    /**
     * Lấy các ca học của các lớp mà GIẢNG VIÊN đang phụ trách (teacherId trùng), dùng để
     * lên lịch thông báo nhắc trước ca dạy. Đáp ứng yêu cầu "chỉ lên lịch cho ca mà
     * giảng viên đang phụ trách".
     */
    public void getUpcomingShiftsForTeacher(String teacherId,
                                            OnSuccessListener<List<Shift>> onSuccess,
                                            OnFailureListener onFailure) {
        db.collection(COL_CLASSES)
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<String> classIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        classIds.add(doc.getId());
                    }
                    if (classIds.isEmpty()) { onSuccess.onSuccess(new ArrayList<>()); return; }
                    getShiftsForClasses(classIds, onSuccess::onSuccess);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    /** Tạo một truy vấn {@code whereIn} cho một đoạn id (≤ {@link #WHERE_IN_CHUNK_SIZE} phần tử). */
    private interface ChunkQueryBuilder {
        Query build(List<String> chunk);
    }

    /**
     * Chạy một truy vấn {@code whereIn} trên danh sách id dài mà không vượt giới hạn
     * của Firestore: cắt {@code ids} thành các đoạn ≤ {@link #WHERE_IN_CHUNK_SIZE},
     * gọi song song một query cho mỗi đoạn (do {@code builder} tạo) rồi gộp toàn bộ
     * document trả về và phát một lần qua {@code onComplete}. List rỗng/null trả về
     * ngay danh sách rỗng. Lỗi của một đoạn chỉ làm thiếu dữ liệu của đoạn đó (giống
     * hành vi cũ của getShiftsForClasses), không làm hỏng cả kết quả.
     */
    private void runChunkedWhereIn(List<String> ids, ChunkQueryBuilder builder,
                                   OnSuccessListener<List<DocumentSnapshot>> onComplete) {
        if (ids == null || ids.isEmpty()) { onComplete.onSuccess(new ArrayList<>()); return; }
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += WHERE_IN_CHUNK_SIZE) {
            chunks.add(ids.subList(i, Math.min(i + WHERE_IN_CHUNK_SIZE, ids.size())));
        }
        List<DocumentSnapshot> all = new ArrayList<>();
        final int[] pending = {chunks.size()};
        for (List<String> chunk : chunks) {
            builder.build(chunk).get()
                    .addOnSuccessListener(docs -> {
                        all.addAll(docs.getDocuments());
                        if (--pending[0] == 0) onComplete.onSuccess(all);
                    })
                    .addOnFailureListener(e -> {
                        if (--pending[0] == 0) onComplete.onSuccess(all);
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

    /**
     * Xóa một ca học nếu bản mới nhất trên Firestore vẫn đang ở trạng thái upcoming.
     * Transaction tránh xóa nhầm khi ca vừa được mở điểm danh trong lúc hộp thoại xác nhận đang mở.
     */
    public void deleteUpcomingShift(String shiftId,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        DocumentReference shiftRef = db.collection(COL_SHIFTS).document(shiftId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(shiftRef);
                    Shift current = snapshot.exists() ? snapshot.toObject(Shift.class) : null;
                    if (current == null) {
                        throw new FirebaseFirestoreException(
                                "Không tìm thấy ca học",
                                FirebaseFirestoreException.Code.NOT_FOUND);
                    }
                    if (!Shift.STATUS_UPCOMING.equals(current.getStatus())
                            || current.isAttendanceOpened()) {
                        throw new FirebaseFirestoreException(
                                "Chỉ có thể xóa ca học đang ở trạng thái sắp diễn ra",
                                FirebaseFirestoreException.Code.ABORTED);
                    }
                    transaction.delete(shiftRef);
                    return null;
                })
                .addOnSuccessListener(unused -> onSuccess.onSuccess(null))
                .addOnFailureListener(onFailure::onFailure);
    }

    /**
     * Dời (cập nhật lịch) một ca học sang ngày/giờ/phòng mới.
     *
     * <p>Điều kiện: ca học CHƯA mở điểm danh và chưa kết thúc/hủy — được kiểm tra
     * lại ở phía server (đọc bản mới nhất) để tránh tình huống ca vừa được mở điểm
     * danh ngay trước khi dời. Đồng thời kiểm tra xung đột lịch giảng và phòng học
     * với toàn bộ ca khác, trừ chính ca đang dời. {@code dayOfWeek} được tính lại
     * từ ngày mới để {@code getDayOfWeekDisplay()} hiển thị đúng thứ.
     *
     * @param newRoom phòng học mới bắt buộc
     */
    public void rescheduleShift(String shiftId, String classId, String newDate,
                                String newStartAt, String newEndAt, String newRoom,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        if (newRoom == null || newRoom.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Phòng học không được để trống"));
            return;
        }
        db.collection(COL_SHIFTS).document(shiftId).get()
                .addOnSuccessListener(doc -> {
                    Shift current = doc.exists() ? doc.toObject(Shift.class) : null;
                    if (current == null) {
                        onFailure.onFailure(new Exception("Không tìm thấy ca học"));
                        return;
                    }
                    if (current.isAttendanceOpened()
                            || Shift.STATUS_COMPLETED.equals(current.getStatus())
                            || Shift.STATUS_CANCELLED.equals(current.getStatus())) {
                        onFailure.onFailure(new ShiftConflictException(
                                "Ca học đã mở điểm danh hoặc đã kết thúc, không thể dời"));
                        return;
                    }
                    getClassById(classId, classModel -> {
                        resolveTeacherDisplayName(classModel, current.getTeacher(), teacherName -> {
                            String effectiveRoom = newRoom.trim();
                            String teacherId = current.getTeacherName();
                            if (teacherId == null || teacherId.trim().isEmpty()) {
                                teacherId = classModel.getTeacherId();
                            }

                            Shift requestedShift = new Shift();
                            requestedShift.setShiftId(shiftId);
                            requestedShift.setClassId(classId);
                            requestedShift.setClassName(current.getClassName());
                            requestedShift.setTeacher(teacherName);
                            requestedShift.setTeacherName(teacherId);
                            requestedShift.setDate(newDate);
                            requestedShift.setStartAt(newStartAt);
                            requestedShift.setEndAt(newEndAt);
                            requestedShift.setRoom(effectiveRoom);

                            checkTeacherAndRoomConflicts(
                                    Collections.singletonList(requestedShift), shiftId,
                                    teacherName,
                                    () -> checkClassScheduleConflict(requestedShift, shiftId,
                                            () -> writeReschedule(shiftId, newDate,
                                                    newStartAt, newEndAt, effectiveRoom, teacherName,
                                                    onSuccess, onFailure),
                                            onFailure),
                                    onFailure);
                        }, onFailure);
                    }, onFailure);
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    private void writeReschedule(String shiftId, String newDate, String newStartAt,
                                 String newEndAt, String newRoom, String teacherName,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(newDate));
            int vnDow = AttendanceUtils.getDayOfWeekVN(cal.get(Calendar.DAY_OF_WEEK));

            Map<String, Object> updates = new HashMap<>();
            updates.put("date", newDate);
            updates.put("dayOfWeek", vnDow);
            updates.put("startAt", newStartAt);
            updates.put("endAt", newEndAt);
            updates.put("room", newRoom.trim());
            updates.put("teacher", teacherName);
            // Dời lịch luôn đưa ca về trạng thái sắp diễn ra.
            updates.put("status", Shift.STATUS_UPCOMING);

            db.collection(COL_SHIFTS).document(shiftId).update(updates)
                    .addOnSuccessListener(aVoid -> onSuccess.onSuccess(null))
                    .addOnFailureListener(onFailure::onFailure);
        } catch (Exception e) {
            onFailure.onFailure(e);
        }
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

    /** Cập nhật số phút cho phép vào muộn của một phiên điểm danh. */
    public void updateSessionLateMinutes(String sessionId, int lateAfterMinutes,
                                         OnSuccessListener<Void> onSuccess,
                                         OnFailureListener onFailure) {
        db.collection(COL_SESSIONS).document(sessionId)
                .update("lateAfterMinutes", lateAfterMinutes)
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onFailure::onFailure);
    }

    /** Cập nhật bán kính cho phép điểm danh của một phiên (đơn vị mét). */
    public void updateSessionRadius(String sessionId, double radius,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection(COL_SESSIONS).document(sessionId)
                .update("radius", radius)
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onFailure::onFailure);
    }

    /** Bổ sung mốc kết thúc theo lịch cho các phiên cũ chưa có trường này. */
    public void updateSessionScheduledEndTime(String sessionId, Timestamp scheduledEndTime,
                                              OnSuccessListener<Void> onSuccess,
                                              OnFailureListener onFailure) {
        db.collection(COL_SESSIONS).document(sessionId)
                .update("scheduledEndTime", scheduledEndTime)
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onFailure::onFailure);
    }

    public void updateSessionToken(String sessionId, String token,
                                   OnSuccessListener<Void> onSuccess,
                                   OnFailureListener onFailure) {
        db.collection(COL_SESSIONS).document(sessionId)
                .update("token", token)
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onFailure::onFailure);
    }

    /**
     * Tạo một phiên điểm danh BÙ cho buổi học đã kết thúc (cho sinh viên đi muộn).
     * Bắt buộc đặt {@code startTime = now - lateAfterMinutes} để mọi sinh viên
     * điểm danh trong phiên này đều được tính là MUỘN. Buổi học được mở lại
     * (status = ongoing) cho tới khi giáo viên đóng phiên.
     */
    public void createMakeupSession(Session session,
                                    OnSuccessListener<String> onSuccess,
                                    OnFailureListener onFailure) {
        long backMillis = (long) session.getLateAfterMinutes() * 60_000L;
        session.setStartTime(new Timestamp(new Date(System.currentTimeMillis() - backMillis)));
        session.setActive(true);

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

    public void closeSession(String sessionId, String shiftId, String content,
                             OnSuccessListener<Void> onSuccess,
                             OnFailureListener onFailure) {
        Map<String, Object> sessionUpdates = new HashMap<>();
        sessionUpdates.put(Session.FIELD_ACTIVE, false);
        sessionUpdates.put("endTime", Timestamp.now());
        if (content != null) sessionUpdates.put("content", content);

        Map<String, Object> shiftUpdates = new HashMap<>();
        shiftUpdates.put("status", Shift.STATUS_COMPLETED);
        shiftUpdates.put("attendanceOpened", false);
        // Lưu kèm nội dung buổi học lên shift để màn lịch sử lấy nhanh, khỏi đọc từng session.
        if (content != null) shiftUpdates.put("content", content);

        WriteBatch batch = db.batch();
        batch.update(db.collection(COL_SESSIONS).document(sessionId), sessionUpdates);
        batch.update(db.collection(COL_SHIFTS).document(shiftId), shiftUpdates);
        batch.commit()
                .addOnSuccessListener(unused -> onSuccess.onSuccess(null))
                .addOnFailureListener(onFailure::onFailure);
    }

    // ==================== ATTENDANCE ====================

    public enum AttendanceWriteResult {
        SAVED,
        SESSION_CLOSED,
        SHIFT_ENDED
    }

    public enum ExpiredSessionResult {
        CLOSED,
        ALREADY_CLOSED,
        NOT_EXPIRED
    }

    /**
     * Đóng phiên và ca học trong một transaction nếu thời gian hiện tại đã chạm
     * {@code endAt}. Nhiều thiết bị gọi đồng thời sẽ tự retry và chỉ một lần đóng thắng.
     */
    public void closeSessionIfExpired(String sessionId, String shiftId,
                                      OnSuccessListener<ExpiredSessionResult> onSuccess,
                                      OnFailureListener onFailure) {
        DocumentReference sessionRef = db.collection(COL_SESSIONS).document(sessionId);
        DocumentReference shiftRef = db.collection(COL_SHIFTS).document(shiftId);

        db.runTransaction(transaction -> {
                    DocumentSnapshot sessionDoc = transaction.get(sessionRef);
                    Session session = sessionDoc.exists() ? sessionDoc.toObject(Session.class) : null;
                    if (session == null) throw new IllegalStateException("Không tìm thấy phiên điểm danh");
                    if (!session.isActive()) return ExpiredSessionResult.ALREADY_CLOSED;

                    DocumentSnapshot shiftDoc = transaction.get(shiftRef);
                    Shift shift = shiftDoc.exists() ? shiftDoc.toObject(Shift.class) : null;
                    Timestamp shiftEnd = AttendanceUtils.getShiftEndTimestamp(shift);
                    if (shiftEnd == null || session.getScheduledEndTime() == null) {
                        throw new IllegalStateException("Không xác định được giờ kết thúc ca học");
                    }
                    // scheduledEndTime được chốt từ endAt lúc mở phiên và là cùng
                    // mốc mà Firestore Rules sử dụng với thời gian máy chủ.
                    if (Timestamp.now().compareTo(session.getScheduledEndTime()) < 0) {
                        return ExpiredSessionResult.NOT_EXPIRED;
                    }

                    Map<String, Object> sessionUpdates = new HashMap<>();
                    sessionUpdates.put(Session.FIELD_ACTIVE, false);
                    sessionUpdates.put("endTime", FieldValue.serverTimestamp());
                    transaction.update(sessionRef, sessionUpdates);

                    Map<String, Object> shiftUpdates = new HashMap<>();
                    shiftUpdates.put("status", Shift.STATUS_COMPLETED);
                    shiftUpdates.put("attendanceOpened", false);
                    transaction.update(shiftRef, shiftUpdates);
                    return ExpiredSessionResult.CLOSED;
                })
                .addOnSuccessListener(onSuccess::onSuccess)
                .addOnFailureListener(onFailure::onFailure);
    }

    /**
     * Kiểm tra lại phiên + ca học ngay trong transaction cuối cùng trước khi ghi.
     * Nếu ca đã hết giờ, transaction chỉ đóng phiên/ca và tuyệt đối không tạo attendance.
     */
    public void saveAttendanceIfShiftOpen(Attendance attendance,
                                          OnSuccessListener<AttendanceWriteResult> onSuccess,
                                          OnFailureListener onFailure) {
        if (attendance.getAttendanceId() == null || attendance.getAttendanceId().isEmpty()) {
            attendance.setAttendanceId(db.collection(COL_ATTENDANCES).document().getId());
        }
        DocumentReference sessionRef = db.collection(COL_SESSIONS)
                .document(attendance.getSessionId());
        DocumentReference shiftRef = db.collection(COL_SHIFTS)
                .document(attendance.getShiftId());
        DocumentReference attendanceRef = db.collection(COL_ATTENDANCES)
                .document(attendance.getAttendanceId());

        db.runTransaction(transaction -> {
                    DocumentSnapshot sessionDoc = transaction.get(sessionRef);
                    Session session = sessionDoc.exists() ? sessionDoc.toObject(Session.class) : null;
                    if (session == null) throw new IllegalStateException("Không tìm thấy phiên điểm danh");
                    if (!session.isActive()) return AttendanceWriteResult.SESSION_CLOSED;

                    DocumentSnapshot shiftDoc = transaction.get(shiftRef);
                    Shift shift = shiftDoc.exists() ? shiftDoc.toObject(Shift.class) : null;
                    Timestamp shiftEnd = AttendanceUtils.getShiftEndTimestamp(shift);
                    if (shiftEnd == null || session.getScheduledEndTime() == null) {
                        throw new IllegalStateException("Không xác định được giờ kết thúc ca học");
                    }

                   Timestamp now = Timestamp.now();
                    /*Đóng phiên điểm danh nếu đã quá thời gian kết thúc.*/
                    if (now.compareTo(session.getScheduledEndTime()) >= 0) {
                        Map<String, Object> sessionUpdates = new HashMap<>();
                        sessionUpdates.put(Session.FIELD_ACTIVE, false);
                        sessionUpdates.put("endTime", FieldValue.serverTimestamp());
                        transaction.update(sessionRef, sessionUpdates);

                        Map<String, Object> shiftUpdates = new HashMap<>();
                        shiftUpdates.put("status", Shift.STATUS_COMPLETED);
                        shiftUpdates.put("attendanceOpened", false);
                        transaction.update(shiftRef, shiftUpdates);
                        return AttendanceWriteResult.SHIFT_ENDED;
                    }

                    attendance.setCheckinTime(now);
                    transaction.set(attendanceRef, attendance);
                    return AttendanceWriteResult.SAVED;
                })
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
     * Sinh viên đã có bản ghi điểm danh cho BUỔI HỌC này chưa (bất kỳ phiên nào,
     * kể cả phiên bù). Dùng để chặn điểm danh trùng khi một buổi có nhiều phiên.
     */
    public void checkStudentAttendedShift(String studentId, String shiftId,
                                          OnSuccessListener<Boolean> onSuccess) {
        db.collection(COL_ATTENDANCES)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("shiftId", shiftId)
                .get()
                .addOnSuccessListener(docs -> onSuccess.onSuccess(!docs.isEmpty()));
    }

    /**
     * Lấy bản ghi điểm danh của một sinh viên cho một phiên (session) cụ thể.
     * Trả về {@code null} nếu sinh viên chưa điểm danh phiên đó.
     */
    public void getStudentAttendanceForSession(String studentId, String sessionId,
                                               OnSuccessListener<Attendance> onSuccess) {
        if (sessionId == null) { onSuccess.onSuccess(null); return; }
        db.collection(COL_ATTENDANCES)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("sessionId", sessionId)
                .get()
                .addOnSuccessListener(docs -> {
                    if (docs.isEmpty()) { onSuccess.onSuccess(null); return; }
                    onSuccess.onSuccess(docs.getDocuments().get(0).toObject(Attendance.class));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getStudentAttendanceForSession failed: " + e.getMessage());
                    onSuccess.onSuccess(null);
                });
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
                                                          OnSuccessListener<List<Attendance>> onUpdate,
                                                          OnFailureListener onFailure) {
        return db.collection(COL_ATTENDANCES)
                .whereEqualTo("sessionId", sessionId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "listenToSessionAttendance failed for session " + sessionId, error);
                        onFailure.onFailure(error);
                        return;
                    }
                    if (snapshots == null) return;

                    List<Attendance> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Attendance a = doc.toObject(Attendance.class);
                        if (a != null) list.add(a);
                    }

                    // Sort locally so this real-time query only needs the built-in single-field
                    // index on sessionId. This keeps the teacher's screen live even when a
                    // composite Firestore index has not yet been deployed.
                    Collections.sort(list, (first, second) -> {
                        Timestamp firstTime = first.getCheckinTime();
                        Timestamp secondTime = second.getCheckinTime();
                        if (firstTime == null) return secondTime == null ? 0 : 1;
                        if (secondTime == null) return -1;
                        return firstTime.compareTo(secondTime);
                    });
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

                    // In chi tiết danh sách bản ghi điểm danh lấy được ra Logcat.
                    Log.d(TAG, "getStudentAttendanceHistory: studentId=" + studentId
                            + ", classId=" + classId + " -> " + list.size() + " ban ghi");
                    for (int i = 0; i < list.size(); i++) {
                        Attendance a = list.get(i);
                        Log.d(TAG, String.format(Locale.US,
                                "  [%d] id=%s | classId=%s | shiftId=%s | sessionId=%s | "
                                        + "status=%s | distance=%.1fm | toaDo=%.5f,%.5f | checkin=%s",
                                i, a.getAttendanceId(), a.getClassId(), a.getShiftId(),
                                a.getSessionId(), a.getStatus(), a.getDistance(),
                                a.getLatitude(), a.getLongitude(),
                                a.getCheckinTime() != null ? a.getCheckinTime().toDate() : "null"));
                    }

                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getStudentAttendanceHistory THAT BAI: studentId=" + studentId, e);
                    onFailure.onFailure(e);
                });
    }

    // ==================== HELPER INTERFACES ====================

    public interface OnSuccessListener<T> {
        void onSuccess(T result);
    }

    public interface OnFailureListener {
        void onFailure(Exception e);
    }
}
