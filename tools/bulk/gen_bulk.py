"""성적 조회 실험용 대량 데이터를 CSV 로 만든다(모듈 A 설계 2-5).

사용: python -X utf8 tools/bulk/gen_bulk.py --scale S [--out build/bulk/S]
     표준 라이브러리만 쓴다. 난수 시드가 고정이라 같은 인수면 같은 데이터가 나온다.
적재: python -X utf8 tools/bulk/load_bulk.py --dir build/bulk/S   (별도 DB crs_bulk 에 넣고 verify.sql 로 검사)

규모        학생     학기   정규학기당 강의   수강신청 행     생성 / 적재 / 검사(로컬 기준)
XS          300      8      60               약 6천          생성기 점검용
S           3천      8      450              약 7.6만
M           1만      8      1,500            약 26만         5초 / 13초 / 55초
L           29,997   8      4,500            약 77만         13초 / 25초 / 190초
입학 연도가 2024·2025·2026 으로 나뉘어 학생마다 다닌 학기 수가 달라서, 학기당 6과목을 모두 곱한 값보다 적다.

학기 8개는 정규 6(2024-1 ~ 2026-2) + 계절 2(2024 여름, 2025 겨울)이고, 모두 성적이 확정된 과거 학기다.
현재 학기는 2026-2 다.

분포
- 강의 인기는 쏠림(Zipf). 70% 는 소속 학과 강의에서, 30% 는 전체에서 고른다. 일부 강의는 정원이 찬다.
- 점수는 학생 실력 + 강의 난이도 + 잡음(정규분포). 10명 이상 강의는 상대평가(A 40%, A+B 80%)를 지킨다.
- 재수강 약 4%(C+ 이하를 받은 과목), 채플은 정규학기마다, 휴학 약 10%.

지키는 규칙
- 스키마 CHECK 전부. enrl_cnt = 신청 건수 ≤ 정원
- 한 학생의 같은 학기 강의는 시간이 겹치지 않는다(강의마다 시간 칸 하나, 학생은 칸마다 하나만)
- 같은 학기에 같은 과목 두 분반 금지, 최대학점 이내(직전 정규학기 평점 4.0 이상 21, 아니면 18, 계절 6. 채플 제외)
- 재수강 성적은 A- 까지, P/F 과목은 점수 없음, 취소한 신청에는 성적 없음
- 같은 강의실·같은 시간 칸에 대면 강의 하나, 교수 한 명도 같은 시간 칸에 강의 하나
- 학기별 최대학점(semester_maximum_credit)은 다닌 학기마다 넣는다

학번은 시드와 같은 8자리(입학 연도 4 + 일련 4, D30)다. 입학 연도당 9,999명까지라 학생은 최대 29,997명이다.
"""
import argparse
import math
import random
from bisect import bisect_left
from datetime import datetime, timedelta
from itertools import accumulate
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# 학번 8자리(D30)라 입학 연도당 9,999명, 세 해 합쳐 29,997명이 상한이다. 강의 하나에 학생 약 6.7명 비율을 지킨다
SCALES = {
    "XS": dict(students=300, lectures=60),
    "S": dict(students=3_000, lectures=450),
    "M": dict(students=10_000, lectures=1_500),
    "L": dict(students=29_997, lectures=4_500),
}
MAX_PER_COHORT = 9_999

# test1234! (BCrypt). 시드와 같은 값
PWD_HASH = "$2a$10$mVtkuC8t.ybJShFMmg7dAePnho.f1IpD92TXrTXipF.OhtbWdF/.S"
ADMIN = "9001"
NULL = r"\N"

# 학기: (sem_no, 연도, 학기구분). 시간 순서
SEMESTERS = [
    (1, 2024, "FIRST"), (2, 2024, "SUMMER"), (3, 2024, "SECOND"), (4, 2025, "FIRST"),
    (5, 2025, "SECOND"), (6, 2025, "WINTER"), (7, 2026, "FIRST"), (8, 2026, "SECOND"),
]
REGULAR = {"FIRST", "SECOND"}
COHORTS = [2024, 2025, 2026]  # 입학 연도. 각각 정규학기 6·4·2개를 다님

