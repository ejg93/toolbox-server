package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.Plugin;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 5-38a — SpotBugs 코어 플러그인이 클래스패스에서 읽히고, FindSecBugs 자원 jar 를 꺼내 플러그인으로 읽는다(5-38b 부터 SpotBugsRun 한 자리로) */
class SpotBugsLoadTest {

    @Test
    void corePluginLoads() {
        Plugin core = DetectorFactoryCollection.instance().getCorePlugin();
        assertNotNull(core, "SpotBugs 코어 플러그인이 없다(findbugs.xml)");
        long n = core.getDetectorFactories().size();
        assertTrue(n >= 100, "코어 검출기 " + n);
    }

    /**
     * PR #85 리뷰 — 반입 zip 의 jlink 런타임(package.sh JLINK_MODULES)이 SpotBugs 묶음이 쓰는 JDK 모듈을 다 품나. 빠지면 반입 PC 에서만
     * 「Could not instantiate …UnreadFields」(java.rmi)로 묶음 ㅅ 이 통째로 멈춘다(2026-10-24 실측). jdeps 가 없으면(JRE) 건너뛴다
     */
    @Test
    void jlinkModulesCoverSpotBugs() throws Exception {
        java.util.Optional<java.util.spi.ToolProvider> jdeps = java.util.spi.ToolProvider.findFirst("jdeps");
        org.junit.jupiter.api.Assumptions.assumeTrue(jdeps.isPresent(), "jdeps 없음");
        java.util.List<String> args = new java.util.ArrayList<>(java.util.List.of("--multi-release", "17", "--ignore-missing-deps", "--print-module-deps"));
        for (Class<?> c : java.util.List.of(edu.umd.cs.findbugs.FindBugs2.class, org.objectweb.asm.ClassReader.class, org.apache.bcel.Const.class,
                org.dom4j.Document.class, org.jaxen.XPath.class, com.google.gson.Gson.class, org.apache.commons.lang3.StringUtils.class,
                org.apache.commons.text.StringEscapeUtils.class)) {
            args.add(Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        }
        args.add(Analyzers.findSecBugsJar(Path.of("target", "spotbugs-load-test")).toString());
        java.io.StringWriter out = new java.io.StringWriter();
        java.io.StringWriter err = new java.io.StringWriter();
        assertEquals(0, jdeps.get().run(new java.io.PrintWriter(out), new java.io.PrintWriter(err), args.toArray(String[]::new)), err.toString());
        java.util.Set<String> need = new java.util.TreeSet<>(java.util.List.of(out.toString().trim().split(",")));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^JLINK_MODULES=(\\S+)$")
                .matcher(Files.readString(Path.of("scripts/package.sh"), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(m.find(), "package.sh 에 JLINK_MODULES= 줄이 없다");
        // jlink 처럼 requires 를 따라 닫는다(java.desktop → java.prefs 등)
        java.util.Set<String> image = new java.util.TreeSet<>();
        java.lang.module.Configuration.empty().resolve(java.lang.module.ModuleFinder.ofSystem(), java.lang.module.ModuleFinder.of(),
                java.util.List.of(m.group(1).split(","))).modules().forEach(r -> image.add(r.name()));
        need.removeAll(image);
        assertEquals(java.util.Set.of(), need, "jlink 런타임에 없는 모듈 — package.sh JLINK_MODULES 에 더한다");
    }

    @Test
    void findSecBugsResourceLoadsAsPlugin() throws Exception {
        // 플러그인 클래스로더가 jar 를 쥔 채라(Windows) @TempDir 은 못 지운다 — target 아래에
        Path data = Path.of("target", "spotbugs-load-test");
        Path jar = Analyzers.findSecBugsJar(data);
        long size = Files.size(jar);
        assertTrue(size > 100_000, "플러그인 jar " + size);
        assertEquals(jar, Analyzers.findSecBugsJar(data), "두 번째는 그대로");
        Plugin p = SpotBugsRun.findSecBugs(jar); // JVM 에 한 번 — 다른 시험이 먼저 읽었으면 그 플러그인
        assertEquals("com.h3xstream.findsecbugs", p.getPluginId());
        assertTrue(p.getDetectorFactories().size() >= 50, "FindSecBugs 검출기 " + p.getDetectorFactories().size());
    }
}
