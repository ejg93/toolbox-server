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

    /**
     * 절대 규칙 1 예외(svn 서버, 2026-10-02)를 코드로 묶는다(PR #23 AI 리뷰 ②). 프로세스를 띄우는 자리는 둘뿐 —
     * 형상 관리 명령({@code core.vcs.Cli}, 실행 파일은 {@code Cli.EXECUTABLES} 의 git·svn)과 기동 뒤 브라우저 열기({@code web.App}).
     * 나머지 클래스가 프로세스를 띄우면 svn 말고 다른 것이 밖으로 나갈 길이 생긴다.
     */
    @Test
    void processesOnlyFromVcsCliAndBrowserOpen() {
        noClasses().that().doNotHaveFullyQualifiedName("kr.ejg.toolbox.core.vcs.Cli")
                .and().doNotHaveFullyQualifiedName("kr.ejg.toolbox.web.App")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
                .orShould().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo(Runtime.class)))
                        .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.name("exec"))))
                .because("절대 규칙 1: 밖으로 나가는 프로세스는 svn(사용자가 조회를 누를 때)뿐")
                .check(main);
    }

    /**
     * {@code Cli.run} 은 git·svn 의 아무 인자나 받는다 — 부르는 자리를 {@code core.vcs}(ref 를 검사하는 {@code WorkingCopy})로 묶는다
     * (PR #24 AI 리뷰 ①: web 이 {@code Cli.run(List.of("git","push"))} 를 부를 길).
     */
    @Test
    void cliRunOnlyFromVcsPackage() {
        noClasses().that().resideOutsideOfPackage("kr.ejg.toolbox.core.vcs..")
                .should().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo(kr.ejg.toolbox.core.vcs.Cli.class)))
                        .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.name("run"))))
                .because("명령 인자를 검사하는 곳은 WorkingCopy 뿐이다")
                .check(main);
    }

    /**
     * 벤더 딕셔너리 SQL 은 {@code VendorMetaSource.prepare} 한 곳으로만 연다 — 60초 제한·물러서기 경고(1-12)가 빠지지 않게
     * (1-24, PR #39 AI 리뷰 ①: 이력 글로만 있던 규칙)
     */
    @Test
    void dialectPreparesOnlyThroughVendorMetaSource() {
        noClasses().that().resideInAPackage("kr.ejg.toolbox.core.dialect..").and().doNotHaveSimpleName("VendorMetaSource")
                .should().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo(java.sql.Connection.class)))
                        .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.name("prepareStatement"))))
                .because("벤더 SQL 은 VendorMetaSource.prepare(시간 제한·물러서기) 를 거친다")
                .check(main);
    }
}
