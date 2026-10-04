package kr.ejg.toolbox.core.meta;

/** CHECK 제약(1-21). {@code condition} 은 딕셔너리가 준 조건 글 그대로(DDL 메타 — 사용자 코드 본문이 아니다) */
public record Check(String name, String condition) {
}
