package kr.ejg.toolbox.core.gen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.text.Csv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V-4 — DdlReader·DtoGenerator·InsertGen 을 실물 DDL 전부에 돌린다(eGov 공통컴포넌트 8방언 + 모듈별, Oracle 샘플 스키마, chinook 방언 여섯).
 * <ul>
 *   <li>등급 A: 예외 · 읽은 테이블이 주석·문자열 밖 {@code CREATE TABLE} 수보다 적음 · 만든 DTO 가 컴파일 안 됨 · INSERT 생성(방언 6) 예외</li>
 *   <li>등급 B: 못 읽은 줄({@code unreadable})이 있는 파일</li>
 * </ul>
 */
@Tag("corpus")
class DdlCorpusTest {

    record Ddl(String rel, String dialect, String text) {
    }

    static final List<Ddl> DDLS = new ArrayList<>();
    /**
     * 참조용 — 줄머리의 CREATE TABLE 만(「GRANT create table」·「REM Create table …」 같은 줄 가운데는 문장이 아니다, 첫 판 오탐).
     * Oracle 객체 테이블 {@code CREATE TABLE x OF type} 은 컬럼이 타입에 있어 DDL 만으로 못 읽는다 — 셈에서 뺀다(한계)
     */
    static final Pattern CREATE_TABLE = Pattern.compile(
            "(?im)^[ \\t]*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:(?:GLOBAL|LOCAL)\\s+)?(?:TEMPORARY\\s+|TEMP\\s+|UNLOGGED\\s+)?TABLE\\b");

    @BeforeAll
    static void load() throws IOException {
        for (Path p : CorpusFiles.files("egov", "*.sql")) {
            String rel = CorpusFiles.rel(p);
            Matcher m = Pattern.compile("/ddl/([a-z]+)/").matcher(rel);
            if (m.find()) {
                DDLS.add(new Ddl(rel, dialect(m.group(1)), read(p)));
            }
        }
        for (Path p : CorpusFiles.files("db-samples", "*.sql")) {
            String t = read(p);
            if (CREATE_TABLE.matcher(strip(t)).find()) {
                DDLS.add(new Ddl(CorpusFiles.rel(p), "oracle", t));
            }
        }
        for (Path p : CorpusFiles.files("chinook", "*.sql")) {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            String d = n.contains("oracle") ? "oracle" : n.contains("mysql") ? "mariadb" : n.contains("postgre") ? "postgresql"
                    : n.contains("sqlserver") ? "mssql" : n.contains("db2") ? "db2" : "sqlite";
            DDLS.add(new Ddl(CorpusFiles.rel(p), d, read(p)));
        }
    }

    static String dialect(String egovDir) {
        return switch (egovDir) {
            case "maria", "mysql" -> "mariadb";
            case "postgres" -> "postgresql";
            case "tibero" -> "tibero";
            case "oracle" -> "oracle";
            default -> egovDir; // altibase·cubrid·goldilocks — TypeMapping 이 모르면 JDBC 타입 이름으로
        };
    }

    static String read(Path p) throws IOException {
        String t = Csv.decode(Files.readAllBytes(p));
        return t.startsWith(String.valueOf((char) 0xFEFF)) ? t.substring(1) : t;
    }

