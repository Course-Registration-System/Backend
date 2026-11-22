"""개발·테스트·시연에 함께 쓰는 시드 데이터를 만든다.

사용: python -X utf8 tools/seed/build_seed.py   (표준 라이브러리만 쓴다. 난수 시드가 고정이라 매번 같은 결과가 나온다)

만드는 파일 (모두 생성물이다. 직접 고치지 말고 이 생성기를 고친다)
- src/main/resources/db/seed/R__seed_dev.sql        기본 시드(T0, 2026-2 성적 입력 기간). Flyway 가 서버 시작 시 적용한다
- src/main/resources/db/scenario/s1~s3_*.sql       뒤 시점으로 옮기는 오버레이(성적 마감 → 신청일 → 정정)
- src/test/java/com/crs/backend/support/SeedFixtures.java   테스트에서 사례를 이름으로 가리키는 상수
- docs/seed-data.md                                 모듈별 안내(시점, 계정, 사례)

데이터가 나타내는 시점(스냅숏)은 2027-02-10 18:00이다.
- 현재 학기 2026-2(성적 확정 완료), 신청 대상 학기 2027-1
- 진급·졸업 처리와 2027-1 최대학점 산정이 끝났다
- 2027-1 수강신청은 4학년(02-09)·3학년(02-10) 날이 지났고, 2·1학년은 장바구니만 있다
졸업 판정에 4년치 이력이 필요해서 2023-1부터 만든다.
졸업 = 다닌 정규학기 8개 이상, 채플을 뺀 이수 학점 130 이상, 채플 P 학기 6개 이상.
채플은 학기마다 다시 신청한다(재수강이 아니다, retake_yn N). 같은 학기에 두 번은 못 듣고, 계절학기에도 연다.
"""
from pathlib import Path
from datetime import datetime, timedelta
import itertools
import math
import random

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
OUT_SEED = REPO / "src/main/resources/db/seed/R__seed_dev.sql"
OUT_SCENARIO = REPO / "src/main/resources/db/scenario"
OUT_FIXTURES = REPO / "src/test/java/com/crs/backend/support/SeedFixtures.java"
OUT_GUIDE = REPO / "docs/seed-data.md"
rng = random.Random(20261118)

# 모든 mock 계정의 비밀번호는 test1234! (BCrypt cost 10, Spring BCryptPasswordEncoder와 호환되는 $2a$ 형식)
PWD_HASH = "$2a$10$mVtkuC8t.ybJShFMmg7dAePnho.f1IpD92TXrTXipF.OhtbWdF/.S"
SNAPSHOT = datetime(2026, 12, 21, 10, 0)   # T0: 2026-2 성적 입력 기간
ADMIN = "9001"
GRAD_CREDITS = 130          # 졸업 이수 학점
GRAD_SEMESTERS = 8          # 졸업에 필요한 정규학기 수
GRAD_CHAPELS = 6            # 졸업에 필요한 채플 P 학기 수(정규·계절 모두 셈)

GRADE_TABLE = [("A+", 97, 4.5), ("A0", 94, 4.3), ("A-", 90, 4.0), ("B+", 87, 3.5), ("B0", 84, 3.3), ("B-", 80, 3.0),
               ("C+", 77, 2.5), ("C0", 74, 2.3), ("C-", 70, 2.0), ("D+", 67, 1.5), ("D0", 64, 1.3), ("D-", 60, 1.0),
               ("F", 0, 0.0)]
ORDER = [g for g, _, _ in GRADE_TABLE]
PT = {g: p for g, _, p in GRADE_TABLE}
MID = {"A+": 98, "A0": 95, "A-": 91, "B+": 88, "B0": 85, "B-": 81, "C+": 78, "C0": 75, "C-": 71,
       "D+": 68, "D0": 65, "D-": 61, "F": 50}


def grade_of(tot):
    return next(g for g, lo, _ in GRADE_TABLE if tot >= lo)


def better(a, b):  # a가 b보다 좋은 등급인가
    return ORDER.index(a) < ORDER.index(b)


# ---------------------------------------------------------------- 기준 데이터
DEPTS = [(101, "컴퓨터학부"), (102, "경영학부"), (103, "교양교육원")]
ROOMS = [("21301", "정보관 301호"), ("21302", "정보관 302호"), ("21303", "정보관 303호"),
         ("06101", "경영관 101호"), ("06102", "경영관 102호"), ("06103", "경영관 103호"), ("01101", "대강당")]
NORMAL_ROOMS = [r for r, _ in ROOMS if r != "01101"]

# (교번, 이름, 학과, 연구실, 계정상태, 마지막으로 강의한 학기번호)
PROFS = [("100001", "김정훈", 101, "정보관 501호", "ACTIVE", 99), ("100002", "이수진", 101, "정보관 502호", "ACTIVE", 99),
         ("100003", "박성민", 102, "경영관 401호", "ACTIVE", 99), ("100004", "최은영", 102, "경영관 402호", "ACTIVE", 99),
         ("100005", "정다은", 103, "본관 301호", "ACTIVE", 99), ("100006", "한상철", 101, None, "INACTIVE", 4)]

MAJOR_NAMES = {
    101: ["프로그래밍기초", "컴퓨터개론", "이산수학", "객체지향프로그래밍", "디지털논리", "선형대수",
          "자료구조", "컴퓨터구조", "확률과통계", "운영체제", "웹프로그래밍", "파일처리",
          "알고리즘", "데이터베이스", "컴퓨터네트워크", "소프트웨어공학", "데이터베이스응용", "인공지능",
          "캡스톤디자인1", "정보보호", "컴파일러", "캡스톤디자인2", "분산시스템", "클라우드컴퓨팅"],
    102: ["경영학원론", "회계원리", "경제학원론", "경영통계", "조직행동론", "미시경제",
          "마케팅원론", "재무관리", "인사관리", "생산운영관리", "경영정보시스템", "관리회계",
          "마케팅조사", "투자론", "국제경영", "소비자행동", "전략경영", "세무회계",
          "경영캡스톤1", "기업가정신", "경영컨설팅", "경영캡스톤2", "브랜드관리", "경영윤리"],
}
# 교양: (일련, 이름, 학점, 이수구분, 평가방식, 권장학년)
GEN = [(1, "채플", 0.5, "CHAPEL", "PASS_FAIL", None), (2, "사회봉사", 1, "FREE_ELEC", "PASS_FAIL", None),
       (3, "글쓰기와표현", 2, "GEN_REQ", "GRADE", 1), (4, "대학영어", 2, "GEN_REQ", "GRADE", 1),
       (5, "컴퓨팅적사고", 3, "GEN_REQ", "GRADE", 1), (6, "교육학개론", 2, "TEACHING", "GRADE", 2),
       (7, "심리학의이해", 3, "GEN_ELEC", "GRADE", None), (8, "철학과삶", 3, "GEN_ELEC", "GRADE", None),
       (9, "현대사회와윤리", 3, "GEN_ELEC", "GRADE", None), (10, "세계사", 3, "GEN_ELEC", "GRADE", None),
       (11, "과학기술과사회", 3, "GEN_ELEC", "GRADE", None), (12, "영화로읽는문화", 3, "GEN_ELEC", "GRADE", None),
       (13, "글로벌비즈니스영어", 3, "GEN_ELEC", "GRADE", None), (14, "창업과혁신", 3, "GEN_ELEC", "GRADE", None),
       (15, "환경과인간", 3, "GEN_ELEC", "GRADE", None), (16, "음악의이해", 3, "GEN_ELEC", "GRADE", None),
       (17, "진로탐색", 1, "FREE_ELEC", "PASS_FAIL", None)]

COURSES = {}  # crs_cd -> dict
for dept, names in MAJOR_NAMES.items():
    for i, nm in enumerate(names):
        yr, k = i // 6 + 1, i % 6
        cmpl = "MAJOR_BASIC" if yr == 1 else ("MAJOR_REQ" if yr in (2, 3) and k % 3 == 0 else "MAJOR_ELEC")
        COURSES[f"{dept}{i + 1:04d}"] = dict(cd=f"{dept}{i + 1:04d}", dept=dept, nm=nm, crdt=3, cmpl=cmpl,
                                             eval="GRADE", rcmd=yr, term="FIRST" if k < 3 else "SECOND")
for no, nm, crdt, cmpl, ev, rcmd in GEN:
    COURSES[f"103{no:04d}"] = dict(cd=f"103{no:04d}", dept=103, nm=nm, crdt=crdt, cmpl=cmpl, eval=ev, rcmd=rcmd, term="BOTH")
CHAPEL = "1030001"
# 2026-2에 처음 개설한 교양. 학생 대부분이 들어 수강생 10명 이상(상대평가 대상)인 성적 입력 시연 강의가 된다
POPULAR = "1030018"
COURSES[POPULAR] = dict(cd=POPULAR, dept=103, nm="데이터리터러시", crdt=3, cmpl="GEN_ELEC", eval="GRADE", rcmd=None,
                        term="NEW")
PREREQS = [("1010007", "1010001", "D-"),   # 자료구조 ← 프로그래밍기초
           ("1010013", "1010007", "D-"),   # 알고리즘 ← 자료구조
           ("1010014", "1010007", "C0"),   # 데이터베이스 ← 자료구조(C0 이상)
           ("1010017", "1010014", "D-"),   # 데이터베이스응용 ← 데이터베이스
           ("1010022", "1010019", "D-"),   # 캡스톤디자인2 ← 캡스톤디자인1
           ("1020008", "1020002", "D-")]   # 재무관리 ← 회계원리
PREQ = {}
for c, p, g in PREREQS:
    PREQ.setdefault(c, []).append((p, g))

# (학기번호, 연도, 학기구분). 2026-2가 현재 학기, 2027-1이 신청 대상 학기
SEMS = [(1, 2023, "FIRST"), (2, 2023, "SECOND"), (3, 2024, "FIRST"), (4, 2024, "SECOND"), (5, 2025, "FIRST"),
        (6, 2025, "SECOND"), (7, 2026, "FIRST"), (8, 2026, "SUMMER"), (9, 2026, "SECOND"), (10, 2026, "WINTER"),
        (11, 2027, "FIRST")]
SEM = {s: (y, t) for s, y, t in SEMS}
CUR, TARGET = 9, 11
REGULAR = [s for s, _, t in SEMS if t in ("FIRST", "SECOND")]


def dt(y, m, d, hh=10, mm=0):
    return datetime(y, m, d, hh, mm)


