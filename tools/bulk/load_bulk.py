"""gen_bulk.py 가 만든 CSV 를 MySQL 컨테이너의 별도 DB 에 적재하고 seed-checks.sql 로 검사한다.

사용: python -X utf8 tools/bulk/load_bulk.py --dir build/bulk/S [--container crs-mysql] [--database crs_bulk]
     root 비밀번호는 --password, 환경 변수 MYSQL_ROOT_PASSWORD, 프로젝트 루트 .env 순서로 찾는다.

순서
1. DB 를 지우고 다시 만든 뒤 V1__init_schema.sql 로 테이블을 만든다(개발 DB crs 는 건드리지 않는다)
2. CSV 를 컨테이너의 /var/lib/mysql-files/bulk 로 복사한다(MySQL 이미지의 secure_file_priv 위치)
3. 부모 테이블부터(TABLES 순서) LOAD DATA 로 넣고 통계를 갱신한다(ANALYZE TABLE).
   FK·UNIQUE 검사를 끄지 않으므로 고아 행이나 키 중복이 있으면 적재가 실패한다
4. 위반 건수를 출력한다. 하나라도 0 이 아니면 종료 코드 1
   - seed-checks.sql(업무 규칙, 시드와 같은 검사) 중 모든 시점의 기대값이 0 인 것. 시점별 검사와 시드 전용 검사는 건너뛴다
   - 대량 데이터 검사(BULK_CHECKS): 계절학기 성적입력·확정이 다음 정규학기 장바구니 전에 끝나는지,
     학기별 최대학점이 MaxCreditRepository 규칙으로 다시 계산한 값과 같은지, 학생당 재수강 과목이 10개 이하인지
"""
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_bulk import TABLES  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
SCHEMA = REPO / "src/main/resources/db/migration/V1__init_schema.sql"
SEED_CHECKS = REPO / "src/test/resources/seed/seed-checks.sql"
# 시드의 특정 학생 사례를 보는 검사. 대량 데이터의 같은 학번은 다른 학생이라 건너뛴다
SEED_ONLY = {
    "2026-2 평점: 20240001은 정확히 4.0, 20240002는 4.0 미만",
}
REMOTE_DIR = "/var/lib/mysql-files/bulk"

SEM_ORD = "(yr * 10 + FIELD(sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER'))"
GRADE_POINT = ("CASE g.ltr_grd WHEN 'A+' THEN 4.5 WHEN 'A0' THEN 4.3 WHEN 'A-' THEN 4.0 WHEN 'B+' THEN 3.5"
               " WHEN 'B0' THEN 3.3 WHEN 'B-' THEN 3.0 WHEN 'C+' THEN 2.5 WHEN 'C0' THEN 2.3 WHEN 'C-' THEN 2.0"
               " WHEN 'D+' THEN 1.5 WHEN 'D0' THEN 1.3 WHEN 'D-' THEN 1.0 ELSE 0 END")
