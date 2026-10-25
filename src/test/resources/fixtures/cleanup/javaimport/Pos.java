package cleanup.javaimport;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.*;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/** 5-34a — 쓰지 않는 import 셋(보통 둘·static 하나 — 5-48), star·쓰이는 static 은 그대로 */
public class Pos {
    public List<String> names() {
        return emptyList();
    }
}
