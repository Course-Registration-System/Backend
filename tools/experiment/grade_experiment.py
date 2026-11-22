"""성적 조회 성능 실험(모듈 A 설계 3-3). 대량 데이터를 넣은 DB 에서 쿼리 × 비교군을 재서 CSV 로 남긴다.

준비: 실험 전용 컨테이너를 띄우고 대량 데이터를 넣는다. 버퍼 풀을 디스크에 저장·복원하지 않고 O_DIRECT 로 OS 캐시를 거치지 않아야
      재시작 직후 측정이 진짜 콜드가 된다.
  docker run -d --name crs-exp -e MYSQL_ROOT_PASSWORD=<비밀번호> mysql:8.4 --innodb-buffer-pool-size=512M \\
      --innodb-buffer-pool-load-at-startup=OFF --innodb-buffer-pool-dump-at-shutdown=OFF --innodb-flush-method=O_DIRECT
  python -X utf8 tools/bulk/gen_bulk.py --scale M
  python -X utf8 tools/bulk/load_bulk.py --dir build/bulk/M --container crs-exp
실행: python -X utf8 tools/experiment/grade_experiment.py --scale M [--container crs-exp] [--runs 30]
결과: build/experiment/<규모>/ 에 results.csv(응답 시간), extras.csv(쓰기 비용·인덱스 크기·갱신 시간·V3), explain/, env.txt

쿼리
  Q1 학생 한 명의 전체 성적표(GRD-03 과 같은 SQL)
  Q2 한 학기 전체 학생의 학기 평점(최대학점 산정의 입력)
  Q3 학과·학기별 학기 평점 분포(전체 집계)
비교군
  V0  grade(lec_no) 인덱스 없음
  V1  현재 스키마(ix_grade_lec = grade(lec_no))
  V1c 커버링 인덱스 grade(lec_no, ltr_grd)
  V2  V1 + 학생·학기별 평점 요약 테이블(Q2·Q3 만. Q1 의 누적 평점은 재수강을 빼야 해서 요약 테이블로 못 낸다)
  V3  최대학점 산정: 학생별 SELECT·INSERT 반복 vs INSERT ... SELECT 한 문장
"""
import argparse
import csv
import math
import statistics
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "bulk"))
from load_bulk import root_password  # noqa: E402

REPO = Path(__file__).resolve().parents[2]

# GradePoint.sql 과 MaxCreditRepository.SEM_ORD 를 그대로 옮긴 식. 앱과 같은 SQL 을 재야 하므로 바꾸면 같이 바꾼다
GP = ("CASE g.ltr_grd WHEN 'A+' THEN 4.5 WHEN 'A0' THEN 4.3 WHEN 'A-' THEN 4.0 WHEN 'B+' THEN 3.5 WHEN 'B0' THEN 3.3"
      " WHEN 'B-' THEN 3.0 WHEN 'C+' THEN 2.5 WHEN 'C0' THEN 2.3 WHEN 'C-' THEN 2.0 WHEN 'D+' THEN 1.5"
      " WHEN 'D0' THEN 1.3 WHEN 'D-' THEN 1.0 ELSE 0 END")
SEM_ORD = "(s.yr * 10 + FIELD(s.sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER'))"

STUDENT = "20240001"  # 2024 입학, 가장 많은 학기를 다닌 학생
SEMESTER = 8  # 2026-2, 마지막 정규학기

# 등급제·확정 강의의 학생·학기별 학점 합과 학점×평점 합. Q2·Q3·V2·V3 가 모두 이 묶음을 쓴다
GRADED = f"""
  FROM lecture l
  JOIN course c ON c.crs_cd = l.crs_cd
  JOIN grade g ON g.lec_no = l.lec_no
 WHERE l.grd_stat = 'CONFIRMED' AND c.eval_type = 'GRADE' AND g.ltr_grd IS NOT NULL"""

Q1 = f"""
SELECT s.sem_no, s.yr, s.sem_type, l.lec_no, l.crs_cd, c.crs_nm, c.crdt, c.cmpl_type, c.eval_type,
       g.ltr_grd, e.retake_yn,
       CASE WHEN c.eval_type = 'GRADE' THEN {GP} END AS grade_point,
       CASE WHEN c.cmpl_type = 'CHAPEL' THEN 1
            WHEN ROW_NUMBER() OVER (PARTITION BY l.crs_cd ORDER BY {SEM_ORD} DESC) = 1 THEN 1
            ELSE 0 END AS counted
  FROM enrollment e
  JOIN lecture l ON l.lec_no = e.lec_no
  JOIN semester s ON s.sem_no = l.sem_no
  JOIN course c ON c.crs_cd = l.crs_cd
  JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
 WHERE e.std_no = '{STUDENT}'
   AND e.enrl_stat = 'ENROLLED'
   AND l.grd_stat = 'CONFIRMED'
   AND g.ltr_grd IS NOT NULL
 ORDER BY {SEM_ORD}, c.crs_nm, l.sect_no"""

