package org.demo.app.order;

import org.demo.app.util.Helper;

/** 5-34 마무리 — 같은 조직 타입의 정적 호출은 그 클래스가 초기화 중 이쪽을 되읽을 수 있어 순서를 안 바꾼다 */
public class SkipSameOrg {
    private static final Object H = Helper.make();
    public static final Object J = new java.util.ArrayList<>();
}
