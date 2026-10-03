package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 8-3 — logback.xml 이 XML 로 읽히고 콘솔이 stderr 다(8-2). 설정이 깨지면 logback 은 오류를 찍고 기본 설정(stdout)으로 돌아가
 * 테스트가 초록인 채로 CLI 의 json 출력에 로그가 섞인다 — 8-2 에서 주석 안 「--」 로 실제로 그랬다.
 */
class LogbackConfigTest {

    @Test
    void parsesAndConsoleIsStderr() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc;
        try (InputStream in = LogbackConfigTest.class.getResourceAsStream("/logback.xml")) {
            doc = f.newDocumentBuilder().parse(in);
        }
        NodeList apps = doc.getElementsByTagName("appender");
        boolean console = false;
        for (int i = 0; i < apps.getLength(); i++) {
            Element a = (Element) apps.item(i);
            if (a.getAttribute("name").equals("CONSOLE")) {
                console = true;
                assertEquals("System.err", a.getElementsByTagName("target").item(0).getTextContent().trim());
            }
        }
        assertTrue(console, "CONSOLE appender");
    }
}