Q2 = f"SELECT g.std_no, SUM(c.crdt * {GP}) / SUM(c.crdt) AS gpa{GRADED} AND l.sem_no = {SEMESTER} GROUP BY g.std_no"

Q3 = f"""
SELECT st.dept_no, x.sem_no, FLOOR(x.gpa * 2) / 2 AS bucket, COUNT(*) AS students
  FROM (SELECT g.std_no, l.sem_no, SUM(c.crdt * {GP}) / SUM(c.crdt) AS gpa{GRADED} GROUP BY g.std_no, l.sem_no) x
  JOIN student st ON st.std_no = x.std_no
 GROUP BY st.dept_no, x.sem_no, bucket"""

Q2_SUMMARY = f"SELECT std_no, pt_sum / crdt_sum AS gpa FROM semester_gpa WHERE sem_no = {SEMESTER}"
Q3_SUMMARY = """
SELECT st.dept_no, x.sem_no, FLOOR(x.pt_sum / x.crdt_sum * 2) / 2 AS bucket, COUNT(*) AS students
  FROM semester_gpa x
  JOIN student st ON st.std_no = x.std_no
 GROUP BY st.dept_no, x.sem_no, bucket"""

SUMMARY_SELECT = f"SELECT g.std_no, l.sem_no, SUM(c.crdt) AS crdt_sum, SUM(c.crdt * {GP}) AS pt_sum{GRADED}"

QUERIES = {
    "V0": {"Q1": Q1, "Q2": Q2, "Q3": Q3},
    "V1": {"Q1": Q1, "Q2": Q2, "Q3": Q3},
    "V1c": {"Q1": Q1, "Q2": Q2, "Q3": Q3},
    "V2": {"Q2": Q2_SUMMARY, "Q3": Q3_SUMMARY},
}
GRADE_INDEXES = {"V0": [], "V1": ["ix_grade_lec (lec_no)"], "V1c": ["ix_grade_lec_grd (lec_no, ltr_grd)"]}


class Db:
    def __init__(self, container, pw, database):
        self.container, self.pw, self.database = container, pw, database

    def run(self, sql, raw=False):
        cmd = ["docker", "exec", "-i", "-e", f"MYSQL_PWD={self.pw}", self.container, "mysql", "-uroot", "-N",
               "--comments", "--default-character-set=utf8mb4", self.database]
        if raw:
            cmd.insert(cmd.index("-N"), "-r")
        result = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True)
        if result.returncode != 0:
            sys.exit(result.stderr.decode("utf-8", "replace"))
        return result.stdout.decode("utf-8", "replace")

    def rows(self, sql):
        return [line.split("\t") for line in self.run(sql).splitlines() if line]

    def elapsed(self, sql):
        """여러 문장을 한 세션에서 돌린 시간(ms). docker exec 시간이 흔들려서 세션 안에서 NOW(6) 차이로 잰다."""
        out = self.run(f"SET @t0 = NOW(6);\n{sql}\nSELECT TIMESTAMPDIFF(MICROSECOND, @t0, NOW(6)) / 1000;")
        return float(out.split()[-1])

    def restart(self):
        """버퍼 풀을 비운다. 시작할 때 덤프를 읽지 않게 띄웠으므로 재시작 직후가 콜드다."""
        subprocess.run(["docker", "restart", self.container], check=True, capture_output=True)
        for _ in range(60):
            ok = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={self.pw}", self.container, "mysql", "-uroot",
                                 "-e", "SELECT 1"], capture_output=True)
            if ok.returncode == 0:
                break
            time.sleep(1)
        else:
            sys.exit("MySQL 이 다시 올라오지 않았습니다.")
        self.run("UPDATE performance_schema.setup_consumers SET ENABLED = 'YES'"
                 " WHERE NAME = 'events_statements_history_long';")


