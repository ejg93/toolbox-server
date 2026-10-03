package kr.ejg.toolbox.core.analyze;

/**
 * 프로그램 분석이 못 풀었거나 덜 푼 자리(6-2·6-3) — 지우지 않고 낸다. detail 은 식별자(ns.id·클래스.메서드·표·refid)만, 코드 본문은 없다(규칙 3).
 * kind — parse·table·tagVerb·dialect·missing·statement·prefix·ambiguous·depth·viewDynamic
 */
public record Unresolved(String kind, String file, int line, String detail) {
}
