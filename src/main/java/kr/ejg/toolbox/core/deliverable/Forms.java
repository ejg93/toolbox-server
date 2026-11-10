package kr.ejg.toolbox.core.deliverable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.core.report.Form;
import kr.ejg.toolbox.core.report.Mapping;

/**
 * 산출물 문서 11종의 양식 열 한곳 모음(2-4) — 예시 양식 xlsx·매핑 YAML 을 만들 때와, 커밋된 예시 양식이 값 표 열과
 * 어긋나지 않았는지 잴 때 쓴다. 열 목록 원본은 각 문서를 만드는 클래스의 상수다.
 */
public final class Forms {

    /** 예시 양식의 머리·첫 데이터 행(엑셀 행 번호) — make-templates 와 내장 매핑이 같이 쓴다 */
    public static final int HEADER_ROW = 2;
    public static final int FIRST_ROW = 3;
    /** 내장 양식·매핑이 찍혀 나가는 자리(make-templates 기본값). 프로필이 이것을 가리키면 디스크를 안 읽고 코드에서 만든다(설계 61) */
    public static final String EXAMPLE_DIR = "templates/deliverable/example";
    public static final String EXAMPLE_MAPPING = "mappings/deliverable/example.yaml";

    private Forms() {
    }

    /** 프로필 값이 비었거나 예시 자리(끝 구분자·./·역슬래시·절대 경로 꼴 포함)를 가리킨다 → 내장 */
    public static boolean builtIn(String configured, String example) {
        return configured == null || configured.isBlank()
                || Path.of(configured.trim().replace('\\', '/')).toAbsolutePath().normalize().equals(Path.of(example).toAbsolutePath().normalize());
    }

    /** 내장 매핑 — 커밋된 예시 매핑 YAML 과 같은 글에서 읽는다 */
    public static Mapping mapping() throws IOException {
        return Mapping.parse(Mapping.exampleYaml(all(), HEADER_ROW, FIRST_ROW));
    }

    /** 문서 번호의 내장 양식 */
    public static Form of(String no) {
        return all().stream().filter(f -> f.no().equals(no)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("내장 양식이 없는 문서: " + no));
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