GRADE_TABLE = [("A+", 97, 4.5), ("A0", 94, 4.3), ("A-", 90, 4.0), ("B+", 87, 3.5), ("B0", 84, 3.3), ("B-", 80, 3.0),
               ("C+", 77, 2.5), ("C0", 74, 2.3), ("C-", 70, 2.0), ("D+", 67, 1.5), ("D0", 64, 1.3), ("D-", 60, 1.0),
               ("F", 0, 0.0)]
ORDER = [g for g, _, _ in GRADE_TABLE]
POINT = {g: p for g, _, p in GRADE_TABLE}
RETAKE_OK = set(ORDER[ORDER.index("C+"):])  # C+ 이하면 재수강할 수 있다

DEPT_NAMES = ["컴퓨터학부", "경영학부", "전자공학과", "기계공학과", "화학과", "수학과", "영어영문학과", "경제학과", "심리학과",
              "교양교육원"]
GEN_DEPT = 110  # 교양교육원: 교양 과목만 연다
SURNAMES = "김이박최정강조윤장임한오서신권황안송류홍"
GIVEN = ["민준", "서연", "도윤", "지우", "하준", "서윤", "시우", "지민", "주원", "하은", "예준", "수아", "지호", "지유", "준서",
         "채원", "현우", "다은", "건우", "유나"]

# 시간 칸. 학생은 칸마다 강의 하나만 들으므로 같은 학기 강의끼리 시간이 겹치지 않는다
PERIODS = [("09:00", "10:15"), ("10:30", "11:45"), ("12:00", "13:15"), ("13:30", "14:45"), ("15:00", "16:15"),
           ("16:30", "17:45"), ("18:00", "19:15")]
BLOCKS = [[(d, s, e) for d in pair] for pair in (("MON", "WED"), ("TUE", "THU")) for s, e in PERIODS]
BLOCKS += [[("FRI", s, e)] for s, e in (("09:00", "11:45"), ("12:00", "14:45"), ("15:00", "17:45"))]
CHAPEL_SLOT = ("FRI", "18:00", "18:50")  # 다른 칸과 겹치지 않는 채플 전용 시간

TABLES = {
    "department": ["dept_no", "dept_nm"],
    "user_account": ["usr_no", "pwd", "usr_nm", "email", "role", "acct_stat"],
    "administrator": ["emp_no"],
    "student": ["std_no", "dept_no", "yr_lvl", "std_stat"],
    "professor": ["prof_no", "dept_no", "office"],
    "semester": ["sem_no", "yr", "sem_type", "cur_sem_yn"],
    "academic_schedule": ["schd_no", "sem_no", "schd_type", "tgt_yr_lvl", "start_dtm", "end_dtm"],
    "semester_maximum_credit": ["std_no", "sem_no", "max_crdt"],
    "classroom": ["room_cd", "room_nm"],
    "course": ["crs_cd", "dept_no", "crs_nm", "crdt", "cmpl_type", "eval_type", "rcmd_yr_lvl"],
    "lecture": ["lec_no", "crs_cd", "sem_no", "prof_no", "sect_no", "cap", "enrl_cnt", "lec_stat", "eng_lec_yn",
                "rel_grd_exmp_yn", "plan_file", "mid_wt", "fin_wt", "hw_wt", "att_wt", "grd_stat", "cnfm_emp_no",
                "cnfm_dtm"],
    "lecture_time_slot": ["lec_no", "dow", "start_tm", "end_tm", "cls_type", "room_cd"],
    "enrollment": ["std_no", "lec_no", "enrl_stat", "retake_yn", "proc_type", "enrl_dtm", "cncl_dtm"],
    "grade": ["std_no", "lec_no", "mid_scr", "fin_scr", "hw_scr", "att_scr", "tot_scr", "ltr_grd"],
}


def grade_of(tot):
    return next(g for g, lo, _ in GRADE_TABLE if tot >= lo)


def clamp(v, lo=0, hi=100):
    return max(lo, min(hi, v))