def schedules(sem):
    """학기마다 장바구니 → 수강신청(학년별) → 정정 → 성적입력. 계절학기 신청은 전 학년."""
    y, t = SEM[sem]
    if t == "FIRST":
        cart, enr, adj, grd = ((1, 18), (1, 22)), (2, 9), ((3, 3), (3, 9)), ((6, 15), (6, 26))
    elif t == "SECOND":
        cart, enr, adj, grd = ((7, 20), (7, 24)), (8, 10), ((9, 2), (9, 8)), ((12, 14), (12, 23))
    else:
        rows = {"SUMMER": [("CART", (5, 25), (5, 27)), ("ENROLL", (6, 1), (6, 2)), ("ADJUST", (6, 22), (6, 23)),
                           ("GRADE", (7, 20), (7, 24))],
                "WINTER": [("CART", (11, 23), (11, 25)), ("ENROLL", (11, 30), (12, 1)), ("ADJUST", (12, 21), (12, 22)),
                           ("GRADE", (13, 18), (13, 22))]}[t]
        out = []
        for typ, (m1, d1), (m2, d2) in rows:
            y1, y2 = (y + 1, y + 1) if m1 == 13 else (y, y)
            out.append((typ, None, dt(y1, (m1 - 1) % 12 + 1, d1, 10), dt(y2, (m2 - 1) % 12 + 1, d2, 17)))
        return out
    out = [("CART", None, dt(y, *cart[0], 10), dt(y, *cart[1], 17))]
    for i, lvl in enumerate([4, 3, 2, 1]):
        d = dt(y, enr[0], enr[1]) + timedelta(days=i)
        out.append(("ENROLL", lvl, d, d.replace(hour=17)))
    out.append(("ADJUST", None, dt(y, *adj[0], 10), dt(y, *adj[1], 17)))
    out.append(("GRADE", None, dt(y, *grd[0], 9), dt(y, *grd[1], 18)))
    return out


SCHED = {s: schedules(s) for s, _, _ in SEMS}


def sched_of(sem, typ, lvl=None):
    return next(r for r in SCHED[sem] if r[0] == typ and (lvl is None or r[1] in (lvl, None)))


# ---------------------------------------------------------------- 시간표 칸
def mins(h, m=0):
    return h * 60 + m


PAIRS = [("MON", "WED"), ("TUE", "THU")]
STARTS = [mins(9), mins(10, 30), mins(12), mins(13, 30), mins(15), mins(16, 30)]
PAIR_OPTS = [[(a, s, s + 75), (b, s, s + 75)] for a, b in PAIRS for s in STARTS]
FRI_OPTS = [[("FRI", s, s + 110)] for s in (mins(9), mins(11), mins(13), mins(15))]
CHAPEL_SLOT = [("THU", mins(12), mins(12, 50))]


def overlap(a, b):
    return any(d1 == d2 and s1 < e2 and s2 < e1 for d1, s1, e1 in a for d2, s2, e2 in b)


WEIGHTS = [(30, 40, 20, 10), (35, 35, 20, 10), (40, 40, 10, 10), (30, 30, 30, 10)]

# ---------------------------------------------------------------- 강의 개설
LECTURES = []          # dict 목록
room_busy, prof_busy = {}, {}


def open_lecture(sem, cd, sect=1, cap=None, cls="OFFLINE", no_room=False, eng="N", exmp="N", weights=True,
                 stat="OPEN", prof=None, slots=None, room_fixed=None):
    c = COURSES[cd]
    if slots:
        opts = [slots]
    elif cd == CHAPEL:
        opts = [CHAPEL_SLOT]
    elif c["crdt"] == 3:
        opts = PAIR_OPTS[:]
    else:
        opts = FRI_OPTS[:]
    if not slots:   # 시간·강의실을 고정한 강의는 난수를 쓰지 않는다(다른 학기 데이터가 바뀌지 않게)
        rng.shuffle(opts)
    if prof:
        cands = [prof]
    else:
        pool = [p for p, _, d, _, _, last in PROFS if sem <= last and (d == c["dept"] or c["dept"] == 103)]
        pool.sort(key=lambda p: (0 if dict((x[0], x[2]) for x in PROFS)[p] == c["dept"] else 1,
                                 len(prof_busy.get((sem, p), []))))
        cands = pool
    for slot in opts:
        for p in cands:
            if any(overlap(slot, s) for s in prof_busy.get((sem, p), [])):
                continue
            room = None
            if not no_room and cls == "OFFLINE":
                rooms = [room_fixed] if room_fixed else (
                    ["01101"] if cd == CHAPEL else rng.sample(NORMAL_ROOMS, len(NORMAL_ROOMS)))
                room = next((r for r in rooms if not any(overlap(slot, s) for s in room_busy.get((sem, r), []))), None)
                if room is None:
                    continue
                room_busy.setdefault((sem, room), []).append(slot)
            prof_busy.setdefault((sem, p), []).append(slot)
            lec = dict(sem=sem, cd=cd, sect=sect, prof=p, slot=slot, room=room, cls=cls,
                       cap=cap or (300 if cd == CHAPEL else (60 if c["dept"] == 103 else 40)),
                       eng=eng, exmp=exmp, stat=stat,
                       wt=(rng.choice(WEIGHTS) if weights and c["eval"] == "GRADE" else None), keep=False)
            LECTURES.append(lec)
            return lec
    raise RuntimeError(f"시간·강의실 배정 실패: {sem} {cd}")


SEASONAL_OFFER = {8: ["1030007", "1010006", CHAPEL], 10: ["1030008", "1030010", "1030006"]}
for sem, y, t in SEMS:
    if t in ("FIRST", "SECOND"):
        for cd, c in COURSES.items():
            if c["term"] in (t, "BOTH"):
                kw = {}
                if sem == TARGET:
                    if cd == "1030011":
                        kw = dict(cls="ONLINE")            # 온라인 강의
                    elif cd == "1030014":
                        kw = dict(no_room=True)            # 대면인데 강의실 미배정
                    elif cd == "1030016":
                        kw = dict(stat="CLOSED")           # 폐강
                    elif cd == "1010015":
                        kw = dict(eng="Y")                 # 전공 영어강의(상대평가 제외)
                    elif cd == "1030013":
                        kw = dict(eng="Y")                 # 교양 영어강의(상대평가 대상 그대로)
                    elif cd == "1020015":
                        kw = dict(exmp="Y")                # 상대평가 제외 인정
                    if cd in ("1010019", "1020019", "1030009"):
                        kw["weights"] = False              # 반영비율 아직 입력 안 함
                open_lecture(sem, cd, **kw)
        if sem == TARGET:
            open_lecture(sem, "1030007", sect=2)           # 같은 과목 2분반
        if sem == CUR:
            open_lecture(sem, POPULAR, cap=60, slots=[("MON", mins(9), mins(10, 15)), ("WED", mins(9), mins(10, 15))],
                         room_fixed="01101", weights=False)["wt"] = WEIGHTS[0]   # 고정값(난수를 쓰지 않음)
    else:
        for cd in SEASONAL_OFFER[sem]:
            open_lecture(sem, cd, cap=30)


def find_lec(sem, cd, sect=1):
    return next(l for l in LECTURES if l["sem"] == sem and l["cd"] == cd and l["sect"] == sect)


# ---------------------------------------------------------------- 학생
# 학번, 이름, 학과, 입학연도, 휴학한 정규학기, 목표 학점, 실력(평균 점수)
STUDENTS = [
    ("20230001", "김민준", 101, 2023, set(), 18, 93), ("20230002", "이서연", 102, 2023, set(), 15, 74),
    ("20230003", "박도윤", 101, 2023, {7, 9}, 18, 80),
    ("20240001", "최지우", 101, 2024, set(), 18, 87), ("20240002", "정하준", 102, 2024, set(), 18, 86),
    ("20240003", "강서준", 101, 2024, set(), 18, 76), ("20240004", "윤예린", 101, 2024, set(), 18, 80),
    ("20240005", "장수아", 102, 2024, set(), 18, 84),
    ("20250001", "임지호", 101, 2025, set(), 18, 83), ("20250002", "한유나", 102, 2025, {7, 9}, 18, 90),
    ("20250003", "오시우", 101, 2025, set(), 18, 79),
    ("20260001", "서준우", 101, 2026, set(), 18, 82), ("20260002", "권하은", 102, 2026, set(), 18, 85),
    ("20260003", "배지안", 101, 2026, set(), 18, 78), ("20260004", "송태윤", 102, 2026, set(), 18, 81),
    ("20270001", "문서윤", 101, 2027, set(), 18, 80), ("20270002", "류채원", 102, 2027, set(), 18, 80),
]
ST = {s[0]: dict(no=s[0], nm=s[1], dept=s[2], entry=s[3], leave=s[4], load=s[5], ab=s[6]) for s in STUDENTS}
STILL_ON_LEAVE = {"20230003"}            # 스냅숏 시점에도 휴학 중
TA = [("20240005", CUR, "1020004"), ("20240005", TARGET, "1020001")]

# 강제 수강: {학번: {학기: [(과목, 등급, 재수강여부)]}}. 등급 None이면 성적 없음(2027-1)
FORCED = {
    "20240003": {3: [("1010003", "F", "N")], 5: [("1010003", "A+", "Y"), ("1010007", "B0", "N")],
                 7: [("1010014", "F", "N"), ("1010015", "C+", "N")], 11: [("1010014", None, "Y")]},
    "20240004": {5: [("1010007", "D+", "N")]},
    "20250003": {9: [("1030002", "P", "N"), ("1030017", "P", "N")]},
}
ONLY_FORCED = {("20250003", 9)}
PIN_SEM = {("20240001", 9): "ALL_A-", ("20240002", 9): "JUST_UNDER_4", ("20250002", 6): "ALL_A0",
           ("20260001", 9): "ALL_A-"}   # 20260001: 시연 강의(데이터리터러시)까지 A-면 평점 4.0 → 2027-1 최대학점 21
# 2학년 1학기 최대학점은 1학년 두 학기 평균으로 정해서(ACA-01) 1학년 첫 학기도 고정한다.
# 나중에 넣은 고정이라 다른 데이터가 바뀌지 않게 점수를 뽑은 뒤 덮어쓴다(난수 흐름 유지)
LATE_PIN = {("20260001", 7): "A-", ("20250002", 5): "A0"}
# 계절 수강: {학기: {과목: (인원, 후보 학번 순서)}}. 이미 들은 과목이면 다음 후보로 넘어간다
# 채플은 이미 들었어도 다시 듣고(학기마다 신청), 다른 계절 강의와 함께 들을 수 있다(시간이 겹치지 않으면)
SEASONAL_ENROLL = {8: {"1030007": (2, ["20260001", "20240003", "20260002", "20250001"]),
                       "1010006": (1, ["20260003", "20260001"]),
                       CHAPEL: (3, ["20230003", "20230002", "20250002", "20260001"])},
                   10: {"1030008": (3, ["20230003", "20260001", "20250001", "20260003", "20240004"]),
                        "1030010": (2, ["20230003", "20260002", "20240001", "20260004", "20250003", "20230002", "20250002"]),
                        "1030006": (2, ["20230003", "20260004", "20240005", "20260002", "20230002", "20250001"])}}
