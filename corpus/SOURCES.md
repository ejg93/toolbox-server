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
| egov35-lib(선택) | egovframe 메이븐 저장소 `https://maven.egovframe.go.kr/maven/` — `corpus/egov35-lib/pom.xml` 레시피 | `egovframework.rte.*:3.5.0`(ptl.mvc·psl.dataaccess·fdl.cmmn·fdl.property) + 전이 의존성 · `javax.servlet-api:3.1.0` · `javax.annotation-api:1.3.2` | Apache-2.0(전자정부 실행환경)·각 jar 라이선스 | jar 35 — 저장소·반입 묶음에 안 넣는다. **MANIFEST 밖**(선택 표본 — 없는 PC 에서 다른 표본 테스트를 막지 않게). `corpus-fetch.sh egov35-lib` 로 이름을 골라야 받는다 | V-17 |
| jspwiki | apache/jspwiki | `2.12.5` | Apache-2.0 | JSP · `src/main/java` | V-2·V-3 |
| roller | apache/roller | `a944dcb5` | Apache-2.0 | JSP · `src/main/java` | V-2·V-3 |
| struts | apache/struts | `3e428e43` | Apache-2.0 | JSP · `core/src/main/java` | V-2·V-3 |
| commons-lang | apache/commons-lang | `rel/commons-lang-3.20.0` | Apache-2.0 | `src/main/java` | V-2 |
| commons-io | apache/commons-io | `rel/commons-io-2.22.0` | Apache-2.0 | `src/main/java` | V-2 |
| commons-collections | apache/commons-collections | `rel/commons-collections-4.6.0` | Apache-2.0 | `src/main/java` | V-2 |
| db-samples | oracle-samples/db-sample-schemas | `v23.3` | MIT(LICENSE.txt 본문, GitHub 표시 MIT) | HR·OE·SH·CO `*.sql` | V-4·V-6·V-8·V-9 |
| chinook | lerocha/chinook-database | `v1.4.5` | MIT(LICENSE.md 본문 — GitHub 은 NOASSERTION 으로 표시) | `DataSources/*.sql` — Oracle·MySQL·PG·SQL Server·DB2·SQLite 방언 | V-4·V-9·V-10 |
| k8s-examples | kubernetes/examples | `d6b8cd27` | Apache-2.0 | YAML·sh | V-2 |
| kafka | apache/kafka | `41a8c21f` | Apache-2.0 | sh·bat·cmd | V-2 |
| bootstrap5 | twbs/bootstrap | `v5.3.8` | MIT | `scss` | V-2 |
| bootstrap3 | twbs/bootstrap | `v3.4.1` | MIT | `less` | V-2 |
| csv | 공공데이터포털 파일데이터(목록 최신순 CSV) | 받은 날 2026-09-27, 목록은 `csv/SOURCES.tsv`(ID·원래 이름·이용허락·제목) | 이용허락범위 「제한 없음」 만 | CSV 100(3MB 이하, MS949 84·UTF-8 BOM 16) — `scripts/puppeteer/corpus-csv-fetch.js` 가 받는다(확인창 자동 수락). 지문에만 싣고 `corpus-fetch.sh` 는 받지 않는다 | V-4 |
| jsontest | nst/JSONTestSuite | `1ef36fa0` | MIT | `test_parsing` 318(y_ 유효·n_ 무효·i_ 구현 재량)·`test_transform` 22 | V-7 |
| shopizer | shopizer-ecommerce/shopizer | `6a4a0a65`(태그 3.2.7) | Apache-2.0 | `sm-core-model`·`sm-core`·`sm-shop` 의 `src/main/java` — Spring Boot 2.5·`javax` JPA, 엔티티 81·저장소 72·`@Query` 233(네이티브 3)·EntityManager, QueryDSL 없음 | V-25 |
| egov-msa | eGovFramework/egovframe-msa-common-components | `4f5a895b`(main) | Apache-2.0 | 모듈마다 `src/main/java` — egovframe-boot 5·`jakarta` JPA, 엔티티 98(COMTN 표)·저장소 73·`JPAQueryFactory`·`@Entity(name)`·모듈마다 같은 클래스 이름 | V-25 |
| ruoyi | yangzongzhuan/RuoYi-Vue | `13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3`(2026-08-26) | MIT(LICENSE 본문) | 모듈마다 `src/main/java` Java 266 · `src/main/resources/mapper` MyBatis XML 20(`${}` 6 자리 — `${params.dataScope}` 5·`${sql}` 1) · `sql/ry_20260417.sql`(MySQL DDL 20 표 + 초기 데이터)·`sql/quartz.sql`(11 표) — Spring Boot + MyBatis 전형(설계 33 §2, JeecgBoot 는 매퍼 9·`${}` 1 이라 뺐다) | V-29 · 6-33a · 6-34a |
| jspwiki-bin(선택) | Maven Central — `corpus/jspwiki-bin/pom.xml` 레시피 | `org.apache.jspwiki:jspwiki-main:2.12.5` + 전이 의존성 | Apache-2.0·각 jar 라이선스 | `classes/`(jspwiki-main jar 를 푼 것, 클래스 384) · `lib/`(jar 47). 소스 표본 jspwiki 와 같은 판 — SpotBugs 지적의 소스 줄 매핑을 잰다. **MANIFEST 밖**, `corpus-fetch.sh jspwiki-bin` 로 골라야 받는다 | V-29 · 5-38b |
| ruoyi-bin(선택) | 위 ruoyi 캐시 클론을 저장소 Maven(JDK 17)으로 `package -DskipTests` | ruoyi 와 같은 커밋 | MIT·각 jar 라이선스 | `classes/`(모듈 `target/classes` 합침, 클래스 285) · `lib/`(`ruoyi-admin.jar` 의 `BOOT-INF/lib`, jar 137). 빌드 산출물이라 **MANIFEST 밖**(바이트 재현 약속 없음), `corpus-fetch.sh ruoyi ruoyi-bin` | V-29 · 5-38b |
| jeecg | zhangdaiscott/jeecg | `2608008efc47594c8920d4de74f3b3d475c348c2`(2021-08-17) | Apache-2.0(GitHub 표시) | `src/main/webapp` JSP 387 · `src/main/java` Java 600 · FreeMarker `*.ftl` 209 · `docs/*.sql` 3 — JEECG 4.0 Spring MVC + Hibernate·MiniDAO 전형(설계 41) | V-35 · R-1 |
| jeesite | thinkgem/jeesite | `v1.2.7`(`0856a3ad`) | Apache-2.0(GitHub 표시) | `src/main/webapp` JSP 122 · Java 279 · `src/main/resources/mappings` MyBatis XML 29 · `db/**/*.sql` 59 — Spring MVC + MyBatis + Shiro, 옛 판(설계 41) | V-35 · R-1 |
| zheng | shuzheng/zheng | `7005c0a775e6d014d1dc8a8a809f7b1c13bf785a`(2019-09-01) | MIT(GitHub 표시) | 모듈마다 `src/main/webapp` JSP 114 · Java 374 · `*Mapper.xml` 38 · `project-datamodel/*.sql` 3 — 다중 모듈 Spring MVC + MyBatis(설계 41) | V-35 · R-1 |
| ruoyi-classic | yangzongzhuan/RuoYi | `9cd770d2ac839999d6a425f70f3bd2cf3a0ee5cd`(2026-10-10) | MIT(LICENSE 본문) | Java 285 · Thymeleaf `templates/**/*.html` 138 · 매퍼 XML 21 · 코드 생성기 템플릿 `vm/**/*.vm` 14 · `sql/*.sql` 2 — RuoYi 원판(Spring Boot + MyBatis + Thymeleaf, 설계 41) | V-35 · R-5 |
| benchmark | OWASP-Benchmark/BenchmarkJava | `f40c618c5a709afce350ef06ed2899c85cf586e9`(2026-10-27) | **GPL-2.0**(LICENSE 본문) — 저장소 밖 표본만, 골든은 시험 이름·건수(설계 41 §3-8) | `src/main/java/org/owasp/benchmark/testcode/BenchmarkTest*.java` 2,740 + `expectedresults-1.2.csv`(정답 — 범주 11, real true 1,415·false 1,325) | V-36 · R-2 |
| juliet(선택) | NIST SARD zip `https://samate.nist.gov/SARD/downloads/test-suites/2017-10-01-juliet-test-suite-for-java-v1-3.zip`(76,798,417 B) — `corpus/` 레시피 `corpus-fetch.sh` 의 `ZIP` | Juliet Java 1.3(2017-10-01) | 공개(NIST SARD, 미국 정부 저작물) | `Java/src/testcases/CWE{78,89,23,36,327,328,338,601,113,259,321,209,80,81,83}_*/**/*.java` 11,151(`antbuild/` 틀 빼고 — CWE330 은 zip 에 없다). **MANIFEST 밖**, `corpus-fetch.sh juliet` 로 골라야 받는다 | V-36 · R-2 |
| sqlfluff-dialects | sqlfluff/sqlfluff | `4.4.0`(`0348222a`) | MIT | `test/fixtures/dialects/{ansi,oracle,mysql,mariadb,postgres,tsql,sqlite,db2}/*.sql` 1,166 + 같은 이름 파스 트리 `.yml` · `test/fixtures/rules/std_rule_cases/*.yml` 94(규칙마다 `fail_str`·`fix_str`) | V-37 · R-3 |

**둘째 JSP·Java 출처**(V-1 이 고름): eGov 공통컴포넌트만으로는 JSP 가 747 이라 4장 목표(~1,500)에 모자라다. 같은 발주처 결의 eGov 템플릿 셋(274)과 결이 다른 Apache 웹앱 셋(jspwiki·roller·struts, 309)을 더했다 — 합 ~1,330. 모두 Apache-2.0.

**4장 표와 다른 것**: 툴별 DDL 덤프(DBeaver·Toad·tbAdmin 머리) 검색 대신 chinook(한 스키마를 방언 여섯으로 떠 둔 것)을 먼저 넣었다 — 방언 축은 chinook·eGov 8방언이 채운다. 툴 덤프는 V-4 가 더한다. eGov 공통컴포넌트 DDL 은 MSSQL 이 없다(altibase·cubrid·goldilocks·maria·mysql·oracle·postgres·tibero).

V-7 의 표·한국어·EUC-KR 은 새 출처 없이 기존 표본에서 — 표는 JSP 안 순수 HTML 표, 한국어 줄은 공공데이터 CSV, EUC-KR 은 CSV 의 MS949 81.
