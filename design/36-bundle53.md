# 설계 36 — 번들 53: 외부 분석기 지적의 권장 수정 — 「상」 57 유형만 (Opus 설계 · Opus 실행, 2026-10-24)

## 맥락
번들 51(설계 34)이 코드 검사에 SpotBugs·FindSecBugs 를 넣었는데, 지적의 규칙 이름이 `spotbugs.<BUG_TYPE>`·`findsecbugs.<BUG_TYPE>` 이라 규칙 단위 권장 수정(`rules.yaml` `fix`)이 안 닿는다. 데모(RuoYi 205건 · 38 유형)에서 권장 수정 칸이 전부 비었다. SpotBugs 설명은 영문뿐(한국어 판 없음, 일본어만)이고 HTML 문단이라 한 줄 권장 수정으로 못 쓴다.

사용자 결정(2026-10-24, 뒤집지 않는다):
- **고치는 법이 하나로 정해진 유형(「상」)에만** 한국어 한 줄 권장 수정을 넣는다 — 「90% 는 보통 이런 수정안으로 권고한다」 고 판단되는 것만. 「중」(방향은 같지만 꼴이 문맥마다 갈림)·「하」(의도된 설계·오탐이 흔해 확인이 먼저)와 표본에 아직 안 나온 유형은 **비워 둔다**.
- 문구 57 줄은 사용자가 대화에서 훑고 「진행해」(아래 §3 표 그대로).

판정 근거: 표본 둘(`jspwiki-bin`·`ruoyi-bin`, 기본 설정 — 우선순위 2 까지·`IMPROPER_UNICODE` 제외)의 지적 654 건 · 80 유형을 하나씩 — SpotBugs·FindSecBugs 공식 설명의 고치는 법과 KISA 구현단계 가이드가 같은 수정을 권하면 상.

| 판정 | 유형 | 지적 | 비율 |
|---|---|---|---|
| 상 | 57 (SpotBugs 32 · FindSecBugs 25) | 563 | 86% |
| 중 | 21 | 75 | 11% |
| 하 | 2 (`ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD` — RuoYi `@Value` setter 의 의도된 꼴 · `SPRING_CSRF_PROTECTION_DISABLED` — JWT REST 에선 끄는 게 표준) | 16 | 2% |

주의(사용자에게 알림): 상은 「고치는 법이 정해졌다」 이지 「그 지적이 진짜 문제다」 가 아니다(`EI_EXPOSE_REP` 는 DTO 에서 끄는 팀이 많고 `PATH_TRAVERSAL_IN` 은 입력이 사용자에게서 안 오면 오탐). 권장 수정은 지적이 맞을 때의 처방.

## 1. 목표와 범위
- 목표: 외부 분석기 지적 가운데 상 57 유형이 결과 표·복사(TSV)·xlsx 의 「권장 수정」 칸에 한국어 한 줄로 나온다. 프로필에서 더하거나 덮어쓸 수 있다.
- In scope: 표 파일 `check/ext-fixes.yaml` · `core/check/ExtFixes` · `GET /api/check/fixes` · xlsx · 화면 · 시험 · 표본 골든에 권장 수정이 붙은 건수.
- Out of scope: 중·하 유형 문구 · 지적 설명(원문 칸)의 한국어화 · `java.parseError`·`mybatis.parseError` 문구(사용자가 상만 고름).

## 2. 현재 구조
- 화면 `code_check_ext.js` 는 `/api/check/rules` 의 `fix` 로 `fixOf[규칙 id]` 를 만들어 결과 표·복사에 쓴다(L16·32·512·557) — `spotbugs.X` 는 규칙 목록에 없어 빈칸.
- xlsx `POST /api/check/runs/{id}/export`(`CheckRoutes` L275~) 는 `defs.get(f.rule())` 의 `fix` — 마찬가지로 빈칸.
- 규칙 덮어쓰기 `Rule.Def.override` 는 `params` 를 키 단위로 합친다 — 프로필 `codecheck.rules.ext.spotbugs.params.fixes` 가 그대로 정의에 들어온다.
- 유형 이름 검증 재료: 시험 픽스처 `fixtures/check/ext/sonar-findbugs-v4.6.0.yaml`(`findbugs:` 497 · `findsecbugs:` 129 — 5-38e).
- 레인: `core/check`·화면 JS 는 corpus 레인 → 마무리 `--corpus`. db 레인 안 건드림.

