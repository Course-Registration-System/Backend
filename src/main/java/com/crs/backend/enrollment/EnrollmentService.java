package com.crs.backend.enrollment;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.enrollment.EnrollmentRepository.LectureInfo;
import com.crs.backend.enrollment.EnrollmentRepository.Prerequisite;
import com.crs.backend.enrollment.EnrollmentRepository.StudentState;
import com.crs.backend.enrollment.dto.EnrollmentResponse;
import com.crs.backend.grade.GradeQuery;
import com.crs.backend.lecture.LecStat;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수강신청·취소(REG-03). 처리 내용 ②~⑪ 순서로 검사하고, 하나라도 걸리면 그 사유로 막습니다. */
@Service
public class EnrollmentService {

    /** 좋은 등급부터. 뒤로 갈수록 낮은 등급입니다. */
    private static final List<String> GRADE_ORDER =
            List.of("A+", "A0", "A-", "B+", "B0", "B-", "C+", "C0", "C-", "D+", "D0", "D-", "F");

    private static final int MAX_RETAKE_COURSES = 10;

    private final EnrollmentRepository enrollmentRepository;
    private final SchedulePolicy schedulePolicy;
    private final GradeQuery gradeQuery;
    private final Clock clock;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            SchedulePolicy schedulePolicy,
            GradeQuery gradeQuery,
            Clock clock) {
        this.enrollmentRepository = enrollmentRepository;
        this.schedulePolicy = schedulePolicy;
        this.gradeQuery = gradeQuery;
        this.clock = clock;
    }

    public List<EnrollmentResponse> list(CurrentUser user) {
        return enrollmentRepository.findEnrolled(requireStudent(user), registrationSemester());
    }

    // 신청. 학생 → 강의 순으로 잠근 뒤 검사한다
    @Transactional
    public EnrollmentResponse enroll(CurrentUser user, int lecNo) {
        String stdNo = requireStudent(user);
        int semNo = registrationSemester();
        StudentState student = lockStudent(stdNo);
        checkEnrollOpen(semNo, student);
        StudentStatusRule.check(student.stdStat(), enrollmentRepository.isSeasonal(semNo));

        LectureInfo lecture = enrollmentRepository
                .lockLecture(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        if (lecture.semNo() != semNo) {
            throw new BusinessException(ErrorCode.OTHER_SEMESTER_LECTURE);
        }
        if (lecture.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        // ③ 같은 과목(다른 분반 포함)
        if (enrollmentRepository.enrolledInCourse(stdNo, semNo, lecture.crsCd())) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED_COURSE);
        }
        boolean retake = checkRetake(stdNo, lecture); // ④
        if (lecture.enrlCnt() >= lecture.cap()) { // ⑤
            throw new BusinessException(ErrorCode.LECTURE_FULL);
        }
        enrollmentRepository
                .findTimeConflict(stdNo, semNo, lecNo)
                .ifPresent(
                        other -> { // ⑥
                            throw new BusinessException(ErrorCode.TIME_CONFLICT, other + " 강의와 시간이 겹칩니다.");
                        });
        checkCredits(stdNo, semNo, lecture); // ⑦
        checkPrerequisites(stdNo, lecture.crsCd()); // ⑧

        // ⑨ 저장. 취소한 기록이 있으면 새로 만들지 않고 되살린다
        LocalDateTime now = LocalDateTime.now(clock);
        if (enrollmentRepository.findEnrlStat(stdNo, lecNo).isPresent()) {
            enrollmentRepository.revive(stdNo, lecNo, retake, now);
        } else {
            enrollmentRepository.insert(stdNo, lecNo, retake, now);
        }
        enrollmentRepository.addEnrlCnt(lecNo, 1);
        return enrollmentRepository.findEnrolled(stdNo, semNo).stream()
                .filter(enrollment -> enrollment.lecNo() == lecNo)
                .findFirst()
                .orElseThrow();
    }

    // ⑪ 취소. 신청과 같은 순서로 잠근다
    @Transactional
    public void cancel(CurrentUser user, int lecNo) {
        String stdNo = requireStudent(user);
        int semNo = registrationSemester();
        StudentState student = lockStudent(stdNo);
        checkEnrollOpen(semNo, student);
        LectureInfo lecture = enrollmentRepository
                .lockLecture(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        if (lecture.semNo() != semNo || enrollmentRepository.cancel(stdNo, lecNo, LocalDateTime.now(clock)) == 0) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        enrollmentRepository.addEnrlCnt(lecNo, -1);
    }

    /**
     * 이미 확정 성적이 있는 과목이면 재수강입니다. 가장 최근 확정 성적이 C+ 이하(P/F 과목은 F)여야 하고, 재수강 과목은 10과목까지입니다.
     */
    private boolean checkRetake(String stdNo, LectureInfo lecture) {
        Optional<String> previous = gradeQuery.latestConfirmedGrade(stdNo, lecture.crsCd());
        if (previous.isEmpty()) {
            return false;
        }
        String grade = previous.get();
        boolean allowed = lecture.passFail() ? "F".equals(grade) : rank(grade) >= rank("C+");
        if (!allowed) {
            throw new BusinessException(ErrorCode.RETAKE_NOT_ALLOWED);
        }
        boolean newCourse = !enrollmentRepository.retakenCourse(stdNo, lecture.crsCd());
        if (newCourse && enrollmentRepository.countRetakenCourses(stdNo) >= MAX_RETAKE_COURSES) {
            throw new BusinessException(ErrorCode.RETAKE_LIMIT_EXCEEDED);
        }
        return true;
    }

    /** 채플 학점은 최대학점 계산에 넣지 않습니다. */
    private void checkCredits(String stdNo, int semNo, LectureInfo lecture) {
        if (lecture.chapel()) {
            return;
        }
        BigDecimal total = enrollmentRepository.sumCredits(stdNo, semNo).add(lecture.crdt());
        if (total.compareTo(BigDecimal.valueOf(schedulePolicy.maxCredit(stdNo, semNo))) > 0) {
            throw new BusinessException(ErrorCode.CREDIT_EXCEEDED);
        }
    }

    /** 확정 성적이 최소취득등급 이상이어야 합니다. 선수과목이 P/F 과목이면 P를 받았어야 합니다. */
    private void checkPrerequisites(String stdNo, String crsCd) {
        for (Prerequisite prerequisite : enrollmentRepository.findPrerequisites(crsCd)) {
            String grade =
                    gradeQuery.latestConfirmedGrade(stdNo, prerequisite.crsCd()).orElse(null);
            // 최소취득등급에 F 는 올 수 없으므로 F 는 언제나 미이수다
            boolean met = grade != null
                    && (prerequisite.passFail()
                            ? "P".equals(grade)
                            : rank(grade) >= 0 && rank(grade) <= rank(prerequisite.minGrade()));
            if (!met) {
                throw new BusinessException(
                        ErrorCode.PREREQUISITE_NOT_MET, "선수과목 " + prerequisite.crsNm() + "을(를) 이수하지 않았습니다.");
            }
        }
    }

    private static int rank(String grade) {
        return GRADE_ORDER.indexOf(grade);
    }

    private String requireStudent(CurrentUser user) {
        if (!user.is(Role.STUDENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user.usrNo();
    }

    private int registrationSemester() {
        return schedulePolicy
                .registrationSemester()
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_REGISTRATION_SEMESTER));
    }

    private StudentState lockStudent(String stdNo) {
        return enrollmentRepository.lockStudent(stdNo).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void checkEnrollOpen(int semNo, StudentState student) {
        if (!schedulePolicy.enrollOpen(semNo, student.yrLvl())) {
            throw new BusinessException(ErrorCode.ENROLL_CLOSED);
        }
    }
}
