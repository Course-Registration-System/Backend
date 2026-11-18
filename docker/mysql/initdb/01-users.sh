#!/bin/sh
# DB 계정을 만든다. 비밀번호는 환경 변수(.env)에서 받고 이 파일에는 적지 않는다.
# MySQL 공식 이미지의 /docker-entrypoint-initdb.d 에 넣어 컨테이너 첫 기동 때 한 번 실행한다.
# 테이블과 권한은 백엔드가 시작할 때 Flyway가 migrator 계정으로 적용한다.
#   V1__init_schema.sql(= schema.sql) → R__grants.sql(= grants.sql)
#
# migrator : Flyway 전용. crs 데이터베이스의 DDL과 GRANT를 한다.
# app      : 백엔드 런타임. 권한은 grants.sql이 준다(DDL 없음).
# readonly : 실험·분석 스크립트. 권한은 grants.sql이 준다(SELECT만).
#
# 필요한 환경 변수: MYSQL_ROOT_PASSWORD, MYSQL_DATABASE, MIGRATOR_DB_PASSWORD, APP_DB_PASSWORD, READONLY_DB_PASSWORD
# 비밀번호에 작은따옴표(')는 쓰지 않는다.
set -eu

: "${MYSQL_DATABASE:?MYSQL_DATABASE가 필요하다}"
: "${MIGRATOR_DB_PASSWORD:?MIGRATOR_DB_PASSWORD가 필요하다}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD가 필요하다}"
: "${READONLY_DB_PASSWORD:?READONLY_DB_PASSWORD가 필요하다}"

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'migrator'@'%' IDENTIFIED BY '${MIGRATOR_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'app'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'readonly'@'%' IDENTIFIED BY '${READONLY_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO 'migrator'@'%' WITH GRANT OPTION;
SQL
