package kr.ejg.toolbox.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.CorpusHr;
import kr.ejg.toolbox.core.check.CheckRunner;
import kr.ejg.toolbox.core.check.Finding;
import kr.ejg.toolbox.core.check.RuleSet;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.profile.ProfileStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-20 — CLI 를 실물에 돌려 화면·엔진과 같은 수가 나오는지(컨테이너 없음).
 * <ul>
 *   <li>eGov check — 같은 프로필로 엔진({@link CheckRunner})을 직접 돈 규칙별 건수와 같다(규칙 묶음은 프로필에 따라 달라 골든과는 안 맞댄다)</li>
 *   <li>eGov analyze — 골든 {@code corpus/analyze-egov.json} 의 programs·tables</li>
 *   <li>HR(H2) — snapshot → ddl(DdlReader 로 읽어 표 7) → generate(새 파일 70) → deliverable(파일 + 건너뜀 = 11) → logical masking(EMPLOYEES 넷)</li>
 * </ul>
 * 등급 A: 끝 코드 0 아님·수 불일치. 새 골든은 안 만든다.
 */
@Tag("corpus")
class CliCorpusTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    @Test
    void cliMatchesEngineAndCorpus() throws Exception {
        CorpusFiles.verify();
        List<String> a = new ArrayList<>();
        CorpusHr.Loaded hr = CorpusHr.open();
        try (CliFixture fx = new CliFixture(tmp).withTemplates()) {
            Files.writeString(tmp.resolve("profiles/hr.yaml"), "name: hr\nframework: egov35\nconnections:\n  - id: hr\n    dialect: h2\n    url: " + hr.url()
                    + "\n    user: sa\noutput:\n  dir: '" + tmp.resolve("out").toString().replace('\\', '/') + "'\n", StandardCharsets.UTF_8);
            Batch.passwordHook(id -> new char[0]);
            Path egov = CorpusFiles.root().resolve("egov");

            // ① check — 엔진과 규칙별 건수
            CliFixture.Run c = fx.run("check", egov.toString(), "--profile", "hr", "--json");
            if (c.code() != 0) {
                a.add("check 끝 코드 " + c.code() + " " + c.err());
            } else {
                Map<String, Integer> cli = new TreeMap<>();
                for (JsonNode f : JSON.readTree(c.out()).path("findings")) {
                    cli.merge(f.path("rule").asText(), 1, Integer::sum);
                }
                RuleSet rules = RuleSet.load(new ProfileStore(tmp.resolve("profiles"), tmp.resolve("data")).load("hr"), Path.of(""));
                Map<String, Integer> engine = new TreeMap<>();
                for (Finding f : CheckRunner.run(egov.toString(), rules, new LocalFiles(tmp.resolve("data2")), null).findings()) {
                    engine.merge(f.rule(), 1, Integer::sum);
                }
                if (!cli.equals(engine)) {
                    a.add("check 규칙별 건수 CLI " + cli + " ≠ 엔진 " + engine);
                }
                if (cli.isEmpty()) {
                    a.add("check 건수 0 — eGov 에 걸리는 규칙이 있어야 한다");
                }
            }

            // ② analyze — 골든
            JsonNode golden = JSON.readTree(Path.of("src/test/resources/golden/corpus/analyze-egov.json").toFile());
            CliFixture.Run an = fx.run("analyze", egov.toString(), "--profile", "hr", "--json");
            if (an.code() != 0) {
                a.add("analyze 끝 코드 " + an.code() + " " + an.err());
            } else {
                JsonNode n = JSON.readTree(an.out());
                if (n.path("programs").size() != golden.path("programs").asInt() || n.path("tables").size() != golden.path("tables").asInt()) {
                    a.add("analyze 프로그램·표 " + n.path("programs").size() + "·" + n.path("tables").size() + " ≠ 골든 " + golden.path("programs") + "·"
                            + golden.path("tables"));
                }
            }

            // ③ HR
            CliFixture.Run s = fx.run("snapshot", "--conn", "hr", "--profile", "hr");
            if (s.code() != 0) {
                a.add("snapshot " + s.err());
            } else {
                CliFixture.Run d = fx.run("ddl", "--snapshot", "latest", "--target", "postgresql", "--profile", "hr", "--json");
                if (d.code() != 0) {
                    a.add("ddl " + d.err());
                } else {
                    String sql = Files.readString(Path.of(JSON.readTree(d.out()).path("path").asText()));
                    int tables = DdlReader.read(sql).tables().size();
                    if (tables != 7) {
                        a.add("ddl 을 DdlReader 로 읽은 표 " + tables + " ≠ 7");
                    }
                }
                Files.createDirectories(tmp.resolve("gen")); // 라우트는 있는 출력 폴더만 받는다
                CliFixture.Run g = fx.run("generate", "--snapshot", "latest", "--tables",
                        "REGIONS,COUNTRIES,LOCATIONS,DEPARTMENTS,JOBS,EMPLOYEES,JOB_HISTORY", "--set", "egov35", "--package", "kr.go.hr", "--out", "gen",
                        "--profile", "hr", "--json");
                if (g.code() != 0 || JSON.readTree(g.out()).path("created").asInt() != 70) {
                    a.add("generate " + g.code() + " " + g.out() + g.err());
                }
                CliFixture.Run dl = fx.run("deliverable", "--snapshot", "latest", "--profile", "hr", "--json");
                if (dl.code() != 0 || JSON.readTree(dl.out()).path("files").size() + JSON.readTree(dl.out()).path("skipped").size() != 11) { // 08 은 코드 접속이 없으면 건너뜀
                    a.add("deliverable " + dl.code() + " " + dl.out() + dl.err());
                }
                CliFixture.Run m = fx.run("logical", "masking", "--snapshot", "latest", "--profile", "hr", "--json");
                if (m.code() != 0) {
                    a.add("masking " + m.err());
                } else {
                    List<String> emp = new ArrayList<>();
                    for (JsonNode cand : JSON.readTree(m.out()).path("candidates")) {
                        if (cand.path("table").asText().equalsIgnoreCase("EMPLOYEES")) {
                            emp.add(cand.path("col").asText().toUpperCase());
                        }
                    }
                    for (String col : List.of("FIRST_NAME", "LAST_NAME", "EMAIL", "PHONE_NUMBER")) {
                        if (!emp.contains(col)) {
                            a.add("masking EMPLOYEES 에 " + col + " 없음 " + emp);
                        }
                    }
                }
            }
        } finally {
            hr.conn().close();
        }
        CorpusFiles.none("CLI 실물 왕복(eGov check·analyze + HR 다섯)", a, 7);
    }
}
