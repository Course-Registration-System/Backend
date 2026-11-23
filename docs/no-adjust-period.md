# 정정 기간이 없는 학기의 종료 시각

학사일정 등록(ACA-01)에서 정정(ADJUST)은 필수가 아니다. 정정 일정이 없는 학기에는 `SchedulePolicy.adjustStart/adjustEnd` 가 비어 있고, 지금 코드는 이것을 "아직 안 지남"으로 보아 아래 기간이 끝나지 않는다(#24 리뷰).

## 규칙

수강신청 기간의 끝은 정정 기간을 포함한다. 정정이 없으면 마지막 수강신청일이 끝나는 시각을 정정 기간 시작·종료 시각으로 본다.

| 업무 | 지금 기준 | 정정이 없는 학기 |
| --- | --- | --- |
| REG-05 관리자 추가·취소 | 첫 수강신청일 ~ 정정 종료 전 | 첫 수강신청일 ~ 마지막 수강신청일 종료 전 |
| ACA-03 강의실·담당 교수·정원 변경 | 정정 종료 전 | 마지막 수강신청일 종료 전 |
| ACA-03 폐강 | 정정 종료 전 | 마지막 수강신청일 종료 전 |
| ACA-03 강의실 미배정 | 정정 시작 전 | 마지막 수강신청일 종료 전 |

수강신청 일정도 정정 일정도 없으면 지금처럼 "아직 시작 안 함"으로 본다.

- REG-05: 첫 수강신청일이 없으므로 추가·취소할 수 없다.
- ACA-03: 일정 등록 전이므로 수정·폐강할 수 있다.

업무 분석서 ACA-03·REG-05 업무 규칙에도 같은 문장을 넣었다.

## SchedulePolicy (academic 모듈)

`SchedulePolicy.registrationEnd` 로 학기의 신청 종료 시각을 돌려준다.

```java
/** ENROLL·ADJUST 일정 중 가장 늦은 종료 시각. 둘 다 없으면 빈 값 */
Optional<LocalDateTime> registrationEnd(int semNo);
```

```sql
SELECT MAX(end_dtm) FROM academic_schedule
 WHERE sem_no = ? AND schd_type IN ('ENROLL', 'ADJUST')
```

## 적용 (모듈 B)

- `EnrollmentService.checkAdminPeriod`: `adjustEnd` → `registrationEnd`
- `LectureAdminService`: 변경·폐강 마감은 `adjustEnd` → `registrationEnd`
- 미배정 마감은 `adjustStart` 그대로 둔다. 정정이 없으면 이 검사는 열려 있지만, 신청 종료 뒤에는 변경 자체가 막혀 결과적으로 마지막 수강신청일 종료까지만 미배정이 허용된다.