WINTER_STATE = {"1030008": "CONFIRMED", "1030010": "SUBMITTED", "1030006": "ENTERING"}

ENR = []      # dict(std, lec, stat, retake, proc, enrl_dtm, cncl_dtm, grade dict or None)
PLANNED = []  # (std, 학년, chosen) 2027-1 신청일이 T0 뒤인 2·1학년. T3 오버레이에서 신청으로 만든다
CART = []     # (std, lec, reg_dtm)
MAXC = {}     # (std, sem) -> max


def history(std, before_sem):
    """확정된 이수 내역: [(sem, cd, grade)] (취소 제외)."""
    return [(e["lec"]["sem"], e["lec"]["cd"], e["grade"]["ltr"]) for e in ENR
            if e["std"] == std and e["stat"] == "ENROLLED" and e["grade"] and e["grade"]["ltr"]
            and e["lec"].get("gstat") == "CONFIRMED" and order_key(e["lec"]["sem"]) < order_key(before_sem)]


def order_key(sem):
    y, t = SEM[sem]
    return (y, {"FIRST": 1, "SUMMER": 2, "SECOND": 3, "WINTER": 4}[t])


def latest(std, before_sem):
    out = {}
    for sem, cd, g in sorted(history(std, before_sem), key=lambda x: order_key(x[0])):
        out[cd] = g
    return out


def passed(std, cd, min_grd, before_sem):
    g = latest(std, before_sem).get(cd)
    if g is None:
        return False
    if COURSES[cd]["eval"] == "PASS_FAIL":
        return g == "P"
    return g != "F" and not better(min_grd, g)


def attended_regular(std, before_sem):
    return sorted({e["lec"]["sem"] for e in ENR if e["std"] == std and e["stat"] == "ENROLLED"
                   and e["lec"]["sem"] in REGULAR and order_key(e["lec"]["sem"]) < order_key(before_sem)
                   and e["lec"].get("gstat") == "CONFIRMED"}, key=order_key)


def grad_credits(std, before_sem):
    """졸업 이수 학점: 과목마다 마지막 성적이 F가 아닌 학점의 합(채플 제외)."""
    return sum(COURSES[cd]["crdt"] for cd, g in latest(std, before_sem).items() if g != "F" and cd != CHAPEL)


def chapel_sems(std, before_sem):
    """채플을 P로 이수한 학기 수(정규·계절 모두)."""
    return len({sem for sem, cd, g in history(std, before_sem) if cd == CHAPEL and g == "P"})


def sem_gpa(std, sem):
    rows = [(COURSES[e["lec"]["cd"]]["crdt"], PT[e["grade"]["ltr"]]) for e in ENR
            if e["std"] == std and e["lec"]["sem"] == sem and e["stat"] == "ENROLLED" and e["grade"]
            and COURSES[e["lec"]["cd"]]["eval"] == "GRADE"]
    if not rows:
        return None
    return sum(c * p for c, p in rows) / sum(c for c, _ in rows)


def gpa_over(std, sems):
    rows = [(COURSES[e["lec"]["cd"]]["crdt"], PT[e["grade"]["ltr"]]) for e in ENR
            if e["std"] == std and e["lec"]["sem"] in sems and e["stat"] == "ENROLLED" and e["grade"]
            and COURSES[e["lec"]["cd"]]["eval"] == "GRADE"]
    if not rows:
        return None
    return sum(c * p for c, p in rows) / sum(c for c, _ in rows)


def max_credit_regular(std, sem):
    """마지막으로 다닌 정규학기 평점 4.0 이상이면 21. 2학년 1학기(다닌 정규학기 2개)는 1학년 두 학기 평균으로 본다."""
    att = attended_regular(std, sem)
    if not att:
        return 18
    g = gpa_over(std, att) if len(att) == 2 else sem_gpa(std, att[-1])
    return 21 if g is not None and g >= 4.0 else 18


