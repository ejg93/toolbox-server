package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.Plugin;
import edu.umd.cs.findbugs.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 5-38a — SpotBugs 코어 플러그인이 클래스패스에서 읽히고, FindSecBugs 자원 jar 를 꺼내 플러그인으로 읽는다 */
class SpotBugsLoadTest {

    @Test
    void corePluginLoads() {
        Plugin core = DetectorFactoryCollection.instance().getCorePlugin();
        assertNotNull(core, "SpotBugs 코어 플러그인이 없다(findbugs.xml)");
        long n = core.getDetectorFactories().size();
        assertTrue(n >= 100, "코어 검출기 " + n);
    }

    @Test
    void findSecBugsResourceLoadsAsPlugin() throws Exception {
        // 플러그인 클래스로더가 jar 를 쥔 채라(Windows) @TempDir 은 못 지운다 — target 아래에
        Path data = Path.of("target", "spotbugs-load-test");
        Path jar = Analyzers.findSecBugsJar(data);
        long size = Files.size(jar);
        assertTrue(size > 100_000, "플러그인 jar " + size);
        assertEquals(jar, Analyzers.findSecBugsJar(data), "두 번째는 그대로");
        Plugin p = Plugin.loadCustomPlugin(jar.toFile(), new Project());
        assertEquals("com.h3xstream.findsecbugs", p.getPluginId());
        assertTrue(p.getDetectorFactories().size() >= 50, "FindSecBugs 검출기 " + p.getDetectorFactories().size());
    }
}
