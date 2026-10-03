package kr.ejg.toolbox.core.gen;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.CorpusHr;
import kr.ejg.toolbox.core.dialect.MetaSources;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-17 — 선택 표본 egov35-lib(전자정부 3.5.0 실행환경 jar, {@code corpus-fetch.sh egov35-lib})가 있을 때만: HR 표 7 을 egov35 세트로 생성해
 * javac 로 컴파일하고(A — 오류 진단 0), 생성 oracle 매퍼를 MyBatis 로 파싱(A)한 뒤 H2 HR 에서 표마다 건수·목록 첫 쪽을 실제로 돌린다(A).
 * 등록·수정·삭제는 안 돌린다(FK). jar 가 없으면 건너뛰고 사유를 낸다. egov5 는 javac 를 안 돈다 — 5.x jar 좌표가 그 저장소에 없다
 */
@Tag("corpus")
class GeneratorJavacTest {

    static final Pattern VO_TYPE = Pattern.compile("<resultMap id=\"\\w+\" type=\"([\\w.]+)\">");
    static final Pattern NAMESPACE = Pattern.compile("<mapper namespace=\"(\\w+)\">");

    @TempDir
    Path tmp;

    @Test
    void compilesAndRunsMappers() throws Exception {
        Path lib = CorpusFiles.root().resolve("egov35-lib");
        List<Path> jars = new ArrayList<>();
        if (Files.isDirectory(lib)) {
            try (Stream<Path> s = Files.list(lib)) {
                jars.addAll(s.filter(p -> p.toString().endsWith(".jar")).sorted().toList());
            }
        }
        Assumptions.assumeTrue(!jars.isEmpty(), "선택 표본 egov35-lib 가 없다 — bash scripts/corpus-fetch.sh egov35-lib 로 받으면 돈다");

        CorpusHr.Loaded hr = CorpusHr.open();
        List<String> a = new ArrayList<>();
        try {
            List<Schema> snap = MetaSources.forDialect("h2", hr.conn()).collect(new Scope(List.of("PUBLIC"), null, null, null));
            List<Table> tables = snap.get(0).tables();
            TemplateSet set = TemplateSet.load(GenTemplatesTest.GEN, "egov35");
            Path out = tmp.resolve("out");
            Files.createDirectories(out);
            GenModel.Options o = new GenModel.Options("kr.go.hr", null, List.of(), Map.of(), "oracle", set.vars());
            Generator.run(set, tables, o, Map.of(), GenModelTest.TYPES, out, new LocalFiles(tmp.resolve("data")), "UTF-8", "LF", null);

            // javac
            List<File> sources = new ArrayList<>();
            List<Path> mappers = new ArrayList<>();
            try (Stream<Path> s = Files.walk(out)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    if (p.toString().endsWith(".java")) {
                        sources.add(p.toFile());
                    } else if (p.toString().endsWith("_SQL_oracle.xml")) {
                        mappers.add(p);
                    }
                }
            }
            Path classes = tmp.resolve("classes");
            Files.createDirectories(classes);
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
            String cp = String.join(File.pathSeparator, jars.stream().map(Path::toString).toList());
            try (StandardJavaFileManager fm = javac.getStandardFileManager(diag, Locale.ROOT, StandardCharsets.UTF_8)) {
                boolean ok = javac.getTask(null, fm, diag, List.of("-proc:none", "-encoding", "UTF-8", "-classpath", cp, "-d", classes.toString()),
                        null, fm.getJavaFileObjectsFromFiles(sources)).call();
                for (Diagnostic<? extends JavaFileObject> d : diag.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        a.add("javac " + (d.getSource() == null ? "" : Path.of(d.getSource().toUri()).getFileName()) + ":" + d.getLineNumber()
                                + " " + d.getMessage(Locale.ROOT));
                    }
                }
                if (!ok && a.isEmpty()) {
                    a.add("javac 실패(진단 없음)");
                }
            }
            if (!a.isEmpty()) {
                CorpusFiles.none("egov35 생성물 javac(파일 " + sources.size() + ")", a, sources.size());
            }

            // 매퍼 파싱 + 실행 — 생성 VO 만 따로 올린다(jar 안 MyBatis 3.2.8 과 안 섞이게 테스트 경로의 MyBatis 로)
            try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
                ClassLoader before = Resources.getDefaultClassLoader();
                Resources.setDefaultClassLoader(loader);
                try {
                    Configuration cfg = new Configuration(new Environment("gen", new JdbcTransactionFactory(),
                            new UnpooledDataSource("org.h2.Driver", hr.url(), "sa", "")));
                    Map<String, String> voOf = new HashMap<>();
                    for (Path m : mappers) {
                        String text = Files.readString(m, StandardCharsets.UTF_8);
                        Matcher ns = NAMESPACE.matcher(text);
                        Matcher vo = VO_TYPE.matcher(text);
                        if (!ns.find() || !vo.find()) {
                            a.add("매퍼 머리 못 읽음 " + m.getFileName());
                            continue;
                        }
                        voOf.put(ns.group(1), vo.group(1));
                        try (InputStream in = Files.newInputStream(m)) {
                            new XMLMapperBuilder(in, cfg, m.getFileName().toString(), cfg.getSqlFragments()).parse();
                        } catch (RuntimeException e) {
                            a.add("매퍼 파싱 " + m.getFileName() + " " + e.getMessage());
                        }
                    }
                    SqlSessionFactory f = new SqlSessionFactoryBuilder().build(cfg);
                    try (SqlSession s = f.openSession(); Statement st = hr.conn().createStatement()) {
                        for (Table t : tables) {
                            if (t.pk() == null || t.pk().columns().isEmpty()) {
                                continue;
                            }
                            String name = DtoGenerator.className(t.name(), List.of());
                            Class<?> voClass = loader.loadClass(voOf.get(name));
                            Object vo = voClass.getDeclaredConstructor().newInstance();
                            voClass.getMethod("setFirstIndex", int.class).invoke(vo, 0);
                            voClass.getMethod("setRecordCountPerPage", int.class).invoke(vo, 5);
                            int want;
                            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t.name())) {
                                rs.next();
                                want = rs.getInt(1);
                            }
                            Integer got = s.selectOne(name + ".select" + name + "ListTotCnt", vo);
                            if (got == null || got != want) {
                                a.add(t.name() + " 건수 " + got + " ≠ " + want);
                            }
                            int rows = s.selectList(name + ".select" + name + "List", vo).size();
                            if (rows != Math.min(5, want)) {
                                a.add(t.name() + " 목록 첫 쪽 " + rows + " ≠ " + Math.min(5, want));
                            }
                        }
                    }
                } finally {
                    Resources.setDefaultClassLoader(before);
                }
            }
        } finally {
            hr.conn().close();
        }
        CorpusFiles.none("egov35 생성물 매퍼 실행(H2 HR)", a, 7);
    }
}
