package kr.ejg.toolbox.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 4-2 — 줄 비교. 골든 셋은 {@code golden/text/diff-*.json} */
class LineDiffTest {

    @Test
    void changedLine() {
        GoldenFiles.assertJson("text/diff-changed.json", LineDiff.diff(
                "<%@ page contentType=\"text/html\" %>\n<div>\n  <p>가</p>\n</div>\n",
                "<%@ page contentType=\"text/html\" %>\n<div>\n  <p>나</p>\n</div>\n"));
    }

    @Test
    void insertAndDelete() {
        GoldenFiles.assertJson("text/diff-insert-delete.json", LineDiff.diff(
                "a\nb\nc\nd\ne\n",
                "a\nc\nd\nx\ny\ne\n"));
    }

    @Test
    void lineEndingsAndEmptySides() {
        GoldenFiles.assertJson("text/diff-edges.json", List.of(
                LineDiff.diff("a\r\nb\r\n", "a\nb"),
                LineDiff.diff("", "새 파일\n"),
                LineDiff.diff("지운 파일\n", null)));
    }

    @Test
    void tooBig() {
        String many = "x\n".repeat(LineDiff.MAX_LINES + 1);
        assertTrue(LineDiff.diff(many, "").tooBig());
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            a.append("a").append(i).append('\n');
            b.append("b").append(i).append('\n');
        }
        LineDiff.Result r = LineDiff.diff(a.toString(), b.toString());
        assertTrue(r.tooBig(), "가운데 표가 상한을 넘는다");
        assertEquals(0, r.ops().size());
    }
}
