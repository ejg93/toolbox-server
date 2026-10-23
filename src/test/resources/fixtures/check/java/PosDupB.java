package egov.pos.dup;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * 5-35b 양성 — PosDupA 를 베꼈다
 */
public class PosDupB {

    // 같은 몸
    List<String> build(int seed) {
        List<String> out = new ArrayList<>();

        out.add("a1-" + seed);
        out.add("a2-" + seed);
        out.add("a3-" + seed);
        out.add("a4-" + seed);
        out.add("a5-" + seed);
        out.add("a6-" + seed);
        out.add("a7-" + seed);
        out.add("a8-" + seed);
        out.add("a9-" + seed);
        out.add("a10-" + seed);
        out.add("a11-" + seed);
        out.add("a12-" + seed);
        out.add("a13-" + seed);
        out.add("a14-" + seed);
        out.add("a15-" + seed);
        // 가운데 주석

        out.add("a16-" + seed);
        out.add("a17-" + seed);
        out.add("a18-" + seed);
        out.add("a19-" + seed);
        out.add("a20-" + seed);
        out.add("a21-" + seed);
        out.add("a22-" + seed);
        out.add("a23-" + seed);
        out.add("a24-" + seed);
        out.add("a25-" + seed);
        out.add("a26-" + seed);
        out.add("a27-" + seed);
        out.add("a28-" + seed);
        out.add("a29-" + seed);
        out.add("a30-" + seed);
        return out;
    }

    Map<String, String> other() {
        return null;
    }
}
