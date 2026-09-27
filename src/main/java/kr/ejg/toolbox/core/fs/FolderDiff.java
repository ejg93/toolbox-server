package kr.ejg.toolbox.core.fs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 폴더 비교(4-2). 두 폴더를 {@link LocalFiles#list} 로 훑어(같은 제외·거절) 상대 경로로 맞춘다.
 * 같음은 크기가 같을 때만 SHA-256 으로 잰다. 결과는 상대 경로 정렬.
 */
public final class FolderDiff {

    public enum Status { ONLY_A, ONLY_B, SAME, DIFF }

    public record Item(String rel, Status status, Long sizeA, Long sizeB) {
    }

    public record Result(List<Item> items, boolean truncated) {
        public Result {
            items = List.copyOf(items);
        }
    }

    private FolderDiff() {
    }

    public static Result compare(LocalFiles files, String a, String b, List<String> globs) throws IOException {
        LocalFiles.Listing la = files.list(a, globs, LocalFiles.MAX_FILES);
        LocalFiles.Listing lb = files.list(b, globs, LocalFiles.MAX_FILES);
        Path ra = files.check(a);
        Path rb = files.check(b);
        Map<String, Long> sa = new TreeMap<>();
        la.files().forEach(e -> sa.put(e.rel(), e.size()));
        Map<String, Long> sb = new TreeMap<>();
        lb.files().forEach(e -> sb.put(e.rel(), e.size()));
        TreeSet<String> all = new TreeSet<>(sa.keySet());
        all.addAll(sb.keySet());
        List<Item> out = new ArrayList<>();
        for (String rel : all) {
            Long x = sa.get(rel);
            Long y = sb.get(rel);
            Status st;
            if (y == null) {
                st = Status.ONLY_A;
            } else if (x == null) {
                st = Status.ONLY_B;
            } else if (!x.equals(y)) {
                st = Status.DIFF;
            } else {
                st = Arrays.equals(sha256(ra.resolve(rel)), sha256(rb.resolve(rel))) ? Status.SAME : Status.DIFF;
            }
            out.add(new Item(rel, st, x, y));
        }
        return new Result(out, la.truncated() || lb.truncated());
    }

    static byte[] sha256(Path p) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buf = new byte[65536];
        try (InputStream in = Files.newInputStream(p)) {
            for (int r; (r = in.read(buf)) > 0; ) {
                md.update(buf, 0, r);
            }
        }
        return md.digest();
    }
}