def yr_level(std, sem):
    return min(4, len(attended_regular(std, sem)) // 2 + 1)


def is_attending(std, sem):
    """그 학기에 재학 중인가. 계절학기는 바로 앞 정규학기의 학적상태를 따른다."""
    s = ST[std]
    if SEM[sem][0] < s["entry"]:
        return False
    if SEM[sem][1] in ("SUMMER", "WINTER"):
        sem = max((r for r in REGULAR if order_key(r) < order_key(sem)), key=order_key)
    return sem not in s["leave"] and not (std in STILL_ON_LEAVE and sem == TARGET)


def exists_at(std, sem):
    return SEM[sem][0] >= ST[std]["entry"]


def candidates(std, sem, yr):
    s = ST[std]
    this_sem = {e["lec"]["cd"] for e in ENR if e["std"] == std and e["lec"]["sem"] == sem}
    taken = {cd for _, cd, _ in history(std, sem)} | this_sem
    offered = {l["cd"]: l for l in LECTURES if l["sem"] == sem and l["sect"] == 1 and l["stat"] == "OPEN"}
    own = [cd for cd, c in COURSES.items() if c["dept"] == s["dept"]]
    other = [cd for cd, c in COURSES.items() if c["dept"] not in (s["dept"], 103)]
    gen_req = [cd for cd, c in COURSES.items() if c["cmpl"] == "GEN_REQ"]
    gen_el = [cd for cd, c in COURSES.items() if c["cmpl"] in ("GEN_ELEC",) and cd != POPULAR]
    rng.shuffle(gen_el)
    order = []
    order.append(CHAPEL)   # 채플은 학기마다 다시 신청한다(재수강 아님)
    if sem == CUR:
        order.append(POPULAR)
    order += [cd for cd in own if COURSES[cd]["rcmd"] == yr]
    order += [cd for cd in gen_req if COURSES[cd]["rcmd"] <= yr]
    order += [cd for cd in own if COURSES[cd]["rcmd"] < yr]
    order += gen_el
    if s["dept"] == 102 and yr >= 2:
        order.append("1030006")
    order += [cd for cd in other if COURSES[cd]["rcmd"] <= yr]
    out = []
    for cd in order:
        if (cd in taken and cd != CHAPEL) or cd in this_sem or cd not in offered or cd in out:
            continue
        if any(not passed(std, p, g, sem) for p, g in PREQ.get(cd, [])):
            continue
        out.append(cd)
    return [offered[cd] for cd in out]


def credit_excl_chapel(lecs):
    return sum(COURSES[l["cd"]]["crdt"] for l in lecs if l["cd"] != CHAPEL)


def choose(std, sem, mx, load):
    chosen = []
    for cd, g, rt in FORCED.get(std, {}).get(sem, []):
        chosen.append((find_lec(sem, cd), g, rt))
    if (std, sem) in ONLY_FORCED:
        return chosen
    yr = yr_level(std, sem)
    for lec in candidates(std, sem, yr):
        lecs = [c[0] for c in chosen]
        if credit_excl_chapel(lecs) >= min(load, mx):
            break
        if any(overlap(lec["slot"], l["slot"]) for l in lecs):
            continue
        if credit_excl_chapel(lecs + [lec]) > min(load, mx):
            continue
        chosen.append((lec, None, "N"))
    return chosen


def enroll_time(std, sem, idx):
    lvl = yr_level(std, sem) if SEM[sem][1] in ("FIRST", "SECOND") else None
    start = sched_of(sem, "ENROLL", lvl)[2]
    return start + timedelta(minutes=idx * 3 + int(std[-2:]) % 7)


def score_grade(lec, ab, forced, retake):
    c = COURSES[lec["cd"]]
    if c["eval"] == "PASS_FAIL":
        return dict(mid=None, fin=None, hw=None, att=None, tot=None, ltr=forced or "P", base=forced or "P")
    if forced:
        sc = [MID[forced]] * 4
    else:
        sc = [max(40, min(100, round(rng.gauss(ab, 7)))), max(40, min(100, round(rng.gauss(ab, 7)))),
              max(40, min(100, round(rng.gauss(ab + 5, 5)))), max(60, min(100, round(rng.gauss(97, 3))))]
    tot = math.floor(sum(s * w for s, w in zip(sc, lec["wt"])) / 100)
    g = grade_of(tot)
    if retake == "Y" and better(g, "A-"):
        g = "A-"   # 재수강 상한
    return dict(mid=sc[0], fin=sc[1], hw=sc[2], att=sc[3], tot=tot, ltr=g, base=g)


def relative_fix(lec, rows):
    """수강인원 10명 이상 상대평가 강의의 등급 비율을 맞춘다(보호 행은 건드리지 않는다)."""
    c = COURSES[lec["cd"]]
    n = len(rows)
    exempt = (c["eval"] != "GRADE" or n < 10 or lec["exmp"] == "Y"
              or (lec["eng"] == "Y" and c["cmpl"] in ("MAJOR_BASIC", "MAJOR_REQ", "MAJOR_ELEC")))
    if exempt:
        return
    is_a = lambda e: e["grade"]["ltr"] in ("A+", "A0", "A-")
    is_ab = lambda e: e["grade"]["ltr"][0] in "AB"
    a_lim = math.floor(n * (0.5 if c["cmpl"] == "TEACHING" else 0.4))
    ab_lim = n if c["cmpl"] == "TEACHING" else math.floor(n * 0.8)
    for test, lim, to in ((is_a, a_lim, "B+"), (is_ab, ab_lim, "C+")):
        hits = sorted([e for e in rows if test(e)], key=lambda e: (e["protect"], e["grade"]["tot"] or 0))
        while len(hits) > lim:
            e = hits.pop(0)
            assert not e["protect"], f"보호 행이 비율을 넘김: {lec['cd']}"
            e["grade"]["ltr"] = to


def just_under_4(lecs):
    """학기 평점이 4.0 바로 아래가 되는 등급 조합(등급제 과목만)."""
    gl = [l for l in lecs if COURSES[l["cd"]]["eval"] == "GRADE"]
    best = None
    for combo in itertools.product(["A+", "A0", "A-", "B+", "B0"], repeat=len(gl)):
        cr = [COURSES[l["cd"]]["crdt"] for l in gl]
        g = sum(c * PT[x] for c, x in zip(cr, combo)) / sum(cr)
        if g < 4.0 and (best is None or g > best[0]):
            best = (g, combo)
    return dict(zip([l["cd"] for l in gl], best[1]))


# ---------------------------------------------------------------- 학기 순서대로 쌓기
GRADUATED = set()
for sem, y, t in sorted(SEMS, key=lambda x: order_key(x[0])):
    if t in ("SUMMER", "WINTER"):
        for std in ST:
            if exists_at(std, sem) and std not in GRADUATED:
                MAXC[(std, sem)] = 6 if is_attending(std, sem) else 3
        # 이미 들은 과목은 건너뛰고, 계절학기에는 한 학생이 채플 말고는 한 강의만 듣는다
        for cd, (k, cands) in SEASONAL_ENROLL[sem].items():
            lec = find_lec(sem, cd)
            picked = 0
            for i, std in enumerate(cands):
                mine = [e["lec"] for e in ENR if e["std"] == std and e["lec"]["sem"] == sem]
                taken = {c for _, c, _ in history(std, sem)} | {e["lec"]["cd"] for e in ENR if e["std"] == std}
                busy = any((l["cd"] == CHAPEL) == (cd == CHAPEL) or overlap(l["slot"], lec["slot"]) for l in mine)
                if picked == k or (cd in taken and cd != CHAPEL) or busy:
                    continue
                picked += 1
                ENR.append(dict(std=std, lec=lec, stat="ENROLLED", retake="N", proc="STUDENT",
                                enrl_dtm=enroll_time(std, sem, i), cncl_dtm=None, grade=None, protect=False))
    else:
        if sem == TARGET:
            # 진급·졸업 처리: 2026-2 확정 뒤, 2027-1 최대학점 산정 전에 한 번 실행한다
            for std in ST:
                if ST[std]["entry"] <= 2026 and std not in STILL_ON_LEAVE:
                    att = attended_regular(std, sem)
                    if (len(att) >= GRAD_SEMESTERS and grad_credits(std, sem) >= GRAD_CREDITS
                            and chapel_sems(std, sem) >= GRAD_CHAPELS):
                        GRADUATED.add(std)
        for std in ST:
            if not is_attending(std, sem) or std in GRADUATED:
                continue
            mx = max_credit_regular(std, sem)
            MAXC[(std, sem)] = mx
            load = 21 if (std == "20230001" and mx == 21) else ST[std]["load"]
            chosen = choose(std, sem, mx, load)
            lvl = yr_level(std, sem)
            enrolls = sem != TARGET or lvl in (4, 3)          # 2027-1은 4·3학년만 신청 끝남
            cart_start = sched_of(sem, "CART")[2]
            if sem == TARGET and not enrolls:
                PLANNED.append((std, lvl, chosen))
            for i, (lec, g, rt) in enumerate(chosen):
                if sem == TARGET:
                    CART.append((std, lec, cart_start + timedelta(hours=i, minutes=int(std[-2:]))))
                if enrolls:
                    ENR.append(dict(std=std, lec=lec, stat="ENROLLED", retake=rt, proc="STUDENT",
                                    enrl_dtm=enroll_time(std, sem, i), cncl_dtm=None, grade=None, forced=g,
                                    protect=g is not None or (std, sem) in PIN_SEM))
    # 성적
    if sem == TARGET:
        continue
    for lec in [l for l in LECTURES if l["sem"] == sem]:
        rows = [e for e in ENR if e["lec"] is lec and e["stat"] == "ENROLLED"]
        for e in rows:
            pin = PIN_SEM.get((e["std"], sem))
            forced = e.get("forced")
            if pin == "ALL_A-":
                forced = forced or ("A-" if COURSES[lec["cd"]]["eval"] == "GRADE" else None)
            elif pin == "ALL_A0":
                forced = forced or ("A0" if COURSES[lec["cd"]]["eval"] == "GRADE" else None)
            elif pin == "JUST_UNDER_4":
                mine = [x["lec"] for x in ENR if x["std"] == e["std"] and x["lec"]["sem"] == sem and x["stat"] == "ENROLLED"]
                forced = forced or just_under_4(mine).get(lec["cd"])
            e["grade"] = score_grade(lec, ST[e["std"]]["ab"], forced, e["retake"])
            late = LATE_PIN.get((e["std"], sem))
            if late and forced is None and COURSES[lec["cd"]]["eval"] == "GRADE":
                forced = late
                e["grade"] = score_grade(lec, ST[e["std"]]["ab"], forced, e["retake"])
            e["protect"] = e.get("protect") or forced is not None
        relative_fix(lec, rows)
        lec["gstat"] = "CONFIRMED"
        if sem == 10:
            assert rows, f"겨울 강의 수강생 없음: {lec['cd']}"

# ---------------------------------------------------------------- 특별 사례
# 2026-2 정정 기간에 취소한 신청(성적 없음), 관리자 수동 추가 1건
lec = next(l for l in LECTURES if l["sem"] == CUR and l["cd"] == "1030015")
ENR.append(dict(std="20240002", lec=lec, stat="CANCELLED", retake="N", proc="STUDENT",
                enrl_dtm=enroll_time("20240002", CUR, 9), cncl_dtm=sched_of(CUR, "ADJUST")[2] + timedelta(hours=2),
                grade=None, protect=False))
next(e for e in ENR if e["std"] == "20250001" and e["lec"]["sem"] == CUR and e["lec"]["cd"] != CHAPEL)["proc"] = "ADMIN"
# 2027-1 신청 후 취소 1건
e = next(e for e in ENR if e["std"] == "20240002" and e["lec"]["sem"] == TARGET)
e["stat"], e["cncl_dtm"] = "CANCELLED", e["enrl_dtm"] + timedelta(hours=3)
# 선수과목 미달 학생이 장바구니에 데이터베이스를 담음(담기는 경고만)
CART.append(("20240004", find_lec(TARGET, "1010014"), sched_of(TARGET, "CART")[2] + timedelta(days=1)))
# 재수강 가능 과목(C+)을 장바구니에 담음
CART.append(("20240003", find_lec(TARGET, "1010015"), sched_of(TARGET, "CART")[2] + timedelta(days=1, hours=1)))

# 2027-1 정원: 가장 많이 신청된 강의를 마감, 다음 강의를 한 자리 남김. 다른 사례로 쓰는 과목은 고르지 않는다
CASE_COURSES = {CHAPEL, POPULAR, "1010015", "1030016", "1030014", "1030011", "1020015", "1030013", "1020001", "1010014",
                "1010019", "1020019", "1030009"}
cnt = lambda l: sum(1 for e in ENR if e["lec"] is l and e["stat"] == "ENROLLED")
tgt = sorted([l for l in LECTURES if l["sem"] == TARGET and l["cd"] not in CASE_COURSES and l["stat"] == "OPEN"],
             key=lambda l: (-cnt(l), l["cd"]))
FULL, ONE_LEFT = tgt[0], tgt[1]
FULL["cap"], ONE_LEFT["cap"] = cnt(FULL), cnt(ONE_LEFT) + 1
for std in ("20260001", "20250002"):
    if not any(c[0] == std and c[1] is FULL for c in CART):
        CART.append((std, FULL, sched_of(TARGET, "CART")[2] + timedelta(days=2, minutes=int(std[-2:]))))
# 남은 한 자리를 2학년 둘 이상이 노리게 한다(T1 동시 신청 실험·시연)
racers = [c[0] for c in CART if c[1] is ONE_LEFT and yr_level(c[0], TARGET) == 2]
for std in sorted(s for s in ST if yr_level(s, TARGET) == 2 and is_attending(s, TARGET) and s not in GRADUATED):
    if len(racers) >= 2:
        break
    if std in racers or ONE_LEFT["cd"] in {cd for _, cd, _ in history(std, TARGET)}:
        continue
    if all(passed(std, p, g, TARGET) for p, g in PREQ.get(ONE_LEFT["cd"], [])):
        CART.append((std, ONE_LEFT, sched_of(TARGET, "CART")[2] + timedelta(days=2, hours=1, minutes=int(std[-2:]))))
        racers.append(std)
assert len(racers) >= 2, racers

for std, sem, cd in TA:
    find_lec(sem, cd)["keep"] = True


# T1(2학년 신청일) 시연 사례: 장바구니에 하나를 더 담아, 신청할 때 한 가지 규칙으로만 막히거나(또는 허용되게) 한다
# 다른 사례로 쓰는 강의. 한 강의가 여러 사례를 겸하지 않게 새 사례 후보에서 뺀다
SPECIAL = {id(FULL), id(ONE_LEFT)} | {id(find_lec(TARGET, cd)) for cd in CASE_COURSES if cd != POPULAR}
PLAN = {std: [c[0] for c in chosen] for std, lvl, chosen in PLANNED if lvl == 2}
TARGET_LECS = sorted([l for l in LECTURES if l["sem"] == TARGET], key=lambda l: (l["cd"], l["sect"]))


def extra_ok(std, lec, need_prereq=True):
    """std 가 계획(장바구니)에 더 담을 수 있고, 정원·폐강·강의실·중복 과목으로는 막히지 않는 강의인가."""
    mine = PLAN[std]
    if id(lec) in SPECIAL or lec["stat"] != "OPEN" or lec["cd"] == CHAPEL or cnt(lec) >= lec["cap"]:
        return False
    if lec["cls"] == "OFFLINE" and lec["room"] is None:
        return False
    if lec["cd"] in {cd for _, cd, _ in history(std, TARGET)} | {l["cd"] for l in mine}:
        return False
    if any(c[0] == std and c[1] is lec for c in CART):
        return False
    ok = all(passed(std, p, g, TARGET) for p, g in PREQ.get(lec["cd"], []))
    return ok if need_prereq else not ok


def pick(std, cond):
    return next((l for l in TARGET_LECS if cond(l)), None)


RICH = "20260001"   # 2026-2 평점 4.0 → 최대 21. 계획 18학점 + 3학점을 더 담아 21학점까지 신청된다
assert MAXC[(RICH, TARGET)] == 21 and RICH in PLAN, (MAXC.get((RICH, TARGET)), RICH in PLAN)
RICH_EXTRA = pick(RICH, lambda l: extra_ok(RICH, l) and not any(overlap(l["slot"], m["slot"]) for m in PLAN[RICH])
                  and credit_excl_chapel(PLAN[RICH] + [l]) <= 21)
OVERMAX, OVERLAP, PREREQ = None, None, None
for std in [s for s in sorted(PLAN) if s != RICH]:   # 시간 겹침으로만 막힘(최대학점에 여유가 있어야 한다)
    mx = MAXC[(std, TARGET)]
    l = pick(std, lambda l: extra_ok(std, l) and credit_excl_chapel(PLAN[std] + [l]) <= mx
             and [m for m in PLAN[std] if overlap(l["slot"], m["slot"])]
             and all(id(m) not in SPECIAL and m["cd"] != CHAPEL for m in PLAN[std] if overlap(l["slot"], m["slot"])))
    if l:
        OVERLAP = (std, l, next(m for m in PLAN[std] if overlap(l["slot"], m["slot"])))
        break
for std in [s for s in sorted(PLAN) if s not in (RICH, OVERLAP and OVERLAP[0])]:   # 최대학점 초과로만 막힘
    mx = MAXC[(std, TARGET)]
    l = pick(std, lambda l: extra_ok(std, l) and not any(overlap(l["slot"], m["slot"]) for m in PLAN[std])
             and credit_excl_chapel(PLAN[std] + [l]) > mx)
    if l:
        OVERMAX = (std, l, credit_excl_chapel(PLAN[std]))
        break
used = {RICH, OVERLAP and OVERLAP[0], OVERMAX and OVERMAX[0]}
for std in sorted(PLAN, key=lambda s: (s in used, s)):   # 선수과목 미이수로 막힘
    l = pick(std, lambda l: extra_ok(std, l, need_prereq=False) and PREQ.get(l["cd"])
             and not any(overlap(l["slot"], m["slot"]) for m in PLAN[std]))
    if l:
        PREREQ = (std, l)
        break
assert RICH_EXTRA and OVERMAX and OVERLAP and PREREQ, (RICH_EXTRA, OVERMAX, OVERLAP, PREREQ)
t1_cart = sched_of(TARGET, "CART")[2] + timedelta(days=3)
CART.append((RICH, RICH_EXTRA, t1_cart))
CART.append((OVERMAX[0], OVERMAX[1], t1_cart + timedelta(minutes=10)))
CART.append((OVERLAP[0], OVERLAP[1], t1_cart + timedelta(minutes=20)))
CART.append((PREREQ[0], PREREQ[1], t1_cart + timedelta(minutes=30)))

# ---------------------------------------------------------------- 검증(생성기 안에서)
assert "20230001" in GRADUATED and "20230002" not in GRADUATED, GRADUATED
assert grad_credits("20230002", TARGET) < GRAD_CREDITS                  # 졸업 유예 사유는 학점 부족
assert any(e["lec"]["sem"] == 8 and e["lec"]["cd"] == CHAPEL for e in ENR)  # 계절학기 채플
assert sem_gpa("20240001", CUR) == 4.0
g2 = sem_gpa("20240002", CUR)
assert 3.95 <= g2 < 4.0, g2
assert MAXC[("20240001", TARGET)] == 21 and MAXC[("20240002", TARGET)] == 18
assert MAXC[("20250002", TARGET)] == 21     # 복학생: 2학년 1학기라 1학년(2025) 평점 4.3
assert gpa_over("20250002", [5, 6]) == 4.3
assert MAXC[("20250003", TARGET)] == 18     # 직전 학기 P/F만
assert ("20230001", TARGET) not in MAXC and ("20230003", TARGET) not in MAXC
assert MAXC[("20230003", 10)] == 3 and MAXC[("20260001", 10)] == 6
assert any(e["std"] == "20230003" and e["lec"]["sem"] == 10 for e in ENR)   # 휴학생 계절 수강

# 빈 과거 강의는 지우고 강의번호를 다시 매긴다
used = {id(e["lec"]) for e in ENR}
LECTURES = [l for l in LECTURES if l["sem"] == TARGET or id(l) in used or l["keep"]]
LECTURES.sort(key=lambda l: (order_key(l["sem"]), l["cd"], l["sect"]))
for i, l in enumerate(LECTURES, 1):
    l["no"] = i

# 최종 학년·학적상태
FINAL = {}
for std, s in ST.items():
    yr = yr_level(std, TARGET)
    stat = "GRADUATED" if std in GRADUATED else ("ON_LEAVE" if std in STILL_ON_LEAVE else "ATTENDING")
    FINAL[std] = (yr, stat)


# ---------------------------------------------------------------- SQL 출력
def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, datetime):
        return f"'{v:%Y-%m-%d %H:%M:%S}'"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def hm(m):
    return f"{m // 60:02d}:{m % 60:02d}"


