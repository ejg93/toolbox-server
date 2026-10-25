package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 5-50 — 감싼 label(같은 줄·다음 줄)·스프레드·여러 줄 태그·중괄호 안 {@code >} */
class JsxLabelRuleTest {

    @Test
    void wrappedLabelIsLabelled() {
        assertEquals(List.of(), JsxLabelRule.flagged("<label><input type=\"checkbox\" name=\"c\" /> 글</label>"));
        assertEquals(List.of(), JsxLabelRule.flagged("<label className=\"x\">\n  <input type=\"radio\" name=\"r\" />\n  글\n</label>"));
        assertEquals(List.of(3), JsxLabelRule.flagged("<label htmlFor=\"a\">글</label>\n<p/>\n<input name=\"b\" />"));
    }

    @Test
    void spreadAndAttributes() {
        assertEquals(List.of(), JsxLabelRule.flagged("<input {...shared} type={type} />"));
        assertEquals(List.of(), JsxLabelRule.flagged("<input id={name} />"));
        assertEquals(List.of(), JsxLabelRule.flagged("<input type={\"hidden\"} name=\"h\" />"));
        assertEquals(List.of(), JsxLabelRule.flagged("<input aria-label=\"검색\" />"));
    }

    @Test
    void multiLineTagAndArrowInsideBraces() {
        assertEquals(List.of(2), JsxLabelRule.flagged("<div>\n<input\n  type=\"text\"\n  name=\"q2\"\n/>\n</div>"));
        assertEquals(List.of(1), JsxLabelRule.flagged("<input onChange={e => f(e)} name=\"q\" />"));
        assertEquals(List.of(), JsxLabelRule.flagged("<input onChange={e => f(e)} id=\"q\" />"));
    }
}
