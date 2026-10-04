package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * PR #38 리뷰 — README 의 배치 명령 표가 CLI 와 맞는다. 새 명령·옵션을 더하면 README 도.
 * ① 하위 명령 이름이 README 에 「`이름 …`」 또는 「`이름`」 으로 있다(serve·version·make-templates 는 표 밖)
 * ② README 의 「`이름 … --옵션 …`」 에 적힌 옵션이 그 명령(또는 그 아래 명령)에 실제로 있다
 */
class ReadmeCommandsTest {

    static final Set<String> NOT_BATCH = Set.of("serve", "version", "make-templates");

    static String readme() throws Exception {
        return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
    }

    @Test
    void readmeListsEveryBatchCommand() throws Exception {
        String readme = readme();
        List<String> missing = new ArrayList<>();
        for (String name : Main.commandLine().getSubcommands().keySet()) {
            // 표 칸에 「`이름 …`」 또는 「`이름`」 으로 — 한 칸에 둘을 적은 줄(snapshots · diff)도 있다
            if (!NOT_BATCH.contains(name) && !readme.contains("`" + name + " ") && !readme.contains("`" + name + "`")) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(), "README 배치 명령 표에 없는 명령: " + missing);
    }

    /** 명령과 그 아래 명령의 옵션 이름 전부 */
    static Set<String> options(CommandLine c) {
        Set<String> out = new HashSet<>();
        c.getCommandSpec().options().forEach(o -> out.addAll(List.of(o.names())));
        c.getSubcommands().values().forEach(s -> out.addAll(options(s)));
        return out;
    }

    @Test
    void readmeOptionsExist() throws Exception {
        String readme = readme();
        Map<String, CommandLine> subs = Main.commandLine().getSubcommands();
        List<String> wrong = new ArrayList<>();
        int rows = 0;
        // 표의 한 줄: 첫 칸에 적힌 명령들(「`snapshots` · `diff …`」)의 옵션을 모아, 그 줄 어디에든(설명 칸 포함) 적힌 --옵션을 맞댄다
        for (String line : readme.split("\n")) {
            if (!line.startsWith("| `")) {
                continue;
            }
            String first = line.substring(1, line.indexOf('|', 1));
            Set<String> have = new HashSet<>();
            Set<String> names = new HashSet<>();
            Matcher m = Pattern.compile("`([a-z][a-z-]*)").matcher(first);
            while (m.find()) {
                CommandLine c = subs.get(m.group(1));
                if (c != null && !NOT_BATCH.contains(m.group(1))) {
                    have.addAll(options(c));
                    names.add(m.group(1));
                }
            }
            if (names.isEmpty()) {
                continue;
            }
            rows++;
            Matcher o = Pattern.compile("--[a-z][a-z-]*").matcher(line);
            while (o.find()) {
                if (!have.contains(o.group())) {
                    wrong.add(names + " " + o.group());
                }
            }
        }
        assertTrue(rows >= 10, "README 배치 명령 표 줄을 못 찾았다: " + rows);
        assertTrue(wrong.isEmpty(), "README 에 적혔지만 CLI 에 없는 옵션: " + wrong);
    }

    /** PR #38 리뷰 9차 — 표 밖 글의 옵션: 「`run.bat --x`」 는 serve 의 옵션, 스크립트(`*.sh`) 칸 밖의 나머지 `--x` 는 어느 명령에든 있다 */
    @Test
    void readmeProseOptionsExist() throws Exception {
        CommandLine root = Main.commandLine();
        Set<String> serve = options(root.getSubcommands().get("serve"));
        Set<String> any = options(root);
        List<String> wrong = new ArrayList<>();
        int seen = 0;
        for (String line : readme().split("\n")) {
            if (line.startsWith("|") || line.contains("scripts/")) { // 표(위 시험)·저장소 스크립트 줄(package.sh --with 등, CLI 밖)
                continue;
            }
            Matcher run = Pattern.compile("`run\\.bat((?: [^`]*)?)`").matcher(line);
            while (run.find()) {
                Matcher o = Pattern.compile("--[a-z][a-z-]*").matcher(run.group(1));
                while (o.find()) {
                    seen++;
                    if (!serve.contains(o.group())) {
                        wrong.add("run.bat " + o.group());
                    }
                }
            }
            Matcher o = Pattern.compile("--[a-z][a-z-]*").matcher(line.replaceAll("`run\\.bat[^`]*`", "").replaceAll("`[^`]*\\.sh\\b[^`]*`", "")); // 스크립트(package.sh 등) 옵션은 CLI 밖
            while (o.find()) {
                seen++;
                if (!any.contains(o.group())) {
                    wrong.add(o.group());
                }
            }
        }
        assertTrue(seen >= 3, "README 표 밖 옵션을 못 찾았다: " + seen);
        assertTrue(wrong.isEmpty(), "README 표 밖에 적혔지만 CLI 에 없는 옵션: " + wrong);
    }
}
