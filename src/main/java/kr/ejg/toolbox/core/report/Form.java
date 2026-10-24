package kr.ejg.toolbox.core.report;

import java.util.List;

/**
 * 산출물 양식 하나 — 번호·이름·열(2-4). 0-62b 에서 {@code core.deliverable.Form} 에서 옮겼다 — 양식 채우기({@link XlsxFiller}·{@link Mapping})가
 * 산출물 패키지를 거꾸로 부르던 고리(deliverable ↔ report)를 끊는다. 목록은 {@code core.deliverable.Forms.all()}.
 * file: 예시 양식 파일 이름 — 번호_문서명(공백 없이).xlsx
 */
public record Form(String no, String name, List<String> columns) {
    public Form {
        columns = List.copyOf(columns);
    }

    public String file() {
        return no + "_" + name.replace(" ", "") + ".xlsx";
    }
}
