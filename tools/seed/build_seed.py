"""개발·테스트·시연에 함께 쓰는 시드 데이터를 만든다.

사용: python -X utf8 tools/seed/build_seed.py   (표준 라이브러리만 쓴다. 난수 시드가 고정이라 매번 같은 결과가 나온다)

만드는 파일 (모두 생성물이다. 직접 고치지 말고 이 생성기를 고친다)
- src/main/resources/db/seed/R__seed_dev.sql        기본 시드. Flyway 가 서버 시작 시 적용한다
- src/main/resources/db/scenario/t3_grade.sql       T3(2027-1 성적 입력 기간) 시점으로 옮기는 오버레이
- src/test/java/com/crs/backend/support/SeedFixtures.java   테스트에서 사례를 이름으로 가리키는 상수
- docs/seed-data.md                                 모듈별 안내(시점, 계정, 사례)

데이터가 나타내는 시점(스냅숏)은 2027-02-10 18:00이다.
- 현재 학기 2026-2(성적 확정 완료), 신청 대상 학기 2027-1
- 진급·졸업 처리와 2027-1 최대학점 산정이 끝났다
- 2027-1 수강신청은 4학년(02-09)·3학년(02-10) 날이 지났고, 2·1학년은 장바구니만 있다
졸업 판정에 4년치 이력이 필요해서 2023-1부터 만든다.
"""
from pathlib import Path
from datetime import datetime, timedelta
import itertools
import math
import random

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
OUT_SEED = REPO / "src/main/resources/db/seed/R__seed_dev.sql"
OUT_T3 = REPO / "src/main/resources/db/scenario/t3_grade.sql"
OUT_FIXTURES = REPO / "src/test/java/com/crs/backend/support/SeedFixtures.java"
OUT_GUIDE = REPO / "docs/seed-data.md"
rng = random.Random(20261118)

# 모든 mock 계정의 비밀번호는 test1234! (BCrypt cost 10, Spring BCryptPasswordEncoder와 호환되는 $2a$ 형식)
PWD_HASH = "$2a$10$mVtkuC8t.ybJShFMmg7dAePnho.f1IpD92TXrTXipF.OhtbWdF/.S"
SNAPSHOT = datetime(2027, 2, 10, 18, 0)
ADMIN = "9001"
GRAD_CREDITS = 130          # 졸업 이수 학점
GRAD_SEMESTERS = 8          # 졸업에 필요한 정규학기 수

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
# 2027-1에 처음 개설하는 교양. 학생 대부분이 먼저 담아 수강생 10명 이상(상대평가 대상) 강의가 되게 한다
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