# seed-checks.sql 은 시드의 특정 시점만 보므로, 모든 학기에 걸친 규칙은 여기서 따로 본다. (이름, 위반 건수를 내는 SQL)
BULK_CHECKS = [
    ("계절학기 성적입력 종료·성적 확정이 다음 정규학기 장바구니 시작 이후(ACA-01)", f"""
WITH ord AS (SELECT sem_no, sem_type, {SEM_ORD} AS o FROM semester),
nxt AS (
  SELECT s.sem_no,
         (SELECT MIN(c.start_dtm) FROM ord r JOIN academic_schedule c ON c.sem_no = r.sem_no AND c.schd_type = 'CART'
           WHERE r.sem_type IN ('FIRST', 'SECOND') AND r.o > s.o) AS cart_start
  FROM ord s WHERE s.sem_type IN ('SUMMER', 'WINTER'))
SELECT (SELECT COUNT(*) FROM nxt JOIN academic_schedule a ON a.sem_no = nxt.sem_no AND a.schd_type = 'GRADE'
         WHERE a.end_dtm >= nxt.cart_start)
     + (SELECT COUNT(*) FROM nxt JOIN lecture l ON l.sem_no = nxt.sem_no WHERE l.cnfm_dtm >= nxt.cart_start);
"""),
    # 정규학기: 대상 학기 앞에 마지막으로 다닌 정규학기 평점(다닌 정규학기가 2개면 둘을 합침) 4.0 이상 21, 아니면 18.
    # 계절학기: 재학 6, 휴학 3. 과거 학기의 학적상태는 남지 않으므로 직전 정규학기에 다녔으면 재학으로 본다.
    # 그 학기에 신청한 학생의 행이 없어도 위반으로 센다
    ("학기별 최대학점이 MaxCreditRepository 규칙으로 다시 계산한 값과 다름", f"""
WITH ord AS (SELECT sem_no, sem_type, {SEM_ORD} AS o FROM semester),
term AS (
  SELECT e.std_no, s.o,
         SUM(CASE WHEN c.eval_type = 'GRADE' THEN c.crdt END) AS crd,
         SUM(CASE WHEN c.eval_type = 'GRADE' THEN c.crdt * ({GRADE_POINT}) END) AS pts
  FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no JOIN ord s ON s.sem_no = l.sem_no
  JOIN course c ON c.crs_cd = l.crs_cd JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
  WHERE e.enrl_stat = 'ENROLLED' AND l.grd_stat = 'CONFIRMED' AND s.sem_type IN ('FIRST', 'SECOND')
  GROUP BY e.std_no, s.o),
prior AS (
  SELECT m.std_no, m.sem_no, t.o, t.crd, t.pts,
         COUNT(t.o) OVER w AS term_cnt, MAX(t.o) OVER w AS last_o
  FROM semester_maximum_credit m JOIN ord s ON s.sem_no = m.sem_no
  JOIN term t ON t.std_no = m.std_no AND t.o < s.o
  WHERE s.sem_type IN ('FIRST', 'SECOND')
  WINDOW w AS (PARTITION BY m.std_no, m.sem_no)),
gpa AS (
  SELECT std_no, sem_no, SUM(crd) AS crd, SUM(pts) AS pts FROM prior
  WHERE term_cnt = 2 OR o = last_o GROUP BY std_no, sem_no),
prev_regular AS (
  SELECT s.sem_no, (SELECT MAX(r.o) FROM ord r WHERE r.sem_type IN ('FIRST', 'SECOND') AND r.o < s.o) AS prev_o
  FROM ord s WHERE s.sem_type IN ('SUMMER', 'WINTER')),
expect AS (
  SELECT m.std_no, m.sem_no, m.max_crdt,
         CASE WHEN s.sem_type IN ('FIRST', 'SECOND')
              THEN CASE WHEN p.crd > 0 AND p.pts >= 4.0 * p.crd THEN 21 ELSE 18 END
              WHEN EXISTS (SELECT 1 FROM term t WHERE t.std_no = m.std_no AND t.o = pr.prev_o) THEN 6
              ELSE 3 END AS mx
  FROM semester_maximum_credit m JOIN ord s ON s.sem_no = m.sem_no
  LEFT JOIN gpa p ON p.std_no = m.std_no AND p.sem_no = m.sem_no
  LEFT JOIN prev_regular pr ON pr.sem_no = m.sem_no)
SELECT (SELECT COUNT(*) FROM expect WHERE max_crdt <> mx)
     + (SELECT COUNT(*) FROM (SELECT DISTINCT e.std_no, l.sem_no FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no
                              WHERE e.enrl_stat = 'ENROLLED') x
         LEFT JOIN semester_maximum_credit m ON m.std_no = x.std_no AND m.sem_no = x.sem_no
         WHERE m.std_no IS NULL);
"""),
    ("재수강 과목이 10개 넘는 학생(채플 제외, 같은 과목 여러 번은 1)", """
SELECT COUNT(*) FROM (
  SELECT e.std_no FROM enrollment e JOIN lecture l ON l.lec_no = e.lec_no JOIN course c ON c.crs_cd = l.crs_cd
  WHERE e.enrl_stat = 'ENROLLED' AND e.retake_yn = 'Y' AND c.cmpl_type <> 'CHAPEL'
  GROUP BY e.std_no HAVING COUNT(DISTINCT l.crs_cd) > 10) x;
"""),
]


