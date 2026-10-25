package egov.pos;

import java.util.Map;
import java.util.Set;

/** 5-47 — Java 21 패턴 switch·레코드 패턴(제네릭·한정·when·case null). 쓰지 않는 import 하나 */
public class PosJava21 {

    sealed interface Allowed<T> permits Everything, Only {
    }

    record Everything<T>() implements Allowed<T> {
    }

    record Only<T>(Set<T> values) implements Allowed<T> {
    }

    sealed interface Verdict permits Verdict.Granted, Verdict.Denied {
        record Granted(String who, int n) implements Verdict {
        }

        record Denied(String why) implements Verdict {
        }
    }

    public <T> boolean covers(Allowed<T> a, T v) {
        return switch (a) {
            case Everything<T> ignored -> true;
            case Only<T>(Set<T> values) -> values.contains(v);
        };
    }

    public String describe(Verdict v) {
        return switch (v) {
            case Verdict.Granted(String who, int n) when n > 0 -> who + n;
            case Verdict.Granted g -> g.who();
            case Verdict.Denied(String why) -> why;
            case null -> "none";
        };
    }
}