SEASONAL_OFFER = {8: ["1030007", "1010006"], 10: ["1030008", "1030010", "1030006"]}
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
PIN_SEM = {("20240001", 9): "ALL_A-", ("20240002", 9): "JUST_UNDER_4", ("20250002", 6): "ALL_A0"}
# 계절 수강: {학기: {과목: (인원, 후보 학번 순서)}}. 이미 들은 과목이면 다음 후보로 넘어간다
SEASONAL_ENROLL = {8: {"1030007": (2, ["20260001", "20240003", "20260002", "20250001"]),
                       "1010006": (1, ["20260003", "20260001"])},
                   10: {"1030008": (3, ["20230003", "20260001", "20250001", "20260003", "20240004"]),
                        "1030010": (2, ["20230003", "20260002", "20240001", "20260004", "20250003", "20230002"]),
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


def sem_gpa(std, sem):
    rows = [(COURSES[e["lec"]["cd"]]["crdt"], PT[e["grade"]["ltr"]]) for e in ENR
            if e["std"] == std and e["lec"]["sem"] == sem and e["stat"] == "ENROLLED" and e["grade"]
            and COURSES[e["lec"]["cd"]]["eval"] == "GRADE"]
    if not rows:
        return None
    return sum(c * p for c, p in rows) / sum(c for c, _ in rows)


def max_credit_regular(std, sem):
    att = attended_regular(std, sem)
    if not att:
        return 18
    g = sem_gpa(std, att[-1])
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
    taken = {cd for _, cd, _ in history(std, sem)} | {e["lec"]["cd"] for e in ENR if e["std"] == std and e["lec"]["sem"] == sem}
    offered = {l["cd"]: l for l in LECTURES if l["sem"] == sem and l["sect"] == 1 and l["stat"] == "OPEN"}
    own = [cd for cd, c in COURSES.items() if c["dept"] == s["dept"]]
    other = [cd for cd, c in COURSES.items() if c["dept"] not in (s["dept"], 103)]
    gen_req = [cd for cd, c in COURSES.items() if c["cmpl"] == "GEN_REQ"]
    gen_el = [cd for cd, c in COURSES.items() if c["cmpl"] in ("GEN_ELEC",) and cd != POPULAR]
    rng.shuffle(gen_el)
    order = []
    if CHAPEL not in taken:
        order.append(CHAPEL)
    if sem == TARGET:
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
        if cd in taken or cd not in offered or cd in out:
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
        # 이미 들은 과목은 건너뛰고, 계절학기에는 한 학생이 한 강의만 듣는다
        for cd, (k, cands) in SEASONAL_ENROLL[sem].items():
            lec = find_lec(sem, cd)
            picked = 0
            for i, std in enumerate(cands):
                taken = {c for _, c, _ in history(std, sem)} | {e["lec"]["cd"] for e in ENR if e["std"] == std}
                busy = any(e["std"] == std and e["lec"]["sem"] == sem for e in ENR)
                if picked == k or cd in taken or busy:
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
                    lat = latest(std, sem)
                    cr = sum(COURSES[cd]["crdt"] for cd, g in lat.items() if g != "F")
                    if len(att) >= GRAD_SEMESTERS and cr >= GRAD_CREDITS:
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
            e["protect"] = e.get("protect") or forced is not None
        relative_fix(lec, rows)
        lec["gstat"] = "CONFIRMED"
        if sem == 10:
            lec["gstat"] = WINTER_STATE[lec["cd"]]
            assert rows, f"겨울 강의 수강생 없음: {lec['cd']}"
            if lec["gstat"] == "ENTERING":
                rows[-1]["grade"] = dict(mid=None, fin=None, hw=None, att=None, tot=None, ltr=None)

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


# T2(정정 기간) 차단 사례: 다른 이유로는 막히지 않고 시간 겹침·최대학점 초과로만 막히는 장바구니 강의
def enrolled_in(std, sem):
    return [e["lec"] for e in ENR if e["std"] == std and e["lec"]["sem"] == sem and e["stat"] == "ENROLLED"]


# 다른 사례로 쓰는 강의. 한 강의가 여러 사례를 겸하지 않게 새 사례 후보에서 뺀다
SPECIAL = {id(FULL), id(ONE_LEFT)} | {id(find_lec(TARGET, cd)) for cd in CASE_COURSES}


def only_blocked_by_rule(std, lec):
    mine = enrolled_in(std, TARGET)
    if id(lec) in SPECIAL:
        return False
    if lec["stat"] != "OPEN" or lec["cd"] == CHAPEL or lec is FULL or lec is ONE_LEFT or cnt(lec) >= lec["cap"]:
        return False
    if lec["cls"] == "OFFLINE" and lec["room"] is None:
        return False
    taken = {cd for _, cd, _ in history(std, TARGET)} | {l["cd"] for l in mine}
    if lec["cd"] in taken or any(c[0] == std and c[1] is lec for c in CART):
        return False
    return all(passed(std, p, g, TARGET) for p, g in PREQ.get(lec["cd"], []))


T2_POOL = [s for s in sorted(ST) if enrolled_in(s, TARGET) and s not in (
    "20240001", "20240002", "20240003", "20240004", "20240005", "20250002")]   # 다른 사례 학생은 뺀다
TARGET_LECS = sorted([l for l in LECTURES if l["sem"] == TARGET], key=lambda l: (l["cd"], l["sect"]))
OVERLAP = OVERMAX = None
for std in T2_POOL:
    mine, mx = enrolled_in(std, TARGET), MAXC[(std, TARGET)]
    for lec in TARGET_LECS:
        if OVERLAP is None and only_blocked_by_rule(std, lec) and any(overlap(lec["slot"], l["slot"]) for l in mine)                 and credit_excl_chapel(mine + [lec]) <= mx:
            OVERLAP = (std, lec, next(l for l in mine if overlap(lec["slot"], l["slot"])))
for std in T2_POOL:
    if OVERLAP and std == OVERLAP[0]:
        continue
    mine, mx = enrolled_in(std, TARGET), MAXC[(std, TARGET)]
    for lec in TARGET_LECS:
        if OVERMAX is None and only_blocked_by_rule(std, lec) and not any(overlap(lec["slot"], l["slot"]) for l in mine)                 and credit_excl_chapel(mine + [lec]) > mx:
            OVERMAX = (std, lec, credit_excl_chapel(mine), mx)
assert OVERLAP and OVERMAX, (OVERLAP, OVERMAX)
CART.append((OVERLAP[0], OVERLAP[1], sched_of(TARGET, "CART")[2] + timedelta(days=3)))
CART.append((OVERMAX[0], OVERMAX[1], sched_of(TARGET, "CART")[2] + timedelta(days=3, hours=1)))

# ---------------------------------------------------------------- 검증(생성기 안에서)
assert "20230001" in GRADUATED and "20230002" not in GRADUATED, GRADUATED
assert sem_gpa("20240001", CUR) == 4.0
g2 = sem_gpa("20240002", CUR)
assert 3.95 <= g2 < 4.0, g2
assert MAXC[("20240001", TARGET)] == 21 and MAXC[("20240002", TARGET)] == 18
assert MAXC[("20250002", TARGET)] == 21     # 복학생: 휴학 직전 2025-2 평점 4.3
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


TABLES = ["grade", "enrollment", "course_cart", "teaching_assistant_assignment", "lecture_time_slot", "lecture",
          "semester_maximum_credit", "academic_schedule", "prerequisite_course", "course", "classroom", "semester",
          "administrator", "professor", "student", "department", "user_account"]
sql = ["-- 개발·테스트·시연용 시드. tools/seed/build_seed.py 로 생성한다. 직접 고치지 말고 생성기를 고친다.",
       f"-- 기준 시각(T0) {SNAPSHOT:%Y-%m-%d %H:%M}: 현재 학기 2026-2(확정 완료), 신청 대상 학기 2027-1(4·3학년 신청 끝, 2·1학년 장바구니만)",
       "-- 모든 계정 비밀번호: test1234! (BCrypt)",
       "-- Flyway 반복 마이그레이션이다. 이 파일이 바뀌면 다음 서버 시작 때 모든 테이블을 비우고 다시 넣는다.",
       "SET NAMES utf8mb4;", "SET FOREIGN_KEY_CHECKS = 0;"]
sql += [f"DELETE FROM {t};" for t in TABLES]
sql += ["SET FOREIGN_KEY_CHECKS = 1;", ""]
accts = [(ADMIN, PWD_HASH, "관리자", "admin@example.ac.kr", "ADMIN", "ACTIVE")]
accts += [(p, PWD_HASH, nm, f"prof{p}@example.ac.kr", "PROFESSOR", st) for p, nm, _, _, st, _ in PROFS]
accts += [(s, PWD_HASH, v["nm"], f"{s}@example.ac.kr", "STUDENT", "ACTIVE") for s, v in ST.items()]
sql.append(insert("user_account", ["usr_no", "pwd", "usr_nm", "email", "role", "acct_stat"], accts))
sql.append(insert("department", ["dept_no", "dept_nm"], DEPTS))
sql.append(insert("student", ["std_no", "dept_no", "yr_lvl", "std_stat"],
                  [(s, v["dept"], FINAL[s][0], FINAL[s][1]) for s, v in ST.items()]))
sql.append(insert("professor", ["prof_no", "dept_no", "office"], [(p, d, o) for p, _, d, o, _, _ in PROFS]))
sql.append(insert("administrator", ["emp_no"], [(ADMIN,)]))
sql.append(insert("semester", ["sem_no", "yr", "sem_type", "cur_sem_yn"],
                  [(s, y, t, "Y" if s == CUR else "N") for s, y, t in SEMS]))
sql.append(insert("classroom", ["room_cd", "room_nm"], ROOMS))
sql.append(insert("course", ["crs_cd", "dept_no", "crs_nm", "crdt", "cmpl_type", "eval_type", "rcmd_yr_lvl"],
                  [(c["cd"], c["dept"], c["nm"], c["crdt"], c["cmpl"], c["eval"], c["rcmd"]) for c in COURSES.values()]))
sql.append(insert("prerequisite_course", ["crs_cd", "prereq_crs_cd", "min_req_grd"], PREREQS))
srows, n = [], 0
for s, _, _ in SEMS:
    for typ, lvl, a, b in SCHED[s]:
        n += 1
        srows.append((n, s, typ, lvl, a, b))
sql.append(insert("academic_schedule", ["schd_no", "sem_no", "schd_type", "tgt_yr_lvl", "start_dtm", "end_dtm"], srows))
sql.append(insert("semester_maximum_credit", ["std_no", "sem_no", "max_crdt"],
                  sorted([(s, sem, m) for (s, sem), m in MAXC.items()], key=lambda r: (r[1], r[0]))))
lrows = []
for l in LECTURES:
    gst = l.get("gstat", "NOT_ENTERED")
    cdt = sched_of(l["sem"], "GRADE")[3] + timedelta(days=5) if gst == "CONFIRMED" else None
    wt = l["wt"] or (None,) * 4
    lrows.append((l["no"], l["cd"], l["sem"], l["prof"], l["sect"], l["cap"], cnt(l), l["stat"], l["eng"], l["exmp"],
                  f"plans/{l['sem']}/{l['cd']}-{l['sect']}.pdf", *wt, gst, ADMIN if cdt else None, cdt))
sql.append(insert("lecture", ["lec_no", "crs_cd", "sem_no", "prof_no", "sect_no", "cap", "enrl_cnt", "lec_stat",
                              "eng_lec_yn", "rel_grd_exmp_yn", "plan_file", "mid_wt", "fin_wt", "hw_wt", "att_wt",
                              "grd_stat", "cnfm_emp_no", "cnfm_dtm"], lrows))
sql.append(insert("lecture_time_slot", ["lec_no", "dow", "start_tm", "end_tm", "cls_type", "room_cd"],
                  [(l["no"], d, hm(a), hm(b), l["cls"], l["room"]) for l in LECTURES for d, a, b in l["slot"]]))
sql.append(insert("teaching_assistant_assignment", ["lec_no", "std_no"], [(find_lec(sem, cd)["no"], s) for s, sem, cd in TA]))
sql.append(insert("course_cart", ["std_no", "lec_no", "reg_dtm"], sorted({(s, l["no"], d) for s, l, d in CART})))
ENR.sort(key=lambda e: (order_key(e["lec"]["sem"]), e["lec"]["no"], e["std"]))
sql.append(insert("enrollment", ["std_no", "lec_no", "enrl_stat", "retake_yn", "proc_type", "enrl_dtm", "cncl_dtm"],
                  [(e["std"], e["lec"]["no"], e["stat"], e["retake"], e["proc"], e["enrl_dtm"], e["cncl_dtm"]) for e in ENR]))
sql.append(insert("grade", ["std_no", "lec_no", "mid_scr", "fin_scr", "hw_scr", "att_scr", "tot_scr", "ltr_grd"],
                  [(e["std"], e["lec"]["no"], e["grade"]["mid"], e["grade"]["fin"], e["grade"]["hw"], e["grade"]["att"],
                    e["grade"]["tot"], e["grade"]["ltr"]) for e in ENR if e["grade"] and e["stat"] == "ENROLLED"]))
OUT_SEED.write_text("\n\n".join(sql) + "\n", encoding="utf-8", newline="\n")

# ---------------------------------------------------------------- 시점
T0 = SNAPSHOT                          # 기준. 열린 기간 없음
T1 = datetime(2027, 2, 11, 11, 0)      # 2학년 수강신청
T2 = datetime(2027, 3, 4, 11, 0)       # 정정(전 학년)
T3 = datetime(2027, 6, 20, 10, 0)      # 2027-1 성적 입력 (오버레이 필요)


def lec_label(l):
    return f"{l['no']}번 {COURSES[l['cd']]['nm']}({l['cd']}-{l['sect']})"


def gpa_txt(std, sem):
    g = sem_gpa(std, sem)
    return "없음" if g is None else f"{g:.4f}"


tl = lambda cd, sect=1: find_lec(TARGET, cd, sect)

# ---------------------------------------------------------------- T3 오버레이
# T0 → T3 사이에 일어난 일: 2·1학년 수강신청(02-11·12), 겨울학기 성적 확정, 현재학기 전환(2027-1), 2027-1 성적 입력 시작
OV_ENR, extra = [], {}
for std, lvl, chosen in sorted(PLANNED, key=lambda x: (-x[1], x[0])):
    for i, (lec, _, rt) in enumerate(chosen):
        if lec["stat"] != "OPEN" or cnt(lec) + extra.get(id(lec), 0) >= lec["cap"]:
            continue
        extra[id(lec)] = extra.get(id(lec), 0) + 1
        OV_ENR.append(dict(std=std, lec=lec, retake=rt, enrl_dtm=enroll_time(std, TARGET, i)))


def t3_rows(lec):
    base = [e for e in ENR if e["lec"] is lec and e["stat"] == "ENROLLED"]
    return sorted([(e["std"], e["retake"]) for e in base] + [(e["std"], e["retake"]) for e in OV_ENR if e["lec"] is lec])


def gradable(l):
    return (l["sem"] == TARGET and l["stat"] == "OPEN" and COURSES[l["cd"]]["eval"] == "GRADE" and l["wt"]
            and 3 <= len(t3_rows(l)) <= 9 and id(l) not in SPECIAL and l is not OVERLAP[1] and l is not OVERMAX[1])


T3_PICK = sorted([l for l in LECTURES if gradable(l)], key=lambda l: (l["prof"] != "100001", l["no"]))
T3_ENTERING, T3_SUBMITTED = T3_PICK[0], T3_PICK[1]
T3_RELATIVE = sorted([l for l in LECTURES if l["sem"] == TARGET and l["stat"] == "OPEN" and l["wt"]
                      and COURSES[l["cd"]]["eval"] == "GRADE" and len(t3_rows(l)) >= 10],
                     key=lambda l: (l["exmp"] == "Y" or l["eng"] == "Y", l["no"]))
assert T3_RELATIVE, "T3에 상대평가 대상(10명 이상) 강의가 없음"
T3_GRADES = []   # (std, lec, grade dict)
for lec, st in ((T3_ENTERING, "ENTERING"), (T3_SUBMITTED, "SUBMITTED")):
    rows = t3_rows(lec)
    for i, (std, rt) in enumerate(rows):
        empty = st == "ENTERING" and i == len(rows) - 1
        g = dict(mid=None, fin=None, hw=None, att=None, tot=None, ltr=None) if empty else \
            score_grade(lec, ST[std]["ab"], None, rt)
        T3_GRADES.append((std, lec, g))
    lec["t3_gstat"] = st
W_ENTERING, W_SUBMITTED = find_lec(10, "1030006"), find_lec(10, "1030010")
W_FILL = [(e["std"], e["lec"], score_grade(e["lec"], ST[e["std"]]["ab"], None, e["retake"]))
          for e in ENR if e["lec"] is W_ENTERING and e["grade"] and e["grade"]["ltr"] is None]
W_CNFM = datetime(2027, 2, 15, 10, 0)

ov = ["-- T3 시점(2027-06-20 10:00, 2027-1 성적 입력 기간)으로 옮기는 오버레이. tools/seed/build_seed.py 로 생성한다.",
      "-- 기본 시드(R__seed_dev.sql) 위에 한 번만 적용한다. 되돌리려면 기본 시드부터 다시 넣는다.",
      "-- T0 → T3 사이의 일: 2·1학년 수강신청(02-11·12), 2026 겨울학기 성적 확정(02-15), 현재학기 2027-1로 전환, 2027-1 성적 입력 시작",
      "SET NAMES utf8mb4;", ""]
ov.append(insert("enrollment", ["std_no", "lec_no", "enrl_stat", "retake_yn", "proc_type", "enrl_dtm"],
                 [(e["std"], e["lec"]["no"], "ENROLLED", e["retake"], "STUDENT", e["enrl_dtm"])
                  for e in sorted(OV_ENR, key=lambda e: (e["lec"]["no"], e["std"]))]))
for lec in sorted({id(e["lec"]): e["lec"] for e in OV_ENR}.values(), key=lambda l: l["no"]):
    ov.append(f"UPDATE lecture SET enrl_cnt = {cnt(lec) + extra[id(lec)]} WHERE lec_no = {lec['no']};")
for std, lec, g in W_FILL:
    ov.append(f"UPDATE grade SET mid_scr = {g['mid']}, fin_scr = {g['fin']}, hw_scr = {g['hw']}, att_scr = {g['att']}, "
              f"tot_scr = {g['tot']}, ltr_grd = {q(g['ltr'])} WHERE std_no = {q(std)} AND lec_no = {lec['no']};")
ov.append(f"UPDATE lecture SET grd_stat = 'CONFIRMED', cnfm_emp_no = {q(ADMIN)}, cnfm_dtm = {q(W_CNFM)} "
          f"WHERE lec_no IN ({W_ENTERING['no']}, {W_SUBMITTED['no']});")
ov.append(f"UPDATE semester SET cur_sem_yn = 'N' WHERE sem_no = {CUR};")
ov.append(f"UPDATE semester SET cur_sem_yn = 'Y' WHERE sem_no = {TARGET};")
ov.append(insert("grade", ["std_no", "lec_no", "mid_scr", "fin_scr", "hw_scr", "att_scr", "tot_scr", "ltr_grd"],
                 [(s_, l_["no"], g["mid"], g["fin"], g["hw"], g["att"], g["tot"], g["ltr"]) for s_, l_, g in T3_GRADES]))
ov.append(f"UPDATE lecture SET grd_stat = 'ENTERING' WHERE lec_no = {T3_ENTERING['no']};")
ov.append(f"UPDATE lecture SET grd_stat = 'SUBMITTED' WHERE lec_no = {T3_SUBMITTED['no']};")
OUT_T3.write_text("\n\n".join(ov) + "\n", encoding="utf-8", newline="\n")

# ---------------------------------------------------------------- 사례 찾기(안내·상수용)
ONLINE, NO_ROOM, CLOSED = tl("1030011"), tl("1030014"), tl("1030016")
ENG_MAJOR, ENG_GENERAL, REL_EXEMPT = tl("1010015"), tl("1030013"), tl("1020015")
NO_WEIGHTS = [tl(c) for c in ("1010019", "1020019", "1030009")]
TWO_SECTIONS = tl("1030007", 2)
RETAKE_LEC = tl("1010014")
W_CONFIRMED = find_lec(10, "1030008")
SOPH = sorted({s_ for s_, l_, _ in CART if l_ is FULL and yr_level(s_, TARGET) == 2}, key=lambda x: (x != "20260001", x))
ONE_LEFT_CART = sorted({s_ for s_, l_, _ in CART if l_ is ONE_LEFT and not any(
    e["std"] == s_ and e["lec"] is ONE_LEFT for e in ENR)})
ONLINE_STD = sorted({e["std"] for e in ENR if e["lec"] is ONLINE and e["stat"] == "ENROLLED"})
ONLINE_WHEN = "T0"
if not ONLINE_STD:   # T0에 온라인 강의 수강생이 없으면 T3(2·1학년 신청 후)에서 고른다
    ONLINE_STD, ONLINE_WHEN = sorted({e["std"] for e in OV_ENR if e["lec"] is ONLINE}), "T3"
assert ONLINE_STD, "온라인 강의 수강생 없음"
PROF_LECS = [l for l in LECTURES if l["sem"] == TARGET and l["prof"] == "100001"]
TA_LEC = find_lec(TARGET, "1020001")
assert SOPH, "정원 마감 강의를 담은 2학년이 없음"

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
    ("Y2026_SECOND", CUR, "2026-2. T0~T2의 현재 학기(성적 확정 완료)"),
    ("Y2026_WINTER", 10, "2026 겨울학기"),
    ("Y2027_FIRST", TARGET, "2027-1. 신청 대상 학기, T3의 현재 학기")])
