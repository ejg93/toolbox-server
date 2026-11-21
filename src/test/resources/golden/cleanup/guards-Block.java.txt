package cleanup.guards;

public class Block {
    String s = """
        // tb-ignore common.todo
        """;
    // tb-ignore common.todo 5-121 — 텍스트 블록 밖 진짜 표식(쓸모없음) — 가드가 이 파일을 건너뛰는지
}
