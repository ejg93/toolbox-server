package kr.ejg.toolbox;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 글로만 있던 구조 규칙을 빌드에서 막는다(0-19).
 * 운영 코드(테스트 제외)만 본다.
 */
class ArchitectureTest {

    static JavaClasses main;

    @BeforeAll
    static void load() {
        main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("kr.ejg.toolbox");
    }

    /** PLAN 5-11 — core 는 web·cli·Javalin 을 import 하지 않는다 */
    @Test
    void coreDoesNotDependOnWebCliOrJavalin() {
        noClasses().that().resideInAPackage("kr.ejg.toolbox.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "kr.ejg.toolbox.web..", "kr.ejg.toolbox.cli..", "io.javalin..")
                .because("PLAN 5-11: core 는 web·cli·Javalin 을 import 하지 않는다")
                .check(main);
    }

    /** 방향은 cli → web → core 하나 */
    @Test
    void webDoesNotDependOnCli() {
        noClasses().that().resideInAPackage("kr.ejg.toolbox.web..")
                .should().dependOnClassesThat().resideInAPackage("kr.ejg.toolbox.cli..")
                .because("cli 가 web 을 띄운다. 거꾸로 부르면 순환")
                .check(main);
    }

    /**
     * 절대 규칙 1 — 런타임에 127.0.0.1 밖으로 나가는 호출을 만들지 않는다.
     * 나가는 통신에 쓰는 타입(HTTP 클라이언트·URLConnection·클라이언트 소켓·SSL)을 운영 코드가 안 쓴다.
     * 서버 쪽({@code ServerSocket}·{@code InetSocketAddress})과 리소스 경로({@code URI}·{@code URL})는 허용.
     */
    @Test
    void noOutboundNetworkTypes() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.net.http..", "javax.net.ssl..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.URLConnection")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.HttpURLConnection")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.Socket")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.DatagramSocket")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.channels.SocketChannel")
                .because("절대 규칙 1: 외부 통신 0")
                .check(main);
    }

    /**
     * URL 타입은 리소스 경로 때문에 허용이지만 그것으로 여는 것은 막는다(2026-09-27 AI 리뷰 —
     * `new URL(..).openStream()` 은 URLConnection 의존 없이 위 규칙을 통과했다).
     * classpath 리소스는 getResourceAsStream 으로 읽는다.
     */
    @Test
    void noOpeningUrls() {
        noClasses().should().callMethod(java.net.URL.class, "openStream")
                .orShould().callMethod(java.net.URL.class, "openConnection")
                .orShould().callMethod(java.net.URL.class, "openConnection", java.net.Proxy.class)
                .orShould().callMethod(java.net.URL.class, "getContent")
                .because("절대 규칙 1: 외부 통신 0")
                .check(main);
    }
}