def set_variant(db, variant):
    """grade 의 보조 인덱스를 비교군에 맞게 바꾸고 통계를 다시 모은다. V2 는 V1 인덱스 위에 요약 테이블을 얹는다."""
    wanted = GRADE_INDEXES["V1" if variant == "V2" else variant]
    have = [r[0] for r in db.rows("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS"
                                  " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grade'"
                                  " AND INDEX_NAME LIKE 'ix_grade_lec%'")]
    changes = [f"DROP INDEX {name}" for name in have] + [f"ADD INDEX {spec}" for spec in wanted]
    if changes:
        db.run(f"ALTER TABLE grade {', '.join(changes)};")
    db.run("ANALYZE TABLE grade, lecture, course, enrollment, student;")


def measure(db, variant, query_id, sql, runs):
    """콜드 1회 + 웜 runs 회. 서버가 잰 문장 시간(performance_schema, 네트워크·클라이언트 제외)을 쓴다."""
    db.restart()
    tag = f"/* exp {variant} {query_id} */"
    db.run("TRUNCATE performance_schema.events_statements_history_long;")
    db.run(f"{tag} {sql};\n" * (runs + 1))
    timings = db.rows(f"""
        SELECT TIMER_WAIT / 1e9, ROWS_EXAMINED, ROWS_SENT FROM performance_schema.events_statements_history_long
         WHERE SQL_TEXT LIKE '{tag}%' ORDER BY EVENT_ID""")
    if len(timings) != runs + 1:
        sys.exit(f"{variant} {query_id}: 측정값이 {len(timings)}개뿐입니다(기대 {runs + 1}).")
    ms = [float(t[0]) for t in timings]
    warm = sorted(ms[1:])
    return dict(cold_ms=round(ms[0], 2), median_ms=round(statistics.median(warm), 2),
                p95_ms=round(warm[math.ceil(0.95 * len(warm)) - 1], 2),
                rows_examined=timings[-1][1], rows_sent=timings[-1][2])


def median_elapsed(db, sql, times=3):
    return round(statistics.median(db.elapsed(sql) for _ in range(times)), 1)


def write_cost(db, variant):
    """grade 전체를 인덱스 구성이 같은 빈 테이블에 다시 넣는 시간. 보조 인덱스가 쓰기를 얼마나 늦추는지 본다."""
    db.run("DROP TABLE IF EXISTS grade_w; CREATE TABLE grade_w LIKE grade;")
    took = median_elapsed(db, "TRUNCATE grade_w; INSERT INTO grade_w SELECT * FROM grade;")
    db.run("DROP TABLE grade_w;")
    return took


def index_sizes(db):
    return {name: round(int(pages) * 16 / 1024, 2) for name, pages in db.rows(
        "SELECT index_name, stat_value FROM mysql.innodb_index_stats"
        " WHERE database_name = DATABASE() AND table_name = 'grade' AND stat_name = 'size'")}


def build_summary(db):
    full = median_elapsed(db, f"""
        DROP TABLE IF EXISTS semester_gpa;
        CREATE TABLE semester_gpa (PRIMARY KEY (sem_no, std_no)) {SUMMARY_SELECT} GROUP BY g.std_no, l.sem_no;""")
    # 성적 확정 때 그 학기만 다시 만드는 경우
    one = median_elapsed(db, f"""
        START TRANSACTION;
        DELETE FROM semester_gpa WHERE sem_no = {SEMESTER};
        INSERT INTO semester_gpa {SUMMARY_SELECT} AND l.sem_no = {SEMESTER} GROUP BY g.std_no, l.sem_no;
        COMMIT;""")
    db.run("ANALYZE TABLE semester_gpa;")
    return full, one