java += jclass("Account", "학생이 아닌 계정.", [
    ("ADMIN", ADMIN, "관리자"),
    ("PROFESSOR", "100001", f"교수. 2027-1 담당 강의 {len(PROF_LECS)}개"),
    ("PROFESSOR_INACTIVE", "100006", "퇴직 교수(INACTIVE). 로그인 차단")])
java += jclass("Student", "학번.", [
    ("GRADUATED", "20230001", "졸업생. 2027-1 장바구니·신청·최대학점 없음"),
    ("ON_LEAVE", "20230003", "휴학생. 2027-1 최대학점 없음"),
    ("RETURNED", "20250002", "복학생. 휴학 직전 학기 평점 4.3 → 최대학점 21"),
    ("FRESHMAN", "20270001", "2027 신입생. 이력 없음"),
    ("GPA_EXACTLY_4", "20240001", "2026-2 평점 4.0 → 최대학점 21"),
    ("GPA_JUST_UNDER_4", "20240002", "2026-2 평점 4.0 미만 → 최대학점 18. 신청 취소 이력"),
    ("PASS_FAIL_ONLY", "20250003", "2026-2 P/F 과목만 → 평점 없음 → 최대학점 18"),
    ("RETAKE", "20240003", "재수강 이력(F → A- 상한). 2027-1 재수강 신청, 컴퓨터네트워크(C+) 재수강 가능"),
    ("PREREQ_NOT_MET", "20240004", "자료구조 D+ → 데이터베이스(최소 C0) 신청 불가. 장바구니에는 있음"),
    ("TEACHING_ASSISTANT", "20240005", f"조교. 2027-1 {TA_LEC['no']}번 강의"),
    ("ADMIN_ADDED", "20250001", "2026-2 관리자 수동 추가 1건"),
    ("SOPHOMORE_FULL_IN_CART", SOPH[0], "2학년. 정원 마감 강의가 장바구니에 있음(T1)"),
    ("TIME_CONFLICT", OVERLAP[0], "장바구니 강의가 신청한 강의와 시간이 겹침(T2)"),
    ("OVER_MAX_CREDIT", OVERMAX[0], f"장바구니 강의를 더하면 최대학점({OVERMAX[3]}) 초과(T2)")])
