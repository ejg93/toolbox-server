package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PR #38 리뷰 — README 의 배치 명령 표가 CLI 의 하위 명령을 다 든다(새 명령을 더하면 README 도). serve·version·make-templates 는 표 밖 */
class ReadmeCommandsTest {

    static final Set<String> NOT_BATCH = Set.of("serve", "version", "make-templates");

    @Test
    void readmeListsEveryBatchCommand() throws Exception {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (String name : Main.commandLine().getSubcommands().keySet()) {
            // 표 칸에 「`이름 …`」 또는 「`이름`」 으로 — 한 칸에 둘을 적은 줄(snapshots · diff)도 있다
            if (!NOT_BATCH.contains(name) && !readme.contains("`" + name + " ") && !readme.contains("`" + name + "`")) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(), "README 배치 명령 표에 없는 명령: " + missing);
    }
}
