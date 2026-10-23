# 반입 재료 출처

반입 zip(11장)에 넣는 외부 바이너리의 출처·판·라이선스. 반입 zip 은 실행 전용이다(번들 48) — `jre/` 는 아래 JDK 를 `scripts/package.sh` 의 `JLINK_MODULES` 로 줄인 jlink 이미지, `drivers/` 는 `--with` 프로필의 dialect 것만(없으면 바로 아래 넷), `drivers/alt/` 는 넣지 않고 현장이 요청하면 레시피로 받아 따로 넣는다. 받기는 `bash scripts/bundle-fetch.sh`(네트워크, 빌드 PC), 지문은 `bundle/MANIFEST`(그 스크립트만 쓴다). 받은 파일은 저장소에 넣지 않는다(`.gitignore` — `jre/`·`drivers/`(사람이 넣는 Tibero jar·라이선스 파일까지)).

| 폴더 | 무엇 | 출처·좌표 | 판 | 라이선스 |
|---|---|---|---|---|
| `jre/` | Temurin 17 JDK x64 Windows(zip) — 반입 zip 에는 그 jlink 이미지(javac 없음) | Adoptium API `api.adoptium.net/v3/assets/latest/17/hotspot?…&image_type=jdk&os=windows` → GitHub `adoptium/temurin17-binaries` 릴리스. 대조는 API 의 sha256 | 받는 날의 17 최신(MANIFEST 머리) | GPLv2 + Classpath Exception |
| `drivers/` | Oracle JDBC | `com.oracle.database.jdbc:ojdbc11` | 23.8.0.25.04 | Oracle Free Use Terms and Conditions(FUTC) |
| `drivers/` | PostgreSQL JDBC | `org.postgresql:postgresql` | 42.7.12 | BSD-2-Clause |
| `drivers/` | MariaDB Connector/J | `org.mariadb.jdbc:mariadb-java-client` | 3.5.9 | LGPL-2.1 |
| `drivers/` | Microsoft JDBC(SQL Server) | `com.microsoft.sqlserver:mssql-jdbc` | 12.10.2.jre11 | MIT |
| `drivers/alt/` | Oracle JDBC(JDK 8 현장 WAS·12c~) | `com.oracle.database.jdbc:ojdbc8` | 23.8.0.25.04 | FUTC |
| `drivers/alt/` | Oracle JDBC(11g) | `com.oracle.database.jdbc:ojdbc6` | 11.2.0.4 | FUTC |
| `drivers/alt/` | MySQL Connector/J | `com.mysql:mysql-connector-j` | 9.7.0 | GPLv2 + Universal FOSS Exception |
| `drivers/` | Tibero JDBC | **Maven Central 에 없다** — TmaxData 기술 지원 사이트의 개발자판 `tibero*.jar` 와 라이선스 파일을 사람이 넣는다 | 현장 DB 판에 맞춰 | TmaxData 라이선스 |

드라이버를 쓰는 법: `drivers/` 바로 아래 jar 를 전부 등록하므로 같은 벤더 jar 는 하나만 둔다. 다른 판이 필요하면(반입 zip 에는 `drivers/alt/` 가 없다) 빌드 PC 에서 받은 `drivers/alt/` 의 jar 를 따로 반입해 현장 `drivers/` 의 같은 벤더 jar 와 바꿔 넣는다. 반입 전 사내 반입 조건(Oracle FUTC·MySQL GPL 예외)을 확인한다(16장 끝 체크리스트).

## app.jar 안 분석 엔진

코드 검사 묶음 ㅅ(5-38, 설계 34)의 SpotBugs·FindSecBugs. 받기는 `bundle-fetch.sh` 가 아니라 Maven 빌드(`pom.xml` 런타임 의존성 → `m2/`)이고, `app.jar` 에 shade 로 든다. FindSecBugs 만 의존성이 아니라 `maven-dependency-plugin` `copy` 로 자원 `analyzers/findsecbugs-plugin.jar` 가 된다(SpotBugs 가 플러그인을 파일로만 읽는다 — 실행 때 `data/analyzers/` 로 꺼낸다). 고치지 않은 판을 별도 클래스로 부르는 동적 링크라 LGPL 판은 이 표가 고지·출처다. 라이선스는 각 pom(없으면 상위 pom) 표기 그대로.

| 무엇 | 좌표 | 판 | 라이선스 | 출처 |
|---|---|---|---|---|
| SpotBugs 엔진(코어 검출기 192) | `com.github.spotbugs:spotbugs` | 4.9.3 | LGPL-2.1 | github.com/spotbugs/spotbugs |
| SpotBugs 어노테이션 | `com.github.spotbugs:spotbugs-annotations` | 4.9.3 | LGPL-2.1 | github.com/spotbugs/spotbugs |
| FindSecBugs 플러그인(검출기 121, 자원 jar) | `com.h3xstream.findsecbugs:findsecbugs-plugin` | 1.14.0 | LGPL-3.0 | github.com/find-sec-bugs/find-sec-bugs |
| ASM 다섯(asm·analysis·commons·tree·util) | `org.ow2.asm:asm*` | 9.7.1 | BSD-3-Clause | asm.ow2.io |
| BCEL | `org.apache.bcel:bcel` | 6.10.0 | Apache-2.0 | commons.apache.org/proper/commons-bcel |
| dom4j | `org.dom4j:dom4j` | 2.1.4 | BSD 꼴 dom4j License(pom 표기 Plexus) | github.com/dom4j/dom4j |
| Jaxen | `jaxen:jaxen` | 2.0.0 | BSD-3-Clause | github.com/jaxen-xpath/jaxen |
| Commons Lang · Text | `org.apache.commons:commons-lang3` · `commons-text` | 3.17.0 · 1.13.0 | Apache-2.0 | commons.apache.org |
| Gson | `com.google.code.gson:gson` | 2.12.1 | Apache-2.0 | github.com/google/gson |
| JSR-305 어노테이션 | `com.google.code.findbugs:jsr305` | 3.0.2 | Apache-2.0(pom 표기) | github.com/findbugsproject/findbugs |
| JCIP 어노테이션(재구현) | `com.github.stephenc.jcip:jcip-annotations` | 1.0-1 | Apache-2.0 | github.com/stephenc/jcip-annotations |

빼고 넣은 것: `net.sf.saxon:Saxon-HE`(XSLT 보고서 — 안 쓴다, 5.6MB)·`org.apache.logging.log4j:log4j-core`(logback 과 바인딩이 겹친다)는 `pom.xml` exclusions. 15 jar 가 `app.jar` 를 37.0→43.7MB 로 키웠다(2026-10-24 실측).