java += jclass("Lecture", "강의번호.", [
    ("FULL", FULL["no"], "2027-1 정원 마감"),
    ("ONE_SEAT_LEFT", ONE_LEFT["no"], "2027-1 한 자리 남음"),
    ("ONLINE", ONLINE["no"], "2027-1 온라인(강의실 없음)"),
    ("NO_ROOM", NO_ROOM["no"], "2027-1 대면인데 강의실 미배정"),
    ("CLOSED", CLOSED["no"], "2027-1 폐강"),
    ("TWO_SECTIONS", TWO_SECTIONS["no"], "같은 과목의 2분반"),
    ("ENGLISH_MAJOR", ENG_MAJOR["no"], "전공 영어강의 → 상대평가 제외"),
    ("ENGLISH_GENERAL", ENG_GENERAL["no"], "교양 영어강의 → 상대평가 대상"),
    ("RELATIVE_EXEMPT", REL_EXEMPT["no"], "상대평가 제외 인정"),
    ("NO_WEIGHTS", NO_WEIGHTS[0]["no"], "반영비율 미입력"),
    ("TIME_CONFLICT_TARGET", OVERLAP[1]["no"], "TIME_CONFLICT 학생이 담은, 시간이 겹치는 강의"),
    ("OVER_MAX_CREDIT_TARGET", OVERMAX[1]["no"], "OVER_MAX_CREDIT 학생이 담은 강의"),
    ("RETAKE_TARGET", RETAKE_LEC["no"], "RETAKE 학생이 재수강 신청한 강의"),
    ("TA_TARGET", TA_LEC["no"], "TEACHING_ASSISTANT 학생이 조교인 강의"),
    ("WINTER_CONFIRMED", W_CONFIRMED["no"], "2026 겨울 확정"),
    ("WINTER_SUBMITTED", W_SUBMITTED["no"], "2026 겨울 제출(확정 대기)"),
    ("WINTER_ENTERING", W_ENTERING["no"], "2026 겨울 입력중(점수 빈 학생 1명)"),
    ("T3_ENTERING", T3_ENTERING["no"], "T3 오버레이 후 입력중(점수 빈 학생 1명)"),
    ("T3_SUBMITTED", T3_SUBMITTED["no"], "T3 오버레이 후 제출"),
    ("T3_RELATIVE", T3_RELATIVE[0]["no"], f"T3 오버레이 후 수강생 {len(t3_rows(T3_RELATIVE[0]))}명, 상대평가 대상")])
