# 순수본 코드 검사 — portfolio 세션 지시문

toolbox-server 5-9 가 낸다. 받는 쪽은 portfolio 저장소의 순수본 도구 폴더 `frontend/public/toolbox/` 다. toolbox-server 는 이 일을 하지 않는다 — 순수본은 portfolio 가 원본이고 toolbox-server 는 끌어오기만 한다(절대 규칙 4). 커밋 해시를 알리면 toolbox-server 가 5-10 으로 끌어와 자바 구현과 교차 검증한다.

## 1. 만들 것

- 새 파일 `tools/code_check.html` — 이름은 영문 그대로(백엔드본 런처 카드 `data-file="code_check.html"` 과 같게)
- 단일 파일. `file://` 로 열린다. CDN·외부 스크립트·외부 글꼴 없음. 서버 호출 없음
- 화면은 다른 순수본 도구와 같은 CSS 변수(`--bg` `--surface` `--border` `--text` `--muted` `--accent`)와 글꼴(`'Consolas','D2Coding',monospace`)

## 2. 규칙은 붙이기만 한다

- 원본은 toolbox-server `src/main/resources/check/rules.yaml`. 순수본이 쓰는 것은 그중 정규식 규칙 54 — ㄱ 공통 잔재 9 · ㄹ JSP 3 · ㅁ TSX·JS 4 · ㅇ 보안약점 29(2차 15 는 KISA 구현단계 정적분 5-13, 3차 7 은 개인정보 꼴·설정 5-18) · ㅈ 웹 접근성·표준 9(5-17 — 「파일에 없음」 규칙 셋은 백엔드본만)
- toolbox-server `src/test/resources/golden/check/pure-rules.json` 을 그대로 `const RULES = [ … ];` 로 붙인다. 손으로 고치지 않는다 — 고칠 일이 있으면 toolbox-server 의 YAML 을 고치고 JSON 을 다시 받는다
- 항목 하나의 꼴: `{id, group, severity, langs, regex, flags, skipComments, max, message}`
  - `langs` — 이 규칙을 적용할 언어(확장자 소문자)
  - `regex` — JS `RegExp` 로 그대로 컴파일된다(toolbox-server 가 자바 전용 문법을 테스트로 막는다. Node 22 로 54 전부 컴파일됨)
  - `flags` — `["i"]` 처럼 문자 목록. `new RegExp(regex, flags.join(''))`
  - `max` — 있으면 건수 규칙(아래 4)
- ㄴ Java 구조·ㄷ MyBatis·ㅂ 파일 묶음과 미사용 import 는 순수본에 넣지 않는다 — 파서·폴더가 있어야 해서 백엔드본만 한다

## 3. 입력

- 붙여넣기 칸 하나 + 언어 선택(java · jsp · js · ts · tsx · xml · properties · html). 언어가 곧 확장자다
- 묶음 체크박스 넷(ㄱ·ㄹ·ㅁ·ㅇ), 묶음을 펼치면 규칙별 켜고 끄기
- 켜고 끈 상태는 `localStorage` 에 둔다(순수본은 서버가 없다)

## 4. 적용 뜻 — 백엔드본과 같은 답을 내야 한다

1. 고른 언어가 `langs` 에 든 규칙만 돈다
2. `skipComments` 가 참이면 주석을 **같은 길이의 공백으로** 지운 글에 맞춘다. 줄바꿈은 남겨 줄 번호가 안 밀린다
   - java · js · jsx · ts · tsx · mjs — `//` 부터 줄 끝, `/* … */`. 따옴표 `"` `'` 와 백틱 안은 주석으로 안 본다(자바 구현이 java 에도 백틱을 따옴표로 본다 — 같게). 따옴표 안의 `\` 는 다음 글자를 묶는다. `"`·`'` 문자열은 줄바꿈에서 끝나고 백틱은 안 끝난다
   - jsp · jspf · tag — `<%-- … --%>` 와 `<!-- … -->`
   - xml · html · htm — `<!-- … -->`
   - properties — 줄머리(앞 공백 뒤) `#`·`!` 로 시작하는 줄 전체
   - yml · yaml — 줄머리 `#` 줄 전체
3. 줄마다 `RegExp.test(줄)` — 한 줄에 한 건. 결과의 원문은 **원래 줄**(주석을 안 지운 줄)의 앞뒤 공백을 뗀 앞 160자
4. `max` 가 있으면 줄마다 맞은 횟수를 전부 센다(`g` 플래그로 반복). 파일 전체 합이 `max` 를 넘을 때 첫 맞은 줄에 한 건, 원문 자리에 `N개 (기준 max)`
5. 결과 정렬은 줄 번호, 같은 줄이면 규칙 id

## 5. 결과 화면

- 표: 줄 · 묶음 · 규칙 · 등급 · 원문
- 등급(error·warn·info)·묶음 거르기, 결과 복사(탭 구분)
- 결과는 화면에만. 어디에도 저장하지 않는다

## 6. 받는 기준

toolbox-server 의 픽스처와 골든으로 잰다.

- toolbox-server `src/test/resources/fixtures/check/{common,jsp,tsx,security}/pos*` 파일을 하나씩 붙여 넣고 확장자대로 언어를 고르면, 결과의 (줄, 규칙) 이 `src/test/resources/golden/check/fixtures.json` 의 그 파일 항목과 같다. 단 `file.*`·`tsx.unusedImport` 항목은 뺀다(정규식이 아니다)
- 같은 폴더의 `neg*` 파일은 결과 0 건
- 브라우저 콘솔 오류 0

## 7. 런처·메모

- 런처 `toolbox.html` 의 `TOOLS` 배열에 카드 하나 — 이름 「코드 검사」, 설명 첫 문장에 무엇을·무엇으로·어떻게(예: 「붙여 넣은 소스를 정규식 규칙 묶음(잔재·JSP·TSX·보안약점)으로 훑어 줄 목록을 낸다」)
- `개선사항_메모.md` 에 이 도구 한 줄(백엔드본은 폴더·변경분·Java 구조·MyBatis·파일 묶음까지 한다는 것)

## 8. 끝

portfolio 에만 커밋한다. 커밋 해시를 toolbox-server 쪽에 알린다 — toolbox-server 는 그 해시로 `scripts/sync-pure.sh` 를 돌리고(순수본 8) 5-10 에서 같은 픽스처를 브라우저로 돌려 자바 결과와 견준다.
