package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.dict.Dictionaries;
import org.junit.jupiter.api.Test;

/** 3-2 — 조립 규칙 여덟. 사전은 손으로 만든 작은 것 */
class ConverterTest {

    static final Dictionaries D = new Dictionaries(
            Map.of("ITEM", "항목", "ID", "아이디", "NM", "명", "CD", "코드", "USE", "사용", "YN", "여부"),
            Map.of("TB_ITEM", "항목테이블", "CD", "기관코드"),
            Map.of("UPD", "수정", "NM", "이름"),
            Map.of(), Map.of());

    static Converter.Result conv(String phys) {
        return new Converter(D, true).convert(phys, List.of());
    }

    @Test
    void wholeNameInOrgWins() {
        assertEquals(new Converter.Result("항목테이블", "given", List.of()), conv("tb_item"));
    }

    @Test
    void tokenPriorityOrgThenUserThenWord() {
        assertEquals("기관코드", conv("CD").name(), "통째 기관 매칭");
        assertEquals(new Converter.Result("항목기관코드", "multi", List.of()), conv("ITEM_CD"), "토큰 CD 는 기관 먼저");
        assertEquals("항목이름", conv("ITEM_NM").name(), "NM 은 사용자가 공통보다 먼저");
    }

    @Test
    void orgLastWhenPriorityIsDict() {
        Converter c = new Converter(D, false);
        assertEquals("항목코드", c.convert("ITEM_CD", List.of()).name(), "기관을 뒤로 — 공통 CD 가 이긴다");
    }

    @Test
    void consecutiveMissingKeepUnderscore() {
        assertEquals(new Converter.Result("항목FOO_BAR아이디", "mix", List.of("FOO", "BAR")), conv("ITEM_FOO_BAR_ID"));
        assertEquals("항목FOO아이디", conv("ITEM_FOO_ID").name(), "하나만 빠지면 _ 없음");
    }

    @Test
    void singleSourceAndMulti() {
        assertEquals(new Converter.Result("사용여부", "word", List.of()), conv("USE_YN"));
        assertEquals("multi", conv("UPD_YN").src(), "사용자 + 공통");
    }

    @Test
    void noneKeepsOriginalName() {
        assertEquals(new Converter.Result("foo_bar", "none", List.of("FOO", "BAR")), conv("foo_bar"));
        assertEquals(new Converter.Result("__", "none", List.of()), conv("__"), "토큰이 없으면 원문");
    }

    @Test
    void skipTokensApplyOnlyWhenGiven() {
        Converter c = new Converter(D, true);
        assertEquals(new Converter.Result("항목아이디", "word", List.of()), c.convert("TMP_ITEM_ID", List.of("TMP")));
        assertEquals("mix", c.convert("TMP_ITEM_ID", List.of()).src(), "무시 토큰 없이는 TMP 가 남는다");
    }

    @Test
    void usedTokensAndWordsAreCollected() {
        Converter c = new Converter(D, true);
        c.convert("ITEM_NM", List.of());
        c.convert("ITEM_ID", List.of());
        assertEquals(List.of("ITEM", "NM", "ID"), List.copyOf(c.usedTokens().keySet()));
        assertEquals(new Converter.Used("이름", "user"), c.usedTokens().get("NM"));
        assertEquals(List.of("ITEM", "ID"), List.copyOf(c.usedWords()), "word 소스만");
    }
}