def root_password(arg):
    if arg:
        return arg
    if os.environ.get("MYSQL_ROOT_PASSWORD"):
        return os.environ["MYSQL_ROOT_PASSWORD"]
    env = REPO / ".env"
    if env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.startswith("MYSQL_ROOT_PASSWORD="):
                return line.split("=", 1)[1].strip()
    sys.exit("root 비밀번호가 없습니다. --password 또는 MYSQL_ROOT_PASSWORD 를 주세요.")


def mysql(container, password, sql, database=None, table=False):
    cmd = ["docker", "exec", "-i", "-e", f"MYSQL_PWD={password}", container, "mysql", "-uroot",
           "--default-character-set=utf8mb4"]
    if not table:
        cmd.append("-N")
    if database:
        cmd.append(database)
    result = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True)
    if result.returncode != 0:
        sys.exit(result.stderr.decode("utf-8", "replace"))
    return result.stdout.decode("utf-8", "replace")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dir", type=Path, required=True)
    parser.add_argument("--container", default="crs-mysql")
    parser.add_argument("--database", default="crs_bulk")
    parser.add_argument("--password")
    args = parser.parse_args()
    if args.database == "crs":
        sys.exit("개발 DB(crs)에는 적재하지 않습니다. 다른 이름을 주세요.")
    pw = root_password(args.password)
    db = args.database

    started = time.time()
    mysql(args.container, pw, f"DROP DATABASE IF EXISTS {db}; CREATE DATABASE {db};")
    mysql(args.container, pw, SCHEMA.read_text(encoding="utf-8"), db)
    print(f"스키마 생성: {db}")

    subprocess.run(["docker", "exec", args.container, "rm", "-rf", REMOTE_DIR], check=True)
    subprocess.run(["docker", "cp", str(args.dir.resolve()), f"{args.container}:{REMOTE_DIR}"], check=True)

    loads = []
    for table, columns in TABLES.items():
        loads.append(
            f"LOAD DATA INFILE '{REMOTE_DIR}/{table}.csv' INTO TABLE {table} CHARACTER SET utf8mb4 "
            f"FIELDS TERMINATED BY ',' LINES TERMINATED BY '\\n' ({', '.join(columns)});")
    # 적재 직후에는 통계가 비어 있어 검사 SQL 이 나쁜 실행 계획을 고른다
    loads.append(f"ANALYZE TABLE {', '.join(TABLES)};")
    mysql(args.container, pw, "\n".join(loads), db)
    counts = mysql(args.container, pw, " UNION ALL ".join(
        f"SELECT '{t}', COUNT(*) FROM {t}" for t in TABLES) + ";", db)
    print(f"적재 완료 ({time.time() - started:.0f}초)")
    for line in counts.strip().splitlines():
        t, n = line.split("\t")
        print(f"  {t:<20} {int(n):>10,}")

    failed = 0
    started = time.time()
    print("업무 규칙 (seed-checks.sql)")
    for name, sql in seed_checks():
        t = time.time()
        n = int(mysql(args.container, pw, "SET @now = NOW();\n" + sql, db).strip())
        failed += report(f"{name} ({time.time() - t:.0f}초)", n)
    print("대량 데이터 검사")
    for name, sql in BULK_CHECKS:
        t = time.time()
        n = int(mysql(args.container, pw, sql, db).strip())
        failed += report(f"{name} ({time.time() - t:.0f}초)", n)
    print(f"검사 {time.time() - started:.0f}초")
    subprocess.run(["docker", "exec", args.container, "rm", "-rf", REMOTE_DIR], check=True)
    sys.exit(1 if failed else 0)


def seed_checks():
    """seed-checks.sql 에서 모든 시점의 기대값이 0 인 검사만 고른다. 시점별 검사와 시드 전용 검사는 건너뛴다."""
    for block in SEED_CHECKS.read_text(encoding="utf-8").split("\n-- CHECK ")[1:]:
        header, sql = block.split("\n", 1)
        name, expected = (part.strip() for part in header.rsplit("|", 1))
        if expected == "0" and name not in SEED_ONLY:
            yield name, sql.strip()


def report(name, n):
    print(f"  {'OK' if n == 0 else 'NG'}  {name}: {n}")
    return n != 0

if __name__ == "__main__":
    main()