java[-1:] = ["}"]
OUT_FIXTURES.write_text("\n".join(java) + "\n", encoding="utf-8", newline="\n")

# ---------------------------------------------------------------- 모듈별 안내
def stds(lst):
    return ", ".join(lst)


def row(*c):
    return "| " + " | ".join(str(x) for x in c) + " |"


g = ["# 시드 데이터 안내", "",
     "개발·테스트·시연 모두 이 데이터를 쓴다. `tools/seed/build_seed.py` 가 만든다(직접 고치지 않는다). 모든 계정의 비밀번호는 `test1234!` 다.", "",
     "## 시점", "",
     "앱의 현재 시각을 아래 시점 중 하나로 고정해 띄운다. 기본값은 T0 다. 기능마다 열려 있어야 하는 기간이 다르기 때문이다.", "",
     row("시점", "시각", "열린 기간", "준비", "주로 쓰는 모듈"), row(*["---"] * 5),
     row("T0 기준", f"{T0:%Y-%m-%d %H:%M}", "없음", "기본 시드", "A 조회, B 계정·강의 개설, C 강의 운영·성적 확정"),
     row("T1 신청", f"{T1:%Y-%m-%d %H:%M}", "2학년 수강신청", "기본 시드", "B 수강신청·정원"),
     row("T2 정정", f"{T2:%Y-%m-%d %H:%M}", "정정(전 학년)", "기본 시드", "B 장바구니·신청 차단 규칙"),
     row("T3 성적", f"{T3:%Y-%m-%d %H:%M}", "2027-1 성적 입력", "기본 시드 + `t3_grade.sql`", "C 성적 입력·제출"), "",
     "### 시점 바꾸기", "",
     "- 로컬: `.env` 의 `CLOCK_FIXED_AT` 을 바꾸고 서버를 다시 시작한다(예: `CLOCK_FIXED_AT=2027-03-04T11:00:00`). 비우면 실제 시각을 쓴다.",
     "- T3: 서버를 한 번 띄워 기본 시드를 넣은 뒤 오버레이를 적용한다.", "",
     "  ```bash", "  docker exec -i crs-mysql sh -c 'mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" crs' < src/main/resources/db/scenario/t3_grade.sql", "  ```", "",
     "- 처음 상태로 되돌리기: `docker compose down -v && docker compose up -d` 후 서버를 다시 시작한다.",
     "- 테스트: `@TestPropertySource(properties = \"crs.clock.fixed-at=\" + SeedTime.T2)` 처럼 고르고, 사례는 `SeedFixtures` 상수로 가리킨다. T3 는 `SeedTime.applyT3(dataSource)` 를 테스트 트랜잭션 안에서 부른다.", "",
     "## 로그인 계정", "",
     row("역할", "ID", "쓰임"), row(*["---"] * 3),
     row("관리자", ADMIN, "학기·일정, 강의 개설, 사용자, 신청 현황, 성적 확정"),
     row("교수", "100001", f"2027-1 담당 강의 {len(PROF_LECS)}개({', '.join(str(l['no']) for l in PROF_LECS)}번)"),
     row("교수(퇴직)", "100006", "비활성 계정 로그인 차단"),
     row("학생 겸 조교", "20240005", f"2027-1 {lec_label(TA_LEC)} 조교"),
     row("학생", "20240001", "평점 4.0, 최대학점 21"),
     row("학생(2학년)", SOPH[0], "T1에 신청해 볼 학생"), "",
     "## 모듈 A: 학사 기준·조회", "",
     row("기능", "시점", "대상", "확인할 것"), row(*["---"] * 4),
     row("성적 조회(GRD-03)", "T0", "20240001", f"2026-2 평점 {gpa_txt('20240001', CUR)}(전부 A-)"),
     row("성적 조회", "T0", "20240002", f"2026-2 평점 {gpa_txt('20240002', CUR)}(4.0 바로 아래)"),
     row("성적 조회", "T0", "20250003", "2026-2 P/F 과목만 → 평점 없음"),
     row("성적 조회", "T0", "20240003", "이산수학 F → 재수강 A-(총점 98이어도 상한)"),
     row("성적 조회", "T0", "20230003", "휴학 중 2026 겨울 수강. 계절 성적은 정규학기 평점에서 빠지고 이수 내역에는 들어감"),
     row("최대학점 산정", "T0", "20240001 / 20240002", "21 / 18 (4.0 경계)"),
     row("최대학점 산정", "T0", "20250002", "복학생: 휴학 직전 2025-2 평점 기준 21"),
     row("최대학점 산정", "T0", "20250003", "직전 학기 평점 없음 → 18"),
     row("최대학점 산정", "T0", "20230001 / 20230003", "졸업생·휴학생은 2027-1 정규학기 최대학점 없음"),
     row("강의 검색(REG-01)", "T0", lec_label(FULL), "잔여 0"),
     row("강의 검색", "T0", lec_label(ONE_LEFT), "잔여 1"),
     row("강의 검색", "T0", f"{lec_label(tl('1030007'))}, {TWO_SECTIONS['no']}번", "같은 과목 2분반"),
     row("강의 검색", "T0", lec_label(CLOSED), "폐강"),
     row("시간표(REG-04)", ONLINE_WHEN, stds(ONLINE_STD[:2]), f"온라인 강의 {lec_label(ONLINE)} 포함(강의실 없음)"),
     row("학기·일정(ACA-01)", "T0", "2027-1 일정", "4·3학년 신청일은 시작됨(삭제 불가), 정정·성적 입력은 시작 전(삭제 가능)"),
     row("대시보드(ACA-04)", "T0", "2027-1", f"개설 강의 {sum(1 for l in LECTURES if l['sem'] == TARGET and l['stat'] == 'OPEN')}개, 폐강 1개, 정원 마감 1개"),
     row("품질 검사", "T0", lec_label(NO_ROOM), "신청이 시작됐는데 대면 강의에 강의실 미배정"), "",
     "## 모듈 B: 계정·수강신청", "",
     row("기능", "시점", "대상", "확인할 것"), row(*["---"] * 4),
     row("로그인(USR-01)", "아무 때", "9001 / 100001 / 20240001", "역할별 로그인"),
     row("로그인", "아무 때", "100006", "비활성 계정 차단"),
     row("강의 개설(ACA-03)", "T0", lec_label(NO_ROOM), "강의실 미배정 대면 강의. 강의실·시간 배정"),
     row("강의 개설", "T0", lec_label(CLOSED), "폐강 강의"),
     row("수강신청(REG-03)", "T1", f"{SOPH[0]} → {FULL['no']}번", "정원 마감으로 차단"),
     row("수강신청", "T1", f"{stds(ONE_LEFT_CART) or '-'} → {ONE_LEFT['no']}번", "남은 한 자리(동시 신청 실험)"),
     row("수강신청", "T1", "20250001(3학년)", "자기 학년 신청일이 아님 → 차단"),
     row("수강신청", "T2", f"{OVERLAP[0]} → {OVERLAP[1]['no']}번", f"이미 신청한 {OVERLAP[2]['no']}번과 시간 겹침 → 차단"),
     row("수강신청", "T2", f"{OVERMAX[0]} → {OVERMAX[1]['no']}번", f"신청 {OVERMAX[2]:g}학점 + {COURSES[OVERMAX[1]['cd']]['crdt']:g}학점 > 최대 {OVERMAX[3]} → 차단"),
     row("수강신청", "T2", f"20240004 → {RETAKE_LEC['no']}번", "선수과목(자료구조 C0 이상) 미이수 → 차단"),
     row("수강신청", "T2", f"20240003 → {ENG_MAJOR['no']}번", "컴퓨터네트워크 C+ → 재수강으로 신청(retake_yn Y)"),
     row("수강신청", "T2", "20230001 / 20230003", "졸업생·휴학생 → 차단"),
     row("수강신청", "T2", "아무 학생", "장바구니에 없는 강의 → 차단"),
     row("장바구니(REG-02)", "T0 / T2", "아무 학생", "T0은 기간 밖이라 담기 차단, T2는 담기·삭제 가능"),
     row("신청 현황(REG-05)", "T2", "관리자 9001", "장바구니 확인 없이 수동 추가. 2026-2 수동 추가 이력은 20250001"), "",
     "## 모듈 C: 강의 운영·성적", "",
     row("기능", "시점", "대상", "확인할 것"), row(*["---"] * 4),
     row("과목(ACA-02)", "T0", "선수과목 6건", "최소취득등급 기본값 D-, 채플 0.5학점 P/F"),
     row("담당 강의(LEC-01)", "T0", "교수 100001", f"2027-1 {len(PROF_LECS)}개"),
     row("강의계획서·반영비율(LEC-03)", "T0", ", ".join(f"{l['no']}번" for l in NO_WEIGHTS), "반영비율 미입력"),
     row("조교(LEC-04)", "T0", f"20240005 / {TA_LEC['no']}번", "조교 지정·삭제, 조교 로그인 권한"),
     row("성적 확정(GRD-02)", "T0", lec_label(W_SUBMITTED), "제출 → 관리자 확정"),
     row("성적 확정", "T0", lec_label(W_ENTERING), "점수 빈 학생 1명 → 미입력 강의로 보임"),
     row("성적 확정", "T0", lec_label(W_CONFIRMED), "확정 후 교수 수정 불가"),
     row("성적 입력(GRD-01)", "T3", lec_label(T3_ENTERING), "입력중, 점수 빈 학생 1명"),
     row("성적 입력", "T3", lec_label(T3_SUBMITTED), "제출 → 수정하면 입력중으로"),
     row("성적 입력", "T3", lec_label(T3_RELATIVE[0]), f"수강생 {len(t3_rows(T3_RELATIVE[0]))}명 → 상대평가(A 40%, A+B 80%)"),
     row("성적 입력", "T3", lec_label(ENG_MAJOR), "전공 영어강의 → 상대평가 제외"),
     row("성적 입력", "T3", lec_label(REL_EXEMPT), "상대평가 제외 인정"),
     row("성적 입력", "T3", lec_label(RETAKE_LEC), "20240003 재수강 → A- 상한"), "",
     "## 학생", "",
     row("학번", "이름", "학과", "학년", "학적상태", "2027-1 최대학점"), row(*["---"] * 6)]
