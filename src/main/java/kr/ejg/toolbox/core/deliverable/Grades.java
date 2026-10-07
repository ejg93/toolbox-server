package kr.ejg.toolbox.core.deliverable;

import java.util.ArrayList;
import java.util.List;

/**
 * 정의서 열마다 값이 어디서 오나(2-13, 설계 14 부록 B) — 자동(DB·소스에서 그대로) · 추정(코멘트·이름 꼴·사전으로 짐작) ·
 * 수동(DB·소스에 없어 사람이 채운다). 정의서 파일엔 색·메모를 안 넣고 00_작성안내.xlsx 「항목」 시트로만 보인다.
 * 열 이름이 바뀌면 GradesTest 가 잡는다 — 모든 (문서, 열)이 그 문서 열 목록에 있어야 한다.
 */
public final class Grades {

    public static final String AUTO = "자동";
    public static final String GUESS = "추정";
    public static final String MANUAL = "수동";

    public record Grade(String doc, String column, String level, String how) {
    }

    /**
     * 문서 단위 판정(1-34) — ● 핵심 항목이 메타·분석에서 나온다 · ◐ 핵심 일부가 추정·수동 · ○ 핵심이 DB·소스에 없다.
     * 문서 종류로 정한 고정값이다(설계 14 feasibility 표). 열 등급 수로 계산하지 않는다 — 등급은 「설명이 필요한 열」 만 담는다
     */
    public record DocMark(String no, String name, String mark, String reason) {
    }

    public static final String FULL = "●";
    public static final String PART = "◐";
    public static final String NONE = "○";

    private static final List<Grade> ALL = build();

    private static final List<DocMark> MARKS = List.of(
            new DocMark("01", "데이터베이스 정의서", PART, "한 행이라 DB 이름·버전은 자동, 담당·운영 정보는 프로필·화면 입력"),
            new DocMark("02", "테이블 정의서", FULL, "표·코멘트·행 수·생성일은 메타에서. 업무 분류·담당자는 수동"),
            new DocMark("03", "컬럼 정의서", FULL, "컬럼·타입·NULL·기본값·PK·FK 는 메타에서. 개인정보 여부는 이름으로 추정"),
            new DocMark("04", "테이블 관계 정의서", PART, "FK 는 메타에서. 매퍼 조인으로 추정한 관계는 뚜렷한 것만"),
            new DocMark("05", "DB 표준단어", PART, "사전에 있는 단어만 — 미매칭 조각은 논리명 변환기에서 채운 뒤 다시 만든다"),
            new DocMark("06", "DB 표준도메인", PART, "타입·길이에서 추정. 도메인 이름은 수동"),
            new DocMark("07", "DB 표준용어", PART, "컬럼명을 사전으로 풀어 추정. 사전에 없는 조각은 빈칸"),
            new DocMark("08", "DB 표준코드", FULL, "고른 코드 표의 값을 접속에서 그대로. 코드 설명은 표에 있는 만큼"),
            new DocMark("09", "연계데이터 목록 정의서", PART, "DB 링크·외부 표 이름은 단서일 뿐 — 연계 상대·주기는 수동"),
            new DocMark("10", "인덱스 정의서", FULL, "인덱스·컬럼·정렬·유니크는 메타에서"),
            new DocMark("11", "제약조건 정의서", FULL, "PK·FK·UNIQUE·CHECK 원문은 메타에서"),
            new DocMark("18", "테이블 대 응용프로그램 상관도", FULL, "프로그램 분석 CRUD 그대로. 동적 호출·타일즈는 못 본다"));

    private Grades() {
    }

    public static List<Grade> all() {
        return List.copyOf(ALL);
    }

    public static List<Grade> of(String doc) {
        return ALL.stream().filter(g -> g.doc().equals(doc)).toList();
    }

    /** 문서 판정 12개, 번호순(1-34) */
    public static List<DocMark> marks() {
        return MARKS;
    }

    /** 그 문서의 열 등급 수 — {자동, 추정, 수동} */
    public static int[] counts(String doc) {
        int[] n = new int[3];
        for (Grade g : of(doc)) {
            n[g.level().equals(AUTO) ? 0 : g.level().equals(GUESS) ? 1 : 2]++;
        }
        return n;
    }