## 3. 설계 결정 — 전부 [고정]
- **표 파일** `src/main/resources/check/ext-fixes.yaml`: 머리 주석(기준·날짜·중·하는 비움) + `spotbugs:`·`findsecbugs:` 아래 `BUG_TYPE: '문구'`. 문구는 아래 표 그대로(60자 이하, 「…한다」 꼴).
- **`core/check/ExtFixes`**: `static Map<String, String> of(RuleSet rules)` — 표를 `spotbugs.<TYPE>`·`findsecbugs.<TYPE>` 키로 읽고, 그 위에 외부 규칙 정의(`ext.spotbugs`·`ext.findsecbugs`, 켬과 무관하게 `defs()` 에서)의 `params.fixes`(`{TYPE: 문구}`)를 같은 접두로 덮는다. 빈 문구는 그 유형을 지운다(프로필로 끄기). `static Map<String, String> builtin()`.
- **API** `GET /api/check/fixes` → `{ "spotbugs.EI_EXPOSE_REP2": "…", … }`(활성 프로필 반영). 규칙 목록 응답 꼴은 안 바꾼다.
- **xlsx**: 규칙 정의에 `fix` 가 없으면 `ExtFixes` 에서.
- **화면**: `loadRules` 끝에 `/api/check/fixes` 를 받아 `fixOf` 에 없는 키만 더한다(실패해도 검사는 된다).
- **시험**: `ExtFixesTest` — 57 · 키마다 sonar 표의 같은 엔진 절에 있다 · 60자 이하 · 하·중 대표(`ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD`·`SPRING_CSRF_PROTECTION_DISABLED`·`IS2_INCONSISTENT_SYNC`·`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`)는 없다 · 프로필 `params.fixes` 덮기·빈 문구 지우기. `CheckRoutesTest`(`/api/check/fixes`). 스모크 `codeCheckExternal`(결과 표 `findsecbugs.PATH_TRAVERSAL_IN` 행에 권장 수정, xlsx 에도). 표본 `SpotBugsCorpusTest` 골든에 `withFix`(권장 수정이 붙은 지적 수).
- 버린 대안: 지적(`Finding`)에 `fix` 칸 더하기(이력·골든·화면 전부 바뀐다 — 규칙 3 은 아니어도 범위가 커진다) · 규칙 목록에 유형을 끼우기(화면 체크박스가 터진다) · SpotBugs 영문 설명 원문(문단·영문).

### 문구 — 상 57 (사용자 확인 2026-10-24)
SpotBugs 32: `EI_EXPOSE_REP2` 받은 배열·Date·컬렉션은 복사해서 필드에 넣는다 · `EI_EXPOSE_REP` 내부 가변 객체는 복사본이나 읽기 전용 뷰로 돌려준다 · `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` File.delete·mkdirs 등의 반환값을 확인해 실패를 처리한다 · `HSM_HIDING_METHOD` 상위 클래스 static 메서드와 같은 이름을 피해 이름을 바꾼다 · `MS_SHOULD_BE_FINAL` static 필드에 final 을 붙인다 · `MS_PKGPROTECT` public static 필드를 final 로 하고 공개 범위를 줄인다 · `PA_PUBLIC_PRIMITIVE_ATTRIBUTE` public 필드를 private 으로 하고 접근자를 둔다 · `MS_MUTABLE_ARRAY` 배열은 private 으로 두고 복사본이나 불변 List 로 내준다 · `BX_UNBOXING_IMMEDIATELY_REBOXED` 꺼냈다 다시 감싸는 박싱을 없애고 같은 타입으로 둔다 · `DM_DEFAULT_ENCODING` 문자셋을 명시한다(StandardCharsets.UTF_8 등) · `FS_BAD_DATE_FORMAT_FLAG_COMBO` 날짜 형식 문자를 맞춘다(12시간 hh 는 a 와, 24시간은 HH) · `SF_SWITCH_NO_DEFAULT` switch 에 default 절을 둔다 · `SS_SHOULD_BE_STATIC` 값이 고정된 final 인스턴스 필드를 static 으로 한다 · `WMI_WRONG_MAP_ITERATOR` keySet 뒤 get 대신 entrySet 으로 돈다 · `REC_CATCH_EXCEPTION` Exception 대신 실제로 나는 예외만 잡는다 · `SE_COMPARATOR_SHOULD_BE_SERIALIZABLE` Comparator 에 Serializable 을 구현한다 · `AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION` 확인 후 넣기를 putIfAbsent·compute 같은 원자 연산으로 바꾼다 · `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` null 일 수 없는 값의 null 확인을 지운다 · `RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE` null 확인을 그 값을 쓰기 전으로 옮긴다 · `UC_USELESS_OBJECT` 만들고 쓰지 않는 객체를 지운다 · `CN_IDIOM_NO_SUPER_CALL` clone() 에서 super.clone() 을 부른다 · `DCN_NULLPOINTER_EXCEPTION` NullPointerException 을 잡지 말고 null 을 먼저 확인한다 · `DMI_ENTRY_SETS_MAY_REUSE_ENTRY_OBJECTS` entrySet 의 Entry 를 모으지 말고 새 Entry 로 복사한다 · `HE_EQUALS_USE_HASHCODE` equals 를 재정의하면 hashCode 도 같이 재정의한다 · `OS_OPEN_STREAM` 스트림은 try-with-resources 로 연다 · `RV_RETURN_VALUE_IGNORED` 버린 반환값(새 객체·결과)을 받아 쓴다 · `SIC_INNER_SHOULD_BE_STATIC` 바깥 인스턴스를 안 쓰는 내부 클래스를 static 으로 한다 · `SSD_DO_NOT_USE_INSTANCE_LOCK_ON_SHARED_STATIC_DATA` static 데이터는 static 락 객체로 동기화한다 · `VA_FORMAT_STRING_USES_NEWLINE` 형식 문자열의 \n 을 %n 으로 바꾼다 · `DMI_RANDOM_USED_ONLY_ONCE` Random 은 한 번 만들어 재사용한다 · `OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE` 자원은 try-with-resources 로 열어 예외 때도 닫는다 · `SBSC_USE_STRINGBUFFER_CONCATENATION` 반복문 안 문자열 + 를 StringBuilder 로 바꾼다.

