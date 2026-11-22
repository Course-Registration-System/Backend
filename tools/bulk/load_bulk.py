"""gen_bulk.py 가 만든 CSV 를 MySQL 컨테이너의 별도 DB 에 적재하고 verify.sql 로 검사한다.

사용: python -X utf8 tools/bulk/load_bulk.py --dir build/bulk/S [--container crs-mysql] [--database crs_bulk]
     root 비밀번호는 --password, 환경 변수 MYSQL_ROOT_PASSWORD, 프로젝트 루트 .env 순서로 찾는다.

순서
1. DB 를 지우고 다시 만든 뒤 V1__init_schema.sql 로 테이블을 만든다(개발 DB crs 는 건드리지 않는다)
2. CSV 를 컨테이너의 /var/lib/mysql-files/bulk 로 복사한다(MySQL 이미지의 secure_file_priv 위치)
3. FK 검사를 끄고 LOAD DATA 로 넣은 뒤 다시 켜고, 통계를 갱신한다(ANALYZE TABLE)
4. verify.sql 의 위반 건수를 출력한다. 하나라도 0 이 아니면 종료 코드 1
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
VERIFY = Path(__file__).resolve().parent / "verify.sql"
REMOTE_DIR = "/var/lib/mysql-files/bulk"


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

    loads = ["SET FOREIGN_KEY_CHECKS = 0;", "SET UNIQUE_CHECKS = 0;"]
    for table, columns in TABLES.items():
        loads.append(
            f"LOAD DATA INFILE '{REMOTE_DIR}/{table}.csv' INTO TABLE {table} CHARACTER SET utf8mb4 "
            f"FIELDS TERMINATED BY ',' LINES TERMINATED BY '\\n' ({', '.join(columns)});")
    loads += ["SET UNIQUE_CHECKS = 1;", "SET FOREIGN_KEY_CHECKS = 1;"]
    # 적재 직후에는 통계가 비어 있어 검사 SQL 이 나쁜 실행 계획을 고른다
    loads.append(f"ANALYZE TABLE {', '.join(TABLES)};")
    mysql(args.container, pw, "\n".join(loads), db)
    counts = mysql(args.container, pw, " UNION ALL ".join(
        f"SELECT '{t}', COUNT(*) FROM {t}" for t in TABLES) + ";", db)
    print(f"적재 완료 ({time.time() - started:.0f}초)")
    for line in counts.strip().splitlines():
        t, n = line.split("\t")
        print(f"  {t:<20} {int(n):>10,}")

    started = time.time()
    result = mysql(args.container, pw, VERIFY.read_text(encoding="utf-8"), db)
    print(f"검사 ({time.time() - started:.0f}초)")
    failed = 0
    for line in result.strip().splitlines():
        chk, n = line.rsplit("\t", 1)
        failed += int(n) != 0
        print(f"  {'OK ' if int(n) == 0 else 'NG '} {chk}: {n}")
    subprocess.run(["docker", "exec", args.container, "rm", "-rf", REMOTE_DIR], check=True)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
