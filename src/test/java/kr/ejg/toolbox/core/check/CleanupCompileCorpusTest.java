package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-34 마무리 — 코드 정리를 실제로 덮어쓴 뒤 컴파일한다. 의존성 없이 JDK 만으로 컴파일되는 표본(commons 셋)의 사본에
 * {@link CleanupRunner} 로 미리보기 → 결과 폴더에 쓰기 → 사본 위에 얹고 javac 를 돌린다(9-3b). 등급 A: 컴파일 오류 0 · 적용 실패 0.
 * 원본이 컴파일되는 것은 2026-10-22 손으로 쟀다(같은 javac, 오류 0).
 */
@Tag("corpus")
class CleanupCompileCorpusTest {

    static final List<String> SOURCES = List.of("commons-lang", "commons-io", "commons-collections");

    @TempDir
    Path tmp;

    @Test
    void cleanedSourcesCompile() throws Exception {
        CorpusFiles.verify();
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        RuleSet rules = CheckCorpusTest.rules(false);
        List<String> a = new ArrayList<>();
        int written = 0;
        for (String source : SOURCES) {
            Path from = CorpusFiles.root().resolve(source);
            Path copy = tmp.resolve("copy").resolve(source);
            List<Path> javaFiles = new ArrayList<>();
            for (Path p : CorpusFiles.files(source, "*.java")) {
                Path t = copy.resolve(from.relativize(p).toString());
                Files.createDirectories(t.getParent());
                Files.copy(p, t);
                javaFiles.add(t);
            }
            CleanupRunner.Preview pv = CleanupRunner.preview(copy.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
            List<CleanupRunner.FileRef> refs = new ArrayList<>();
            pv.rows().stream().filter(CleanupRunner.FileRow::changed).forEach(r -> refs.add(new CleanupRunner.FileRef(r.rel(), r.hash(), r.outHash())));
            Path out = tmp.resolve("out").resolve(source);
            Path result = files.resultRoot(null, out.resolve("cleanup"), copy, "cleanup", source);
            CleanupRunner.Applied ap = CleanupRunner.apply(copy.toString(), rules, Cleanup.RULES, "LF", files, null, refs, List.of(), result, out);
            written += ap.written();
            // 9-3b — 원본은 안 고친다. 결과 폴더의 고친 파일을 사본 위에 얹은 뒤 컴파일(전에 덮어쓰던 것과 같은 판정)
            for (CleanupRunner.Written w : ap.rows()) {
                if (w.ok()) {
                    Files.copy(Path.of(w.path()), copy.resolve(w.rel()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (ap.failed() + ap.stale() > 0) {
                a.add(source + " 적용 실패 " + ap.failed() + " · 낡음 " + ap.stale());
            }
            a.addAll(compile(source, javaFiles, tmp.resolve("classes").resolve(source)));
        }
        CorpusFiles.none("코드 정리 뒤 컴파일(고친 파일 " + written + ")", a, written);
        assertTrue(written > 500, "고친 파일이 너무 적다: " + written);
    }

    /** javac — 오류만(경고는 안 본다). 사유는 경로·줄·javac 글 첫 줄 */
    private static List<String> compile(String source, List<Path> javaFiles, Path classes) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        Files.createDirectories(classes);
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diags, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(javaFiles);
            javac.getTask(null, fm, diags, List.of("-proc:none", "-nowarn", "-encoding", "UTF-8", "-d", classes.toString()), null, units).call();
        }
        List<String> out = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                String name = d.getSource() == null ? "?" : Path.of(d.getSource().toUri()).getFileName().toString();
                out.add(source + "/" + name + ":" + d.getLineNumber() + " " + d.getMessage(Locale.ROOT).lines().findFirst().orElse(""));
            }
        }
        return out;
    }
}
