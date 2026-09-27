# 실물 표본 출처

사람이 이전에 만든 코드·SQL·JSP 를 테스트 입력으로 쓴다(PLAN 4장, V-1). 표본 실물은 저장소 밖 `C:/workspace/toolbox-corpus`(`TOOLBOX_CORPUS`) — 이 저장소에는 레시피 `scripts/corpus-fetch.sh`, 이 문서, 지문 `MANIFEST` 만 있다. 표본을 재배포하지 않으므로 라이선스는 출처 확인용으로 적는다.

받기는 고정 태그·커밋만, `core.autocrlf=false` 로 바이트 그대로. 파일 수는 `MANIFEST` 가 정본이다.

| 이름 | 저장소 | 태그·커밋 | 라이선스 | 잘라 온 것 | 쓰는 행 |
|---|---|---|---|---|---|
| egov | eGovFramework/egovframe-common-components | `v5.0.6` | Apache-2.0 | `src/main/java` Java · `webapp` JSP·JS·CSS · mapper XML · properties · `script/{ddl,dml,comment}/<8방언>` · `src/script` 모듈 SQL | V-2·V-3·V-4·V-5·V-6 |
| egov-prev | 같은 저장소 | `v5.0.5` | Apache-2.0 | Java · JSP · mapper XML · `script` SQL | V-6(폴더 비교 두 판) · V-9 |
| egov-portal | eGovFramework/egovframe-portal-site-template | `249e8199` | Apache-2.0 | JSP · Java | V-2·V-3 |
| egov-enterprise | eGovFramework/egovframe-enterprise-business-template | `ab78fc95` | Apache-2.0 | JSP · Java | V-2·V-3 |
| egov-homepage | eGovFramework/egovframe-simple-homepage-template | `9dacccbc` | Apache-2.0 | JSP · Java | V-2·V-3 |
| egov-react | eGovFramework/egovframe-template-simple-react | `957d9b1b` | Apache-2.0 | `src` JSX·JS | V-2 |
| jspwiki | apache/jspwiki | `2.12.5` | Apache-2.0 | JSP · `src/main/java` | V-2·V-3 |
| roller | apache/roller | `a944dcb5` | Apache-2.0 | JSP · `src/main/java` | V-2·V-3 |
| struts | apache/struts | `3e428e43` | Apache-2.0 | JSP · `core/src/main/java` | V-2·V-3 |
| commons-lang | apache/commons-lang | `rel/commons-lang-3.20.0` | Apache-2.0 | `src/main/java` | V-2 |
| commons-io | apache/commons-io | `rel/commons-io-2.22.0` | Apache-2.0 | `src/main/java` | V-2 |
| commons-collections | apache/commons-collections | `rel/commons-collections-4.6.0` | Apache-2.0 | `src/main/java` | V-2 |
| db-samples | oracle-samples/db-sample-schemas | `v23.3` | MIT(LICENSE.txt 본문, GitHub 표시 MIT) | HR·OE·SH·CO `*.sql` | V-4·V-6·V-8·V-9 |
| chinook | lerocha/chinook-database | `v1.4.5` | MIT(LICENSE.md 본문 — GitHub 은 NOASSERTION 으로 표시) | `DataSources/*.sql` — Oracle·MySQL·PG·SQL Server·DB2·SQLite 방언 | V-4·V-9·V-10 |

**둘째 JSP·Java 출처**(V-1 이 고름): eGov 공통컴포넌트만으로는 JSP 가 747 이라 4장 목표(~1,500)에 모자라다. 같은 발주처 결의 eGov 템플릿 셋(274)과 결이 다른 Apache 웹앱 셋(jspwiki·roller·struts, 309)을 더했다 — 합 ~1,330. 모두 Apache-2.0.

**4장 표와 다른 것**: 툴별 DDL 덤프(DBeaver·Toad·tbAdmin 머리) 검색 대신 chinook(한 스키마를 방언 여섯으로 떠 둔 것)을 먼저 넣었다 — 방언 축은 chinook·eGov 8방언이 채운다. 툴 덤프는 V-4 가 더한다. eGov 공통컴포넌트 DDL 은 MSSQL 이 없다(altibase·cubrid·goldilocks·maria·mysql·oracle·postgres·tibero).

행마다 더하는 출처: V-2(yaml·scss/less·sh·bat) · V-4(공공데이터 CSV·툴 덤프) · V-7(JSON·MDN 표·한글 텍스트·EUC-KR).
