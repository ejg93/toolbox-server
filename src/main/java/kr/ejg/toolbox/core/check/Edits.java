package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 줄 지우기·바꾸기 모음(줄 번호 1부터, 원래 글 기준) — 결과와 고친 줄(옛·새 번호)을 낸다. 0-81 — Cleanup 안에서 밖으로(길이 상한) */
final class Edits {
    final Map<Integer, String> repl = new HashMap<>(); // Cleanup.deleteStage 가 덮어쓴다(0-81 — 안쪽 클래스일 때 private 가 보였다)
    private final String[] lines;

    Edits(String text) {
        this.lines = text.split("\n", -1);
    }

    void delete(int line) {
        repl.put(line, null);
    }

    void replace(int line, String text) {
        if (!repl.containsKey(line)) {
            repl.put(line, text);
        }
    }

    boolean isEmpty() {
        return repl.isEmpty();
    }

    String result() {
        List<String> out = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            if (!repl.containsKey(i + 1)) {
                out.add(lines[i]);
            } else if (repl.get(i + 1) != null) {
                out.add(repl.get(i + 1));
            }
        }
        return String.join("\n", out);
    }

    Set<Integer> touchedOld() {
        return new TreeSet<>(repl.keySet());
    }

    Set<Integer> touchedNew() {
        Set<Integer> out = new TreeSet<>();
        int n = 0;
        for (int i = 0; i < lines.length; i++) {
            if (!repl.containsKey(i + 1)) {
                n++;
            } else if (repl.get(i + 1) != null) {
                for (int k = 0; k < repl.get(i + 1).split("\n", -1).length; k++) { // 5-62c — 끼운 줄도 고친 줄
                    n++;
                    out.add(n);
                }
            }
        }
        return out;
    }
}
