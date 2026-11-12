package cleanup.starimport;

import java.io.*;
import java.util.*;
import static java.util.Collections.*;

/** 설계 64 — 별표 import 풀기: java.util 은 쓰는 셋으로 · java.io 는 안 써서 삭제 · 정적 별표는 손으로 */
public class Pos {
    public List<String> names(Map<String, String> m) {
        for (Map.Entry<String, String> e : m.entrySet()) {
            e.getKey();
        }
        return Collections.unmodifiableList(emptyList());
    }
}
