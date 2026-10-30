package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 5-55c(설계 44 §3-6) — *.properties 값 색인 */
class PropertiesIndexTest {

    static Source props(String rel, String text) {
        return new Source(rel, text, null, null, null);
    }

    @Test
    void keysValuesAndComments() {
        PropertiesIndex idx = new PropertiesIndex();
        idx.see(props("a.properties", "# 주석\n! 주석\n\nalg=DES/ECB/PKCS5Padding\n  hash.alg : MD5  \nurl=jdbc:x:y\nbroken\n"));
        assertEquals(Set.of("DES/ECB/PKCS5Padding"), idx.get("alg"));
        assertEquals(Set.of("MD5"), idx.get("hash.alg"));
        assertEquals(Set.of("jdbc:x:y"), idx.get("url"));
        assertEquals(Set.of(), idx.get("broken"));
        assertEquals(Set.of(), idx.get("none"));
        assertEquals(1, idx.files());
    }

    @Test
    void sameKeyInTwoFilesIsUnion() {
        PropertiesIndex idx = new PropertiesIndex();
        idx.see(props("dev.properties", "alg=AES/GCM/NoPadding"));
        idx.see(props("prod.properties", "alg=DES"));
        assertEquals(Set.of("AES/GCM/NoPadding", "DES"), idx.get("alg"));
        assertEquals(2, idx.files());
    }

    @Test
    void valuesAreCapped() {
        PropertiesIndex idx = new PropertiesIndex();
        idx.see(props("a.properties", IntStream.range(0, 20).mapToObj(i -> "k=v" + i).collect(Collectors.joining("\n"))));
        assertEquals(PropertiesIndex.VALUES_MAX, idx.get("k").size());
    }
}
