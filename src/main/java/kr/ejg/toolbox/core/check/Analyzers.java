package kr.ejg.toolbox.core.check;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 외부 분석기 재료(5-38, 설계 34). FindSecBugs 플러그인은 app.jar 안 자원({@code /analyzers/findsecbugs-plugin.jar})인데 SpotBugs 가
 * 플러그인을 파일로만 읽어 도구 자기 출력 폴더({@code <data>/analyzers/})로 한 번 꺼낸다 — 크기가 같으면 다시 안 쓴다
 */
public final class Analyzers {

    static final String FINDSECBUGS = "/analyzers/findsecbugs-plugin.jar";

    private Analyzers() {
    }

    /** @return 꺼낸 플러그인 파일 */
    public static Path findSecBugsJar(Path dataDir) {
        Path dir = dataDir.resolve("analyzers");
        Path out = dir.resolve("findsecbugs-plugin.jar");
        try (InputStream in = Analyzers.class.getResourceAsStream(FINDSECBUGS)) {
            if (in == null) {
                throw new IllegalStateException(FINDSECBUGS + " 가 app.jar 에 없다 — mvn package 의 dependency:copy 를 본다");
            }
            byte[] b = in.readAllBytes();
            if (Files.isRegularFile(out) && Files.size(out) == b.length) {
                return out;
            }
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "findsecbugs", ".tmp");
            Files.write(tmp, b);
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