g += [row(s_, v["nm"], dict(DEPTS)[v["dept"]], FINAL[s_][0], FINAL[s_][1], MAXC.get((s_, TARGET), "-")) for s_, v in ST.items()]
g += ["", "## 학사일정 (2027-1)", "", row("유형", "대상학년", "시작", "종료"), row(*["---"] * 4)]
g += [row(t, lv or "전체", f"{a:%Y-%m-%d %H:%M}", f"{b:%Y-%m-%d %H:%M}") for t, lv, a, b in SCHED[TARGET]]
g += ["", "## 건수", "", row("테이블", "기본 시드", "T3 오버레이 추가"), row(*["---"] * 3),
      row("lecture", len(LECTURES), "-"), row("enrollment", len(ENR), len(OV_ENR)),
      row("grade", sum(1 for e in ENR if e["grade"] and e["stat"] == "ENROLLED"), len(T3_GRADES)),
      row("course_cart", len({(s_, l_['no']) for s_, l_, _ in CART}), "-"),
      row("semester_maximum_credit", len(MAXC), "-")]
OUT_GUIDE.write_text("\n".join(g) + "\n", encoding="utf-8", newline="\n")
print(f"lectures {len(LECTURES)}, enrollments {len(ENR)} (+T3 {len(OV_ENR)}), graduated {sorted(GRADUATED)}")
print(f"T2 overlap {OVERLAP[0]}->{OVERLAP[1]['no']}, overmax {OVERMAX[0]}->{OVERMAX[1]['no']} ({OVERMAX[2]}+ > {OVERMAX[3]})")
print(f"T3 entering {T3_ENTERING['no']}, submitted {T3_SUBMITTED['no']}, relative {T3_RELATIVE[0]['no']} ({len(t3_rows(T3_RELATIVE[0]))})")