class Writer:
    """테이블별 CSV. 값에 쉼표가 없도록 만들고, NULL 은 \\N 으로 쓴다."""

    def __init__(self, out):
        out.mkdir(parents=True, exist_ok=True)
        self.files = {t: open(out / f"{t}.csv", "w", encoding="utf-8", newline="\n") for t in TABLES}
        self.counts = dict.fromkeys(TABLES, 0)

    def row(self, table, *values):
        self.files[table].write(",".join(NULL if v is None else str(v) for v in values) + "\n")
        self.counts[table] += 1

    def close(self):
        for f in self.files.values():
            f.close()


class Picker:
    """Zipf 가중치로 강의를 고른다. 누적 가중치에 이분 탐색."""

    def __init__(self, rng, lectures):
        self.lectures = lectures
        ranks = list(range(1, len(lectures) + 1))
        rng.shuffle(ranks)
        self.cum = list(accumulate(1.0 / r for r in ranks))

    def pick(self, rng):
        return self.lectures[bisect_left(self.cum, rng.random() * self.cum[-1])]


def schedule_times(yr, sem_type):
    """학기별 장바구니·수강신청·정정·성적입력 기간."""
    base = {"FIRST": datetime(yr, 2, 1), "SUMMER": datetime(yr, 6, 1), "SECOND": datetime(yr, 8, 1),
            "WINTER": datetime(yr, 12, 1)}[sem_type]
    cart = (base, base + timedelta(days=4))
    enroll = (base + timedelta(days=7, hours=10), base + timedelta(days=10, hours=17))
    adjust = (base + timedelta(days=30, hours=10), base + timedelta(days=36, hours=17))
    grade = (base + timedelta(days=120), base + timedelta(days=130))
    return dict(CART=cart, ENROLL=enroll, ADJUST=adjust, GRADE=grade)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--scale", choices=SCALES, default="S")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--seed", type=int, default=20261122)
    args = parser.parse_args()
    cfg = SCALES[args.scale]
    out = args.out or REPO / "build" / "bulk" / args.scale
    rng = random.Random(args.seed)
    w = Writer(out)

    n_students, n_lec = cfg["students"], cfg["lectures"]
    assert math.ceil(n_students / len(COHORTS)) <= MAX_PER_COHORT, "학번 8자리로 만들 수 있는 학생 수를 넘었다"

    # ---------------------------------------------------------------- 기준 데이터
    depts = list(range(101, 101 + len(DEPT_NAMES)))
    major_depts = [d for d in depts if d != GEN_DEPT]
    for d, nm in zip(depts, DEPT_NAMES):
        w.row("department", d, nm)

    w.row("user_account", ADMIN, PWD_HASH, "관리자", "admin@crs.ac.kr", "ADMIN", "ACTIVE")
    w.row("administrator", ADMIN)

    for sem_no, yr, st in SEMESTERS:
        w.row("semester", sem_no, yr, st, "Y" if sem_no == 8 else "N")
    schd_no = 0
    sched = {}
    for sem_no, yr, st in SEMESTERS:
        sched[sem_no] = schedule_times(yr, st)
        for typ, (s, e) in sched[sem_no].items():
            schd_no += 1
            w.row("academic_schedule", schd_no, sem_no, typ, None, s, e)

    # 교수: 교수 한 명이 학기당 강의 약 4개
    n_prof = max(10, n_lec // 4)
    profs_by_dept = {d: [] for d in depts}
    all_profs = []
    for i in range(n_prof):
        pno = str(100001 + i)
        d = depts[i % len(depts)]
        profs_by_dept[d].append(pno)
        all_profs.append(pno)
        nm = rng.choice(SURNAMES) + rng.choice(GIVEN)
        w.row("user_account", pno, PWD_HASH, nm, f"p{pno}@crs.ac.kr", "PROFESSOR", "ACTIVE")
        w.row("professor", pno, d, f"연구실 {i + 1}")

    # 강의실: 시간 칸 하나에 대면 강의가 몰리는 만큼
    n_room = math.ceil(n_lec * 1.2 / len(BLOCKS)) + 5
    rooms = [f"{20000 + i:05d}" for i in range(1, n_room + 1)]
    for i, r in enumerate(rooms):
        w.row("classroom", r, f"공학관 {i + 1}호")

    # 과목: 학과마다 전공 과목, 교양교육원은 교양, 채플 하나
    courses = {}  # crs_cd -> dict
    n_course = max(30, n_lec // 2)
    for i in range(n_course):
        d = depts[i % len(depts)]
        seq = i // len(depts) + 1
        cd = f"{d}{seq:04d}"
        if d == GEN_DEPT:
            cmpl, yr_lvl = rng.choice(["GEN_REQ", "GEN_ELEC", "GEN_ELEC"]), None
        else:
            cmpl, yr_lvl = rng.choice(["MAJOR_BASIC", "MAJOR_REQ", "MAJOR_ELEC", "MAJOR_ELEC", "FREE_ELEC"]), rng.randint(1, 4)
        crdt = rng.choices(["3.0", "2.0", "1.0"], [85, 12, 3])[0]
        ev = "PASS_FAIL" if rng.random() < 0.04 else "GRADE"
        courses[cd] = dict(cd=cd, dept=d, crdt=float(crdt), cmpl=cmpl, eval=ev)
        w.row("course", cd, d, f"{DEPT_NAMES[d - 101][:2]}과목{seq}", crdt, cmpl, ev, yr_lvl)
    chapel_cd = f"{GEN_DEPT}9999"
    courses[chapel_cd] = dict(cd=chapel_cd, dept=GEN_DEPT, crdt=0.5, cmpl="CHAPEL", eval="PASS_FAIL")
    w.row("course", chapel_cd, GEN_DEPT, "채플", "0.5", "CHAPEL", "PASS_FAIL", None)
    normal_courses = [c for c in courses.values() if c["cmpl"] != "CHAPEL"]
    gen_courses = [c for c in normal_courses if c["dept"] == GEN_DEPT]

    # 학생: 입학 연도별로 1/3 씩. 휴학생 약 10%
    students = []
    for i in range(n_students):
        cohort = COHORTS[i % len(COHORTS)]
        sno = f"{cohort}{i // len(COHORTS) + 1:04d}"
        st = dict(no=sno, dept=rng.choice(major_depts), cohort=cohort, ability=rng.gauss(0, 1),
                  on_leave=rng.random() < 0.10, history={}, last_gpa=None, regular_cnt=0)
        # 휴학생은 다닐 수 있는 정규학기 중 마지막 하나 이상을 쉰다(현재 휴학 중)
        own_regular = [s for s, yr, t in SEMESTERS if t in REGULAR and yr >= cohort]
        st["leave_sems"] = set(own_regular[-rng.randint(1, min(2, len(own_regular))):]) if st["on_leave"] else set()
        students.append(st)

    # ---------------------------------------------------------------- 학기별 강의·신청·성적
    lec_no = 0
    total_enrollments = 0
    for sem_no, yr, sem_type in SEMESTERS:
        regular = sem_type in REGULAR
        times = sched[sem_no]
        cnfm_dtm = times["GRADE"][1] + timedelta(days=1)
        n_sem_lec = n_lec if regular else max(10, n_lec // 10)
        pool = normal_courses if regular else gen_courses + rng.sample(normal_courses, min(len(normal_courses), 20))

        # 강의 개설. 시간 칸은 차례로 돌리고, 같은 칸의 대면 강의는 강의실을 하나씩 쓴다
        lectures = []
        sect = {}
        room_next = [0] * len(BLOCKS)
        busy = set()  # (교수, 시간 칸). 교수 한 명이 같은 시간에 두 강의를 맡지 않는다

        def free_prof(dept, block):
            cands = [p for p in profs_by_dept[dept] if (p, block) not in busy]
            prof = rng.choice(cands or [p for p in all_profs if (p, block) not in busy])
            busy.add((prof, block))
            return prof

        for k in range(n_sem_lec):
            crs = rng.choice(pool)
            sect[crs["cd"]] = sect.get(crs["cd"], 0) + 1
            if sect[crs["cd"]] > 99:
                continue
            lec_no += 1
            block = k % len(BLOCKS)
            online = rng.random() < 0.08
            room = None
            if not online:
                room = rooms[room_next[block]]
                room_next[block] += 1
            lec = dict(no=lec_no, crs=crs, sem=sem_no, block=block, cap=rng.choice([30, 40, 50, 60, 80, 120]),
                       prof=free_prof(crs["dept"], block), sect=sect[crs["cd"]], online=online, room=room,
                       exempt=rng.random() < 0.05, students=[])
            lectures.append(lec)
        # 채플: 정규학기마다, 온라인(강의실 없음), 정원 999 분반을 필요한 만큼
        chapels = []
        if regular:
            for s in range(1, math.ceil(n_students / 900) + 2):
                lec_no += 1
                chapels.append(dict(no=lec_no, crs=courses[chapel_cd], sem=sem_no, block=None, cap=999,
                                    prof=free_prof(GEN_DEPT, "CHAPEL"), sect=s, online=True, room=None,
                                    exempt=True, students=[]))

        by_dept = {}
        for lec in lectures:
            by_dept.setdefault(lec["crs"]["dept"], []).append(lec)
        all_picker = Picker(rng, lectures)
        dept_picker = {d: Picker(rng, ls) for d, ls in by_dept.items()}
        offered = {}
        for lec in lectures:
            offered.setdefault(lec["crs"]["cd"], []).append(lec)

        # 신청
        enroll_rows = []  # (student, lec, retake)
        for st in students:
            if yr < st["cohort"] or sem_no in st["leave_sems"]:
                continue
            if not regular and rng.random() > 0.10:  # 계절학기는 약 10% 만
                continue
            max_crdt = (21 if (st["last_gpa"] or 0) >= 4.0 else 18) if regular else 6
            w.row("semester_maximum_credit", st["no"], sem_no, max_crdt)
            target = rng.randint(5, 7) if regular else rng.randint(1, 2)
            used_blocks, taken_crs, credits, chosen = set(), set(), 0.0, []

            def try_add(lec, retake):
                nonlocal credits
                crs = lec["crs"]
                if (lec["block"] in used_blocks or crs["cd"] in taken_crs or len(lec["students"]) >= lec["cap"]
                        or credits + crs["crdt"] > max_crdt):
                    return False
                prev = st["history"].get(crs["cd"])
                if prev is not None and (not retake or prev not in RETAKE_OK):
                    return False
                used_blocks.add(lec["block"])
                taken_crs.add(crs["cd"])
                credits += crs["crdt"]
                lec["students"].append(st)
                chosen.append((lec, prev is not None))
                return True

            # 재수강 후보부터(약 5%)
            for cd, g in st["history"].items():
                if g in RETAKE_OK and cd in offered and rng.random() < 0.15:
                    try_add(rng.choice(offered[cd]), True)
            tries = 0
            while len(chosen) < target and tries < 40:
                tries += 1
                picker = dept_picker.get(st["dept"]) if rng.random() < 0.7 else all_picker
                try_add((picker or all_picker).pick(rng), False)
            if regular and chapels and rng.random() < 0.9:
                ch = next(c for c in chapels if len(c["students"]) < c["cap"])
                ch["students"].append(st)
                chosen.append((ch, False))
            for lec, retake in chosen:
                enroll_rows.append((st, lec, retake))
            if regular and chosen:
                st["regular_cnt"] += 1

        # 강의 행과 시간
        for lec in lectures + chapels:
            pf = lec["crs"]["eval"] == "PASS_FAIL"
            weights = (None,) * 4 if pf else (30, 40, 20, 10)
            w.row("lecture", lec["no"], lec["crs"]["cd"], sem_no, lec["prof"], lec["sect"], lec["cap"],
                  len(lec["students"]), "OPEN", "N", "Y" if lec["exempt"] else "N", f"plan_{lec['no']}.pdf",
                  *weights, "CONFIRMED", ADMIN, cnfm_dtm)
            slots = [CHAPEL_SLOT] if lec["block"] is None else BLOCKS[lec["block"]]
            for dow, s, e in slots:
                w.row("lecture_time_slot", lec["no"], dow, s + ":00", e + ":00",
                      "ONLINE" if lec["online"] else "OFFLINE", lec["room"])

        # 성적: 강의별로 점수를 내고 상대평가를 맞춘다
        letters = {}
        for lec in lectures + chapels:
            members = lec["students"]
            if lec["crs"]["eval"] == "PASS_FAIL":
                for st in members:
                    letters[(st["no"], lec["no"])] = ("F" if rng.random() < 0.05 else "P", None)
                continue
            diff = rng.gauss(0, 0.5)
            scored = []
            for st in members:
                base = 84 + 7 * st["ability"] - 4 * diff
                comp = [clamp(round(base + rng.gauss(0, 7))) for _ in range(4)]
                tot = round((comp[0] * 30 + comp[1] * 40 + comp[2] * 20 + comp[3] * 10) / 100)
                scored.append([st, comp, tot, grade_of(tot)])
            scored.sort(key=lambda x: -x[2])
            if len(scored) >= 10 and not lec["exempt"]:
                a_max, ab_max = int(len(scored) * 0.4), int(len(scored) * 0.8)
                for i, row in enumerate(scored):
                    g = row[3]
                    if g.startswith("A") and i >= a_max:
                        row[3] = "B+"
                    if row[3][0] in "AB" and i >= ab_max:
                        row[3] = "C+"
            for st, comp, tot, g in scored:
                letters[(st["no"], lec["no"])] = (g, (comp, tot))

        # 신청·성적 행. 약 2% 는 정정 기간에 취소한 기록(성적 없음, 신청인원에도 없음)
        enr_s, enr_e = times["ENROLL"]
        for st, lec, retake in enroll_rows:
            g, scores = letters[(st["no"], lec["no"])]
            if retake and g in ORDER and ORDER.index(g) < ORDER.index("A-"):
                g = "A-"  # 재수강 성적 상한
            dtm = enr_s + timedelta(seconds=rng.randint(0, int((enr_e - enr_s).total_seconds()) - 1))
            w.row("enrollment", st["no"], lec["no"], "ENROLLED", "Y" if retake else "N", "STUDENT", dtm, None)
            if scores:
                (m, f, h, a), tot = scores
                w.row("grade", st["no"], lec["no"], m, f, h, a, tot, g)
            else:
                w.row("grade", st["no"], lec["no"], None, None, None, None, None, g)
            if lec["crs"]["cmpl"] != "CHAPEL":
                st["history"][lec["crs"]["cd"]] = g
            total_enrollments += 1
        cancel_cands = [lec for lec in lectures if len(lec["students"]) < lec["cap"]]
        for st in rng.sample(students, int(len(students) * 0.02)):
            if yr < st["cohort"] or sem_no in st["leave_sems"] or not cancel_cands:
                continue
            lec = rng.choice(cancel_cands)
            if st in lec["students"] or lec["crs"]["cd"] in st["history"]:
                continue
            adj_s = times["ADJUST"][0]
            w.row("enrollment", st["no"], lec["no"], "CANCELLED", "N", "STUDENT", enr_s + timedelta(hours=1),
                  adj_s + timedelta(hours=rng.randint(1, 48)))

        # 직전 정규학기 평점(다음 학기 최대학점)
        if regular:
            sem_rows = {}
            for st, lec, retake in enroll_rows:
                if lec["crs"]["eval"] == "GRADE":
                    g = letters[(st["no"], lec["no"])][0]
                    if retake and ORDER.index(g) < ORDER.index("A-"):
                        g = "A-"
                    c, p = sem_rows.get(st["no"], (0.0, 0.0))
                    sem_rows[st["no"]] = (c + lec["crs"]["crdt"], p + lec["crs"]["crdt"] * POINT[g])
            for st in students:
                if st["no"] in sem_rows:
                    c, p = sem_rows[st["no"]]
                    st["last_gpa"] = p / c

        print(f"  학기 {sem_no} ({yr} {sem_type}): 강의 {len(lectures) + len(chapels)}, 신청 {len(enroll_rows)}")

    # 학생 행: 학년 = min(4, 다닌 정규학기 ÷ 2 + 1), 학적상태
    for st in students:
        nm = rng.choice(SURNAMES) + rng.choice(GIVEN)
        w.row("user_account", st["no"], PWD_HASH, nm, f"s{st['no']}@crs.ac.kr", "STUDENT", "ACTIVE")
        w.row("student", st["no"], st["dept"], min(4, st["regular_cnt"] // 2 + 1),
              "ON_LEAVE" if st["on_leave"] else "ATTENDING")

    w.close()
    print(f"{args.scale} → {out}")
    for t, c in w.counts.items():
        print(f"  {t:<20} {c:>10,}")


if __name__ == "__main__":
    main()
