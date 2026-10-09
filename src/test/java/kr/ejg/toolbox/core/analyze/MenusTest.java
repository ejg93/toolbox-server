package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.ejg.toolbox.core.text.Csv;
import org.junit.jupiter.api.Test;

/** 6-29 — 메뉴 CSV: 경로 한 열 + URL, 헤더 한글·영문, 구분자 정리, 사용여부 정규화 */
class MenusTest {

    @Test
    void koreanHeader() {
        Menus.Parsed p = Menus.parse("메뉴,URL,화면ID,사용여부\n"
                + "게시판 > 목록,/bbs/list.do?x=1,SCR-01,1\n"
                + "게시판 >목록(옛),/bbs/list.do,,아니오\n"
                + "관리,,,Y\n"
                + "가 ＞ 나 ＞ 다 ＞ 라 ＞ 마,/deep.do,,보류\n");
        assertEquals(List.of(
                new Menus.Row(1, "게시판 > 목록", "목록", "/bbs/list.do", "SCR-01", "Y", null),
                new Menus.Row(2, "게시판 > 목록(옛)", "목록(옛)", "/bbs/list.do", null, "N", null),
                new Menus.Row(3, "관리", "관리", null, null, "Y", null),
                new Menus.Row(4, "가 > 나 > 다 > 라 > 마", "마", "/deep.do", null, "보류", null)), p.rows(), "깊이 제한 없음 · 전각 ＞ · ? 뒤 뗌");
        assertEquals(3, p.withUrl(), "URL 없는 중간 메뉴는 안 센다");
        assertEquals(List.of("사용여부가 Y/N 밖 1 — 원래 글 그대로"), p.warnings());
    }

    @Test
    void englishHeaderAndBom() {
        byte[] bytes = ((char) 0xFEFF + "menu,url,use_yn,auth\nA > B,/a.do,N,ROLE_ADMIN\n").getBytes(StandardCharsets.UTF_8);
        Menus.Parsed p = Menus.parse(Csv.decode(bytes));
        assertEquals(List.of(new Menus.Row(1, "A > B", "B", "/a.do", null, "N", "ROLE_ADMIN")), p.rows());
    }

    @Test
    void rejects() {
        assertThrows(IllegalArgumentException.class, () -> Menus.parse("이름,주소값\nA,/a.do\n"), "경로 열 없음");
        assertThrows(IllegalArgumentException.class, () -> Menus.parse("메뉴,URL\n"), "데이터 없음");
        assertThrows(IllegalArgumentException.class, () -> Menus.parse("메뉴,URL\n,/a.do\n"), "경로가 전부 빔");
        assertEquals("/x.do", Menus.normUrl("  /x.do?y=1 "));
        assertEquals(null, Menus.normUrl("  "));
    }
}