def insert(table, cols, rows, chunk=50):
    out = []
    for i in range(0, len(rows), chunk):
        part = rows[i:i + chunk]
        vals = ",\n  ".join("(" + ", ".join(q(v) for v in r) + ")" for r in part)
        out.append(f"INSERT INTO {table} ({', '.join(cols)}) VALUES\n  {vals};")
    return "\n".join(out)


# ---------------------------------------------------------------- 시점과 단계
# T0 기본 시드: 2026-2 성적 입력 기간. 2026-2는 대부분 제출, 시연 강의는 입력중(20260001 빈칸), 1개는 미입력
# s1 성적 마감(12-23 ~ 01-15): 2026-2 확정, 진급·졸업·복학, 2027 신입생 계정, 2027-1 최대학점
# s2 신청일까지(01-18 ~ 02-11 10:05): 겨울학기 성적 확정, 장바구니, 4·3학년 신청(02-09·10)
# s3 정정까지(02-11 ~ 03-04 11:00): 2·1학년 신청(02-11·12), 현재학기 2027-1로 전환(03-02)
T0 = SNAPSHOT
T1 = datetime(2027, 2, 11, 10, 5)
T2 = datetime(2027, 3, 4, 11, 0)
S1_CNFM = datetime(2026, 12, 28, 10, 0)
S2_CNFM = datetime(2027, 1, 25, 10, 0)

DEMO_LEC = find_lec(CUR, POPULAR)          # 성적 입력 시연 강의(입력중, 20260001 빈칸)
DEMO_PROF = DEMO_LEC["prof"]
PINNED = {RICH}   # 시연 학생의 성적은 시연 강의 하나만 비운다
NOTENT = sorted([l for l in LECTURES if l["sem"] == CUR and l is not DEMO_LEC and COURSES[l["cd"]]["eval"] == "GRADE"
                 and l["wt"] and l["cd"] != "1020004"
                 and 2 <= sum(1 for e in ENR if e["lec"] is l and e["stat"] == "ENROLLED") <= 9
                 and not any(e["lec"] is l and e["std"] in PINNED for e in ENR)],
                key=lambda l: (l["prof"] != DEMO_PROF, l["no"]))[0]   # 미입력 강의(관리자 화면의 미입력 확인)
ENR.sort(key=lambda e: (order_key(e["lec"]["sem"]), e["lec"]["no"], e["std"]))
BASE_ENR = [e for e in ENR if e["lec"]["sem"] <= 10]
S2_ENR = [e for e in ENR if e["lec"]["sem"] == TARGET]       # 4·3학년(02-09·10) + 신청 후 취소 1건

# s3: 2·1학년 신청(장바구니 순서, 정원 안에서)
S3_ENR, extra = [], {}
for std, lvl, chosen in sorted(PLANNED, key=lambda x: (-x[1], x[0])):
    for i, (lec, _, rt) in enumerate(chosen):
        if lec["stat"] != "OPEN" or cnt(lec) + extra.get(id(lec), 0) >= lec["cap"]:
            continue
        extra[id(lec)] = extra.get(id(lec), 0) + 1
        S3_ENR.append(dict(std=std, lec=lec, retake=rt, enrl_dtm=enroll_time(std, TARGET, i)))


def t0_gstat(l):
    if l["sem"] < CUR:
        return "CONFIRMED"
    if l["sem"] == CUR:
        return "NOT_ENTERED" if l is NOTENT else ("ENTERING" if l is DEMO_LEC else "SUBMITTED")
    return "NOT_ENTERED"


def grade_row(e, blank=False):
    g = e["grade"]
    if blank:
        return (e["std"], e["lec"]["no"], None, None, None, None, None, None)
    return (e["std"], e["lec"]["no"], g["mid"], g["fin"], g["hw"], g["att"], g["tot"], g["ltr"])


def yr_stat_t0(std):
    on_leave = CUR in ST[std]["leave"] or std in STILL_ON_LEAVE
    return yr_level(std, CUR), "ON_LEAVE" if on_leave else "ATTENDING"


T0_STUDENTS = [s_ for s_ in ST if ST[s_]["entry"] <= 2026]
NEW_STUDENTS = [s_ for s_ in ST if ST[s_]["entry"] == 2027]
GCOLS = ["std_no", "lec_no", "mid_scr", "fin_scr", "hw_scr", "att_scr", "tot_scr", "ltr_grd"]

# ---------------------------------------------------------------- 기본 시드(T0)
TABLES = ["grade", "enrollment", "course_cart", "teaching_assistant_assignment", "lecture_time_slot", "lecture",
          "semester_maximum_credit", "academic_schedule", "prerequisite_course", "course", "classroom", "semester",
          "administrator", "professor", "student", "department", "user_account"]
sql = ["-- 개발·테스트·시연용 시드. tools/seed/build_seed.py 로 생성한다. 직접 고치지 말고 생성기를 고친다.",
       f"-- T0 {T0:%Y-%m-%d %H:%M}: 2026-2 성적 입력 기간. 2027-1 강의는 개설됐고 장바구니·신청·최대학점은 아직 없다.",
       "-- 모든 계정 비밀번호: test1234! (BCrypt)",
       "-- Flyway 반복 마이그레이션이다. 이 파일이 바뀌면 다음 서버 시작 때 모든 테이블을 비우고 다시 넣는다.",
       "SET NAMES utf8mb4;", "SET FOREIGN_KEY_CHECKS = 0;"]
sql += [f"DELETE FROM {t};" for t in TABLES]
sql += ["SET FOREIGN_KEY_CHECKS = 1;", ""]


def acct(s_):
    return (s_, PWD_HASH, ST[s_]["nm"], f"{s_}@example.ac.kr", "STUDENT", "ACTIVE")


accts = [(ADMIN, PWD_HASH, "관리자", "admin@example.ac.kr", "ADMIN", "ACTIVE")]
accts += [(p, PWD_HASH, nm, f"prof{p}@example.ac.kr", "PROFESSOR", st) for p, nm, _, _, st, _ in PROFS]
accts += [acct(s_) for s_ in T0_STUDENTS]
ACOLS = ["usr_no", "pwd", "usr_nm", "email", "role", "acct_stat"]
sql.append(insert("user_account", ACOLS, accts))
sql.append(insert("department", ["dept_no", "dept_nm"], DEPTS))
sql.append(insert("student", ["std_no", "dept_no", "yr_lvl", "std_stat"],
                  [(s_, ST[s_]["dept"], *yr_stat_t0(s_)) for s_ in T0_STUDENTS]))
sql.append(insert("professor", ["prof_no", "dept_no", "office"], [(p, d, o) for p, _, d, o, _, _ in PROFS]))
sql.append(insert("administrator", ["emp_no"], [(ADMIN,)]))
sql.append(insert("semester", ["sem_no", "yr", "sem_type", "cur_sem_yn"],
                  [(s_, y, t, "Y" if s_ == CUR else "N") for s_, y, t in SEMS]))
sql.append(insert("classroom", ["room_cd", "room_nm"], ROOMS))
sql.append(insert("course", ["crs_cd", "dept_no", "crs_nm", "crdt", "cmpl_type", "eval_type", "rcmd_yr_lvl"],
                  [(c["cd"], c["dept"], c["nm"], c["crdt"], c["cmpl"], c["eval"], c["rcmd"]) for c in COURSES.values()]))
