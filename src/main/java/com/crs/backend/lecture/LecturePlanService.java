package com.crs.backend.lecture;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.course.EvalType;
import com.crs.backend.lecture.LecturePlanRepository.PlanRow;
import com.crs.backend.lecture.dto.LecturePlanResponse;
import com.crs.backend.lecture.dto.PlanWeights;
import com.crs.backend.lecture.dto.PlanWeightsRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 강의계획서 관리(LEC-03). 교수와 권한 있는 조교가 장바구니 기간 시작 전까지 계획서 PDF 를 올리고(교체) 평가 반영 비율을 입력합니다.
 * 계획서는 로그인한 사용자 모두 내려받을 수 있습니다(학생이 강의 검색에서 봄).
 */
@Service
public class LecturePlanService {

    /** 계획서 파일 크기 한도. application.yml 의 spring.servlet.multipart.max-file-size 와 같다 */
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final LecturePlanRepository lecturePlanRepository;
    private final PlanFileStorage planFileStorage;
    private final LectureAccess lectureAccess;
    private final SchedulePolicy schedulePolicy;
    private final Clock clock;

    public LecturePlanService(
            LecturePlanRepository lecturePlanRepository,
            PlanFileStorage planFileStorage,
            LectureAccess lectureAccess,
            SchedulePolicy schedulePolicy,
            Clock clock) {
        this.lecturePlanRepository = lecturePlanRepository;
        this.planFileStorage = planFileStorage;
        this.lectureAccess = lectureAccess;
        this.schedulePolicy = schedulePolicy;
        this.clock = clock;
    }

    /** 계획서·반영 비율 조회. 없는 강의는 권한보다 먼저 404 입니다. */
    @Transactional(readOnly = true)
    public LecturePlanResponse view(CurrentUser user, int lecNo) {
        PlanRow plan =
                lecturePlanRepository.find(lecNo).orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        lectureAccess.checkManage(user, lecNo);
        return toResponse(plan);
    }

    /** 계획서 PDF 올리기·교체. 강의당 하나라 같은 경로에 덮어씁니다. */
    @Transactional
    public LecturePlanResponse uploadFile(CurrentUser user, int lecNo, MultipartFile file) {
        // 404 → 403 → 409(폐강·마감) → 413·400 순서. 크기 한도를 넘는 요청 본문은 서버가 먼저 거절하고(GlobalExceptionHandler),
        // 여기는 한도를 넘지 않는 설정에서의 두 번째 확인이다
        PlanRow plan = lockEditable(user, lecNo);
        if (file != null && file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PLAN_FILE_TOO_LARGE);
        }
        if (file == null || file.isEmpty() || !isPdf(file)) {
            throw new BusinessException(ErrorCode.INVALID_PLAN_FILE);
        }

        String path = PlanFileStorage.relativePath(plan.semNo(), plan.crsCd(), plan.sectNo());
        try (InputStream content = file.getInputStream()) {
            planFileStorage.save(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        lecturePlanRepository.updatePlanFile(lecNo, path);
        return reload(lecNo);
    }

    /** 평가 반영 비율 입력. 합이 100 이어야 하고, P/F 과목은 받지 않습니다. */
    @Transactional
    public LecturePlanResponse updateWeights(CurrentUser user, int lecNo, PlanWeightsRequest request) {
        int mid = request.midWt().intValueExact();
        int fin = request.finWt().intValueExact();
        int hw = request.hwWt().intValueExact();
        int att = request.attWt().intValueExact();
        if (mid + fin + hw + att != 100) {
            throw new BusinessException(ErrorCode.WEIGHT_SUM_INVALID);
        }

        PlanRow plan = lockEditable(user, lecNo);
        if (plan.evalType() == EvalType.PASS_FAIL) {
            throw new BusinessException(ErrorCode.WEIGHTS_NOT_ALLOWED);
        }
        lecturePlanRepository.updateWeights(lecNo, mid, fin, hw, att);
        return reload(lecNo);
    }

    /** 내려받을 계획서. 강의가 없으면 404 LECTURE_NOT_FOUND, 계획서가 없거나 파일이 없으면 404 PLAN_NOT_FOUND 입니다. */
    @Transactional(readOnly = true)
    public PlanDownload download(int lecNo) {
        PlanRow plan =
                lecturePlanRepository.find(lecNo).orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        Path file = Optional.ofNullable(plan.planFile())
                .flatMap(planFileStorage::find)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        return new PlanDownload(file, plan.crsCd() + "-" + plan.sectNo() + ".pdf");
    }

    /** 내려받기: 디스크의 파일과 응답에 붙일 파일 이름. */
    public record PlanDownload(Path file, String fileName) {}

    // 등록·수정 공통: 강의 행 잠금 → 404 → 403 → 폐강 → 마감
    private PlanRow lockEditable(CurrentUser user, int lecNo) {
        PlanRow plan =
                lecturePlanRepository.lock(lecNo).orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        lectureAccess.checkManage(user, lecNo);
        if (plan.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        if (!beforeDeadline(schedulePolicy.cartStart(plan.semNo()))) {
            throw new BusinessException(ErrorCode.PLAN_PERIOD_CLOSED);
        }
        return plan;
    }

    /** 장바구니 시작(일정이 없으면 수강신청 시작) 전인지. 둘 다 없는 학기는 기한이 없다 */
    private boolean beforeDeadline(Optional<LocalDateTime> deadline) {
        LocalDateTime now = LocalDateTime.now(clock);
        return deadline.map(now::isBefore).orElse(true);
    }

    /** 파일 내용이 %PDF- 로 시작하는지. 이름·Content-Type 은 바꾸기 쉬워 보지 않는다 */
    private static boolean isPdf(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return Arrays.equals(in.readNBytes(PDF_MAGIC.length), PDF_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    private LecturePlanResponse reload(int lecNo) {
        return toResponse(lecturePlanRepository.find(lecNo).orElseThrow());
    }

    private LecturePlanResponse toResponse(PlanRow plan) {
        Optional<LocalDateTime> deadline = schedulePolicy.cartStart(plan.semNo());
        PlanWeights weights = plan.evalType() == EvalType.GRADE && plan.midWt() != null
                ? new PlanWeights(plan.midWt(), plan.finWt(), plan.hwWt(), plan.attWt())
                : null;
        return new LecturePlanResponse(
                plan.lecNo(),
                plan.crsCd(),
                plan.crsNm(),
                plan.sectNo(),
                plan.evalType(),
                plan.planFile() != null,
                weights,
                deadline.orElse(null),
                plan.lecStat() == LecStat.OPEN && beforeDeadline(deadline));
    }
}
