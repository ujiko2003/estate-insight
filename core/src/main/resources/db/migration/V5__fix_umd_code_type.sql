-- =========================================================
-- V5__fix_umd_code_type.sql
-- apt_complex.umd_code 컬럼: CHAR(3) -> VARCHAR(5)
--
-- 이유: V1은 umd_code를 3자리로 가정했지만, 실제 API 응답의
--       umdCd는 5자리다(예: 대치동 10600). 강남구 2026-09 응답
--       125건 모두 5자리였다. 그대로 두면 1-6에서 apt_complex에
--       저장할 때 길이 초과로 실패한다.
--       CHAR 대신 VARCHAR를 쓰는 이유는 V4의 sgg_code와 같다
--       (Hibernate 기본 매핑과 일치, CHAR의 공백 패딩 문제 회피).
--
-- 영향: 적용 시점에 apt_complex는 0건이라 데이터 변환이 없다.
-- =========================================================

ALTER TABLE apt_complex   ALTER COLUMN umd_code TYPE VARCHAR(5);
