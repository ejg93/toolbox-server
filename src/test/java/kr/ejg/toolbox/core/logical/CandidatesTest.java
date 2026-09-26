package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.text.Csv;
import org.junit.jupiter.api.Test;

/**
 * 3-6 — 후보 CSV 넷이 순수본 expTerms·expStdWords·expDomains·expWordUse 를 Puppeteer 로 뜬 것과 글자까지 같다(DB명 SAMPLE,
 * 검토 제외 끔). git 이 CSV 를 LF 로 바꿔 두므로 비교는 LF 로.
 */
class CandidatesTest {

    static String golden(String kind) throws Exception {
        return Files.readString(LogicalRunSampleTest.GOLDEN.resolve("candidates-" + kind + ".csv"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    static String lf(String s) {
        return s.replace("\r\n", "\n");
    }

    @Test
    void termsMatchJs() throws Exception {
        String csv = Candidates.terms(LogicalRunSampleTest.sampleResult(), LogicalRunSampleTest.sampleDicts(), true, "SAMPLE", false);
        assertEquals(golden("terms"), lf(csv));
    }

    @Test
    void stdWordsMatchJs() throws Exception {
        String csv = Candidates.stdWords(LogicalRunSampleTest.sampleResult(), LogicalRunSampleTest.sampleDicts(), "SAMPLE");
        assertEquals(golden("words"), lf(csv));
    }

    @Test
    void domainsMatchJs() throws Exception {
        LogicalRunSampleTest.sampleResult();
        String csv = Candidates.domains(LogicalRunSampleTest.sampleResult(), new DomainMatcher(LogicalRunSampleTest.domains), "SAMPLE");
        assertEquals(golden("domains"), lf(csv));
    }

    @Test
    void wordUseMatchesJs() throws Exception {
        byte[] moi;
        try (InputStream in = DictStore.class.getResourceAsStream(DictStore.MOI_WORDS)) {
            moi = in.readAllBytes();
        }
        List<List<String>> rows = Csv.parse(Csv.decode(moi));
        String csv = Candidates.wordUse(rows, 1, LogicalRunSampleTest.sampleResult());
        assertEquals(golden("worduse"), lf(csv));
    }

    @Test
    void dbNameFallsBackToOwners() throws Exception {
        assertEquals("SHOP", Candidates.dbName(" ", LogicalRunSampleTest.sampleResult()));
        assertEquals("X", Candidates.dbName("X", LogicalRunSampleTest.sampleResult()));
    }

    @Test
    void domainRules() {
        assertEquals(new DomainMatcher.Fmt("V20", "20자리 이내 문자", "20자리 이내 문자"), DomainMatcher.fmt("varchar2", "20", ""));
        assertEquals(new DomainMatcher.Fmt("N13,2", "99999999999.99", "99999999999.99"), DomainMatcher.fmt("NUMBER", "13", "2"));
        assertNull(DomainMatcher.fmt("DATE", "7", ""), "모르는 계열");
        assertNull(DomainMatcher.fmt("NUMBER", "2", "2"), "정수 자리 0");
        assertEquals("V", DomainMatcher.typeCode("CHARACTER VARYING"));
        assertEquals("C", DomainMatcher.typeCode("nchar"));
        assertEquals("", DomainMatcher.intOrBlank("0"), "JS parseInt(..)||''");
        assertEquals("12", DomainMatcher.intOrBlank(" 12abc"));
        DomainMatcher dm = new DomainMatcher(List.of(
                new DictStore.Domain("", "번호", "번호V20", "VARCHAR", "20", "", "", "", "", "", ""),
                new DictStore.Domain("", "우편번호", "우편번호C5", "CHAR", "5", "", "", "", "", "", "")));
        assertEquals("우편번호", dm.word("자택우편번호"), "긴 분류명이 먼저");
        assertEquals("번호", dm.word("주문번호"));
    }
}
