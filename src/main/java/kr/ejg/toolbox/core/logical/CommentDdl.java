package kr.ejg.toolbox.core.logical;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 변환 결과 → COMMENT DDL. 순수본 {@code genDDL} 과 글자까지 같다(골든은 순수본을 Puppeteer 로 뜬 것, 3-5).
 * <ul>
 *   <li>테이블(선택) 다음 컬럼, 순서는 변환 결과 그대로</li>
 *   <li>영문이 남은 줄(none·mix)은 실행형이면 앞에 {@code -- [검토] }, 대조표형(Sybase)이면 뒤에 {@code ← [검토] …}</li>
 *   <li>작은따옴표는 두 번</li>
 *   <li>생성 시각은 순수본 {@code toLocaleString('ko-KR')} 모양 — 테스트는 시각을 넘긴다</li>
 * </ul>
 */
public final class CommentDdl {

    /** text 는 파일 전체, lines 는 머리말 뺀 줄 수, reviewCount 는 [검토] 줄 수 */
    public record Ddl(String text, int lines, int reviewCount, boolean executable) {
    }

    /** 순수본 ko-KR 모양 — 「2026. 9. 27. 오전 9:00:00」 */
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy. M. d. a h:mm:ss", Locale.KOREAN);

    private CommentDdl() {
    }

    public static Ddl generate(LogicalRun.Result r, Dialect d, boolean includeTables, LocalDateTime now) {
        List<String> lines = new ArrayList<>();
        int review = 0;
        if (includeTables) {
            for (LogicalRun.TableRow t : r.tableRows()) {
                if (t.name().isEmpty()) {
                    continue;
                }
                boolean need = need(t.src());
                if (need) {
                    review++;
                }
                lines.add(mark(need, d) + d.table(t.owner(), t.table(), sq(t.name())) + tail(need, d));
            }
        }
        for (LogicalRun.Row c : r.rows()) {
            if (c.name().isEmpty()) {
                continue;
            }
            boolean need = need(c.src());
            if (need) {
                review++;
            }
            lines.add(mark(need, d) + d.column(c.owner(), c.table(), c.col(), sq(c.name())) + tail(need, d));
        }
        String stamp = now.format(STAMP);
        StringBuilder head = new StringBuilder();
        if (d.noExec()) {
            head.append("-- 생성 ").append(stamp).append(" · [").append(d.label()).append("] · 총 ").append(lines.size())
                    .append("줄 — 전부 주석, 실행 가능 0줄\n")
                    .append(d.note()).append('\n')
                    .append("-- 이 파일은 실행용이 아니다. 물리명과 논리명을 나란히 놓은 대조표다\n")
                    .append("-- 논리명은 [매칭결과 CSV 저장] 으로 받아 DA#·erwin 의 엔티티·속성 일괄 편집에 붙여 모델에 직접 넣는다\n");
            if (review > 0) {
                head.append("-- ※ 줄 끝 [검토] ").append(review).append("줄은 논리명에 영문이 남았다. 5번 랭킹에 한글을 넣고 다시 변환할 것\n");
            }
            head.append('\n');
        } else {
            head.append("-- 생성 ").append(stamp).append(" · [").append(d.label()).append("] · 총 ").append(lines.size())
                    .append("줄 (실행 ").append(lines.size() - review).append(" / 주석처리 ").append(review).append(")\n");
            if (review > 0) {
                head.append("-- ※ [검토] 표시 ").append(review)
                        .append("줄은 논리명에 영문이 남아 주석 처리됨. 검토 후 '-- [검토] ' 지우면 실행됨\n");
            }
            head.append(d.note()).append('\n')
                    .append("-- 용도: erwin 스크립트 리버스용. 추출한 스키마 DDL(schema.sql) 맨 끝에 붙여 파일 하나로 만든다\n")
                    .append("-- 운영 DB 실행은 별도 승인 사항\n\n");
        }
        return new Ddl(head + String.join("\n", lines), lines.size(), review, !d.noExec());
    }

    /** 실행할 줄만(3-9 직접 실행) — [검토] 로 주석 처리된 줄과 대조표형 방언은 뺀다 */
    public static List<String> executableLines(LogicalRun.Result r, Dialect d, boolean includeTables) {
        List<String> out = new ArrayList<>();
        if (d.noExec()) {
            return out;
        }
        if (includeTables) {
            r.tableRows().stream().filter(t -> !t.name().isEmpty() && !need(t.src()))
                    .forEach(t -> out.add(d.table(t.owner(), t.table(), sq(t.name()))));
        }
        if (d == Dialect.MARIADB) {
            return out; // 컬럼 줄은 사람이 타입을 채워야 돈다(/* 컬럼타입 명시 필요 */) — 그대로 실행하면 전부 실패(V-9 실물 237/237)
        }
        r.rows().stream().filter(c -> !c.name().isEmpty() && !need(c.src()))
                .forEach(c -> out.add(d.column(c.owner(), c.table(), c.col(), sq(c.name()))));
        return out;
    }

    static boolean need(String src) {
        return src.equals("none") || src.equals("mix");
    }

    private static String mark(boolean need, Dialect d) {
        return need && !d.noExec() ? "-- [검토] " : "";
    }

    private static String tail(boolean need, Dialect d) {
        return need && d.noExec() ? "   ← [검토] 논리명에 영문 잔존" : "";
    }

    static String sq(String s) {
        return s.replace("'", "''");
    }
}
