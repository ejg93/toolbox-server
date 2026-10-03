package kr.ejg.toolbox.core.text;

import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 외부 DTD·엔티티·스키마를 안 읽는 SAX(규칙 1) — 코드 검사·형상 관리·프로그램 분석 공용(6-2).
 * 설정은 parse 와 같은 메서드에 둔다 — FindSecBugs XXE 판정이 메서드 안만 본다(5-3 실측).
 */
public final class SafeSax {

    private SafeSax() {
    }

    /**
     * doctype 이 false 면 {@code <!DOCTYPE>} 자체를 거부한다(svn XML). true 면 받되 외부 DTD 는 안 읽는다 —
     * MyBatis 매퍼는 전부 {@code <!DOCTYPE mapper PUBLIC …>} 를 단다(eGov 1,224 실측)
     */
    public static void parse(InputSource in, DefaultHandler h, boolean doctype) throws SAXException, IOException {
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(false);
            f.setValidating(false);
            f.setXIncludeAware(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !doctype);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            SAXParser parser = f.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            parser.parse(in, h);
        } catch (ParserConfigurationException e) {
            throw new SAXException(e);
        }
    }
}
