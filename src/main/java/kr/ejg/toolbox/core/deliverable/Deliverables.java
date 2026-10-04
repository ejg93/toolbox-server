package kr.ejg.toolbox.core.deliverable;

import java.util.ArrayList;
import java.util.List;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;

/**
 * 정의서 대상 표 거르기(2-16) — 프로필 {@code deliverable.filter}. 스냅샷 {@code scope} 와 같은 {@link Scope} 규칙을 그대로 쓴다
 * (include 가 exclude 보다 먼저, 대소문자 무시). 스냅샷은 전체로 찍고 정의서·DDL·08·09 후보만 거른 표로 만든다
 */
public final class Deliverables {

    private Deliverables() {
    }

    /** filter 가 null 이면 그대로. 스키마가 안 맞으면 빼고, 맞으면 표를 거른 스키마 */
    public static List<Schema> filter(List<Schema> snapshot, Scope filter) {
        if (filter == null) {
            return snapshot;
        }
        List<Schema> out = new ArrayList<>();
        for (Schema s : snapshot) {
            if (filter.acceptsSchema(s.name())) {
                out.add(s.withTables(s.tables().stream().filter(filter::accepts).toList()));
            }
        }
        return out;
    }

    public static int tableCount(List<Schema> schemas) {
        return schemas.stream().mapToInt(s -> s.tables().size()).sum();
    }
}