sql.append(insert("prerequisite_course", ["crs_cd", "prereq_crs_cd", "min_req_grd"], PREREQS))
srows, n = [], 0
SCHD_NO = {}
for s_, _, _ in SEMS:
    for typ, lvl, a, b in SCHED[s_]:
        n += 1
        srows.append((n, s_, typ, lvl, a, b))
        SCHD_NO[(s_, typ, lvl)] = n
sql.append(insert("academic_schedule", ["schd_no", "sem_no", "schd_type", "tgt_yr_lvl", "start_dtm", "end_dtm"], srows))
sql.append(insert("semester_maximum_credit", ["std_no", "sem_no", "max_crdt"],
                  sorted([(s_, sem, m) for (s_, sem), m in MAXC.items() if sem <= 10], key=lambda r: (r[1], r[0]))))
base_cnt = lambda l: sum(1 for e in BASE_ENR if e["lec"] is l and e["stat"] == "ENROLLED")
lrows = []
for l in LECTURES:
    gst = t0_gstat(l)
    cdt = sched_of(l["sem"], "GRADE")[3] + timedelta(days=5) if gst == "CONFIRMED" else None
    wt = l["wt"] or (None,) * 4
    lrows.append((l["no"], l["cd"], l["sem"], l["prof"], l["sect"], l["cap"], base_cnt(l), l["stat"], l["eng"], l["exmp"],
                  f"plans/{l['sem']}/{l['cd']}-{l['sect']}.pdf", *wt, gst, ADMIN if cdt else None, cdt))
sql.append(insert("lecture", ["lec_no", "crs_cd", "sem_no", "prof_no", "sect_no", "cap", "enrl_cnt", "lec_stat",
                              "eng_lec_yn", "rel_grd_exmp_yn", "plan_file", "mid_wt", "fin_wt", "hw_wt", "att_wt",
                              "grd_stat", "cnfm_emp_no", "cnfm_dtm"], lrows))
sql.append(insert("lecture_time_slot", ["lec_no", "dow", "start_tm", "end_tm", "cls_type", "room_cd"],
                  [(l["no"], d, hm(a), hm(b), l["cls"], l["room"]) for l in LECTURES for d, a, b in l["slot"]]))
sql.append(insert("teaching_assistant_assignment", ["lec_no", "std_no"], [(find_lec(sem, cd)["no"], s_) for s_, sem, cd in TA]))
ECOLS = ["std_no", "lec_no", "enrl_stat", "retake_yn", "proc_type", "enrl_dtm", "cncl_dtm"]
erow = lambda e: (e["std"], e["lec"]["no"], e["stat"], e["retake"], e["proc"], e["enrl_dtm"], e["cncl_dtm"])
sql.append(insert("enrollment", ECOLS, [erow(e) for e in BASE_ENR]))
base_grades = []
for e in BASE_ENR:
    if not e["grade"] or e["stat"] != "ENROLLED" or e["lec"]["sem"] == 10 or e["lec"] is NOTENT:
        continue
    base_grades.append(grade_row(e, blank=e["lec"] is DEMO_LEC and e["std"] == RICH))