    /** 참조용 — 블록·줄 주석과 '…' 문자열을 뗀다(DdlReader 와 따로 짠 단순판) */
    static String strip(String s) {
        // 정규식 교대(`'(?:[^']|'')*'`)는 긴 문자열에서 스택이 넘친다(첫 판 StackOverflow) — 한 글자씩
        StringBuilder o = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                int j = i + 1;
                while (j < s.length() && !(s.charAt(j) == '\'' && (j + 1 >= s.length() || s.charAt(j + 1) != '\''))) {
                    j += s.charAt(j) == '\'' ? 2 : 1;
                }
                o.append("''");
                i = j;
            } else if (c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-') {
                int e = s.indexOf('\n', i);
                i = e < 0 ? s.length() : e - 1;
                o.append(' ');
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                i = e < 0 ? s.length() : e + 1;
                o.append(' ');
            } else {
                o.append(c);
            }
        }
        return o.toString();
    }

    /** 서로 다른 테이블 이름 수 — 한 파일에서 같은 테이블을 두 번 만드는 DDL 이 있다(eGov sym.mnu 의 COMTNSITEMAP) */
    static int creates(String text) {
        Matcher m = Pattern.compile(CREATE_TABLE.pattern() + "\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"\\[\\]`]+)(\\s+OF\\b)?")
                .matcher(strip(text));
        java.util.Set<String> names = new java.util.HashSet<>();
        while (m.find()) {
            if (m.group(2) != null) {
                continue; // 객체 테이블 — 한계
            }
            String n = m.group(1).replaceAll("[\"\\[\\]`]", "").toUpperCase(Locale.ROOT);
            names.add(n.substring(n.lastIndexOf('.') + 1));
        }
        return names.size();
    }

    @Test
    void everyCreateTableIsRead() {
        List<String> bad = new ArrayList<>();
        for (Ddl d : DDLS) {
            try {
                DdlReader.Result r = DdlReader.read(d.text());
                int got = r.tables().size();
                int want = creates(d.text());
                // 놓친 테이블을 unreadable 로 드러냈으면 B(아래 baseline) — 조용히 놓친 것만 A
                if (got < want && r.unreadable().isEmpty()) {
                    bad.add(d.rel() + " 테이블 " + got + "/" + want);
                }
            } catch (RuntimeException e) {
                bad.add(d.rel() + " 예외 " + e.getClass().getSimpleName());
            }
        }
        CorpusFiles.none("DdlReader", bad, DDLS.size());
    }

    @Test
    void unreadableLinesBaseline() throws IOException {
        List<String> b = new ArrayList<>();
        for (Ddl d : DDLS) {
            if (!DdlReader.read(d.text()).unreadable().isEmpty()) {
                b.add(d.rel());
            }
        }
        CorpusFiles.baseline("ddl-unreadable", b, DDLS.size());
    }

    @Test
    void dtosCompile() throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DtoGenerator gen = new DtoGenerator(TypeMapping.load());
        Path root = Path.of("target", "corpus-dto").toAbsolutePath();
        Map<Path, String> fileOf = new LinkedHashMap<>();
        int i = 0;
        for (Ddl d : DDLS) {
            String pkg = "corpus.f" + (i++);
            for (Table t : DdlReader.read(d.text()).tables()) {
                DtoGenerator.Source s = gen.generate(t, new DtoGenerator.Options(pkg, DtoGenerator.Style.RECORD, List.of(), Map.of(), Map.of(), d.dialect(),
                        Validation.Ns.JAVAX)); // 1-31b — 표본 CHECK 까지 어노테이션으로
                Path f = root.resolve(pkg.replace('.', '/')).resolve(s.className() + ".java");
                Files.createDirectories(f.getParent());
                Files.writeString(f, s.text(), StandardCharsets.UTF_8);
                fileOf.put(f, d.rel() + " " + t.name());
            }
        }
        List<Path> all = new ArrayList<>(fileOf.keySet());
        List<String> bad = new ArrayList<>();
        for (int k = 0; k < all.size(); k += 400) {
            List<Path> part = all.subList(k, Math.min(all.size(), k + 400));
            List<String> args = new ArrayList<>(List.of("-encoding", "UTF-8", "-proc:none", "-cp", System.getProperty("java.class.path"), "-d",
                    root.resolve("classes").toString()));
            part.forEach(p -> args.add(p.toString()));
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            if (javac.run(null, null, new PrintStream(err, true, StandardCharsets.UTF_8), args.toArray(String[]::new)) != 0) {
                for (Path p : part) {
                    if (err.toString(StandardCharsets.UTF_8).contains(p.getFileName().toString())) {
                        bad.add(fileOf.get(p));
                    }
                }
                if (bad.isEmpty()) {
                    bad.add("javac 실패(파일 특정 못 함) " + err.toString(StandardCharsets.UTF_8).lines().limit(3).toList());
                }
            }
        }
        CorpusFiles.none("DTO 컴파일(테이블 " + all.size() + ")", bad, all.size());
    }

    @Test
    void insertGenerationNeverThrows() {
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (Ddl d : DDLS) {
            for (Table t : DdlReader.read(d.text()).tables()) {
                for (String dialect : InsertGen.DIALECTS) {
                    n++;
                    try {
                        InsertGen.generate(t, InsertGen.checkIns(d.text()), new InsertGen.Options(dialect, 3, false, true, LocalDate.of(2026, 1, 31)), null);
                        InsertGen.generate(t, Map.of(), new InsertGen.Options(dialect, 1, true, false, LocalDate.of(2026, 1, 31)), null);
                    } catch (RuntimeException e) {
                        bad.add(d.rel() + " " + t.name() + " " + dialect + " " + e.getClass().getSimpleName());
                    }
                }
            }
        }
        CorpusFiles.none("InsertGen(테이블×방언 " + n + ")", bad, n);
    }

    /** 출처·방언별 파일·테이블·못 읽은 줄 수 — 코드 본문 없이 수만 */
    @Test
    void summaryGolden() {
        Map<String, int[]> by = new TreeMap<>();
        for (Ddl d : DDLS) {
            DdlReader.Result r = DdlReader.read(d.text());
            int[] a = by.computeIfAbsent(d.rel().substring(0, d.rel().indexOf('/')) + ":" + d.dialect(), k -> new int[3]);
            a[0]++;
            a[1] += r.tables().size();
            a[2] += r.unreadable().size();
        }
        Map<String, String> out = new TreeMap<>();
        by.forEach((k, a) -> out.put(k, "파일 " + a[0] + " · 테이블 " + a[1] + " · 못 읽은 줄 " + a[2]));
        GoldenFiles.assertJson("corpus/ddl-summary.json", out);
    }
}