    private static List<Grade> build() {
        List<Grade> g = new ArrayList<>();
        // 01 데이터베이스 정의서
        add(g, "01", "기관명", MANUAL, "화면·요청의 기관명(프로필 deliverable.org)");
        add(g, "01", "부서명", MANUAL, "화면·요청의 부서명");
        add(g, "01", "관련법령", MANUAL, "DB 구축 근거 법령");
        add(g, "01", "한글 DB명", MANUAL, "화면·요청의 한글 DB명");
        add(g, "01", "영문 DB명", AUTO, "스키마 이름(요청에 DB명이 있으면 그것)");
        add(g, "01", "구축일자", MANUAL, "DB 구축 완료일");
        add(g, "01", "DB 설명", MANUAL, "화면·요청의 DB 설명");
        add(g, "01", "업무분류체계", MANUAL, "기관 BRM 분류");
        add(g, "01", "DBMS 정보", AUTO, "접속 DB 의 제품·버전");
        add(g, "01", "운영체제정보", MANUAL, "화면·요청의 운영체제");
        add(g, "01", "DB 형태", AUTO, "관계형 DB 만 다뤄 「정형」");
        add(g, "01", "테이블 수", AUTO, "스냅샷 표 수(프로필 거름 뒤)");
        add(g, "01", "데이터 용량", AUTO, "벤더 뷰의 스키마 용량 — 권한이 없으면 빈칸");
        // 02 테이블 정의서
        add(g, "02", "영문 DB명", AUTO, "스키마 이름(요청에 DB명이 있으면 그것)");
        add(g, "02", "테이블 소유자", AUTO, "스키마");
        add(g, "02", "한글 테이블명", GUESS, "테이블 코멘트 — 없으면 (코멘트 없음) 표시. 코멘트가 이름이 아닐 수 있다");
        add(g, "02", "영문 테이블명", AUTO, "테이블 이름");
        add(g, "02", "테이블 유형", MANUAL, "코드·이력·임시 등 논리 유형");
        add(g, "02", "관련 엔터티명", GUESS, "테이블 코멘트를 옮겼다 — 논리 모델 엔터티명으로 고친다");
        add(g, "02", "테이블 설명", MANUAL, "테이블의 용도");
        add(g, "02", "발생주기", MANUAL, "데이터가 쌓이는 주기");
        add(g, "02", "테이블 볼륨", AUTO, "DB 통계 행 수 — 통계 전이면 빈칸");
        add(g, "02", "공개/비공개 여부", MANUAL, "공개 대상인지");
        add(g, "02", "개방데이터목록", MANUAL, "공공데이터 개방 목록 이름");
        add(g, "02", "최초등록일", AUTO, "DB 의 표 생성 시각 — 모르면 빈칸");
        add(g, "02", "최종수정일", MANUAL, "변경 이력에서");
        add(g, "02", "최종수정자", MANUAL, "화면·요청의 작성자");
        add(g, "02", "변경구분", MANUAL, "신규·수정·삭제");
        // 03 컬럼 정의서
        add(g, "03", "영문 테이블명", AUTO, "테이블 이름");
        add(g, "03", "한글 컬럼명", GUESS, "컬럼 코멘트 — 없으면 (코멘트 없음) 표시");
        add(g, "03", "영문 컬럼명", AUTO, "컬럼 이름");
        add(g, "03", "컬럼 설명", MANUAL, "컬럼의 뜻");
        add(g, "03", "연관 엔터티명", GUESS, "테이블 코멘트를 옮겼다");
        add(g, "03", "연관 속성명", GUESS, "컬럼 코멘트를 옮겼다");
        add(g, "03", "데이터 타입", AUTO, "DB 타입");
        add(g, "03", "데이터 길이", AUTO, "문자 길이·수 정밀도");
        add(g, "03", "Not Null 여부", AUTO, "표준 표기 — Y = Nullable, N = Not Null");
        add(g, "03", "PK정보", AUTO, "PK + 참여 순서(PK01)");
        add(g, "03", "AK정보", AUTO, "UNIQUE 제약 — AK_<순번>-<참여 순서>");
        add(g, "03", "FK정보", AUTO, "선언된 FK 만 — 참조테이블.참조컬럼");
        add(g, "03", "제약조건", AUTO, "기본값·이 컬럼이 든 CHECK");
        add(g, "03", "개인정보 여부", GUESS, "논리명·코멘트 키워드와 컬럼명 꼴(마스킹 규칙)로 짐작");
        add(g, "03", "암호화 여부", MANUAL, "DB 암호화 적용 여부");
        add(g, "03", "공개/비공개 여부", MANUAL, "공개 대상인지");
        add(g, "03", "최초등록일", AUTO, "DB 의 표 생성 시각 — 모르면 빈칸");
        add(g, "03", "최종수정일", MANUAL, "변경 이력에서");
        add(g, "03", "최종수정자", MANUAL, "화면·요청의 작성자");
        add(g, "03", "변경구분", MANUAL, "신규·수정·삭제");
        // 04 테이블 관계 정의서(표준 문서 없음 — 확장)
        add(g, "04", "부모 영문 테이블명", AUTO, "선언된 FK 의 참조 표");
        add(g, "04", "자식 영문 테이블명", AUTO, "선언된 FK 를 가진 표");
        add(g, "04", "삭제규칙", AUTO, "딕셔너리의 FK 삭제규칙");
        add(g, "04", "갱신규칙", AUTO, "딕셔너리의 FK 갱신규칙 — Oracle 은 ON UPDATE 가 없어 NO ACTION");
        add(g, "04", "근거", GUESS, "선언 = DB 의 FK. 추정 = 매퍼 조인에서 한쪽 표의 PK 전부가 맞선 쌍(프로그램 분석 실행을 골랐을 때). "
                + "방향을 모르는 쌍은 작성안내 「관계 후보」 시트");
        // 05~07 표준사전
        add(g, "05", "표준단어명", GUESS, "컬럼명을 쪼개 공통표준·기관 사전과 맞춘 단어");
        add(g, "05", "단어 영문약어명", AUTO, "컬럼명 조각");
        add(g, "05", "단어 설명", MANUAL, "단어의 뜻(공통표준 단어는 사전 설명)");
        add(g, "06", "도메인명", GUESS, "컬럼 타입·길이와 접미어로 고른 도메인");
        add(g, "06", "데이터타입", AUTO, "DB 타입");
        add(g, "07", "표준용어명", GUESS, "단어를 이은 논리명");
        add(g, "07", "용어설명", MANUAL, "용어의 뜻");
        add(g, "07", "허용값", MANUAL, "값 범위·코드");
        // 08 표준코드
        add(g, "08", "관리부서명", MANUAL, "화면·요청의 부서명");
        add(g, "08", "한글코드명", GUESS, "코드 표 코멘트");
        add(g, "08", "영문코드명", AUTO, "코드 그룹 값(없으면 표 이름)");
        add(g, "08", "코드설명", MANUAL, "코드의 뜻");
        add(g, "08", "코드값", AUTO, "코드 표 조회");
        add(g, "08", "코드값 의미", AUTO, "코드 표 이름 컬럼");
        add(g, "08", "제정일자", MANUAL, "코드 제정일");
        // 09 연계 데이터 목록
        add(g, "09", "연계정보 구분", MANUAL, "제공·활용");
        add(g, "09", "연계 정보명", MANUAL, "연계 이름");
        add(g, "09", "연계 주기", MANUAL, "실시간·일·월 등");
        add(g, "09", "연계 항목명", GUESS, "후보 표의 컬럼 코멘트 — 후보는 이름(IF·RCV·SND)·코멘트·뷰로 고른 짐작");
        add(g, "09", "출처 테이블명", GUESS, "연계 후보 표");
        add(g, "09", "제공기관", MANUAL, "보내는 기관·시스템");
        add(g, "09", "활용기관", MANUAL, "받는 기관·시스템");
        // 10·11(표준 문서 없음 — 확장)
        add(g, "10", "정렬", AUTO, "딕셔너리의 인덱스 정렬 — PK 행·모르면 빈칸");
        add(g, "11", "제약내용", AUTO, "PK·UNIQUE 컬럼, CHECK 조건 글");
        return List.copyOf(g);
    }

    private static void add(List<Grade> g, String doc, String column, String level, String how) {
        g.add(new Grade(doc, column, level, how));
    }
}