def max_credit(db):
    """V3. 결과가 같은지 먼저 확인하고, 둘 다 한 트랜잭션으로 잰다."""
    single = f"""
        START TRANSACTION;
        DELETE FROM exp_max_credit;
        INSERT INTO exp_max_credit
        SELECT g.std_no, IF(SUM(c.crdt * {GP}) >= 4.0 * SUM(c.crdt), 21, 18){GRADED} AND l.sem_no = {SEMESTER}
         GROUP BY g.std_no;
        COMMIT;"""
    students = [r[0] for r in db.rows(f"SELECT DISTINCT g.std_no{GRADED} AND l.sem_no = {SEMESTER}")]
    per_student = "".join(f"""
        SELECT IF(SUM(c.crdt * {GP}) >= 4.0 * SUM(c.crdt), 21, 18) INTO @m{GRADED}
           AND l.sem_no = {SEMESTER} AND g.std_no = '{no}';
        INSERT INTO exp_max_credit VALUES ('{no}', @m);""" for no in students)
    loop = f"START TRANSACTION; DELETE FROM exp_max_credit; {per_student} COMMIT;"

    db.run("DROP TABLE IF EXISTS exp_max_credit;"
           " CREATE TABLE exp_max_credit (std_no CHAR(8) PRIMARY KEY, max_crdt TINYINT NOT NULL);")
    checksum = "SELECT COUNT(*), SUM(max_crdt), BIT_XOR(CRC32(CONCAT(std_no, max_crdt))) FROM exp_max_credit;"
    db.run(single)
    a = db.run(checksum)
    db.run(loop)
    b = db.run(checksum)
    if a != b:
        sys.exit(f"V3 두 방식의 결과가 다릅니다: {a.strip()} / {b.strip()}")
    result = dict(students=len(students), single_ms=median_elapsed(db, single), loop_ms=median_elapsed(db, loop),
                  statements_loop=2 * len(students))
    db.run("DROP TABLE exp_max_credit;")
    return result


def environment(db, scale):
    counts = db.rows("SELECT 'student', COUNT(*) FROM student UNION ALL SELECT 'lecture', COUNT(*) FROM lecture"
                     " UNION ALL SELECT 'enrollment', COUNT(*) FROM enrollment UNION ALL SELECT 'grade', COUNT(*) FROM grade")
    settings = db.rows("SELECT VERSION(), @@innodb_buffer_pool_size / 1024 / 1024, @@innodb_flush_method")[0]
    info = subprocess.run(["docker", "info", "--format", "{{.NCPU}} CPU, {{.MemTotal}} bytes, {{.OperatingSystem}}"],
                          capture_output=True, text=True).stdout.strip()
    lines = [f"scale: {scale}", f"mysql: {settings[0]}", f"buffer_pool_mb: {float(settings[1]):.0f}",
             f"flush_method: {settings[2]}", f"docker: {info}"] + [f"rows.{k}: {v}" for k, v in counts]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--scale", required=True, help="결과 폴더 이름. 적재한 규모와 같게 준다")
    parser.add_argument("--container", default="crs-exp")
    parser.add_argument("--database", default="crs_bulk")
    parser.add_argument("--password")
    parser.add_argument("--runs", type=int, default=30)
    args = parser.parse_args()
    db = Db(args.container, root_password(args.password), args.database)
    out = REPO / "build" / "experiment" / args.scale
    (out / "explain").mkdir(parents=True, exist_ok=True)
    (out / "env.txt").write_text(environment(db, args.scale), encoding="utf-8")

    results, extras = [], []
    for variant, queries in QUERIES.items():
        set_variant(db, variant)
        if variant in GRADE_INDEXES:
            extras.append((variant, "write_all_grades_ms", write_cost(db, variant)))
            for name, mb in index_sizes(db).items():
                extras.append((variant, f"index_mb.{name}", mb))
        if variant == "V2":
            full, one = build_summary(db)
            extras += [("V2", "summary_build_all_ms", full), ("V2", "summary_refresh_one_semester_ms", one)]
        for query_id, sql in queries.items():
            r = measure(db, variant, query_id, sql, args.runs)
            results.append(dict(scale=args.scale, variant=variant, query=query_id, **r))
            (out / "explain" / f"{variant}_{query_id}.txt").write_text(
                db.run(f"EXPLAIN ANALYZE {sql};", raw=True), encoding="utf-8")
            print(f"{variant:4} {query_id}  cold {r['cold_ms']:>9} ms  median {r['median_ms']:>9} ms"
                  f"  p95 {r['p95_ms']:>9} ms  rows_examined {r['rows_examined']}", flush=True)

    set_variant(db, "V1")
    v3 = max_credit(db)
    extras += [("V3", k, v) for k, v in v3.items()]
    print(f"V3   학생 {v3['students']}명  한 문장 {v3['single_ms']} ms  반복 {v3['loop_ms']} ms")
    db.run("DROP TABLE IF EXISTS semester_gpa;")  # 끝나면 현재 스키마(V1)로 되돌린다

    with open(out / "results.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(results[0]))
        w.writeheader()
        w.writerows(results)
    with open(out / "extras.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["variant", "metric", "value"])
        w.writerows(extras)
    print(f"결과: {out}")


if __name__ == "__main__":
    main()
