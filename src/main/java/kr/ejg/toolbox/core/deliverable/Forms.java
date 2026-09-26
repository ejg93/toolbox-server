package kr.ejg.toolbox.core.deliverable;

import java.util.List;

/**
 * 산출물 문서 11종의 양식 열 한곳 모음(2-4) — 예시 양식 xlsx·매핑 YAML 을 만들 때와, 커밋된 예시 양식이 값 표 열과
 * 어긋나지 않았는지 잴 때 쓴다. 열 목록 원본은 각 문서를 만드는 클래스의 상수다.
 */
public final class Forms {

    /** file: 예시 양식 파일 이름 — 번호_문서명(공백 없이).xlsx */
    public record Form(String no, String name, List<String> columns) {
        public Form {
            columns = List.copyOf(columns);
        }

        public String file() {
            return no + "_" + name.replace(" ", "") + ".xlsx";
        }
    }

    private Forms() {
    }

    public static List<Form> all() {
        return List.of(
                new Form("01", "데이터베이스 정의서", Definitions.COLS_01),
                new Form("02", "테이블 정의서", Definitions.COLS_02),
                new Form("03", "컬럼 정의서", Definitions.COLS_03),
                new Form("04", "테이블 관계 정의서", Definitions.COLS_04),
                new Form("05", "DB 표준단어", Standards.COLS_05),
                new Form("06", "DB 표준도메인", Standards.COLS_06),
                new Form("07", "DB 표준용어", Standards.COLS_07),
                new Form("08", "DB 표준코드", CodeAndLink.COLS_08),
                new Form("09", "연계데이터 목록 정의서", CodeAndLink.COLS_09),
                new Form("10", "인덱스 정의서", Definitions.COLS_10),
                new Form("11", "제약조건 정의서", Definitions.COLS_11));
    }
}