sql.append(insert("grade", GCOLS, base_grades))
OUT_SEED.write_text("\n\n".join(sql) + "\n", encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- 오버레이
def write_overlay(name, title, parts):
    head = [f"-- {title}. tools/seed/build_seed.py 로 생성한다.",
            "-- 앞 단계까지 적용한 뒤 한 번 적용한다. 시연 중 앱 기능으로 이미 처리한 내용(입력한 점수, 계산한 최대학점, 만든 계정)은 덮어쓰지 않는다.",
            "SET NAMES utf8mb4;", ""]
    (OUT_SCENARIO / f"{name}.sql").write_text("\n\n".join(head + [p_ for p_ in parts if p_]) + "\n",
                                                encoding="utf-8", newline="\n")


def keep_existing(stmt, key_col):
    """INSERT 를 이미 있는 행은 그대로 두는 INSERT 로 바꾼다."""
    return stmt.replace(";\nINSERT", f"\nON DUPLICATE KEY UPDATE {key_col} = {key_col};\nINSERT").rstrip(";") + \
        f"\nON DUPLICATE KEY UPDATE {key_col} = {key_col};"


rich_demo = next(e for e in ENR if e["lec"] is DEMO_LEC and e["std"] == RICH)
g = rich_demo["grade"]
s1 = [f"UPDATE grade SET mid_scr = {g['mid']}, fin_scr = {g['fin']}, hw_scr = {g['hw']}, att_scr = {g['att']}, "
      f"tot_scr = {g['tot']}, ltr_grd = {q(g['ltr'])} WHERE std_no = {q(RICH)} AND lec_no = {DEMO_LEC['no']} AND ltr_grd IS NULL;",
      keep_existing(insert("grade", GCOLS, [grade_row(e) for e in ENR if e["lec"] is NOTENT and e["stat"] == "ENROLLED"]),
                    "ltr_grd"),
      f"UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = {q(ADMIN)}, cnfm_dtm = {q(S1_CNFM)} "
      f"WHERE sem_no = {CUR} AND grd_stat <> 'CONFIRMED';"]
for s_ in T0_STUDENTS:
    if yr_stat_t0(s_) != FINAL[s_]:
        s1.append(f"UPDATE student SET yr_lvl = {FINAL[s_][0]}, std_stat = {q(FINAL[s_][1])} WHERE std_no = {q(s_)};")
s1.append(keep_existing(insert("user_account", ACOLS, [acct(s_) for s_ in NEW_STUDENTS]), "usr_no"))
s1.append(keep_existing(insert("student", ["std_no", "dept_no", "yr_lvl", "std_stat"],
                               [(s_, ST[s_]["dept"], *FINAL[s_]) for s_ in NEW_STUDENTS]), "std_no"))
s1.append(keep_existing(insert("semester_maximum_credit", ["std_no", "sem_no", "max_crdt"],
                               sorted([(s_, sem, m) for (s_, sem), m in MAXC.items() if sem == TARGET])), "max_crdt"))
write_overlay("s1_grade_close", "s1 성적 마감(2026-12-23 ~ 2027-01-15): 2026-2 확정, 진급·졸업·복학, 2027 신입생 계정, 2027-1 최대학점", s1)

s2 = [insert("grade", GCOLS, [grade_row(e) for e in ENR if e["lec"]["sem"] == 10 and e["stat"] == "ENROLLED"]),
      f"UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = {q(ADMIN)}, cnfm_dtm = {q(S2_CNFM)} WHERE sem_no = 10;",
      insert("course_cart", ["std_no", "lec_no", "reg_dtm"], sorted({(s_, l["no"], d) for s_, l, d in CART})),
      insert("enrollment", ECOLS, [erow(e) for e in S2_ENR])]
for l in sorted({id(e["lec"]): e["lec"] for e in S2_ENR}.values(), key=lambda l: l["no"]):
    s2.append(f"UPDATE lecture SET enrl_cnt = {cnt(l)} WHERE lec_no = {l['no']};")
write_overlay("s2_enroll_day", f"s2 신청일까지(2027-01-18 ~ {T1:%m-%d %H:%M}): 겨울학기 성적 확정, 장바구니, 4·3학년 수강신청", s2)

s3 = [insert("enrollment", ["std_no", "lec_no", "enrl_stat", "retake_yn", "proc_type", "enrl_dtm"],
             [(e["std"], e["lec"]["no"], "ENROLLED", e["retake"], "STUDENT", e["enrl_dtm"])
              for e in sorted(S3_ENR, key=lambda e: (e["lec"]["no"], e["std"]))])]
for l in sorted({id(e["lec"]): e["lec"] for e in S3_ENR}.values(), key=lambda l: l["no"]):
    s3.append(f"UPDATE lecture SET enrl_cnt = {cnt(l) + extra[id(l)]} WHERE lec_no = {l['no']};")
s3 += [f"UPDATE semester SET cur_sem_yn = 'N' WHERE sem_no = {CUR};",
       f"UPDATE semester SET cur_sem_yn = 'Y' WHERE sem_no = {TARGET};"]
write_overlay("s3_adjust", f"s3 정정 기간까지(2027-02-11 ~ {T2:%m-%d %H:%M}): 2·1학년 수강신청, 현재학기 2027-1로 전환(03-02)", s3)


# ---------------------------------------------------------------- 사례(안내·상수용)
def lec_label(l):
    return f"{l['no']}번 {COURSES[l['cd']]['nm']}({l['cd']}-{l['sect']})"


def gpa_txt(std, sem):
    g_ = sem_gpa(std, sem)
    return "없음" if g_ is None else f"{g_:.4f}"


tl = lambda cd, sect=1: find_lec(TARGET, cd, sect)
ONLINE, NO_ROOM, CLOSED = tl("1030011"), tl("1030014"), tl("1030016")
ENG_MAJOR, ENG_GENERAL, REL_EXEMPT = tl("1010015"), tl("1030013"), tl("1020015")
NO_WEIGHTS = [tl(c) for c in ("1010019", "1020019", "1030009")]
TWO_SECTIONS = tl("1030007", 2)
RETAKE_LEC = tl("1010014")
SUMMER_CHAPEL = find_lec(8, CHAPEL)
SUMMER_CHAPEL_STD = sorted(e["std"] for e in ENR if e["lec"] is SUMMER_CHAPEL)
TA_LEC = find_lec(TARGET, "1020001")
DEMO_ROWS = [e for e in ENR if e["lec"] is DEMO_LEC and e["stat"] == "ENROLLED"]
A_LIMIT = math.floor(len(DEMO_ROWS) * 0.4)
A_NOW = sum(1 for e in DEMO_ROWS if e["std"] != RICH and e["grade"]["ltr"] in ("A+", "A0", "A-"))
RACERS = sorted({s_ for s_, l_, _ in CART if l_ is ONE_LEFT and s_ in PLAN})   # T1에 신청하는 2학년
ONLINE_STD = sorted({e["std"] for e in S3_ENR if e["lec"] is ONLINE})
assert ONLINE_STD and A_NOW < A_LIMIT, (ONLINE_STD, A_NOW, A_LIMIT)
assert sem_gpa(RICH, CUR) == 4.0


# ---------------------------------------------------------------- 테스트 상수
def jconst(name, value, doc):
    v = f'"{value}"' if isinstance(value, str) else str(value)
    t = "String" if isinstance(value, str) else "int"
    return [f"        /** {doc} */", f"        public static final {t} {name} = {v};", ""]


def jclass(name, doc, items):
    out = [f"    /** {doc} */", f"    public static final class {name} {{", "", f"        private {name}() {{}}", ""]
    for it in items:
        out += jconst(*it)
    out[-1:] = ["    }", ""]
    return out


java = ["package com.crs.backend.support;", "",
        "/**", " * 시드 데이터의 사례를 이름으로 가리키는 상수. tools/seed/build_seed.py 가 만든다. 직접 고치지 않는다.", " *",
        " * <p>설명은 docs/seed-data.md 에 있다.", " */",
        "public final class SeedFixtures {", "", "    private SeedFixtures() {}", "",
        "    /** 모든 계정의 비밀번호. */", '    public static final String PASSWORD = "test1234!";', ""]
java += jclass("Semester", "학기번호.", [
    ("Y2026_SECOND", CUR, "2026-2. T0의 성적 입력 학기, T1까지 현재 학기"),
    ("Y2026_WINTER", 10, "2026 겨울학기"),
    ("Y2027_FIRST", TARGET, "2027-1. 수강신청 학기, s3 이후 현재 학기")])
java += jclass("Account", "학생이 아닌 계정.", [
    ("ADMIN", ADMIN, "관리자"),
    ("DEMO_PROFESSOR", DEMO_PROF, "성적 입력 시연 강의의 교수"),
    ("PROFESSOR_INACTIVE", "100006", "퇴직 교수(INACTIVE). 로그인 차단")])
java += jclass("Student", "학번.", [
    ("DEMO", RICH, "시연 학생. 2026-1 전부 A-, 2026-2 시연 강의 점수만 빈칸, 나머지 A-. A-를 넣으면 1학년 평점 4.0 → 2027-1 최대학점 21"),
    ("GPA_EXACTLY_4", "20240001", "2026-2 평점 4.0 → 최대학점 21"),
    ("GPA_JUST_UNDER_4", "20240002", "2026-2 평점 4.0 미만 → 최대학점 18. 신청 취소 이력"),
    ("PASS_FAIL_ONLY", "20250003", "2026-2 P/F 과목만 → 평점 없음 → 최대학점 18"),
    ("GRADUATING", "20230001", "s1 에서 졸업 처리(정규 8학기, 채플 뺀 130학점 이상, 채플 P 6학기 이상)"),
    ("ON_LEAVE", "20230003", "휴학생. 2027-1 최대학점 없음"),
    ("RETURNING", "20250002", "T0 휴학 → s1 에서 복학. 2학년 1학기라 1학년(2025) 평점 4.3 → 최대학점 21"),
    ("FRESHMAN", "20270001", "2027 신입생. s1 에서 계정 생성"),
    ("RETAKE", "20240003", "재수강 이력(F → A- 상한). 2027-1 데이터베이스 재수강 신청"),
    ("PREREQ_NOT_MET", "20240004", "자료구조 D+ → 데이터베이스(최소 C0) 신청 불가(T2)"),
    ("TEACHING_ASSISTANT", "20240005", f"조교. 2027-1 {TA_LEC['no']}번 강의"),
    ("ADMIN_ADDED", "20250001", "2026-2 관리자 수동 추가 1건. 3학년이라 T1 에 신청 불가"),
    ("SOPHOMORE_FULL_IN_CART", "20260001", "정원 마감 강의가 장바구니에 있음(T1)"),
    ("OVER_MAX_CREDIT", OVERMAX[0], "장바구니 강의를 모두 신청하면 최대학점 초과(T1)"),
    ("TIME_CONFLICT", OVERLAP[0], "장바구니 강의 두 개의 시간이 겹침(T1)"),
    ("PREREQ_NOT_MET_SOPHOMORE", PREREQ[0], "장바구니 강의의 선수과목 미이수(T1)")])
java += jclass("Lecture", "강의번호.", [
    ("DEMO_GRADE", DEMO_LEC["no"], f"2026-2 성적 입력 시연(입력중, 수강생 {len(DEMO_ROWS)}명, A 한도 {A_LIMIT}명)"),
    ("NOT_ENTERED", NOTENT["no"], "2026-2 성적 미입력"),
    ("FULL", FULL["no"], "2027-1 정원 마감(s2 이후)"),
    ("ONE_SEAT_LEFT", ONE_LEFT["no"], "2027-1 한 자리 남음(s2 이후)"),
    ("DEMO_EXTRA", RICH_EXTRA["no"], "DEMO 학생이 21학점을 채우려고 더 담은 강의"),
    ("OVER_MAX_CREDIT_TARGET", OVERMAX[1]["no"], "OVER_MAX_CREDIT 학생이 더 담은 강의"),
    ("TIME_CONFLICT_TARGET", OVERLAP[1]["no"], "TIME_CONFLICT 학생이 더 담은, 시간이 겹치는 강의"),
    ("PREREQ_TARGET", PREREQ[1]["no"], "PREREQ_NOT_MET_SOPHOMORE 학생이 담은, 선수과목이 있는 강의"),
    ("ONLINE", ONLINE["no"], "2027-1 온라인(강의실 없음)"),
    ("NO_ROOM", NO_ROOM["no"], "2027-1 대면인데 강의실 미배정"),
    ("CLOSED", CLOSED["no"], "2027-1 폐강"),
    ("TWO_SECTIONS", TWO_SECTIONS["no"], "같은 과목의 2분반"),
    ("ENGLISH_MAJOR", ENG_MAJOR["no"], "전공 영어강의 → 상대평가 제외"),
    ("ENGLISH_GENERAL", ENG_GENERAL["no"], "교양 영어강의 → 상대평가 대상"),
    ("RELATIVE_EXEMPT", REL_EXEMPT["no"], "상대평가 제외 인정"),
    ("NO_WEIGHTS", NO_WEIGHTS[0]["no"], "반영비율 미입력"),
    ("RETAKE_TARGET", RETAKE_LEC["no"], "RETAKE 학생이 재수강 신청한 강의"),
    ("TA_TARGET", TA_LEC["no"], "TEACHING_ASSISTANT 학생이 조교인 강의")])
java[-1:] = ["}"]
OUT_FIXTURES.write_text("\n".join(java) + "\n", encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- 안내 문서
def row(*c):
    return "| " + " | ".join(str(x) for x in c) + " |"


crd = lambda l: f"{COURSES[l['cd']]['crdt']:g}"
plan_cr = lambda s_: f"{credit_excl_chapel(PLAN[s_]):g}"
gd = ["# 시드 데이터 안내", "",
      "개발·테스트·시연 모두 이 데이터를 쓴다. `tools/seed/build_seed.py` 가 만든다(직접 고치지 않는다). 모든 계정의 비밀번호는 `test1234!` 다.", "",
      "## 시연 흐름", "",
      "2026-2 성적을 입력·확정하면 그 성적으로 2027-1 최대학점이 정해지고, 그 학점으로 수강신청이 막히거나 허용된다.", "",
      row("순서", "시각", "누가", "무엇을", "데이터"), row(*["---"] * 5),
      row("① 성적 입력", f"T0 {T0:%Y-%m-%d %H:%M}", f"교수 {DEMO_PROF}", f"{lec_label(DEMO_LEC)}에서 {RICH} 점수 입력(90~93점 → A-) 후 제출", f"A {A_NOW}명 + 1 = 한도 {A_LIMIT}명. A를 더 주면 제출 불가"),
      row("② 성적 확정", "T0", f"관리자 {ADMIN}", f"미입력 강의 {lec_label(NOTENT)} 확인, 2026-2 확정", "기본 시드"),
      row("③ 학기 마감", "(T0 → T1)", f"관리자 {ADMIN}", "진급·졸업·복학, 2027-1 최대학점 산정", "앱 기능. 없으면 `s1_grade_close`"),
      row("④ 시계 넘김", f"T1 {T1:%Y-%m-%d %H:%M}", "시연 API", "다른 학생들의 장바구니와 4·3학년 신청을 채움", "`s2_enroll_day`"),
      row("⑤ 수강신청", "T1", f"학생 {RICH}", f"장바구니 {plan_cr(RICH)}학점 + {RICH_EXTRA['no']}번 {crd(RICH_EXTRA)}학점 = 21학점 신청 성공. {FULL['no']}번은 정원 마감으로 차단", "s2 이후"),
      row("⑥ 차단 사례", "T1", "학생 여럿", "아래 모듈 B 표의 T1 사례", "s2 이후"),
      row("⑦ 데이터 제어", "T1", "root·app·readonly", "`demo/` SQL", "s2 이후"), "",
      "①에서 89점 이하(B+)를 넣으면 평점이 4.0 아래가 되어 ③의 최대학점이 18이 되고, ⑤에서 21학점 신청이 막힌다. `s1_grade_close` 는 최대학점을 미리 계산해 둔 값(21)으로 넣으므로 이 대비를 보이려면 ③을 앱 기능으로 해야 한다.", "",
      "## 시점과 오버레이", "",
      row("시점", "시각", "열린 기간", "적용할 오버레이"), row(*["---"] * 4),
      row("T0", f"{T0:%Y-%m-%d %H:%M}", "2026-2 성적 입력, 2026 겨울 정정", "없음(기본 시드)"),
      row("T1", f"{T1:%Y-%m-%d %H:%M}", "2027-1 2학년 수강신청", "`s1_grade_close`, `s2_enroll_day`"),
      row("T2", f"{T2:%Y-%m-%d %H:%M}", "2027-1 정정(전 학년)", "`s1_grade_close`, `s2_enroll_day`, `s3_adjust`"), "",
      "오버레이는 `src/main/resources/db/scenario/` 에 있고, 앞 단계부터 순서대로 한 번씩 적용한다. 되돌리려면 `docker compose down -v` 후 다시 올린다.", "",
      "### 시점 바꾸기", "",
      "- **시연·로컬(권장)**: 시연 API 로 오버레이를 넣고 시계를 옮긴다. 프로필을 지정하지 않으면 demo 라 바로 쓸 수 있다. 같은 오버레이를 두 번 넣으면 409 로 거절하고 데이터는 그대로다.", "",
      "  ```bash",
      "  curl -X POST localhost:8080/api/dev/scenarios/s1_grade_close",
      "  curl -X POST localhost:8080/api/dev/scenarios/s2_enroll_day",
      f"  curl -X PUT 'localhost:8080/api/dev/clock?at={T1:%Y-%m-%dT%H:%M:%S}'",
      "  curl localhost:8080/api/dev/clock", "  ```", "",
      "- **서버 시작 시각**: `.env` 의 `CLOCK_FIXED_AT` (기본 T0). 비우면 실제 시각.",
      "- **테스트**: `@TestPropertySource(properties = \"crs.clock.fixed-at=\" + SeedTime.T1)` 로 시각을 고르고, `SeedTime.atT1(dataSource)` 로 오버레이를 테스트 트랜잭션 안에서 적용한다. 사례는 `SeedFixtures` 상수로 가리킨다.", "",
      "## 로그인 계정", "",
      row("역할", "ID", "쓰임"), row(*["---"] * 3),
      row("관리자", ADMIN, "학기·일정, 강의 개설, 사용자, 신청 현황, 성적 확정"),
      row("교수", DEMO_PROF, f"성적 입력 시연 {lec_label(DEMO_LEC)}"),
      row("교수(퇴직)", "100006", "비활성 계정 로그인 차단"),
      row("학생", RICH, "시연 학생(성적 입력 → 21학점 신청)"),
      row("학생 겸 조교", "20240005", f"2027-1 {lec_label(TA_LEC)} 조교"), "",
      "## 모듈 A: 학사 기준·조회", "",
      row("기능", "시점", "대상", "확인할 것"), row(*["---"] * 4),
      row("성적 조회(GRD-03)", "T0", "20240001", "2026-2 성적은 제출됐지만 확정 전 → 학생 화면 표시 여부"),
      row("성적 조회", "T1", "20240001 / 20240002", f"2026-2 평점 {gpa_txt('20240001', CUR)} / {gpa_txt('20240002', CUR)}"),
      row("성적 조회", "T1", "20250003", "2026-2 P/F 과목만 → 평점 없음"),
      row("성적 조회", "T1", "20240003", "이산수학 F → 재수강 A-(총점 98이어도 상한)"),
      row("성적 조회", "T1", "20230003", "휴학 중 2026 겨울 수강. 계절 성적은 정규학기 평점에서 빠지고 이수 내역에는 들어감"),
      row("학기 마감(진급·졸업·복학)", "T0 → s1", "20230001 / 20250002 / 2026 입학생", "졸업 / 복학 / 1→2학년"),
      row("졸업 판정", "T0 → s1", "20230001 / 20230002",
          " / ".join(f"정규 {len(attended_regular(s_, TARGET))}학기·채플 뺀 {grad_credits(s_, TARGET):g}학점·채플 P {chapel_sems(s_, TARGET)}학기"
                     for s_ in ("20230001", "20230002")) + " → 졸업 / 학점 부족으로 재학 유지"),
      row("채플(계절학기)", "T0", f"2026 여름 {lec_label(SUMMER_CHAPEL)}: {', '.join(SUMMER_CHAPEL_STD)}",
          "계절학기에 받은 채플 P도 졸업 요건(채플 P 6학기)에 셈. 채플은 학기마다 신청하고 재수강이 아님"),
      row("최대학점 산정", "T0 → s1", f"{RICH} / 20240001 / 20240002", "21 / 21 / 18 (4.0 경계)"),
      row("최대학점 산정", "T0 → s1", "20250002", "복학생: 2학년 1학기라 1학년(2025) 두 학기 평점 4.3 기준 21"),
      row("최대학점 산정", "T0 → s1", "20250003", "직전 학기 평점 없음 → 18"),
      row("최대학점 산정", "T0 → s1", "20230001 / 20230003", "졸업생·휴학생은 2027-1 정규학기 최대학점 없음"),
      row("강의 검색(REG-01)", "T1", lec_label(FULL), "잔여 0"),
      row("강의 검색", "T1", lec_label(ONE_LEFT), "잔여 1"),
      row("강의 검색", "T1", f"{lec_label(tl('1030007'))}, {TWO_SECTIONS['no']}번", "같은 과목 2분반"),
      row("강의 검색", "T1", lec_label(CLOSED), "폐강"),
      row("시간표(REG-04)", "T2", ", ".join(ONLINE_STD[:2]), f"온라인 강의 {lec_label(ONLINE)} 포함(강의실 없음)"),
      row("학기·일정(ACA-01)", "T0", "2027-1 일정", "모두 시작 전 → 삭제 가능. T1에는 장바구니·4·3학년 신청일이 시작돼 삭제 불가"),
      row("대시보드(ACA-04)", "T1", "2027-1", f"개설 강의 {sum(1 for l in LECTURES if l['sem'] == TARGET and l['stat'] == 'OPEN')}개, 폐강 1개, 정원 마감 1개, 강의실 미배정 1개({NO_ROOM['no']}번)"),
      row("품질 검사", "T2", lec_label(NO_ROOM), "정정 기간이 시작됐는데 대면 강의에 강의실 미배정(미배정은 정정 시작 전까지만 허용)"), "",
      "## 모듈 B: 계정·수강신청", "",
      row("기능", "시점", "대상", "확인할 것"), row(*["---"] * 4),
      row("로그인(USR-01)", "아무 때", f"{ADMIN} / {DEMO_PROF} / {RICH}", "역할별 로그인"),
      row("로그인", "아무 때", "100006", "비활성 계정 차단"),
      row("계정 관리(USR-02)", "T0", "20250002", "휴학 → 복학 처리"),
      row("강의 개설(ACA-03)", "T0", lec_label(NO_ROOM), "강의실 미배정 대면 강의. 강의실은 정정 시작 전까지 배정, 시간은 수강신청 시작 전까지 변경"),
      row("강의 개설", "T0", lec_label(CLOSED), "폐강 강의"),
      row("수강신청(REG-03)", "T1", f"{RICH} → 장바구니 전체", f"{plan_cr(RICH)} + {crd(RICH_EXTRA)} = 21학점, 최대 21 → 성공(단 {FULL['no']}번은 정원 마감)"),
      row("수강신청", "T1", f"{', '.join(RACERS)} → {ONE_LEFT['no']}번", "남은 한 자리(동시 신청 실험)"),
      row("수강신청", "T1", f"{OVERMAX[0]} → 장바구니 전체", f"{OVERMAX[2]:g} + {crd(OVERMAX[1])}학점 > 최대 {MAXC[(OVERMAX[0], TARGET)]} → {OVERMAX[1]['no']}번 차단"),
      row("수강신청", "T1", f"{OVERLAP[0]} → {OVERLAP[1]['no']}번", f"장바구니의 {OVERLAP[2]['no']}번과 시간 겹침 → 둘 중 나중 것 차단"),
      row("수강신청", "T1", f"{PREREQ[0]} → {PREREQ[1]['no']}번", "선수과목 미이수 → 차단"),
      row("수강신청", "T1", "20250001(3학년)", "자기 학년 신청일이 아님 → 차단"),
      row("수강신청", "T1", "20230001", "졸업생 → 차단"),
      row("수강신청", "T2", f"20240004 → {RETAKE_LEC['no']}번", "선수과목(자료구조 C0 이상) 미이수 → 차단"),
      row("수강신청", "T2", f"20240003 → {ENG_MAJOR['no']}번", "컴퓨터네트워크 C+ → 재수강으로 신청(retake_yn Y)"),
      row("수강신청", "T2", "20230003", "휴학생 → 차단"),
      row("장바구니(REG-02)", "T0 / T1", "아무 학생", "T0은 기간 밖이라 담기 차단, T1은 담기·삭제 가능"),
      row("신청 현황(REG-05)", "T1", f"관리자 {ADMIN}", "장바구니 확인 없이 수동 추가. 2026-2 수동 추가 이력은 20250001"), "",
      "## 모듈 C: 강의 운영·성적", "",
      row("기능", "시점", "대상", "확인할 것"), row(*["---"] * 4),
      row("성적 입력(GRD-01)", "T0", lec_label(DEMO_LEC), f"입력중, {RICH} 빈칸. 수강생 {len(DEMO_ROWS)}명 → 상대평가(A {A_LIMIT}명, A+B {math.floor(len(DEMO_ROWS) * 0.8)}명 한도)"),
      row("성적 입력", "T0", lec_label(DEMO_LEC), f"현재 A {A_NOW}명. A 두 명을 더 주면 제출 불가"),
      row("성적 입력", "T0", "2026-2 나머지 강의", "제출 상태. 수정하면 입력중으로"),
      row("성적 확정(GRD-02)", "T0", lec_label(NOTENT), "미입력 강의로 보임"),
      row("성적 확정", "T0", "2026-2 제출 강의", "관리자 확정 → 이후 교수 수정 불가"),
      row("과목(ACA-02)", "T0", "선수과목 6건", "최소취득등급 기본값 D-, 채플 0.5학점 P/F(학기마다 신청, 이수 학점에서 제외)"),
      row("담당 강의(LEC-01)", "T0", f"교수 {DEMO_PROF}", "2026-2·2027-1 담당 강의"),
      row("강의계획서·반영비율(LEC-03)", "T0", ", ".join(f"{l['no']}번" for l in NO_WEIGHTS), "2027-1 반영비율 미입력"),
      row("조교(LEC-04)", "T0", f"20240005 / {TA_LEC['no']}번", "조교 지정·삭제, 조교 로그인 권한"), "",
      "## 학생", "",
      row("학번", "이름", "학과", "T0 학년·학적", "s1 이후 학년·학적", "2027-1 최대학점"), row(*["---"] * 6)]
gd += [row(s_, v["nm"], dict(DEPTS)[v["dept"]],
           "-" if s_ in NEW_STUDENTS else "{} {}".format(*yr_stat_t0(s_)), "{} {}".format(*FINAL[s_]),
           MAXC.get((s_, TARGET), "-")) for s_, v in ST.items()]
gd += ["", "## 학사일정 (2027-1)", "", row("유형", "대상학년", "시작", "종료"), row(*["---"] * 4)]
gd += [row(t, lv or "전체", f"{a:%Y-%m-%d %H:%M}", f"{b:%Y-%m-%d %H:%M}") for t, lv, a, b in SCHED[TARGET]]
gd += ["", "## 건수", "", row("테이블", "기본 시드", "s1", "s2", "s3"), row(*["---"] * 5),
       row("enrollment", len(BASE_ENR), "-", len(S2_ENR), len(S3_ENR)),
       row("grade", len(base_grades), sum(1 for e in ENR if e["lec"] is NOTENT and e["stat"] == "ENROLLED"),
           sum(1 for e in ENR if e["lec"]["sem"] == 10 and e["stat"] == "ENROLLED"), "-"),
       row("course_cart", "-", "-", len({(s_, l_['no']) for s_, l_, _ in CART}), "-"),
       row("semester_maximum_credit", sum(1 for (s_, sem) in MAXC if sem <= 10), sum(1 for (s_, sem) in MAXC if sem == TARGET), "-", "-")]
OUT_GUIDE.write_text("\n".join(gd) + "\n", encoding="utf-8", newline="\n")
print(f"lectures {len(LECTURES)}, base enrollments {len(BASE_ENR)}, s2 {len(S2_ENR)}, s3 {len(S3_ENR)}")
print(f"demo lecture {DEMO_LEC['no']} prof {DEMO_PROF} rows {len(DEMO_ROWS)} A {A_NOW}/{A_LIMIT}, not entered {NOTENT['no']}")
print(f"T1 rich+{RICH_EXTRA['no']}, overmax {OVERMAX[0]}->{OVERMAX[1]['no']}, overlap {OVERLAP[0]}->{OVERLAP[1]['no']}, prereq {PREREQ[0]}->{PREREQ[1]['no']}, racers {RACERS}")
