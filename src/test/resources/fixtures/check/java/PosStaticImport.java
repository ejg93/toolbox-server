package egov.pos;

import static java.util.Collections.emptyList;
import static java.util.Map.*;
import static java.util.Objects.requireNonNull;

import java.util.List;

/** 5-48 — 쓰지 않는 정적 import 하나, 정적 별표는 starImport, 쓰는 정적 import 는 그대로(이름을 주석에 적으면 쓰임으로 센다) */
public class PosStaticImport {

    public List<String> names(List<String> in) {
        return requireNonNull(in);
    }
}
