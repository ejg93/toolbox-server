package kr.ejg.toolbox.web;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.report.XlsxWriter;
import kr.ejg.toolbox.core.sqlrun.ResultTable;

/**
 * 내려받기 파일 자리와 값 표 xlsx — 코드 검사(5-5·5-8)·프로그램 분석(6-7) 공용.
 * 자리는 {@code <프로필 output.dir 또는 out>/<프로필>/<yyyyMMdd-HHmmss>/}. 열 타입은 열 정의로 직접 받는다 —
 * 이름 부분 일치로 고르지 않는다(5-11, PR #24 AI 리뷰 ②). 칸 형식은 XlsxWriter 가 값의 타입으로 정한다(행 상한 없음)
 */
final class Outputs {

    private Outputs() {
    }

    static ResultTable.Col text(String name) {
        return new ResultTable.Col(name, "VARCHAR");
    }

    static ResultTable.Col num(String name) {
        return new ResultTable.Col(name, "INTEGER");
    }

    /** 이번 내려받기 폴더 — 같은 요청의 파일 여럿이 한 폴더에 들게 한 번만 부른다 */
    static Path dir(Profile p) {
        String base = p != null && p.output() != null && p.output().dir() != null ? p.output().dir() : "out";
        return Path.of(base, p == null ? "default" : p.name(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
                .toAbsolutePath();
    }

    static Path xlsx(Path dir, String name, List<ResultTable.Col> cols, List<List<Object>> rows) throws IOException {
        Path file = dir.resolve(name);
        XlsxWriter.write(new ResultTable(cols, rows, false, -1, 0), file);
        return file;
    }

    /** 시트 여럿 한 파일(6-24) — 이름 → 표, 넣은 순서대로 */
    static Path xlsx(Path dir, String name, java.util.LinkedHashMap<String, ResultTable> sheets) throws IOException {
        Path file = dir.resolve(name);
        XlsxWriter.write(sheets, file);
        return file;
    }

    static ResultTable table(List<ResultTable.Col> cols, List<List<Object>> rows) {
        return new ResultTable(cols, rows, false, -1, 0);
    }

    static Path xlsx(Profile p, String name, List<ResultTable.Col> cols, List<List<Object>> rows) throws IOException {
        return xlsx(dir(p), name, cols, rows);
    }
}