FindSecBugs 25: `PATH_TRAVERSAL_IN` 경로를 정규화한 뒤 기준 폴더 안인지 확인한다(파일명은 허용 목록) · `POTENTIAL_XML_INJECTION` XML 은 문자열로 잇지 말고 XML API 로 만들거나 값을 이스케이프한다 · `SQL_INJECTION_JDBC` PreparedStatement 의 ? 바인딩으로 값을 넘긴다 · `XSS_SERVLET` 응답에 쓰는 입력값은 HTML 인코딩해 출력한다 · `URLCONNECTION_SSRF_FD` 접속할 URL·호스트를 허용 목록으로 검사한다 · `UNVALIDATED_REDIRECT` 이동할 주소를 허용 목록이나 상대 경로로 제한한다 · `INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE` 예외 내용은 로그에만 남기고 응답엔 일반 메시지를 준다 · `WEAK_MESSAGE_DIGEST_MD5` SHA-256 이상으로 바꾼다(비밀번호는 bcrypt·PBKDF2) · `PREDICTABLE_RANDOM` 보안 값(토큰·비밀번호)은 SecureRandom 으로 만든다 · `WEAK_TRUST_MANAGER` 인증서 검증을 끄지 말고 기본 TrustManager 를 쓴다 · `LDAP_INJECTION` LDAP 필터 값은 이스케이프하거나 매개변수로 넘긴다 · `XXE_DOCUMENT` 파서에서 DTD·외부 엔티티를 끈다(disallow-doctype-decl) · `CRLF_INJECTION_LOGS` 로그에 남기는 입력값의 줄바꿈(CR·LF)을 지우거나 바꾼다 · `PATH_TRAVERSAL_OUT` 쓸 경로를 정규화한 뒤 기준 폴더 안인지 확인한다 · `FILE_UPLOAD_FILENAME` 업로드 원본 파일명 대신 서버가 만든 이름으로 저장한다 · `HARD_CODE_PASSWORD` 비밀번호를 코드에서 빼 외부 설정·암호화 값에서 읽는다 · `HTTPONLY_COOKIE` 쿠키에 HttpOnly 를 켠다(setHttpOnly(true)) · `INSECURE_COOKIE` 쿠키에 Secure 를 켠다(setSecure(true)) · `REQUESTDISPATCHER_FILE_DISCLOSURE` forward·include 경로를 입력으로 정하지 말고 허용 목록으로 · `UNSAFE_HASH_EQUALS` 해시 비교는 MessageDigest.isEqual 로 한다 · `WEAK_MESSAGE_DIGEST_SHA1` SHA-256 이상으로 바꾼다(비밀번호는 bcrypt·PBKDF2) · `PERMISSIVE_CORS` CORS 허용 출처를 * 대신 정해진 도메인으로 한다 · `SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING` @RequestMapping 에 method 를 정하거나 @GetMapping 등을 쓴다 · `SSL_CONTEXT` SSLContext 는 TLSv1.2 이상을 지정한다 · `WEAK_HOSTNAME_VERIFIER` 호스트 이름 검증을 끄지 말고 기본 검증기를 쓴다.

## 4. 실행 스텝
- **S0** — 이 파일 + PLAN §15 번들 53 · §16 행 셋 + PROGRESS(번들 52 머지 표시 뒤 한 줄).
- **5-43a** — `ext-fixes.yaml` · `ExtFixes` · `GET /api/check/fixes` · xlsx · `ExtFixesTest` · `CheckRoutesTest`. 검증 `-Dtest='ExtFixesTest,CheckRoutesTest'`.
- **5-43b** — 화면 `code_check_ext.js` · 스모크 `codeCheckExternal`. 검증 `-Dtest='SmokeHtmlUnitTest#codeCheckExternal,ToolsFolderTest'`.
- **V-31** — `SpotBugsCorpusTest` 골든 `withFix`(갱신 모드로 diff 를 보고 이력에). 검증 `verify.sh --corpus`.
- 마무리 — 번들 하나·PR 하나([[one-pr-per-batch]]), 머지 위임(사용자 「진행해」 2026-10-24).

## 5. 금지 사항
- 중·하 유형에 문구를 넣지 않는다 · 문구를 바꾸지 않는다(사용자 확인본) · `Finding`·이력 스키마 불변 · 등급 규칙 불변(5-38e).

## 6. 최종 검증
`bash scripts/verify.sh` → `--corpus` → `gate-probe.sh` → PR → CI → AI 리뷰 → 머지.

## 7. 중단 조건
- 표의 유형 이름이 sonar 표에 없다(오타 — 고쳐서 이어감, 문구는 그대로) 외에는 CLAUDE.md 번들 모드.

## 8. 불확실 항목
없음.
